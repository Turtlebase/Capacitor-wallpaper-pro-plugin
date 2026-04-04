// ─────────────────────────────────────────────────────────────────────────────
//  capacitor-wallpaper-pro  •  ImageProcessor.swift  (v2)
//  New: gradient builder, text overlay, crop anchors, cacheSize()
// ─────────────────────────────────────────────────────────────────────────────
import Foundation
import UIKit
import CoreImage
import CoreImage.CIFilterBuiltins

// ─────────────────────────────────────────────────────────────────────────────
//  FilterOptions
// ─────────────────────────────────────────────────────────────────────────────
struct FilterOptions {
    var blur: Float = 0; var brightness: Float = 0; var contrast: Float = 1
    var saturation: Float = 1; var grayscale: Bool = false; var sepia: Float = 0
    var vignette: Float = 0; var hue: Float = 0; var temperature: Float = 0
    var opacity: Float = 1; var tintColor: UIColor? = nil; var tintOpacity: Float = 0.3

    init(from d: [String: Any]) {
        blur        = Float(d["blur"]        as? Double ?? 0)
        brightness  = Float((d["brightness"] as? Double ?? 1) - 1)
        contrast    = Float(d["contrast"]    as? Double ?? 1)
        saturation  = Float(d["saturation"]  as? Double ?? 1)
        grayscale   = d["grayscale"]  as? Bool   ?? false
        sepia       = Float(d["sepia"]        as? Double ?? 0)
        vignette    = Float(d["vignette"]     as? Double ?? 0)
        hue         = Float(d["hue"]          as? Double ?? 0) * (.pi / 180)
        temperature = Float(d["temperature"]  as? Double ?? 0)
        opacity     = Float(d["opacity"]      as? Double ?? 1)
        tintOpacity = Float(d["tintOpacity"]  as? Double ?? 0.3)
        if let hex  = d["tintColor"] as? String { tintColor = UIColor(hex: hex) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  TextOverlayOptions
// ─────────────────────────────────────────────────────────────────────────────
struct TextOverlayOptions {
    var text: String = ""; var fontSize: CGFloat = 48; var color: UIColor = .white
    var bold: Bool = false; var italic: Bool = false; var alignment: NSTextAlignment = .center
    var anchorX: CGFloat = 0.5; var anchorY: CGFloat = 0.85
    var shadowColor: UIColor = UIColor(white: 0, alpha: 0.5); var shadowRadius: CGFloat = 8
    var shadowOffset: CGSize = CGSize(width: 2, height: 2)
    var backgroundColor: UIColor? = nil; var backgroundPadding: CGFloat = 16; var backgroundRadius: CGFloat = 12
    var maxWidthFraction: CGFloat = 0.85; var lineSpacing: CGFloat = 1.2

    init(from d: [String: Any]) {
        text              = d["text"]             as? String ?? ""
        fontSize          = CGFloat(d["fontSize"] as? Double ?? 48)
        if let c = d["color"] as? String { color = UIColor(hex: c) ?? .white }
        bold              = d["bold"]   as? Bool ?? false
        italic            = d["italic"] as? Bool ?? false
        switch d["alignment"] as? String { case "left": alignment = .left; case "right": alignment = .right; default: alignment = .center }
        anchorX           = CGFloat(d["anchorX"]           as? Double ?? 0.5)
        anchorY           = CGFloat(d["anchorY"]           as? Double ?? 0.85)
        shadowRadius      = CGFloat(d["shadowRadius"]      as? Double ?? 8)
        shadowOffset      = CGSize(width: CGFloat(d["shadowDx"] as? Double ?? 2), height: CGFloat(d["shadowDy"] as? Double ?? 2))
        backgroundPadding = CGFloat(d["backgroundPadding"] as? Double ?? 16)
        backgroundRadius  = CGFloat(d["backgroundRadius"]  as? Double ?? 12)
        maxWidthFraction  = CGFloat(d["maxWidthFraction"]  as? Double ?? 0.85)
        lineSpacing       = CGFloat(d["lineSpacing"]       as? Double ?? 1.2)
        if let sc = d["shadowColor"]    as? String { shadowColor    = UIColor(hex: sc) ?? UIColor(white:0,alpha:0.5) }
        if let bc = d["backgroundColor"] as? String { backgroundColor = UIColor(hex: bc) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ImageProcessor
// ─────────────────────────────────────────────────────────────────────────────
class ImageProcessor {

    private let ciContext    = CIContext(options: [.useSoftwareRenderer: false])
    private let cacheDirName = "WallpaperProCache"

    // ── load / cache ──────────────────────────────────────────────────────

    func loadImage(from urlString: String, useCache: Bool) throws -> UIImage {
        if urlString.hasPrefix("/") || urlString.hasPrefix("file://") {
            let path = urlString.replacingOccurrences(of: "file://", with: "")
            guard let img = UIImage(contentsOfFile: path) else { throw WPError.loadFailed(path) }
            return img
        }
        let cu = cacheFileURL(for: urlString)
        if useCache, let cached = UIImage(contentsOfFile: cu.path) { return cached }
        guard let remote = URL(string: urlString) else { throw WPError.invalidURL(urlString) }
        let data = try Data(contentsOf: remote)
        try data.write(to: cu)
        guard let img = UIImage(data: data) else { throw WPError.loadFailed("Unsupported format") }
        return img
    }

    func cacheFilePath(for url: String) -> String { cacheFileURL(for: url).path }
    func cacheSize() -> Int64 {
        var total: Int64 = 0
        if let files = try? FileManager.default.contentsOfDirectory(at: cacheDirectory, includingPropertiesForKeys: [.fileSizeKey]) {
            files.forEach { total += Int64(((try? $0.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? 0) ?? 0) }
        }
        return total
    }

    func clearCache() -> Int64 {
        var freed: Int64 = 0
        if let files = try? FileManager.default.contentsOfDirectory(at: cacheDirectory, includingPropertiesForKeys: [.fileSizeKey]) {
            for f in files { freed += Int64(((try? f.resourceValues(forKeys:[.fileSizeKey]).fileSize) ?? 0) ?? 0); try? FileManager.default.removeItem(at: f) }
        }
        return freed
    }

    // ── crop / scale ──────────────────────────────────────────────────────

    func crop(_ image: UIImage, to size: CGSize, mode: String, anchorX: CGFloat = 0.5, anchorY: CGFloat = 0.5) -> UIImage {
        UIGraphicsBeginImageContextWithOptions(size, true, 1); defer { UIGraphicsEndImageContext() }
        let sw = image.size.width, sh = image.size.height
        switch mode {
        case "fit":
            let s = min(size.width/sw, size.height/sh)
            let nw=sw*s, nh=sh*s
            UIColor.black.setFill(); UIRectFill(CGRect(origin:.zero,size:size))
            image.draw(in:CGRect(x:(size.width-nw)/2, y:(size.height-nh)/2, width:nw, height:nh))
        case "stretch":
            image.draw(in:CGRect(origin:.zero,size:size))
        case "center":
            UIColor.black.setFill(); UIRectFill(CGRect(origin:.zero,size:size))
            image.draw(in:CGRect(x:(size.width-sw)/2, y:(size.height-sh)/2, width:sw, height:sh))
        default: // fill with anchor
            let s = max(size.width/sw, size.height/sh)
            let nw=sw*s, nh=sh*s
            let x = -(nw-size.width)*anchorX, y = -(nh-size.height)*anchorY
            image.draw(in:CGRect(x:x, y:y, width:nw, height:nh))
        }
        return UIGraphicsGetImageFromCurrentImageContext() ?? image
    }

    // ── gradient builder ──────────────────────────────────────────────────

    func buildGradient(dict: [String: Any], size: CGSize) -> UIImage {
        UIGraphicsBeginImageContextWithOptions(size, true, 1); defer { UIGraphicsEndImageContext() }
        guard let ctx = UIGraphicsGetCurrentContext() else { return UIImage() }
        let colorStrings = dict["colors"] as? [String] ?? ["#000000","#FFFFFF"]
        let cgColors = colorStrings.compactMap { UIColor(hex: $0)?.cgColor } as CFArray
        let stops = (dict["stops"] as? [Double])?.map { CGFloat($0) }
        let space = CGColorSpaceCreateDeviceRGB()
        guard let grad = CGGradient(colorsSpace: space, colors: cgColors, locations: stops.flatMap { $0 }) else { return UIImage() }
        let type = dict["type"] as? String ?? "linear"
        let angle = CGFloat((dict["angle"] as? Double ?? 180) * .pi / 180)
        if type == "radial" {
            ctx.drawRadialGradient(grad, startCenter: CGPoint(x:size.width/2,y:size.height/2), startRadius:0,
                endCenter: CGPoint(x:size.width/2,y:size.height/2), endRadius:max(size.width,size.height)/2, options:[.drawsAfterEndLocation])
        } else {
            let sx = size.width/2 - cos(angle)*size.width/2, sy = size.height/2 - sin(angle)*size.height/2
            let ex = size.width/2 + cos(angle)*size.width/2, ey = size.height/2 + sin(angle)*size.height/2
            ctx.drawLinearGradient(grad, start:CGPoint(x:sx,y:sy), end:CGPoint(x:ex,y:ey), options:[.drawsBeforeStartLocation,.drawsAfterEndLocation])
        }
        return UIGraphicsGetImageFromCurrentImageContext() ?? UIImage()
    }

    // ── text overlay ──────────────────────────────────────────────────────

    func drawText(over base: UIImage, options o: TextOverlayOptions) -> UIImage {
        guard !o.text.isEmpty else { return base }
        UIGraphicsBeginImageContextWithOptions(base.size, false, base.scale); defer { UIGraphicsEndImageContext() }
        base.draw(at: .zero)
        let w = base.size.width, h = base.size.height
        let maxW = w * o.maxWidthFraction
        var traits: UIFontDescriptor.SymbolicTraits = []
        if o.bold   { traits.insert(.traitBold) }
        if o.italic { traits.insert(.traitItalic) }
        let descriptor = UIFont.systemFont(ofSize: o.fontSize).fontDescriptor.withSymbolicTraits(traits) ?? UIFont.systemFont(ofSize: o.fontSize).fontDescriptor
        let font       = UIFont(descriptor: descriptor, size: o.fontSize)
        let shadow     = NSShadow(); shadow.shadowColor = o.shadowColor; shadow.shadowBlurRadius = o.shadowRadius; shadow.shadowOffset = o.shadowOffset
        let paraStyle  = NSMutableParagraphStyle(); paraStyle.alignment = o.alignment; paraStyle.lineSpacing = o.fontSize * (o.lineSpacing - 1)
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: o.color, .shadow: shadow, .paragraphStyle: paraStyle]
        let attrStr    = NSAttributedString(string: o.text, attributes: attrs)
        let boundSize  = CGSize(width: maxW, height: .greatestFiniteMagnitude)
        let textRect   = attrStr.boundingRect(with: boundSize, options: [.usesLineFragmentOrigin,.usesFontLeading], context: nil)
        let cx = w * o.anchorX - textRect.width/2
        let cy = h * o.anchorY - textRect.height/2
        if let bg = o.backgroundColor {
            let pad = o.backgroundPadding
            let bgRect = CGRect(x:cx-pad, y:cy-pad, width:textRect.width+pad*2, height:textRect.height+pad*2)
            let path   = UIBezierPath(roundedRect: bgRect, cornerRadius: o.backgroundRadius)
            bg.setFill(); path.fill()
        }
        attrStr.draw(in: CGRect(x:cx, y:cy, width:maxW, height:textRect.height+10))
        return UIGraphicsGetImageFromCurrentImageContext() ?? base
    }

    // ── filters ───────────────────────────────────────────────────────────

    func applyFilters(_ input: UIImage, options f: FilterOptions) -> UIImage {
        guard var ci = CIImage(image: input) else { return input }
        if f.grayscale || f.saturation != 1 { let fl = CIFilter.colorControls(); fl.inputImage = ci; fl.saturation = f.grayscale ? 0 : f.saturation; fl.brightness = 0; fl.contrast = 1; if let o = fl.outputImage { ci = o } }
        if f.brightness != 0 || f.contrast != 1 { let fl = CIFilter.colorControls(); fl.inputImage = ci; fl.brightness = f.brightness; fl.contrast = f.contrast; fl.saturation = 1; if let o = fl.outputImage { ci = o } }
        if f.hue != 0 { let fl = CIFilter.hueAdjust(); fl.inputImage = ci; fl.angle = f.hue; if let o = fl.outputImage { ci = o } }
        if f.sepia > 0 { let fl = CIFilter.sepiaTone(); fl.inputImage = ci; fl.intensity = f.sepia; if let o = fl.outputImage { ci = o } }
        if f.vignette > 0 { let fl = CIFilter.vignette(); fl.inputImage = ci; fl.intensity = f.vignette * 2; fl.radius = 1.5; if let o = fl.outputImage { ci = o } }
        if f.blur > 0 { let fl = CIFilter.gaussianBlur(); fl.inputImage = ci; fl.radius = f.blur * 3; if let o = fl.outputImage { ci = o } }
        let extent = ci.extent
        guard let cg = ciContext.createCGImage(ci, from: extent) else { return input }
        var result = UIImage(cgImage: cg)
        if let tint = f.tintColor { result = drawTint(over: result, color: tint, opacity: f.tintOpacity) }
        if f.opacity < 1 { result = applyOpacity(result, alpha: f.opacity) }
        return result
    }

    private func drawTint(over b: UIImage, color: UIColor, opacity: Float) -> UIImage {
        UIGraphicsBeginImageContextWithOptions(b.size, false, b.scale); b.draw(at:.zero)
        color.withAlphaComponent(CGFloat(opacity)).setFill(); UIRectFill(CGRect(origin:.zero,size:b.size))
        let r = UIGraphicsGetImageFromCurrentImageContext() ?? b; UIGraphicsEndImageContext(); return r
    }

    private func applyOpacity(_ image: UIImage, alpha: Float) -> UIImage {
        UIGraphicsBeginImageContextWithOptions(image.size, false, image.scale)
        image.draw(at: .zero, blendMode: .normal, alpha: CGFloat(alpha))
        let r = UIGraphicsGetImageFromCurrentImageContext() ?? image; UIGraphicsEndImageContext(); return r
    }

    // ── cache helpers ─────────────────────────────────────────────────────

    private func cacheFileURL(for url: String) -> URL {
        let hash = url.data(using:.utf8).map { $0.map{String(format:"%02x",$0)}.joined() } ?? String(url.hashValue)
        return cacheDirectory.appendingPathComponent("wp_\(hash.prefix(32)).jpg")
    }

    private var cacheDirectory: URL {
        let d = FileManager.default.urls(for:.cachesDirectory,in:.userDomainMask)[0].appendingPathComponent(cacheDirName)
        try? FileManager.default.createDirectory(at:d,withIntermediateDirectories:true); return d
    }
}

enum WPError: Error { case invalidURL(String); case loadFailed(String) }

extension UIColor {
    convenience init?(hex: String) {
        var h = hex.trimmingCharacters(in:.whitespacesAndNewlines).uppercased()
        if h.hasPrefix("#") { h.removeFirst() }
        guard h.count == 6 else { return nil }
        var v: UInt64 = 0; Scanner(string:h).scanHexInt64(&v)
        self.init(red:CGFloat((v&0xFF0000)>>16)/255, green:CGFloat((v&0x00FF00)>>8)/255, blue:CGFloat(v&0x0000FF)/255, alpha:1)
    }
}
