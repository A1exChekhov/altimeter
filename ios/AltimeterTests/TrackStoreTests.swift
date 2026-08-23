import CoreLocation
import XCTest
@testable import Altimeter

final class TrackStoreTests: XCTestCase {
    func testArchiveSurvivesReloadAndDeletesSelectedFile() throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("AltimeterTrackStoreTests-\(UUID().uuidString)", isDirectory: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }

        let store = TrackStore(directory: directory)
        let recorder = GPXRecorder()
        let start = Date(timeIntervalSince1970: 1_700_000_000)
        recorder.begin(at: start)
        let location = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 43.65, longitude: 40.31),
            altitude: 1_200,
            horizontalAccuracy: 5,
            verticalAccuracy: 4,
            timestamp: start
        )
        XCTAssertTrue(recorder.offer(location: location, elevation: 1_198, date: start))

        let id = UUID()
        let url = store.makeTrackURL(at: start)
        let saved = try store.save(recorder: recorder, to: url, id: id, complete: true, now: start)
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        XCTAssertEqual(saved.pointCount, 1)

        let reloaded = TrackStore(directory: directory)
        XCTAssertEqual(reloaded.load().map(\.id), [id])
        XCTAssertEqual(reloaded.tracks.first?.fileName, url.lastPathComponent)

        try reloaded.delete(saved)
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
        XCTAssertTrue(reloaded.tracks.isEmpty)
    }
}
