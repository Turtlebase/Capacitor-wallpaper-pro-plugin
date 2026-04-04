// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  ImageProcessor.kt  (v2)
//
//  New in v2:
//    • OOM guard  – safe inSampleSize pre-check before loading
//    • Text/quote overlay  – native Canvas text rendering
//    • Gradient wallpaper  – linear / radial / sweep from JS config
//    • Crop anchor  – cropX / cropY 0.0–1.0 for fill mode
//    • LRU cache integration
// ─────────────────────────────────────────────────────────────────────────────
package com.wallpaperpro

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.getcapacitor.JSObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.*

data class SetResult(val success: Boolean, val message: String)

// ─────────────────────────────────────────────────────────────────────────────
//  FilterOptions
// ─────────────────────────────────────────────────────────────────────────────
data class FilterOptions(
    val blur: Float        = 0f,
    val brightness: Float  = 1f,
    val contrast: Float    = 1f,
    val saturation: Float  = 1f,
    val grayscale: Boolean = false,
    val sepia: Float       = 0f,
    val vignette: Float    = 0f,
    val hue: Float         = 0f,
    val temperature: Float = 0f,
    val opacity: Float     = 1f,
    val tintColor: String? = null,
    val tintOpacity: Float = 0.3f,
) {
    companion object {
        fun fromJSObject(js: JSObject) = FilterOptions(
            blur        = js.optDouble("blur",        0.0).toFloat(),
            brightness  = js.optDouble("brightness",  1.0).toFloat(),
            contrast    = js.optDouble("contrast",    1.0).toFloat(),
            saturation  = js.optDouble("saturation",  1.0).toFloat(),
            grayscale   = js.optBoolean("grayscale",  false),
            sepia       = js.optDouble("sepia",       0.0).toFloat(),
            vignette    = js.optDouble("vignette",    0.0).toFloat(),
            hue         = js.optDouble("hue",         0.0).toFloat(),
            temperature = js.optDouble("temperature", 0.0).toFloat(),
            opacity     = js.optDouble("opacity",     1.0).toFloat(),
            tintColor   = if (js.has("tintColor")) js.getString("tintColor") else null,
            tintOpacity = js.optDouble("tintOpacity", 0.3).toFloat(),
        )
    }

    val isIdentity get() = blur == 0f && brightness == 1f && contrast == 1f &&
        saturation == 1f && !grayscale && sepia == 0f && vignette == 0f &&
        hue == 0f && temperature == 0f && opacity == 1f && tintColor == null
}

// ─────────────────────────────────────────────────────────────────────────────
//  TextOverlayOptions
// ─────────────────────────────────────────────────────────────────────────────
data class TextOverlayOptions(
    val text: String,
    val fontSize: Float         = 48f,
    val color: String           = "#FFFFFF",
    val fontFamily: String      = "sans-serif",
    val bold: Boolean           = false,
    val italic: Boolean         = false,
    val alignment: String       = "center",   // "left" | "center" | "right"
    val anchorX: Float          = 0.5f,       // 0.0 – 1.0
    val anchorY: Float          = 0.85f,
    val shadowColor: String     = "#00000088",
    val shadowRadius: Float     = 8f,
    val shadowDx: Float         = 2f,
    val shadowDy: Float         = 2f,
    val backgroundColor: String? = null,
    val backgroundPadding: Float = 16f,
    val backgroundRadius: Float = 12f,
    val maxWidthFraction: Float = 0.85f,      // max % of canvas width
    val lineSpacing: Float      = 1.2f,
) {
    companion object {
        fun fromJSObject(js: JSObject) = TextOverlayOptions(
            text              = js.getString("text") ?: "",
            fontSize          = js.optDouble("fontSize",           48.0).toFloat(),
            color             = js.optString("color",              "#FFFFFF"),
            fontFamily        = js.optString("fontFamily",         "sans-serif"),
            bold              = js.optBoolean("bold",              false),
            italic            = js.optBoolean("italic",            false),
            alignment         = js.optString("alignment",          "center"),
            anchorX           = js.optDouble("anchorX",            0.5).toFloat(),
            anchorY           = js.optDouble("anchorY",            0.85).toFloat(),
            shadowColor       = js.optString("shadowColor",        "#00000088"),
            shadowRadius      = js.optDouble("shadowRadius",       8.0).toFloat(),
            shadowDx          = js.optDouble("shadowDx",           2.0).toFloat(),
            shadowDy          = js.optDouble("shadowDy",           2.0).toFloat(),
            backgroundColor   = if (js.has("backgroundColor")) js.getString("backgroundColor") else null,
            backgroundPadding = js.optDouble("backgroundPadding",  16.0).toFloat(),
            backgroundRadius  = js.optDouble("backgroundRadius",   12.0).toFloat(),
            maxWidthFraction  = js.optDouble("maxWidthFraction",   0.85).toFloat(),
            lineSpacing       = js.optDouble("lineSpacing",        1.2).toFloat(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  GradientOptions
// ─────────────────────────────────────────────────────────────────────────────
data class GradientOptions(
    val type: String         = "linear",     // "linear" | "radial" | "sweep"
    val colors: List<String> = listOf("#000000", "#FFFFFF"),
    val stops: List<Float>?  = null,
    val angle: Float         = 180f,         // degrees, for linear
) {
    companion object {
        fun fromJSObject(js: JSObject): GradientOptions {
            val colorsArr = js.optJSONArray("colors")
            val colorList = if (colorsArr != null)
                (0 until colorsArr.length()).map { colorsArr.getString(it) }
            else listOf("#000000", "#FFFFFF")

            val stopsArr = js.optJSONArray("stops")
            val stopList = if (stopsArr != null)
                (0 until stopsArr.length()).map { stopsArr.getDouble(it).toFloat() }
            else null

            return GradientOptions(
                type   = js.optString("type",  "linear"),
                colors = colorList,
                stops  = stopList,
                angle  = js.optDouble("angle", 180.0).toFloat(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ImageProcessor
// ─────────────────────────────────────────────────────────────────────────────
class ImageProcessor(private val context: Context) {

    companion object {
        private const val TAG         = "WallpaperPro.Img"
        private const val IMAGE_CACHE = "wallpaper_pro_cache"
        private const val CONN_TIMEOUT = 20_000
        private const val READ_TIMEOUT = 30_000
        // Max pixels to load into RAM before OOM risk
        private const val MAX_SAFE_PIXELS = 4096L * 4096L
    }

    private val imageCacheDir: File get() =
        File(context.cacheDir, IMAGE_CACHE).also { it.mkdirs() }

    private val lru by lazy {
        LRUCacheManager(context, imageCacheDir, 300L * 1024 * 1024) // 300 MB image cache
    }

    // ── public entry ─────────────────────────────────────────────────────

    fun processAndSet(
        url: String,
        target: String          = "both",
        parallax: Boolean       = false,
        parallaxMult: Float     = 1.5f,
        filter: FilterOptions   = FilterOptions(),
        quality: Int            = 95,
        cropMode: String        = "fill",
        cropX: Float            = 0.5f,
        cropY: Float            = 0.5f,
        useCache: Boolean       = true,
        textOverlay: TextOverlayOptions? = null,
    ): SetResult {
        val screen  = getScreenSize()
        val targetW = if (parallax) (screen.width * parallaxMult.coerceIn(1.1f, 3f)).toInt()
                      else screen.width
        val targetH = screen.height

        var bmp = loadBitmapSafe(url, targetW, targetH, useCache)
            ?: return SetResult(false, "Failed to load image: $url")

        bmp = cropAndScale(bmp, targetW, targetH, cropMode, cropX, cropY)
        if (!filter.isIdentity) bmp = applyFilters(bmp, filter)
        if (textOverlay != null && textOverlay.text.isNotBlank()) {
            bmp = drawTextOverlay(bmp, textOverlay)
        }

        return setWallpaper(bmp, target, quality)
    }

    /** Generate a gradient bitmap and set it as wallpaper (no image needed). */
    fun setGradientWallpaper(
        gradient: GradientOptions,
        target: String  = "both",
        quality: Int    = 95,
        textOverlay: TextOverlayOptions? = null,
    ): SetResult {
        val screen = getScreenSize()
        var bmp    = buildGradientBitmap(gradient, screen.width, screen.height)
        if (textOverlay != null && textOverlay.text.isNotBlank()) {
            bmp = drawTextOverlay(bmp, textOverlay)
        }
        return setWallpaper(bmp, target, quality)
    }

    fun downloadAndCache(url: String): String {
        val file = cacheFile(url)
        if (!file.exists()) downloadToFile(url, file, maxRetries = 3)
        lru.put(url, file)
        return file.absolutePath
    }

    fun clearCache(): Long = lru.evictAll().also {
        imageCacheDir.listFiles()?.forEach { f -> f.delete() }
    }

    // ── OOM-safe bitmap loading ───────────────────────────────────────────

    private fun loadBitmapSafe(url: String, targetW: Int, targetH: Int, useCache: Boolean): Bitmap? {
        return try {
            val file: File = if (url.startsWith("/") || url.startsWith("file://")) {
                File(url.removePrefix("file://"))
            } else {
                val cached = if (useCache) lru.get(url) else null
                if (cached != null) cached
                else {
                    val dest = cacheFile(url)
                    downloadToFile(url, dest, maxRetries = 3)
                    lru.put(url, dest)
                    dest
                }
            }

            // Step 1: measure image without decoding pixels
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)

            // Step 2: calculate safe inSampleSize
            opts.inSampleSize    = calcSampleSize(opts.outWidth, opts.outHeight, targetW, targetH)
            opts.inJustDecodeBounds = false
            opts.inPreferredConfig  = Bitmap.Config.ARGB_8888

            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM loading bitmap – returning null", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "loadBitmapSafe error: $url", e)
            null
        }
    }

    /**
     * Pick the largest inSampleSize such that the decoded image fits within
     * MAX_SAFE_PIXELS *and* is at least as large as the target dimensions.
     */
    private fun calcSampleSize(srcW: Int, srcH: Int, targetW: Int, targetH: Int): Int {
        var sample = 1
        var w = srcW; var h = srcH
        // Ensure the downsampled size covers the target (for fill/fit)
        while ((w / 2) >= targetW && (h / 2) >= targetH &&
               (w.toLong() * h) > MAX_SAFE_PIXELS) {
            w /= 2; h /= 2; sample *= 2
        }
        return sample
    }

    // ── download with retry ───────────────────────────────────────────────

    private fun downloadToFile(url: String, dest: File, maxRetries: Int = 3) {
        var attempt = 0
        var lastEx: Exception? = null
        while (attempt < maxRetries) {
            try {
                val tmp = File(dest.parent, "${dest.name}.tmp")
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONN_TIMEOUT
                    readTimeout    = READ_TIMEOUT
                    setRequestProperty("User-Agent", "WallpaperPro/1.0")
                    connect()
                }
                if (conn.responseCode != HttpURLConnection.HTTP_OK)
                    throw IOException("HTTP ${conn.responseCode}")
                conn.inputStream.use { i -> FileOutputStream(tmp).use { o -> i.copyTo(o) } }
                tmp.renameTo(dest)
                Log.d(TAG, "Downloaded ${dest.length()} bytes → ${dest.name}")
                return
            } catch (e: Exception) {
                lastEx = e
                attempt++
                val delay = (500L * (1 shl (attempt - 1))).coerceAtMost(4_000L)
                Log.w(TAG, "Download attempt $attempt failed, retry in ${delay}ms", e)
                Thread.sleep(delay)
            }
        }
        throw lastEx ?: IOException("Download failed after $maxRetries attempts")
    }

    // ── crop & scale ──────────────────────────────────────────────────────

    private fun cropAndScale(
        src: Bitmap, w: Int, h: Int, cropMode: String, cropX: Float, cropY: Float,
    ): Bitmap {
        if (src.width == w && src.height == h) return src
        return when (cropMode) {
            "fit"     -> fitBitmap(src, w, h)
            "center"  -> centerBitmap(src, w, h)
            "stretch" -> Bitmap.createScaledBitmap(src, w, h, true)
            else      -> fillBitmap(src, w, h, cropX.coerceIn(0f, 1f), cropY.coerceIn(0f, 1f))
        }
    }

    private fun fillBitmap(src: Bitmap, w: Int, h: Int, anchorX: Float, anchorY: Float): Bitmap {
        val scale  = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw     = (src.width  * scale).toInt()
        val sh     = (src.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        // anchorX/Y controls which region of the scaled image to show
        val x = ((sw - w) * anchorX).toInt().coerceAtLeast(0)
        val y = ((sh - h) * anchorY).toInt().coerceAtLeast(0)
        return Bitmap.createBitmap(scaled, x, y, w, h)
    }

    private fun fitBitmap(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale  = min(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw     = (src.width  * scale).toInt()
        val sh     = (src.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        val out    = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.BLACK)
            drawBitmap(scaled, ((w - sw) / 2f), ((h - sh) / 2f), null)
        }
        return out
    }

    private fun centerBitmap(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.BLACK)
            drawBitmap(src, ((w - src.width) / 2f), ((h - src.height) / 2f), null)
        }
        return out
    }

    // ── gradient generator ────────────────────────────────────────────────

    private fun buildGradientBitmap(g: GradientOptions, w: Int, h: Int): Bitmap {
        val out      = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas   = Canvas(out)
        val intColors = g.colors.map { safeParseColor(it, Color.BLACK) }.toIntArray()
        val stops     = g.stops?.toFloatArray()

        val shader: Shader = when (g.type) {
            "radial" -> RadialGradient(
                w / 2f, h / 2f,
                max(w, h) / 2f,
                intColors, stops, Shader.TileMode.CLAMP,
            )
            "sweep" -> SweepGradient(w / 2f, h / 2f, intColors, stops)
            else -> {  // "linear"
                val rad    = Math.toRadians(g.angle.toDouble())
                val startX = w / 2f - cos(rad).toFloat() * w / 2f
                val startY = h / 2f - sin(rad).toFloat() * h / 2f
                val endX   = w / 2f + cos(rad).toFloat() * w / 2f
                val endY   = h / 2f + sin(rad).toFloat() * h / 2f
                LinearGradient(startX, startY, endX, endY, intColors, stops, Shader.TileMode.CLAMP)
            }
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { this.shader = shader })
        return out
    }

    // ── filter pipeline ───────────────────────────────────────────────────

    fun applyFilters(src: Bitmap, f: FilterOptions): Bitmap {
        var bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        bmp = applyColorMatrix(bmp, f)
        if (f.blur > 0f) bmp = StackBlur.blur(bmp, f.blur.toInt().coerceIn(1, 25))
        val canvas = Canvas(bmp)
        if (f.vignette > 0f) drawVignette(canvas, bmp.width, bmp.height, f.vignette)
        if (f.tintColor != null) drawTint(canvas, bmp.width, bmp.height, f.tintColor, f.tintOpacity)
        if (f.opacity < 1f) {
            val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            Canvas(out).apply {
                drawColor(Color.BLACK)
                drawBitmap(bmp, 0f, 0f, Paint().apply { alpha = (f.opacity * 255).toInt() })
            }
            bmp = out
        }
        return bmp
    }

    private fun applyColorMatrix(bmp: Bitmap, f: FilterOptions): Bitmap {
        val matrix = ColorMatrix()
        if (f.saturation != 1f || f.grayscale) matrix.setSaturation(if (f.grayscale) 0f else f.saturation)
        val c = f.contrast; val b = (f.brightness - 1f) * 255f
        matrix.postConcat(ColorMatrix(floatArrayOf(
            c,0f,0f,0f,b, 0f,c,0f,0f,b, 0f,0f,c,0f,b, 0f,0f,0f,1f,0f)))
        if (f.sepia > 0f) {
            val s = f.sepia
            matrix.postConcat(ColorMatrix(floatArrayOf(
                0.393f+0.607f*(1-s), 0.769f-0.769f*(1-s), 0.189f-0.189f*(1-s),0f,0f,
                0.349f-0.349f*(1-s), 0.686f+0.314f*(1-s), 0.168f-0.168f*(1-s),0f,0f,
                0.272f-0.272f*(1-s), 0.534f-0.534f*(1-s), 0.131f+0.869f*(1-s),0f,0f,
                0f,0f,0f,1f,0f)))
        }
        if (f.temperature != 0f) {
            val t = f.temperature * 30f
            matrix.postConcat(ColorMatrix(floatArrayOf(
                1f,0f,0f,0f,if(t>0)t else 0f, 0f,1f,0f,0f,0f, 0f,0f,1f,0f,if(t<0)-t else 0f, 0f,0f,0f,1f,0f)))
        }
        if (f.hue != 0f) {
            val hm = ColorMatrix().apply { setRotate(0,f.hue); setRotate(1,f.hue); setRotate(2,f.hue) }
            matrix.postConcat(hm)
        }
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bmp, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return out
    }

    private fun drawVignette(canvas: Canvas, w: Int, h: Int, i: Float) {
        val r = max(w, h) * 0.75f
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(w/2f, h/2f, r,
                intArrayOf(Color.TRANSPARENT, Color.BLACK),
                floatArrayOf((1f-i.coerceIn(0f,1f)), 1f), Shader.TileMode.CLAMP)
        })
    }

    private fun drawTint(canvas: Canvas, w: Int, h: Int, color: String, opacity: Float) {
        try {
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
                this.color = safeParseColor(color, Color.TRANSPARENT)
                alpha       = (opacity.coerceIn(0f,1f) * 255).toInt()
            })
        } catch (_: Exception) {}
    }

    // ── text overlay ──────────────────────────────────────────────────────

    private fun drawTextOverlay(src: Bitmap, opts: TextOverlayOptions): Bitmap {
        val bmp    = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)
        val w      = bmp.width.toFloat()
        val h      = bmp.height.toFloat()

        val typeface = Typeface.create(
            opts.fontFamily,
            when {
                opts.bold && opts.italic -> Typeface.BOLD_ITALIC
                opts.bold               -> Typeface.BOLD
                opts.italic             -> Typeface.ITALIC
                else                    -> Typeface.NORMAL
            },
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface  = typeface
            textSize       = opts.fontSize
            color          = safeParseColor(opts.color, Color.WHITE)
            textAlign      = when (opts.alignment) {
                "left"  -> Paint.Align.LEFT
                "right" -> Paint.Align.RIGHT
                else    -> Paint.Align.CENTER
            }
            setShadowLayer(opts.shadowRadius, opts.shadowDx, opts.shadowDy,
                safeParseColor(opts.shadowColor, Color.TRANSPARENT))
        }

        // Word-wrap
        val maxW   = w * opts.maxWidthFraction
        val lines  = wrapText(opts.text, paint, maxW)
        val lineH  = paint.fontSpacing * opts.lineSpacing
        val blockH = lineH * lines.size

        // Anchor position
        val cx = w * opts.anchorX
        val cy = h * opts.anchorY - blockH / 2f

        // Optional background pill
        if (opts.backgroundColor != null) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = safeParseColor(opts.backgroundColor, Color.TRANSPARENT)
            }
            val pad  = opts.backgroundPadding
            val textW = lines.maxOf { paint.measureText(it) }
            val bgX  = cx - textW / 2f - pad
            val bgY  = cy - pad
            val bgR  = RectF(bgX, bgY, bgX + textW + pad * 2, bgY + blockH + pad * 2)
            canvas.drawRoundRect(bgR, opts.backgroundRadius, opts.backgroundRadius, bgPaint)
        }

        lines.forEachIndexed { i, line ->
            val x = when (opts.alignment) {
                "left"  -> cx - w * opts.maxWidthFraction / 2f
                "right" -> cx + w * opts.maxWidthFraction / 2f
                else    -> cx
            }
            canvas.drawText(line, x, cy + lineH * i + paint.textSize, paint)
        }

        return bmp
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words  = text.split(" ")
        val lines  = mutableListOf<String>()
        var cur    = StringBuilder()
        for (word in words) {
            val test = if (cur.isEmpty()) word else "$cur $word"
            if (paint.measureText(test) <= maxWidth) {
                cur = StringBuilder(test)
            } else {
                if (cur.isNotEmpty()) lines.add(cur.toString())
                cur = StringBuilder(word)
            }
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines.ifEmpty { listOf(text) }
    }

    // ── set wallpaper ─────────────────────────────────────────────────────

    private fun setWallpaper(bmp: Bitmap, target: String, quality: Int): SetResult {
        return try {
            val wm     = WallpaperManager.getInstance(context)
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val bytes  = stream.toByteArray()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val flags = when (target) {
                    "home" -> WallpaperManager.FLAG_SYSTEM
                    "lock" -> WallpaperManager.FLAG_LOCK
                    else   -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                wm.setStream(ByteArrayInputStream(bytes), null, true, flags)
            } else {
                wm.setStream(ByteArrayInputStream(bytes))
            }
            Log.d(TAG, "Wallpaper set (${bytes.size} bytes, target=$target)")
            SetResult(true, "Wallpaper set successfully (target=$target)")
        } catch (e: Exception) {
            Log.e(TAG, "setWallpaper failed", e)
            SetResult(false, "Error: ${e.message}")
        }
    }

    // ── utils ─────────────────────────────────────────────────────────────

    private fun getScreenSize(): Size {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds; Size(b.width(), b.height())
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics()
            wm.defaultDisplay.getRealMetrics(dm)
            Size(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun safeParseColor(hex: String, fallback: Int): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) { fallback }

    private fun cacheFile(url: String): File {
        val hash = md5(url)
        return File(imageCacheDir, "wp_$hash.jpg")
    }

    private fun md5(input: String): String {
        val b = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return b.joinToString("") { "%02x".format(it) }
    }

    data class Size(val width: Int, val height: Int)
}

// ─────────────────────────────────────────────────────────────────────────────
//  StackBlur (pure Kotlin, no RenderScript dependency)
// ─────────────────────────────────────────────────────────────────────────────
object StackBlur {
    fun blur(src: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceAtLeast(1)
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = bmp.width; val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        blurH(pixels, w, h, r); blurV(pixels, w, h, r)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        return bmp
    }
    private fun blurH(pix: IntArray, w: Int, h: Int, r: Int) {
        val div = 2*r+1
        for (y in 0 until h) {
            var rA=0;var gA=0;var bA=0
            for (i in -r..r) { val p=pix[y*w+i.coerceIn(0,w-1)]; rA+=Color.red(p);gA+=Color.green(p);bA+=Color.blue(p) }
            for (x in 0 until w) {
                pix[y*w+x]=Color.rgb(rA/div,gA/div,bA/div)
                val pL=pix[y*w+(x-r).coerceAtLeast(0)]; val pR=pix[y*w+(x+r+1).coerceAtMost(w-1)]
                rA+=Color.red(pR)-Color.red(pL);gA+=Color.green(pR)-Color.green(pL);bA+=Color.blue(pR)-Color.blue(pL)
            }
        }
    }
    private fun blurV(pix: IntArray, w: Int, h: Int, r: Int) {
        val div = 2*r+1
        for (x in 0 until w) {
            var rA=0;var gA=0;var bA=0
            for (i in -r..r) { val p=pix[i.coerceIn(0,h-1)*w+x]; rA+=Color.red(p);gA+=Color.green(p);bA+=Color.blue(p) }
            for (y in 0 until h) {
                pix[y*w+x]=Color.rgb(rA/div,gA/div,bA/div)
                val pT=pix[(y-r).coerceAtLeast(0)*w+x]; val pB=pix[(y+r+1).coerceAtMost(h-1)*w+x]
                rA+=Color.red(pB)-Color.red(pT);gA+=Color.green(pB)-Color.green(pT);bA+=Color.blue(pB)-Color.blue(pT)
            }
        }
    }
}
