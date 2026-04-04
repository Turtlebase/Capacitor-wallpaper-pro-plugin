// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  VideoLiveWallpaperService.kt
//
//  Extends WallpaperService to render a looping video onto the wallpaper
//  surface using ExoPlayer.  All ExoPlayer operations are pinned to the
//  Engine's internal HandlerThread — never the main UI thread.
//
//  Flow:
//    1. Service reads video path from SharedPreferences.
//    2. Engine.onCreate() → builds ExoPlayer on background thread via Handler.
//    3. Engine.onSurfaceCreated() → attaches surface to ExoPlayer.
//    4. Engine.onDestroy() → releases ExoPlayer safely on background thread.
// ─────────────────────────────────────────────────────────────────────────────
package com.wallpaperpro

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import java.io.File

@UnstableApi
class VideoLiveWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "WallpaperPro.LiveSvc"

        // SharedPreferences keys (written by plugin, read here)
        const val PREFS_NAME      = "WallpaperProPrefs"
        const val KEY_VIDEO_PATH  = "live_video_path"
        const val KEY_VIDEO_SPEED = "live_video_speed"
        const val KEY_VIDEO_MUTE  = "live_video_mute"
        const val KEY_VIDEO_LOOP  = "live_video_loop"
        const val KEY_VIDEO_SCALE = "live_video_scale"  // "fit" | "fill" | "stretch"
    }

    override fun onCreateEngine(): Engine = VideoEngine()

    // ─────────────────────────────────────────────────────────────────────────
    //  VideoEngine  –  one instance per live-wallpaper "slot"
    // ─────────────────────────────────────────────────────────────────────────
    inner class VideoEngine : Engine() {

        /** Dedicated background thread – all ExoPlayer calls run here */
        private val bgThread = HandlerThread("WallpaperProVideoThread").also { it.start() }
        private val bgHandler = Handler(bgThread.looper)

        private var player: ExoPlayer? = null
        private var surfaceHolder: SurfaceHolder? = null
        private var surfaceValid = false

        // ── lifecycle ─────────────────────────────────────────────────────

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            Log.d(TAG, "Engine.onCreate")
            bgHandler.post { buildPlayer() }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            surfaceHolder = holder
            surfaceValid  = true
            bgHandler.post { attachSurface(holder) }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceHolder = holder
            bgHandler.post { attachSurface(holder) }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            surfaceValid = false
            bgHandler.post {
                player?.clearVideoSurface()
                Log.d(TAG, "Surface detached")
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            bgHandler.post {
                if (visible) {
                    player?.play()
                } else {
                    // Pause when wallpaper not visible → save battery
                    player?.pause()
                }
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            bgHandler.post {
                releasePlayer()
                bgThread.quitSafely()
                Log.d(TAG, "Engine destroyed, player released")
            }
        }

        // ── ExoPlayer management (always on bgHandler) ────────────────────

        private fun buildPlayer() {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val videoPath = prefs.getString(KEY_VIDEO_PATH, null)
            if (videoPath.isNullOrEmpty()) {
                Log.w(TAG, "No video path stored – nothing to play")
                return
            }

            val speed     = prefs.getFloat(KEY_VIDEO_SPEED, 1.0f)
            val muted     = prefs.getBoolean(KEY_VIDEO_MUTE, true)
            val looping   = prefs.getBoolean(KEY_VIDEO_LOOP, true)

            Log.d(TAG, "Building ExoPlayer for: $videoPath")

            // ExoPlayer must be created on a Looper thread – bgThread has one
            val context = applicationContext
            val exo = ExoPlayer.Builder(context)
                .setLooper(bgThread.looper)
                .build()
                .also { player = it }

            // Media source
            val uri = if (videoPath.startsWith("http://") || videoPath.startsWith("https://")) {
                Uri.parse(videoPath)
            } else {
                Uri.fromFile(File(videoPath))
            }

            val dataFactory = DefaultDataSource.Factory(context)
            val mediaSource = ProgressiveMediaSource.Factory(dataFactory)
                .createMediaSource(MediaItem.fromUri(uri))

            exo.setMediaSource(mediaSource)
            exo.prepare()

            // Repeat mode
            exo.repeatMode = if (looping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF

            // Volume
            exo.volume = if (muted) 0f else 1f

            // Playback speed
            exo.setPlaybackSpeed(speed.coerceIn(0.25f, 4f))

            // Attach surface if already available
            if (surfaceValid) {
                surfaceHolder?.let { attachSurface(it) }
            }

            exo.playWhenReady = true

            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                }
                override fun onPlaybackStateChanged(state: Int) {
                    Log.d(TAG, "Player state: $state")
                }
            })

            Log.d(TAG, "ExoPlayer ready")
        }

        private fun attachSurface(holder: SurfaceHolder) {
            val p = player ?: return
            try {
                p.setVideoSurface(holder.surface)
                Log.d(TAG, "Surface attached to ExoPlayer")
            } catch (e: Exception) {
                Log.e(TAG, "attachSurface error", e)
            }
        }

        private fun releasePlayer() {
            try {
                player?.stop()
                player?.release()
                player = null
            } catch (e: Exception) {
                Log.e(TAG, "releasePlayer error", e)
            }
        }
    }
}
