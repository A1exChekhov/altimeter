import Foundation
#if canImport(WidgetKit)
import WidgetKit
#endif

struct AltimeterWidgetSnapshot: Codable, Equatable {
    var altitudeMeters: Double?
    var usesFeet = false
    var trackIsRecording = false
    var trackDistanceMeters = 0.0
    var trackPointCount = 0
    var heartRateBPM: Double?
    var oxygenPercent: Double?
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
        usesFeet: Bool,
        trackIsRecording: Bool,
        trackDistanceMeters: Double,
        trackPointCount: Int
    ) {
        var snapshot = read()
        snapshot.altitudeMeters = altitudeMeters
        snapshot.usesFeet = usesFeet
        snapshot.trackIsRecording = trackIsRecording
        snapshot.trackDistanceMeters = trackDistanceMeters
        snapshot.trackPointCount = trackPointCount
        snapshot.updatedAt = Date()
        write(snapshot)
    }

    static func updateVitals(
        heartRateBPM: Double?,
        oxygenPercent: Double?,
        heartRateSource: String?,
        oxygenSource: String?
    ) {
        var snapshot = read()
        snapshot.heartRateBPM = heartRateBPM
        snapshot.oxygenPercent = oxygenPercent
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
        WidgetCenter.shared.reloadTimelines(ofKind: "AltimeterStatusWidget")
        #endif
    }
}
