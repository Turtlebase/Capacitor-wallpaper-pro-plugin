// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  WallpaperHistory.kt
//
//  Persists a ring-buffer of the last MAX_ENTRIES wallpaper changes.
//  Each entry records: url, target, label, filter snapshot, timestamp.
//  Used by the JS getHistory() / undoLastWallpaper() API.
// ─────────────────────────────────────────────────────────────────────────────
package com.wallpaperpro

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class HistoryEntry(
    val url: String,
    val target: String,
    val label: String?,
    val filterJson: String?,
    val timestamp: Long,
    val isLive: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("url",        url)
        put("target",     target)
        put("label",      label ?: "")
        put("filterJson", filterJson ?: "")
        put("timestamp",  timestamp)
        put("isLive",     isLive)
        put("date",       SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(timestamp)))
    }

    companion object {
        fun fromJson(o: JSONObject) = HistoryEntry(
            url        = o.optString("url"),
            target     = o.optString("target", "both"),
            label      = o.optString("label").ifEmpty { null },
            filterJson = o.optString("filterJson").ifEmpty { null },
            timestamp  = o.optLong("timestamp", System.currentTimeMillis()),
            isLive     = o.optBoolean("isLive", false),
        )
    }
}

class WallpaperHistory(private val context: Context) {

    companion object {
        private const val TAG        = "WallpaperPro.History"
        private const val PREFS_NAME = "WallpaperProHistory"
        private const val KEY        = "history_json"
        const val MAX_ENTRIES        = 50
    }

    // ── public API ────────────────────────────────────────────────────────

    fun push(entry: HistoryEntry) {
        val list = load().toMutableList()
        // Remove duplicate URL if already in history
        list.removeAll { it.url == entry.url && it.isLive == entry.isLive }
        list.add(0, entry)   // newest first
        if (list.size > MAX_ENTRIES) list.subList(MAX_ENTRIES, list.size).clear()
        save(list)
        Log.d(TAG, "History: ${list.size} entries, latest=${entry.url.take(60)}")
    }

    fun getAll(): List<HistoryEntry> = load()

    /** Remove the most-recent entry and return it. */
    fun pop(): HistoryEntry? {
        val list = load().toMutableList()
        if (list.isEmpty()) return null
        val head = list.removeAt(0)
        save(list)
        return head
    }

    /** Return the previous entry (index 1) without removing anything. */
    fun getPrevious(): HistoryEntry? = load().getOrNull(1)

    fun clear() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }

    // ── persistence ───────────────────────────────────────────────────────

    private fun load(): List<HistoryEntry> {
        return try {
            val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return emptyList()
            val arr = JSONArray(raw)
            (0 until arr.length()).map { HistoryEntry.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "load error", e)
            emptyList()
        }
    }

    private fun save(list: List<HistoryEntry>) {
        val arr = JSONArray().also { a -> list.forEach { a.put(it.toJson()) } }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
