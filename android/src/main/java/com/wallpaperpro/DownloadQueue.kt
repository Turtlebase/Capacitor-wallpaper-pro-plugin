// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  DownloadQueue.kt
//
//  A bounded priority download queue with:
//    • Max concurrent downloads (default 2)
//    • Priority levels: HIGH / NORMAL / LOW
//    • Exponential back-off retry (up to 3 attempts)
//    • Per-job progress + completion callbacks
//    • Deduplication: same URL won't be queued twice
//    • Graceful shutdown
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
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicInteger

enum class DownloadPriority(val value: Int) {
    HIGH(0), NORMAL(1), LOW(2)
}

data class DownloadJob(
    val url: String,
    val destFile: File,
    val priority: DownloadPriority = DownloadPriority.NORMAL,
    val onProgress: ((received: Long, total: Long) -> Unit)? = null,
    val onComplete: ((file: File) -> Unit)? = null,
    val onError: ((error: Throwable) -> Unit)? = null,
) : Comparable<DownloadJob> {
    override fun compareTo(other: DownloadJob) = priority.value.compareTo(other.priority.value)
}

class DownloadQueue(
    private val context: Context,
    private val maxConcurrent: Int = 2,
) {
    companion object {
        private const val TAG          = "WallpaperPro.Queue"
        private const val MAX_RETRIES  = 3
        private const val CONN_TIMEOUT = 20_000
        private const val READ_TIMEOUT = 60_000
        private const val BUFFER_SIZE  = 64 * 1024
    }

    private val scope       = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue       = PriorityQueue<DownloadJob>()
    private val active      = AtomicInteger(0)
    private val inFlight    = mutableSetOf<String>()   // urls currently downloading
    private val queued      = mutableSetOf<String>()   // urls waiting in queue
    private val queueLock   = Any()

    // ── public API ────────────────────────────────────────────────────────

    fun enqueue(job: DownloadJob) {
        synchronized(queueLock) {
            if (job.url in inFlight || job.url in queued) {
                Log.d(TAG, "Dedup – already queued/active: ${job.url.take(60)}")
                return
            }
            queue.add(job)
            queued.add(job.url)
        }
        drain()
    }

    fun cancelAll() {
        synchronized(queueLock) {
            queue.clear()
            queued.clear()
        }
        scope.coroutineContext.cancelChildren()
        Log.d(TAG, "All downloads cancelled")
    }

    fun shutdown() {
        cancelAll()
        scope.cancel()
    }

    fun activeCount(): Int = active.get()

    // ── internal ──────────────────────────────────────────────────────────

    private fun drain() {
        while (active.get() < maxConcurrent) {
            val job = synchronized(queueLock) {
                if (queue.isEmpty()) return
                val j = queue.poll() ?: return
                queued.remove(j.url)
                inFlight.add(j.url)
                j
            }
            active.incrementAndGet()
            scope.launch { execute(job) }
        }
    }

    private suspend fun execute(job: DownloadJob) {
        var attempt = 0
        var lastError: Throwable? = null

        while (attempt < MAX_RETRIES) {
            try {
                downloadWithProgress(job)
                synchronized(queueLock) { inFlight.remove(job.url) }
                active.decrementAndGet()
                job.onComplete?.invoke(job.destFile)
                drain()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                attempt++
                val delayMs = (1_000L * (1 shl (attempt - 1))).coerceAtMost(8_000L)
                Log.w(TAG, "Attempt $attempt failed for ${job.url.take(60)} – retrying in ${delayMs}ms", e)
                delay(delayMs)
            }
        }

        synchronized(queueLock) { inFlight.remove(job.url) }
        active.decrementAndGet()
        job.onError?.invoke(lastError ?: RuntimeException("Max retries exceeded"))
        drain()
    }

    private fun downloadWithProgress(job: DownloadJob) {
        val tmp = File(job.destFile.parent, "${job.destFile.name}.tmp")
        try {
            val conn = (URL(job.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONN_TIMEOUT
                readTimeout    = READ_TIMEOUT
                setRequestProperty("User-Agent", "WallpaperPro/1.0")
                connect()
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK)
                throw RuntimeException("HTTP ${conn.responseCode}")

            val total  = conn.contentLengthLong
            var recv   = 0L
            val buf    = ByteArray(BUFFER_SIZE)

            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        recv += n
                        job.onProgress?.invoke(recv, total)
                    }
                }
            }
            tmp.renameTo(job.destFile)
            Log.d(TAG, "Downloaded ${job.destFile.name} (${job.destFile.length()} bytes)")
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }
}
