import Foundation

/// Persists GPX files and a compact metadata index used by the in-app track archive.
final class TrackStore {
    private let fileManager: FileManager
    let directory: URL
    private var indexURL: URL { directory.appendingPathComponent("TrackIndex.json") }

    private(set) var tracks: [SavedTrack] = []

    init(
        directory: URL? = nil,
        fileManager: FileManager = .default
    ) {
        self.fileManager = fileManager
        if let directory {
            self.directory = directory
        } else {
            let documents = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!
            self.directory = documents.appendingPathComponent("Tracks", isDirectory: true)
        }
    }

    @discardableResult
    func load() -> [SavedTrack] {
        try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        var indexed: [SavedTrack] = []
        if let data = try? Data(contentsOf: indexURL),
           let decoded = try? JSONDecoder.trackDecoder.decode([SavedTrack].self, from: data) {
            indexed = decoded.filter { fileManager.fileExists(atPath: fileURL(for: $0).path) }
        }

        let knownNames = Set(indexed.map(\.fileName))
        let orphaned = ((try? fileManager.contentsOfDirectory(
            at: directory,
            includingPropertiesForKeys: [.contentModificationDateKey],
            options: [.skipsHiddenFiles]
        )) ?? [])
            .filter { $0.pathExtension.lowercased() == "gpx" && !knownNames.contains($0.lastPathComponent) }
            .map { url in
                let values = try? url.resourceValues(forKeys: [.contentModificationDateKey])
                let date = values?.contentModificationDate ?? Date()
                return SavedTrack(
                    id: UUID(),
                    fileName: url.lastPathComponent,
                    startedAt: date,
                    updatedAt: date,
                    duration: 0,
                    pointCount: 0,
                    distanceMeters: 0,
                    ascentMeters: 0,
                    isComplete: true
                )
            }

        tracks = (indexed + orphaned).sorted { $0.startedAt > $1.startedAt }
        persistIndex()
        return tracks
    }

    func makeTrackURL(at date: Date = Date()) -> URL {
        try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd_HH-mm-ss"
        return directory.appendingPathComponent("Altimeter_\(formatter.string(from: date)).gpx")
    }

    @discardableResult
    func save(
        recorder: GPXRecorder,
        to url: URL,
        id: UUID,
        complete: Bool,
        now: Date = Date()
    ) throws -> SavedTrack {
        try recorder.save(to: url)
        let duration = max(0, (recorder.lastPointDate ?? now).timeIntervalSince(recorder.startedAt))
        let track = SavedTrack(
            id: id,
            fileName: url.lastPathComponent,
            startedAt: recorder.startedAt,
            updatedAt: now,
            duration: duration,
            pointCount: recorder.points.count,
            distanceMeters: recorder.distanceMeters,
            ascentMeters: recorder.ascentMeters,
            isComplete: complete
        )
        if let index = tracks.firstIndex(where: { $0.id == id || $0.fileName == url.lastPathComponent }) {
            tracks[index] = track
        } else {
            tracks.append(track)
        }
        tracks.sort { $0.startedAt > $1.startedAt }
        persistIndex()
        return track
    }

    func delete(_ track: SavedTrack) throws {
        let url = fileURL(for: track)
        if fileManager.fileExists(atPath: url.path) { try fileManager.removeItem(at: url) }
        tracks.removeAll { $0.id == track.id }
        persistIndex()
    }

    func fileURL(for track: SavedTrack) -> URL {
        directory.appendingPathComponent(track.fileName)
    }

    private func persistIndex() {
        guard let data = try? JSONEncoder.trackEncoder.encode(tracks) else { return }
        try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        try? data.write(to: indexURL, options: .atomic)
    }
}

private extension JSONEncoder {
    static var trackEncoder: JSONEncoder {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }
}

private extension JSONDecoder {
    static var trackDecoder: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }
}
