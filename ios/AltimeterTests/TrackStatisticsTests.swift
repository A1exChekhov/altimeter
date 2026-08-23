import XCTest
@testable import Altimeter

final class TrackStatisticsTests: XCTestCase {
    func testStatisticsAndVerticalSpeed() {
        let statistics = TrackStatistics()
        let start = Date(timeIntervalSince1970: 1_000)
        for second in 0..<10 {
            statistics.add(
                date: start.addingTimeInterval(Double(second)),
                altitude: 100 + Double(second)
            )
        }

        XCTAssertEqual(statistics.minAltitude, 100)
        XCTAssertEqual(statistics.maxAltitude, 109)
        XCTAssertEqual(statistics.ascent, 8, accuracy: 0.001)
        XCTAssertEqual(statistics.verticalSpeedMetersPerMinute ?? 0, 60, accuracy: 0.001)
        XCTAssertEqual(statistics.history.count, 5)
    }

    func testNoiseBelowHysteresisDoesNotAddAscent() {
        let statistics = TrackStatistics()
        let start = Date(timeIntervalSince1970: 2_000)
        [100.0, 100.4, 99.8, 100.7, 99.9].enumerated().forEach { index, altitude in
            statistics.add(date: start.addingTimeInterval(Double(index) * 2), altitude: altitude)
        }
        XCTAssertEqual(statistics.ascent, 0)
        XCTAssertEqual(statistics.descent, 0)
    }
}

