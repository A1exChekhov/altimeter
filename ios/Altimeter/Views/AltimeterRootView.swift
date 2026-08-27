import SwiftUI
import PhotosUI
import UIKit
import CoreLocation

enum AppTab: Hashable {
    case home
    case map
    case track
    case data

    var title: String {
        switch self {
        case .home: L10n.string("Главная")
        case .map: L10n.string("Карта")
        case .track: L10n.string("Поход")
        case .data: L10n.string("Данные")
        }
    }
}

struct AltimeterRootView: View {
    @EnvironmentObject private var model: AppModel
    @State private var selection: AppTab = .home

    var body: some View {
        TabView(selection: $selection) {
            AltimeterScreen(page: .home)
                .tag(AppTab.home)
                .tabItem { Label("Главная", systemImage: "mountain.2") }

            FullMapScreen()
                .tag(AppTab.map)
                .tabItem { Label("Карта", systemImage: "map") }

            AltimeterScreen(page: .track)
                .tag(AppTab.track)
                .tabItem { Label("Поход", systemImage: "figure.hiking") }
                .badge(model.state.track.isRecording ? 1 : 0)

            AltimeterScreen(page: .data)
                .tag(AppTab.data)
                .tabItem { Label("Данные", systemImage: "chart.xyaxis.line") }
        }
        .tint(.primary)
    }
}

private struct FullMapScreen: View {
    @EnvironmentObject private var model: AppModel
    @State private var showsSettings = false
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var selectedPhotoURL: URL?
    @State private var showsCamera = false
    @State private var showsPhotoLibrary = false
    @State private var photoErrorMessage: String?
    @State private var shareItems: [Any] = []
    @State private var showsShare = false

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                AltimeterMapSurface(
                    coordinate: model.state.coordinate,
                    trackPoints: model.state.trackPoints,
                    onlineStyle: $model.onlineMapStyle,
                    sourceMode: $model.mapSourceMode
                )
                .ignoresSafeArea(edges: .bottom)

                if let coordinate = model.state.coordinate {
                    VStack(spacing: 8) {
                        Text(String(format: "%.5f, %.5f", coordinate.latitude, coordinate.longitude))
                            .font(.caption.monospacedDigit().weight(.light))
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 9)
                            .background(.regularMaterial, in: Capsule())
                        HStack(spacing: 8) {
                            Button {
                                prepareShare(coordinate: coordinate)
                            } label: {
                                Label("Поделиться", systemImage: "square.and.arrow.up")
                            }
                            .buttonStyle(.borderedProminent)

                            Menu {
                                Button {
                                    if UIImagePickerController.isSourceTypeAvailable(.camera) {
                                        showsCamera = true
                                    } else {
                                        photoErrorMessage = L10n.string("photo.camera.unavailable")
                                    }
                                } label: {
                                    Label(L10n.string("photo.source.camera"), systemImage: "camera.fill")
                                }
                                Button {
                                    showsPhotoLibrary = true
                                } label: {
                                    Label(L10n.string("photo.source.gallery"), systemImage: "photo.on.rectangle")
                                }
                            } label: {
                                Label(
                                    "Добавить фото",
                                    systemImage: "camera.fill"
                                )
                            }
                            .buttonStyle(.bordered)
                        }
                        .font(.caption.weight(.regular))
                    }
                    .padding(.bottom, 12)
                }
            }
            .navigationTitle("Карта")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showsSettings = true } label: {
                        Image(systemName: "gearshape")
                    }
                }
            }
        }
        .sheet(isPresented: $showsSettings) { SettingsView() }
        .sheet(isPresented: $showsShare) { ActivityShareView(items: shareItems) }
        .fullScreenCover(isPresented: $showsCamera) {
            CameraCaptureView { image in preparePhoto(image) }
                .ignoresSafeArea()
        }
        .photosPicker(
            isPresented: $showsPhotoLibrary,
            selection: $selectedPhoto,
            matching: .images
        )
        .alert(
            L10n.string("photo.error.title"),
            isPresented: Binding(
                get: { photoErrorMessage != nil },
                set: { if !$0 { photoErrorMessage = nil } }
            )
        ) {
            Button("OK", role: .cancel) { photoErrorMessage = nil }
        } message: {
            Text(photoErrorMessage ?? "")
        }
        .onChange(of: selectedPhoto) { _, item in
            guard let item else { return }
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self),
                      let image = UIImage(data: data) else {
                    photoErrorMessage = L10n.string("photo.process.failed")
                    return
                }
                preparePhoto(image)
                selectedPhoto = nil
            }
        }
        .task { model.start() }
    }

    private func prepareShare(coordinate: CLLocationCoordinate2D) {
        let altitude = model.state.altitude.map {
            L10n.string(
                "format.altitude.value",
                Int(model.unit.value(fromMeters: $0).rounded()),
                model.unit.symbol
            )
        } ?? "—"
        let pressure = model.state.pressureHPA.map { L10n.string("format.pressure", $0) } ?? "—"
        let formatter = DateFormatter()
        formatter.locale = model.appLanguage.locale
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        let link = "https://maps.apple.com/?ll=\(coordinate.latitude),\(coordinate.longitude)"
        let text = """
        📍 \(String(format: "%.5f, %.5f", coordinate.latitude, coordinate.longitude))
        \(L10n.string("share.measurements", altitude, pressure))
        \(formatter.string(from: Date()))
        \(link)

        Errarium™ by Aleksey Hermes
        errarium.ai@gmail.com
        """
        shareItems = [text]
        if let selectedPhotoURL { shareItems.append(selectedPhotoURL) }
        showsShare = true
    }

    private func preparePhoto(_ image: UIImage) {
        guard let coordinate = model.state.coordinate else {
            photoErrorMessage = L10n.string("photo.location.unavailable")
            return
        }
        let altitude = model.state.altitude.map {
            L10n.string(
                "format.altitude.value",
                Int(model.unit.value(fromMeters: $0).rounded()),
                model.unit.symbol
            )
        } ?? "—"
        let pressure = model.state.pressureHPA.map { L10n.string("format.pressure", $0) } ?? "—"
        let formatter = DateFormatter()
        formatter.locale = model.appLanguage.locale
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        let stamp = LocationPhotoStamp(
            altitude: L10n.string("photo.stamp.altitude", altitude),
            pressure: L10n.string("photo.stamp.pressure", pressure),
            coordinates: String(format: "%.5f, %.5f", coordinate.latitude, coordinate.longitude),
            localTime: formatter.string(from: Date())
        )
        do {
            if let selectedPhotoURL { try? FileManager.default.removeItem(at: selectedPhotoURL) }
            selectedPhotoURL = try LocationPhotoComposer.compose(image: image, stamp: stamp)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                prepareShare(coordinate: coordinate)
            }
        } catch {
            photoErrorMessage = L10n.string("photo.process.failed")
        }
    }
}

private struct ActivityShareView: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
