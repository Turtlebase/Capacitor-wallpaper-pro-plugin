// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  DepthConfig.kt
//
//  Data classes for the depth-effect wallpaper.
//  Parsed from the JSON stored in SharedPreferences by WallpaperProPlugin.
// ─────────────────────────────────────────────────────────────────────────────
package com.wallpaperpro

import android.graphics.PointF
import org.json.JSONObject

// ─── Clock ────────────────────────────────────────────────────────────────────

enum class ClockStyle { DIGITAL, ANALOG, MINIMAL, NEON, RETRO, WORD }
enum class ClockAnimation { NONE, PULSE, GLOW, FADE, SLIDE, FLIP }
enum class TimeFormat { H12, H24 }

data class ClockConfig(
    val style:           ClockStyle     = ClockStyle.DIGITAL,
    val animation:       ClockAnimation = ClockAnimation.PULSE,
    val format:          TimeFormat     = TimeFormat.H24,
    val position:        PointF         = PointF(0.5f, 0.35f),  // 0–1 relative
    val color:           String         = "#FFFFFF",
    val secondaryColor:  String         = "#CCCCCC",
    val accentColor:     String         = "#FF6B6B",            // seconds hand, accent
    val fontSize:        Float          = 80f,
    val showSeconds:     Boolean        = true,
    val showDate:        Boolean        = true,
    val showAmPm:        Boolean        = false,
    val opacity:         Float          = 1.0f,
    val shadow:          Boolean        = true,
    val shadowRadius:    Float          = 24f,
    val shadowColor:     String         = "#CC000000",
    val strokeWidth:     Float          = 4f,                   // analog hands / borders
) {
    companion object {
        fun fromJson(json: JSONObject): ClockConfig = ClockConfig(
            style          = when (json.optString("style", "digital").lowercase()) {
                "analog"  -> ClockStyle.ANALOG
                "minimal" -> ClockStyle.MINIMAL
                "neon"    -> ClockStyle.NEON
                "retro"   -> ClockStyle.RETRO
                "word"    -> ClockStyle.WORD
                else      -> ClockStyle.DIGITAL
            },
            animation      = when (json.optString("animation", "pulse").lowercase()) {
                "none"    -> ClockAnimation.NONE
                "glow"    -> ClockAnimation.GLOW
                "fade"    -> ClockAnimation.FADE
                "slide"   -> ClockAnimation.SLIDE
                "flip"    -> ClockAnimation.FLIP
                else      -> ClockAnimation.PULSE
            },
            format         = if (json.optString("format", "24h") == "12h") TimeFormat.H12 else TimeFormat.H24,
            position       = json.optJSONObject("position")?.let {
                PointF(it.optDouble("x", 0.5).toFloat(), it.optDouble("y", 0.35).toFloat())
            } ?: PointF(0.5f, 0.35f),
            color          = json.optString("color",          "#FFFFFF"),
            secondaryColor = json.optString("secondaryColor", "#CCCCCC"),
            accentColor    = json.optString("accentColor",    "#FF6B6B"),
            fontSize       = json.optDouble("fontSize",        80.0).toFloat(),
            showSeconds    = json.optBoolean("showSeconds",    true),
            showDate       = json.optBoolean("showDate",       true),
            showAmPm       = json.optBoolean("showAmPm",       false),
            opacity        = json.optDouble("opacity",         1.0).toFloat(),
            shadow         = json.optBoolean("shadow",         true),
            shadowRadius   = json.optDouble("shadowRadius",    24.0).toFloat(),
            shadowColor    = json.optString("shadowColor",     "#CC000000"),
            strokeWidth    = json.optDouble("strokeWidth",     4.0).toFloat(),
        )
    }
}

// ─── Depth ────────────────────────────────────────────────────────────────────

data class DepthLayerConfig(
    /** 0.0 = fixed (no parallax), 1.0 = full parallax movement */
    val bgParallaxFactor:    Float = 0.25f,
    /** Clock is between layers — slight movement gives nice depth */
    val clockParallaxFactor: Float = 0.05f,
    /** Foreground moves most — appears nearest to viewer */
    val fgParallaxFactor:    Float = 1.0f,
    /** Maximum pixel offset at full tilt */
    val maxOffset:           Float = 48f,
    /** Sensor sensitivity multiplier */
    val sensitivity:         Float = 1.0f,
    /** Lerp factor for smoothing: 0 = very smooth, 1 = instant */
    val smoothing:           Float = 0.08f,
) {
    companion object {
        fun fromJson(json: JSONObject): DepthLayerConfig = DepthLayerConfig(
            bgParallaxFactor    = json.optDouble("bgParallaxFactor",    0.25).toFloat(),
            clockParallaxFactor = json.optDouble("clockParallaxFactor", 0.05).toFloat(),
            fgParallaxFactor    = json.optDouble("fgParallaxFactor",    1.0).toFloat(),
            maxOffset           = json.optDouble("maxOffset",           48.0).toFloat(),
            sensitivity         = json.optDouble("sensitivity",         1.0).toFloat(),
            smoothing           = json.optDouble("smoothing",           0.08).toFloat(),
        )
    }
}

// ─── Root config ──────────────────────────────────────────────────────────────

data class DepthWallpaperConfig(
    val backgroundPath: String,
    val foregroundPath: String,
    val target:         String           = "both",
    val clock:          ClockConfig      = ClockConfig(),
    val depth:          DepthLayerConfig = DepthLayerConfig(),
) {
    companion object {
        const val PREFS_KEY = "depth_wallpaper_config"

        fun fromJson(raw: String): DepthWallpaperConfig? {
            return try {
                val json = JSONObject(raw)
                DepthWallpaperConfig(
                    backgroundPath = json.getString("backgroundPath"),
                    foregroundPath = json.getString("foregroundPath"),
                    target         = json.optString("target", "both"),
                    clock          = json.optJSONObject("clock")?.let { ClockConfig.fromJson(it) } ?: ClockConfig(),
                    depth          = json.optJSONObject("depth")?.let { DepthLayerConfig.fromJson(it) } ?: DepthLayerConfig(),
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
