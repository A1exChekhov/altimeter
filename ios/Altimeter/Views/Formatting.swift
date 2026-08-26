import Foundation
import SwiftUI

enum AltimeterFormat {
    static func altitude(_ meters: Double?, unit: AltitudeUnit) -> String {
        guard let meters else { return "—" }
        return String(Int(unit.value(fromMeters: meters).rounded()))
    }

    static func decimalAltitude(_ meters: Double?, unit: AltitudeUnit) -> String {
        guard let meters else { return "—" }
        return String(format: "%.1f", unit.value(fromMeters: meters))
    }

    static func speed(_ metersPerMinute: Double?, unit: AltitudeUnit) -> String {
        guard let metersPerMinute else { return "—" }
        return String(format: "%+.1f", unit.value(fromMeters: metersPerMinute))
    }

    static func distance(_ meters: Double) -> String {
        meters >= 1_000
            ? L10n.string("format.distance.km", meters / 1_000)
            : L10n.string("format.distance.m", Int(meters.rounded()))
    }

    static func freshness(_ date: Date?) -> String {
        guard let date else { return L10n.string("freshness.none") }
        let interval = max(0, Date().timeIntervalSince(date))
        if interval < 60 { return L10n.string("freshness.now") }
        if interval < 3_600 { return L10n.string("freshness.minutes", Int(interval / 60)) }
        return L10n.string("freshness.hours", Int(interval / 3_600))
    }

    static func duration(_ interval: TimeInterval) -> String {
        let seconds = max(0, Int(interval.rounded()))
        let hours = seconds / 3_600
        let minutes = (seconds % 3_600) / 60
        if hours > 0 { return L10n.string("duration.hours", hours, minutes) }
        return L10n.string("duration.minutes", minutes)
    }

    static func altitudeColor(_ meters: Double?) -> Color {
        guard let meters else { return .secondary }
        switch meters {
        case ..<1_500: return Color(red: 0.25, green: 0.88, blue: 0.73)
        case ..<2_500: return Color(red: 0.77, green: 0.91, blue: 0.34)
        case ..<3_500: return Color(red: 1.0, green: 0.68, blue: 0.25)
        default: return Color(red: 1.0, green: 0.36, blue: 0.35)
        }
    }
}

extension Advice {
    var isFieldInformation: Bool {
        switch kind {
        case .pressureFallingFast, .pressureFalling, .pressureRising, .hydration, .gpsWeak:
            true
        case .altitudeAcclimatize, .altitudeHigh, .altitudeVeryHigh, .fastAscent,
             .oxygenLow, .oxygenVeryLow, .heartRateHigh:
            false
        }
    }

    var title: String {
        switch kind {
        case .pressureFallingFast: L10n.string("advice.pressure.fast.title")
        case .pressureFalling: L10n.string("advice.pressure.falling.title")
        case .pressureRising: L10n.string("advice.pressure.rising.title")
        case .altitudeAcclimatize: L10n.string("advice.altitude.acclimatize.title")
        case .altitudeHigh: L10n.string("advice.altitude.high.title")
        case .altitudeVeryHigh: L10n.string("advice.altitude.veryHigh.title")
        case .fastAscent: L10n.string("advice.ascent.fast.title")
        case .hydration: L10n.string("advice.hydration.title")
        case .oxygenLow: L10n.string("advice.oxygen.low.title")
        case .oxygenVeryLow: L10n.string("advice.oxygen.veryLow.title")
        case .heartRateHigh: L10n.string("advice.heart.high.title")
        case .gpsWeak: L10n.string("advice.gps.weak.title")
        }
    }

    var message: String {
        switch kind {
        case .pressureFallingFast:
            L10n.string("advice.pressure.fast.message", value ?? "?")
        case .pressureFalling:
            L10n.string("advice.pressure.falling.message", value ?? "?")
        case .pressureRising:
            L10n.string("advice.pressure.rising.message")
        case .altitudeAcclimatize:
            L10n.string("advice.altitude.acclimatize.message")
        case .altitudeHigh:
            L10n.string("advice.altitude.high.message")
        case .altitudeVeryHigh:
            L10n.string("advice.altitude.veryHigh.message")
        case .fastAscent:
            L10n.string("advice.ascent.fast.message")
        case .hydration:
            L10n.string("advice.hydration.message")
        case .oxygenLow:
            L10n.string("advice.oxygen.low.message", value ?? "?")
        case .oxygenVeryLow:
            L10n.string("advice.oxygen.veryLow.message", value ?? "?")
        case .heartRateHigh:
            L10n.string("advice.heart.high.message", value ?? "?")
        case .gpsWeak:
            L10n.string("advice.gps.weak.message")
        }
    }

    var color: Color {
        switch severity {
        case .info: Color.cyan
        case .caution: Color.orange
        case .warning: Color.red
        }
    }

    var icon: String {
        switch kind {
        case .pressureFallingFast, .pressureFalling, .pressureRising: "cloud.sun.rain.fill"
        case .oxygenLow, .oxygenVeryLow: "lungs.fill"
        case .heartRateHigh: "heart.fill"
        case .hydration: "drop.fill"
        case .gpsWeak: "location.slash.fill"
        default: "mountain.2.fill"
        }
    }
}
