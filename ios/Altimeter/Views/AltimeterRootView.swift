import SwiftUI

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

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                TopoMapView(
                    coordinate: model.state.coordinate,
                    topographic: model.useTopographicMap
                )
                .ignoresSafeArea(edges: .bottom)

                if let coordinate = model.state.coordinate {
                    Text(String(format: "%.5f, %.5f", coordinate.latitude, coordinate.longitude))
                        .font(.caption.monospacedDigit().weight(.light))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 9)
                        .background(.regularMaterial, in: Capsule())
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
        .task { model.start() }
    }
}
