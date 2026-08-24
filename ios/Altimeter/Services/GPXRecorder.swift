import Foundation
import CoreLocation

final class GPXRecorder {
    struct Point {
        let coordinate: CLLocationCoordinate2D
        let elevation: Double?
        let date: Date
        let startsNewSegment: Bool
    }

    private(set) var points: [Point] = []
    private(set) var distanceMeters = 0.0
    private(set) var ascentMeters = 0.0
    private(set) var descentMeters = 0.0
    private(set) var movingTime: TimeInterval = 0
    private(set) var stoppedTime: TimeInterval = 0

    private var lastAcceptedElevation: Double?
    private var lastBearing: Double?
    private var samplingMode: TrackSamplingMode = .everySecond
    private(set) var startedAt = Date()

    var lastPointDate: Date? { points.last?.date }

    func begin(at date: Date = Date()) {
        points.removeAll(keepingCapacity: true)
        distanceMeters = 0
        ascentMeters = 0
        descentMeters = 0
        movingTime = 0
        stoppedTime = 0
        lastAcceptedElevation = nil
        lastBearing = nil
        startedAt = date
    }

    func setSamplingMode(_ mode: TrackSamplingMode) {
        samplingMode = mode
    }

    @discardableResult
    func offer(location: CLLocation, elevation: Double?, date: Date = Date()) -> Bool {
        guard location.horizontalAccuracy >= 0, location.horizontalAccuracy <= 35 else { return false }

        var startsNewSegment = points.isEmpty
        var acceptedBearing: Double?

        if let last = points.last {
            let elapsed = date.timeIntervalSince(last.date)
            let previousLocation = CLLocation(
                latitude: last.coordinate.latitude,
                longitude: last.coordinate.longitude
            )
            let moved = location.distance(from: previousLocation)
            let speed = moved / max(elapsed, 0.001)
            let bearing = Self.bearing(from: last.coordinate, to: location.coordinate)
            acceptedBearing = bearing
            let minimumMovement = min(2.5, max(0.8, location.horizontalAccuracy * 0.10))
            let isSharpTurn = lastBearing.map {
                Self.angularDifference($0, bearing) >= 15 && moved >= minimumMovement
            } ?? false
            let interval: TimeInterval
            switch samplingMode {
            case .everySecond: interval = 1
            case .everyTwoSeconds: interval = 2
            case .everyFourSeconds: interval = 4
            case .automatic:
                interval = speed >= 7 ? 1 : (speed >= 2 ? 2 : 4)
            }
            guard elapsed >= interval || isSharpTurn else { return false }
            guard elapsed >= 0.75 else { return false }
            if moved < minimumMovement, elapsed < 10 { return false }

            startsNewSegment = (elapsed > 30 && moved > 10) || (moved > 250 && speed > 12)
            if !startsNewSegment, moved < 10_000 {
                distanceMeters += moved
                if elapsed <= 120 {
                    if speed >= 0.35 { movingTime += elapsed } else { stoppedTime += elapsed }
                }
            }
        }

        if let elevation {
            if let lastAcceptedElevation, abs(elevation - lastAcceptedElevation) >= 2 {
                if elevation > lastAcceptedElevation { ascentMeters += elevation - lastAcceptedElevation }
                else { descentMeters += lastAcceptedElevation - elevation }
                self.lastAcceptedElevation = elevation
            } else if lastAcceptedElevation == nil {
                lastAcceptedElevation = elevation
            }
        }

        points.append(
            Point(
                coordinate: location.coordinate,
                elevation: elevation,
                date: date,
                startsNewSegment: startsNewSegment
            )
        )
        lastBearing = startsNewSegment ? nil : acceptedBearing
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
        for (index, point) in points.enumerated() {
            if index > 0, point.startsNewSegment {
                xml += "  </trkseg><trkseg>\n"
            }
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

    private static func bearing(
        from start: CLLocationCoordinate2D,
        to end: CLLocationCoordinate2D
    ) -> Double {
        let lat1 = start.latitude * .pi / 180
        let lat2 = end.latitude * .pi / 180
        let deltaLongitude = (end.longitude - start.longitude) * .pi / 180
        let y = sin(deltaLongitude) * cos(lat2)
        let x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLongitude)
        return atan2(y, x) * 180 / .pi
    }

    private static func angularDifference(_ first: Double, _ second: Double) -> Double {
        let raw = abs(first - second).truncatingRemainder(dividingBy: 360)
        return raw > 180 ? 360 - raw : raw
    }

    private static let iso8601: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()
}
