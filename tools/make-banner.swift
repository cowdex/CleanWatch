// Draws the Android TV home-screen banner (16:9) as PNGs.
// Run from the project root:  swift tools/make-banner.swift
import AppKit

func banner(width: Int, height: Int, to path: String) throws {
    guard let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil, pixelsWide: width, pixelsHigh: height,
        bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
        colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0),
        let context = NSGraphicsContext(bitmapImageRep: rep)
    else { fatalError("no bitmap context") }

    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = context
    let w = CGFloat(width), h = CGFloat(height), u = h / 180  // design unit: 1 = 1px at 320x180

    // background
    NSGradient(starting: NSColor(srgbRed: 0.075, green: 0.078, blue: 0.098, alpha: 1),
               ending: NSColor(srgbRed: 0.027, green: 0.027, blue: 0.035, alpha: 1))!
        .draw(in: NSRect(x: 0, y: 0, width: w, height: h), angle: -90)

    let lime = NSColor(srgbRed: 0x95 / 255.0, green: 1, blue: 0x50 / 255.0, alpha: 1)

    // play triangle with rounded corners
    let cx = 62 * u, cy = h / 2, s = 30 * u
    let tri = NSBezierPath()
    tri.move(to: NSPoint(x: cx - s * 0.42, y: cy + s * 0.62))
    tri.line(to: NSPoint(x: cx + s * 0.62, y: cy))
    tri.line(to: NSPoint(x: cx - s * 0.42, y: cy - s * 0.62))
    tri.close()
    tri.lineJoinStyle = .round
    tri.lineWidth = 9 * u
    lime.setStroke()
    lime.setFill()
    tri.stroke()
    tri.fill()

    // wordmark
    let font = NSFont.systemFont(ofSize: 38 * u, weight: .bold)
    let text = NSMutableAttributedString(
        string: "CleanWatch",
        attributes: [.font: font, .foregroundColor: NSColor.white, .kern: -0.6 * u])
    text.addAttribute(.foregroundColor, value: lime, range: NSRange(location: 0, length: 5))
    let size = text.size()
    text.draw(at: NSPoint(x: 104 * u, y: (h - size.height) / 2))

    NSGraphicsContext.restoreGraphicsState()
    guard let png = rep.representation(using: .png, properties: [:]) else { fatalError("no png") }
    try FileManager.default.createDirectory(
        atPath: (path as NSString).deletingLastPathComponent, withIntermediateDirectories: true)
    try png.write(to: URL(fileURLWithPath: path))
    print("wrote \(path) (\(width)x\(height))")
}

try banner(width: 320, height: 180, to: "app/src/main/res/drawable-xhdpi/banner.png")
try banner(width: 640, height: 360, to: "app/src/main/res/drawable-xxxhdpi/banner.png")
