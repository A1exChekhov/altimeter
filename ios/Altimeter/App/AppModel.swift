import Combine
import Foundation
import UIKit

@MainActor
final class AppModel: ObservableObject {
    let engine = AltimeterEngine()
    let health = HealthService()
    let notifications = NotificationService()

    @Published var unit: AltitudeUnit {
        didSet {
            defaults.set(unit.rawValue, forKey: Keys.unit)
            updateWidget(force: true)
        }
    }
    @Published var calibrationMode: CalibrationMode {
        didSet {
            defaults.set(calibrationMode.rawValue, forKey: Keys.calibrationMode)
            applyCalibration()
        }
    }
    @Published var qnhHPA: Double {
        didSet {
            defaults.set(qnhHPA, forKey: Keys.qnh)
            applyCalibration()
        }
    }
    @Published var manualOffset: Double? {
        didSet {
            if let manualOffset { defaults.set(manualOffset, forKey: Keys.manualOffset) }
            applyCalibration()
        }
    }
    @Published var useTopographicMap: Bool {
        didSet { defaults.set(useTopographicMap, forKey: Keys.topographicMap) }
    }
    @Published var keepScreenAwake: Bool {
        didSet {
            defaults.set(keepScreenAwake, forKey: Keys.keepScreenAwake)
            UIApplication.shared.isIdleTimerDisabled = keepScreenAwake
        }
    }

    private let defaults = UserDefaults.standard
    private var subscriptions: Set<AnyCancellable> = []
    private var lastWidgetUpdate = Date.distantPast
    init() {
        let store = UserDefaults.standard
        unit = AltitudeUnit(rawValue: store.string(forKey: Keys.unit) ?? "") ?? .meters
        calibrationMode = CalibrationMode(rawValue: store.string(forKey: Keys.calibrationMode) ?? "") ?? .automatic
        let storedQNH = store.double(forKey: Keys.qnh)
        qnhHPA = storedQNH == 0 ? 1013.25 : storedQNH
        manualOffset = store.object(forKey: Keys.manualOffset) as? Double
        useTopographicMap = store.object(forKey: Keys.topographicMap) as? Bool ?? true
        keepScreenAwake = store.object(forKey: Keys.keepScreenAwake) as? Bool ?? true

        engine.objectWillChange
            .sink { [weak self] _ in
                self?.objectWillChange.send()
                Task { @MainActor [weak self] in
                    await Task.yield()
                    self?.updateWidget()
                }
            }
            .store(in: &subscriptions)
        health.objectWillChange
            .sink { [weak self] _ in
                self?.objectWillChange.send()
                Task { @MainActor [weak self] in
                    await Task.yield()
                    self?.updateHealthWidget()
                }
            }
            .store(in: &subscriptions)
        notifications.objectWillChange
            .sink { [weak self] _ in self?.objectWillChange.send() }
            .store(in: &subscriptions)

        applyCalibration()
        UIApplication.shared.isIdleTimerDisabled = keepScreenAwake
    }

    var state: AltimeterState { engine.state }
    var vitals: VitalSample { health.vitals }

    var advices: [Advice] {
        Advisor().evaluate(
            AdvisorInput(
                now: state.timestamp,
                altitude: state.altitude,
                verticalSpeedMetersPerMinute: state.verticalSpeedMetersPerMinute,
                hasFix: state.hasFix,
                locationAuthorized: state.authorization == .authorizedAlways || state.authorization == .authorizedWhenInUse,
                oxygenPercent: vitals.oxygenPercent,
                oxygenDate: vitals.oxygenDate,
                heartRate: vitals.heartRateBPM,
                heartRateDate: vitals.heartRateDate,
                pressureTrendHPAperHour: state.pressureTrendHPAperHour
            )
        )
    }

    func start() {
        engine.start()
        updateWidget(force: true)
        updateHealthWidget()
        if health.hasRequestedAccess { Task { await health.refresh() } }
    }

    func calibrateManually(displayedValue: Double) {
        let meters = unit.meters(from: displayedValue)
        guard (-500...10_000).contains(meters) else { return }
        manualOffset = engine.calibrateManually(meters: meters)
        calibrationMode = .manual
    }

    func setQNH(_ value: Double) {
        guard (850...1_100).contains(value) else { return }
        qnhHPA = value
        calibrationMode = .qnh
    }

    private func applyCalibration() {
        engine.applySettings(mode: calibrationMode, manualOffset: manualOffset, qnhHPA: qnhHPA)
    }

    private func updateWidget(force: Bool = false) {
        let now = Date()
        guard force || now.timeIntervalSince(lastWidgetUpdate) >= 15 else { return }
        lastWidgetUpdate = now
        let state = engine.state
        WidgetSnapshotStore.updateAltitudeAndTrack(
            altitudeMeters: state.altitude,
            usesFeet: unit == .feet,
            trackIsRecording: state.track.isRecording,
            trackDistanceMeters: state.track.distanceMeters,
            trackPointCount: state.track.pointCount
        )
    }

    private func updateHealthWidget() {
        let sample = health.vitals
        WidgetSnapshotStore.updateVitals(
            heartRateBPM: sample.heartRateBPM,
            oxygenPercent: sample.oxygenPercent,
            heartRateSource: sample.heartRateSource,
            oxygenSource: sample.oxygenSource
        )
    }

    private enum Keys {
        static let unit = "altitudeUnit"
        static let calibrationMode = "calibrationMode"
        static let qnh = "qnhHPA"
        static let manualOffset = "manualOffset"
        static let topographicMap = "useTopographicMap"
        static let keepScreenAwake = "keepScreenAwake"
    }
}
