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
            if altitude >= 4_000 {
                result.append(Advice(kind: .altitudeVeryHigh, severity: .warning, value: nil))
            } else if altitude >= 3_000 {
                result.append(Advice(kind: .altitudeHigh, severity: .caution, value: nil))
            } else if altitude >= 2_500 {
                result.append(Advice(kind: .altitudeAcclimatize, severity: .info, value: nil))
            }
            if altitude > 2_000, (input.verticalSpeedMetersPerMinute ?? 0) >= 12 {
                result.append(Advice(kind: .fastAscent, severity: .caution, value: nil))
            }
            if altitude >= 1_500 {
                result.append(Advice(kind: .hydration, severity: .info, value: nil))
            }
        }

        if let oxygen = input.oxygenPercent, isFresh(input.oxygenDate, now: input.now, maxAge: 45 * 60) {
            let value = String(Int(oxygen.rounded()))
            if oxygen < 88 {
                result.append(Advice(kind: .oxygenVeryLow, severity: .warning, value: value))
            } else if oxygen <= 92 {
                result.append(Advice(kind: .oxygenLow, severity: .caution, value: value))
            }
        }

        if let heartRate = input.heartRate,
           (input.altitude ?? 0) > 2_500,
           heartRate >= 120,
           isFresh(input.heartRateDate, now: input.now, maxAge: 15 * 60) {
            result.append(Advice(kind: .heartRateHigh, severity: .info, value: String(Int(heartRate.rounded()))))
        }

        if input.locationAuthorized && !input.hasFix {
            result.append(Advice(kind: .gpsWeak, severity: .info, value: nil))
        }

        return result.sorted { $0.severity > $1.severity }.prefix(4).map { $0 }
    }

    private func isFresh(_ date: Date?, now: Date, maxAge: TimeInterval) -> Bool {
        guard let date else { return false }
        return now.timeIntervalSince(date) >= 0 && now.timeIntervalSince(date) <= maxAge
    }
}
