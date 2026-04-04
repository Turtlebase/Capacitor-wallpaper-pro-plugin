// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  DepthRenderer.kt
//
//  Composites three layers onto a Canvas each frame:
//    1. Background  (moves slowest → appears furthest away)
//    2. Clock       (fixed or very slight movement → appears mid-depth)
//    3. Foreground  (moves most → appears closest, creates depth pop)
//
//  Each layer bitmap is pre-scaled with an extra margin so tilting never
//  reveals black edges.  The margin = maxOffset * fgParallaxFactor.
// ─────────────────────────────────────────────────────────────────────────────
package com.wallpaperpro

import android.graphics.*

class DepthRenderer(
    private val config:        DepthWallpaperConfig,
    private val clockRenderer: ClockRenderer,
) {
    companion object {
        private const val TAG = "WallpaperPro.Depth"
    }

    // Prepared bitmaps (created once, reused every frame)
    var backgroundBitmap: Bitmap? = null
    var foregroundBitmap: Bitmap? = null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    // ── Render ────────────────────────────────────────────────────────────────

    /**
     * Draw everything onto [canvas] for one frame.
     * [rawOffsetX] and [rawOffsetY] are the smoothed sensor values
     * in the range ±[DepthLayerConfig.maxOffset].
     */
    fun render(canvas: Canvas, rawOffsetX: Float, rawOffsetY: Float) {
        val w   = canvas.width.toFloat()
        val h   = canvas.height.toFloat()
        val d   = config.depth

        // ── Layer 1: Background ───────────────────────────────────────────────
        backgroundBitmap?.let { bg ->
            val dx = rawOffsetX * d.bgParallaxFactor
            val dy = rawOffsetY * d.bgParallaxFactor
            drawLayer(canvas, bg, w, h, dx, dy)
        }

        // ── Layer 2: Clock ────────────────────────────────────────────────────
        val clockDx = rawOffsetX * d.clockParallaxFactor
        val clockDy = rawOffsetY * d.clockParallaxFactor
        clockRenderer.draw(canvas, w, h, clockDx, clockDy)

        // ── Layer 3: Foreground ───────────────────────────────────────────────
        foregroundBitmap?.let { fg ->
            val dx = rawOffsetX * d.fgParallaxFactor
            val dy = rawOffsetY * d.fgParallaxFactor
            drawLayer(canvas, fg, w, h, dx, dy)
        }
    }

    /**
     * Draw [bmp] centred on screen, shifted by [dx]/[dy].
     * The bitmap is already sized with margin so edges are never visible.
     */
    private fun drawLayer(canvas: Canvas, bmp: Bitmap, w: Float, h: Float, dx: Float, dy: Float) {
        val left = (w - bmp.width)  / 2f + dx
        val top  = (h - bmp.height) / 2f + dy
        canvas.drawBitmap(bmp, left, top, paint)
    }

    // ── Bitmap preparation ────────────────────────────────────────────────────

    /**
     * Scale a raw bitmap so it covers (screenW × screenH) PLUS a margin
     * large enough to prevent black edges at maximum parallax offset.
     *
     * [factor] is the parallax factor for this layer.
     * With factor=1.0 and maxOffset=48, the bitmap needs 96 extra pixels.
     */
    fun prepareLayerBitmap(
        raw:      Bitmap,
        screenW:  Int,
        screenH:  Int,
        factor:   Float,
    ): Bitmap {
        val maxOff  = config.depth.maxOffset
        val margin  = (maxOff * factor * 2).toInt().coerceAtLeast(0)
        val targetW = screenW + margin
        val targetH = screenH + margin

        // Scale to fill targetW × targetH (fill mode: largest dimension drives scale)
        val scaleW  = targetW.toFloat() / raw.width
        val scaleH  = targetH.toFloat() / raw.height
        val scale   = maxOf(scaleW, scaleH)

        val scaledW = (raw.width  * scale).toInt()
        val scaledH = (raw.height * scale).toInt()

        val scaled  = Bitmap.createScaledBitmap(raw, scaledW, scaledH, true)

        // Centre-crop to targetW × targetH
        val x = (scaledW - targetW) / 2
        val y = (scaledH - targetH) / 2
        return if (x == 0 && y == 0 && scaledW == targetW && scaledH == targetH) {
            scaled
        } else {
            Bitmap.createBitmap(scaled, x.coerceAtLeast(0), y.coerceAtLeast(0), targetW, targetH)
        }
    }
}
