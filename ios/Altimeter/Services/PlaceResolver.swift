import Foundation
import CoreLocation

@MainActor
final class PlaceResolver {
    private let geocoder = CLGeocoder()
    private var lastLocation: CLLocation?
    private var lastResolvedAt = Date.distantPast

    func resolve(_ location: CLLocation) async -> String? {
        if let lastLocation,
           location.distance(from: lastLocation) < 1_000,
           Date().timeIntervalSince(lastResolvedAt) < 10 * 60 {
            return nil
        }
        lastLocation = location
        lastResolvedAt = Date()
        geocoder.cancelGeocode()
        do {
            guard let place = try await geocoder.reverseGeocodeLocation(location).first else { return nil }
            let parts = [place.locality, place.subLocality, place.administrativeArea]
                .compactMap { $0 }
                .reduce(into: [String]()) { result, value in
                    if !result.contains(value) { result.append(value) }
                }
            return parts.prefix(2).joined(separator: ", ")
        } catch {
            return nil
        }
    }
}

