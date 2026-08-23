import Foundation
import CoreLocation

final class GPXRecorder {
    struct Point {
        let coordinate: CLLocationCoordinate2D
        let elevation: Double?
        let date: Date
    }

    private(set) var points: [Point] = []
    private(set) var distanceMeters = 0.0
    private(set) var ascentMeters = 0.0

    private var lastAcceptedElevation: Double?
    private(set) var startedAt = Date()

    var lastPointDate: Date? { points.last?.date }

    func begin(at date: Date = Date()) {
        points.removeAll(keepingCapacity: true)
        distanceMeters = 0
        ascentMeters = 0
        lastAcceptedElevation = nil
        startedAt = date
    }

    @discardableResult
    func offer(location: CLLocation, elevation: Double?, date: Date = Date()) -> Bool {
        guard location.horizontalAccuracy >= 0, location.horizontalAccuracy <= 50 else { return false }

        if let last = points.last {
            let elapsed = date.timeIntervalSince(last.date)
            guard elapsed >= 2 else { return false }
            let previousLocation = CLLocation(
                latitude: last.coordinate.latitude,
                longitude: last.coordinate.longitude
            )
            let moved = location.distance(from: previousLocation)
            if moved < 2, elapsed < 15 { return false }
            if moved < 10_000 { distanceMeters += moved }
        }

        if let elevation {
            if let lastAcceptedElevation, abs(elevation - lastAcceptedElevation) >= 2 {
                if elevation > lastAcceptedElevation { ascentMeters += elevation - lastAcceptedElevation }
                self.lastAcceptedElevation = elevation
            } else if lastAcceptedElevation == nil {
                lastAcceptedElevation = elevation
            }
        }

        points.append(Point(coordinate: location.coordinate, elevation: elevation, date: date))
        return true
    }

    func save(to url: URL) throws {
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try data().write(to: url, options: .atomic)
    }

    func data() -> Data {
        var xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="Errarium Altimeter" xmlns="http://www.topografix.com/GPX/1/1">
          <metadata><time>\(Self.iso8601.string(from: startedAt))</time></metadata>
          <trk><name>Altimeter track</name><trkseg>

        """
        for point in points {
            xml += String(
                format: "    <trkpt lat=\"%.7f\" lon=\"%.7f\">",
                locale: Locale(identifier: "en_US_POSIX"),
                point.coordinate.latitude,
                point.coordinate.longitude
            )
            if let elevation = point.elevation {
                xml += String(format: "<ele>%.1f</ele>", locale: Locale(identifier: "en_US_POSIX"), elevation)
            }
            xml += "<time>\(Self.iso8601.string(from: point.date))</time></trkpt>\n"
        }
        xml += "  </trkseg></trk>\n</gpx>\n"
        return Data(xml.utf8)
    }

    private static let iso8601: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
}
