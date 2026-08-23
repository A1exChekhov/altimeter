import MapKit
import SwiftUI

struct TopoMapView: UIViewRepresentable {
    let coordinate: CLLocationCoordinate2D?
    let topographic: Bool

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> MKMapView {
        let map = MKMapView(frame: .zero)
        map.delegate = context.coordinator
        map.mapType = .mutedStandard
        map.showsUserLocation = true
        map.showsCompass = true
        map.showsScale = true
        map.pointOfInterestFilter = .excludingAll
        context.coordinator.configureOverlay(on: map, topographic: topographic)
        return map
    }

    func updateUIView(_ map: MKMapView, context: Context) {
        context.coordinator.configureOverlay(on: map, topographic: topographic)
        guard let coordinate else { return }
        if context.coordinator.lastCenteredCoordinate == nil {
            map.setRegion(
                MKCoordinateRegion(
                    center: coordinate,
                    latitudinalMeters: 4_000,
                    longitudinalMeters: 4_000
                ),
                animated: false
            )
            context.coordinator.lastCenteredCoordinate = coordinate
        }
    }

    final class Coordinator: NSObject, MKMapViewDelegate {
        var lastCenteredCoordinate: CLLocationCoordinate2D?
        private var tileOverlay: MKTileOverlay?
        private var currentMode: Bool?

        func configureOverlay(on map: MKMapView, topographic: Bool) {
            guard currentMode != topographic else { return }
            currentMode = topographic
            if let tileOverlay { map.removeOverlay(tileOverlay) }
            tileOverlay = nil
            map.mapType = topographic ? .standard : .mutedStandard
            guard topographic else { return }

            let overlay = MKTileOverlay(
                urlTemplate: "https://a.tile.opentopomap.org/{z}/{x}/{y}.png"
            )
            overlay.canReplaceMapContent = true
            overlay.minimumZ = 1
            overlay.maximumZ = 17
            tileOverlay = overlay
            map.addOverlay(overlay, level: .aboveLabels)
        }

        func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let tile = overlay as? MKTileOverlay { return MKTileOverlayRenderer(tileOverlay: tile) }
            return MKOverlayRenderer(overlay: overlay)
        }
    }
}

struct MapCardView: View {
    let state: AltimeterState
    @Binding var topographic: Bool

    var body: some View {
        InstrumentCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    SectionHeading(icon: "map.fill", title: "Карта")
                    Spacer()
                    Picker("Слой", selection: $topographic) {
                        Text("Топо").tag(true)
                        Text("Apple").tag(false)
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 145)
                }

                ZStack {
                    TopoMapView(coordinate: state.coordinate, topographic: topographic)
                    if state.coordinate == nil {
                        Rectangle().fill(.black.opacity(0.45))
                        ProgressView("Ждём координаты…")
                    }
                }
                .frame(height: 260)
                .clipShape(RoundedRectangle(cornerRadius: 16))

                Text(topographic
                     ? "© OpenStreetMap contributors · © OpenTopoMap (CC-BY-SA)"
                     : "Картографические данные © Apple")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

