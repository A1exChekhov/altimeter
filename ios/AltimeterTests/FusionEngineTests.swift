import XCTest
@testable import Altimeter

final class FusionEngineTests: XCTestCase {
    func testAutomaticModeAnchorsBarometerToGPS() {
        let engine = FusionEngine()
        engine.onPressure(898.75)
        engine.onGPSAltitude(1_000, verticalAccuracy: 3)

        XCTAssertEqual(engine.displayedAltitude ?? 0, 1_000, accuracy: 0.01)
        XCTAssertNotNil(engine.displayedAccuracy)
        XCTAssertFalse(engine.isCalibrating)
    }

    func testManualCalibrationUsesKnownAltitude() {
        let engine = FusionEngine()
        engine.onPressure(900)
        let offset = engine.calibrateManually(knownAltitude: 1_234)
        engine.apply(mode: .manual, manualOffset: offset, qnhHPA: 1013.25)

        XCTAssertEqual(engine.displayedAltitude ?? 0, 1_234, accuracy: 0.01)
        XCTAssertNil(engine.displayedAccuracy)
    }

    func testQNHModeUsesProvidedSeaLevelPressure() {
        let engine = FusionEngine()
        engine.onPressure(950)
        engine.apply(mode: .qnh, manualOffset: nil, qnhHPA: 1_020)

        XCTAssertEqual(
            engine.displayedAltitude ?? 0,
            FusionEngine.standardAltitude(pressureHPA: 950, seaLevelHPA: 1_020),
            accuracy: 0.001
        )
    }

    func testRejectsVeryInaccurateGPSForFusion() {
        let engine = FusionEngine()
        engine.onPressure(900)
        engine.onGPSAltitude(2_000, verticalAccuracy: 90)

        XCTAssertNil(engine.displayedAltitude)
        XCTAssertTrue(engine.isCalibrating)
    }
}

