// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  example/DepthWallpaperScreen.tsx
//
//  Complete depth wallpaper UI.
//  User picks background + foreground URLs, configures clock style,
//  positions it, picks animation and depth settings — all optional.
//  Only changed values are sent to the plugin.
// ─────────────────────────────────────────────────────────────────────────────

import React, { useState, useEffect, useCallback } from 'react';
import { WallpaperPro, ClockStyle, ClockAnimation, WallpaperTarget } from '../src/index';

// ── Defaults ─────────────────────────────────────────────────────────────────

const DEFAULT_CLOCK = {
  style:          'digital'  as ClockStyle,
  animation:      'pulse'    as ClockAnimation,
  format:         '24h'      as '12h' | '24h',
  positionX:      0.5,
  positionY:      0.35,
  color:          '#FFFFFF',
  secondaryColor: '#CCCCCC',
  accentColor:    '#FF6B6B',
  fontSize:       80,
  showSeconds:    true,
  showDate:       true,
  showAmPm:       false,
  shadow:         true,
  shadowRadius:   24,
  opacity:        1.0,
};

const DEFAULT_DEPTH = {
  bgParallaxFactor:    0.25,
  clockParallaxFactor: 0.05,
  fgParallaxFactor:    1.0,
  maxOffset:           48,
  sensitivity:         1.0,
  smoothing:           0.08,
};

// ── Preset recipes ────────────────────────────────────────────────────────────

const CLOCK_PRESETS: Record<string, Partial<typeof DEFAULT_CLOCK>> = {
  'Clean White':    { color: '#FFFFFF', secondaryColor: '#CCCCCC', accentColor: '#FF6B6B', shadow: true },
  'Gold Luxury':    { color: '#FFD700', secondaryColor: '#C8A800', accentColor: '#FF8C00', shadow: true, shadowRadius: 30 },
  'Neon Blue':      { color: '#00FFFF', secondaryColor: '#0088FF', accentColor: '#FF00FF', shadow: true, shadowRadius: 40 },
  'Minimal Black':  { color: '#FFFFFF', secondaryColor: '#888888', accentColor: '#FFFFFF', shadow: false },
  'Warm Sunset':    { color: '#FFE4B5', secondaryColor: '#DEB887', accentColor: '#FF4500', shadow: true },
};

const DEPTH_PRESETS: Record<string, Partial<typeof DEFAULT_DEPTH>> = {
  'iOS-like':       { bgParallaxFactor: 0.2, fgParallaxFactor: 1.0, maxOffset: 55, smoothing: 0.07 },
  'Subtle':         { bgParallaxFactor: 0.1, fgParallaxFactor: 0.5, maxOffset: 25, smoothing: 0.04 },
  'Dramatic':       { bgParallaxFactor: 0.3, fgParallaxFactor: 1.5, maxOffset: 70, smoothing: 0.1  },
  'Silky Smooth':   { smoothing: 0.04, sensitivity: 0.8 },
  'Hair Trigger':   { smoothing: 0.2,  sensitivity: 1.8 },
};

// ── Component ─────────────────────────────────────────────────────────────────

const DepthWallpaperScreen: React.FC = () => {
  const [bgUrl,    setBgUrl]    = useState('');
  const [fgUrl,    setFgUrl]    = useState('');
  const [target,   setTarget]   = useState<WallpaperTarget>('both');
  const [clock,    setClock]    = useState(DEFAULT_CLOCK);
  const [depth,    setDepth]    = useState(DEFAULT_DEPTH);
  const [applying, setApplying] = useState(false);
  const [stage,    setStage]    = useState<string | null>(null);
  const [result,   setResult]   = useState<string | null>(null);

  const setC = (k: keyof typeof DEFAULT_CLOCK, v: any) =>
    setClock(c => ({ ...c, [k]: v }));
  const setD = (k: keyof typeof DEFAULT_DEPTH, v: any) =>
    setDepth(d => ({ ...d, [k]: v }));

  useEffect(() => {
    let handle: { remove: () => void } | null = null;
    WallpaperPro.addListener('depthWallpaperProgress', e => {
      setStage(e.stage === 'ready' ? 'Opening preview…' : 'Downloading images…');
    }).then(h => { handle = h; });
    return () => { handle?.remove(); };
  }, []);

  const handleApply = useCallback(async () => {
    if (!bgUrl.trim()) { setResult('Background URL is required'); return; }
    if (!fgUrl.trim()) { setResult('Foreground URL is required'); return; }
    setApplying(true); setResult(null); setStage('Downloading images…');

    try {
      const res = await WallpaperPro.setDepthWallpaper({
        backgroundUrl: bgUrl.trim(),
        foregroundUrl: fgUrl.trim(),
        target,
        clock: {
          style:          clock.style,
          animation:      clock.animation,
          format:         clock.format,
          position:       { x: clock.positionX, y: clock.positionY },
          color:          clock.color,
          secondaryColor: clock.secondaryColor,
          accentColor:    clock.accentColor,
          fontSize:       clock.fontSize,
          showSeconds:    clock.showSeconds,
          showDate:       clock.showDate,
          showAmPm:       clock.showAmPm,
          shadow:         clock.shadow,
          shadowRadius:   clock.shadowRadius,
          opacity:        clock.opacity,
        },
        depth: {
          bgParallaxFactor:    depth.bgParallaxFactor,
          clockParallaxFactor: depth.clockParallaxFactor,
          fgParallaxFactor:    depth.fgParallaxFactor,
          maxOffset:           depth.maxOffset,
          sensitivity:         depth.sensitivity,
          smoothing:           depth.smoothing,
        },
      });
      setResult(res.message);
    } catch (e: any) {
      setResult(`Error: ${e.message}`);
    } finally {
      setApplying(false); setStage(null);
    }
  }, [bgUrl, fgUrl, target, clock, depth]);

  return (
    <div className="depth-screen">
      <h2>🌊 Depth Wallpaper</h2>
      <p className="subtitle">Background + Foreground PNG + Real-time Clock</p>

      {/* ── Image URLs ─────────────────────────────────────────────────────── */}
      <div className="section">
        <label className="section-title">Images</label>
        <div className="url-input">
          <span className="url-label">🖼 Background (JPG)</span>
          <input type="url" placeholder="https://…/mountain.jpg"
            value={bgUrl} onChange={e => setBgUrl(e.target.value)} />
        </div>
        <div className="url-input">
          <span className="url-label">✂️ Foreground (PNG)</span>
          <input type="url" placeholder="https://…/person_cutout.png"
            value={fgUrl} onChange={e => setFgUrl(e.target.value)} />
          <span className="hint">Must be PNG with transparent background</span>
        </div>
      </div>

      {/* ── Target ─────────────────────────────────────────────────────────── */}
      <div className="section">
        <label className="section-title">Apply To</label>
        <div className="button-group">
          {(['home', 'lock', 'both'] as WallpaperTarget[]).map(t => (
            <button key={t} className={target === t ? 'active' : ''} onClick={() => setTarget(t)}>
              {t === 'home' ? '🏠 Home' : t === 'lock' ? '🔒 Lock' : '📱 Both'}
            </button>
          ))}
        </div>
      </div>

      {/* ── Clock Style ────────────────────────────────────────────────────── */}
      <div className="section">
        <label className="section-title">⏱ Clock Style</label>
        <div className="button-group wrap">
          {(['digital','analog','minimal','neon','retro','word'] as ClockStyle[]).map(s => (
            <button key={s} className={clock.style === s ? 'active' : ''} onClick={() => setC('style', s)}>
              {s}
            </button>
          ))}
        </div>

        <label className="section-title" style={{marginTop:12}}>Animation</label>
        <div className="button-group wrap">
          {(['none','pulse','glow','fade','slide','flip'] as ClockAnimation[]).map(a => (
            <button key={a} className={clock.animation === a ? 'active' : ''} onClick={() => setC('animation', a)}>
              {a}
            </button>
          ))}
        </div>

        <div className="button-group" style={{marginTop:8}}>
          <button className={clock.format === '24h' ? 'active' : ''} onClick={() => setC('format', '24h')}>24h</button>
          <button className={clock.format === '12h' ? 'active' : ''} onClick={() => setC('format', '12h')}>12h</button>
        </div>
      </div>

      {/* ── Clock Presets ──────────────────────────────────────────────────── */}
      <div className="section">
        <label className="section-title">Clock Colour Presets</label>
        <div className="button-group wrap">
          {Object.keys(CLOCK_PRESETS).map(name => (
            <button key={name} onClick={() => setClock(c => ({ ...c, ...CLOCK_PRESETS[name] }))}>
              {name}
            </button>
          ))}
        </div>
      </div>

      {/* ── Clock colours ──────────────────────────────────────────────────── */}
      <div className="section">
        <label className="section-title">Clock Colours</label>
        <ColorRow label="Primary"    value={clock.color}          onChange={v => setC('color', v)} />
        <ColorRow label="Secondary"  value={clock.secondaryColor} onChange={v => setC('secondaryColor', v)} />
        <ColorRow label="Accent"     value={clock.accentColor}    onChange={v => setC('accentColor', v)} />
      </div>

      {/* ── Clock positioning ──────────────────────────────────────────────── */}
      <div className="section">
        <label className="section-title">Clock Position</label>
        <Slider label={`Horizontal  ${(clock.positionX * 100).toFixed(0)}%`}
          min={0} max={1} step={0.01} value={clock.positionX} onChange={v => setC('positionX', v)} />
        <Slider label={`Vertical    ${(clock.positionY * 100).toFixed(0)}%`}
          min={0} max={1} step={0.01} value={clock.positionY} onChange={v => setC('positionY', v)} />
        <Slider label={`Font size  ${clock.fontSize}px`}
          min={40} max={160} step={4} value={clock.fontSize} onChange={v => setC('fontSize', v)} />
        <Slider label={`Opacity    ${(clock.opacity * 100).toFixed(0)}%`}
          min={0} max={1} step={0.05} value={clock.opacity} onChange={v => setC('opacity', v)} />
      </div>

      {/* ── Clock options ──────────────────────────────────────────────────── */}
      <div className="section">
        <label className="section-title">Clock Options</label>
        <ToggleRow label="Show seconds"      value={clock.showSeconds} onChange={v => setC('showSeconds', v)} />
        <ToggleRow label="Show date"         value={clock.showDate}    onChange={v => setC('showDate', v)} />
        <ToggleRow label="Show AM/PM (12h)"  value={clock.showAmPm}    onChange={v => setC('showAmPm', v)} />
        <ToggleRow label="Drop shadow/glow"  value={clock.shadow}      onChange={v => setC('shadow', v)} />
        {clock.shadow && (
          <Slider label={`Shadow radius  ${clock.shadowRadius}`}
            min={0} max={80} step={4} value={clock.shadowRadius} onChange={v => setC('shadowRadius', v)} />
        )}
      </div>

      {/* ── Depth presets ──────────────────────────────────────────────────── */}
      <div className="section">
        <label className="section-title">Depth Presets</label>
        <div className="button-group wrap">
          {Object.keys(DEPTH_PRESETS).map(name => (
            <button key={name} onClick={() => setDepth(d => ({ ...d, ...DEPTH_PRESETS[name] }))}>
              {name}
            </button>
          ))}
        </div>
      </div>

      {/* ── Depth config ───────────────────────────────────────────────────── */}
      <div className="section">
        <label className="section-title">Depth / Parallax</label>
        <Slider label={`Background movement  ${(depth.bgParallaxFactor*100).toFixed(0)}%`}
          min={0} max={1} step={0.05} value={depth.bgParallaxFactor} onChange={v => setD('bgParallaxFactor', v)} />
        <Slider label={`Clock movement       ${(depth.clockParallaxFactor*100).toFixed(0)}%`}
          min={0} max={0.5} step={0.01} value={depth.clockParallaxFactor} onChange={v => setD('clockParallaxFactor', v)} />
        <Slider label={`Foreground movement  ${(depth.fgParallaxFactor*100).toFixed(0)}%`}
          min={0.5} max={2} step={0.05} value={depth.fgParallaxFactor} onChange={v => setD('fgParallaxFactor', v)} />
        <Slider label={`Max offset           ${depth.maxOffset}px`}
          min={10} max={100} step={2} value={depth.maxOffset} onChange={v => setD('maxOffset', v)} />
        <Slider label={`Sensitivity          ×${depth.sensitivity.toFixed(2)}`}
          min={0.2} max={3} step={0.1} value={depth.sensitivity} onChange={v => setD('sensitivity', v)} />
        <Slider label={`Smoothing            ${depth.smoothing.toFixed(3)}`}
          min={0.01} max={0.3} step={0.01} value={depth.smoothing} onChange={v => setD('smoothing', v)} />
      </div>

      {/* ── Apply ──────────────────────────────────────────────────────────── */}
      {applying && stage && <div className="stage-msg">{stage}</div>}

      <button className="apply-btn" disabled={applying || !bgUrl || !fgUrl} onClick={handleApply}>
        {applying ? 'Processing…' : '🌊 Set Depth Wallpaper'}
      </button>

      {result && <div className="result-msg">{result}</div>}

      <div className="info-box">
        <strong>How it works:</strong> The background sits behind the clock.
        The foreground PNG floats in front. Tilting your phone shifts each layer
        at a different speed creating a 3D depth illusion — like iOS depth wallpapers.
      </div>
    </div>
  );
};

export default DepthWallpaperScreen;

// ── Tiny UI primitives ────────────────────────────────────────────────────────
const ToggleRow: React.FC<{ label: string; value: boolean; onChange: (v: boolean) => void }> = ({ label, value, onChange }) => (
  <div className="row space-between">
    <span>{label}</span>
    <button className={`toggle ${value ? 'on' : 'off'}`} onClick={() => onChange(!value)}>
      {value ? '●' : '○'}
    </button>
  </div>
);

const Slider: React.FC<{ label: string; min: number; max: number; step: number; value: number; onChange: (v: number) => void }> = ({ label, min, max, step, value, onChange }) => (
  <div className="slider-row">
    <span className="slider-label">{label}</span>
    <input type="range" min={min} max={max} step={step} value={value}
      onChange={e => onChange(parseFloat(e.target.value))} />
  </div>
);

const ColorRow: React.FC<{ label: string; value: string; onChange: (v: string) => void }> = ({ label, value, onChange }) => (
  <div className="row space-between">
    <span>{label}</span>
    <input type="color" value={value.startsWith('#') ? value.slice(0, 7) : '#ffffff'}
      onChange={e => onChange(e.target.value)} />
  </div>
);
