import MapKit
import SwiftUI

struct TopoMapView: UIViewRepresentable {
    let coordinate: CLLocationCoordinate2D?
    let trackPoints: [TrackMapPoint]
    let topographic: Bool

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView(frame: .zero)
        map.delegate = context.coordinator
        map.mapType = .mutedStandard
        map.showsUserLocation = true
        map.showsCompass = true
        map.showsScale = false
        map.pointOfInterestFilter = .excludingAll
        let scale = MKScaleView(mapView: map)
        scale.scaleVisibility = .visible
        scale.legendAlignment = .leading
        scale.translatesAutoresizingMaskIntoConstraints = false
        map.addSubview(scale)
        NSLayoutConstraint.activate([
            scale.leadingAnchor.constraint(equalTo: map.leadingAnchor, constant: 12),
            scale.bottomAnchor.constraint(equalTo: map.safeAreaLayoutGuide.bottomAnchor, constant: -12),
        ])
        let modeChanged = context.coordinator.configureOverlay(on: map, topographic: topographic)
        context.coordinator.configureRoute(on: map, points: trackPoints, force: modeChanged)
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        let modeChanged = context.coordinator.configureOverlay(on: map, topographic: topographic)
        context.coordinator.configureRoute(on: map, points: trackPoints, force: modeChanged)
        guard let coordinate else { return }
        if context.coordinator.lastCenteredCoordinate == nil {
            map.setRegion(
                MKCoordinateRegion(
                    center: coordinate,
                    latitudinalMeters: 18_000,
                    longitudinalMeters: 18_000
                ),
                animated: false
            )
            context.coordinator.lastCenteredCoordinate = coordinate
        }
    }

    final class Coordinator: NSObject, MKMapViewDelegate {
        var lastCenteredCoordinate: CLLocationCoordinate2D?
        private var tileOverlay: MKTileOverlay?
        private var routeOverlays: [MKPolyline] = []
        private var routePoints: [TrackMapPoint] = []
        private var currentMode: Bool?

        @discardableResult
        func configureOverlay(on map: MKMapView, topographic: Bool) -> Bool {
            guard currentMode != topographic else { return false }
            currentMode = topographic
            if let tileOverlay { map.removeOverlay(tileOverlay) }
            tileOverlay = nil
            map.mapType = topographic ? .standard : .mutedStandard
            guard topographic else { return true }

            let overlay = MKTileOverlay(
                urlTemplate: "https://a.tile.opentopomap.org/{z}/{x}/{y}.png"
            )
            overlay.canReplaceMapContent = true
            overlay.minimumZ = 1
            overlay.maximumZ = 17
            tileOverlay = overlay
            map.addOverlay(overlay, level: .aboveLabels)
            return true
        }

        func configureRoute(on map: MKMapView, points: [TrackMapPoint], force: Bool = false) {
            guard force || routePoints != points else { return }
            if !routeOverlays.isEmpty { map.removeOverlays(routeOverlays) }
            routePoints = points

            var segments: [[CLLocationCoordinate2D]] = []
            for point in points {
                if segments.isEmpty || point.startsNewSegment { segments.append([]) }
                segments[segments.count - 1].append(point.coordinate)
            }
            routeOverlays = segments.compactMap { segment in
                guard segment.count >= 2 else { return nil }
                return segment.withUnsafeBufferPointer { buffer in
                    guard let baseAddress = buffer.baseAddress else { return nil }
                    return MKPolyline(coordinates: baseAddress, count: buffer.count)
                }
            }
            if !routeOverlays.isEmpty {
                map.addOverlays(routeOverlays, level: .aboveLabels)
            }
        }

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let tile = overlay as? MKTileOverlay { return MKTileOverlayRenderer(tileOverlay: tile) }
            if let route = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: route)
                renderer.strokeColor = UIColor(red: 0.21, green: 0.88, blue: 0.82, alpha: 1)
                renderer.lineWidth = 5
                renderer.lineCap = .round
                renderer.lineJoin = .round
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }
    }
}

struct MapCardView: View {
    let state: AltimeterState
    @Binding var topographic: Bool
    @Binding var sourceMode: MapSourceMode

    private var usesKailash: Bool {
        sourceMode.usesKailash(at: state.coordinate) && KailashOfflineMap.resources != nil
    }

    var body: some View {
        InstrumentCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    SectionHeading(icon: "map.fill", title: L10n.string("Карта"))
                }

                ZStack {
                    AltimeterMapSurface(
                        coordinate: state.coordinate,
                        trackPoints: state.trackPoints,
                        topographic: $topographic,
                        sourceMode: $sourceMode,
                        compactSourceControl: true
                    )
                    if state.coordinate == nil && !usesKailash {
                        Rectangle().fill(.black.opacity(0.45))
                        ProgressView("Ждём координаты…")
                    }
                }
                .frame(height: 260)
                .clipShape(RoundedRectangle(cornerRadius: 16))

                Text(usesKailash
                     ? "© OpenStreetMap contributors · Terrain © Mapterhorn"
                     : (topographic
                        ? "© OpenStreetMap contributors · © OpenTopoMap (CC-BY-SA)"
                        : "Картографические данные © Apple"))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
