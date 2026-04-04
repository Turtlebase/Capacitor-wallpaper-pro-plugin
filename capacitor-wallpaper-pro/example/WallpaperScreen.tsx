// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  example/WallpaperScreen.tsx
//
//  Full example wallpaper screen in React / Ionic.
//  Shows exactly how a real app UI maps to plugin calls.
//  All feature panels (filter, text, parallax) are opt-in toggles.
//  The user only sees options they choose to expand.
// ─────────────────────────────────────────────────────────────────────────────

import React, { useState, useEffect, useCallback } from 'react';
import {
  applyWallpaper,
  applyGradientWallpaper,
  applySchedule,
  WallpaperActions,
  DEFAULT_WALLPAPER_STATE,
  DEFAULT_FILTER_STATE,
  DEFAULT_TEXT_STATE,
  UIWallpaperState,
  UIFilterState,
  UITextState,
} from './WallpaperManager';
import { WallpaperTarget } from '../src/index';

// ─────────────────────────────────────────────────────────────────────────────
//  Sub-component: Target selector (home / lock / both)
// ─────────────────────────────────────────────────────────────────────────────

const TargetSelector: React.FC<{
  value: WallpaperTarget;
  onChange: (v: WallpaperTarget) => void;
}> = ({ value, onChange }) => (
  <div className="section">
    <label className="section-title">Apply To</label>
    <div className="button-group">
      {(['home', 'lock', 'both'] as WallpaperTarget[]).map(t => (
        <button
          key={t}
          className={`target-btn ${value === t ? 'active' : ''}`}
          onClick={() => onChange(t)}
        >
          {t === 'home' ? '🏠 Home' : t === 'lock' ? '🔒 Lock' : '📱 Both'}
        </button>
      ))}
    </div>
  </div>
);

// ─────────────────────────────────────────────────────────────────────────────
//  Sub-component: Filter Panel (collapsible, all sliders optional)
// ─────────────────────────────────────────────────────────────────────────────

const FilterPanel: React.FC<{
  state: UIFilterState;
  onChange: (s: UIFilterState) => void;
}> = ({ state, onChange }) => {
  const set = (key: keyof UIFilterState, val: any) =>
    onChange({ ...state, [key]: val });

  return (
    <div className="panel">
      {/* Master toggle — if off, no filter is sent to plugin at all */}
      <div className="panel-header">
        <span>🎨 Filters</span>
        <Toggle value={state.enabled} onChange={v => set('enabled', v)} />
      </div>

      {state.enabled && (
        <div className="panel-body">
          {/* Each slider only affects the plugin if user moves it */}
          <Slider label="Blur"        min={0}    max={25}   step={0.5} value={state.blur}        onChange={v => set('blur', v)} />
          <Slider label="Brightness"  min={0}    max={2}    step={0.05} value={state.brightness}  onChange={v => set('brightness', v)} neutral={1} />
          <Slider label="Contrast"    min={0}    max={2}    step={0.05} value={state.contrast}    onChange={v => set('contrast', v)}    neutral={1} />
          <Slider label="Saturation"  min={0}    max={3}    step={0.05} value={state.saturation}  onChange={v => set('saturation', v)}  neutral={1} />
          <Slider label="Sepia"       min={0}    max={1}    step={0.05} value={state.sepia}       onChange={v => set('sepia', v)} />
          <Slider label="Vignette"    min={0}    max={1}    step={0.05} value={state.vignette}    onChange={v => set('vignette', v)} />
          <Slider label="Hue"         min={-180} max={180}  step={1}   value={state.hue}         onChange={v => set('hue', v)} />
          <Slider label="Temperature" min={-1}   max={1}    step={0.05} value={state.temperature} onChange={v => set('temperature', v)} />
          <Slider label="Opacity"     min={0}    max={1}    step={0.05} value={state.opacity}     onChange={v => set('opacity', v)}     neutral={1} />

          <CheckRow label="Grayscale" value={state.grayscale} onChange={v => set('grayscale', v)} />

          <div className="sub-panel">
            <CheckRow label="Colour Tint" value={state.tintEnabled} onChange={v => set('tintEnabled', v)} />
            {state.tintEnabled && (
              <>
                <ColorPicker label="Tint Colour" value={state.tintColor} onChange={v => set('tintColor', v)} />
                <Slider label="Tint Strength" min={0} max={1} step={0.05} value={state.tintOpacity} onChange={v => set('tintOpacity', v)} />
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
//  Sub-component: Text Overlay Panel
// ─────────────────────────────────────────────────────────────────────────────

const TextPanel: React.FC<{
  state: UITextState;
  onChange: (s: UITextState) => void;
}> = ({ state, onChange }) => {
  const set = (key: keyof UITextState, val: any) =>
    onChange({ ...state, [key]: val });

  return (
    <div className="panel">
      <div className="panel-header">
        <span>✏️ Text Overlay</span>
        <Toggle value={state.enabled} onChange={v => set('enabled', v)} />
      </div>

      {state.enabled && (
        <div className="panel-body">
          <textarea
            placeholder="Enter your quote or text..."
            value={state.text}
            onChange={e => set('text', e.target.value)}
            rows={3}
          />

          <Slider label="Font Size" min={20} max={120} step={2} value={state.fontSize} onChange={v => set('fontSize', v)} />

          <div className="row">
            <ColorPicker label="Text Colour" value={state.color} onChange={v => set('color', v)} />
            <div className="button-group small">
              <button className={state.bold   ? 'active' : ''} onClick={() => set('bold',   !state.bold)}>   <b>B</b> </button>
              <button className={state.italic ? 'active' : ''} onClick={() => set('italic', !state.italic)}> <i>I</i> </button>
            </div>
          </div>

          <div className="button-group">
            {(['left','center','right'] as const).map(a => (
              <button key={a} className={state.alignment === a ? 'active' : ''} onClick={() => set('alignment', a)}>
                {a === 'left' ? '⬅' : a === 'center' ? '↔' : '➡'}
              </button>
            ))}
          </div>

          {/* Position sliders */}
          <Slider label="Horizontal Position" min={0} max={1} step={0.01} value={state.anchorX} onChange={v => set('anchorX', v)} />
          <Slider label="Vertical Position"   min={0} max={1} step={0.01} value={state.anchorY} onChange={v => set('anchorY', v)} />

          <CheckRow label="Drop Shadow" value={state.shadowEnabled} onChange={v => set('shadowEnabled', v)} />
          {state.shadowEnabled && (
            <Slider label="Shadow Radius" min={0} max={30} step={1} value={state.shadowRadius} onChange={v => set('shadowRadius', v)} />
          )}

          <CheckRow label="Background Pill" value={state.bgEnabled} onChange={v => set('bgEnabled', v)} />
          {state.bgEnabled && (
            <>
              <ColorPicker label="Background Colour" value={state.bgColor} onChange={v => set('bgColor', v)} />
              <Slider label="Padding"       min={4}  max={40} step={2} value={state.bgPadding} onChange={v => set('bgPadding', v)} />
              <Slider label="Corner Radius" min={0}  max={40} step={2} value={state.bgRadius}  onChange={v => set('bgRadius',  v)} />
            </>
          )}
        </div>
      )}
    </div>
  );
};

// ─────────────────────────────────────────────────────────────────────────────
//  Main Screen
// ─────────────────────────────────────────────────────────────────────────────

const WallpaperScreen: React.FC = () => {
  const [state, setState]         = useState<UIWallpaperState>(DEFAULT_WALLPAPER_STATE);
  const [applying, setApplying]   = useState(false);
  const [progress, setProgress]   = useState<number | null>(null);
  const [result, setResult]       = useState<string | null>(null);
  const [cacheInfo, setCacheInfo] = useState<{ imageCacheMB: string; videoCacheMB: string } | null>(null);

  const set = (key: keyof UIWallpaperState, val: any) =>
    setState(s => ({ ...s, [key]: val }));

  // ── Permissions on mount ────────────────────────────────────────────────
  useEffect(() => {
    WallpaperActions.requestPermissions();

    // Listen for video download progress
    let handle: { remove: () => void } | null = null;
    WallpaperActions.onVideoProgress(e => setProgress(e.progress))
      .then(h => { handle = h; });

    return () => { handle?.remove(); };
  }, []);

  // ── Apply wallpaper ─────────────────────────────────────────────────────
  const handleApply = useCallback(async () => {
    if (!state.imageUrl) { setResult('Please enter an image or video URL'); return; }
    setApplying(true);
    setResult(null);
    setProgress(null);

    try {
      const res = await applyWallpaper(state);
      setResult(res.message);
    } catch (e: any) {
      setResult(`Error: ${e.message}`);
    } finally {
      setApplying(false);
      setProgress(null);
    }
  }, [state]);

  // ── Refresh cache info ──────────────────────────────────────────────────
  const refreshCache = useCallback(async () => {
    const info = await WallpaperActions.getCacheInfo();
    setCacheInfo({ imageCacheMB: info.imageCacheMB, videoCacheMB: info.videoCacheMB });
  }, []);

  return (
    <div className="wallpaper-screen">
      <h1>Wallpaper Pro</h1>

      {/* ── URL Input ──────────────────────────────────────────────────── */}
      <div className="section">
        <label className="section-title">Image / Video URL</label>
        <input
          type="url"
          placeholder="https://example.com/photo.jpg"
          value={state.imageUrl}
          onChange={e => set('imageUrl', e.target.value)}
        />
        <div className="button-group small" style={{ marginTop: 8 }}>
          <button
            className={!state.isVideo ? 'active' : ''}
            onClick={() => set('isVideo', false)}
          >
            🖼 Image
          </button>
          <button
            className={state.isVideo ? 'active' : ''}
            onClick={() => set('isVideo', true)}
          >
            🎬 Video (Live)
          </button>
        </div>
      </div>

      {/* ── Target: home / lock / both ─────────────────────────────────── */}
      <TargetSelector value={state.target} onChange={v => set('target', v)} />

      {/* ── Parallax (images only) — simple toggle, no force ───────────── */}
      {!state.isVideo && (
        <div className="section">
          <div className="row space-between">
            <span>🔀 Parallax Effect</span>
            <Toggle value={state.parallax} onChange={v => set('parallax', v)} />
          </div>
          {state.parallax && (
            <Slider
              label={`Intensity  ×${state.parallaxIntensity.toFixed(1)}`}
              min={1.1} max={3.0} step={0.1}
              value={state.parallaxIntensity}
              onChange={v => set('parallaxIntensity', v)}
            />
          )}
        </div>
      )}

      {/* ── Live wallpaper options (videos only) ───────────────────────── */}
      {state.isVideo && (
        <div className="section">
          <label className="section-title">Video Options</label>
          <CheckRow label="🔇 Mute Audio"  value={state.videoMute} onChange={v => set('videoMute', v)} />
          <CheckRow label="🔁 Loop Video"  value={state.videoLoop} onChange={v => set('videoLoop', v)} />
          <Slider
            label={`▶ Speed  ×${state.videoSpeed.toFixed(2)}`}
            min={0.25} max={4.0} step={0.25}
            value={state.videoSpeed}
            onChange={v => set('videoSpeed', v)}
            neutral={1.0}
          />
        </div>
      )}

      {/* ── Filter Panel — entirely optional ───────────────────────────── */}
      {!state.isVideo && (
        <FilterPanel
          state={state.filter}
          onChange={f => set('filter', f)}
        />
      )}

      {/* ── Text Overlay Panel — entirely optional ──────────────────────── */}
      {!state.isVideo && (
        <TextPanel
          state={state.text}
          onChange={t => set('text', t)}
        />
      )}

      {/* ── Download progress bar (live wallpaper only) ─────────────────── */}
      {applying && progress !== null && (
        <div className="progress-bar">
          <div className="progress-fill" style={{ width: `${progress}%` }} />
          <span>{progress}%</span>
        </div>
      )}

      {/* ── Apply button ────────────────────────────────────────────────── */}
      <button
        className="apply-btn"
        disabled={applying || !state.imageUrl}
        onClick={handleApply}
      >
        {applying ? (state.isVideo ? `Downloading… ${progress ?? 0}%` : 'Applying…') : '🖼 Set Wallpaper'}
      </button>

      {/* ── Result message ──────────────────────────────────────────────── */}
      {result && <div className="result-msg">{result}</div>}

      {/* ── Utility row ─────────────────────────────────────────────────── */}
      <div className="utility-row">
        <button onClick={() => WallpaperActions.undo().then(r => setResult(r.message))}>
          ↩ Undo
        </button>
        <button onClick={refreshCache}>
          💾 Cache Info
        </button>
        <button onClick={() => WallpaperActions.clearImageCache().then(() => refreshCache())}>
          🗑 Clear Images
        </button>
        <button onClick={() => WallpaperActions.clearVideoCache().then(() => refreshCache())}>
          🗑 Clear Videos
        </button>
      </div>

      {cacheInfo && (
        <div className="cache-info">
          Images: {cacheInfo.imageCacheMB} MB &nbsp;|&nbsp; Videos: {cacheInfo.videoCacheMB} MB
        </div>
      )}
    </div>
  );
};

export default WallpaperScreen;

// ─────────────────────────────────────────────────────────────────────────────
//  Tiny reusable UI primitives (replace with your design system components)
// ─────────────────────────────────────────────────────────────────────────────

const Toggle: React.FC<{ value: boolean; onChange: (v: boolean) => void }> = ({ value, onChange }) => (
  <button className={`toggle ${value ? 'on' : 'off'}`} onClick={() => onChange(!value)}>
    {value ? '●' : '○'}
  </button>
);

const CheckRow: React.FC<{ label: string; value: boolean; onChange: (v: boolean) => void }> = ({ label, value, onChange }) => (
  <div className="row space-between">
    <span>{label}</span>
    <Toggle value={value} onChange={onChange} />
  </div>
);

const Slider: React.FC<{
  label: string; min: number; max: number; step: number;
  value: number; onChange: (v: number) => void; neutral?: number;
}> = ({ label, min, max, step, value, onChange, neutral }) => (
  <div className="slider-row">
    <div className="row space-between">
      <span className="slider-label">{label}</span>
      <span className="slider-value">{value.toFixed(step < 1 ? 2 : 0)}</span>
    </div>
    <input
      type="range" min={min} max={max} step={step} value={value}
      onChange={e => onChange(parseFloat(e.target.value))}
    />
    {neutral !== undefined && value !== neutral && (
      <button className="reset-btn" onClick={() => onChange(neutral)}>reset</button>
    )}
  </div>
);

const ColorPicker: React.FC<{ label: string; value: string; onChange: (v: string) => void }> = ({ label, value, onChange }) => (
  <div className="row">
    <span>{label}</span>
    <input type="color" value={value.startsWith('#') ? value.slice(0, 7) : '#000000'} onChange={e => onChange(e.target.value)} />
  </div>
);
