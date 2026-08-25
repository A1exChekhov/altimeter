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

    func testOneSecondDefaultKeepsEveryTurn() {
        let recorder = GPXRecorder()
        let start = Date(timeIntervalSince1970: 1_800_000_000)
        recorder.begin(at: start)
        var locations: [CLLocation] = []
        for index in 0...10 {
            locations.append(location(55 + Double(index) * 0.00001, 37, start, index))
        }
        for index in 0..<10 {
            locations.append(location(55.00010, 37 + Double(index + 1) * 0.00002, start, index + 11))
        }
        for index in 0..<10 {
            locations.append(location(55.00010 - Double(index + 1) * 0.00001, 37.00020, start, index + 21))
        }

        for location in locations {
            XCTAssertTrue(recorder.offer(location: location, elevation: 300, date: location.timestamp))
        }
        XCTAssertEqual(recorder.points.count, locations.count)
        XCTAssertEqual(recorder.mapPoints.count, locations.count)
        XCTAssertTrue(recorder.mapPoints[0].startsNewSegment)
        XCTAssertFalse(recorder.mapPoints[1].startsNewSegment)
        XCTAssertEqual(recorder.mapPoints[20].longitude, 37.00020, accuracy: 0.0000001)
    }

    func testFourSecondModeStillKeepsImmediateTurnAndGPSGapIsSegmented() {
        let recorder = GPXRecorder()
        recorder.setSamplingMode(.everyFourSeconds)
        let start = Date(timeIntervalSince1970: 1_800_000_100)
        recorder.begin(at: start)

        XCTAssertTrue(recorder.offer(location: location(55, 37, start, 0), elevation: 300, date: start))
        XCTAssertFalse(recorder.offer(location: location(55.00001, 37, start, 1), elevation: 300, date: start.addingTimeInterval(1)))
        XCTAssertTrue(recorder.offer(location: location(55.00004, 37, start, 4), elevation: 300, date: start.addingTimeInterval(4)))
        XCTAssertTrue(recorder.offer(location: location(55.00004, 37.00002, start, 5), elevation: 300, date: start.addingTimeInterval(5)))
        XCTAssertTrue(recorder.offer(location: location(55.001, 37.001, start, 45), elevation: 300, date: start.addingTimeInterval(45)))

        let xml = String(decoding: recorder.data(), as: UTF8.self)
        XCTAssertEqual(xml.components(separatedBy: "<trkseg>").count - 1, 2)
        XCTAssertEqual(xml.components(separatedBy: "<trkpt ").count - 1, 4)
    }

    private func location(
        _ latitude: Double,
        _ longitude: Double,
        _ start: Date,
        _ second: Int
    ) -> CLLocation {
        CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
            altitude: 300,
            horizontalAccuracy: 3,
            verticalAccuracy: 3,
            timestamp: start.addingTimeInterval(Double(second))
        )
    }
}

