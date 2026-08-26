import CoreLocation
import MapLibre
import SwiftUI
import UIKit

struct AltimeterMapSurface: View {
    let coordinate: CLLocationCoordinate2D?
    let trackPoints: [TrackMapPoint]
    let topographic: Bool
    let sourceMode: MapSourceMode

    private var usesKailash: Bool {
        sourceMode.usesKailash(at: coordinate) && KailashOfflineMap.resources != nil
    }

    var body: some View {
        ZStack(alignment: .topLeading) {
            if usesKailash, let resources = KailashOfflineMap.resources {
                KailashOfflineMapView(
                    coordinate: coordinate,
                    trackPoints: trackPoints,
                    resources: resources
                )
            } else {
                TopoMapView(
                    coordinate: coordinate,
                    trackPoints: trackPoints,
                    topographic: topographic
                )
            }

            if usesKailash {
                Label(L10n.string("Кайлас · офлайн"), systemImage: "arrow.down.circle.fill")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(.black.opacity(0.68), in: Capsule())
                    .padding(10)
            }
        }
    }
}

struct KailashOfflineMapView: UIViewRepresentable {
    let coordinate: CLLocationCoordinate2D?
    let trackPoints: [TrackMapPoint]
    let resources: KailashMapResources

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> MLNMapView {
        let map = MLNMapView(frame: .zero, styleURL: resources.styleURL)
        map.delegate = context.coordinator
        map.showsUserLocation = true
        map.showsCompassView = true
        map.showsScale = true
        map.scaleBarUsesMetricSystem = true
        map.showsLogoView = false
        map.minimumZoomLevel = 7.5
        map.maximumZoomLevel = 15
        map.tintColor = UIColor(red: 0.10, green: 0.82, blue: 0.78, alpha: 1)
        context.coordinator.update(trackPoints: trackPoints)
        center(map, animated: false)
        return map
    }

    func updateUIView(_ map: MLNMapView, context: Context) {
        context.coordinator.update(trackPoints: trackPoints)
        guard !context.coordinator.hasCenteredOnUser,
              let coordinate,
              KailashOfflineMap.contains(coordinate) else { return }
        map.setCenter(coordinate, zoomLevel: 12, direction: 0, animated: false)
        context.coordinator.hasCenteredOnUser = true
    }

    private func center(_ map: MLNMapView, animated: Bool) {
        if let coordinate, KailashOfflineMap.contains(coordinate) {
            map.setCenter(coordinate, zoomLevel: 12, direction: 0, animated: animated)
        } else {
            map.setCenter(KailashOfflineMap.center, zoomLevel: 9.5, direction: 0, animated: animated)
        }
    }

    final class Coordinator: NSObject, MLNMapViewDelegate {
        var hasCenteredOnUser = false
        private var routeSource: MLNShapeSource?
        private var latestTrackPoints: [TrackMapPoint] = []

        func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
            guard routeSource == nil else { return }
            let source = MLNShapeSource(
                identifier: "recorded-track",
                shape: routeShape(from: latestTrackPoints),
                options: nil
            )
            style.addSource(source)
            routeSource = source

            let casing = MLNLineStyleLayer(identifier: "recorded-track-casing", source: source)
            casing.lineColor = NSExpression(forConstantValue: UIColor.black.withAlphaComponent(0.7))
            casing.lineWidth = NSExpression(forConstantValue: 7)
            casing.lineCap = NSExpression(forConstantValue: "round")
            casing.lineJoin = NSExpression(forConstantValue: "round")
            style.addLayer(casing)

            let route = MLNLineStyleLayer(identifier: "recorded-track-line", source: source)
            route.lineColor = NSExpression(
                forConstantValue: UIColor(red: 0.10, green: 0.92, blue: 0.84, alpha: 1)
            )
            route.lineWidth = NSExpression(forConstantValue: 4)
            route.lineCap = NSExpression(forConstantValue: "round")
            route.lineJoin = NSExpression(forConstantValue: "round")
            style.addLayer(route)
        }

        func update(trackPoints: [TrackMapPoint]) {
            guard latestTrackPoints != trackPoints else { return }
            latestTrackPoints = trackPoints
            routeSource?.shape = routeShape(from: trackPoints)
        }

        private func routeShape(from points: [TrackMapPoint]) -> MLNShape {
            var segments: [[CLLocationCoordinate2D]] = []
            for point in points {
                if segments.isEmpty || point.startsNewSegment { segments.append([]) }
                segments[segments.count - 1].append(point.coordinate)
            }
            let polylines: [MLNPolylineFeature] = segments.compactMap { segment in
                guard segment.count >= 2 else { return nil }
                var coordinates = segment
                return MLNPolylineFeature(
                    coordinates: &coordinates,
                    count: UInt(coordinates.count)
                )
            }
            return MLNShapeCollectionFeature(shapes: polylines.map { $0 as MLNShape })
        }
    }
}
