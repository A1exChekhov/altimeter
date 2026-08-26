import XCTest
@testable import Altimeter

final class AltitudeStabilizerTests: XCTestCase {
    private let start = Date(timeIntervalSince1970: 10_000)

    func testHoldsAltitudeDuringStationaryNoise() {
        let stabilizer = AltitudeStabilizer()
        var result = stabilizer.update(rawAltitude: 100, at: start)!
        for second in 1...60 {
            let noise = second.isMultiple(of: 2) ? 1.2 : -1.2
            result = stabilizer.update(
                rawAltitude: 100 + noise,
                at: start.addingTimeInterval(Double(second))
            )!
        }
        XCTAssertEqual(result, 100, accuracy: 0.001)
    }

    func testRejectsSingleLargeSpike() {
        let stabilizer = AltitudeStabilizer()
        var result = stabilizer.update(rawAltitude: 100, at: start)!
        for second in 1...20 {
            result = stabilizer.update(
                rawAltitude: second == 10 ? 112 : 100,
                at: start.addingTimeInterval(Double(second))
            )!
        }
        XCTAssertEqual(result, 100, accuracy: 0.001)
    }

    func testFollowsSustainedWalkingClimb() {
        let stabilizer = AltitudeStabilizer()
        stabilizer.update(rawAltitude: 100, at: start)
        var result = 100.0
        for second in 1...60 {
            result = stabilizer.update(
                rawAltitude: 100 + Double(second) * 0.18,
                at: start.addingTimeInterval(Double(second))
            )!
        }
        XCTAssertGreaterThan(result, 107)
        XCTAssertLessThanOrEqual(result, 111)
    }

    func testReactsQuicklyToAircraftOrElevatorClimb() {
        let stabilizer = AltitudeStabilizer()
        stabilizer.update(rawAltitude: 100, at: start)
        var result = 100.0
        for second in 1...10 {
            result = stabilizer.update(
                rawAltitude: 100 + Double(second) * 4,
                at: start.addingTimeInterval(Double(second))
            )!
        }
        XCTAssertGreaterThan(result, 132)
        XCTAssertLessThanOrEqual(result, 140)
    }
}
