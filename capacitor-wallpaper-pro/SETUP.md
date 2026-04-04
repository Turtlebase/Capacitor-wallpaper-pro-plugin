# capacitor-wallpaper-pro — Complete Setup Guide

This guide walks you through everything from a brand-new Capacitor project to a
fully working wallpaper app.  Follow the sections that apply to your project.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Create a New Capacitor Project (optional)](#2-create-a-new-capacitor-project-optional)
3. [Install the Plugin](#3-install-the-plugin)
4. [Android Setup](#4-android-setup)
   - 4.1 [Minimum SDK](#41-minimum-sdk)
   - 4.2 [Permissions](#42-permissions)
   - 4.3 [Register the Live Wallpaper Service](#43-register-the-live-wallpaper-service)
   - 4.4 [MainActivity](#44-mainactivity)
   - 4.5 [Gradle Sync](#45-gradle-sync)
   - 4.6 [Battery Optimisation (Scheduling)](#46-battery-optimisation-scheduling)
   - 4.7 [Exact Alarm Permission (Android 12+)](#47-exact-alarm-permission-android-12)
5. [iOS Setup](#5-ios-setup)
   - 5.1 [Info.plist](#51-infoplist)
   - 5.2 [Pod Install](#52-pod-install)
   - 5.3 [iOS Limitation Explained](#53-ios-limitation-explained)
6. [Initialise the Plugin in Your App](#6-initialise-the-plugin-in-your-app)
7. [Request Permissions](#7-request-permissions)
8. [Set a Static Wallpaper](#8-set-a-static-wallpaper)
9. [Set a Live Video Wallpaper](#9-set-a-live-video-wallpaper)
10. [Use Filters](#10-use-filters)
11. [Use Text Overlay](#11-use-text-overlay)
12. [Set a Gradient Wallpaper](#12-set-a-gradient-wallpaper)
13. [Set a Random Wallpaper](#13-set-a-random-wallpaper)
14. [Schedule Wallpapers](#14-schedule-wallpapers)
    - 14.1 [Daily Schedule](#141-daily-schedule)
    - 14.2 [Weekly Schedule](#142-weekly-schedule)
    - 14.3 [Interval Schedule](#143-interval-schedule)
    - 14.4 [Cancel a Schedule](#144-cancel-a-schedule)
15. [History and Undo](#15-history-and-undo)
16. [Cache Management](#16-cache-management)
17. [Listen to Events](#17-listen-to-events)
18. [Use the Example Integration Layer](#18-use-the-example-integration-layer)
19. [Publish to GitHub](#19-publish-to-github)
20. [Publish to npm](#20-publish-to-npm)
21. [Troubleshooting](#21-troubleshooting)
22. [Full File Reference](#22-full-file-reference)

---

## 1. Prerequisites

Make sure you have the following installed and working **before** you start.

| Tool | Minimum version | Check |
|---|---|---|
| Node.js | 18 LTS | `node -v` |
| npm | 9+ | `npm -v` |
| Capacitor CLI | 6+ | `npx cap --version` |
| Android Studio | Hedgehog (2023.1+) | Open → Help → About |
| JDK | 17 | `java -version` |
| Android SDK | API 22+ installed | SDK Manager in Android Studio |
| Xcode (iOS only) | 15+ | `xcode-select -v` |
| CocoaPods (iOS only) | 1.13+ | `pod --version` |

---

## 2. Create a New Capacitor Project (optional)

Skip this if you already have a project.

```bash
# Plain Capacitor + TypeScript
npm init @capacitor/app my-wallpaper-app
cd my-wallpaper-app
npm install @capacitor/android @capacitor/ios
npx cap add android
npx cap add ios
```

Or with Ionic:

```bash
npm install -g @ionic/cli
ionic start my-wallpaper-app blank --type=react --capacitor
cd my-wallpaper-app
ionic capacitor add android
ionic capacitor add ios
```

---

## 3. Install the Plugin

```bash
npm install capacitor-wallpaper-pro
npx cap sync
```

`npx cap sync` does three things:
- Copies your web build into the native projects
- Installs/updates CocoaPods on iOS
- Updates `capacitor.config.ts` references

You should see output like:

```
✔  update android  in 1.23s
✔  update ios      in 2.45s
```

---

## 4. Android Setup

### 4.1 Minimum SDK

Open `android/variables.gradle` and confirm `minSdkVersion` is **22 or higher**:

```groovy
// android/variables.gradle
ext {
    minSdkVersion    = 22    // ← must be 22+
    compileSdkVersion = 34
    targetSdkVersion  = 34
    androidxAppCompatVersion = '1.6.1'
}
```

### 4.2 Permissions

The plugin's own `AndroidManifest.xml` already declares all needed permissions.
Gradle merges them automatically.  You do **not** need to copy them.

The following are declared by the plugin:

```
SET_WALLPAPER
SET_WALLPAPER_HINTS
READ_EXTERNAL_STORAGE   (max SDK 32)
WRITE_EXTERNAL_STORAGE  (max SDK 28)
INTERNET
ACCESS_NETWORK_STATE
RECEIVE_BOOT_COMPLETED
SCHEDULE_EXACT_ALARM
USE_EXACT_ALARM
POST_NOTIFICATIONS
WAKE_LOCK
```

If you want users to receive silent change notifications, add this one line
inside `<manifest>` in `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 4.3 Register the Live Wallpaper Service

> ⚠️ **This step is required if you use `setLiveWallpaper()`.**
> Android's manifest merger does not always propagate `<service>` entries from
> library modules.  Add the following block manually inside `<application>` in
> `android/app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        ...>

        <!-- ─────────────────────────────────────────────────── -->
        <!--  ADD THIS BLOCK for live video wallpapers           -->
        <!-- ─────────────────────────────────────────────────── -->
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
        <!-- ─────────────────────────────────────────────────── -->

        <activity android:name=".MainActivity" ...>
            ...
        </activity>

    </application>
</manifest>
```

Also copy the plugin's resource file into your app's `res/xml/` folder.
Create `android/app/src/main/res/xml/` if it doesn't exist, then create
`video_live_wallpaper.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<wallpaper
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/app_name"
    android:thumbnail="@mipmap/ic_launcher" />
```

### 4.4 MainActivity

No changes needed.  Capacitor 4+ auto-registers all installed plugins.

```kotlin
// android/app/src/main/java/…/MainActivity.kt
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity()
// That's it — nothing else required
```

### 4.5 Gradle Sync

After editing any Gradle or manifest file:

1. Open the `android/` folder in Android Studio
2. Click **File → Sync Project with Gradle Files**
3. Wait for the sync to complete (bottom status bar)
4. If you see dependency conflicts, add a resolution to
   `android/app/build.gradle`:

```groovy
configurations.all {
    resolutionStrategy {
        force 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
        force 'androidx.media3:media3-exoplayer:1.3.1'
    }
}
```

### 4.6 Battery Optimisation (Scheduling)

On Xiaomi, Huawei, OPPO and Samsung devices, the OS aggressively kills
background processes.  For schedules to fire reliably you need the user to
whitelist your app.

**Ask at runtime:**

```typescript
import { WallpaperPro } from 'capacitor-wallpaper-pro';

// Check if we need to ask
const { notifications } = await WallpaperPro.checkPermissions();
if (notifications !== 'granted') {
  await WallpaperPro.requestPermissions();
}
```

**Deeplink to battery settings (Android):**

```typescript
import { App } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';

if (Capacitor.getPlatform() === 'android') {
  // Opens battery optimisation settings for your specific app
  (window as any).cordova?.plugins?.diagnostic?.requestIgnoreBatteryOptimizations(
    () => {}, () => {}
  );
}
```

### 4.7 Exact Alarm Permission (Android 12+)

On Android 12 (API 31+), `SCHEDULE_EXACT_ALARM` requires user approval.

Check and request it with:

```typescript
const info = await WallpaperPro.getWallpaperInfo();
// info.capabilities.supportsScheduling tells you if scheduling will work
```

If the user has denied exact alarms, guide them to:
**Settings → Apps → Your App → Alarms & Reminders → Allow**

---

## 5. iOS Setup

### 5.1 Info.plist

The plugin saves processed images to the Photos library so the user can set
them as wallpapers from the iOS Settings app.  Add the photo library
permission key to `ios/App/App/Info.plist`:

```xml
<key>NSPhotoLibraryAddUsageDescription</key>
<string>WallpaperPro saves your wallpaper to the photo library so you can set it from iOS Settings.</string>
```

Open the file in Xcode:
1. Open `ios/App/App.xcworkspace` in Xcode
2. In the Project Navigator click `App/Info.plist`
3. Click the `+` button on any row
4. Type `NSPhotoLibraryAddUsageDescription`
5. Set Value to your description string

### 5.2 Pod Install

After `npx cap sync`, CocoaPods should run automatically.  If it doesn't:

```bash
cd ios
pod install --repo-update
cd ..
```

Expected output:

```
Installing CapacitorWallpaperPro (1.0.0)
Pod installation complete!
```

Open the workspace (not the `.xcodeproj`) in Xcode:

```bash
open ios/App/App.xcworkspace
```

### 5.3 iOS Limitation Explained

Apple does not provide a public API for third-party apps to set the wallpaper
directly.  The plugin handles this as gracefully as possible:

1. Downloads the image
2. Applies all filters natively via Core Image
3. Saves the result to the Photos library
4. Automatically presents a share sheet
5. The user taps **"Use as Wallpaper"** — one tap, done

This is the only way to do it on iOS.  Live wallpapers are not supported on iOS.

---

## 6. Initialise the Plugin in Your App

Import once at the top of your entry file or a dedicated service file:

```typescript
// src/services/wallpaper.ts  (or wherever you keep services)
import { WallpaperPro } from 'capacitor-wallpaper-pro';

export { WallpaperPro };
```

**Angular — add to `app.component.ts`:**

```typescript
import { Component, OnInit } from '@angular/core';
import { WallpaperPro } from 'capacitor-wallpaper-pro';

@Component({ selector: 'app-root', templateUrl: 'app.component.html' })
export class AppComponent implements OnInit {
  async ngOnInit() {
    await WallpaperPro.requestPermissions();
  }
}
```

**React — add to `App.tsx`:**

```tsx
import { useEffect } from 'react';
import { WallpaperPro } from 'capacitor-wallpaper-pro';

function App() {
  useEffect(() => {
    WallpaperPro.requestPermissions();
  }, []);
  // ...
}
```

**Vue — add to `App.vue`:**

```vue
<script setup lang="ts">
import { onMounted } from 'vue';
import { WallpaperPro } from 'capacitor-wallpaper-pro';

onMounted(() => {
  WallpaperPro.requestPermissions();
});
</script>
```

---

## 7. Request Permissions

Call this once early in your app lifecycle — before any wallpaper operation.

```typescript
import { WallpaperPro } from 'capacitor-wallpaper-pro';

async function initWallpaper() {
  // Check what we have
  const current = await WallpaperPro.checkPermissions();

  // Request anything that's missing
  if (
    current.wallpaper     !== 'granted' ||
    current.storage       !== 'granted'
  ) {
    const result = await WallpaperPro.requestPermissions();
    console.log('Permissions after request:', result);
  }
}
```

**What each permission controls:**

| Permission | Used for |
|---|---|
| `wallpaper` | `SET_WALLPAPER` — set static wallpapers |
| `storage` | Read/write cached image and video files |
| `notifications` | Silent change notifications (optional) |

---

## 8. Set a Static Wallpaper

### Minimal call

```typescript
await WallpaperPro.setWallpaper({
  url: 'https://picsum.photos/1080/1920',
});
// Defaults: target='both', no filters, no text, quality=95
```

### With target selection

```typescript
// Home screen only
await WallpaperPro.setWallpaper({
  url:    'https://example.com/photo.jpg',
  target: 'home',
});

// Lock screen only
await WallpaperPro.setWallpaper({
  url:    'https://example.com/photo.jpg',
  target: 'lock',
});

// Both (default)
await WallpaperPro.setWallpaper({
  url:    'https://example.com/photo.jpg',
  target: 'both',
});
```

### With all options

```typescript
const result = await WallpaperPro.setWallpaper({
  url:               'https://example.com/photo.jpg',
  target:            'both',

  // Parallax — makes canvas wider for launcher swipe depth effect
  parallax:          true,
  parallaxIntensity: 1.6,     // 1.1–3.0

  // Crop control
  cropMode:          'fill',  // 'fill' | 'fit' | 'center' | 'stretch'
  cropX:             0.5,     // 0.0=left edge … 1.0=right edge
  cropY:             0.3,     // 0.0=top … 1.0=bottom (0.3 = upper third)

  quality:           95,       // JPEG output quality 0–100
  cache:             true,     // use LRU disk cache

  // Filters — see Section 10
  filter: { brightness: 1.1, saturation: 1.3, vignette: 0.3 },

  // Text overlay — see Section 11
  textOverlay: { text: 'Good Morning', fontSize: 60, color: '#FFFFFF' },

  // Label saved to history
  label: 'Morning photo',
});

console.log(result.success);  // true
console.log(result.message);  // "Wallpaper set successfully (target=both)"
```

---

## 9. Set a Live Video Wallpaper

> **Android only.**  On iOS this returns `success: false` with an explanatory message.

### Minimal call

```typescript
await WallpaperPro.setLiveWallpaper({
  url: 'https://example.com/loop.mp4',
});
// Defaults: target='both', mute=true, loop=true, speed=1.0
```

### With target and options

```typescript
// Listen for download progress first (remote URLs only)
const progressHandle = await WallpaperPro.addListener(
  'liveWallpaperProgress',
  event => {
    console.log(`Downloading: ${event.progress}%`);
    updateProgressBar(event.progress);
  }
);

const result = await WallpaperPro.setLiveWallpaper({
  url:    'https://example.com/nature_loop.mp4',
  target: 'home',    // 'home' | 'lock' | 'both'
  mute:   true,      // almost always true for wallpapers
  loop:   true,
  speed:  1.0,       // 0.25×–4.0×  (slow motion or fast forward)
});

// Clean up listener
progressHandle.remove();

console.log(result.message);
// "Live wallpaper set directly (target=home)"
// or
// "Live wallpaper preview opened. Tap 'Set Wallpaper' to confirm."
```

### Supported video formats

| Format | Extension | Notes |
|---|---|---|
| MP4 (H.264) | `.mp4` | Most common, best compatibility |
| MP4 (H.265) | `.mp4` | Smaller files, Android 5+ |
| WebM (VP9) | `.webm` | Open format |
| Matroska | `.mkv` | |
| QuickTime | `.mov` | |
| HLS Stream | `.m3u8` | Live/adaptive streaming |
| DASH Stream | `.mpd` | Adaptive streaming |
| Local file | `/data/…` | Absolute path on device |

### What happens step by step

1. If the URL is remote: video downloads in 64 KB chunks, progress events fire
2. Video is saved to the app cache directory
3. Config (path, speed, mute, loop, target) is saved to SharedPreferences
4. `VideoLiveWallpaperService` reads the config when it starts
5. On API 24+: attempts to set live wallpaper directly to the target flag
6. Fallback: opens the system live wallpaper preview screen
7. User taps **"Set Wallpaper"** once to confirm (system requirement)

---

## 10. Use Filters

All filters are **optional**.  Only pass the ones the user has changed.

```typescript
// Only blur — nothing else sent to plugin
await WallpaperPro.setWallpaper({
  url:    'https://example.com/photo.jpg',
  filter: { blur: 8 },
});

// Night mode recipe
await WallpaperPro.setWallpaper({
  url:    'https://example.com/photo.jpg',
  filter: {
    brightness:  0.6,
    saturation:  0.5,
    temperature: -0.2,
    vignette:    0.5,
  },
});

// Colour tint (blue overlay)
await WallpaperPro.setWallpaper({
  url:    'https://example.com/photo.jpg',
  filter: {
    tintColor:   '#1a1aff',
    tintOpacity: 0.25,
  },
});
```

### Complete filter reference

| Property | Type | Range | Default | Effect |
|---|---|---|---|---|
| `blur` | number | 0–25 | 0 | Gaussian blur radius |
| `brightness` | number | 0.0–3.0 | 1.0 | Lighten / darken |
| `contrast` | number | 0.0–3.0 | 1.0 | Flatten / punch mid-tones |
| `saturation` | number | 0.0–3.0 | 1.0 | Desaturate / hyper-saturate |
| `grayscale` | boolean | — | false | Full B&W conversion |
| `sepia` | number | 0.0–1.0 | 0 | Warm brown film tone |
| `vignette` | number | 0.0–1.0 | 0 | Dark radial edge fade |
| `hue` | number | -180–180 | 0 | Rotate all colours |
| `temperature` | number | -1.0–1.0 | 0 | Warm (+) / Cool (-) |
| `opacity` | number | 0.0–1.0 | 1.0 | Composite over black |
| `tintColor` | string | `#RRGGBB` | — | Solid colour overlay |
| `tintOpacity` | number | 0.0–1.0 | 0.3 | Tint strength |

---

## 11. Use Text Overlay

Text overlay is **entirely optional**.  Only include `textOverlay` if the user
has typed something.

```typescript
// Minimal — just the text
await WallpaperPro.setWallpaper({
  url:         'https://example.com/bg.jpg',
  textOverlay: {
    text: 'Hello World',
  },
});

// Full example
await WallpaperPro.setWallpaper({
  url: 'https://example.com/bg.jpg',
  textOverlay: {
    text:             '"Be the change you wish to see."',
    fontSize:         56,
    color:            '#FFFFFF',
    bold:             true,
    italic:           true,
    alignment:        'center',     // 'left' | 'center' | 'right'
    anchorX:          0.5,          // 0=left … 1=right (0.5 = centre)
    anchorY:          0.82,         // 0=top  … 1=bottom (0.82 = lower third)
    shadowColor:      '#00000099',
    shadowRadius:     12,
    shadowDx:         2,
    shadowDy:         2,
    backgroundColor:  '#00000066',  // semi-transparent pill
    backgroundPadding: 24,
    backgroundRadius:  16,
    maxWidthFraction: 0.8,          // wraps at 80% of screen width
    lineSpacing:      1.3,
  },
});
```

### Text on a gradient (no image needed)

```typescript
await WallpaperPro.setGradientWallpaper({
  gradient: {
    type:   'linear',
    colors: ['#0f0c29', '#302b63', '#24243e'],
    angle:  160,
  },
  textOverlay: {
    text:    '"The only way out is through."',
    fontSize: 58,
    color:   '#E8E8E8',
    italic:  true,
    anchorY: 0.5,
  },
});
```

---

## 12. Set a Gradient Wallpaper

No image URL needed.  Everything is generated natively at full screen resolution.

```typescript
// Simple two-colour linear gradient
await WallpaperPro.setGradientWallpaper({
  gradient: {
    type:   'linear',
    colors: ['#141E30', '#243B55'],
    angle:  180,         // 0=top→bottom, 90=left→right, 180=bottom→top
  },
  target: 'both',
});

// Multi-stop radial gradient
await WallpaperPro.setGradientWallpaper({
  gradient: {
    type:   'radial',
    colors: ['#FFFFFF', '#7F00FF', '#000000'],
    stops:  [0.0, 0.4, 1.0],
  },
});

// Rainbow sweep
await WallpaperPro.setGradientWallpaper({
  gradient: {
    type:   'sweep',
    colors: ['#FF0000','#FF8800','#FFFF00','#00FF00','#0000FF','#8800FF','#FF0000'],
  },
});
```

---

## 13. Set a Random Wallpaper

```typescript
const { success, selectedUrl } = await WallpaperPro.setRandomWallpaper({
  urls: [
    'https://example.com/wallpaper1.jpg',
    'https://example.com/wallpaper2.jpg',
    'https://example.com/wallpaper3.jpg',
  ],
  target:   'both',
  parallax: true,
  // filter is optional — only include if needed
});

console.log('Plugin picked:', selectedUrl);
```

---

## 14. Schedule Wallpapers

### 14.1 Daily Schedule

Fires at the same time every day.  The current slot is applied immediately on
registration, then automatically at each `HH:mm` time going forward.

```typescript
await WallpaperPro.schedule24HourWallpapers({
  scheduleType: 'daily',
  target:       'both',
  parallax:     true,
  preloadAll:   true,   // download all images now so changes are instant
  schedule: [
    {
      time:  '06:00',
      url:   'https://example.com/sunrise.jpg',
      label: 'Sunrise',
      filter: { temperature: 0.4, brightness: 1.05 },
    },
    {
      time:  '12:00',
      url:   'https://example.com/noon.jpg',
      label: 'Noon',
    },
    {
      time:  '18:00',
      url:   'https://example.com/golden.jpg',
      label: 'Golden Hour',
      filter: { temperature: 0.6, saturation: 1.4 },
    },
    {
      time:  '22:00',
      url:   'https://example.com/night.jpg',
      label: 'Night',
      filter: { brightness: 0.6, saturation: 0.5 },
    },
  ],
});
```

### 14.2 Weekly Schedule

Each entry fires on a specific day of the week.
`dayOfWeek` values: `1`=Monday … `7`=Sunday.

```typescript
await WallpaperPro.schedule24HourWallpapers({
  scheduleType: 'weekly',
  target:       'both',
  schedule: [
    { time: '08:00', dayOfWeek: 1, url: mondayUrl,   label: 'Monday Motivation' },
    { time: '08:00', dayOfWeek: 2, url: tuesdayUrl,  label: 'Tuesday' },
    { time: '08:00', dayOfWeek: 3, url: wednesdayUrl,label: 'Wednesday' },
    { time: '08:00', dayOfWeek: 4, url: thursdayUrl, label: 'Thursday' },
    { time: '08:00', dayOfWeek: 5, url: fridayUrl,   label: 'Friday Vibes' },
    { time: '09:00', dayOfWeek: 6, url: saturdayUrl, label: 'Weekend Chill' },
    { time: '10:00', dayOfWeek: 7, url: sundayUrl,   label: 'Sunday Reset' },
  ],
});
```

### 14.3 Interval Schedule

Cycles through the list every N minutes.  Order is preserved — after the last
entry it wraps back to the first.

```typescript
await WallpaperPro.schedule24HourWallpapers({
  scheduleType:    'interval',
  intervalMinutes: 120,    // change every 2 hours
  target:          'both',
  preloadAll:      true,
  schedule: [
    { time: '00:00', url: 'https://example.com/1.jpg', label: 'Scene 1' },
    { time: '00:00', url: 'https://example.com/2.jpg', label: 'Scene 2' },
    { time: '00:00', url: 'https://example.com/3.jpg', label: 'Scene 3' },
    { time: '00:00', url: 'https://example.com/4.jpg', label: 'Scene 4' },
  ],
});
// time is ignored in interval mode — only used for daily/weekly
```

### 14.4 Cancel a Schedule

```typescript
await WallpaperPro.clearSchedule();
// All alarms cancelled, schedule removed from storage
```

### Check Schedule Status

```typescript
const info = await WallpaperPro.getWallpaperInfo();

if (info.scheduleActive) {
  console.log(`${info.scheduleCount} entries active`);
  console.log(`Next change: ${info.nextChangeTime} — ${info.nextChangeLabel}`);
  console.log(`Current:     ${info.currentLabel}`);
}
```

---

## 15. History and Undo

```typescript
// Get last 20 wallpaper changes
const { history, count } = await WallpaperPro.getHistory({ limit: 20 });

history.forEach(entry => {
  console.log(`${entry.date}  ${entry.label ?? entry.url.slice(-30)}`);
  console.log(`  target=${entry.target}  live=${entry.isLive}`);
});

// Restore the previous wallpaper
const undo = await WallpaperPro.undoWallpaper();
if (undo.success) {
  console.log('Restored:', undo.restoredUrl);
} else {
  console.log(undo.message);  // "No previous wallpaper in history"
}

// Wipe all history
await WallpaperPro.clearHistory();
```

---

## 16. Cache Management

### Pre-warm the cache

```typescript
// High priority — jumps the download queue ahead of normal requests
await WallpaperPro.preloadWallpaper({
  url:      'https://example.com/hero.jpg',
  priority: 'high',   // 'high' | 'normal' | 'low'
});
```

### Inspect cache usage

```typescript
const info = await WallpaperPro.getCacheInfo();

console.log(`Image cache:  ${info.imageCacheMB} MB`);
console.log(`Video cache:  ${info.videoCacheMB} MB`);
console.log(`Total:        ${info.totalBytes} bytes`);
```

### Clear cache

```typescript
// Images only (does not delete videos)
const { bytesFreed } = await WallpaperPro.clearCache();
console.log(`Freed ${(bytesFreed / 1024 / 1024).toFixed(1)} MB`);

// Videos only
await WallpaperPro.clearVideoCache();
```

---

## 17. Listen to Events

```typescript
// Fires every time the scheduled wallpaper auto-changes
const changeHandle = await WallpaperPro.addListener(
  'wallpaperChanged',
  event => {
    console.log(`[${event.time}] ${event.label}`);
    console.log(`URL: ${event.url}`);
    console.log(`Success: ${event.success}`);
  }
);

// Fires during video download (0–100%)
const progressHandle = await WallpaperPro.addListener(
  'liveWallpaperProgress',
  event => {
    console.log(`Video download: ${event.progress}%`);
  }
);

// Remove a specific listener
changeHandle.remove();

// Remove all listeners at once (e.g. on component unmount)
await WallpaperPro.removeAllListeners();
```

---

## 18. Use the Example Integration Layer

The `example/` folder contains two ready-made files you can copy directly into
your app:

### `WallpaperManager.ts`

This is the **only file in your app that imports the plugin**.
It defines UI state types and converters that build clean plugin options from
whatever the user has selected — nothing is forced.

```bash
cp example/WallpaperManager.ts src/services/WallpaperManager.ts
```

Usage in your component:

```typescript
import {
  applyWallpaper,
  applyGradientWallpaper,
  applySchedule,
  WallpaperActions,
  DEFAULT_WALLPAPER_STATE,
} from './services/WallpaperManager';

// Minimal — user just picked a URL and a target
const result = await applyWallpaper({
  ...DEFAULT_WALLPAPER_STATE,
  imageUrl: 'https://example.com/photo.jpg',
  target:   'both',
  // filter.enabled = false by default → no filter sent to plugin
  // text.enabled   = false by default → no text sent to plugin
});

// Only send filter if user enabled it
const stateWithFilter = {
  ...DEFAULT_WALLPAPER_STATE,
  imageUrl: 'https://example.com/photo.jpg',
  filter: {
    enabled:    true,   // ← user toggled this ON
    blur:       0,
    brightness: 0.8,    // ← user moved this slider
    // everything else stays at neutral → not sent to plugin
    contrast:   1.0,
    saturation: 1.0,
    grayscale:  false,
    sepia:      0,
    vignette:   0,
    hue:        0,
    temperature: 0,
    opacity:    1.0,
    tintEnabled: false,
    tintColor:  '#000000',
    tintOpacity: 0.3,
  },
  text: {
    enabled: false,  // ← user left this OFF → no text sent
    // ... rest of defaults
  },
};

await applyWallpaper(stateWithFilter);
// Plugin receives: { url, target, filter: { brightness: 0.8 } }
// Nothing else — no blur, no contrast, no text
```

### `WallpaperScreen.tsx`

A complete React wallpaper UI you can use as-is or as a starting point.
All panels are collapsible toggles.

```bash
cp example/WallpaperScreen.tsx src/pages/WallpaperScreen.tsx
```

### `ScheduleScreen.tsx`

A complete schedule management screen with daily/weekly/interval type picker.

```bash
cp example/ScheduleScreen.tsx src/pages/ScheduleScreen.tsx
```

---

## 19. Publish to GitHub

```bash
# 1. Unzip the plugin
unzip capacitor-wallpaper-pro.zip
cd capacitor-wallpaper-pro

# 2. Initialise git
git init
git add .
git commit -m "feat: initial release v1.0.0"

# 3. Create repo on GitHub (GitHub CLI)
gh repo create capacitor-wallpaper-pro --public --source=. --push

# OR push to an existing repo
git remote add origin https://github.com/YOUR_USERNAME/capacitor-wallpaper-pro.git
git branch -M main
git push -u origin main

# 4. Create a release tag
git tag v1.0.0
git push origin v1.0.0
```

The GitHub Actions workflow at `.github/workflows/ci.yml` will:
- Run on every push to `main`
- Build and lint the TypeScript
- **Auto-publish to npm** when you create a release tag

---

## 20. Publish to npm

### First time

```bash
# Login to npm
npm login

# Build the TypeScript output
npm run build

# Dry run to check what will be published
npm publish --dry-run

# Publish
npm publish --access public
```

### Add your npm token to GitHub Actions (for auto-publish)

1. Go to **npmjs.com → Account → Access Tokens → Generate New Token**
2. Choose **Automation** type
3. Copy the token
4. Go to your GitHub repo → **Settings → Secrets → Actions → New secret**
5. Name: `NPM_TOKEN`, Value: paste your token
6. Save

Now every time you push a release tag (`v1.0.0`, `v1.1.0`, etc.) the CI
workflow publishes to npm automatically.

### Update an existing version

```bash
# Bump version in package.json
npm version patch   # 1.0.0 → 1.0.1
# or
npm version minor   # 1.0.0 → 1.1.0

# This also creates a git tag automatically
git push && git push --tags
```

---

## 21. Troubleshooting

### ❌ `SET_WALLPAPER permission denied`

**Cause:** The permission was not merged or was denied by the user.

**Fix:**
1. Add `<uses-permission android:name="android.permission.SET_WALLPAPER"/>` to your **app's** `AndroidManifest.xml`
2. Uninstall and reinstall the app
3. Call `requestPermissions()` before `setWallpaper()`

---

### ❌ `Live wallpaper service not found`

**Cause:** The `<service>` block was not added to the app manifest.

**Fix:** Follow [Section 4.3](#43-register-the-live-wallpaper-service) exactly.
Check that `android:name="com.wallpaperpro.VideoLiveWallpaperService"` matches
the package name exactly (it does if you haven't renamed anything).

---

### ❌ Wallpaper schedule doesn't fire on time

**Cause:** Battery optimisation is killing your app's background processes.

**Fix:**
1. Go to **Settings → Apps → Your App → Battery → Unrestricted**
2. On Android 12+: **Settings → Apps → Your App → Alarms & Reminders → Allow**
3. Check Logcat: `adb logcat -s WallpaperPro.Sched WallpaperPro.Recv`

---

### ❌ `OutOfMemoryError` when setting a large image

**Cause:** Shouldn't happen with this plugin — the OOM guard is built in.
If it still occurs, the image is unusually large.

**Fix:**
- Lower `quality` to `80` or `85`
- The plugin will auto-downsample, but very high resolution + aggressive filters
  can still push memory
- Use `cropMode: 'fill'` (default) to ensure the image is scaled to screen size

---

### ❌ Download fails / times out

**Cause:** Server is slow, or the URL requires authentication headers.

**Fix:**
- The plugin retries 3 times with exponential back-off (0s → 1s → 2s)
- Check the URL is accessible from a browser
- Some CDNs block non-browser User-Agent strings — the plugin sends
  `User-Agent: WallpaperPro/1.0`

---

### ❌ iOS share sheet doesn't appear

**Cause:** `NSPhotoLibraryAddUsageDescription` is missing or permissions denied.

**Fix:**
1. Add the key to `Info.plist` (see [Section 5.1](#51-infoplist))
2. Go to **Settings → Privacy → Photos → Your App → Add Photos Only**
3. Call `requestPermissions()` before `setWallpaper()`

---

### ❌ Gradle sync fails with dependency conflict

**Fix:** Add a resolution strategy to `android/app/build.gradle`:

```groovy
android {
    configurations.all {
        resolutionStrategy {
            force 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
            force 'androidx.media3:media3-exoplayer:1.3.1'
            force 'androidx.media3:media3-common:1.3.1'
        }
    }
}
```

---

### ❌ `npx cap sync` hangs on iOS

**Fix:**
```bash
cd ios
pod deintegrate
pod install --repo-update
cd ..
npx cap sync ios
```

---

### Debug Logcat tags

| Tag | What it shows |
|---|---|
| `WallpaperPro` | Main plugin — method entry/exit |
| `WallpaperPro.Img` | Image download, decode, filter, set |
| `WallpaperPro.Sched` | Alarm scheduling |
| `WallpaperPro.Recv` | Alarm receiver fires |
| `WallpaperPro.LRU` | Cache put/evict |
| `WallpaperPro.Queue` | Download queue events |
| `WallpaperPro.LiveSvc` | ExoPlayer / video wallpaper |
| `WallpaperPro.VidDL` | Video download progress |
| `WallpaperPro.History` | History push/pop |

Run all at once:

```bash
adb logcat -s WallpaperPro WallpaperPro.Img WallpaperPro.Sched \
  WallpaperPro.Recv WallpaperPro.LRU WallpaperPro.Queue \
  WallpaperPro.LiveSvc WallpaperPro.VidDL WallpaperPro.History
```

---

## 22. Full File Reference

```
capacitor-wallpaper-pro/
│
├── src/
│   ├── definitions.ts          TypeScript types for all options + events
│   ├── index.ts                Plugin entry point (registerPlugin)
│   └── web.ts                  Browser fallback (downloads processed image)
│
├── android/
│   ├── build.gradle            Gradle config + ExoPlayer (Media3) dependencies
│   └── src/main/
│       ├── AndroidManifest.xml Permissions + service + receiver declarations
│       ├── res/
│       │   ├── xml/video_live_wallpaper.xml  Live wallpaper descriptor
│       │   └── values/strings.xml
│       └── java/com/wallpaperpro/
│           ├── WallpaperProPlugin.kt          Main plugin — all JS-callable methods
│           ├── ImageProcessor.kt              Download → OOM-safe decode → crop →
│           │                                  filters → text → gradient → setStream
│           ├── VideoLiveWallpaperService.kt   WallpaperService + ExoPlayer engine
│           ├── VideoDownloadManager.kt        Chunked video download + cache
│           ├── WallpaperScheduler.kt          AlarmManager: daily/weekly/interval
│           ├── WallpaperSchedulerReceiver.kt  Alarm fires + BOOT_COMPLETED
│           ├── WallpaperHistory.kt            50-entry ring buffer (SharedPrefs)
│           ├── LRUCacheManager.kt             300 MB LRU disk cache
│           └── DownloadQueue.kt               Priority queue, dedup, retry
│
├── ios/
│   ├── CapacitorWallpaperPro.podspec
│   └── Plugin/
│       ├── WallpaperProPlugin.swift   Main plugin + schedule timers + history
│       ├── ImageProcessor.swift       Core Image filters + gradient + text + cache
│       └── WallpaperProPlugin.m       Objective-C bridge (registers all methods)
│
├── example/
│   ├── WallpaperManager.ts     Integration layer — UI state → plugin calls
│   ├── WallpaperScreen.tsx     Full React wallpaper UI with all optional panels
│   └── ScheduleScreen.tsx      Schedule management UI (daily/weekly/interval)
│
├── .github/workflows/ci.yml   CI: build + lint on push, auto-publish on release
├── package.json
├── tsconfig.json
├── rollup.config.js
├── README.md                  Feature overview + API reference
└── SETUP.md                   ← You are here
```

---

*That's everything.  If you run into anything not covered here, open an issue on GitHub.*

---

## 23. Depth Effect Wallpaper (iOS-style)

The depth wallpaper is the most advanced feature in the plugin.  It composites
three layers in real time using the device's rotation sensor:

```
┌──────────────────────────────────────┐
│  LAYER 3: Foreground PNG             │  ← moves most (nearest)
│           (transparent cutout)       │
├──────────────────────────────────────┤
│  LAYER 2: Real-time Clock            │  ← fixed or barely moves
│           (between the two images)   │
├──────────────────────────────────────┤
│  LAYER 1: Background JPG             │  ← moves least (farthest)
└──────────────────────────────────────┘
            ↑ tilting the phone shifts each layer at a different speed
```

The clock sits **between** the two image layers.  When you tilt the phone,
the foreground subject appears to float in front of the clock, which floats
in front of the background — exactly like iOS depth wallpapers.

### Android Setup for Depth Service

Add this block to your app's `AndroidManifest.xml` inside `<application>`:

```xml
<service
    android:name="com.wallpaperpro.DepthWallpaperService"
    android:enabled="true"
    android:exported="true"
    android:label="WallpaperPro Depth"
    android:permission="android.permission.BIND_WALLPAPER">
    <intent-filter>
        <action android:name="android.service.wallpaper.WallpaperService" />
    </intent-filter>
    <meta-data
        android:name="android.service.wallpaper"
        android:resource="@xml/depth_live_wallpaper" />
</service>
```

Create `android/app/src/main/res/xml/depth_live_wallpaper.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<wallpaper
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/app_name"
    android:thumbnail="@mipmap/ic_launcher" />
```

### Minimal Call

```typescript
await WallpaperPro.setDepthWallpaper({
  backgroundUrl: 'https://example.com/mountain_bg.jpg',
  foregroundUrl: 'https://example.com/person_cutout.png',
});
// Opens system preview → user taps "Set Wallpaper"
```

### Full Call with All Options

```typescript
// Track image download
await WallpaperPro.addListener('depthWallpaperProgress', e => {
  if (e.stage === 'ready') console.log('Images ready, preview opening…');
});

await WallpaperPro.setDepthWallpaper({
  backgroundUrl: 'https://example.com/bg.jpg',
  foregroundUrl: 'https://example.com/fg.png',
  target:        'both',   // 'home' | 'lock' | 'both'

  clock: {
    style:          'digital',    // see styles below
    animation:      'pulse',      // see animations below
    format:         '24h',        // '12h' | '24h'
    position:       { x: 0.5, y: 0.35 },
    color:          '#FFFFFF',
    secondaryColor: '#CCCCCC',
    accentColor:    '#FF6B6B',
    fontSize:       80,
    showSeconds:    true,
    showDate:       true,
    showAmPm:       false,
    shadow:         true,
    shadowRadius:   24,
    shadowColor:    '#CC000000',
    strokeWidth:    4,
    opacity:        1.0,
  },

  depth: {
    bgParallaxFactor:    0.25,  // 0=fixed, 1=full tilt
    clockParallaxFactor: 0.05,  // clock barely moves
    fgParallaxFactor:    1.0,   // foreground pops forward
    maxOffset:           48,    // max pixels to shift
    sensitivity:         1.0,   // sensor sensitivity
    smoothing:           0.08,  // 0=silky smooth, 1=instant
  },
});
```

### Clock Styles

| Style | Description |
|---|---|
| `digital` | Large digital time with sliding seconds |
| `analog` | Classic clock face with smooth-sweep hands |
| `minimal` | Ultra-thin typeface, HH:MM only |
| `neon` | Multi-layer glow effect with pulsing colon |
| `retro` | Authentic 7-segment LCD display |
| `word` | "Half past three" natural language |

### Clock Animations

| Animation | What It Does |
|---|---|
| `none` | Static, no animation |
| `pulse` | Clock gently breathes (scales 1.0↔1.012) every cycle |
| `glow` | Shadow radius oscillates — ambient halo effect |
| `fade` | Opacity pulses softly |
| `slide` | Digits slide up on each second tick |
| `flip` | Eased slide-in on second change |

### Depth Config Tuning Guide

| Effect | Settings |
|---|---|
| Maximum iOS-like depth | `bgFactor: 0.2, clockFactor: 0.0, fgFactor: 1.0, maxOffset: 60` |
| Subtle, calm depth | `bgFactor: 0.15, fgFactor: 0.6, maxOffset: 30, smoothing: 0.04` |
| Very responsive | `sensitivity: 1.5, smoothing: 0.15` |
| Silky smooth | `smoothing: 0.04, sensitivity: 0.8` |
| Fixed clock (no clock parallax) | `clockParallaxFactor: 0.0` |

### How to Create a Good Foreground PNG

The foreground image must be a **PNG with a transparent background**.  The
subject (person, animal, object) should have no background — just the cutout.

Tools to create foreground PNGs:
- **Remove.bg** — automatic background removal
- **Photoshop** — Select Subject → Delete Background
- **GIMP** — Fuzzy Select → Delete
- **iOS Photos app** — long press a subject → Copy (on iOS 16+)

The plugin scales both images to fill the screen with extra margin so tilting
never reveals black edges.  No manual sizing needed.

### How the Sensor Works

The plugin uses `TYPE_ROTATION_VECTOR` (drift-free, fused gyro + accelerometer)
with a fallback to `TYPE_ACCELEROMETER` with a low-pass gravity filter.

- The sensor is registered on a dedicated `HandlerThread` to avoid blocking
  the main thread.
- Sensor values are smoothed with a lerp factor per frame (Choreographer 60fps).
- The sensor unregisters automatically when the wallpaper is not visible
  (saves battery when screen is off or another app is active).

### Logcat tags for depth wallpaper

```bash
adb logcat -s WallpaperPro.LiveSvc WallpaperPro.Depth
```
