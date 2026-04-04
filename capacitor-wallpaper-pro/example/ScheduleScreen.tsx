// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  example/ScheduleScreen.tsx
//
//  Example schedule management screen.
//  Shows how to build daily / weekly / interval schedule UI
//  that calls the plugin with only what the user sets.
// ─────────────────────────────────────────────────────────────────────────────

import React, { useState, useEffect } from 'react';
import {
  applySchedule,
  WallpaperActions,
  DEFAULT_FILTER_STATE,
  UIScheduleEntry,
} from './WallpaperManager';
import { WallpaperTarget, ScheduleType } from '../src/index';

const DAY_NAMES = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

const ScheduleScreen: React.FC = () => {
  const [scheduleType,    setScheduleType]    = useState<ScheduleType>('daily');
  const [target,          setTarget]          = useState<WallpaperTarget>('both');
  const [parallax,        setParallax]        = useState(false);
  const [preloadAll,      setPreloadAll]      = useState(true);
  const [showNotifs,      setShowNotifs]      = useState(false);
  const [intervalMinutes, setIntervalMinutes] = useState(60);
  const [entries,         setEntries]         = useState<UIScheduleEntry[]>([
    { time: '07:00', url: '', label: '' },
  ]);
  const [scheduleInfo,    setScheduleInfo]    = useState<any>(null);
  const [saving,          setSaving]          = useState(false);
  const [message,         setMessage]         = useState<string | null>(null);

  // Load current schedule info on mount
  useEffect(() => {
    WallpaperActions.getInfo().then(setScheduleInfo);
  }, []);

  const addEntry = () =>
    setEntries((e: UIScheduleEntry[]) => [...e, { time: '12:00', url: '', label: '' }]);

  const removeEntry = (i: number) =>
    setEntries((e: UIScheduleEntry[]) => e.filter((_, idx) => idx !== i));

  const updateEntry = (i: number, key: keyof UIScheduleEntry, val: any) =>
    setEntries((e: UIScheduleEntry[]) => e.map((entry, idx) => idx === i ? { ...entry, [key]: val } : entry));

  const handleSave = async () => {
    const validEntries = entries.filter((e: UIScheduleEntry) => e.url.trim());
    if (!validEntries.length) { setMessage('Add at least one entry with a URL'); return; }

    setSaving(true);
    setMessage(null);
    try {
      const res = await applySchedule({
        entries: validEntries,
        target,
        scheduleType,
        intervalMinutes,
        parallax,
        preloadAll,
        showNotifications: showNotifs,
      });
      setMessage(res.message);
      const info = await WallpaperActions.getInfo();
      setScheduleInfo(info);
    } catch (e: any) {
      setMessage(`Error: ${e.message}`);
    } finally {
      setSaving(false);
    }
  };

  const handleClear = async () => {
    await WallpaperActions.clearSchedule();
    setScheduleInfo(await WallpaperActions.getInfo());
    setMessage('Schedule cleared');
  };

  return (
    <div className="schedule-screen">
      <h2>⏰ Wallpaper Schedule</h2>

      {/* Current status */}
      {scheduleInfo && (
        <div className="status-card">
          {scheduleInfo.scheduleActive ? (
            <>
              <span className="badge active">Active</span>
              <span>{scheduleInfo.scheduleCount} entries</span>
              {scheduleInfo.nextChangeTime && (
                <span>Next: {scheduleInfo.nextChangeTime} — {scheduleInfo.nextChangeLabel}</span>
              )}
            </>
          ) : (
            <span className="badge inactive">No schedule</span>
          )}
        </div>
      )}

      {/* Schedule type */}
      <div className="section">
        <label className="section-title">Schedule Type</label>
        <div className="button-group">
          {(['daily', 'weekly', 'interval'] as ScheduleType[]).map(t => (
            <button
              key={t}
              className={scheduleType === t ? 'active' : ''}
              onClick={() => setScheduleType(t)}
            >
              {t === 'daily' ? '📅 Daily' : t === 'weekly' ? '📆 Weekly' : '🔄 Every N min'}
            </button>
          ))}
        </div>

        {scheduleType === 'interval' && (
          <div className="slider-row">
            <label>Interval: every {intervalMinutes} minutes</label>
            <input
              type="range" min={5} max={1440} step={5}
              value={intervalMinutes}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) => setIntervalMinutes(+e.target.value)}
            />
            <div className="presets">
              {[15, 30, 60, 120, 240].map(m => (
                <button key={m} className={intervalMinutes === m ? 'active' : ''}
                  onClick={() => setIntervalMinutes(m)}>
                  {m < 60 ? `${m}m` : `${m/60}h`}
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Target */}
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

      {/* Options row */}
      <div className="section options-row">
        <ToggleRow label="🔀 Parallax"            value={parallax}   onChange={setParallax}   />
        <ToggleRow label="⬇ Pre-download all"     value={preloadAll} onChange={setPreloadAll} />
        <ToggleRow label="🔔 Change notifications" value={showNotifs} onChange={setShowNotifs} />
      </div>

      {/* Entries */}
      <div className="section">
        <div className="row space-between">
          <label className="section-title">
            {scheduleType === 'interval' ? 'Wallpaper Pool' : 'Time Slots'}
          </label>
          <button className="add-btn" onClick={addEntry}>+ Add</button>
        </div>

        {entries.map((entry, i) => (
          <div key={i} className="entry-card">
            <div className="entry-row">
              {/* Time — only relevant for daily/weekly */}
              {scheduleType !== 'interval' && (
                <input
                  type="time"
                  value={entry.time}
                  onChange={e => updateEntry(i, 'time', e.target.value)}
                />
              )}

              {/* Day of week — only for weekly */}
              {scheduleType === 'weekly' && (
                <select
                  value={entry.dayOfWeek ?? 1}
                  onChange={e => updateEntry(i, 'dayOfWeek', +e.target.value)}
                >
                  {DAY_NAMES.slice(1).map((d, idx) => (
                    <option key={idx+1} value={idx+1}>{d}</option>
                  ))}
                </select>
              )}

              <input
                type="url"
                placeholder="Image URL…"
                value={entry.url}
                onChange={e => updateEntry(i, 'url', e.target.value)}
                style={{ flex: 1 }}
              />

              <input
                placeholder="Label (optional)"
                value={entry.label ?? ''}
                onChange={e => updateEntry(i, 'label', e.target.value)}
                style={{ width: 120 }}
              />

              {entries.length > 1 && (
                <button className="remove-btn" onClick={() => removeEntry(i)}>✕</button>
              )}
            </div>

            {/* Per-slot preload button (optional nicety) */}
            {entry.url && (
              <button
                className="preload-btn"
                onClick={() => WallpaperActions.preload(entry.url, 'low')}
              >
                ⬇ Pre-cache this image
              </button>
            )}
          </div>
        ))}
      </div>

      {/* Save / Clear */}
      <div className="action-row">
        <button className="apply-btn" disabled={saving} onClick={handleSave}>
          {saving ? 'Saving…' : '💾 Save Schedule'}
        </button>
        {scheduleInfo?.scheduleActive && (
          <button className="clear-btn" onClick={handleClear}>
            🗑 Clear Schedule
          </button>
        )}
      </div>

      {message && <div className="result-msg">{message}</div>}
    </div>
  );
};

export default ScheduleScreen;

// ── tiny helpers ──────────────────────────────────────────────────────────────
const ToggleRow: React.FC<{ label: string; value: boolean; onChange: (v: boolean) => void }> = ({ label, value, onChange }) => (
  <div className="row space-between">
    <span>{label}</span>
    <button className={`toggle ${value ? 'on' : 'off'}`} onClick={() => onChange(!value)}>
      {value ? '●' : '○'}
    </button>
  </div>
);
