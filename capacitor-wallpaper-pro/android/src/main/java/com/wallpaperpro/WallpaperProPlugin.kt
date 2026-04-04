// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  WallpaperProPlugin.kt  (v2)
// ─────────────────────────────────────────────────────────────────────────────
package com.wallpaperpro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.media3.common.util.UnstableApi
import com.getcapacitor.*
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@CapacitorPlugin(
    name = "WallpaperPro",
    permissions = [
        Permission(strings = [Manifest.permission.SET_WALLPAPER], alias = "wallpaper"),
        Permission(strings = [
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ], alias = "storage"),
    ]
)
class WallpaperProPlugin : Plugin() {

    companion object {
        private const val TAG              = "WallpaperPro"
        const val PREFS_NAME               = "WallpaperProPrefs"
        const val KEY_SCHEDULE             = "schedule_json"
        const val KEY_TARGET               = "schedule_target"
        const val KEY_PARALLAX             = "schedule_parallax"
        const val KEY_VIDEO_PATH           = "live_video_path"
        const val KEY_VIDEO_SPEED          = "live_video_speed"
        const val KEY_VIDEO_MUTE           = "live_video_mute"
        const val KEY_VIDEO_LOOP           = "live_video_loop"
        const val KEY_SCHEDULE_TYPE        = "schedule_type"
        const val KEY_INTERVAL_MINUTES     = "schedule_interval_minutes"
        const val CHANNEL_ID               = "wallpaper_pro_channel"
    }

    private val pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var prefs: SharedPreferences
    private val history by lazy { WallpaperHistory(context) }
    private val dlQueue by lazy { DownloadQueue(context, maxConcurrent = 2) }

    override fun load() {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createNotificationChannel()
        Log.d(TAG, "WallpaperPro v2 loaded – Android ${Build.VERSION.SDK_INT}")
    }

    // ── setWallpaper ──────────────────────────────────────────────────────

    @PluginMethod
    fun setWallpaper(call: PluginCall) {
        val url          = call.getString("url")              ?: return call.reject("url is required")
        val target       = call.getString("target", "both")!!
        val parallax     = call.getBoolean("parallax", false)!!
        val parallaxMult = call.getFloat("parallaxIntensity", 1.5f)!!
        val quality      = call.getInt("quality", 95)!!
        val cropMode     = call.getString("cropMode", "fill")!!
        val cropX        = call.getFloat("cropX", 0.5f)!!
        val cropY        = call.getFloat("cropY", 0.5f)!!
        val cache        = call.getBoolean("cache", true)!!
        val label        = call.getString("label")
        val filterObj    = call.getObject("filter") ?: JSObject()
        val filter       = FilterOptions.fromJSObject(filterObj)
        val textObj      = call.getObject("textOverlay")
        val textOverlay  = if (textObj != null) TextOverlayOptions.fromJSObject(textObj) else null

        pluginScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ImageProcessor(context).processAndSet(
                        url = url, target = target, parallax = parallax,
                        parallaxMult = parallaxMult, filter = filter,
                        quality = quality, cropMode = cropMode,
                        cropX = cropX, cropY = cropY,
                        useCache = cache, textOverlay = textOverlay,
                    )
                }
                if (result.success) {
                    history.push(HistoryEntry(url = url, target = target,
                        label = label, filterJson = filterObj.toString(),
                        timestamp = System.currentTimeMillis()))
                }
                call.resolve(JSObject().apply {
                    put("success", result.success); put("message", result.message)
                })
            } catch (e: Exception) {
                Log.e(TAG, "setWallpaper error", e)
                call.reject("Failed: ${e.message}", e)
            }
        }
    }

    // ── setGradientWallpaper ──────────────────────────────────────────────

    @PluginMethod
    fun setGradientWallpaper(call: PluginCall) {
        val gradientObj = call.getObject("gradient") ?: return call.reject("gradient is required")
        val target      = call.getString("target", "both")!!
        val quality     = call.getInt("quality", 95)!!
        val textObj     = call.getObject("textOverlay")
        val textOverlay = if (textObj != null) TextOverlayOptions.fromJSObject(textObj) else null

        pluginScope.launch {
            try {
                val gradient = GradientOptions.fromJSObject(gradientObj)
                val result   = withContext(Dispatchers.IO) {
                    ImageProcessor(context).setGradientWallpaper(gradient, target, quality, textOverlay)
                }
                call.resolve(JSObject().apply {
                    put("success", result.success); put("message", result.message)
                })
            } catch (e: Exception) {
                Log.e(TAG, "setGradientWallpaper error", e)
                call.reject("Failed: ${e.message}", e)
            }
        }
    }

    // ── setRandomWallpaper ────────────────────────────────────────────────

    @PluginMethod
    fun setRandomWallpaper(call: PluginCall) {
        val urlsArr = call.getArray("urls") ?: return call.reject("urls array is required")
        val target  = call.getString("target", "both")!!
        val parallax = call.getBoolean("parallax", false)!!
        val filterObj = call.getObject("filter") ?: JSObject()
        val filter    = FilterOptions.fromJSObject(filterObj)

        pluginScope.launch {
            try {
                val urls  = (0 until urlsArr.length()).map { urlsArr.getString(it) }
                val url   = urls.random()
                val result = withContext(Dispatchers.IO) {
                    ImageProcessor(context).processAndSet(
                        url = url, target = target, parallax = parallax, filter = filter,
                    )
                }
                if (result.success) {
                    history.push(HistoryEntry(url = url, target = target,
                        label = "Random", filterJson = null,
                        timestamp = System.currentTimeMillis()))
                }
                call.resolve(JSObject().apply {
                    put("success", result.success)
                    put("message", result.message)
                    put("selectedUrl", url)
                })
            } catch (e: Exception) {
                call.reject("Failed: ${e.message}", e)
            }
        }
    }

    // ── schedule24HourWallpapers ──────────────────────────────────────────

    @PluginMethod
    fun schedule24HourWallpapers(call: PluginCall) {
        val scheduleArr   = call.getArray("schedule")       ?: return call.reject("schedule is required")
        val target        = call.getString("target", "both")!!
        val parallax      = call.getBoolean("parallax", false)!!
        val preloadAll    = call.getBoolean("preloadAll", true)!!
        val showNotifs    = call.getBoolean("showNotifications", false)!!
        val scheduleType  = call.getString("scheduleType", "daily")!!   // "daily"|"weekly"|"interval"
        val intervalMins  = call.getInt("intervalMinutes", 60)!!

        if (scheduleArr.length() == 0) return call.reject("schedule must have ≥ 1 entry")
        if (scheduleArr.length() > 48) return call.reject("schedule can have at most 48 entries")

        prefs.edit()
            .putString(KEY_SCHEDULE, scheduleArr.toString())
            .putString(KEY_TARGET, target)
            .putBoolean(KEY_PARALLAX, parallax)
            .putString(KEY_SCHEDULE_TYPE, scheduleType)
            .putInt(KEY_INTERVAL_MINUTES, intervalMins)
            .apply()

        WallpaperScheduler.cancelAll(context)
        val entries = parseScheduleEntries(scheduleArr)
        WallpaperScheduler.scheduleAll(context, entries, target, parallax, showNotifs, scheduleType, intervalMins)

        pluginScope.launch {
            // Apply current slot immediately for daily mode
            if (scheduleType == "daily" || scheduleType == "weekly") {
                val current = WallpaperScheduler.currentEntry(entries)
                if (current != null) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val f = if (current.has("filter")) FilterOptions.fromJSObject(current.getJSObject("filter")!!) else FilterOptions()
                            ImageProcessor(context).processAndSet(url = current.getString("url")!!, target = target, parallax = current.optBoolean("parallax", parallax), filter = f)
                        }
                    }
                }
            }
            // Preload all
            if (preloadAll) {
                withContext(Dispatchers.IO) {
                    for (i in 0 until scheduleArr.length()) {
                        runCatching {
                            val url = scheduleArr.getJSONObject(i).optString("url")
                            if (url.isNotEmpty()) ImageProcessor(context).downloadAndCache(url)
                        }
                    }
                }
            }
        }

        call.resolve(JSObject().apply {
            put("success", true)
            put("message", "Schedule ($scheduleType) registered with ${entries.size} entries")
        })
    }

    // ── clearSchedule ─────────────────────────────────────────────────────

    @PluginMethod
    fun clearSchedule(call: PluginCall) {
        WallpaperScheduler.cancelAll(context)
        prefs.edit().remove(KEY_SCHEDULE).remove(KEY_TARGET).remove(KEY_PARALLAX)
            .remove(KEY_SCHEDULE_TYPE).remove(KEY_INTERVAL_MINUTES).apply()
        call.resolve(JSObject().apply { put("success", true) })
    }

    // ── getWallpaperInfo ──────────────────────────────────────────────────

    @PluginMethod
    fun getWallpaperInfo(call: PluginCall) {
        val scheduleJson  = prefs.getString(KEY_SCHEDULE, null)
        val scheduleType  = prefs.getString(KEY_SCHEDULE_TYPE, "daily") ?: "daily"
        val intervalMins  = prefs.getInt(KEY_INTERVAL_MINUTES, 60)
        val entries = if (scheduleJson != null) parseScheduleEntries(JSArray(scheduleJson)) else emptyList()
        val active  = scheduleJson != null && entries.isNotEmpty()

        val caps = JSObject().apply {
            put("canSetHomeScreen",   true)
            put("canSetLockScreen",   Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            put("canSetDual",         Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            put("supportsParallax",   true)
            put("supportsFilters",    true)
            put("supportsScheduling", true)
            put("supportsLiveWallpaper", true)
            put("supportsGradient",   true)
            put("supportsTextOverlay",true)
        }

        val cache = ImageProcessor(context).let {
            JSObject().apply {
                put("scheduleType", scheduleType)
                put("intervalMinutes", intervalMins)
            }
        }

        call.resolve(JSObject().apply {
            put("scheduleActive",   active)
            put("scheduleCount",    entries.size)
            put("scheduleType",     scheduleType)
            put("nextChangeTime",   if (active) WallpaperScheduler.nextEntry(entries)?.optString("time") else null)
            put("nextChangeLabel",  if (active) WallpaperScheduler.nextEntry(entries)?.optString("label") else null)
            put("currentLabel",     if (active) WallpaperScheduler.currentEntry(entries)?.optString("label") else null)
            put("capabilities",     caps)
        })
    }

    // ── getHistory / undoWallpaper / clearHistory ─────────────────────────

    @PluginMethod
    fun getHistory(call: PluginCall) {
        val limit = call.getInt("limit", WallpaperHistory.MAX_ENTRIES)!!
        val all   = history.getAll().take(limit)
        val arr   = JSArray().also { a -> all.forEach { e -> a.put(e.toJson()) } }
        call.resolve(JSObject().apply { put("history", arr); put("count", all.size) })
    }

    @PluginMethod
    fun undoWallpaper(call: PluginCall) {
        val prev = history.getPrevious()
        if (prev == null) {
            call.resolve(JSObject().apply { put("success", false); put("message", "No previous wallpaper in history") })
            return
        }
        pluginScope.launch {
            try {
                val filter = if (!prev.filterJson.isNullOrEmpty())
                    FilterOptions.fromJSObject(JSObject(prev.filterJson)) else FilterOptions()
                val result = withContext(Dispatchers.IO) {
                    ImageProcessor(context).processAndSet(
                        url = prev.url, target = prev.target, filter = filter,
                    )
                }
                if (result.success) {
                    history.pop()  // remove current top
                }
                call.resolve(JSObject().apply {
                    put("success", result.success)
                    put("message", result.message)
                    put("restoredUrl", prev.url)
                })
            } catch (e: Exception) {
                call.reject("Undo failed: ${e.message}", e)
            }
        }
    }

    @PluginMethod
    fun clearHistory(call: PluginCall) {
        history.clear()
        call.resolve(JSObject().apply { put("success", true) })
    }

    // ── live wallpaper ────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    @PluginMethod
    fun setLiveWallpaper(call: PluginCall) {
        val url    = call.getString("url")            ?: return call.reject("url is required")
        val target = call.getString("target", "both")!!  // "home" | "lock" | "both"
        val speed  = call.getFloat("speed",  1.0f)!!
        val muted  = call.getBoolean("mute", true)!!
        val loop   = call.getBoolean("loop", true)!!

        pluginScope.launch {
            try {
                // ── Step 1: Download video on IO thread ───────────────────
                val videoPath = withContext(Dispatchers.IO) {
                    VideoDownloadManager(context).ensureCached(url) { recv, total ->
                        if (total > 0) {
                            val pct = (recv * 100 / total).toInt()
                            notifyListeners("liveWallpaperProgress", JSObject().apply {
                                put("type", "download"); put("progress", pct); put("url", url)
                            })
                        }
                    }
                }

                // ── Step 2: Persist config for VideoLiveWallpaperService ──
                prefs.edit()
                    .putString(KEY_VIDEO_PATH,  videoPath)
                    .putFloat(KEY_VIDEO_SPEED,  speed)
                    .putBoolean(KEY_VIDEO_MUTE, muted)
                    .putBoolean(KEY_VIDEO_LOOP, loop)
                    .putString(KEY_TARGET,       target)
                    .apply()

                // ── Step 3: Apply on main thread ──────────────────────────
                withContext(Dispatchers.Main) {
                    val component = ComponentName(context, VideoLiveWallpaperService::class.java)
                    val wm        = WallpaperManager.getInstance(context)
                    var message   = ""

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        // API 24+: set per-target without showing system UI
                        try {
                            val flags = when (target) {
                                "home" -> WallpaperManager.FLAG_SYSTEM
                                "lock" -> WallpaperManager.FLAG_LOCK
                                else   -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                            }
                            // setWallpaperComponent sets the live wallpaper service directly
                            // We use the component + flag approach via reflection where available,
                            // with a graceful fallback to the system preview intent.
                            val method = runCatching {
                                wm.javaClass.getMethod("setWallpaperComponent", ComponentName::class.java, Int::class.java)
                            }.getOrNull()

                            if (method != null) {
                                // Direct set – no system UI shown (available on some API levels)
                                withContext(Dispatchers.IO) {
                                    method.invoke(wm, component, flags)
                                }
                                message = "Live wallpaper set directly (target=$target)"
                                Log.d(TAG, message)
                            } else {
                                // Fallback: open system preview
                                launchLiveWallpaperPreview(component)
                                message = "Live wallpaper preview opened. Tap 'Set Wallpaper' to confirm."
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Direct setWallpaperComponent failed, falling back to preview", e)
                            launchLiveWallpaperPreview(component)
                            message = "Live wallpaper preview opened. Tap 'Set Wallpaper' to confirm."
                        }
                    } else {
                        // Pre-API 24: open system chooser (no per-target support)
                        launchLiveWallpaperPreview(component)
                        message = "Live wallpaper preview opened (target not supported pre-Android 7)."
                    }

                    history.push(HistoryEntry(
                        url = url, target = target, label = "Live Wallpaper",
                        filterJson = null, timestamp = System.currentTimeMillis(), isLive = true,
                    ))

                    call.resolve(JSObject().apply {
                        put("success",   true)
                        put("message",   message)
                        put("videoPath", videoPath)
                        put("target",    target)
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "setLiveWallpaper error", e)
                call.reject("Failed: ${e.message}", e)
            }
        }
    }

    private fun launchLiveWallpaperPreview(component: ComponentName) {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            activity.startActivity(intent)
        } else {
            activity.startActivity(
                Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ── permissions ───────────────────────────────────────────────────────

    @PluginMethod
    override fun checkPermissions(call: PluginCall) {
        val wp = if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.SET_WALLPAPER) == PackageManager.PERMISSION_GRANTED) "granted" else "prompt"
        val st = if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) "granted" else "prompt"
        val nt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) "granted" else "prompt"
        else "granted"
        call.resolve(JSObject().apply { put("wallpaper", wp); put("storage", st); put("notifications", nt) })
    }

    @PluginMethod
    override fun requestPermissions(call: PluginCall) {
        requestPermissionForAliases(arrayOf("wallpaper", "storage"), call, "permissionsCallback")
    }

    @PermissionCallback
    private fun permissionsCallback(call: PluginCall) = checkPermissions(call)

    // ── setDepthWallpaper ─────────────────────────────────────────────────────

    @PluginMethod
    fun setDepthWallpaper(call: PluginCall) {
        val bgUrl   = call.getString("backgroundUrl") ?: return call.reject("backgroundUrl is required")
        val fgUrl   = call.getString("foregroundUrl") ?: return call.reject("foregroundUrl is required")
        val target  = call.getString("target", "both")!!
        val clockJs = call.getObject("clock")  ?: JSObject()
        val depthJs = call.getObject("depth")  ?: JSObject()

        pluginScope.launch {
            try {
                // ── 1. Download/cache both images on IO thread ────────────────
                val (bgPath, fgPath) = withContext(Dispatchers.IO) {
                    val dl = VideoDownloadManager(context)   // reuses chunked downloader
                    // Use ImageProcessor's cache for static images
                    val bg = ImageProcessor(context).downloadAndCache(bgUrl)
                    val fg = ImageProcessor(context).downloadAndCache(fgUrl)

                    notifyListeners("depthWallpaperProgress", JSObject().apply {
                        put("stage", "ready"); put("bgPath", bg); put("fgPath", fg)
                    })
                    Pair(bg, fg)
                }

                // ── 2. Build config JSON and persist ──────────────────────────
                val configJson = org.json.JSONObject().apply {
                    put("backgroundPath", bgPath)
                    put("foregroundPath", fgPath)
                    put("target",         target)
                    put("clock",  org.json.JSONObject(clockJs.toString()))
                    put("depth",  org.json.JSONObject(depthJs.toString()))
                }

                prefs.edit()
                    .putString(DepthWallpaperService.KEY_DEPTH_CFG, configJson.toString())
                    .apply()

                // ── 3. Open system live wallpaper preview on main thread ───────
                withContext(Dispatchers.Main) {
                    val component = android.content.ComponentName(context, DepthWallpaperService::class.java)
                    val wm        = android.app.WallpaperManager.getInstance(context)

                    // Try direct set (API 24+, FLAG_SYSTEM / FLAG_LOCK)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val flags = when (target) {
                            "home" -> android.app.WallpaperManager.FLAG_SYSTEM
                            "lock" -> android.app.WallpaperManager.FLAG_LOCK
                            else   -> android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK
                        }
                        val method = runCatching {
                            wm.javaClass.getMethod("setWallpaperComponent", android.content.ComponentName::class.java, Int::class.java)
                        }.getOrNull()

                        if (method != null) {
                            withContext(Dispatchers.IO) { method.invoke(wm, component, flags) }
                            call.resolve(JSObject().apply {
                                put("success",   true)
                                put("message",   "Depth wallpaper set directly (target=$target)")
                                put("bgPath",    bgPath)
                                put("fgPath",    fgPath)
                            })
                            return@withContext
                        }
                    }

                    // Fallback: system preview intent
                    val intent = android.content.Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                        putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(context.packageManager) != null) {
                        activity.startActivity(intent)
                    } else {
                        activity.startActivity(android.content.Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }

                    history.push(HistoryEntry(
                        url = bgUrl, target = target, label = "Depth Wallpaper",
                        filterJson = null, timestamp = System.currentTimeMillis(), isLive = true,
                    ))

                    call.resolve(JSObject().apply {
                        put("success", true)
                        put("message", "Depth wallpaper preview opened. Tap 'Set Wallpaper' to confirm.")
                        put("bgPath",  bgPath)
                        put("fgPath",  fgPath)
                    })
                }

            } catch (e: Exception) {
                Log.e(TAG, "setDepthWallpaper error", e)
                call.reject("Failed: ${e.message}", e)
            }
        }
    }

    // ── preloadWallpaper / clearCache / clearVideoCache ───────────────────

    @PluginMethod
    fun preloadWallpaper(call: PluginCall) {
        val url      = call.getString("url")                     ?: return call.reject("url is required")
        val priority = call.getString("priority", "normal")!!
        val dlPriority = when (priority) {
            "high" -> DownloadPriority.HIGH
            "low"  -> DownloadPriority.LOW
            else   -> DownloadPriority.NORMAL
        }
        val destFile = ImageProcessor(context).run {
            java.io.File(context.cacheDir, "wallpaper_pro_cache").also { it.mkdirs() }
            java.io.File(context.cacheDir.absolutePath + "/wallpaper_pro_cache/wp_${md5(url)}.jpg")
        }

        dlQueue.enqueue(DownloadJob(
            url       = url,
            destFile  = destFile,
            priority  = dlPriority,
            onComplete = { f ->
                call.resolve(JSObject().apply { put("success", true); put("cachedPath", f.absolutePath) })
            },
            onError = { e ->
                call.reject("Preload failed: ${e.message}")
            },
        ))
    }

    @PluginMethod
    fun clearCache(call: PluginCall) {
        pluginScope.launch {
            val freed = withContext(Dispatchers.IO) { ImageProcessor(context).clearCache() }
            call.resolve(JSObject().apply { put("success", true); put("bytesFreed", freed) })
        }
    }

    @PluginMethod
    fun clearVideoCache(call: PluginCall) {
        pluginScope.launch {
            val freed = withContext(Dispatchers.IO) { VideoDownloadManager(context).clearCache() }
            call.resolve(JSObject().apply { put("success", true); put("bytesFreed", freed) })
        }
    }

    @PluginMethod
    fun getCacheInfo(call: PluginCall) {
        pluginScope.launch {
            val imgCacheDir = java.io.File(context.cacheDir, "wallpaper_pro_cache")
            val vidCacheDir = java.io.File(context.cacheDir, "wallpaper_pro_video_cache")
            val imgSize     = imgCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            val vidSize     = vidCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            call.resolve(JSObject().apply {
                put("imageCacheBytes", imgSize)
                put("videoCacheBytes", vidSize)
                put("totalBytes",      imgSize + vidSize)
                put("imageCacheMB",    String.format("%.2f", imgSize / 1_048_576.0))
                put("videoCacheMB",    String.format("%.2f", vidSize / 1_048_576.0))
            })
        }
    }

    // ── internal helpers ──────────────────────────────────────────────────

    internal fun notifyWallpaperChanged(event: JSObject) = notifyListeners("wallpaperChanged", event)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Wallpaper Pro", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Wallpaper schedule change notifications" }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun parseScheduleEntries(arr: JSArray): List<JSObject> {
        val list = mutableListOf<JSObject>()
        for (i in 0 until arr.length()) {
            runCatching { list.add(arr.getJSONObject(i).toJSObject()) }
        }
        return list
    }

    private fun md5(input: String): String {
        val b = java.security.MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return b.joinToString("") { "%02x".format(it) }
    }
}

fun JSONObject.toJSObject(): JSObject {
    val js = JSObject()
    keys().forEach { k -> js.put(k, get(k)) }
    return js
}
