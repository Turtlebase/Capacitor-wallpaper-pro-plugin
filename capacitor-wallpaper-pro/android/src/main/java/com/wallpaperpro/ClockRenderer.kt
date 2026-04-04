// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  ClockRenderer.kt
//
//  Renders the clock layer onto a Canvas.
//  Supports 6 styles: DIGITAL, ANALOG, MINIMAL, NEON, RETRO, WORD
//  Supports 6 animations: NONE, PULSE, GLOW, FADE, SLIDE, FLIP
//
//  Called from DepthWallpaperService every frame (60fps from Choreographer)
//  and every second (from the clock tick handler).
// ─────────────────────────────────────────────────────────────────────────────
package com.wallpaperpro

import android.graphics.*
import android.graphics.Camera
import android.os.SystemClock
import java.util.Calendar
import kotlin.math.*

class ClockRenderer(private val config: ClockConfig) {

    companion object {
        private val WORD_HOURS = arrayOf(
            "twelve","one","two","three","four","five","six",
            "seven","eight","nine","ten","eleven","twelve"
        )
        // Segments for 7-segment display: each digit encodes which of a-g are on
        // Bit order: a=6, b=5, c=4, d=3, e=2, f=1, g=0
        private val SEGMENTS = intArrayOf(
            0b1110111, // 0: a,b,c,d,e,f
            0b0010010, // 1: b,c
            0b1011101, // 2: a,b,d,e,g
            0b1011011, // 3: a,b,c,d,g
            0b0111010, // 4: b,c,f,g
            0b1101011, // 5: a,c,d,f,g
            0b1101111, // 6: a,c,d,e,f,g
            0b1010010, // 7: a,b,c
            0b1111111, // 8: all
            0b1111011, // 9: a,b,c,d,f,g
        )
    }

    // ── Paint pool ────────────────────────────────────────────────────────────

    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = safeColor(config.color, Color.WHITE)
        textAlign = Paint.Align.CENTER
        typeface  = Typeface.DEFAULT_BOLD
    }

    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = safeColor(config.secondaryColor, Color.LTGRAY)
        textAlign = Paint.Align.CENTER
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color     = safeColor(config.accentColor, Color.RED)
        strokeCap = Paint.Cap.ROUND
        style     = Paint.Style.STROKE
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = safeColor(config.shadowColor, Color.TRANSPARENT)
    }

    // ── Animation state ───────────────────────────────────────────────────────

    /** Continuously-advancing phase for ambient animations (0..2π) */
    private var animPhase = 0.0
    private var lastFrameMs = SystemClock.elapsedRealtime()

    /** For SLIDE: fraction 0→1 of the slide-in animation */
    private var slideProgress   = 1.0f   // 1 = done, starts at 0 on tick
    private var slidePrevSecond = -1
    private var slideDuration   = 250L   // ms

    /** For FLIP: fraction 0→1 */
    private var flipProgress  = 1.0f
    private var flipPrevDigit = ""
    private var flipNextDigit = ""

    /** For FADE */
    private var fadeAlpha      = 1.0f
    private var fadePrevMinute = -1

    // ── Main entry ────────────────────────────────────────────────────────────

    /**
     * Called every frame (~60fps).
     * [cx] and [cy] are the centre pixel coordinates of the clock.
     * [offsetX] / [offsetY] are the parallax shift applied to the clock layer.
     */
    fun draw(canvas: Canvas, screenW: Float, screenH: Float, offsetX: Float, offsetY: Float) {
        val now     = Calendar.getInstance()
        val hour    = now.get(Calendar.HOUR_OF_DAY)
        val hour12  = now.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val minute  = now.get(Calendar.MINUTE)
        val second  = now.get(Calendar.SECOND)
        val millis  = now.get(Calendar.MILLISECOND)
        val isAm    = hour < 12

        val nowMs   = SystemClock.elapsedRealtime()
        val dt      = (nowMs - lastFrameMs).coerceAtLeast(1L)
        lastFrameMs = nowMs

        // Advance continuous phase
        animPhase += dt * 0.003   // ~3 radians/second
        if (animPhase > 2 * PI) animPhase -= 2 * PI

        // Centre of clock
        val cx = screenW * config.position.x + offsetX
        val cy = screenH * config.position.y + offsetY

        // Base alpha
        val baseAlpha = (config.opacity * 255).toInt().coerceIn(0, 255)

        // Compute animation modifiers
        val animScale  = computePulseScale()
        val animAlpha  = computeFadeAlpha(minute, baseAlpha)
        val glowRadius = computeGlowRadius()

        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(animScale, animScale)

        mainPaint.alpha      = animAlpha
        secondaryPaint.alpha = animAlpha

        // Apply shadow / glow
        if (config.shadow) {
            mainPaint.setShadowLayer(glowRadius, 0f, 2f, safeColor(config.shadowColor, Color.TRANSPARENT))
        } else {
            mainPaint.clearShadowLayer()
        }

        when (config.style) {
            ClockStyle.DIGITAL  -> drawDigital(canvas, hour, hour12, minute, second, isAm, millis)
            ClockStyle.ANALOG   -> drawAnalog(canvas, hour, minute, second, millis, glowRadius)
            ClockStyle.MINIMAL  -> drawMinimal(canvas, hour, hour12, minute, isAm)
            ClockStyle.NEON     -> drawNeon(canvas, hour, hour12, minute, second, isAm, glowRadius)
            ClockStyle.RETRO    -> drawRetro(canvas, hour, hour12, minute, second, isAm)
            ClockStyle.WORD     -> drawWord(canvas, hour12, minute, screenW)
        }

        canvas.restore()
    }

    // ── DIGITAL ───────────────────────────────────────────────────────────────

    private fun drawDigital(canvas: Canvas, hour: Int, hour12: Int, minute: Int, second: Int, isAm: Boolean, millis: Int) {
        val fs  = config.fontSize
        val h   = if (config.format == TimeFormat.H12) hour12 else hour
        val slideOff = computeSlideOffset(second, fs)

        mainPaint.textSize = fs

        // Time string with slide animation
        val timeStr = "%02d:%02d".format(h, minute)
        canvas.save()
        canvas.clipRect(-fs * 3, -fs, fs * 3, fs * 0.6f)
        canvas.translate(0f, slideOff)
        canvas.drawText(timeStr, 0f, 0f, mainPaint)
        canvas.restore()

        // Seconds
        if (config.showSeconds) {
            secondaryPaint.textSize = fs * 0.38f
            canvas.drawText(":%02d".format(second), fs * 1.7f, -fs * 0.25f, secondaryPaint)
        }

        // AM/PM
        if (config.format == TimeFormat.H12 && config.showAmPm) {
            secondaryPaint.textSize = fs * 0.28f
            canvas.drawText(if (isAm) "AM" else "PM", fs * 1.7f, fs * 0.15f, secondaryPaint)
        }

        // Date
        if (config.showDate) {
            secondaryPaint.textSize = fs * 0.3f
            val now = Calendar.getInstance()
            val dayNames = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
            val monNames = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
            val dateStr = "${dayNames[now.get(Calendar.DAY_OF_WEEK)-1]}, " +
                "${monNames[now.get(Calendar.MONTH)]} ${now.get(Calendar.DAY_OF_MONTH)}"
            canvas.drawText(dateStr, 0f, fs * 0.75f, secondaryPaint)
        }
    }

    // ── ANALOG ────────────────────────────────────────────────────────────────

    private fun drawAnalog(canvas: Canvas, hour: Int, minute: Int, second: Int, millis: Int, glowR: Float) {
        val radius = config.fontSize * 1.6f
        val sw     = config.strokeWidth

        // Outer ring
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color  = safeColor(config.color, Color.WHITE)
            style  = Paint.Style.STROKE
            strokeWidth = sw
            alpha  = 180
            if (config.shadow) setShadowLayer(glowR, 0f, 0f, safeColor(config.shadowColor, Color.TRANSPARENT))
        }
        canvas.drawCircle(0f, 0f, radius, ringPaint)

        // Tick marks
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = safeColor(config.secondaryColor, Color.LTGRAY)
            style = Paint.Style.STROKE
            alpha = mainPaint.alpha
        }
        for (i in 0 until 60) {
            val angle  = (i * 6f) * PI.toFloat() / 180f
            val isHour = i % 5 == 0
            tickPaint.strokeWidth = if (isHour) sw * 2 else sw * 0.8f
            val inner = radius - (if (isHour) radius * 0.14f else radius * 0.07f)
            canvas.drawLine(
                sin(angle) * inner,   -cos(angle) * inner,
                sin(angle) * radius,  -cos(angle) * radius,
                tickPaint
            )
        }

        // Smooth second hand angle (includes millis for smooth sweep)
        val secAngle   = (second + millis / 1000f) * 6f
        val minAngle   = (minute + second / 60f) * 6f
        val hourAngle  = ((hour % 12) + minute / 60f) * 30f

        // Hour hand
        drawHand(canvas, hourAngle, radius * 0.52f, sw * 3.5f, safeColor(config.color, Color.WHITE), glowR)
        // Minute hand
        drawHand(canvas, minAngle, radius * 0.78f, sw * 2.5f, safeColor(config.color, Color.WHITE), glowR)
        // Second hand
        drawSecondHand(canvas, secAngle, radius * 0.88f, sw * 1.2f, safeColor(config.accentColor, Color.RED), glowR)

        // Centre dot
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = safeColor(config.accentColor, Color.RED)
            if (config.shadow) setShadowLayer(glowR * 0.5f, 0f, 0f, safeColor(config.shadowColor, Color.TRANSPARENT))
        }
        canvas.drawCircle(0f, 0f, sw * 2.5f, dotPaint)
    }

    private fun drawHand(canvas: Canvas, angleDeg: Float, length: Float, sw: Float, color: Int, glowR: Float) {
        val rad = angleDeg * PI.toFloat() / 180f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color   = color
            strokeWidth  = sw
            strokeCap    = Paint.Cap.ROUND
            style        = Paint.Style.STROKE
            alpha        = mainPaint.alpha
            if (config.shadow) setShadowLayer(glowR * 0.6f, 0f, 0f, safeColor(config.shadowColor, Color.TRANSPARENT))
        }
        canvas.drawLine(0f, 0f, sin(rad) * length, -cos(rad) * length, paint)
    }

    private fun drawSecondHand(canvas: Canvas, angleDeg: Float, length: Float, sw: Float, color: Int, glowR: Float) {
        val rad = angleDeg * PI.toFloat() / 180f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color  = color
            strokeWidth = sw
            strokeCap   = Paint.Cap.ROUND
            style       = Paint.Style.STROKE
            alpha       = mainPaint.alpha
            if (config.shadow) setShadowLayer(glowR * 0.8f, 0f, 0f, safeColor(config.shadowColor, Color.TRANSPARENT))
        }
        // Counterbalance tail
        canvas.drawLine(
            -sin(rad) * length * 0.2f, cos(rad) * length * 0.2f,
             sin(rad) * length, -cos(rad) * length,
            paint
        )
    }

    // ── MINIMAL ───────────────────────────────────────────────────────────────

    private fun drawMinimal(canvas: Canvas, hour: Int, hour12: Int, minute: Int, isAm: Boolean) {
        val fs = config.fontSize * 1.2f
        val h  = if (config.format == TimeFormat.H12) hour12 else hour
        mainPaint.textSize = fs
        mainPaint.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        canvas.drawText("%02d:%02d".format(h, minute), 0f, 0f, mainPaint)
        if (config.format == TimeFormat.H12 && config.showAmPm) {
            secondaryPaint.textSize = fs * 0.25f
            secondaryPaint.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            canvas.drawText(if (isAm) "AM" else "PM", 0f, fs * 0.6f, secondaryPaint)
        }
        mainPaint.typeface = Typeface.DEFAULT_BOLD  // reset
    }

    // ── NEON ──────────────────────────────────────────────────────────────────

    private fun drawNeon(canvas: Canvas, hour: Int, hour12: Int, minute: Int, second: Int, isAm: Boolean, glowR: Float) {
        val fs = config.fontSize
        val h  = if (config.format == TimeFormat.H12) hour12 else hour
        val clr= safeColor(config.color, Color.WHITE)

        // Draw multiple glow passes (outer → inner → solid)
        for (pass in 3 downTo 0) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color       = clr
                textSize    = fs
                textAlign   = Paint.Align.CENTER
                typeface    = Typeface.MONOSPACE
                alpha       = mainPaint.alpha
                if (pass > 0) setShadowLayer(glowR * pass * 0.8f, 0f, 0f, clr)
            }
            canvas.drawText("%02d:%02d".format(h, minute), 0f, 0f, p)
        }

        // Colon pulse
        val colonAlpha = ((sin(animPhase * 2) * 0.5 + 0.5) * 255).toInt()
        val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = clr; textSize = fs; textAlign = Paint.Align.CENTER
            alpha     = colonAlpha
            typeface  = Typeface.MONOSPACE
        }
        canvas.drawText(":", 0f, 0f, cp)

        if (config.showSeconds) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color    = safeColor(config.accentColor, Color.RED)
                textSize = fs * 0.38f; textAlign = Paint.Align.CENTER
                alpha    = mainPaint.alpha
                setShadowLayer(glowR * 0.6f, 0f, 0f, safeColor(config.accentColor, Color.RED))
            }
            canvas.drawText(":%02d".format(second), fs * 1.6f, -fs * 0.25f, sp)
        }
    }

    // ── RETRO (7-segment) ─────────────────────────────────────────────────────

    private fun drawRetro(canvas: Canvas, hour: Int, hour12: Int, minute: Int, second: Int, isAm: Boolean) {
        val fs   = config.fontSize * 0.9f
        val segW = fs * 0.12f      // segment thickness
        val dW   = fs * 0.5f       // digit width
        val dH   = fs * 0.9f       // digit height
        val gap  = fs * 0.12f      // gap between digits
        val clr  = safeColor(config.color, Color.WHITE)
        val dimClr = Color.argb(40, Color.red(clr), Color.green(clr), Color.blue(clr))

        val h   = if (config.format == TimeFormat.H12) hour12 else hour
        val str = "%02d%02d".format(h, minute)
        val digits = str.map { it - '0' }

        // Total width: 4 digits + 1 colon + gaps
        val totalW = dW * 4 + gap * 5 + segW
        canvas.save()
        canvas.translate(-totalW / 2f, -dH / 2f)

        for ((i, digit) in digits.withIndex()) {
            val extraGap = if (i == 2) segW * 1.5f else 0f  // colon gap
            val x = i * (dW + gap) + gap + extraGap
            drawSevenSegDigit(canvas, digit, x, 0f, dW, dH, segW, clr, dimClr)
        }

        // Colon dots
        val colonX  = dW * 2 + gap * 2.5f + gap
        val dotSize = segW * 1.2f
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = clr; alpha = mainPaint.alpha
            if (config.shadow) setShadowLayer(config.shadowRadius * 0.5f, 0f, 0f, safeColor(config.shadowColor, Color.TRANSPARENT))
        }
        val colonAlpha = if ((second % 2) == 0) 255 else 80
        dotPaint.alpha = (mainPaint.alpha * colonAlpha / 255)
        canvas.drawRect(colonX, dH * 0.3f, colonX + dotSize, dH * 0.3f + dotSize, dotPaint)
        canvas.drawRect(colonX, dH * 0.6f, colonX + dotSize, dH * 0.6f + dotSize, dotPaint)

        canvas.restore()

        // Date below
        if (config.showDate) {
            val now = Calendar.getInstance()
            val monNames = arrayOf("JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC")
            secondaryPaint.textSize = fs * 0.28f
            secondaryPaint.typeface = Typeface.MONOSPACE
            canvas.drawText("${monNames[now.get(Calendar.MONTH)]} ${now.get(Calendar.DAY_OF_MONTH)}, ${now.get(Calendar.YEAR)}", 0f, dH * 0.7f, secondaryPaint)
        }
    }

    private fun drawSevenSegDigit(
        canvas: Canvas, digit: Int,
        x: Float, y: Float, w: Float, h: Float, sw: Float,
        onColor: Int, offColor: Int,
    ) {
        val mask = SEGMENTS[digit.coerceIn(0, 9)]
        val pad  = sw * 0.3f
        val p    = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.SQUARE; alpha = mainPaint.alpha }
        if (config.shadow) p.setShadowLayer(config.shadowRadius * 0.4f, 0f, 0f, safeColor(config.shadowColor, Color.TRANSPARENT))

        // a = top, b = top-right, c = bottom-right, d = bottom, e = bottom-left, f = top-left, g = middle
        fun seg(idx: Int, draw: () -> Unit) {
            p.color = if ((mask shr idx) and 1 == 1) onColor else offColor
            draw()
        }
        fun hline(ly: Float) = canvas.drawLine(x + pad + sw, y + ly, x + w - pad, y + ly, p)
        fun vline(lx: Float, ly: Float, ly2: Float) = canvas.drawLine(x + lx, y + ly + pad, x + lx, y + ly2 - pad, p)

        p.strokeWidth = sw
        p.style = Paint.Style.STROKE
        seg(6) { hline(0f) }           // a: top
        seg(5) { vline(w, 0f, h/2) }  // b: top-right
        seg(4) { vline(w, h/2, h) }   // c: bottom-right
        seg(3) { hline(h) }            // d: bottom
        seg(2) { vline(0f, h/2, h) }  // e: bottom-left
        seg(1) { vline(0f, 0f, h/2) } // f: top-left
        seg(0) { hline(h/2) }          // g: middle
    }

    // ── WORD ──────────────────────────────────────────────────────────────────

    private fun drawWord(canvas: Canvas, hour12: Int, minute: Int, screenW: Float) {
        val line1: String
        val line2: String
        val displayHour: Int

        val mins = minute % 5
        val roundedMin = ((minute + 2) / 5) * 5  // nearest 5 minutes

        when {
            roundedMin == 0 || roundedMin == 60 -> {
                val h = if (roundedMin == 60) (hour12 % 12) + 1 else hour12
                line1 = WORD_HOURS[h.coerceIn(0, 12)]
                line2 = "o'clock"
            }
            roundedMin <= 30 -> {
                val wm = when (roundedMin) {
                    5  -> "five past"
                    10 -> "ten past"
                    15 -> "quarter past"
                    20 -> "twenty past"
                    25 -> "twenty five past"
                    30 -> "half past"
                    else -> ""
                }
                line1 = wm
                line2 = WORD_HOURS[hour12]
            }
            else -> {
                val wm = when (60 - roundedMin) {
                    5  -> "five to"
                    10 -> "ten to"
                    15 -> "quarter to"
                    20 -> "twenty to"
                    25 -> "twenty five to"
                    else -> ""
                }
                val nextHour = (hour12 % 12) + 1
                line1 = wm
                line2 = WORD_HOURS[nextHour.coerceIn(0, 12)]
            }
        }

        val fs = config.fontSize * 0.7f
        mainPaint.textSize = fs
        mainPaint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        canvas.drawText(line1, 0f, -fs * 0.6f, mainPaint)
        canvas.drawText(line2, 0f, fs * 0.6f, mainPaint)
        mainPaint.typeface = Typeface.DEFAULT_BOLD
    }

    // ── Animation helpers ─────────────────────────────────────────────────────

    private fun computePulseScale(): Float {
        return when (config.animation) {
            ClockAnimation.PULSE -> 1f + sin(animPhase).toFloat() * 0.012f
            else                 -> 1f
        }
    }

    private fun computeGlowRadius(): Float {
        val base = config.shadowRadius
        return when (config.animation) {
            ClockAnimation.GLOW  -> base + sin(animPhase).toFloat() * base * 0.5f
            ClockAnimation.NEON  -> base + sin(animPhase * 1.5).toFloat() * base * 0.8f
            else                 -> base
        }
    }

    private fun computeFadeAlpha(minute: Int, base: Int): Int {
        return when (config.animation) {
            ClockAnimation.FADE -> {
                if (minute != fadePrevMinute) {
                    fadePrevMinute = minute
                }
                // Simple fade: alpha oscillates gently
                val fade = (sin(animPhase * 0.3) * 0.05 + 0.95).toFloat()
                (base * fade).toInt().coerceIn(0, 255)
            }
            else -> base
        }
    }

    private fun computeSlideOffset(second: Int, fontSize: Float): Float {
        return when (config.animation) {
            ClockAnimation.SLIDE -> {
                if (second != slidePrevSecond) {
                    slidePrevSecond = second
                    slideProgress   = 0f
                }
                slideProgress = (slideProgress + 0.08f).coerceAtMost(1f)
                val eased = easeOutCubic(slideProgress)
                fontSize * 0.3f * (1f - eased)
            }
            else -> 0f
        }
    }

    private fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3)

    private fun safeColor(hex: String, fallback: Int): Int = try {
        Color.parseColor(hex)
    } catch (_: Exception) { fallback }
}
