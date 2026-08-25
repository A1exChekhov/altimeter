import Combine
import CoreLocation
import CoreMotion
import Foundation
import UserNotifications

@MainActor
final class AltimeterEngine: NSObject, ObservableObject {
    @Published private(set) var state = AltimeterState()
    @Published private(set) var savedTracks: [SavedTrack]

    private let locationManager = CLLocationManager()
    private let altimeter = CMAltimeter()
    private let fusion = FusionEngine()
    private let statistics = TrackStatistics()
    private let recorder = GPXRecorder()
    private let motionActivity = CMMotionActivityManager()
    private let trackStore: TrackStore
    private let placeResolver = PlaceResolver()

    private var ticker: Timer?
    private var pressureSamples: [(Date, Double)] = []
    private var lastPressureSampleAt = Date.distantPast
    private var lastPreciseFixAt = Date.distantPast
    private var lastLocation: CLLocation?
    private var currentTrackURL: URL?
    private var currentTrackID: UUID?
    private var lastAutosaveAt = Date.distantPast
    private var isStarted = false
    private var autoTrackEnabled = false
    private var autoMovementActive = false
    private var autoLastMovingAt = Date.distantPast
    private var autoCandidateStartedAt: Date?
    private var autoCandidateLastLocation: CLLocation?
    private var autoCandidateDistance = 0.0

    override init() {
        let trackStore = TrackStore()
        self.trackStore = trackStore
        self.savedTracks = trackStore.load()
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        locationManager.distanceFilter = kCLDistanceFilterNone
        locationManager.activityType = .fitness
        locationManager.pausesLocationUpdatesAutomatically = false
        state.hasBarometer = CMAltimeter.isRelativeAltitudeAvailable()
        state.authorization = locationManager.authorizationStatus
    }

    func start() {
        guard !isStarted else { return }
        isStarted = true
        startBarometer()
        if isLocationAuthorized { locationManager.startUpdatingLocation() }
        ticker = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
        tick()
    }

    func stop() {
        guard isStarted, !state.track.isRecording else { return }
        ticker?.invalidate()
        ticker = nil
        altimeter.stopRelativeAltitudeUpdates()
        locationManager.stopUpdatingLocation()
        isStarted = false
    }

    func requestLocationAccess() {
        locationManager.requestWhenInUseAuthorization()
    }

    func setAutoTrackEnabled(_ enabled: Bool) {
        guard autoTrackEnabled != enabled else { return }
        autoTrackEnabled = enabled
        resetAutoCandidate()

        if enabled {
            if locationManager.authorizationStatus == .notDetermined {
                locationManager.requestWhenInUseAuthorization()
            } else if locationManager.authorizationStatus == .authorizedWhenInUse {
                locationManager.requestAlwaysAuthorization()
            }
            locationManager.desiredAccuracy = kCLLocationAccuracyBest
            locationManager.distanceFilter = state.track.isRecording ? kCLDistanceFilterNone : 10
            locationManager.allowsBackgroundLocationUpdates = true
            locationManager.showsBackgroundLocationIndicator = true
            if isLocationAuthorized { locationManager.startUpdatingLocation() }
            startMotionMonitoring()
            Task {
                _ = try? await UNUserNotificationCenter.current()
                    .requestAuthorization(options: [.alert, .sound])
            }
        } else {
            motionActivity.stopActivityUpdates()
            autoMovementActive = false
            if !state.track.isRecording {
                locationManager.allowsBackgroundLocationUpdates = false
                locationManager.showsBackgroundLocationIndicator = false
                locationManager.distanceFilter = kCLDistanceFilterNone
            }
        }
    }

    func setTrackSamplingMode(_ mode: TrackSamplingMode) {
        recorder.setSamplingMode(mode)
    }

    func applySettings(mode: CalibrationMode, manualOffset: Double?, qnhHPA: Double) {
        fusion.apply(mode: mode, manualOffset: manualOffset, qnhHPA: qnhHPA)
        tick()
    }

    func calibrateManually(meters: Double) -> Double? {
        let offset = fusion.calibrateManually(knownAltitude: meters)
        tick()
        return offset
    }

    func resetStatistics() {
        statistics.reset()
        tick()
    }

    func startRecording(automatic: Bool = false) {
        guard !state.track.isRecording else { return }
        if locationManager.authorizationStatus == .authorizedWhenInUse {
            locationManager.requestAlwaysAuthorization()
        } else if locationManager.authorizationStatus == .notDetermined {
            locationManager.requestWhenInUseAuthorization()
        }
        recorder.begin()
        state.trackPoints = []
        currentTrackURL = trackStore.makeTrackURL()
        currentTrackID = UUID()
        lastAutosaveAt = .distantPast
        state.track = TrackState(
            isRecording: true,
            isPaused: false,
            automatic: automatic,
            startedAt: Date()
        )
        autoLastMovingAt = Date()
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.distanceFilter = kCLDistanceFilterNone
        locationManager.pausesLocationUpdatesAutomatically = false
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.showsBackgroundLocationIndicator = true
        locationManager.startUpdatingLocation()
    }

    func stopRecording() {
        guard state.track.isRecording else { return }
        saveTrack(complete: true)
        state.track.isRecording = false
        state.track.isPaused = false
        state.track.automatic = false
        if !autoTrackEnabled {
            locationManager.allowsBackgroundLocationUpdates = false
            locationManager.showsBackgroundLocationIndicator = false
            locationManager.desiredAccuracy = kCLLocationAccuracyBest
            locationManager.distanceFilter = kCLDistanceFilterNone
        } else {
            locationManager.desiredAccuracy = kCLLocationAccuracyBest
            locationManager.distanceFilter = 10
        }
    }

    func fileURL(for track: SavedTrack) -> URL {
        trackStore.fileURL(for: track)
    }

    func deleteTrack(_ track: SavedTrack) {
        guard !state.track.isRecording || track.id != currentTrackID else { return }
        do {
            try trackStore.delete(track)
            savedTracks = trackStore.tracks
            if state.track.lastSavedURL == trackStore.fileURL(for: track) {
                state.track.lastSavedURL = nil
            }
        } catch {
            // The row remains in the archive when the file could not be deleted.
        }
    }

    private var isLocationAuthorized: Bool {
        locationManager.authorizationStatus == .authorizedWhenInUse ||
        locationManager.authorizationStatus == .authorizedAlways
    }

    private func startBarometer() {
        guard CMAltimeter.isRelativeAltitudeAvailable() else { return }
        altimeter.startRelativeAltitudeUpdates(to: .main) { [weak self] data, _ in
            guard let pressure = data?.pressure.doubleValue else { return }
            Task { @MainActor in
                self?.fusion.onPressure(pressure * 10.0) // kPa -> hPa
                self?.tick()
            }
        }
    }

    private func tick(at date: Date = Date()) {
        let altitude = fusion.displayedAltitude
        if let altitude { statistics.add(date: date, altitude: altitude) }
        sampleSeaLevelPressureIfNeeded(at: date, altitude: altitude)

        state.altitude = altitude
        state.altitudeAccuracy = fusion.displayedAccuracy
        state.pressureHPA = fusion.pressureHPA
        state.isCalibrating = fusion.isCalibrating
        state.hasFix = date.timeIntervalSince(lastPreciseFixAt) < 6
        state.verticalSpeedMetersPerMinute = statistics.verticalSpeedMetersPerMinute
        state.minAltitude = statistics.minAltitude
        state.maxAltitude = statistics.maxAltitude
        state.ascentMeters = statistics.ascent
        state.descentMeters = statistics.descent
        state.history = statistics.history
        state.pressureTrendHPAperHour = pressureTrend()
        state.timestamp = date

    }

    private func handle(_ location: CLLocation, resolvePlace: Bool = true) {
        guard location.horizontalAccuracy >= 0 else { return }
        lastLocation = location
        if location.horizontalAccuracy <= 50 { lastPreciseFixAt = Date() }
        let verticalAccuracy = location.verticalAccuracy >= 0 ? location.verticalAccuracy : nil
        if verticalAccuracy != nil {
            fusion.onGPSAltitude(location.altitude, verticalAccuracy: verticalAccuracy)
        }
        state.coordinate = location.coordinate
        state.horizontalAccuracy = location.horizontalAccuracy
        state.verticalAccuracy = verticalAccuracy
        tick(at: location.timestamp)

        if resolvePlace {
            Task { @MainActor [weak self] in
                guard let self else { return }
                if let name = await placeResolver.resolve(location) { state.placeName = name }
            }
        }

        evaluateAutoTrack(with: location)

        if state.track.isRecording && !state.track.isPaused {
            if recorder.offer(location: location, elevation: state.altitude, date: location.timestamp) {
                state.track.pointCount = recorder.points.count
                state.track.distanceMeters = recorder.distanceMeters
                state.track.ascentMeters = recorder.ascentMeters
                state.track.descentMeters = recorder.descentMeters
                state.track.movingTime = recorder.movingTime
                state.track.stoppedTime = recorder.stoppedTime
                state.trackPoints = recorder.mapPoints
            }
            if Date().timeIntervalSince(lastAutosaveAt) >= 30, !recorder.points.isEmpty {
                lastAutosaveAt = Date()
                saveTrack(complete: false)
            }
        }
    }

    private func startMotionMonitoring() {
        guard CMMotionActivityManager.isActivityAvailable() else { return }
        motionActivity.startActivityUpdates(to: .main) { [weak self] activity in
            guard let activity else { return }
            Task { @MainActor [weak self] in
                guard let self, autoTrackEnabled else { return }
                if activity.automotive {
                    autoMovementActive = false
                    resetAutoCandidate()
                    return
                }
                let moving = activity.walking || activity.running || activity.cycling
                autoMovementActive = moving
                if moving {
                    autoLastMovingAt = Date()
                    if autoCandidateStartedAt == nil {
                        autoCandidateStartedAt = Date()
                        autoCandidateLastLocation = lastLocation
                        autoCandidateDistance = 0
                    }
                }
            }
        }
    }

    private func evaluateAutoTrack(with location: CLLocation) {
        guard autoTrackEnabled else { return }
        let now = location.timestamp

        if !autoMovementActive, now.timeIntervalSince(autoLastMovingAt) > 45 {
            resetAutoCandidate()
            return
        }
        guard autoMovementActive, let startedAt = autoCandidateStartedAt else { return }

        if let previous = autoCandidateLastLocation {
            let segment = location.distance(from: previous)
            if (1...80).contains(segment) { autoCandidateDistance += segment }
        }
        autoCandidateLastLocation = location
        let elapsed = now.timeIntervalSince(startedAt)

        guard !state.track.isRecording, elapsed >= 90, autoCandidateDistance >= 120 else { return }
        let speed = autoCandidateDistance / max(elapsed, 1)
        guard (0.45...4.2).contains(speed) else {
            resetAutoCandidate()
            return
        }
        startRecording(automatic: true)
        resetAutoCandidate()
        notifyAutoTrack(title: "Трек начат автоматически", body: "Подтверждены 90 секунд и 120 м движения.")
    }

    private func resetAutoCandidate() {
        autoCandidateStartedAt = nil
        autoCandidateLastLocation = nil
        autoCandidateDistance = 0
    }

    private func notifyAutoTrack(title: String, body: String) {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        content.threadIdentifier = "auto-track"
        let request = UNNotificationRequest(
            identifier: "auto-track-\(UUID().uuidString)",
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    private func saveTrack(complete: Bool) {
        guard !recorder.points.isEmpty,
              let currentTrackURL,
              let currentTrackID else { return }
        do {
            try trackStore.save(
                recorder: recorder,
                to: currentTrackURL,
                id: currentTrackID,
                complete: complete
            )
            savedTracks = trackStore.tracks
            state.track.lastSavedURL = currentTrackURL
        } catch {
            // The UI keeps recording; a later location update retries autosave.
        }
    }

    private func sampleSeaLevelPressureIfNeeded(at date: Date, altitude: Double?) {
        guard date.timeIntervalSince(lastPressureSampleAt) >= 60,
              let pressure = fusion.pressureHPA,
              let altitude else { return }
        lastPressureSampleAt = date
        let denominator = pow(1.0 - altitude / 44_330.0, 5.255)
        guard denominator > 0 else { return }
        pressureSamples.append((date, pressure / denominator))
        pressureSamples.removeAll { date.timeIntervalSince($0.0) > 4 * 3_600 }
    }

    private func pressureTrend() -> Double? {
        guard pressureSamples.count >= 4 else { return nil }
        let first = Array(pressureSamples.prefix(3))
        let last = Array(pressureSamples.suffix(3))
        let firstTime = first.map { $0.0.timeIntervalSince1970 }.reduce(0, +) / Double(first.count)
        let lastTime = last.map { $0.0.timeIntervalSince1970 }.reduce(0, +) / Double(last.count)
        let hours = (lastTime - firstTime) / 3_600
        guard hours >= 0.75 else { return nil }
        let firstPressure = first.map { $0.1 }.reduce(0, +) / Double(first.count)
        let lastPressure = last.map { $0.1 }.reduce(0, +) / Double(last.count)
        return (lastPressure - firstPressure) / hours
    }
}

extension AltimeterEngine: CLLocationManagerDelegate {
    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor [weak self] in
            guard let self else { return }
            state.authorization = manager.authorizationStatus
            if autoTrackEnabled && manager.authorizationStatus == .authorizedWhenInUse {
                manager.requestAlwaysAuthorization()
            }
            if isLocationAuthorized { manager.startUpdatingLocation() }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard !locations.isEmpty else { return }
        Task { @MainActor [weak self] in
            guard let self else { return }
            for (index, location) in locations.enumerated() {
                handle(location, resolvePlace: index == locations.indices.last)
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Core Location reports transient errors while acquiring a fix; state.hasFix reflects them.
    }
}
