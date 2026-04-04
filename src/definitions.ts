// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  definitions.ts  (v2)
// ─────────────────────────────────────────────────────────────────────────────

import type { PluginListenerHandle } from '@capacitor/core';

export interface WallpaperFilter {
  blur?: number;          // 0–25
  brightness?: number;    // 0.0–3.0 (default 1.0)
  contrast?: number;      // 0.0–3.0
  saturation?: number;    // 0.0–3.0
  grayscale?: boolean;
  sepia?: number;         // 0.0–1.0
  vignette?: number;      // 0.0–1.0
  hue?: number;           // -180–180
  temperature?: number;   // -1.0 (cool) – 1.0 (warm)
  opacity?: number;       // 0.0–1.0
  tintColor?: string;     // "#RRGGBB" or "rgba(r,g,b,a)"
  tintOpacity?: number;   // 0.0–1.0
}

export interface TextOverlayOptions {
  text: string;
  fontSize?: number;           // default 48
  color?: string;              // default "#FFFFFF"
  fontFamily?: string;         // default "sans-serif"
  bold?: boolean;
  italic?: boolean;
  alignment?: 'left' | 'center' | 'right';
  anchorX?: number;            // 0.0–1.0  (default 0.5 = center)
  anchorY?: number;            // 0.0–1.0  (default 0.85 = lower third)
  shadowColor?: string;        // default "#00000088"
  shadowRadius?: number;
  shadowDx?: number;
  shadowDy?: number;
  backgroundColor?: string;    // pill background behind text
  backgroundPadding?: number;
  backgroundRadius?: number;
  maxWidthFraction?: number;   // 0.0–1.0  (default 0.85)
  lineSpacing?: number;        // default 1.2
}

export interface GradientOptions {
  type?: 'linear' | 'radial' | 'sweep';
  colors: string[];            // e.g. ["#FF0000","#0000FF"]
  stops?: number[];            // e.g. [0.0, 1.0]
  angle?: number;              // degrees, for linear (default 180)
}

export type WallpaperTarget = 'home' | 'lock' | 'both';
export type CropMode = 'fill' | 'fit' | 'center' | 'stretch';
export type ScheduleType = 'daily' | 'weekly' | 'interval';
export type DownloadPriority = 'high' | 'normal' | 'low';

export interface SetWallpaperOptions {
  url: string;
  target?: WallpaperTarget;        // default 'both'
  parallax?: boolean;              // default false
  parallaxIntensity?: number;      // 1.1–3.0, default 1.5
  filter?: WallpaperFilter;
  quality?: number;                // 0–100, default 95
  cropMode?: CropMode;             // default 'fill'
  cropX?: number;                  // 0.0–1.0 horizontal anchor for 'fill'
  cropY?: number;                  // 0.0–1.0 vertical anchor for 'fill'
  cache?: boolean;                 // default true
  transitionDuration?: number;     // ms, default 400
  textOverlay?: TextOverlayOptions;
  label?: string;                  // written into history
}

export interface SetGradientWallpaperOptions {
  gradient: GradientOptions;
  target?: WallpaperTarget;
  quality?: number;
  textOverlay?: TextOverlayOptions;
}

export interface SetRandomWallpaperOptions {
  urls: string[];
  target?: WallpaperTarget;
  parallax?: boolean;
  filter?: WallpaperFilter;
}

export interface ScheduleEntry {
  time: string;         // "HH:mm"
  url: string;
  filter?: WallpaperFilter;
  label?: string;
  parallax?: boolean;
  dayOfWeek?: number;   // 1=Mon … 7=Sun  (weekly mode only)
}

export interface Schedule24HourOptions {
  schedule: ScheduleEntry[];
  target?: WallpaperTarget;
  parallax?: boolean;
  preloadAll?: boolean;
  showNotifications?: boolean;
  scheduleType?: ScheduleType;     // default 'daily'
  intervalMinutes?: number;        // for 'interval' mode, default 60
}

export interface SetLiveWallpaperOptions {
  url: string;        // http(s) URL or local path — MP4/MKV/WebM/HLS/DASH
  /** Which screen to apply the live wallpaper to. Default: 'both' */
  target?: WallpaperTarget;
  speed?: number;     // 0.25–4.0, default 1.0
  mute?: boolean;     // default true
  loop?: boolean;     // default true
}

export interface HistoryEntry {
  url: string;
  target: string;
  label?: string;
  filterJson?: string;
  timestamp: number;
  date: string;       // "yyyy-MM-dd HH:mm:ss"
  isLive: boolean;
}

export interface WallpaperInfo {
  scheduleActive: boolean;
  scheduleCount: number;
  scheduleType?: string;
  nextChangeTime?: string;
  nextChangeLabel?: string;
  currentLabel?: string;
  capabilities: PlatformCapabilities;
}

export interface PlatformCapabilities {
  canSetHomeScreen: boolean;
  canSetLockScreen: boolean;
  canSetDual: boolean;
  supportsParallax: boolean;
  supportsFilters: boolean;
  supportsScheduling: boolean;
  supportsLiveWallpaper: boolean;
  supportsGradient: boolean;
  supportsTextOverlay: boolean;
}

export interface PermissionStatus {
  wallpaper: PermissionState;
  storage: PermissionState;
  notifications: PermissionState;
}
export type PermissionState = 'granted' | 'denied' | 'prompt';

export interface CacheInfo {
  imageCacheBytes: number;
  videoCacheBytes: number;
  totalBytes: number;
  imageCacheMB: string;
  videoCacheMB: string;
}

export interface WallpaperChangedEvent {
  time: string;
  label?: string;
  url: string;
  success: boolean;
  error?: string;
}

export interface LiveWallpaperProgressEvent {
  type: 'download';
  progress: number;   // 0–100
  url: string;
}

// ─────────────────────────────────────────────────────────────────────────────
//  Main plugin interface
// ─────────────────────────────────────────────────────────────────────────────
export interface WallpaperProPlugin {
  /** Download, process (filters, parallax, text, crop) and set wallpaper. */
  setWallpaper(options: SetWallpaperOptions): Promise<{ success: boolean; message: string }>;

  /** Generate a gradient and set it as wallpaper — no image URL needed. */
  setGradientWallpaper(options: SetGradientWallpaperOptions): Promise<{ success: boolean; message: string }>;

  /** Pick a random URL from the array and set it. */
  setRandomWallpaper(options: SetRandomWallpaperOptions): Promise<{ success: boolean; message: string; selectedUrl: string }>;

  /** Register a timed schedule (daily / weekly / interval). */
  schedule24HourWallpapers(options: Schedule24HourOptions): Promise<{ success: boolean; message: string }>;

  clearSchedule(): Promise<{ success: boolean }>;

  getWallpaperInfo(): Promise<WallpaperInfo>;

  /** Download video + open system live-wallpaper preview. */
  setLiveWallpaper(options: SetLiveWallpaperOptions): Promise<{ success: boolean; message: string; videoPath: string }>;

  /** Get last N wallpaper changes. */
  getHistory(options?: { limit?: number }): Promise<{ history: HistoryEntry[]; count: number }>;

  /** Restore the previous wallpaper from history. */
  undoWallpaper(): Promise<{ success: boolean; message: string; restoredUrl?: string }>;

  clearHistory(): Promise<{ success: boolean }>;

  checkPermissions(): Promise<PermissionStatus>;
  requestPermissions(): Promise<PermissionStatus>;

  /** Pre-fetch image to cache with optional priority. */
  preloadWallpaper(options: { url: string; priority?: DownloadPriority }): Promise<{ success: boolean; cachedPath: string }>;

  clearCache(): Promise<{ success: boolean; bytesFreed: number }>;
  clearVideoCache(): Promise<{ success: boolean; bytesFreed: number }>;

  /** Get current cache size breakdown. */
  getCacheInfo(): Promise<CacheInfo>;

  addListener(eventName: 'wallpaperChanged', listenerFunc: (event: WallpaperChangedEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'liveWallpaperProgress', listenerFunc: (event: LiveWallpaperProgressEvent) => void): Promise<PluginListenerHandle>;
  /**
   * Set an iOS-style depth effect live wallpaper.
   *
   * Pass a background image, a foreground PNG (with transparency), and an
   * optional real-time clock config + depth/parallax config.
   *
   * The plugin composites three layers:
   *   Background (moves slowly) → Clock (real-time, between layers) → Foreground (moves most)
   *
   * The device's rotation sensor drives the parallax. The clock updates every
   * second in sync with the system clock. Everything runs in a background
   * thread — zero ANR risk.
   *
   * Android only — opens system live wallpaper preview.
   *
   * @example
   * await WallpaperPro.addListener('depthWallpaperProgress', e => {
   *   if (e.stage === 'ready') console.log('Images cached, preview opening…');
   * });
   *
   * await WallpaperPro.setDepthWallpaper({
   *   backgroundUrl: 'https://example.com/mountain.jpg',
   *   foregroundUrl: 'https://example.com/person_cutout.png',
   *   target: 'both',
   *   clock: {
   *     style:     'digital',
   *     animation: 'pulse',
   *     color:     '#FFFFFF',
   *     position:  { x: 0.5, y: 0.35 },
   *     showDate:  true,
   *   },
   *   depth: {
   *     bgParallaxFactor: 0.25,
   *     fgParallaxFactor: 1.0,
   *     maxOffset:        48,
   *     sensitivity:      1.0,
   *   },
   * });
   */
  setDepthWallpaper(options: SetDepthWallpaperOptions): Promise<{
    success: boolean;
    message: string;
    bgPath:  string;
    fgPath:  string;
  }>;

  addListener(
    eventName: 'depthWallpaperProgress',
    listenerFunc: (event: DepthWallpaperProgressEvent) => void,
  ): Promise<PluginListenerHandle>;

  removeAllListeners(): Promise<void>;
}

// ─────────────────────────────────────────────────────────────────────────────
//  Depth Wallpaper types
// ─────────────────────────────────────────────────────────────────────────────

export type ClockStyle     = 'digital' | 'analog' | 'minimal' | 'neon' | 'retro' | 'word';
export type ClockAnimation = 'none' | 'pulse' | 'glow' | 'fade' | 'slide' | 'flip';

export interface ClockPosition {
  /** 0.0 = left edge, 1.0 = right edge. Default: 0.5 (centre) */
  x: number;
  /** 0.0 = top, 1.0 = bottom. Default: 0.35 (upper third) */
  y: number;
}

export interface ClockConfig {
  /** Visual style of the clock. Default: 'digital' */
  style?: ClockStyle;
  /** Second-tick animation. Default: 'pulse' */
  animation?: ClockAnimation;
  /** Time format. Default: '24h' */
  format?: '12h' | '24h';
  /** Clock centre position as fraction of screen. Default: { x:0.5, y:0.35 } */
  position?: ClockPosition;
  /** Primary colour (time digits, analog face). Default: '#FFFFFF' */
  color?: string;
  /** Secondary colour (date, tick marks, analog inner). Default: '#CCCCCC' */
  secondaryColor?: string;
  /** Accent colour (seconds hand, neon glow, highlights). Default: '#FF6B6B' */
  accentColor?: string;
  /** Base font size in pixels. Default: 80 */
  fontSize?: number;
  /** Show seconds. Default: true */
  showSeconds?: boolean;
  /** Show date line below time. Default: true */
  showDate?: boolean;
  /** Show AM/PM label (12h format only). Default: false */
  showAmPm?: boolean;
  /** Overall opacity. Default: 1.0 */
  opacity?: number;
  /** Enable drop shadow / glow. Default: true */
  shadow?: boolean;
  /** Shadow/glow blur radius. Default: 24 */
  shadowRadius?: number;
  /** Shadow colour with alpha. Default: '#CC000000' */
  shadowColor?: string;
  /** Stroke width for analog hands and borders. Default: 4 */
  strokeWidth?: number;
}

export interface DepthLayerConfig {
  /**
   * How much the background moves relative to full parallax.
   * 0.0 = fixed, 1.0 = full movement. Default: 0.25
   * (background appears far away → moves least)
   */
  bgParallaxFactor?: number;
  /**
   * Clock parallax factor. Default: 0.05
   * (clock sits between layers, very slight movement)
   */
  clockParallaxFactor?: number;
  /**
   * Foreground parallax factor. Default: 1.0
   * (foreground appears closest → moves most)
   */
  fgParallaxFactor?: number;
  /** Maximum pixel shift at full device tilt. Default: 48 */
  maxOffset?: number;
  /** Sensor sensitivity multiplier. Default: 1.0 */
  sensitivity?: number;
  /**
   * Lerp smoothing factor per frame.
   * 0.0 = very smooth (laggy), 1.0 = instant (jittery). Default: 0.08
   */
  smoothing?: number;
}

export interface SetDepthWallpaperOptions {
  /**
   * URL or local path to the background image (JPG/PNG).
   * This is the farthest layer — usually a landscape or scene.
   */
  backgroundUrl: string;
  /**
   * URL or local path to the foreground image (PNG with transparency).
   * This layer floats in front of the clock creating the depth pop effect.
   * Use a PNG where the background is transparent (person, object, etc.)
   */
  foregroundUrl: string;
  /** Which screen(s) to apply to. Default: 'both' */
  target?: WallpaperTarget;
  /** Real-time clock configuration. All fields optional. */
  clock?: ClockConfig;
  /** Depth / parallax layer configuration. All fields optional. */
  depth?: DepthLayerConfig;
}

/** Fired when depth wallpaper images finish downloading. */
export interface DepthWallpaperProgressEvent {
  stage:  'downloading' | 'ready';
  bgPath?: string;
  fgPath?: string;
}
