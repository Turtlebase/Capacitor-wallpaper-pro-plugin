// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  WallpaperScheduler.kt  (v2)
//
//  Supports three schedule modes:
//    • "daily"    – HH:mm fires every 24 hours (original)
//    • "weekly"   – day-of-week + HH:mm fires once a week per entry
//    • "interval" – fires every N minutes, cycles through entries
// ─────────────────────────────────────────────────────────────────────────────
package com.wallpaperpro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.getcapacitor.JSObject
import java.util.*

object WallpaperScheduler {

    private const val TAG               = "WallpaperPro.Sched"
    const val ACTION                    = "com.wallpaperpro.APPLY_WALLPAPER"
    const val EXTRA_URL                 = "url"
    const val EXTRA_TARGET              = "target"
    const val EXTRA_PARALLAX            = "parallax"
    const val EXTRA_FILTER_JSON         = "filterJson"
    const val EXTRA_LABEL               = "label"
    const val EXTRA_SHOW_NOTIF          = "showNotif"
    const val EXTRA_REQUEST_CODE        = "requestCode"
    const val EXTRA_SCHEDULE_TYPE       = "scheduleType"
    const val EXTRA_INTERVAL_MINUTES    = "intervalMinutes"
    const val EXTRA_TIME_STRING         = "timeString"

    // ── public ────────────────────────────────────────────────────────────

    fun scheduleAll(
        context: Context,
        entries: List<JSObject>,
        target: String,
        parallax: Boolean,
        showNotifs: Boolean,
        scheduleType: String = "daily",
        intervalMinutes: Int = 60,
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        when (scheduleType) {
            "interval" -> scheduleInterval(context, am, entries, target, parallax, showNotifs, intervalMinutes)
            "weekly"   -> scheduleWeekly(context, am, entries, target, parallax, showNotifs)
            else       -> scheduleDaily(context, am, entries, target, parallax, showNotifs)
        }
    }

    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until 48) {   // up to 48 slots (weekly = 7×24, interval = arbitrary)
            val intent = Intent(ACTION).setPackage(context.packageName)
            val pi     = PendingIntent.getBroadcast(
                context, i, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) { am.cancel(pi); pi.cancel() }
        }
        Log.d(TAG, "All alarms cancelled")
    }

    // ── daily scheduling ──────────────────────────────────────────────────

    private fun scheduleDaily(
        context: Context, am: AlarmManager,
        entries: List<JSObject>, target: String,
        parallax: Boolean, showNotifs: Boolean,
    ) {
        entries.forEachIndexed { idx, entry ->
            val time   = entry.getString("time") ?: return@forEachIndexed
            val url    = entry.getString("url")  ?: return@forEachIndexed
            val label  = entry.optString("label")
            val filter = runCatching { if (entry.has("filter")) entry.getJSObject("filter").toString() else null }.getOrNull()
            val ep     = entry.optBoolean("parallax", parallax)
            val pi     = makePendingIntent(context, idx, url, target, ep, filter, label, showNotifs, "daily", 0, time)
            setExact(am, nextTriggerMs(time), pi)
            Log.d(TAG, "Daily alarm #$idx at $time")
        }
    }

    // ── weekly scheduling ─────────────────────────────────────────────────

    private fun scheduleWeekly(
        context: Context, am: AlarmManager,
        entries: List<JSObject>, target: String,
        parallax: Boolean, showNotifs: Boolean,
    ) {
        entries.forEachIndexed { idx, entry ->
            val dayOfWeek = entry.optInt("dayOfWeek", -1)   // 1=Mon … 7=Sun
            val time      = entry.getString("time") ?: "00:00"
            val url       = entry.getString("url")  ?: return@forEachIndexed
            val label     = entry.optString("label")
            val filter    = runCatching { if (entry.has("filter")) entry.getJSObject("filter").toString() else null }.getOrNull()
            val ep        = entry.optBoolean("parallax", parallax)
            val triggerMs = nextWeeklyTriggerMs(dayOfWeek, time)
            val pi        = makePendingIntent(context, idx, url, target, ep, filter, label, showNotifs, "weekly", 0, time)
            setExact(am, triggerMs, pi)
            Log.d(TAG, "Weekly alarm #$idx – day=$dayOfWeek at $time")
        }
    }

    // ── interval scheduling ───────────────────────────────────────────────

    private fun scheduleInterval(
        context: Context, am: AlarmManager,
        entries: List<JSObject>, target: String,
        parallax: Boolean, showNotifs: Boolean,
        intervalMinutes: Int,
    ) {
        if (entries.isEmpty()) return
        // Only one alarm fires at a time; the receiver cycles through entries.
        val firstEntry = entries[0]
        val url   = firstEntry.getString("url") ?: return
        val label = firstEntry.optString("label")
        val filter = runCatching { if (firstEntry.has("filter")) firstEntry.getJSObject("filter").toString() else null }.getOrNull()
        val triggerMs = System.currentTimeMillis() + intervalMinutes * 60_000L
        val pi    = makePendingIntent(context, 0, url, target, parallax, filter, label, showNotifs, "interval", intervalMinutes, "")
        setExact(am, triggerMs, pi)
        Log.d(TAG, "Interval alarm every ${intervalMinutes}min")
    }

    // ── reschedule single entry after firing ──────────────────────────────

    fun rescheduleEntry(
        context: Context,
        requestCode: Int,
        url: String,
        target: String,
        parallax: Boolean,
        filterJson: String?,
        label: String?,
        showNotif: Boolean,
        scheduleType: String,
        intervalMinutes: Int,
        timeString: String,
        dayOfWeek: Int = -1,
    ) {
        val am       = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextMs   = when (scheduleType) {
            "weekly"   -> nextWeeklyTriggerMs(dayOfWeek, timeString, plusWeeks = 1)
            "interval" -> System.currentTimeMillis() + intervalMinutes * 60_000L
            else       -> nextTriggerMs(timeString, plusDays = 1)
        }
        val pi = makePendingIntent(context, requestCode, url, target, parallax, filterJson,
            label, showNotif, scheduleType, intervalMinutes, timeString)
        setExact(am, nextMs, pi)
        Log.d(TAG, "Rescheduled #$requestCode ($scheduleType) nextMs=$nextMs")
    }

    // ── time helpers ──────────────────────────────────────────────────────

    fun nextTriggerMs(timeHHmm: String, plusDays: Int = 0): Long {
        val p    = timeHHmm.split(":")
        val cal  = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, p.getOrNull(0)?.toIntOrNull() ?: 0)
            set(Calendar.MINUTE,      p.getOrNull(1)?.toIntOrNull() ?: 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, plusDays)
        }
        if (plusDays == 0 && cal.timeInMillis <= System.currentTimeMillis())
            cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun nextWeeklyTriggerMs(dayOfWeek: Int, timeHHmm: String, plusWeeks: Int = 0): Long {
        val p   = timeHHmm.split(":")
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, p.getOrNull(0)?.toIntOrNull() ?: 0)
            set(Calendar.MINUTE,      p.getOrNull(1)?.toIntOrNull() ?: 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.WEEK_OF_YEAR, plusWeeks)
        }
        if (dayOfWeek in 1..7) {
            // Map 1=Mon..7=Sun → Calendar.MONDAY..Calendar.SUNDAY
            val calDay = if (dayOfWeek == 7) Calendar.SUNDAY else dayOfWeek + 1
            cal.set(Calendar.DAY_OF_WEEK, calDay)
            if (cal.timeInMillis <= System.currentTimeMillis())
                cal.add(Calendar.WEEK_OF_YEAR, 1)
        } else {
            if (cal.timeInMillis <= System.currentTimeMillis())
                cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun currentEntry(entries: List<JSObject>): JSObject? {
        if (entries.isEmpty()) return null
        val now  = Calendar.getInstance()
        val nowM = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val sorted = entries.sortedBy { e ->
            val t = e.getString("time") ?: "00:00"
            val p = t.split(":")
            (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
        }
        return sorted.lastOrNull { e ->
            val t = e.getString("time") ?: "00:00"
            val p = t.split(":")
            (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0) <= nowM
        } ?: sorted.lastOrNull()
    }

    fun nextEntry(entries: List<JSObject>): JSObject? {
        if (entries.isEmpty()) return null
        val now  = Calendar.getInstance()
        val nowM = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val sorted = entries.sortedBy { e ->
            val t = e.getString("time") ?: "00:00"
            val p = t.split(":")
            (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
        }
        return sorted.firstOrNull { e ->
            val t = e.getString("time") ?: "00:00"
            val p = t.split(":")
            (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0) > nowM
        } ?: sorted.firstOrNull()
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun makePendingIntent(
        context: Context, code: Int, url: String, target: String,
        parallax: Boolean, filterJson: String?, label: String?,
        showNotif: Boolean, scheduleType: String, intervalMinutes: Int, timeString: String,
    ): PendingIntent {
        val intent = Intent(ACTION).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_URL, url); putExtra(EXTRA_TARGET, target)
            putExtra(EXTRA_PARALLAX, parallax); putExtra(EXTRA_FILTER_JSON, filterJson)
            putExtra(EXTRA_LABEL, label); putExtra(EXTRA_SHOW_NOTIF, showNotif)
            putExtra(EXTRA_REQUEST_CODE, code); putExtra(EXTRA_SCHEDULE_TYPE, scheduleType)
            putExtra(EXTRA_INTERVAL_MINUTES, intervalMinutes); putExtra(EXTRA_TIME_STRING, timeString)
        }
        return PendingIntent.getBroadcast(context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setExact(am: AlarmManager, triggerMs: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        else
            am.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi)
    }
}
