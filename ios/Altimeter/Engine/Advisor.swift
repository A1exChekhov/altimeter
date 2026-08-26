import Foundation

struct AdvisorInput {
    let now: Date
    let altitude: Double?
    let verticalSpeedMetersPerMinute: Double?
    let hasFix: Bool
    let locationAuthorized: Bool
    let oxygenPercent: Double?
    let oxygenDate: Date?
    let heartRate: Double?
    let heartRateDate: Date?
    let pressureTrendHPAperHour: Double?
}

struct Advisor {
    func evaluate(_ input: AdvisorInput) -> [Advice] {
        var result: [Advice] = []

        if let trend = input.pressureTrendHPAperHour {
            let value = String(format: "%.1f", abs(trend))
            if trend <= -1.6 {
                result.append(Advice(kind: .pressureFallingFast, severity: .warning, value: value))
            } else if trend <= -0.8 {
                result.append(Advice(kind: .pressureFalling, severity: .caution, value: value))
            } else if trend >= 1.2 {
                result.append(Advice(kind: .pressureRising, severity: .info, value: nil))
            }
        }

        if let altitude = input.altitude {
            if altitude >= 1_500 {
                result.append(Advice(kind: .hydration, severity: .info, value: nil))
            }
        }

        if input.locationAuthorized && !input.hasFix {
            result.append(Advice(kind: .gpsWeak, severity: .info, value: nil))
        }

        return result.sorted { $0.severity > $1.severity }.prefix(4).map { $0 }
    }
}
