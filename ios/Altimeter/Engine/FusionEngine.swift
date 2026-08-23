import Foundation

/// Combines a smooth barometric height with an absolute GPS altitude.
/// The Kalman state is the slowly changing offset between both sensors.
final class FusionEngine {
    private(set) var mode: CalibrationMode = .automatic
    private(set) var pressureHPA: Double?

    private var qnhHPA = 1013.25
    private var manualOffset: Double?
    private var pendingManualAltitude: Double?
    private var standardBarometricAltitude: Double?

    private var automaticOffset: Double?
    private var automaticOffsetVariance = 1_600.0

    private var gpsAltitude: Double?
    private var gpsVariance = 1_600.0

    func onPressure(_ hpa: Double) {
        guard hpa.isFinite, hpa > 0 else { return }
        pressureHPA = hpa
        let altitude = Self.standardAltitude(pressureHPA: hpa, seaLevelHPA: 1013.25)
        standardBarometricAltitude = altitude
        if let pendingManualAltitude {
            manualOffset = pendingManualAltitude - altitude
            self.pendingManualAltitude = nil
        }
    }

    func onGPSAltitude(_ meters: Double, verticalAccuracy: Double?) {
        guard meters.isFinite else { return }
        let sigma = min(max(verticalAccuracy ?? 25.0, 3.0), 100.0)
        guard sigma <= 60 else { return }
        let measurementVariance = sigma * sigma

        if let current = gpsAltitude {
            gpsVariance += 2.0
            let gain = gpsVariance / (gpsVariance + measurementVariance)
            gpsAltitude = current + gain * (meters - current)
            gpsVariance *= 1.0 - gain
        } else {
            gpsAltitude = meters
            gpsVariance = measurementVariance
        }

        guard let barometric = standardBarometricAltitude else { return }
        let measuredOffset = meters - barometric
        if let current = automaticOffset {
            automaticOffsetVariance += 0.03
            let gain = automaticOffsetVariance / (automaticOffsetVariance + measurementVariance)
            automaticOffset = current + gain * (measuredOffset - current)
            automaticOffsetVariance *= 1.0 - gain
        } else {
            automaticOffset = measuredOffset
            automaticOffsetVariance = measurementVariance
        }
    }

    func apply(mode: CalibrationMode, manualOffset: Double?, qnhHPA: Double) {
        self.mode = mode
        self.qnhHPA = qnhHPA
        if let manualOffset { self.manualOffset = manualOffset }
    }

    @discardableResult
    func calibrateManually(knownAltitude: Double) -> Double? {
        guard let standardBarometricAltitude else {
            pendingManualAltitude = knownAltitude
            return nil
        }
        let offset = knownAltitude - standardBarometricAltitude
        manualOffset = offset
        return offset
    }

    var displayedAltitude: Double? {
        switch mode {
        case .automatic:
            if let standardBarometricAltitude, let automaticOffset {
                return standardBarometricAltitude + automaticOffset
            }
            return gpsAltitude
        case .manual:
            if let standardBarometricAltitude, let manualOffset {
                return standardBarometricAltitude + manualOffset
            }
            return gpsAltitude
        case .qnh:
            if let pressureHPA {
                return Self.standardAltitude(pressureHPA: pressureHPA, seaLevelHPA: qnhHPA)
            }
            return gpsAltitude
        }
    }

    var displayedAccuracy: Double? {
        switch mode {
        case .automatic:
            if standardBarometricAltitude != nil, automaticOffset != nil {
                return max(sqrt(automaticOffsetVariance), 1.0) + 0.5
            }
            return gpsOnlyAccuracy
        case .manual:
            return standardBarometricAltitude != nil && manualOffset != nil ? nil : gpsOnlyAccuracy
        case .qnh:
            return pressureHPA != nil ? nil : gpsOnlyAccuracy
        }
    }

    var isCalibrating: Bool {
        mode == .automatic && standardBarometricAltitude != nil && automaticOffset == nil
    }

    private var gpsOnlyAccuracy: Double? {
        gpsAltitude == nil ? nil : max(sqrt(gpsVariance), 2.0)
    }

    static func standardAltitude(pressureHPA: Double, seaLevelHPA: Double) -> Double {
        44_330.0 * (1.0 - pow(pressureHPA / seaLevelHPA, 0.190_294_9))
    }
}

