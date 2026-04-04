// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  VideoDownloadManager.kt
//
//  Downloads a remote video to the app's cache directory on a background
//  thread using a coroutine.  Supports:
//    • Progress callbacks (bytes received / total)
//    • Cancellation
//    • Cache-hit detection (skips re-download if file exists)
//    • Chunked streaming write (avoids loading entire file into RAM)
// ─────────────────────────────────────────────────────────────────────────────
package com.wallpaperpro

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

typealias ProgressCallback = (bytesReceived: Long, totalBytes: Long) -> Unit

class VideoDownloadManager(private val context: Context) {

    companion object {
        private const val TAG          = "WallpaperPro.VidDL"
        private const val CACHE_DIR    = "wallpaper_pro_video_cache"
        private const val CONN_TIMEOUT = 20_000
        private const val READ_TIMEOUT = 60_000
        private const val BUFFER_SIZE  = 64 * 1024  // 64 KB chunks
    }

    private val cacheDir: File
        get() = File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }

    // ── public API ────────────────────────────────────────────────────────

    /**
     * Ensures the video at [url] is on disk.
     * If it's already a local path, returns it immediately.
     * If it's a remote URL, downloads it (with optional [onProgress]) and
     * returns the cached file path.
     *
     * Must be called from a coroutine (already on Dispatchers.IO).
     */
    suspend fun ensureCached(
        url: String,
        onProgress: ProgressCallback? = null,
    ): String = withContext(Dispatchers.IO) {
        // Already a local file
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            require(File(url).exists()) { "Local video file not found: $url" }
            return@withContext url
        }

        val dest = cacheFile(url)
        if (dest.exists() && dest.length() > 0) {
            Log.d(TAG, "Cache hit: ${dest.name} (${dest.length()} bytes)")
            return@withContext dest.absolutePath
        }

        download(url, dest, onProgress)
        dest.absolutePath
    }

    /**
     * Delete all cached video files.  Returns bytes freed.
     */
    fun clearCache(): Long {
        var freed = 0L
        cacheDir.listFiles()?.forEach { f ->
            freed += f.length()
            f.delete()
        }
        Log.d(TAG, "Video cache cleared: $freed bytes freed")
        return freed
    }

    fun cacheFilePath(url: String): String = cacheFile(url).absolutePath

    // ── download ──────────────────────────────────────────────────────────

    private fun download(url: String, dest: File, onProgress: ProgressCallback?) {
        Log.d(TAG, "Downloading video: $url → ${dest.name}")

        val tmpFile = File(dest.parent, "${dest.name}.tmp")
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONN_TIMEOUT
                readTimeout    = READ_TIMEOUT
                setRequestProperty("User-Agent", "WallpaperPro/1.0")
                connect()
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("HTTP ${conn.responseCode} for $url")
            }

            val total = conn.contentLengthLong   // -1 if unknown
            var received = 0L
            val buf = ByteArray(BUFFER_SIZE)

            conn.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    var bytes: Int
                    while (input.read(buf).also { bytes = it } != -1) {
                        // Honour coroutine cancellation between chunks
                        if (!isActive) {
                            tmpFile.delete()
                            throw CancellationException("Download cancelled")
                        }
                        output.write(buf, 0, bytes)
                        received += bytes
                        onProgress?.invoke(received, total)
                    }
                }
            }

            // Atomic rename only if download completed
            tmpFile.renameTo(dest)
            Log.d(TAG, "Download complete: ${dest.name} (${dest.length()} bytes)")

        } catch (e: CancellationException) {
            tmpFile.delete()
            throw e
        } catch (e: Exception) {
            tmpFile.delete()
            throw RuntimeException("Video download failed: ${e.message}", e)
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private fun cacheFile(url: String): File {
        val hash = md5(url)
        // Preserve extension so ExoPlayer can sniff the format
        val ext = url.substringAfterLast('.').substringBefore('?').take(4)
            .lowercase().let { if (it in listOf("mp4","mkv","webm","mov","avi")) it else "mp4" }
        return File(cacheDir, "vid_${hash}.$ext")
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
