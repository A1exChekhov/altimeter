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
        case .home: "Главная"
        case .map: "Карта"
        case .track: "Поход"
        case .data: "Данные"
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
    @State private var shareItems: [Any] = []
    @State private var showsShare = false

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                TopoMapView(
                    coordinate: model.state.coordinate,
                    topographic: model.useTopographicMap
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

                            PhotosPicker(selection: $selectedPhoto, matching: .images) {
                                Label(
                                    selectedPhotoURL == nil ? "Добавить фото" : "Фото добавлено",
                                    systemImage: selectedPhotoURL == nil ? "photo.badge.plus" : "checkmark.circle"
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
        .onChange(of: selectedPhoto) { _, item in
            guard let item else {
                selectedPhotoURL = nil
                return
            }
            Task {
                guard let data = try? await item.loadTransferable(type: Data.self) else { return }
                let url = FileManager.default.temporaryDirectory
                    .appendingPathComponent("errarium-location-\(UUID().uuidString).jpg")
                if (try? data.write(to: url, options: .atomic)) != nil {
                    selectedPhotoURL = url
                }
            }
        }
        .task { model.start() }
    }

    private func prepareShare(coordinate: CLLocationCoordinate2D) {
        let altitude = model.state.altitude.map { String(format: "%.0f м", $0) } ?? "—"
        let pressure = model.state.pressureHPA.map { String(format: "%.1f гПа", $0) } ?? "—"
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        let link = "https://maps.apple.com/?ll=\(coordinate.latitude),\(coordinate.longitude)"
        let text = """
        📍 (String(format: "%.5f, %.5f", coordinate.latitude, coordinate.longitude))
        Высота: (altitude) · Давление: (pressure)
        (formatter.string(from: Date()))
        (link)

        Errarium™ by Aleksey Hermes
        errarium.ai@gmail.com
        """
        shareItems = [text]
        if let selectedPhotoURL { shareItems.append(selectedPhotoURL) }
        showsShare = true
    }
}

private struct ActivityShareView: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
