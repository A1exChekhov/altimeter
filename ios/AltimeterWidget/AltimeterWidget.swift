import Foundation
import SwiftUI
import WidgetKit

private enum ErrariumWidgetKind {
    static let altitude = "ErrariumAltitudeWidget"
    static let health = "ErrariumHealthWidget"
    static let track = "ErrariumTrackWidget"
    static let expedition = "AltimeterStatusWidget"
}

struct AltimeterWidgetEntry: TimelineEntry {
    let date: Date
    let snapshot: AltimeterWidgetSnapshot
}

struct AltimeterWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> AltimeterWidgetEntry {
        AltimeterWidgetEntry(
            date: Date(),
            snapshot: AltimeterWidgetSnapshot(
                altitudeMeters: 4_206,
                pressureHPA: 627.4,
                latitude: 31.0675,
                longitude: 81.3119,
                trackIsRecording: true,
                trackDistanceMeters: 7_420,
                trackPointCount: 814,
                trackAscentMeters: 620,
                trackDescentMeters: 410,
                trackMovingTime: 13_680,
                trackStoppedTime: 1_440,
                heartRateBPM: 92,
                oxygenPercent: 96,
                stepsToday: 8_640,
                activeCaloriesToday: 610,
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

private extension AltimeterWidgetSnapshot {
    var altitudeValueText: String {
        guard let altitudeMeters else { return "—" }
        let value = usesFeet ? altitudeMeters * 3.280_839_9 : altitudeMeters
        return Int(value.rounded()).formatted(.number.grouping(.automatic))
    }

    var altitudeUnitText: String { altitudeMeters == nil ? "" : (usesFeet ? "фт" : "м") }
    var pressureText: String { pressureHPA.map { String(format: "%.1f гПа", $0) } ?? "— гПа" }
    var coordinateText: String {
        guard let latitude, let longitude else { return "—, —" }
        return String(format: "%.5f, %.5f", latitude, longitude)
    }
    var mapURL: URL? {
        guard let latitude, let longitude else { return nil }
        return URL(string: "https://maps.apple.com/?ll=\(latitude),\(longitude)")
    }
    var distanceText: String {
        trackDistanceMeters >= 1_000
            ? String(format: "%.1f км", trackDistanceMeters / 1_000)
            : "\(Int(trackDistanceMeters.rounded())) м"
    }
    var heartText: String { heartRateBPM.map { Int($0.rounded()).formatted() } ?? "—" }
    var oxygenText: String { oxygenPercent.map { "\(Int($0.rounded()))%" } ?? "—" }
    var stepsText: String {
        stepsToday.map { Int($0.rounded()).formatted(.number.grouping(.automatic)) } ?? "—"
    }
    var dailyCaloriesText: String {
        activeCaloriesToday.map { Int($0.rounded()).formatted(.number.grouping(.automatic)) } ?? "—"
    }
    var trackCaloriesText: String {
        guard trackPointCount > 1 else { return "—" }
        let estimate = trackDistanceMeters / 1_000 * 50 + trackAscentMeters * 0.1
        return "≈\(Int(estimate.rounded()))"
    }
    var movingTimeText: String {
        let totalMinutes = max(0, Int(trackMovingTime / 60))
        return totalMinutes >= 60
            ? String(format: "%d:%02d", totalMinutes / 60, totalMinutes % 60)
            : "\(totalMinutes) мин"
    }
}

private struct AltitudeValue: View {
    let snapshot: AltimeterWidgetSnapshot
    let size: CGFloat

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(snapshot.altitudeValueText)
                .font(.system(size: size, weight: .thin))
                .monospacedDigit()
                .minimumScaleFactor(0.5)
                .lineLimit(1)
            Text(snapshot.altitudeUnitText)
                .font(.system(size: size * 0.28, weight: .light))
                .foregroundStyle(.secondary)
        }
    }
}

private struct InlineMetric: View {
    let emoji: String
    let value: String
    var size: CGFloat = 18

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 3) {
            Text(emoji).font(.system(size: size * 0.82))
            Text(value)
                .font(.system(size: size, weight: .light))
                .monospacedDigit()
                .foregroundStyle(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
        }
    }
}

private extension View {
    func neutralWidgetBackground() -> some View {
        containerBackground(for: .widget) {
            Color(uiColor: .secondarySystemBackground)
        }
    }
}

struct AltitudeWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: AltimeterWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(alignment: .firstTextBaseline) {
                AltitudeValue(snapshot: entry.snapshot, size: family == .systemSmall ? 43 : 48)
                Spacer(minLength: 5)
                Text(entry.snapshot.pressureText)
                    .font(.system(size: 12, weight: .light))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            Text(entry.snapshot.coordinateText)
                .font(.system(size: 11, weight: .light, design: .monospaced))
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .widgetURL(entry.snapshot.mapURL)
        .neutralWidgetBackground()
    }
}

struct HealthWidgetView: View {
    let entry: AltimeterWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 13) {
                InlineMetric(emoji: "❤️", value: entry.snapshot.heartText, size: 22)
                InlineMetric(emoji: "O₂", value: entry.snapshot.oxygenText, size: 22)
            }
            HStack(spacing: 13) {
                InlineMetric(emoji: "👣", value: entry.snapshot.stepsText, size: 19)
                InlineMetric(emoji: "🔥", value: entry.snapshot.dailyCaloriesText, size: 19)
            }
        }
        .privacySensitive()
        .neutralWidgetBackground()
    }
}

struct TrackWidgetView: View {
    let entry: AltimeterWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack(spacing: 12) {
                InlineMetric(emoji: "⏱", value: entry.snapshot.movingTimeText, size: 19)
                InlineMetric(emoji: "↔", value: entry.snapshot.distanceText, size: 19)
            }
            HStack(spacing: 10) {
                InlineMetric(emoji: "↗", value: "\(Int(entry.snapshot.trackAscentMeters.rounded())) м", size: 15)
                InlineMetric(emoji: "↘", value: "\(Int(entry.snapshot.trackDescentMeters.rounded())) м", size: 15)
                InlineMetric(emoji: "🔥", value: entry.snapshot.trackCaloriesText, size: 15)
            }
        }
        .neutralWidgetBackground()
    }
}

struct ExpeditionWidgetView: View {
    let entry: AltimeterWidgetEntry

    var body: some View {
        HStack(spacing: 18) {
            AltitudeValue(snapshot: entry.snapshot, size: 44)
                .frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 11) {
                    InlineMetric(emoji: "❤️", value: entry.snapshot.heartText, size: 17)
                    InlineMetric(emoji: "O₂", value: entry.snapshot.oxygenText, size: 17)
                    InlineMetric(emoji: "👣", value: entry.snapshot.stepsText, size: 17)
                }
                HStack(spacing: 11) {
                    InlineMetric(emoji: "↔", value: entry.snapshot.distanceText, size: 14)
                    InlineMetric(emoji: "↗", value: "\(Int(entry.snapshot.trackAscentMeters.rounded())) м", size: 14)
                    InlineMetric(emoji: "↘", value: "\(Int(entry.snapshot.trackDescentMeters.rounded())) м", size: 14)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .privacySensitive()
        .neutralWidgetBackground()
    }
}

struct AltitudeStatusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: ErrariumWidgetKind.altitude, provider: AltimeterWidgetProvider()) {
            AltitudeWidgetView(entry: $0)
        }
        .configurationDisplayName("Высота")
        .description("Высота, давление и координаты.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct HealthStatusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: ErrariumWidgetKind.health, provider: AltimeterWidgetProvider()) {
            HealthWidgetView(entry: $0)
        }
        .configurationDisplayName("Здоровье")
        .description("Пульс, SpO₂, шаги и активные калории.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct TrackStatusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: ErrariumWidgetKind.track, provider: AltimeterWidgetProvider()) {
            TrackWidgetView(entry: $0)
        }
        .configurationDisplayName("Поход")
        .description("Движение, путь, подъём, спуск и калории трека.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct ExpeditionStatusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: ErrariumWidgetKind.expedition, provider: AltimeterWidgetProvider()) {
            ExpeditionWidgetView(entry: $0)
        }
        .configurationDisplayName("Экспедиция")
        .description("Высота, здоровье и текущий маршрут.")
        .supportedFamilies([.systemMedium])
    }
}

@main
struct AltimeterWidgetBundle: WidgetBundle {
    var body: some Widget {
        AltitudeStatusWidget()
        HealthStatusWidget()
        TrackStatusWidget()
        ExpeditionStatusWidget()
    }
}
