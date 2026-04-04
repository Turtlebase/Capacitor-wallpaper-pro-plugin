// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  LRUCacheManager.kt
//
//  Disk-backed LRU cache for wallpaper images and videos.
//  • Enforces a configurable max size (default 500 MB)
//  • Evicts least-recently-used files when limit is exceeded
//  • Thread-safe via a single lock
//  • Survives app restarts (metadata persisted to SharedPreferences as JSON)
// ─────────────────────────────────────────────────────────────────────────────
package com.wallpaperpro

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.LinkedHashMap

data class CacheEntry(
    val key: String,
    val filePath: String,
    var lastAccessed: Long,
    val sizeBytes: Long,
)

class LRUCacheManager(
    private val context: Context,
    private val cacheDir: File,
    private val maxSizeBytes: Long = DEFAULT_MAX_BYTES,
) {
    companion object {
        private const val TAG             = "WallpaperPro.LRU"
        private const val DEFAULT_MAX_BYTES = 500L * 1024 * 1024   // 500 MB
        private const val PREFS_KEY       = "lru_cache_meta"
        private const val PREFS_NAME      = "WallpaperProLRU"
    }

    private val lock   = Any()
    /** Insertion-order map; eldest = LRU */
    private val map    = LinkedHashMap<String, CacheEntry>(64, 0.75f, true)
    private var totalBytes = 0L

    init {
        cacheDir.mkdirs()
        loadMeta()
        // Remove entries whose files have been deleted externally
        synchronized(lock) {
            val orphans = map.values.filter { !File(it.filePath).exists() }
            orphans.forEach { remove(it.key) }
        }
    }

    // ── public API ────────────────────────────────────────────────────────

    /** Register a newly written file in the cache. */
    fun put(key: String, file: File) {
        synchronized(lock) {
            // Update if already present
            map[key]?.let {
                totalBytes -= it.sizeBytes
                map.remove(key)
            }
            val entry = CacheEntry(key, file.absolutePath, System.currentTimeMillis(), file.length())
            map[key] = entry
            totalBytes += entry.sizeBytes
            evictIfNeeded()
            saveMeta()
        }
        Log.d(TAG, "PUT $key  total=${totalBytes / 1024}KB  limit=${maxSizeBytes / 1024}KB")
    }

    /** Return the cached file if it exists, touching its access time. */
    fun get(key: String): File? {
        synchronized(lock) {
            val entry = map[key] ?: return null
            val file  = File(entry.filePath)
            if (!file.exists()) { remove(key); return null }
            entry.lastAccessed = System.currentTimeMillis()
            saveMeta()
            return file
        }
    }

    /** Explicitly remove one entry. */
    fun remove(key: String) {
        synchronized(lock) {
            val entry = map.remove(key) ?: return
            totalBytes -= entry.sizeBytes
            File(entry.filePath).delete()
        }
    }

    /** Delete everything. Returns bytes freed. */
    fun evictAll(): Long {
        synchronized(lock) {
            var freed = 0L
            map.values.forEach { e ->
                val f = File(e.filePath)
                freed += f.length()
                f.delete()
            }
            map.clear()
            totalBytes = 0L
            saveMeta()
            return freed
        }
    }

    fun currentSizeBytes(): Long = synchronized(lock) { totalBytes }

    fun entryCount(): Int = synchronized(lock) { map.size }

    // ── eviction ──────────────────────────────────────────────────────────

    private fun evictIfNeeded() {
        // LinkedHashMap with accessOrder=true → iterator goes eldest-first
        val iter = map.iterator()
        while (totalBytes > maxSizeBytes && iter.hasNext()) {
            val (_, entry) = iter.next()
            val file = File(entry.filePath)
            val freed = file.length()
            file.delete()
            totalBytes -= freed
            iter.remove()
            Log.d(TAG, "Evicted ${entry.key}  freed=${freed / 1024}KB")
        }
    }

    // ── persistence ───────────────────────────────────────────────────────

    private fun saveMeta() {
        try {
            val arr = org.json.JSONArray()
            map.values.forEach { e ->
                arr.put(JSONObject().apply {
                    put("key",  e.key)
                    put("path", e.filePath)
                    put("ts",   e.lastAccessed)
                    put("size", e.sizeBytes)
                })
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(PREFS_KEY, arr.toString()).apply()
        } catch (ex: Exception) {
            Log.w(TAG, "saveMeta error", ex)
        }
    }

    private fun loadMeta() {
        try {
            val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREFS_KEY, null) ?: return
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val entry = CacheEntry(
                    key          = o.getString("key"),
                    filePath     = o.getString("path"),
                    lastAccessed = o.getLong("ts"),
                    sizeBytes    = o.getLong("size"),
                )
                map[entry.key]  = entry
                totalBytes     += entry.sizeBytes
            }
            Log.d(TAG, "Loaded ${map.size} cache entries  total=${totalBytes / 1024}KB")
        } catch (ex: Exception) {
            Log.w(TAG, "loadMeta error", ex)
        }
    }
}
