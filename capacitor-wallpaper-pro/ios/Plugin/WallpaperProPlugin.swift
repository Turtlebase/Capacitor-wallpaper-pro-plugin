// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  WallpaperProPlugin.swift  (v2)
//
//  iOS additions:
//    ✅  Schedule persisted to UserDefaults — restored on applicationDidBecomeActive
//    ✅  setGradientWallpaper — CoreGraphics gradient → Photos
//    ✅  setRandomWallpaper
//    ✅  getHistory / undoWallpaper / clearHistory
//    ✅  getCacheInfo
//    ✅  Text overlay support
// ─────────────────────────────────────────────────────────────────────────────
import Foundation
import Capacitor
import UIKit
import Photos

@objc(WallpaperProPlugin)
public class WallpaperProPlugin: CAPPlugin {

    private let processor  = ImageProcessor()
    private var schedule:  [[String: Any]] = []
    private var scheduleTimers: [Timer]    = []
    private var historyList: [[String: Any]] = []
    private let maxHistory = 50

    // ── lifecycle ─────────────────────────────────────────────────────────

    public override func load() {
        super.load()
        loadHistory()
        restoreScheduleIfNeeded()
        NotificationCenter.default.addObserver(
            self, selector: #selector(appDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification, object: nil)
    }

    @objc private func appDidBecomeActive() { restoreScheduleIfNeeded() }

    // ── setWallpaper ──────────────────────────────────────────────────────

    @objc func setWallpaper(_ call: CAPPluginCall) {
        guard let url = call.getString("url") else { return call.reject("url is required") }
        let target      = call.getString("target", "both")
        let parallax    = call.getBool("parallax") ?? false
        let parallaxMul = call.getFloat("parallaxIntensity") ?? 1.5
        let quality     = call.getInt("quality") ?? 95
        let cropMode    = call.getString("cropMode", "fill")
        let cropX       = Float(call.getFloat("cropX") ?? 0.5)
        let cropY       = Float(call.getFloat("cropY") ?? 0.5)
        let useCache    = call.getBool("cache") ?? true
        let label       = call.getString("label") ?? ""
        let filterDict  = call.getObject("filter") ?? [:]
        let textDict    = call.getObject("textOverlay")
        let filter      = FilterOptions(from: filterDict)

        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let image    = try self.processor.loadImage(from: url, useCache: useCache)
                let screen   = UIScreen.main.nativeBounds.size
                let tw       = parallax ? screen.width * CGFloat(parallaxMul) : screen.width
                let cropped  = self.processor.crop(image, to: CGSize(width: tw, height: screen.height), mode: cropMode, anchorX: CGFloat(cropX), anchorY: CGFloat(cropY))
                var filtered = self.processor.applyFilters(cropped, options: filter)
                if let td = textDict { filtered = self.processor.drawText(over: filtered, options: TextOverlayOptions(from: td)) }

                self.saveToPhotos(image: filtered) { saved, path in
                    if saved {
                        self.pushHistory(url: url, target: target, label: label, isLive: false)
                        self.presentShareSheet(image: filtered)
                        call.resolve(["success": true, "message": "Image saved to Photos. Tap 'Set as Wallpaper'."])
                    } else {
                        call.resolve(["success": false, "message": "Could not save to Photos."])
                    }
                }
            } catch {
                call.reject("Processing failed: \(error.localizedDescription)")
            }
        }
    }

    // ── setGradientWallpaper ──────────────────────────────────────────────

    @objc func setGradientWallpaper(_ call: CAPPluginCall) {
        guard let gradDict = call.getObject("gradient") else { return call.reject("gradient is required") }
        let quality  = call.getInt("quality") ?? 95
        let textDict = call.getObject("textOverlay")

        DispatchQueue.global(qos: .userInitiated).async {
            let screen = UIScreen.main.nativeBounds.size
            var img    = self.processor.buildGradient(dict: gradDict, size: screen)
            if let td = textDict { img = self.processor.drawText(over: img, options: TextOverlayOptions(from: td)) }

            self.saveToPhotos(image: img) { saved, _ in
                if saved {
                    self.presentShareSheet(image: img)
                    call.resolve(["success": true, "message": "Gradient wallpaper saved to Photos."])
                } else {
                    call.resolve(["success": false, "message": "Could not save gradient."])
                }
            }
        }
    }

    // ── setRandomWallpaper ────────────────────────────────────────────────

    @objc func setRandomWallpaper(_ call: CAPPluginCall) {
        guard let urlsRaw = call.getArray("urls") as? [String], !urlsRaw.isEmpty else {
            return call.reject("urls array is required")
        }
        let selected = urlsRaw.randomElement()!
        let newCall  = call  // capture
        call.keepAlive = true
        // Reuse setWallpaper logic
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let image    = try self.processor.loadImage(from: selected, useCache: true)
                let screen   = UIScreen.main.nativeBounds.size
                let cropped  = self.processor.crop(image, to: screen, mode: "fill", anchorX: 0.5, anchorY: 0.5)
                self.saveToPhotos(image: cropped) { saved, _ in
                    if saved { self.presentShareSheet(image: cropped) }
                    call.resolve(["success": saved, "message": saved ? "Random wallpaper saved to Photos." : "Save failed.", "selectedUrl": selected])
                }
            } catch {
                call.reject("Failed: \(error.localizedDescription)")
            }
        }
    }

    // ── schedule ──────────────────────────────────────────────────────────

    @objc func schedule24HourWallpapers(_ call: CAPPluginCall) {
        guard let rawSchedule = call.getArray("schedule") as? [[String: Any]], !rawSchedule.isEmpty else {
            return call.reject("schedule must have at least 1 entry")
        }
        let target   = call.getString("target", "both")
        let parallax = call.getBool("parallax") ?? false

        cancelAllTimers()
        schedule = rawSchedule

        // Persist to UserDefaults for restoration after app kill/relaunch
        if let data = try? JSONSerialization.data(withJSONObject: rawSchedule) {
            UserDefaults.standard.set(data,     forKey: "wp_pro_schedule")
            UserDefaults.standard.set(target,   forKey: "wp_pro_target")
            UserDefaults.standard.set(parallax, forKey: "wp_pro_parallax")
        }

        for entry in rawSchedule {
            guard let timeStr = entry["time"] as? String, let url = entry["url"] as? String else { continue }
            if let fireDate = nextFireDate(for: timeStr) {
                let capturedEntry = entry
                let timer = Timer(fire: fireDate, interval: 86400, repeats: true) { [weak self] _ in
                    self?.applyEntry(capturedEntry, target: target, globalParallax: parallax)
                }
                RunLoop.main.add(timer, forMode: .common)
                scheduleTimers.append(timer)
            }
        }

        if let current = currentEntry(from: rawSchedule) {
            DispatchQueue.global(qos: .background).async {
                self.applyEntry(current, target: target, globalParallax: parallax)
            }
        }

        call.resolve(["success": true, "message": "Schedule registered with \(rawSchedule.count) entries"])
    }

    @objc func clearSchedule(_ call: CAPPluginCall) {
        cancelAllTimers()
        schedule = []
        UserDefaults.standard.removeObject(forKey: "wp_pro_schedule")
        UserDefaults.standard.removeObject(forKey: "wp_pro_target")
        UserDefaults.standard.removeObject(forKey: "wp_pro_parallax")
        call.resolve(["success": true])
    }

    // ── history ───────────────────────────────────────────────────────────

    @objc func getHistory(_ call: CAPPluginCall) {
        let limit = call.getInt("limit") ?? maxHistory
        let slice = Array(historyList.prefix(limit))
        call.resolve(["history": slice, "count": slice.count])
    }

    @objc func undoWallpaper(_ call: CAPPluginCall) {
        guard historyList.count > 1,
              let prev = historyList[safe: 1],
              let url  = prev["url"] as? String else {
            return call.resolve(["success": false, "message": "No previous wallpaper."])
        }
        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let image = try self.processor.loadImage(from: url, useCache: true)
                self.saveToPhotos(image: image) { saved, _ in
                    if saved {
                        self.historyList.removeFirst()
                        self.saveHistory()
                        self.presentShareSheet(image: image)
                    }
                    call.resolve(["success": saved, "message": saved ? "Previous wallpaper restored." : "Restore failed.", "restoredUrl": url])
                }
            } catch {
                call.reject("Undo failed: \(error.localizedDescription)")
            }
        }
    }

    @objc func clearHistory(_ call: CAPPluginCall) {
        historyList.removeAll()
        saveHistory()
        call.resolve(["success": true])
    }

    // ── getWallpaperInfo ──────────────────────────────────────────────────

    @objc func getWallpaperInfo(_ call: CAPPluginCall) {
        let active = !schedule.isEmpty
        let caps: [String: Any] = [
            "canSetHomeScreen": false, "canSetLockScreen": false, "canSetDual": false,
            "supportsParallax": false, "supportsFilters": true, "supportsScheduling": true,
            "supportsLiveWallpaper": false, "supportsGradient": true, "supportsTextOverlay": true,
        ]
        var result: [String: Any] = ["scheduleActive": active, "scheduleCount": schedule.count, "capabilities": caps]
        if let next = nextEntry(from: schedule) { result["nextChangeTime"] = next["time"]; result["nextChangeLabel"] = next["label"] }
        if let cur  = currentEntry(from: schedule) { result["currentLabel"] = cur["label"] }
        call.resolve(result)
    }

    // ── permissions ───────────────────────────────────────────────────────

    @objc override func checkPermissions(_ call: CAPPluginCall) {
        let s = PHPhotoLibrary.authorizationStatus(for: .addOnly)
        let str: String
        switch s { case .authorized, .limited: str = "granted"; case .denied, .restricted: str = "denied"; default: str = "prompt" }
        call.resolve(["wallpaper": "denied", "storage": str, "notifications": "granted"])
    }

    @objc override func requestPermissions(_ call: CAPPluginCall) {
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { _ in self.checkPermissions(call) }
    }

    // ── preload / cache ───────────────────────────────────────────────────

    @objc func preloadWallpaper(_ call: CAPPluginCall) {
        guard let url = call.getString("url") else { return call.reject("url is required") }
        DispatchQueue.global(qos: .background).async {
            do {
                _ = try self.processor.loadImage(from: url, useCache: true)
                call.resolve(["success": true, "cachedPath": self.processor.cacheFilePath(for: url)])
            } catch {
                call.reject("Preload failed: \(error.localizedDescription)")
            }
        }
    }

    @objc func clearCache(_ call: CAPPluginCall) {
        let freed = processor.clearCache()
        call.resolve(["success": true, "bytesFreed": freed])
    }

    @objc func clearVideoCache(_ call: CAPPluginCall) {
        call.resolve(["success": true, "bytesFreed": 0])  // no video cache on iOS
    }

    @objc func getCacheInfo(_ call: CAPPluginCall) {
        let imgBytes = processor.cacheSize()
        call.resolve([
            "imageCacheBytes": imgBytes, "videoCacheBytes": 0,
            "totalBytes": imgBytes,
            "imageCacheMB": String(format: "%.2f", Double(imgBytes) / 1_048_576),
            "videoCacheMB": "0.00",
        ])
    }

    @objc func setDepthWallpaper(_ call: CAPPluginCall) {
        call.resolve([
            "success": false,
            "message": "iOS: depth effect wallpaper is not supported by Apple APIs. Use setWallpaper() with filters instead.",
            "bgPath": call.getString("backgroundUrl") ?? "",
            "fgPath": call.getString("foregroundUrl") ?? "",
        ])
    }

    @objc func setLiveWallpaper(_ call: CAPPluginCall) {
        call.resolve(["success": false, "message": "iOS: live wallpaper not supported by Apple APIs.", "videoPath": ""])
    }

    // ── internal helpers ──────────────────────────────────────────────────

    private func applyEntry(_ entry: [String: Any], target: String, globalParallax: Bool) {
        guard let url = entry["url"] as? String else { return }
        let filterDict  = entry["filter"] as? [String: Any] ?? [:]
        let filter      = FilterOptions(from: filterDict)
        let parallax    = (entry["parallax"] as? Bool) ?? globalParallax

        do {
            let image    = try processor.loadImage(from: url, useCache: true)
            let filtered = processor.applyFilters(image, options: filter)
            saveToPhotos(image: filtered) { [weak self] _, _ in
                self?.pushHistory(url: url, target: target, label: entry["label"] as? String, isLive: false)
                let evt: [String: Any] = ["time": self?.currentTimeString() ?? "", "label": entry["label"] as? String ?? "", "url": url, "success": true]
                self?.notifyListeners("wallpaperChanged", data: evt)
            }
        } catch {
            print("[WallpaperPro] applyEntry error: \(error)")
        }
    }

    private func saveToPhotos(image: UIImage, completion: @escaping (Bool, String?) -> Void) {
        PHPhotoLibrary.shared().performChanges({
            PHAssetChangeRequest.creationRequestForAsset(from: image)
        }) { saved, _ in DispatchQueue.main.async { completion(saved, nil) } }
    }

    private func presentShareSheet(image: UIImage) {
        DispatchQueue.main.async {
            guard let vc = self.bridge?.viewController else { return }
            let ac = UIActivityViewController(activityItems: [image], applicationActivities: nil)
            vc.present(ac, animated: true)
        }
    }

    private func restoreScheduleIfNeeded() {
        guard schedule.isEmpty,
              let data = UserDefaults.standard.data(forKey: "wp_pro_schedule"),
              let raw  = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return }
        let target   = UserDefaults.standard.string(forKey: "wp_pro_target") ?? "both"
        let parallax = UserDefaults.standard.bool(forKey: "wp_pro_parallax")
        schedule = raw
        for entry in raw {
            guard let timeStr = entry["time"] as? String, let _ = entry["url"] as? String else { continue }
            if let fireDate = nextFireDate(for: timeStr) {
                let capturedEntry = entry
                let timer = Timer(fire: fireDate, interval: 86400, repeats: true) { [weak self] _ in
                    self?.applyEntry(capturedEntry, target: target, globalParallax: parallax)
                }
                RunLoop.main.add(timer, forMode: .common)
                scheduleTimers.append(timer)
            }
        }
        print("[WallpaperPro] Schedule restored: \(raw.count) entries")
    }

    private func cancelAllTimers() {
        scheduleTimers.forEach { $0.invalidate() }
        scheduleTimers.removeAll()
    }

    private func pushHistory(url: String, target: String, label: String?, isLive: Bool) {
        let entry: [String: Any] = [
            "url": url, "target": target, "label": label ?? "",
            "timestamp": Int(Date().timeIntervalSince1970 * 1000),
            "date": currentDateString(), "isLive": isLive,
        ]
        historyList.insert(entry, at: 0)
        if historyList.count > maxHistory { historyList = Array(historyList.prefix(maxHistory)) }
        saveHistory()
    }

    private func loadHistory() {
        if let data = UserDefaults.standard.data(forKey: "wp_pro_history"),
           let list = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
            historyList = list
        }
    }

    private func saveHistory() {
        if let data = try? JSONSerialization.data(withJSONObject: historyList) {
            UserDefaults.standard.set(data, forKey: "wp_pro_history")
        }
    }

    private func nextFireDate(for timeStr: String) -> Date? {
        let parts = timeStr.split(separator: ":").compactMap { Int($0) }
        guard parts.count >= 2 else { return nil }
        var c = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        c.hour = parts[0]; c.minute = parts[1]; c.second = 0
        guard let d = Calendar.current.date(from: c) else { return nil }
        return d < Date() ? Calendar.current.date(byAdding: .day, value: 1, to: d) : d
    }

    private func currentEntry(from entries: [[String: Any]]) -> [String: Any]? {
        let sorted = entries.sorted { minuteVal($0["time"] as? String ?? "00:00") < minuteVal($1["time"] as? String ?? "00:00") }
        let now    = nowMinutes()
        return sorted.last { minuteVal($0["time"] as? String ?? "00:00") <= now } ?? sorted.last
    }

    private func nextEntry(from entries: [[String: Any]]) -> [String: Any]? {
        let sorted = entries.sorted { minuteVal($0["time"] as? String ?? "00:00") < minuteVal($1["time"] as? String ?? "00:00") }
        return sorted.first { minuteVal($0["time"] as? String ?? "00:00") > nowMinutes() } ?? sorted.first
    }

    private func minuteVal(_ t: String) -> Int {
        let p = t.split(separator: ":").compactMap { Int($0) }
        return (p.first ?? 0) * 60 + (p.dropFirst().first ?? 0)
    }

    private func nowMinutes() -> Int {
        let c = Calendar.current.dateComponents([.hour, .minute], from: Date())
        return (c.hour ?? 0) * 60 + (c.minute ?? 0)
    }

    private func currentTimeString() -> String { let f = DateFormatter(); f.dateFormat = "HH:mm"; return f.string(from: Date()) }
    private func currentDateString() -> String  { let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd HH:mm:ss"; return f.string(from: Date()) }
}

extension Array { subscript(safe index: Index) -> Element? { indices.contains(index) ? self[index] : nil } }
