import SwiftUI

struct SavedTracksView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var pendingDeletion: SavedTrack?

    var body: some View {
        NavigationStack {
            Group {
                if model.engine.savedTracks.isEmpty {
                    ContentUnavailableView(
                        "Нет сохранённых маршрутов",
                        systemImage: "figure.hiking",
                        description: Text("Запустите запись на главном экране — GPX появится здесь после первой точки.")
                    )
                } else {
                    List {
                        ForEach(model.engine.savedTracks) { track in
                            trackRow(track)
                                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                    Button(role: .destructive) { pendingDeletion = track } label: {
                                        Label("Удалить", systemImage: "trash")
                                    }
                                }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Маршруты")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
            .confirmationDialog(
                "Удалить маршрут?",
                isPresented: Binding(
                    get: { pendingDeletion != nil },
                    set: { if !$0 { pendingDeletion = nil } }
                ),
                titleVisibility: .visible
            ) {
                Button("Удалить GPX", role: .destructive) {
                    if let track = pendingDeletion { model.engine.deleteTrack(track) }
                    pendingDeletion = nil
                }
                Button("Отмена", role: .cancel) { pendingDeletion = nil }
            } message: {
                Text("Файл будет удалён из приложения «Файлы». Это действие нельзя отменить.")
            }
        }
    }

    private func trackRow(_ track: SavedTrack) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(track.startedAt.formatted(date: .abbreviated, time: .shortened))
                        .font(.headline)
                    Text(track.fileName)
                        .font(.caption.monospaced())
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer()
                if !track.isComplete {
                    Text("АВТОСОХРАНЕНИЕ")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.orange)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 4)
                        .background(.orange.opacity(0.12), in: Capsule())
                }
            }

            HStack(spacing: 16) {
                Label(AltimeterFormat.duration(track.duration), systemImage: "clock")
                Label(AltimeterFormat.distance(track.distanceMeters), systemImage: "point.topleft.down.to.point.bottomright.curvepath")
                Label(
                    L10n.string("track.ascent", Int(track.ascentMeters.rounded())),
                    systemImage: "arrow.up.right"
                )
            }
            .font(.caption)
            .foregroundStyle(.secondary)

            HStack {
                Label(
                    L10n.string("track.points", track.pointCount),
                    systemImage: "mappin.and.ellipse"
                )
                    .font(.caption).foregroundStyle(.secondary)
                Spacer()
                ShareLink(item: model.engine.fileURL(for: track)) {
                    Label("Поделиться", systemImage: "square.and.arrow.up")
                        .font(.subheadline.weight(.semibold))
                }
            }
        }
        .padding(.vertical, 6)
    }
}
