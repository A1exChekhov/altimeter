import CoreLocation
import XCTest
@testable import Altimeter

final class KailashOfflineMapTests: XCTestCase {
    func testBundledMapResourcesAreAvailable() throws {
        let resources = try XCTUnwrap(KailashOfflineMap.resources)
        let baseSize = try resources.mapURL.resourceValues(forKeys: [.fileSizeKey]).fileSize
        let terrainSize = try resources.terrainURL.resourceValues(forKeys: [.fileSizeKey]).fileSize
        XCTAssertEqual((baseSize ?? 0) + (terrainSize ?? 0), KailashOfflineMap.embeddedSizeBytes)
    }

    func testKailashBoundsContainRegionCenter() {
        XCTAssertTrue(KailashOfflineMap.contains(KailashOfflineMap.center))
        XCTAssertFalse(
            KailashOfflineMap.contains(
                CLLocationCoordinate2D(latitude: 27.9881, longitude: 86.9250)
            )
        )
    }

    func testAutomaticModeUsesOfflineMapOnlyInsideRegion() {
        XCTAssertTrue(MapSourceMode.automatic.usesKailash(at: KailashOfflineMap.center))
        XCTAssertFalse(MapSourceMode.automatic.usesKailash(at: nil))
        XCTAssertFalse(MapSourceMode.online.usesKailash(at: KailashOfflineMap.center))
        XCTAssertTrue(MapSourceMode.kailash.usesKailash(at: nil))
    }

    func testPMTilesV3HeaderValidation() throws {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("kailash-test-\(UUID().uuidString).pmtiles")
        defer { try? FileManager.default.removeItem(at: url) }

        var header = Data(repeating: 0, count: 127)
        header.replaceSubrange(0..<7, with: Data("PMTiles".utf8))
        header[7] = 3
        header[99] = 1
        try header.write(to: url)

        XCTAssertNoThrow(try KailashOfflineMap.validatePMTiles(url, expectedTileType: 1))
        XCTAssertThrowsError(try KailashOfflineMap.validatePMTiles(url, expectedTileType: 4))
    }
}
