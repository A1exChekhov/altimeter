import Foundation
#if canImport(WidgetKit)
import WidgetKit
#endif

struct AltimeterWidgetSnapshot: Codable, Equatable {
    var altitudeMeters: Double?
    var pressureHPA: Double?
    var latitude: Double?
    var longitude: Double?
    var usesFeet = false
    var trackIsRecording = false
    var trackDistanceMeters = 0.0
    var trackPointCount = 0
    var trackAscentMeters = 0.0
    var trackDescentMeters = 0.0
    var trackMovingTime: TimeInterval = 0
    var trackStoppedTime: TimeInterval = 0
    var heartRateBPM: Double?
    var oxygenPercent: Double?
    var stepsToday: Double?
    var activeCaloriesToday: Double?
    var heartRateSource: String?
    var oxygenSource: String?
    var updatedAt = Date.distantPast

    static let empty = AltimeterWidgetSnapshot()
}

enum WidgetSnapshotStore {
    static let appGroup = "group.ai.errarium.altimeter"
    private static let key = "altimeterWidgetSnapshot"

    static func read() -> AltimeterWidgetSnapshot {
        guard let data = defaults?.data(forKey: key),
              let snapshot = try? JSONDecoder().decode(AltimeterWidgetSnapshot.self, from: data)
        else { return .empty }
        return snapshot
    }

    static func updateAltitudeAndTrack(
        altitudeMeters: Double?,
        pressureHPA: Double?,
        latitude: Double?,
        longitude: Double?,
        usesFeet: Bool,
        trackIsRecording: Bool,
        trackDistanceMeters: Double,
        trackPointCount: Int,
        trackAscentMeters: Double,
        trackDescentMeters: Double,
        trackMovingTime: TimeInterval,
        trackStoppedTime: TimeInterval
    ) {
        var snapshot = read()
        snapshot.altitudeMeters = altitudeMeters
        snapshot.pressureHPA = pressureHPA
        snapshot.latitude = latitude
        snapshot.longitude = longitude
        snapshot.usesFeet = usesFeet
        snapshot.trackIsRecording = trackIsRecording
        snapshot.trackDistanceMeters = trackDistanceMeters
        snapshot.trackPointCount = trackPointCount
        snapshot.trackAscentMeters = trackAscentMeters
        snapshot.trackDescentMeters = trackDescentMeters
        snapshot.trackMovingTime = trackMovingTime
        snapshot.trackStoppedTime = trackStoppedTime
        snapshot.updatedAt = Date()
        write(snapshot)
    }

    static func updateVitals(
        heartRateBPM: Double?,
        oxygenPercent: Double?,
        stepsToday: Double?,
        activeCaloriesToday: Double?,
        heartRateSource: String?,
        oxygenSource: String?
    ) {
        var snapshot = read()
        snapshot.heartRateBPM = heartRateBPM
        snapshot.oxygenPercent = oxygenPercent
        snapshot.stepsToday = stepsToday
        snapshot.activeCaloriesToday = activeCaloriesToday
        snapshot.heartRateSource = heartRateSource
        snapshot.oxygenSource = oxygenSource
        snapshot.updatedAt = Date()
        write(snapshot)
    }

    private static var defaults: UserDefaults? {
        UserDefaults(suiteName: appGroup)
    }

    private static func write(_ snapshot: AltimeterWidgetSnapshot) {
        guard let data = try? JSONEncoder().encode(snapshot) else { return }
        defaults?.set(data, forKey: key)
        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }
}
