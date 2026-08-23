import Foundation
import UserNotifications

@MainActor
final class NotificationService: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    @Published private(set) var statusMessage: String?

    override init() {
        super.init()
        UNUserNotificationCenter.current().delegate = self
    }

    func sendAltitude(_ state: AltimeterState, unit: AltitudeUnit) async {
        guard let altitude = state.altitude else {
            statusMessage = "Высота ещё не определена."
            return
        }
        do {
            let center = UNUserNotificationCenter.current()
            let granted = try await center.requestAuthorization(options: [.alert, .sound])
            guard granted else {
                statusMessage = "Разрешите уведомления в настройках iPhone."
                return
            }

            let shown = unit.value(fromMeters: altitude)
            let content = UNMutableNotificationContent()
            content.title = "⛰ \(Int(shown.rounded())) \(unit.symbol)"
            var details: [String] = []
            if let placeName = state.placeName { details.append(placeName) }
            if let pressure = state.pressureHPA { details.append(String(format: "%.1f гПа", pressure)) }
            content.body = details.isEmpty ? "Высота над уровнем моря" : details.joined(separator: " · ")
            content.sound = .default
            content.threadIdentifier = "altimeter"

            let request = UNNotificationRequest(
                identifier: "altimeter-\(UUID().uuidString)",
                content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
            )
            try await center.add(request)
            statusMessage = "Уведомление создано. Apple Watch покажет его по обычным правилам зеркалирования."
        } catch {
            statusMessage = "Не удалось отправить уведомление: \(error.localizedDescription)"
        }
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }
}

