import XCTest
@testable import Altimeter

final class AdvisorTests: XCTestCase {
    func testOnlyNonMedicalFieldInformationIsProduced() {
        let now = Date()
        let result = Advisor().evaluate(
            AdvisorInput(
                now: now,
                altitude: 4_200,
                verticalSpeedMetersPerMinute: 15,
                hasFix: true,
                locationAuthorized: true,
                oxygenPercent: 86,
                oxygenDate: now.addingTimeInterval(-60),
                heartRate: 130,
                heartRateDate: now.addingTimeInterval(-60),
                pressureTrendHPAperHour: -2
            )
        )

        XCTAssertEqual(result.count, 2)
        XCTAssertTrue(result.contains { $0.kind == .pressureFallingFast })
        XCTAssertTrue(result.contains { $0.kind == .hydration })
        XCTAssertTrue(result.allSatisfy { $0.isFieldInformation })
    }

    func testStaleOxygenIsIgnored() {
        let now = Date()
        let result = Advisor().evaluate(
            AdvisorInput(
                now: now,
                altitude: 100,
                verticalSpeedMetersPerMinute: nil,
                hasFix: true,
                locationAuthorized: true,
                oxygenPercent: 70,
                oxygenDate: now.addingTimeInterval(-60 * 60),
                heartRate: nil,
                heartRateDate: nil,
                pressureTrendHPAperHour: nil
            )
        )
        XCTAssertFalse(result.contains { $0.kind == .oxygenVeryLow || $0.kind == .oxygenLow })
    }
}
