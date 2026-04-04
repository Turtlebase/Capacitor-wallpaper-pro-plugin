// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  WallpaperSchedulerReceiver.kt  (v2)
//  Handles alarm fires + boot restore.  Adds interval-schedule cycling.
// ─────────────────────────────────────────────────────────────────────────────
package com.wallpaperpro

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import kotlinx.coroutines.*

class WallpaperSchedulerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WallpaperPro.Recv"
        private var notifId  = 1000
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WallpaperScheduler.ACTION -> handleAlarm(context, intent)
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> handleBoot(context)
        }
    }

    private fun handleAlarm(context: Context, intent: Intent) {
        val url           = intent.getStringExtra(WallpaperScheduler.EXTRA_URL)          ?: return
        val target        = intent.getStringExtra(WallpaperScheduler.EXTRA_TARGET)       ?: "both"
        val parallax      = intent.getBooleanExtra(WallpaperScheduler.EXTRA_PARALLAX,    false)
        val filterJson    = intent.getStringExtra(WallpaperScheduler.EXTRA_FILTER_JSON)
        val label         = intent.getStringExtra(WallpaperScheduler.EXTRA_LABEL)
        val showNotif     = intent.getBooleanExtra(WallpaperScheduler.EXTRA_SHOW_NOTIF,  false)
        val reqCode       = intent.getIntExtra(WallpaperScheduler.EXTRA_REQUEST_CODE,    0)
        val scheduleType  = intent.getStringExtra(WallpaperScheduler.EXTRA_SCHEDULE_TYPE) ?: "daily"
        val intervalMins  = intent.getIntExtra(WallpaperScheduler.EXTRA_INTERVAL_MINUTES, 60)
        val timeString    = intent.getStringExtra(WallpaperScheduler.EXTRA_TIME_STRING)  ?: "00:00"

        Log.d(TAG, "Alarm fired – type=$scheduleType url=${url.take(60)} label=$label")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val filter = if (!filterJson.isNullOrEmpty())
                    FilterOptions.fromJSObject(JSObject(filterJson))
                else FilterOptions()

                val result = ImageProcessor(context).processAndSet(
                    url = url, target = target, parallax = parallax, filter = filter,
                )

                // Push to history
                WallpaperHistory(context).push(HistoryEntry(
                    url = url, target = target, label = label,
                    filterJson = filterJson, timestamp = System.currentTimeMillis(),
                ))

                if (showNotif) showNotification(context, label, result.success)

                // Reschedule
                if (scheduleType == "interval") {
                    rescheduleIntervalNext(context, reqCode, target, parallax, showNotif, intervalMins)
                } else {
                    WallpaperScheduler.rescheduleEntry(
                        context, reqCode, url, target, parallax, filterJson,
                        label, showNotif, scheduleType, intervalMins, timeString,
                    )
                }
                Log.d(TAG, "Applied wallpaper: ${result.message}")
            } catch (e: Exception) {
                Log.e(TAG, "handleAlarm error", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** For interval mode: pick the next entry in the stored schedule list. */
    private fun rescheduleIntervalNext(
        context: Context, currentIndex: Int, target: String,
        parallax: Boolean, showNotif: Boolean, intervalMins: Int,
    ) {
        val prefs        = context.getSharedPreferences(WallpaperProPlugin.PREFS_NAME, Context.MODE_PRIVATE)
        val scheduleJson = prefs.getString(WallpaperProPlugin.KEY_SCHEDULE, null) ?: return
        try {
            val arr    = JSArray(scheduleJson)
            val count  = arr.length()
            val nextIdx = (currentIndex + 1) % count
            val entry  = arr.getJSONObject(nextIdx)
            val url    = entry.optString("url")
            val label  = entry.optString("label")
            val fJson  = runCatching { if (entry.has("filter")) JSObject(entry.getJSONObject("filter").toString()).toString() else null }.getOrNull()
            val ep     = entry.optBoolean("parallax", parallax)

            WallpaperScheduler.rescheduleEntry(
                context, nextIdx, url, target, ep, fJson, label, showNotif,
                "interval", intervalMins, "",
            )
        } catch (e: Exception) {
            Log.e(TAG, "rescheduleIntervalNext error", e)
        }
    }

    private fun handleBoot(context: Context) {
        Log.d(TAG, "Boot – restoring schedule")
        val prefs        = context.getSharedPreferences(WallpaperProPlugin.PREFS_NAME, Context.MODE_PRIVATE)
        val scheduleJson = prefs.getString(WallpaperProPlugin.KEY_SCHEDULE, null) ?: return
        val target       = prefs.getString(WallpaperProPlugin.KEY_TARGET, "both") ?: "both"
        val parallax     = prefs.getBoolean(WallpaperProPlugin.KEY_PARALLAX, false)
        val scheduleType = prefs.getString("schedule_type", "daily") ?: "daily"
        val intervalMins = prefs.getInt("schedule_interval_minutes", 60)
        try {
            val arr     = JSArray(scheduleJson)
            val entries = (0 until arr.length()).mapNotNull {
                runCatching { arr.getJSONObject(it).toJSObject() }.getOrNull()
            }
            WallpaperScheduler.scheduleAll(context, entries, target, parallax, false, scheduleType, intervalMins)
            Log.d(TAG, "Restored ${entries.size} alarms after boot")
        } catch (e: Exception) {
            Log.e(TAG, "Boot restore error", e)
        }
    }

    private fun showNotification(context: Context, label: String?, success: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, WallpaperProPlugin.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle(if (success) "Wallpaper changed ✓" else "Wallpaper update failed")
            .setContentText(label ?: "Scheduled wallpaper applied")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId++, notif)
    }
}
