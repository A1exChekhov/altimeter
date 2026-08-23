import Foundation
import SwiftUI
import WidgetKit

struct AltimeterWidgetEntry: TimelineEntry {
    let date: Date
    let snapshot: AltimeterWidgetSnapshot
}

struct AltimeterWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> AltimeterWidgetEntry {
        AltimeterWidgetEntry(
            date: Date(),
            snapshot: AltimeterWidgetSnapshot(
                altitudeMeters: 1_248,
                trackIsRecording: true,
                trackDistanceMeters: 7_420,
                trackPointCount: 814,
                heartRateBPM: 92,
                oxygenPercent: 96,
                heartRateSource: "Apple Watch",
                oxygenSource: "Apple Watch",
                updatedAt: Date()
            )
        )
    }

    func getSnapshot(in context: Context, completion: @escaping (AltimeterWidgetEntry) -> Void) {
        completion(AltimeterWidgetEntry(date: Date(), snapshot: WidgetSnapshotStore.read()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<AltimeterWidgetEntry>) -> Void) {
        let now = Date()
        let entry = AltimeterWidgetEntry(date: now, snapshot: WidgetSnapshotStore.read())
        completion(Timeline(entries: [entry], policy: .after(now.addingTimeInterval(15 * 60))))
    }
}

struct AltimeterWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: AltimeterWidgetEntry

    private var altitude: String {
        guard let meters = entry.snapshot.altitudeMeters else { return "—" }
        if entry.snapshot.usesFeet {
            return "\(Int((meters * 3.280_839_9).rounded())) фт"
        }
        return "\(Int(meters.rounded())) м"
    }

    private var distance: String {
        let meters = entry.snapshot.trackDistanceMeters
        if meters >= 1_000 { return String(format: "%.1f км", meters / 1_000) }
        return "\(Int(meters.rounded())) м"
    }

    private var healthSource: String {
        entry.snapshot.oxygenSource ?? entry.snapshot.heartRateSource ?? "Здоровье"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: family == .systemSmall ? 6 : 8) {
            HStack {
                Text("ERRARIUM™")
                    .font(.caption2.weight(.semibold))
                    .tracking(1.2)
                    .foregroundStyle(.secondary)
                Spacer()
                Image(systemName: "mountain.2.fill")
                    .foregroundStyle(.cyan)
            }

            Text(altitude)
                .font(.system(size: family == .systemSmall ? 28 : 34, weight: .bold, design: .rounded))
                .minimumScaleFactor(0.7)

            Label(
                entry.snapshot.trackIsRecording ? "Запись · \(distance)" : "Путь · \(distance)",
                systemImage: entry.snapshot.trackIsRecording ? "record.circle.fill" : "point.topleft.down.to.point.bottomright.curvepath"
            )
            .font(.caption.weight(.medium))
            .foregroundStyle(entry.snapshot.trackIsRecording ? .red : .secondary)

            HStack(spacing: 12) {
                Label(
                    entry.snapshot.heartRateBPM.map { "\(Int($0.rounded()))" } ?? "—",
                    systemImage: "heart.fill"
                )
                .foregroundStyle(.pink)
                Label(
                    entry.snapshot.oxygenPercent.map { "\(Int($0.rounded()))%" } ?? "—",
                    systemImage: "lungs.fill"
                )
                .foregroundStyle(.blue)
            }
            .font(.caption.weight(.semibold))
            .privacySensitive()

            if family != .systemSmall {
                HStack {
                    Text(healthSource)
                    Spacer()
                    Text(entry.snapshot.updatedAt, style: .time)
                }
                .font(.caption2)
                .foregroundStyle(.tertiary)
            }
        }
        .containerBackground(for: .widget) {
            LinearGradient(
                colors: [Color(red: 0.07, green: 0.11, blue: 0.15), Color(red: 0.10, green: 0.16, blue: 0.21)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }
}

struct AltimeterStatusWidget: Widget {
    let kind = "AltimeterStatusWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: AltimeterWidgetProvider()) { entry in
            AltimeterWidgetView(entry: entry)
        }
        .configurationDisplayName("Высота и здоровье")
        .description("Высота, записанный путь, пульс и SpO₂.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct AltimeterWidgetBundle: WidgetBundle {
    var body: some Widget {
        AltimeterStatusWidget()
    }
}
