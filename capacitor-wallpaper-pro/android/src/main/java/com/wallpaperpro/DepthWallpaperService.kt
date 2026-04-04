// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  DepthWallpaperService.kt
//
//  Renders the depth-effect live wallpaper.
//
//  Architecture:
//    • SensorManager (TYPE_ROTATION_VECTOR on sensorThread) → target offsets
//    • Choreographer.FrameCallback on main thread → lerp + draw at 60fps
//    • Handler clock tick every second (aligned to system second boundary)
//    • All bitmap I/O on Dispatchers.IO coroutine
//
//  Layer order: Background → Clock → Foreground  (depth z-ordering)
// ─────────────────────────────────────────────────────────────────────────────
package com.wallpaperpro

import android.content.Context
import android.graphics.*
import android.hardware.*
import android.os.*
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.*

class DepthWallpaperService : WallpaperService() {

    companion object {
        private const val TAG     = "WallpaperPro.LiveSvc"
        const val PREFS_NAME      = "WallpaperProPrefs"
        const val KEY_DEPTH_CFG   = "depth_wallpaper_config"
    }

    override fun onCreateEngine(): Engine = DepthEngine()

    // ─────────────────────────────────────────────────────────────────────────
    //  DepthEngine
    // ─────────────────────────────────────────────────────────────────────────
    inner class DepthEngine : Engine(), SensorEventListener, Choreographer.FrameCallback {

        // ── Threads / scopes ─────────────────────────────────────────────────
        private val ioScope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val sensorThread = HandlerThread("WP_SensorThread").also { it.start() }
        private val sensorHandler= Handler(sensorThread.looper)
        private val mainHandler  = Handler(Looper.getMainLooper())

        // ── Config + renderers ───────────────────────────────────────────────
        private var cfg: DepthWallpaperConfig? = null
        private var renderer: DepthRenderer?   = null
        private var bitmapsReady               = false

        // ── Choreographer ────────────────────────────────────────────────────
        private val choreographer = Choreographer.getInstance()
        private var isDrawing     = false

        // ── Sensor state ──────────────────────────────────────────────────────
        private var sensorManager: SensorManager? = null
        private var rotationSensor: Sensor?       = null

        // Target offsets from sensor (written on sensorThread)
        @Volatile private var targetX = 0f
        @Volatile private var targetY = 0f
        // Smoothed offsets (lerped on main thread every frame)
        private var smoothX = 0f
        private var smoothY = 0f

        // Gravity filter for tilt baseline
        private val gravityFilter = FloatArray(3) { 0f }

        // ── Surface state ─────────────────────────────────────────────────────
        private var surfaceW = 0
        private var surfaceH = 0

        // ── Clock tick ───────────────────────────────────────────────────────
        private val clockRunnable = object : Runnable {
            override fun run() {
                if (isDrawing) drawFrame()
                // Schedule next tick at the next second boundary
                val delay = 1000L - (System.currentTimeMillis() % 1000L)
                mainHandler.postDelayed(this, delay)
            }
        }

        // ═════════════════════════════════════════════════════════════════════
        //  Lifecycle
        // ═════════════════════════════════════════════════════════════════════

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            loadConfig()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            Log.d(TAG, "Surface created")
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (width != surfaceW || height != surfaceH) {
                surfaceW = width
                surfaceH = height
                prepareBitmaps()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            stopRendering()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                startRendering()
                registerSensor()
            } else {
                stopRendering()
                unregisterSensor()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            stopRendering()
            unregisterSensor()
            sensorThread.quitSafely()
            ioScope.cancel()
        }

        // ═════════════════════════════════════════════════════════════════════
        //  Config + bitmap loading
        // ═════════════════════════════════════════════════════════════════════

        private fun loadConfig() {
            val prefs  = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw    = prefs.getString(KEY_DEPTH_CFG, null)
            if (raw == null) { Log.w(TAG, "No depth config found"); return }

            cfg = DepthWallpaperConfig.fromJson(raw)
            if (cfg == null) { Log.e(TAG, "Failed to parse depth config"); return }

            val clockRend = ClockRenderer(cfg!!.clock)
            renderer      = DepthRenderer(cfg!!, clockRend)

            Log.d(TAG, "Depth config loaded: bg=${cfg!!.backgroundPath.takeLast(30)} fg=${cfg!!.foregroundPath.takeLast(30)}")
        }

        private fun prepareBitmaps() {
            val r = renderer ?: return
            val c = cfg      ?: return
            if (surfaceW == 0 || surfaceH == 0) return

            bitmapsReady = false
            ioScope.launch {
                try {
                    // Decode raw bitmaps (OOM-safe via options)
                    val bgRaw = decodeSafe(c.backgroundPath, surfaceW, surfaceH)
                    val fgRaw = decodeSafe(c.foregroundPath, surfaceW, surfaceH)

                    // Scale with parallax margins
                    val bgBmp = bgRaw?.let { r.prepareLayerBitmap(it, surfaceW, surfaceH, c.depth.bgParallaxFactor) }
                    val fgBmp = fgRaw?.let { r.prepareLayerBitmap(it, surfaceW, surfaceH, c.depth.fgParallaxFactor) }

                    withContext(Dispatchers.Main) {
                        r.backgroundBitmap = bgBmp
                        r.foregroundBitmap = fgBmp
                        bitmapsReady       = true
                        Log.d(TAG, "Bitmaps ready: bg=${bgBmp?.width}×${bgBmp?.height} fg=${fgBmp?.width}×${fgBmp?.height}")
                        if (isDrawing) drawFrame()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "prepareBitmaps error", e)
                }
            }
        }

        /**
         * OOM-safe bitmap decode: measures dimensions first, calculates
         * inSampleSize so the decoded image doesn't exceed target size × 2.
         */
        private fun decodeSafe(path: String, targetW: Int, targetH: Int): Bitmap? {
            return try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, opts)
                opts.inSampleSize    = calcSampleSize(opts.outWidth, opts.outHeight, targetW, targetH)
                opts.inJustDecodeBounds = false
                opts.inPreferredConfig  = Bitmap.Config.ARGB_8888
                BitmapFactory.decodeFile(path, opts)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "OOM decoding $path", e)
                null
            }
        }

        private fun calcSampleSize(srcW: Int, srcH: Int, tW: Int, tH: Int): Int {
            var s = 1; var w = srcW; var h = srcH
            while (w / 2 >= tW && h / 2 >= tH) { w /= 2; h /= 2; s *= 2 }
            return s
        }

        // ═════════════════════════════════════════════════════════════════════
        //  Rendering loop
        // ═════════════════════════════════════════════════════════════════════

        private fun startRendering() {
            if (isDrawing) return
            isDrawing = true
            choreographer.postFrameCallback(this)
            // Align clock tick to next second boundary
            val delay = 1000L - (System.currentTimeMillis() % 1000L)
            mainHandler.postDelayed(clockRunnable, delay)
            Log.d(TAG, "Rendering started")
        }

        private fun stopRendering() {
            isDrawing = false
            choreographer.removeFrameCallback(this)
            mainHandler.removeCallbacks(clockRunnable)
            Log.d(TAG, "Rendering stopped")
        }

        /** Called by Choreographer at vsync (~60fps). */
        override fun doFrame(frameTimeNanos: Long) {
            if (!isDrawing) return
            // Lerp smoothed offsets towards sensor targets
            val s = cfg?.depth?.smoothing ?: 0.08f
            smoothX += (targetX - smoothX) * s
            smoothY += (targetY - smoothY) * s
            drawFrame()
            choreographer.postFrameCallback(this)
        }

        private fun drawFrame() {
            if (!bitmapsReady) return
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
                canvas?.let { c ->
                    c.drawColor(Color.BLACK)
                    renderer?.render(c, smoothX, smoothY)
                }
            } catch (e: Exception) {
                Log.e(TAG, "drawFrame error", e)
            } finally {
                try { canvas?.let { holder.unlockCanvasAndPost(it) } } catch (_: Exception) {}
            }
        }

        // ═════════════════════════════════════════════════════════════════════
        //  Sensor
        // ═════════════════════════════════════════════════════════════════════

        private fun registerSensor() {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rotationSensor == null) {
                Log.w(TAG, "No rotation vector sensor — trying accelerometer fallback")
                rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            }
            rotationSensor?.let { s ->
                sensorManager?.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME, sensorHandler)
                Log.d(TAG, "Sensor registered: ${s.name}")
            }
        }

        private fun unregisterSensor() {
            sensorManager?.unregisterListener(this)
        }

        override fun onSensorChanged(event: SensorEvent) {
            val d   = cfg?.depth ?: return
            val max = d.maxOffset
            val mul = d.sensitivity

            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    // Rotation vector gives drift-free orientation
                    val rotMat = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotMat, event.values)
                    val orient = FloatArray(3)
                    SensorManager.getOrientation(rotMat, orient)
                    // orient[1] = pitch (tilt forward/back)
                    // orient[2] = roll  (tilt left/right)
                    val pitch = orient[1]
                    val roll  = orient[2]
                    targetX = (-roll  * max * mul * 2f).coerceIn(-max, max)
                    targetY = (pitch  * max * mul * 2f).coerceIn(-max, max)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    // Low-pass filter to separate gravity from movement
                    val alpha = 0.8f
                    gravityFilter[0] = alpha * gravityFilter[0] + (1 - alpha) * event.values[0]
                    gravityFilter[1] = alpha * gravityFilter[1] + (1 - alpha) * event.values[1]
                    val tiltX = gravityFilter[0] / SensorManager.GRAVITY_EARTH  // -1 to 1
                    val tiltY = gravityFilter[1] / SensorManager.GRAVITY_EARTH
                    targetX = (-tiltX * max * mul).coerceIn(-max, max)
                    targetY = (tiltY  * max * mul).coerceIn(-max, max)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }
}
