// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  example/WallpaperManager.ts
//
//  This file is the ONLY place in your app that imports the plugin.
//  Your UI components call these functions — they pass only what the
//  user has actually selected.  Nothing is forced.
//
//  Pattern:
//    • Every filter/text/option field is checked before inclusion
//    • If the user didn't touch a slider → it's not sent to the plugin
//    • Plugin receives a clean, minimal options object
// ─────────────────────────────────────────────────────────────────────────────

import {
  WallpaperPro,
  WallpaperTarget,
  WallpaperFilter,
  TextOverlayOptions,
  GradientOptions,
  ScheduleType,
} from '../src/index';


// ─────────────────────────────────────────────────────────────────────────────
//  Types that mirror what your UI state looks like
// ─────────────────────────────────────────────────────────────────────────────

/** What your filter panel exposes to the user */
export interface UIFilterState {
  enabled:     boolean;
  blur:        number;    // slider 0–25,   user default = 0
  brightness:  number;    // slider 0–3,    user default = 1.0
  contrast:    number;    // slider 0–3,    user default = 1.0
  saturation:  number;    // slider 0–3,    user default = 1.0
  grayscale:   boolean;
  sepia:       number;    // slider 0–1
  vignette:    number;    // slider 0–1
  hue:         number;    // slider -180–180
  temperature: number;    // slider -1–1
  opacity:     number;    // slider 0–1,    user default = 1.0
  tintEnabled: boolean;
  tintColor:   string;    // colour picker
  tintOpacity: number;
}

/** What your text panel exposes */
export interface UITextState {
  enabled:          boolean;
  text:             string;
  fontSize:         number;
  color:            string;
  bold:             boolean;
  italic:           boolean;
  alignment:        'left' | 'center' | 'right';
  anchorX:          number;
  anchorY:          number;
  shadowEnabled:    boolean;
  shadowColor:      string;
  shadowRadius:     number;
  bgEnabled:        boolean;
  bgColor:          string;
  bgPadding:        number;
  bgRadius:         number;
}

/** What your schedule panel exposes */
export interface UIScheduleEntry {
  time:       string;         // "HH:mm"
  dayOfWeek?: number;         // weekly mode only
  url:        string;
  label?:     string;
  filter?:    UIFilterState;
}

/** Root app state for the wallpaper screen */
export interface UIWallpaperState {
  // Source
  imageUrl:    string;
  isVideo:     boolean;
  target:      WallpaperTarget;   // 'home' | 'lock' | 'both'

  // Optional features — all off by default
  parallax:    boolean;
  parallaxIntensity: number;

  cropMode:    'fill' | 'fit' | 'center' | 'stretch';
  cropX:       number;
  cropY:       number;

  quality:     number;

  filter:      UIFilterState;
  text:        UITextState;

  // Live wallpaper extras
  videoMute:   boolean;
  videoLoop:   boolean;
  videoSpeed:  number;
}

// ─────────────────────────────────────────────────────────────────────────────
//  Default UI state — nothing enabled, sliders at neutral positions
// ─────────────────────────────────────────────────────────────────────────────

export const DEFAULT_FILTER_STATE: UIFilterState = {
  enabled:     false,
  blur:        0,
  brightness:  1.0,
  contrast:    1.0,
  saturation:  1.0,
  grayscale:   false,
  sepia:       0,
  vignette:    0,
  hue:         0,
  temperature: 0,
  opacity:     1.0,
  tintEnabled: false,
  tintColor:   '#000000',
  tintOpacity: 0.3,
};

export const DEFAULT_TEXT_STATE: UITextState = {
  enabled:       false,
  text:          '',
  fontSize:      52,
  color:         '#FFFFFF',
  bold:          false,
  italic:        false,
  alignment:     'center',
  anchorX:       0.5,
  anchorY:       0.85,
  shadowEnabled: true,
  shadowColor:   '#00000099',
  shadowRadius:  10,
  bgEnabled:     false,
  bgColor:       '#00000066',
  bgPadding:     20,
  bgRadius:      14,
};

export const DEFAULT_WALLPAPER_STATE: UIWallpaperState = {
  imageUrl:          '',
  isVideo:           false,
  target:            'both',
  parallax:          false,
  parallaxIntensity: 1.5,
  cropMode:          'fill',
  cropX:             0.5,
  cropY:             0.5,
  quality:           95,
  filter:            DEFAULT_FILTER_STATE,
  text:              DEFAULT_TEXT_STATE,
  videoMute:         true,
  videoLoop:         true,
  videoSpeed:        1.0,
};

// ─────────────────────────────────────────────────────────────────────────────
//  Converters — UI state → plugin options (only non-default values included)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Convert UI filter state → WallpaperFilter.
 * Returns undefined if the filter panel is disabled (plugin gets nothing).
 * Only includes fields the user actually changed from neutral.
 */
function buildFilter(ui: UIFilterState): WallpaperFilter | undefined {
  if (!ui.enabled) return undefined;

  const f: WallpaperFilter = {};

  if (ui.blur        > 0)    f.blur        = ui.blur;
  if (ui.brightness  !== 1)  f.brightness  = ui.brightness;
  if (ui.contrast    !== 1)  f.contrast    = ui.contrast;
  if (ui.saturation  !== 1)  f.saturation  = ui.saturation;
  if (ui.grayscale)          f.grayscale   = true;
  if (ui.sepia       > 0)    f.sepia       = ui.sepia;
  if (ui.vignette    > 0)    f.vignette    = ui.vignette;
  if (ui.hue         !== 0)  f.hue         = ui.hue;
  if (ui.temperature !== 0)  f.temperature = ui.temperature;
  if (ui.opacity     < 1)    f.opacity     = ui.opacity;
  if (ui.tintEnabled && ui.tintColor) {
    f.tintColor   = ui.tintColor;
    f.tintOpacity = ui.tintOpacity;
  }

  // If nothing changed, return undefined (saves a processing pass in plugin)
  return Object.keys(f).length > 0 ? f : undefined;
}

/**
 * Convert UI text state → TextOverlayOptions.
 * Returns undefined if text panel is disabled or text is empty.
 */
function buildTextOverlay(ui: UITextState): TextOverlayOptions | undefined {
  if (!ui.enabled || !ui.text.trim()) return undefined;

  const t: TextOverlayOptions = {
    text:      ui.text.trim(),
    fontSize:  ui.fontSize,
    color:     ui.color,
    alignment: ui.alignment,
    anchorX:   ui.anchorX,
    anchorY:   ui.anchorY,
  };

  if (ui.bold)   t.bold   = true;
  if (ui.italic) t.italic = true;

  if (ui.shadowEnabled) {
    t.shadowColor  = ui.shadowColor;
    t.shadowRadius = ui.shadowRadius;
  }

  if (ui.bgEnabled) {
    t.backgroundColor  = ui.bgColor;
    t.backgroundPadding = ui.bgPadding;
    t.backgroundRadius  = ui.bgRadius;
  }

  return t;
}

// ─────────────────────────────────────────────────────────────────────────────
//  Main apply function — called when user taps "Set Wallpaper"
// ─────────────────────────────────────────────────────────────────────────────

export async function applyWallpaper(state: UIWallpaperState): Promise<{
  success: boolean;
  message: string;
}> {
  if (!state.imageUrl) throw new Error('No image or video URL selected');

  const filter      = buildFilter(state.filter);
  const textOverlay = buildTextOverlay(state.text);

  // ── Live video wallpaper ────────────────────────────────────────────────
  if (state.isVideo) {
    return WallpaperPro.setLiveWallpaper({
      url:    state.imageUrl,
      target: state.target,
      mute:   state.videoMute,
      loop:   state.videoLoop,
      speed:  state.videoSpeed,
    });
  }

  // ── Static image wallpaper ──────────────────────────────────────────────
  return WallpaperPro.setWallpaper({
    url:    state.imageUrl,
    target: state.target,

    // Parallax — only if user enabled it
    ...(state.parallax && {
      parallax:          true,
      parallaxIntensity: state.parallaxIntensity,
    }),

    // Crop — only include if user changed from defaults
    ...(state.cropMode !== 'fill' && { cropMode: state.cropMode }),
    ...(state.cropX    !== 0.5   && { cropX: state.cropX }),
    ...(state.cropY    !== 0.5   && { cropY: state.cropY }),

    // Quality — only if changed
    ...(state.quality !== 95 && { quality: state.quality }),

    // Filter + text — undefined if user didn't touch them
    ...(filter      && { filter }),
    ...(textOverlay && { textOverlay }),
  });
}

// ─────────────────────────────────────────────────────────────────────────────
//  Gradient apply — called when user is in gradient mode
// ─────────────────────────────────────────────────────────────────────────────

export async function applyGradientWallpaper(
  gradient: GradientOptions,
  target: WallpaperTarget,
  text?: UITextState,
): Promise<{ success: boolean; message: string }> {
  const textOverlay = text ? buildTextOverlay(text) : undefined;

  return WallpaperPro.setGradientWallpaper({
    gradient,
    target,
    ...(textOverlay && { textOverlay }),
  });
}

// ─────────────────────────────────────────────────────────────────────────────
//  Schedule apply
// ─────────────────────────────────────────────────────────────────────────────

export async function applySchedule(options: {
  entries:         UIScheduleEntry[];
  target:          WallpaperTarget;
  scheduleType:    ScheduleType;
  intervalMinutes?: number;
  parallax?:       boolean;
  preloadAll?:     boolean;
  showNotifications?: boolean;
}): Promise<{ success: boolean; message: string }> {
  return WallpaperPro.schedule24HourWallpapers({
    target:            options.target,
    scheduleType:      options.scheduleType,
    intervalMinutes:   options.intervalMinutes ?? 60,
    parallax:          options.parallax ?? false,
    preloadAll:        options.preloadAll ?? true,
    showNotifications: options.showNotifications ?? false,
    schedule: options.entries.map(entry => ({
      time:       entry.time,
      url:        entry.url,
      label:      entry.label,
      dayOfWeek:  entry.dayOfWeek,
      // Only attach filter if user configured one for this slot
      ...(entry.filter && { filter: buildFilter(entry.filter) }),
    })),
  });
}

// ─────────────────────────────────────────────────────────────────────────────
//  Utility helpers your UI can call directly
// ─────────────────────────────────────────────────────────────────────────────

export const WallpaperActions = {

  /** Pre-fetch image in background without blocking UI */
  preload: (url: string, priority: 'high' | 'normal' | 'low' = 'normal') =>
    WallpaperPro.preloadWallpaper({ url, priority }),

  /** Get last N wallpaper changes */
  getHistory: (limit = 20) =>
    WallpaperPro.getHistory({ limit }),

  /** Restore previous wallpaper */
  undo: () => WallpaperPro.undoWallpaper(),

  /** Cancel all scheduled changes */
  clearSchedule: () => WallpaperPro.clearSchedule(),

  /** Get current schedule status */
  getInfo: () => WallpaperPro.getWallpaperInfo(),

  /** Check what the cache is using */
  getCacheInfo: () => WallpaperPro.getCacheInfo(),

  /** Free image cache */
  clearImageCache: () => WallpaperPro.clearCache(),

  /** Free video cache */
  clearVideoCache: () => WallpaperPro.clearVideoCache(),

  /** Request permissions (call once on app start) */
  requestPermissions: () => WallpaperPro.requestPermissions(),

  /** Listen for scheduled wallpaper changes */
  onWallpaperChanged: (cb: (e: { time: string; label?: string; url: string; success: boolean }) => void) =>
    WallpaperPro.addListener('wallpaperChanged', cb),

  /** Listen for video download progress */
  onVideoProgress: (cb: (e: { progress: number; url: string }) => void) =>
    WallpaperPro.addListener('liveWallpaperProgress', cb),

  removeAllListeners: () => WallpaperPro.removeAllListeners(),
};
