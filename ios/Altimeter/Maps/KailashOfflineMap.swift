import CoreLocation
import Foundation

enum MapSourceMode: String, CaseIterable, Identifiable {
    case automatic
    case online
    case kailash

    var id: String { rawValue }

    var title: String {
        switch self {
        case .automatic: L10n.string("map.source.auto")
        case .online: L10n.string("map.source.online")
        case .kailash: L10n.string("map.source.kailash")
        }
    }

    func usesKailash(at coordinate: CLLocationCoordinate2D?) -> Bool {
        switch self {
        case .automatic:
            coordinate.map(KailashOfflineMap.contains) ?? false
        case .online:
            false
        case .kailash:
            true
        }
    }
}

struct KailashMapResources {
    let mapURL: URL
    let terrainURL: URL
    let styleURL: URL
}

enum KailashOfflineMap {
    static let center = CLLocationCoordinate2D(latitude: 31.05, longitude: 81.30)
    static let minimumLatitude = 30.85
    static let maximumLatitude = 31.25
    static let minimumLongitude = 81.05
    static let maximumLongitude = 81.55
    static let embeddedSizeBytes = 12_648_243

    static let resources: KailashMapResources? = try? loadBundledResources()

    static func contains(_ coordinate: CLLocationCoordinate2D) -> Bool {
        (minimumLatitude...maximumLatitude).contains(coordinate.latitude)
            && (minimumLongitude...maximumLongitude).contains(coordinate.longitude)
    }

    static func validatePMTiles(_ url: URL, expectedTileType: UInt8) throws {
        let data = try Data(contentsOf: url, options: [.mappedIfSafe])
        guard data.count >= 127 else { throw ResourceError.invalidArchive(url.lastPathComponent) }
        guard String(decoding: data.prefix(7), as: UTF8.self) == "PMTiles",
              data[7] == 3,
              data[99] == expectedTileType else {
            throw ResourceError.invalidArchive(url.lastPathComponent)
        }
    }

    private static func loadBundledResources() throws -> KailashMapResources {
        let mapURL = try bundledURL(named: "errarium-kailash-kora-2026")
        let terrainURL = try bundledURL(named: "errarium-kailash-kora-2026-dem")
        try validatePMTiles(mapURL, expectedTileType: 1)
        try validatePMTiles(terrainURL, expectedTileType: 4)

        let styleDirectory = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("OfflineMapStyles", isDirectory: true)
        try FileManager.default.createDirectory(
            at: styleDirectory,
            withIntermediateDirectories: true
        )
        let styleURL = styleDirectory.appendingPathComponent("kailash-offline-v1.json")
        let styleData = try makeStyleData(mapURL: mapURL, terrainURL: terrainURL)
        try styleData.write(to: styleURL, options: .atomic)
        return KailashMapResources(mapURL: mapURL, terrainURL: terrainURL, styleURL: styleURL)
    }

    private static func bundledURL(named name: String) throws -> URL {
        if let url = Bundle.main.url(forResource: name, withExtension: "pmtiles", subdirectory: "Maps")
            ?? Bundle.main.url(forResource: name, withExtension: "pmtiles") {
            return url
        }
        throw ResourceError.missingArchive(name)
    }

    private static func makeStyleData(mapURL: URL, terrainURL: URL) throws -> Data {
        let vectorArchive = "pmtiles://\(mapURL.absoluteString)"
        let terrainArchive = "pmtiles://\(terrainURL.absoluteString)"
        let glyphDirectory = Bundle.main.resourceURL?
            .appendingPathComponent("fonts", isDirectory: true)
            .absoluteString ?? ""
        let glyphsURL = "\(glyphDirectory){fontstack}/{range}.pbf"
        let layers: [[String: Any]] = [
            ["id": "background", "type": "background", "paint": ["background-color": "#e8e2d1"]],
            ["id": "earth", "type": "fill", "source": "protomaps", "source-layer": "earth", "paint": ["fill-color": "#e8e2d1"]],
            [
                "id": "landcover", "type": "fill", "source": "protomaps", "source-layer": "landcover",
                "paint": [
                    "fill-color": ["match", ["get", "kind"], "forest", "#bfd3ae", "glacier", "#d9edf1", "scrub", "#d8d6af", "grassland", "#d5d8ae", "#d9d5bb"],
                    "fill-opacity": 0.8
                ]
            ],
            [
                "id": "landuse", "type": "fill", "source": "protomaps", "source-layer": "landuse",
                "paint": [
                    "fill-color": ["match", ["get", "kind"], "forest", "#b6cda6", "wood", "#b6cda6", "national_park", "#c8dcb8", "nature_reserve", "#c8dcb8", "glacier", "#d9edf1", "sand", "#ead9aa", "bare_rock", "#c9c1b4", "#d7d3bb"],
                    "fill-opacity": 0.62
                ]
            ],
            [
                "id": "hillshade", "type": "hillshade", "source": "terrain",
                "paint": [
                    "hillshade-exaggeration": 0.55,
                    "hillshade-shadow-color": "#4b4038",
                    "hillshade-highlight-color": "#fff7e2",
                    "hillshade-accent-color": "#776a59"
                ]
            ],
            ["id": "water-fill", "type": "fill", "source": "protomaps", "source-layer": "water", "filter": ["==", ["geometry-type"], "Polygon"], "paint": ["fill-color": "#8fc8dc"]],
            ["id": "water-line", "type": "line", "source": "protomaps", "source-layer": "water", "filter": ["==", ["geometry-type"], "LineString"], "paint": ["line-color": "#63a9c6", "line-width": ["interpolate", ["linear"], ["zoom"], 8, 0.7, 14, 2.2]]],
            ["id": "boundaries", "type": "line", "source": "protomaps", "source-layer": "boundaries", "paint": ["line-color": "#aa9285", "line-width": 0.7, "line-dasharray": [4, 3], "line-opacity": 0.6]],
            ["id": "buildings", "type": "fill", "source": "protomaps", "source-layer": "buildings", "minzoom": 13, "paint": ["fill-color": "#bcb3aa", "fill-outline-color": "#958c83"]],
            ["id": "major-road-casing", "type": "line", "source": "protomaps", "source-layer": "roads", "filter": ["in", ["get", "kind"], ["literal", ["highway", "major_road"]]], "paint": ["line-color": "#a79478", "line-width": ["interpolate", ["linear"], ["zoom"], 7, 1.2, 15, 7.0]]],
            ["id": "major-roads", "type": "line", "source": "protomaps", "source-layer": "roads", "filter": ["in", ["get", "kind"], ["literal", ["highway", "major_road"]]], "paint": ["line-color": "#f3dfb7", "line-width": ["interpolate", ["linear"], ["zoom"], 7, 0.7, 15, 5.0]]],
            ["id": "minor-roads", "type": "line", "source": "protomaps", "source-layer": "roads", "filter": ["==", ["get", "kind"], "minor_road"], "paint": ["line-color": "#fff4d7", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 0.6, 16, 3.4]]],
            ["id": "trails", "type": "line", "source": "protomaps", "source-layer": "roads", "filter": ["==", ["get", "kind"], "path"], "paint": ["line-color": ["match", ["get", "kind_detail"], "track", "#9b6c3d", "steps", "#a13a3a", "#e36a32"], "line-width": ["interpolate", ["linear"], ["zoom"], 10, 0.7, 16, 2.4], "line-dasharray": [2, 1.5]]],
            ["id": "hut-camp-poi", "type": "circle", "source": "protomaps", "source-layer": "pois", "minzoom": 10, "filter": ["in", ["get", "kind"], ["literal", ["alpine_hut", "wilderness_hut", "shelter", "camp_site", "ranger_station"]]], "paint": ["circle-radius": ["interpolate", ["linear"], ["zoom"], 10, 2.5, 15, 5.5], "circle-color": "#2e8b57", "circle-stroke-color": "#ffffff", "circle-stroke-width": 1.2]],
            ["id": "water-poi", "type": "circle", "source": "protomaps", "source-layer": "pois", "minzoom": 12, "filter": ["in", ["get", "kind"], ["literal", ["drinking_water", "water_point", "spring"]]], "paint": ["circle-radius": 4, "circle-color": "#168bb7", "circle-stroke-color": "#ffffff", "circle-stroke-width": 1]],
            ["id": "peak-poi", "type": "circle", "source": "protomaps", "source-layer": "pois", "minzoom": 9, "filter": ["==", ["get", "kind"], "peak"], "paint": ["circle-radius": 4.5, "circle-color": "#b44037", "circle-stroke-color": "#ffffff", "circle-stroke-width": 1.2]],
            [
                "id": "place-labels", "type": "symbol", "source": "protomaps", "source-layer": "places", "minzoom": 4,
                "layout": [
                    "text-field": ["coalesce", ["get", "name:ru"], ["get", "name:en"], ["get", "pgf:name:en"], ["get", "name"]],
                    "text-font": ["Noto Sans Regular"],
                    "text-size": ["interpolate", ["linear"], ["zoom"], 5, 11, 12, 15],
                    "text-max-width": 8,
                    "text-letter-spacing": 0.03,
                    "text-allow-overlap": false
                ],
                "paint": ["text-color": "#3d3934", "text-halo-color": "#f4eedf", "text-halo-width": 1.5]
            ],
            [
                "id": "trail-labels", "type": "symbol", "source": "protomaps", "source-layer": "roads", "minzoom": 13,
                "filter": ["==", ["get", "kind"], "path"],
                "layout": [
                    "symbol-placement": "line",
                    "text-field": ["coalesce", ["get", "name:ru"], ["get", "name:en"], ["get", "pgf:name:en"], ["get", "name"]],
                    "text-font": ["Noto Sans Regular"],
                    "text-size": 10.5,
                    "text-allow-overlap": false
                ],
                "paint": ["text-color": "#743b22", "text-halo-color": "#f4eedf", "text-halo-width": 1.3]
            ],
            [
                "id": "outdoor-poi-labels", "type": "symbol", "source": "protomaps", "source-layer": "pois", "minzoom": 11,
                "filter": ["in", ["get", "kind"], ["literal", ["peak", "alpine_hut", "wilderness_hut", "shelter", "camp_site", "ranger_station", "drinking_water", "water_point", "viewpoint"]]],
                "layout": [
                    "text-field": ["coalesce", ["get", "name:ru"], ["get", "name:en"], ["get", "pgf:name:en"], ["get", "name"]],
                    "text-font": ["Noto Sans Regular"],
                    "text-size": 11,
                    "text-offset": [0, 1.0],
                    "text-anchor": "top",
                    "text-max-width": 9,
                    "text-allow-overlap": false
                ],
                "paint": ["text-color": "#332f2b", "text-halo-color": "#f6f0df", "text-halo-width": 1.5]
            ]
        ]

        let style: [String: Any] = [
            "version": 8,
            "name": "Errarium Kailash Offline",
            "glyphs": glyphsURL,
            "sources": [
                "protomaps": [
                    "type": "vector",
                    "url": vectorArchive,
                    "attribution": "© OpenStreetMap contributors"
                ],
                "terrain": [
                    "type": "raster-dem",
                    "url": terrainArchive,
                    "tileSize": 512,
                    "encoding": "terrarium",
                    "attribution": "Terrain © Mapterhorn"
                ]
            ],
            "layers": layers
        ]
        return try JSONSerialization.data(withJSONObject: style, options: [.prettyPrinted, .sortedKeys])
    }

    enum ResourceError: LocalizedError {
        case missingArchive(String)
        case invalidArchive(String)

        var errorDescription: String? {
            switch self {
            case let .missingArchive(name): "Missing bundled map archive: \(name).pmtiles"
            case let .invalidArchive(name): "Invalid bundled PMTiles v3 archive: \(name)"
            }
        }
    }
}
