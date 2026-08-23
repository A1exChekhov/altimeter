import Combine
import CoreLocation
import CoreMotion
import Foundation

@MainActor
final class AltimeterEngine: NSObject, ObservableObject {
    @Published private(set) var state = AltimeterState()
    @Published private(set) var savedTracks: [SavedTrack]

    private let locationManager = CLLocationManager()
    private let altimeter = CMAltimeter()
    private let fusion = FusionEngine()
    private let statistics = TrackStatistics()
    private let recorder = GPXRecorder()
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

    func startRecording() {
        guard !state.track.isRecording else { return }
        if locationManager.authorizationStatus == .authorizedWhenInUse {
            locationManager.requestAlwaysAuthorization()
        } else if locationManager.authorizationStatus == .notDetermined {
            locationManager.requestWhenInUseAuthorization()
        }
        recorder.begin()
        currentTrackURL = trackStore.makeTrackURL()
        currentTrackID = UUID()
        lastAutosaveAt = .distantPast
        state.track = TrackState(isRecording: true, startedAt: Date())
        locationManager.allowsBackgroundLocationUpdates = true
        locationManager.showsBackgroundLocationIndicator = true
        locationManager.startUpdatingLocation()
    }

    func stopRecording() {
        guard state.track.isRecording else { return }
        saveTrack(complete: true)
        state.track.isRecording = false
        locationManager.allowsBackgroundLocationUpdates = false
        locationManager.showsBackgroundLocationIndicator = false
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

    private func handle(_ location: CLLocation) {
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

        Task { @MainActor [weak self] in
            guard let self else { return }
            if let name = await placeResolver.resolve(location) { state.placeName = name }
        }

        if state.track.isRecording {
            if recorder.offer(location: location, elevation: state.altitude, date: location.timestamp) {
                state.track.pointCount = recorder.points.count
                state.track.distanceMeters = recorder.distanceMeters
                state.track.ascentMeters = recorder.ascentMeters
            }
            if Date().timeIntervalSince(lastAutosaveAt) >= 60, !recorder.points.isEmpty {
                lastAutosaveAt = Date()
                saveTrack(complete: false)
            }
        }
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
            if isLocationAuthorized { manager.startUpdatingLocation() }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        Task { @MainActor [weak self] in self?.handle(location) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        // Core Location reports transient errors while acquiring a fix; state.hasFix reflects them.
    }
}
