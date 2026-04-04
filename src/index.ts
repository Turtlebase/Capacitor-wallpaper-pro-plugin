// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  index.ts
// ─────────────────────────────────────────────────────────────────────────────
import { registerPlugin } from '@capacitor/core';
import type { WallpaperProPlugin } from './definitions';

const WallpaperPro = registerPlugin<WallpaperProPlugin>('WallpaperPro', {
  web: () => import('./web').then(m => new m.WallpaperProWeb()),
});

export * from './definitions';
export { WallpaperPro };
