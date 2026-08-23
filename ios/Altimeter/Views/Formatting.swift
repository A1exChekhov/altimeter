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
        meters >= 1_000 ? String(format: "%.2f км", meters / 1_000) : "\(Int(meters.rounded())) м"
    }

    static func freshness(_ date: Date?) -> String {
        guard let date else { return "нет данных" }
        let interval = max(0, Date().timeIntervalSince(date))
        if interval < 60 { return "только что" }
        if interval < 3_600 { return "\(Int(interval / 60)) мин назад" }
        return "\(Int(interval / 3_600)) ч назад"
    }

    static func duration(_ interval: TimeInterval) -> String {
        let seconds = max(0, Int(interval.rounded()))
        let hours = seconds / 3_600
        let minutes = (seconds % 3_600) / 60
        if hours > 0 { return String(format: "%d:%02d ч", hours, minutes) }
        return "\(minutes) мин"
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
    var title: String {
        switch kind {
        case .pressureFallingFast: "Давление быстро падает"
        case .pressureFalling: "Давление падает"
        case .pressureRising: "Давление растёт"
        case .altitudeAcclimatize: "Нужна акклиматизация"
        case .altitudeHigh: "Большая высота"
        case .altitudeVeryHigh: "Очень большая высота"
        case .fastAscent: "Быстрый набор"
        case .hydration: "Пейте регулярно"
        case .oxygenLow: "Снижен SpO₂"
        case .oxygenVeryLow: "Низкий SpO₂"
        case .heartRateHigh: "Высокий пульс"
        case .gpsWeak: "Слабый GPS"
        }
    }

    var message: String {
        switch kind {
        case .pressureFallingFast:
            "−\(value ?? "?") гПа/ч: возможна гроза. Подумайте об укрытии или спуске."
        case .pressureFalling:
            "−\(value ?? "?") гПа/ч: погода может ухудшиться."
        case .pressureRising:
            "Погода, вероятно, улучшается."
        case .altitudeAcclimatize:
            "Выше 2500 м возможна горная болезнь — набирайте высоту постепенно."
        case .altitudeHigh:
            "Ограничьте суточный набор высоты сна до 300–500 м."
        case .altitudeVeryHigh:
            "При головной боли, тошноте или головокружении немедленно спускайтесь."
        case .fastAscent:
            "Снизьте темп — это поможет акклиматизации."
        case .hydration:
            "На высоте организм быстрее теряет воду."
        case .oxygenLow:
            "SpO₂ \(value ?? "?")%: отдохните; при плохом самочувствии спускайтесь."
        case .oxygenVeryLow:
            "SpO₂ \(value ?? "?")%: остановитесь, согрейтесь; при ухудшении ищите помощь."
        case .heartRateHigh:
            "Пульс \(value ?? "?") уд/мин на высоте — сделайте паузу."
        case .gpsWeak:
            "Точность снижена. Выйдите на открытое место."
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
