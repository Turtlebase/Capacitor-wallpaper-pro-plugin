# capacitor-wallpaper-pro

> **Pass a URL (or nothing). The plugin handles everything.**

The most advanced open-source wallpaper plugin for Capacitor / Ionic apps.

| Feature | Android | iOS |
|---|---|---|
| Set home-screen wallpaper | ✅ | ⚠️ Saves to Photos¹ |
| Set lock-screen wallpaper | ✅ API 24+ | ⚠️ Saves to Photos |
| Dual home + lock targets | ✅ | ⚠️ |
| **Live video wallpaper (MP4/HLS/DASH)** | ✅ ExoPlayer | — |
| **Parallax effect** | ✅ | — |
| **24-hour daily schedule** | ✅ Survives reboots | ✅ |
| **Weekly schedule (per day-of-week)** | ✅ | ✅ |
| **Interval schedule (every N min)** | ✅ | ✅ |
| **Gradient wallpaper (no image)** | ✅ | ✅ |
| **Text / quote overlay** | ✅ | ✅ |
| **Random wallpaper from array** | ✅ | ✅ |
| **Wallpaper history + undo** | ✅ | ✅ |
| **Smart LRU cache (500 MB limit)** | ✅ | ✅ |
| **Priority download queue** | ✅ | — |
| **Retry with back-off** | ✅ | ✅ |
| **OOM guard (safe inSampleSize)** | ✅ | — |
| Blur / Brightness / Contrast | ✅ | ✅ Core Image |
| Saturation / Sepia / Grayscale | ✅ | ✅ |
| Vignette / Tint / Temperature / Hue | ✅ | ✅ |
| Crop anchor (cropX / cropY) | ✅ | ✅ |
| Opacity | ✅ | ✅ |
| Schedule persistence across reboots | ✅ AlarmManager | ✅ UserDefaults |

¹ Apple does not expose a public API to set wallpapers programmatically.

---

## Install

```bash
npm install capacitor-wallpaper-pro
npx cap sync
```

---

## Android Setup

### 1. Permissions (auto-merged)

The plugin's `AndroidManifest.xml` declares all needed permissions.
They merge automatically — no manual copy required.

For Android 13+ notifications, optionally add:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 2. Register in `MainActivity`

```kotlin
// MainActivity.kt
import com.getcapacitor.BridgeActivity
class MainActivity : BridgeActivity()   // Capacitor 4+ auto-registers
```

### 3. Live Wallpaper Service (important!)

If the `<service>` block does not auto-merge into your app manifest, copy it
manually inside `<application>` in `android/app/src/main/AndroidManifest.xml`:

```xml
<service
    android:name="com.wallpaperpro.VideoLiveWallpaperService"
    android:enabled="true"
    android:exported="true"
    android:label="WallpaperPro Video"
    android:permission="android.permission.BIND_WALLPAPER">
    <intent-filter>
        <action android:name="android.service.wallpaper.WallpaperService" />
    </intent-filter>
    <meta-data
        android:name="android.service.wallpaper"
        android:resource="@xml/video_live_wallpaper" />
</service>
```

### 4. Battery optimisation

For reliable scheduling on Xiaomi / Huawei / Samsung ROMs, guide the user to
set your app to "Unrestricted" battery mode. You can deeplink to:
`android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

---

## iOS Setup

### 1. Run pod install

```bash
cd ios && pod install
```

### 2. Add to `Info.plist`

```xml
<key>NSPhotoLibraryAddUsageDescription</key>
<string>WallpaperPro saves processed wallpapers to your photo library.</string>
```

### iOS Note

Apple's APIs do not allow third-party apps to set wallpapers directly.
On iOS, every `set*` method:
1. Applies all filters natively via Core Image.
2. Saves the result to the Photos library.
3. Presents a share sheet — the user taps **"Use as Wallpaper"**.

---

## Quick Start

```typescript
import { WallpaperPro } from 'capacitor-wallpaper-pro';

// ── 1. Simple set ────────────────────────────────────────────────────────────
await WallpaperPro.setWallpaper({
  url: 'https://picsum.photos/seed/forest/1080/1920',
});

// ── 2. Full options ──────────────────────────────────────────────────────────
await WallpaperPro.setWallpaper({
  url:               'https://picsum.photos/seed/mountain/2160/3840',
  target:            'both',
  parallax:          true,
  parallaxIntensity: 1.6,
  cropMode:          'fill',
  cropX:             0.5,   // centre horizontally
  cropY:             0.3,   // show top third
  quality:           95,
  filter: {
    blur:        2,
    brightness:  1.1,
    contrast:    1.05,
    saturation:  1.3,
    vignette:    0.4,
    temperature: 0.2,
  },
  textOverlay: {
    text:       'Good Morning ☀️',
    fontSize:   64,
    color:      '#FFFFFF',
    bold:       true,
    anchorY:    0.8,
    shadowRadius: 12,
    backgroundColor: '#00000066',
    backgroundPadding: 20,
  },
  label: 'Morning wallpaper',
});

// ── 3. Gradient (no image needed) ────────────────────────────────────────────
await WallpaperPro.setGradientWallpaper({
  gradient: {
    type:   'linear',
    colors: ['#1a1a2e', '#16213e', '#0f3460', '#533483'],
    angle:  160,
  },
  textOverlay: {
    text:     '"The secret of getting ahead is getting started."',
    fontSize: 52,
    anchorY:  0.5,
    color:    '#E0E0E0',
    italic:   true,
    maxWidthFraction: 0.8,
  },
});

// ── 4. Random wallpaper ──────────────────────────────────────────────────────
const { selectedUrl } = await WallpaperPro.setRandomWallpaper({
  urls: [url1, url2, url3, url4, url5],
  parallax: true,
  filter: { saturation: 1.2 },
});
console.log('Selected:', selectedUrl);

// ── 5. 24-hour daily schedule ────────────────────────────────────────────────
await WallpaperPro.schedule24HourWallpapers({
  scheduleType: 'daily',
  target:       'both',
  parallax:     true,
  preloadAll:   true,
  schedule: [
    { time: '05:30', url: sunriseUrl,  label: 'Sunrise', filter: { temperature: 0.4 } },
    { time: '09:00', url: morningUrl,  label: 'Morning' },
    { time: '12:00', url: noonUrl,     label: 'Noon',    filter: { brightness: 1.1 } },
    { time: '17:00', url: goldenUrl,   label: 'Golden Hour', filter: { temperature: 0.6, saturation: 1.4 } },
    { time: '20:00', url: duskUrl,     label: 'Dusk' },
    { time: '22:30', url: nightUrl,    label: 'Night',   filter: { brightness: 0.6, saturation: 0.7 } },
  ],
});

// ── 6. Weekly schedule ───────────────────────────────────────────────────────
await WallpaperPro.schedule24HourWallpapers({
  scheduleType: 'weekly',
  schedule: [
    { time: '08:00', dayOfWeek: 1, url: mondayUrl,  label: 'Monday Motivation' },
    { time: '08:00', dayOfWeek: 5, url: fridayUrl,  label: 'Friday Vibes' },
    { time: '09:00', dayOfWeek: 6, url: saturdayUrl,label: 'Weekend Mode' },
    { time: '09:00', dayOfWeek: 7, url: sundayUrl,  label: 'Sunday Chill' },
  ],
});

// ── 7. Interval schedule (shuffle every 2 hours) ─────────────────────────────
await WallpaperPro.schedule24HourWallpapers({
  scheduleType:    'interval',
  intervalMinutes: 120,
  schedule: [url1, url2, url3, url4].map(url => ({ time: '00:00', url })),
});

// ── 8. Live video wallpaper ──────────────────────────────────────────────────
await WallpaperPro.addListener('liveWallpaperProgress', e => {
  console.log(`Downloading: ${e.progress}%`);
});

await WallpaperPro.setLiveWallpaper({
  url:   'https://example.com/loop.mp4',
  mute:  true,
  loop:  true,
  speed: 1.0,
});

// ── 9. History + undo ────────────────────────────────────────────────────────
const { history } = await WallpaperPro.getHistory({ limit: 10 });
console.log(history[0]); // most recent

await WallpaperPro.undoWallpaper();    // restore previous

// ── 10. Cache management ─────────────────────────────────────────────────────
const info = await WallpaperPro.getCacheInfo();
console.log(`Cache: ${info.imageCacheMB} MB images, ${info.videoCacheMB} MB videos`);

await WallpaperPro.clearCache();       // clear image cache
await WallpaperPro.clearVideoCache();  // clear video cache
```

---

## API Reference

### `setWallpaper(options)`

| Option | Type | Default | Description |
|---|---|---|---|
| `url` | `string` | **required** | Remote URL or local path |
| `target` | `WallpaperTarget` | `'both'` | `'home'` / `'lock'` / `'both'` |
| `parallax` | `boolean` | `false` | Enable parallax swipe effect |
| `parallaxIntensity` | `number` | `1.5` | Canvas width multiplier 1.1–3.0 |
| `filter` | `WallpaperFilter` | `{}` | Pixel-level filters |
| `quality` | `number` | `95` | JPEG quality 0–100 |
| `cropMode` | `CropMode` | `'fill'` | `fill` / `fit` / `center` / `stretch` |
| `cropX` | `number` | `0.5` | Horizontal anchor 0.0–1.0 for `fill` |
| `cropY` | `number` | `0.5` | Vertical anchor 0.0–1.0 for `fill` |
| `cache` | `boolean` | `true` | Use LRU disk cache |
| `textOverlay` | `TextOverlayOptions` | — | Text/quote drawn on image |
| `label` | `string` | — | Stored in history |

---

### `setGradientWallpaper(options)`

No image needed. Generates a gradient natively.

```typescript
await WallpaperPro.setGradientWallpaper({
  gradient: {
    type:   'radial',           // 'linear' | 'radial' | 'sweep'
    colors: ['#FF6B6B', '#4ECDC4', '#45B7D1'],
    stops:  [0.0, 0.5, 1.0],
    angle:  135,               // degrees (linear only)
  },
});
```

---

### `setRandomWallpaper(options)`

```typescript
await WallpaperPro.setRandomWallpaper({
  urls:     [url1, url2, url3],
  parallax: true,
  filter:   { saturation: 1.2 },
});
// returns { success, message, selectedUrl }
```

---

### `schedule24HourWallpapers(options)`

| Option | Type | Default | Description |
|---|---|---|---|
| `schedule` | `ScheduleEntry[]` | **required** | 1–48 entries |
| `scheduleType` | `ScheduleType` | `'daily'` | `'daily'` / `'weekly'` / `'interval'` |
| `intervalMinutes` | `number` | `60` | Minutes between changes (interval mode) |
| `target` | `WallpaperTarget` | `'both'` | |
| `parallax` | `boolean` | `false` | |
| `preloadAll` | `boolean` | `true` | Pre-download all images immediately |
| `showNotifications` | `boolean` | `false` | Silent notification on change |

**ScheduleEntry:**
| Field | Description |
|---|---|
| `time` | `"HH:mm"` |
| `url` | Image URL |
| `dayOfWeek` | `1`=Mon … `7`=Sun (weekly mode) |
| `filter` | Per-slot filter |
| `label` | Human label |
| `parallax` | Per-slot parallax override |

---

### `setLiveWallpaper(options)` *(Android only)*

```typescript
await WallpaperPro.setLiveWallpaper({
  url:   'https://example.com/loop.mp4', // or local path
  mute:  true,
  loop:  true,
  speed: 1.0,   // 0.25–4.0
});
```

Supported formats: **MP4, MKV, WebM, MOV, HLS (.m3u8), DASH (.mpd)**

Progress event during download:
```typescript
WallpaperPro.addListener('liveWallpaperProgress', e => {
  // e.progress: 0–100
});
```

---

### History

```typescript
// Get last N wallpapers
const { history } = await WallpaperPro.getHistory({ limit: 20 });
// history[0] = { url, target, label, timestamp, date, isLive }

// Restore previous wallpaper
await WallpaperPro.undoWallpaper();

// Clear all history
await WallpaperPro.clearHistory();
```

---

### Cache

```typescript
// Pre-fetch with priority
await WallpaperPro.preloadWallpaper({ url, priority: 'high' });  // 'high' | 'normal' | 'low'

// Inspect usage
const info = await WallpaperPro.getCacheInfo();
// { imageCacheBytes, videoCacheBytes, totalBytes, imageCacheMB, videoCacheMB }

// Clean up
await WallpaperPro.clearCache();        // images
await WallpaperPro.clearVideoCache();   // videos
```

---

## Filter Options (complete)

```typescript
interface WallpaperFilter {
  blur?:        number;   // 0–25
  brightness?:  number;   // 0.0–3.0
  contrast?:    number;   // 0.0–3.0
  saturation?:  number;   // 0.0–3.0
  grayscale?:   boolean;
  sepia?:       number;   // 0.0–1.0
  vignette?:    number;   // 0.0–1.0
  hue?:         number;   // -180–180
  temperature?: number;   // -1.0–1.0
  opacity?:     number;   // 0.0–1.0
  tintColor?:   string;   // "#RRGGBB"
  tintOpacity?: number;   // 0.0–1.0
}
```

### Filter Recipes

```typescript
// Night Mode
{ brightness: 0.6, saturation: 0.5, temperature: -0.2, vignette: 0.5 }

// Golden Hour
{ brightness: 1.1, saturation: 1.4, temperature: 0.6 }

// Frosted Glass
{ blur: 18, brightness: 1.05, opacity: 0.9 }

// Vintage Film
{ sepia: 0.6, contrast: 1.2, saturation: 0.8, vignette: 0.5 }

// Monochrome
{ grayscale: true, contrast: 1.3, brightness: 1.05 }

// Cyberpunk
{ hue: 180, saturation: 2.0, contrast: 1.4, tintColor: '#FF00FF', tintOpacity: 0.15 }
```

---

## Text Overlay Options

```typescript
interface TextOverlayOptions {
  text: string;
  fontSize?: number;          // default 48
  color?: string;             // default "#FFFFFF"
  fontFamily?: string;        // default "sans-serif"
  bold?: boolean;
  italic?: boolean;
  alignment?: 'left' | 'center' | 'right';
  anchorX?: number;           // 0.0–1.0 (default 0.5)
  anchorY?: number;           // 0.0–1.0 (default 0.85)
  shadowColor?: string;
  shadowRadius?: number;
  shadowDx?: number;
  shadowDy?: number;
  backgroundColor?: string;   // pill behind text
  backgroundPadding?: number;
  backgroundRadius?: number;
  maxWidthFraction?: number;  // default 0.85
  lineSpacing?: number;       // default 1.2
}
```

---

## Technical Deep-Dives

### Thread Safety

| Operation | Thread |
|---|---|
| Image download | `Dispatchers.IO` / `DispatchQueue.global` |
| Bitmap processing / filters | `Dispatchers.IO` / `DispatchQueue.global` |
| WallpaperManager.setStream | `Dispatchers.IO` |
| ExoPlayer init + playback | Dedicated `HandlerThread` |
| Activity.startActivity (live WP) | `Dispatchers.Main` |
| JS event callbacks | `Dispatchers.Main` |

### OOM Guard (Android)

Before decoding any remote image, the plugin calls `BitmapFactory` with
`inJustDecodeBounds = true` to measure dimensions without allocating pixels.
It then calculates the minimum `inSampleSize` that:
- fits the decoded image within `4096 × 4096` safe pixels
- keeps the image large enough to fill the screen after scaling

This prevents `OutOfMemoryError` on 4K/8K wallpaper downloads.

### Smart LRU Cache

- Default limit: **300 MB** (images) + **unlimited video** (capped by device)
- Backed by a `LinkedHashMap` with `accessOrder = true`
- Metadata (key, path, size, last-accessed) persisted to `SharedPreferences`
- Survives app restarts — eviction picks up where it left off

### Download Queue

- Max **2 concurrent** downloads (configurable)
- **3-level priority**: HIGH → NORMAL → LOW
- **Deduplication**: same URL is never queued twice
- **Retry**: exponential back-off — 0 ms → 1 s → 2 s → 4 s (up to 3 attempts)

### 24-Hour Schedule

**Android:** Each entry gets an `AlarmManager.setExactAndAllowWhileIdle`
alarm. When it fires, the `WallpaperSchedulerReceiver` processes the image
on `Dispatchers.IO` and re-schedules for 24 hours later. `BOOT_COMPLETED`
restores all alarms from `SharedPreferences`.

**iOS:** Uses `Timer` objects on the main `RunLoop`. Schedule JSON is
persisted to `UserDefaults` and restored on `applicationDidBecomeActive`.

---

## Events

```typescript
// Static wallpaper changed (schedule)
WallpaperPro.addListener('wallpaperChanged', event => {
  // event: { time, label, url, success, error? }
});

// Live wallpaper download progress
WallpaperPro.addListener('liveWallpaperProgress', event => {
  // event: { type: 'download', progress: 0-100, url }
});
```

---

## Permissions

```typescript
const status = await WallpaperPro.checkPermissions();
// { wallpaper: 'granted'|'denied'|'prompt',
//   storage:   'granted'|'denied'|'prompt',
//   notifications: 'granted'|'denied'|'prompt' }

await WallpaperPro.requestPermissions();
```

---

## Troubleshooting

**Wallpaper not changing on schedule (Android)**
- Grant `SCHEDULE_EXACT_ALARM` (Android 12+)
- Set app battery mode to "Unrestricted"
- Check Logcat: `adb logcat -s WallpaperPro.Sched WallpaperPro.Recv`

**OOM crash on large image**
- Reduce `quality` to 85 or lower
- Use `cropMode: 'fill'` (default) — the plugin downsamples to screen size

**Live wallpaper not showing in picker**
- Ensure the `<service>` block is in your *app's* `AndroidManifest.xml`
- Check `android:permission="android.permission.BIND_WALLPAPER"` is present

**iOS share sheet not appearing**
- Add `NSPhotoLibraryAddUsageDescription` to `Info.plist`
- Call `requestPermissions()` before `setWallpaper()`

---

## License

MIT
