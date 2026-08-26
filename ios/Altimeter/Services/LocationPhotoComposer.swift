import UIKit

struct LocationPhotoStamp {
    let altitude: String
    let pressure: String
    let coordinates: String
    let localTime: String
}

enum LocationPhotoComposer {
    static func compose(image: UIImage, stamp: LocationPhotoStamp) throws -> URL {
        let sourceSize = image.size
        let longest = max(sourceSize.width, sourceSize.height)
        let scale = min(1, 2_560 / max(longest, 1))
        let size = CGSize(width: sourceSize.width * scale, height: sourceSize.height * scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let output = renderer.image { context in
            image.draw(in: CGRect(origin: .zero, size: size))
            drawStamp(in: context.cgContext, size: size, stamp: stamp)
        }
        guard let data = output.jpegData(compressionQuality: 0.92) else {
            throw ComposerError.encodingFailed
        }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("altimeter-location-\(UUID().uuidString).jpg")
        try data.write(to: url, options: .atomic)
        return url
    }

    private static func drawStamp(in context: CGContext, size: CGSize, stamp: LocationPhotoStamp) {
        let shortest = min(size.width, size.height)
        let margin = max(24, shortest * 0.035)
        let titleSize = min(max(size.width * 0.030, 26), 54)
        let altitudeSize = min(max(size.width * 0.060, 46), 96)
        let detailSize = min(max(size.width * 0.027, 24), 48)
        let gap = detailSize * 0.55
        let panelHeight = titleSize + altitudeSize + detailSize * 3 + gap * 5 + margin
        let panel = CGRect(
            x: margin,
            y: size.height - panelHeight - margin,
            width: size.width - margin * 2,
            height: panelHeight
        )
        context.setFillColor(UIColor(red: 0.03, green: 0.05, blue: 0.09, alpha: 0.82).cgColor)
        context.addPath(UIBezierPath(roundedRect: panel, cornerRadius: margin * 0.55).cgPath)
        context.fillPath()

        let left = panel.minX + margin
        var baseline = panel.minY + margin
        draw(
            "ALTIMETER KAILAS",
            at: CGPoint(x: left, y: baseline),
            font: .boldSystemFont(ofSize: titleSize),
            color: .white
        )
        baseline += titleSize + gap
        draw(
            stamp.altitude,
            at: CGPoint(x: left, y: baseline),
            font: .boldSystemFont(ofSize: altitudeSize),
            color: UIColor(red: 0.94, green: 0.75, blue: 0.36, alpha: 1)
        )
        baseline += altitudeSize + gap
        for line in [stamp.pressure, stamp.coordinates, stamp.localTime] {
            draw(
                line,
                at: CGPoint(x: left, y: baseline),
                font: .systemFont(ofSize: detailSize),
                color: .white
            )
            baseline += detailSize + gap
        }

        let signature = "Errarium™ by Aleksey Hermes"
        let signatureFont = UIFont.systemFont(ofSize: detailSize * 0.72)
        let signatureWidth = (signature as NSString).size(withAttributes: [.font: signatureFont]).width
        draw(
            signature,
            at: CGPoint(
                x: panel.maxX - margin - signatureWidth,
                y: panel.maxY - margin * 0.55 - signatureFont.lineHeight
            ),
            font: signatureFont,
            color: UIColor.white.withAlphaComponent(0.8)
        )
    }

    private static func draw(_ text: String, at point: CGPoint, font: UIFont, color: UIColor) {
        (text as NSString).draw(at: point, withAttributes: [.font: font, .foregroundColor: color])
    }

    enum ComposerError: Error { case encodingFailed }
}
