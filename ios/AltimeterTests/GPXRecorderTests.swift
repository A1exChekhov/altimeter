import CoreLocation
import XCTest
@testable import Altimeter

final class GPXRecorderTests: XCTestCase {
    func testProducesValidGPXWithElevation() throws {
        let recorder = GPXRecorder()
        let start = Date(timeIntervalSince1970: 1_700_000_000)
        recorder.begin(at: start)
        let first = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 43.65581, longitude: 40.31226),
            altitude: 1_200,
            horizontalAccuracy: 5,
            verticalAccuracy: 5,
            timestamp: start
        )
        XCTAssertTrue(recorder.offer(location: first, elevation: 1_198.4, date: start))

        let xml = String(decoding: recorder.data(), as: UTF8.self)
        XCTAssertTrue(xml.contains("<gpx version=\"1.1\""))
        XCTAssertTrue(xml.contains("lat=\"43.6558100\""))
        XCTAssertTrue(xml.contains("lon=\"40.3122600\""))
        XCTAssertTrue(xml.contains("<ele>1198.4</ele>"))
        XCTAssertTrue(xml.contains("</gpx>"))
    }

    func testRejectsInaccuratePoint() {
        let recorder = GPXRecorder()
        recorder.begin()
        let location = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 0, longitude: 0),
            altitude: 0,
            horizontalAccuracy: 100,
            verticalAccuracy: 10,
            timestamp: Date()
        )
        XCTAssertFalse(recorder.offer(location: location, elevation: nil))
        XCTAssertEqual(recorder.points.count, 0)
    }
}

