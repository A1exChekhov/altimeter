import Foundation
import CoreLocation
import CoreMotion

enum AltitudeUnit: String, CaseIterable, Identifiable {
    case meters
    case feet

    var id: Self { self }
    var symbol: String { self == .meters ? "м" : "фт" }
    var speedSymbol: String { self == .meters ? "м/мин" : "фт/мин" }

    func value(fromMeters meters: Double) -> Double {
        self == .meters ? meters : meters * 3.280_839_9
    }

    func meters(from value: Double) -> Double {
        self == .meters ? value : value / 3.280_839_9
    }
}

enum CalibrationMode: String, CaseIterable, Identifiable {
    case automatic
    case manual
    case qnh

    var id: Self { self }
    var title: String {
        switch self {
        case .automatic: "Авто"
        case .manual: "Вручную"
        case .qnh: "QNH"
        }
    }
}

struct ChartPoint: Identifiable, Equatable {
    let id = UUID()
    let date: Date
    let altitude: Double
}

struct VitalPoint: Identifiable, Equatable {
    let id = UUID()
    let date: Date
    let value: Double
}

enum AdviceSeverity: Int, Comparable {
    case info
    case caution
    case warning

    static func < (lhs: Self, rhs: Self) -> Bool { lhs.rawValue < rhs.rawValue }
}

enum AdviceKind: String {
    case pressureFallingFast
    case pressureFalling
    case pressureRising
    case altitudeAcclimatize
    case altitudeHigh
    case altitudeVeryHigh
    case fastAscent
    case hydration
    case oxygenLow
    case oxygenVeryLow
    case heartRateHigh
    case gpsWeak
}

struct Advice: Identifiable, Equatable {
    var id: AdviceKind { kind }
    let kind: AdviceKind
    let severity: AdviceSeverity
    let value: String?
}

struct VitalSample: Equatable {
    var heartRateBPM: Double?
    var heartRateDate: Date?
    var heartRateSource: String?
    var heartRateSourceBundle: String?
    var oxygenPercent: Double?
    var oxygenDate: Date?
    var oxygenSource: String?
    var oxygenSourceBundle: String?
    var stepsToday: Double?
    var stepsDate: Date?
    var activeCaloriesToday: Double?
    var activeCaloriesDate: Date?
    var heartRateSeries: [VitalPoint] = []
    var oxygenSeries: [VitalPoint] = []
    var stepsSeries: [VitalPoint] = []
}

struct TrackState: Equatable {
    var isRecording = false
    var isPaused = false
    var automatic = false
    var startedAt: Date?
    var pointCount = 0
    var distanceMeters = 0.0
    var ascentMeters = 0.0
    var descentMeters = 0.0
    var movingTime: TimeInterval = 0
    var stoppedTime: TimeInterval = 0
    var lastSavedURL: URL?
}

struct SavedTrack: Identifiable, Codable, Equatable {
    let id: UUID
    var fileName: String
    var startedAt: Date
    var updatedAt: Date
    var duration: TimeInterval
    var pointCount: Int
    var distanceMeters: Double
    var ascentMeters: Double
    var isComplete: Bool
}

struct AltimeterState {
    var hasBarometer = CMAltimeter.isRelativeAltitudeAvailable()
    var altitude: Double?
    var altitudeAccuracy: Double?
    var pressureHPA: Double?
    var isCalibrating = false
    var coordinate: CLLocationCoordinate2D?
    var horizontalAccuracy: Double?
    var verticalAccuracy: Double?
    var hasFix = false
    var authorization: CLAuthorizationStatus = .notDetermined
    var verticalSpeedMetersPerMinute: Double?
    var minAltitude: Double?
    var maxAltitude: Double?
    var ascentMeters = 0.0
    var descentMeters = 0.0
    var history: [ChartPoint] = []
    var pressureTrendHPAperHour: Double?
    var placeName: String?
    var track = TrackState()
    var timestamp = Date()
}
