import Foundation
import SwiftUI
import WidgetKit

private enum ErrariumWidgetKind {
    static let altitude = "ErrariumAltitudeWidget"
    static let health = "ErrariumHealthWidget"
    static let combined = "AltimeterStatusWidget"
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
                altitudeMeters: 1_248,
                trackIsRecording: true,
                trackDistanceMeters: 7_420,
                trackPointCount: 814,
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

    var altitudeUnitText: String {
        guard altitudeMeters != nil else { return "" }
        return usesFeet ? "фт" : "м"
    }

    var distanceText: String {
        if trackDistanceMeters >= 1_000 {
            return String(format: "%.1f км", trackDistanceMeters / 1_000)
        }
        return "\(Int(trackDistanceMeters.rounded())) м"
    }

    var trackText: String {
        trackIsRecording ? "Запись · \(distanceText)" : "Путь · \(distanceText)"
    }

    var heartText: String {
        heartRateBPM.map { Int($0.rounded()).formatted() } ?? "—"
    }

    var oxygenText: String {
        oxygenPercent.map { "\(Int($0.rounded()))%" } ?? "—"
    }

    var stepsText: String {
        stepsToday.map { Int($0.rounded()).formatted(.number.grouping(.automatic)) } ?? "—"
    }

    var caloriesText: String {
        activeCaloriesToday.map { Int($0.rounded()).formatted(.number.grouping(.automatic)) } ?? "—"
    }
}

private struct WidgetHeader: View {
    let symbol: String

    var body: some View {
        HStack(spacing: 6) {
            Text("ERRARIUM™")
                .font(.system(size: 10, weight: .regular))
                .tracking(1.45)
                .foregroundStyle(.secondary)
            Spacer(minLength: 4)
            Image(systemName: symbol)
                .font(.system(size: 12, weight: .regular))
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(.cyan)
        }
    }
}

private struct AltitudeValue: View {
    let snapshot: AltimeterWidgetSnapshot
    let size: CGFloat

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(snapshot.altitudeValueText)
                .font(.system(size: size, weight: .light))
                .monospacedDigit()
                .minimumScaleFactor(0.55)
                .lineLimit(1)
            if !snapshot.altitudeUnitText.isEmpty {
                Text(snapshot.altitudeUnitText)
                    .font(.system(size: size * 0.31, weight: .regular))
                    .foregroundStyle(.secondary)
            }
        }
    }
}

private struct MetricColumn: View {
    let title: String
    let value: String
    let color: Color
    var valueSize: CGFloat = 27

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(size: 10, weight: .regular))
                .foregroundStyle(.secondary)
                .lineLimit(1)
            Text(value)
                .font(.system(size: valueSize, weight: .light))
                .monospacedDigit()
                .foregroundStyle(color)
                .minimumScaleFactor(0.62)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct CompactMetric: View {
    let symbol: String
    let value: String
    let color: Color

    var body: some View {
        Label(value, systemImage: symbol)
            .font(.system(size: 13, weight: .regular))
            .monospacedDigit()
            .foregroundStyle(color)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
    }
}

private extension View {
    func errariumWidgetBackground() -> some View {
        containerBackground(for: .widget) {
            LinearGradient(
                colors: [
                    Color(red: 0.055, green: 0.085, blue: 0.13),
                    Color(red: 0.075, green: 0.13, blue: 0.19)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }
}

struct AltitudeWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: AltimeterWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            WidgetHeader(symbol: "mountain.2")
            Spacer(minLength: 4)
            AltitudeValue(
                snapshot: entry.snapshot,
                size: family == .systemSmall ? 48 : 52
            )
            Spacer(minLength: 5)
            HStack(spacing: 10) {
                if family == .systemMedium {
                    Label(
                        entry.snapshot.trackText,
                        systemImage: entry.snapshot.trackIsRecording ? "record.circle.fill" : "figure.hiking"
                    )
                    .foregroundStyle(entry.snapshot.trackIsRecording ? .red : .secondary)
                    Spacer(minLength: 4)
                }
                CompactMetric(symbol: "heart.fill", value: entry.snapshot.heartText, color: .pink)
                CompactMetric(symbol: "figure.walk", value: entry.snapshot.stepsText, color: .green)
            }
            .font(.caption)
            .privacySensitive()
        }
        .errariumWidgetBackground()
    }
}

struct HealthWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: AltimeterWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            WidgetHeader(symbol: "waveform.path.ecg")
            Spacer(minLength: 7)
            if family == .systemSmall {
                HStack(spacing: 12) {
                    MetricColumn(title: "Пульс", value: entry.snapshot.heartText, color: .pink, valueSize: 28)
                    MetricColumn(title: "SpO₂", value: entry.snapshot.oxygenText, color: .cyan, valueSize: 28)
                }
                Spacer(minLength: 5)
                HStack(spacing: 12) {
                    MetricColumn(title: "Шаги", value: entry.snapshot.stepsText, color: .green, valueSize: 22)
                    MetricColumn(title: "Ккал", value: entry.snapshot.caloriesText, color: .orange, valueSize: 22)
                }
            } else {
                HStack(spacing: 13) {
                    MetricColumn(title: "Пульс", value: entry.snapshot.heartText, color: .pink, valueSize: 29)
                    MetricColumn(title: "SpO₂", value: entry.snapshot.oxygenText, color: .cyan, valueSize: 29)
                    MetricColumn(title: "Шаги", value: entry.snapshot.stepsText, color: .green, valueSize: 25)
                    MetricColumn(title: "Ккал", value: entry.snapshot.caloriesText, color: .orange, valueSize: 25)
                }
                Spacer(minLength: 2)
            }
        }
        .privacySensitive()
        .errariumWidgetBackground()
    }
}

struct CombinedWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: AltimeterWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            WidgetHeader(symbol: "figure.hiking")
            Spacer(minLength: 5)
            if family == .systemSmall {
                AltitudeValue(snapshot: entry.snapshot, size: 43)
                Spacer(minLength: 4)
                Text(entry.snapshot.trackText)
                    .font(.system(size: 11, weight: .regular))
                    .foregroundStyle(entry.snapshot.trackIsRecording ? .red : .secondary)
                    .lineLimit(1)
                Spacer(minLength: 4)
                HStack(spacing: 9) {
                    CompactMetric(symbol: "heart.fill", value: entry.snapshot.heartText, color: .pink)
                    CompactMetric(symbol: "lungs.fill", value: entry.snapshot.oxygenText, color: .cyan)
                }
            } else {
                HStack(alignment: .center, spacing: 18) {
                    VStack(alignment: .leading, spacing: 5) {
                        AltitudeValue(snapshot: entry.snapshot, size: 47)
                        Text(entry.snapshot.trackText)
                            .font(.system(size: 11, weight: .regular))
                            .foregroundStyle(entry.snapshot.trackIsRecording ? .red : .secondary)
                            .lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    HStack(spacing: 13) {
                        MetricColumn(title: "Пульс", value: entry.snapshot.heartText, color: .pink, valueSize: 23)
                        MetricColumn(title: "SpO₂", value: entry.snapshot.oxygenText, color: .cyan, valueSize: 23)
                        MetricColumn(title: "Шаги", value: entry.snapshot.stepsText, color: .green, valueSize: 21)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .privacySensitive()
        .errariumWidgetBackground()
    }
}

struct AltitudeStatusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: ErrariumWidgetKind.altitude, provider: AltimeterWidgetProvider()) { entry in
            AltitudeWidgetView(entry: entry)
        }
        .configurationDisplayName("Высота")
        .description("Крупная высота, пульс и шаги.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct HealthStatusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: ErrariumWidgetKind.health, provider: AltimeterWidgetProvider()) { entry in
            HealthWidgetView(entry: entry)
        }
        .configurationDisplayName("Здоровье")
        .description("Пульс, SpO₂, шаги и активные калории из Apple Health.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct AltimeterStatusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: ErrariumWidgetKind.combined, provider: AltimeterWidgetProvider()) { entry in
            CombinedWidgetView(entry: entry)
        }
        .configurationDisplayName("Высота и здоровье")
        .description("Высота, путь и основные показатели здоровья.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

@main
struct AltimeterWidgetBundle: WidgetBundle {
    var body: some Widget {
        AltitudeStatusWidget()
        HealthStatusWidget()
        AltimeterStatusWidget()
    }
}
