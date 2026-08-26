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
            statusMessage = L10n.string("notification.altitude.unavailable")
            return
        }
        do {
            let center = UNUserNotificationCenter.current()
            let granted = try await center.requestAuthorization(options: [.alert, .sound])
            guard granted else {
                statusMessage = L10n.string("notification.permission.required")
                return
            }

            let shown = unit.value(fromMeters: altitude)
            let content = UNMutableNotificationContent()
            content.title = "⛰ \(Int(shown.rounded())) \(unit.symbol)"
            var details: [String] = []
            if let placeName = state.placeName { details.append(placeName) }
            if let pressure = state.pressureHPA { details.append(L10n.string("format.pressure", pressure)) }
            content.body = details.isEmpty
                ? L10n.string("altitude.aboveSeaLevel")
                : details.joined(separator: " · ")
            content.sound = .default
            content.threadIdentifier = "altimeter"

            let request = UNNotificationRequest(
                identifier: "altimeter-\(UUID().uuidString)",
                content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: 1, repeats: false)
            )
            try await center.add(request)
            statusMessage = L10n.string("notification.sent")
        } catch {
            statusMessage = L10n.string("notification.error", error.localizedDescription)
        }
    }

    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }
}
