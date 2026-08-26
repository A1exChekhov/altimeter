import Foundation

/// Produces a stable display altitude while preserving sustained vertical movement.
/// A level value is held until a robust, directionally consistent trend is present.
final class AltitudeStabilizer {
    private struct Sample {
        let date: Date
        let altitude: Double
    }

    private enum MotionMode {
        case level
        case vertical
        case fast
    }

    private var samples: [Sample] = []
    private var visibleAltitude: Double?
    private var mode: MotionMode = .level
    private var quietSince: Date?
    private var lastUpdateAt: Date?

    func update(rawAltitude: Double, at date: Date) -> Double? {
        guard rawAltitude.isFinite else { return visibleAltitude }

        let effectiveDate: Date
        if let lastUpdateAt, date <= lastUpdateAt {
            effectiveDate = lastUpdateAt.addingTimeInterval(0.001)
        } else {
            effectiveDate = date
        }
        let previousUpdate = lastUpdateAt
        lastUpdateAt = effectiveDate

        guard let current = visibleAltitude else {
            visibleAltitude = rawAltitude
            samples = [Sample(date: effectiveDate, altitude: rawAltitude)]
            return rawAltitude
        }

        if let last = samples.last,
           effectiveDate.timeIntervalSince(last.date) < Constants.minimumSampleInterval {
            samples[samples.count - 1] = Sample(date: effectiveDate, altitude: rawAltitude)
        } else {
            samples.append(Sample(date: effectiveDate, altitude: rawAltitude))
        }
        samples.removeAll { effectiveDate.timeIntervalSince($0.date) > Constants.trendWindow }

        let trend = analyzeTrend()
        let requestedMode: MotionMode = trend.fast ? .fast : (trend.vertical ? .vertical : .level)
        if requestedMode != .level {
            mode = requestedMode
            quietSince = nil
        } else if mode != .level {
            if quietSince == nil { quietSince = effectiveDate }
            if let quietSince,
               effectiveDate.timeIntervalSince(quietSince) >= Constants.quietConfirmation {
                mode = .level
                self.quietSince = nil
            }
        }

        guard mode != .level else { return current }
        let target = Self.median(samples.suffix(Constants.robustTargetSize).map(\.altitude))
        let dt = max(effectiveDate.timeIntervalSince(previousUpdate ?? effectiveDate), 0.001)
        let timeConstant = mode == .fast
            ? Constants.fastTimeConstant
            : Constants.verticalTimeConstant
        let alpha = min(max(1 - exp(-dt / timeConstant), 0), 1)
        let next = current + alpha * (target - current)
        if abs(next - current) >= Constants.activeDeadband {
            visibleAltitude = next
        }
        return visibleAltitude
    }

    func reset() {
        samples.removeAll()
        visibleAltitude = nil
        mode = .level
        quietSince = nil
        lastUpdateAt = nil
    }

    private func analyzeTrend() -> (vertical: Bool, fast: Bool) {
        guard samples.count >= 3 else { return (false, false) }
        let edgeSize = min(3, samples.count / 2)
        let first = Array(samples.prefix(edgeSize))
        let last = Array(samples.suffix(edgeSize))
        let firstTime = first.map(\.date.timeIntervalSinceReferenceDate).reduce(0, +) / Double(first.count)
        let lastTime = last.map(\.date.timeIntervalSinceReferenceDate).reduce(0, +) / Double(last.count)
        let span = lastTime - firstTime
        guard span > 0 else { return (false, false) }

        let netChange = Self.median(last.map(\.altitude)) - Self.median(first.map(\.altitude))
        let slope = netChange / span
        var positive = 0
        var negative = 0
        for (a, b) in zip(samples, samples.dropFirst()) {
            let delta = b.altitude - a.altitude
            if delta >= Constants.directionDelta { positive += 1 }
            if delta <= -Constants.directionDelta { negative += 1 }
        }
        let directional = positive + negative
        let consistency = directional == 0
            ? 0
            : Double(max(positive, negative)) / Double(directional)

        let fast = span >= Constants.fastMinimumSpan &&
            abs(netChange) >= Constants.fastMinimumChange &&
            abs(slope) >= Constants.fastMinimumSpeed &&
            directional >= Constants.fastMinimumDirectionalSamples &&
            consistency >= Constants.minimumDirectionConsistency
        let vertical = span >= Constants.verticalMinimumSpan &&
            abs(netChange) >= Constants.verticalMinimumChange &&
            abs(slope) >= Constants.verticalMinimumSpeed &&
            directional >= Constants.verticalMinimumDirectionalSamples &&
            consistency >= Constants.minimumDirectionConsistency
        return (vertical, fast)
    }

    private static func median(_ values: [Double]) -> Double {
        let sorted = values.sorted()
        let middle = sorted.count / 2
        if sorted.count.isMultiple(of: 2) {
            return (sorted[middle - 1] + sorted[middle]) / 2
        }
        return sorted[middle]
    }

    private enum Constants {
        static let minimumSampleInterval = 0.75
        static let trendWindow = 15.0
        static let robustTargetSize = 3
        static let quietConfirmation = 5.0
        static let directionDelta = 0.08
        static let minimumDirectionConsistency = 0.65
        static let verticalMinimumSpan = 7.0
        static let verticalMinimumChange = 0.8
        static let verticalMinimumSpeed = 0.055
        static let verticalMinimumDirectionalSamples = 4
        static let fastMinimumSpan = 2.0
        static let fastMinimumChange = 1.5
        static let fastMinimumSpeed = 0.6
        static let fastMinimumDirectionalSamples = 2
        static let verticalTimeConstant = 2.5
        static let fastTimeConstant = 0.7
        static let activeDeadband = 0.03
    }
}
