import Foundation

final class TrackStatistics {
    private let historyStep: TimeInterval = 2
    private let maxHistoryPoints = 45_000
    private let speedWindow: TimeInterval = 20
    private let ascentThreshold = 3.0

    private var speedPoints: [ChartPoint] = []
    private var lastAcceptedAltitude: Double?
    private var lastHistoryDate = Date.distantPast

    private(set) var history: [ChartPoint] = []
    private(set) var minAltitude: Double?
    private(set) var maxAltitude: Double?
    private(set) var ascent = 0.0
    private(set) var descent = 0.0

    func add(date: Date, altitude: Double) {
        minAltitude = min(minAltitude ?? altitude, altitude)
        maxAltitude = max(maxAltitude ?? altitude, altitude)

        if let lastAcceptedAltitude {
            let delta = altitude - lastAcceptedAltitude
            if abs(delta) >= ascentThreshold {
                if delta > 0 { ascent += delta } else { descent -= delta }
                self.lastAcceptedAltitude = altitude
            }
        } else {
            lastAcceptedAltitude = altitude
        }

        let point = ChartPoint(date: date, altitude: altitude)
        speedPoints.append(point)
        let speedCutoff = date.addingTimeInterval(-speedWindow)
        speedPoints.removeAll { $0.date < speedCutoff }

        if date.timeIntervalSince(lastHistoryDate) >= historyStep {
            lastHistoryDate = date
            history.append(point)
            if history.count > maxHistoryPoints {
                history.removeFirst(history.count - maxHistoryPoints)
            }
        }
    }

    var verticalSpeedMetersPerMinute: Double? {
        guard speedPoints.count >= 6, let first = speedPoints.first else { return nil }
        var sumX = 0.0
        var sumY = 0.0
        var sumXX = 0.0
        var sumXY = 0.0
        let count = Double(speedPoints.count)

        for point in speedPoints {
            let x = point.date.timeIntervalSince(first.date)
            let y = point.altitude
            sumX += x
            sumY += y
            sumXX += x * x
            sumXY += x * y
        }
        let denominator = count * sumXX - sumX * sumX
        guard denominator > 0.000_001 else { return nil }
        return ((count * sumXY - sumX * sumY) / denominator) * 60.0
    }

    func reset() {
        history.removeAll()
        speedPoints.removeAll()
        minAltitude = nil
        maxAltitude = nil
        ascent = 0
        descent = 0
        lastAcceptedAltitude = nil
        lastHistoryDate = .distantPast
    }
}
