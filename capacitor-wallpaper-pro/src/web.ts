// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  web.ts  (v2)
// ─────────────────────────────────────────────────────────────────────────────
import { WebPlugin, type PluginListenerHandle } from '@capacitor/core';
import type {
  WallpaperProPlugin, SetWallpaperOptions, SetGradientWallpaperOptions,
  SetRandomWallpaperOptions, Schedule24HourOptions, SetLiveWallpaperOptions,
  WallpaperInfo, PermissionStatus, WallpaperChangedEvent,
  LiveWallpaperProgressEvent, CacheInfo, WallpaperFilter, HistoryEntry,
  SetDepthWallpaperOptions, DepthWallpaperProgressEvent,
} from './definitions';

export class WallpaperProWeb extends WebPlugin implements WallpaperProPlugin {

  private history: HistoryEntry[] = [];

  // ── filter → CSS ──────────────────────────────────────────────────────

  private buildCssFilter(f: WallpaperFilter = {}): string {
    const p: string[] = [];
    if (f.blur)       p.push(`blur(${f.blur}px)`);
    if (f.brightness) p.push(`brightness(${f.brightness})`);
    if (f.contrast)   p.push(`contrast(${f.contrast})`);
    if (f.saturation) p.push(`saturate(${f.saturation})`);
    if (f.grayscale)  p.push('grayscale(1)');
    if (f.sepia)      p.push(`sepia(${f.sepia})`);
    if (f.hue)        p.push(`hue-rotate(${f.hue}deg)`);
    if (f.opacity !== undefined) p.push(`opacity(${f.opacity})`);
    return p.join(' ');
  }

  private async processToBlob(url: string, filter: WallpaperFilter = {}, quality = 95): Promise<Blob> {
    return new Promise((resolve, reject) => {
      const img = new Image(); img.crossOrigin = 'anonymous';
      img.onload = () => {
        const canvas = document.createElement('canvas');
        canvas.width = img.naturalWidth; canvas.height = img.naturalHeight;
        const ctx = canvas.getContext('2d')!;
        ctx.filter = this.buildCssFilter(filter);
        ctx.drawImage(img, 0, 0);
        if (filter.vignette && filter.vignette > 0) {
          const g = ctx.createRadialGradient(canvas.width/2, canvas.height/2, canvas.width*0.3,
            canvas.width/2, canvas.height/2, canvas.width*0.7);
          g.addColorStop(0, 'rgba(0,0,0,0)');
          g.addColorStop(1, `rgba(0,0,0,${filter.vignette})`);
          ctx.filter = 'none'; ctx.fillStyle = g;
          ctx.fillRect(0, 0, canvas.width, canvas.height);
        }
        if (filter.tintColor) {
          ctx.filter = 'none'; ctx.globalAlpha = filter.tintOpacity ?? 0.3;
          ctx.fillStyle = filter.tintColor;
          ctx.fillRect(0, 0, canvas.width, canvas.height);
          ctx.globalAlpha = 1;
        }
        canvas.toBlob(b => b ? resolve(b) : reject(new Error('toBlob failed')), 'image/jpeg', quality / 100);
      };
      img.onerror = () => reject(new Error(`Cannot load ${url}`));
      img.src = url;
    });
  }

  private download(blob: Blob, name: string) {
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob); a.download = name; a.click();
    URL.revokeObjectURL(a.href);
  }

  // ── API ───────────────────────────────────────────────────────────────

  async setWallpaper(options: SetWallpaperOptions): Promise<{ success: boolean; message: string }> {
    const blob = await this.processToBlob(options.url, options.filter, options.quality ?? 95);
    this.download(blob, 'wallpaper.jpg');
    this.history.unshift({ url: options.url, target: options.target ?? 'both',
      label: options.label, filterJson: JSON.stringify(options.filter),
      timestamp: Date.now(), date: new Date().toISOString(), isLive: false });
    return { success: true, message: 'Web: processed image downloaded. Use native build to set OS wallpaper.' };
  }

  async setGradientWallpaper(options: SetGradientWallpaperOptions): Promise<{ success: boolean; message: string }> {
    const canvas = document.createElement('canvas');
    canvas.width = 1080; canvas.height = 1920;
    const ctx = canvas.getContext('2d')!;
    const g = options.gradient;
    const colors = g.colors ?? ['#000', '#fff'];
    let grad: CanvasGradient;
    if (g.type === 'radial') {
      grad = ctx.createRadialGradient(540, 960, 0, 540, 960, 960);
    } else {
      const angle = ((g.angle ?? 180) * Math.PI) / 180;
      grad = ctx.createLinearGradient(
        540 - Math.cos(angle) * 540, 960 - Math.sin(angle) * 960,
        540 + Math.cos(angle) * 540, 960 + Math.sin(angle) * 960,
      );
    }
    colors.forEach((c, i) => grad.addColorStop(g.stops?.[i] ?? i / (colors.length - 1), c));
    ctx.fillStyle = grad; ctx.fillRect(0, 0, 1080, 1920);
    canvas.toBlob(b => b && this.download(b, 'gradient-wallpaper.jpg'), 'image/jpeg', 0.95);
    return { success: true, message: 'Web: gradient wallpaper downloaded.' };
  }

  async setRandomWallpaper(options: SetRandomWallpaperOptions): Promise<{ success: boolean; message: string; selectedUrl: string }> {
    const url = options.urls[Math.floor(Math.random() * options.urls.length)];
    await this.setWallpaper({ ...options, url });
    return { success: true, message: 'Random wallpaper downloaded.', selectedUrl: url };
  }

  async schedule24HourWallpapers(options: Schedule24HourOptions): Promise<{ success: boolean; message: string }> {
    localStorage.setItem('wp_pro_schedule', JSON.stringify(options));
    return { success: false, message: 'Web: scheduling requires Android native build.' };
  }

  async clearSchedule(): Promise<{ success: boolean }> {
    localStorage.removeItem('wp_pro_schedule'); return { success: true };
  }

  async getWallpaperInfo(): Promise<WallpaperInfo> {
    return {
      scheduleActive: false, scheduleCount: 0,
      capabilities: {
        canSetHomeScreen: false, canSetLockScreen: false, canSetDual: false,
        supportsParallax: false, supportsFilters: true, supportsScheduling: false,
        supportsLiveWallpaper: false, supportsGradient: true, supportsTextOverlay: false,
      },
    };
  }

  async setLiveWallpaper(options: SetLiveWallpaperOptions): Promise<{ success: boolean; message: string; videoPath: string }> {
    return { success: false, message: 'Web: live wallpaper requires Android native build.', videoPath: options.url };
  }

  async getHistory(options?: { limit?: number }): Promise<{ history: HistoryEntry[]; count: number }> {
    const list = this.history.slice(0, options?.limit ?? 50);
    return { history: list, count: list.length };
  }

  async undoWallpaper(): Promise<{ success: boolean; message: string; restoredUrl?: string }> {
    const prev = this.history[1];
    if (!prev) return { success: false, message: 'No previous wallpaper.' };
    await this.setWallpaper({ url: prev.url });
    return { success: true, message: 'Previous wallpaper restored.', restoredUrl: prev.url };
  }

  async clearHistory(): Promise<{ success: boolean }> { this.history = []; return { success: true }; }

  async checkPermissions(): Promise<PermissionStatus> {
    return { wallpaper: 'granted', storage: 'granted', notifications: 'granted' };
  }

  async requestPermissions(): Promise<PermissionStatus> { return this.checkPermissions(); }

  async preloadWallpaper(options: { url: string }): Promise<{ success: boolean; cachedPath: string }> {
    await fetch(options.url); return { success: true, cachedPath: options.url };
  }

  async setDepthWallpaper(options: SetDepthWallpaperOptions): Promise<{ success: boolean; message: string; bgPath: string; fgPath: string }> {
    return {
      success: false,
      message: 'Web: depth wallpaper requires Android native build.',
      bgPath: options.backgroundUrl,
      fgPath: options.foregroundUrl,
    };
  }

  async clearCache(): Promise<{ success: boolean; bytesFreed: number }> { return { success: true, bytesFreed: 0 }; }
  async clearVideoCache(): Promise<{ success: boolean; bytesFreed: number }> { return { success: true, bytesFreed: 0 }; }

  async getCacheInfo(): Promise<CacheInfo> {
    return { imageCacheBytes: 0, videoCacheBytes: 0, totalBytes: 0, imageCacheMB: '0.00', videoCacheMB: '0.00' };
  }

  async addListener(eventName: 'wallpaperChanged', listenerFunc: (event: WallpaperChangedEvent) => void): Promise<PluginListenerHandle>;
  async addListener(eventName: 'liveWallpaperProgress', listenerFunc: (event: LiveWallpaperProgressEvent) => void): Promise<PluginListenerHandle>;
  async addListener(eventName: 'depthWallpaperProgress', listenerFunc: (event: DepthWallpaperProgressEvent) => void): Promise<PluginListenerHandle>;
  async addListener(eventName: string, listenerFunc: any): Promise<PluginListenerHandle> {
    return super.addListener(eventName, listenerFunc);
  }
}
