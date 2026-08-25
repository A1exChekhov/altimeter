import Charts
import SwiftUI

private enum ChartMetric: String, CaseIterable, Hashable, Identifiable {
    case altitude
    case heart
    case oxygen
    case steps

    var id: String { rawValue }
}

private struct MetricLine: Identifiable {
    let id: ChartMetric
    let title: String
    let unit: String
    let color: Color
    let points: [VitalPoint]
    let decimals: Int
}

private struct MetricScale: Identifiable {
    var id: ChartMetric { line.id }
    let line: MetricLine
    let points: [VitalPoint]
    let min: Double
    let max: Double

    func normalized(_ value: Double) -> Double {
        (value - min) / Swift.max(max - min, 0.000_001)
    }

    func value(at fraction: Double) -> Double {
        min + (max - min) * fraction
    }
}

struct AltitudeChartView: View {
    let points: [ChartPoint]
    let vitals: VitalSample
    let unit: AltitudeUnit

    @State private var connected = Set(ChartMetric.allCases)
    @State private var window: TimeInterval = 60 * 60
    @State private var selectedDate: Date?

    private let altitudeColor = Color(red: 0.84, green: 0.65, blue: 0.34)
    private let heartColor = Color(red: 0.88, green: 0.43, blue: 0.43)
    private let oxygenColor = Color(red: 0.38, green: 0.66, blue: 0.85)
    private let stepsColor = Color(red: 0.45, green: 0.73, blue: 0.55)

    var body: some View {
        let lines = metricLines.filter { connected.contains($0.id) && !$0.points.isEmpty }
        let timeline = timeline(for: lines)
        let scales = makeScales(lines: lines, timeline: timeline)

        InstrumentCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeading(icon: "chart.xyaxis.line", title: "Динамика показателей")

                HStack(spacing: 6) {
                    ForEach(timeRanges, id: \.0) { range, title in
                        Button(title) { window = range }
                            .font(.caption.weight(.regular))
                            .buttonStyle(.bordered)
                            .tint(window == range ? .primary : .secondary)
                    }
                }

                HStack(spacing: 6) {
                    ForEach(metricLines) { line in
                        metricConnector(line)
                    }
                }

                if scales.isEmpty || timeline == nil {
                    ContentUnavailableView(
                        "Собираем данные",
                        systemImage: "waveform.path.ecg",
                        description: Text("График появится по мере поступления показателей.")
                    )
                    .frame(height: 170)
                } else if let timeline {
                    chart(scales: scales, timeline: timeline)
                }
            }
        }
    }

    private var metricLines: [MetricLine] {
        [
            MetricLine(
                id: .altitude,
                title: "Высота",
                unit: " (unit.symbol)",
                color: altitudeColor,
                points: points.map {
                    VitalPoint(date: $0.date, value: unit.value(fromMeters: $0.altitude))
                },
                decimals: 0
            ),
            MetricLine(
                id: .heart,
                title: "Пульс",
                unit: " уд/мин",
                color: heartColor,
                points: vitals.heartRateSeries,
                decimals: 0
            ),
            MetricLine(
                id: .oxygen,
                title: "SpO₂",
                unit: "%",
                color: oxygenColor,
                points: vitals.oxygenSeries,
                decimals: 1
            ),
            MetricLine(
                id: .steps,
                title: "Шаги",
                unit: "",
                color: stepsColor,
                points: vitals.stepsSeries,
                decimals: 0
            ),
        ]
    }

    private var timeRanges: [(TimeInterval, String)] {
        [(15 * 60, "15 мин"), (60 * 60, "1 ч"), (3 * 60 * 60, "3 ч"), (6 * 60 * 60, "6 ч")]
    }

    private func timeline(for lines: [MetricLine]) -> (start: Date, end: Date)? {
        let dates = lines.flatMap { $0.points.map(\.date) }
        guard let earliest = dates.min(), let latest = dates.max() else { return nil }
        if latest.timeIntervalSince(earliest) >= window {
            return (latest.addingTimeInterval(-window), latest)
        }
        // Неполное окно закреплено слева и постепенно заполняется новыми точками.
        return (earliest, earliest.addingTimeInterval(window))
    }

    private func makeScales(
        lines: [MetricLine],
        timeline: (start: Date, end: Date)?
    ) -> [MetricScale] {
        guard let timeline else { return [] }
        return lines.compactMap { line in
            let visible = line.points.filter { $0.date >= timeline.start && $0.date <= timeline.end }
            guard let rawMin = visible.map(\.value).min(),
                  let rawMax = visible.map(\.value).max() else { return nil }
            let basePadding: Double = switch line.id {
            case .altitude: 2
            case .heart: 5
            case .oxygen: 1
            case .steps: 1
            }
            let padding = max((rawMax - rawMin) * 0.08, basePadding)
            return MetricScale(
                line: line,
                points: visible,
                min: rawMin - padding,
                max: rawMax + padding
            )
        }
    }

    @ViewBuilder
    private func chart(
        scales: [MetricScale],
        timeline: (start: Date, end: Date)
    ) -> some View {
        Chart {
            ForEach(scales) { scale in
                ForEach(scale.points) { point in
                    LineMark(
                        x: .value("Время", point.date),
                        y: .value(scale.line.title, scale.normalized(point.value)),
                        series: .value("Показатель", scale.line.id.rawValue)
                    )
                    .foregroundStyle(scale.line.color)
                    .lineStyle(StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                }
            }

            if let selectedDate {
                RuleMark(x: .value("Выбранное время", selectedDate))
                    .foregroundStyle(.secondary.opacity(0.7))
                    .annotation(position: .top, alignment: .leading, spacing: 5) {
                        cursorValues(at: selectedDate, scales: scales)
                    }
            }
        }
        .chartXScale(domain: timeline.start...timeline.end)
        .chartYScale(domain: 0...1)
        .chartXAxis {
            AxisMarks(values: .automatic(desiredCount: 4)) { _ in
                AxisGridLine().foregroundStyle(.secondary.opacity(0.13))
                AxisValueLabel(format: .dateTime.hour().minute())
                    .foregroundStyle(.secondary)
            }
        }
        .chartYAxis {
            AxisMarks(position: .leading, values: [0.0, 0.25, 0.5, 0.75, 1.0]) { value in
                AxisGridLine().foregroundStyle(.secondary.opacity(0.13))
                AxisValueLabel {
                    if let fraction = value.as(Double.self) {
                        VStack(alignment: .trailing, spacing: 0) {
                            ForEach(scales) { scale in
                                Text(format(scale.value(at: fraction), decimals: scale.line.decimals))
                                    .font(.system(size: 8, weight: .regular, design: .monospaced))
                                    .foregroundStyle(scale.line.color)
                            }
                        }
                    }
                }
            }
        }
        .chartXSelection(value: $selectedDate)
        .frame(height: 250)
    }

    private func metricConnector(_ line: MetricLine) -> some View {
        let isConnected = connected.contains(line.id)
        return Button {
            if isConnected { connected.remove(line.id) } else { connected.insert(line.id) }
        } label: {
            HStack(spacing: 5) {
                Circle()
                    .fill(isConnected ? line.color : Color.secondary)
                    .frame(width: 7, height: 7)
                Text(line.title)
                    .font(.caption.weight(.regular))
            }
            .foregroundStyle(isConnected ? line.color : .secondary)
            .padding(.horizontal, 9)
            .padding(.vertical, 6)
            .background(Color(uiColor: .tertiarySystemFill), in: Capsule())
            .overlay {
                Capsule().stroke(isConnected ? line.color.opacity(0.5) : .clear, lineWidth: 0.5)
            }
        }
        .buttonStyle(.plain)
    }

    private func cursorValues(at date: Date, scales: [MetricScale]) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(date, format: .dateTime.hour().minute())
                .font(.caption2.monospacedDigit())
                .foregroundStyle(.secondary)
            ForEach(scales) { scale in
                if let point = scale.points.min(by: {
                    abs($0.date.timeIntervalSince(date)) < abs($1.date.timeIntervalSince(date))
                }) {
                    Text("\(format(point.value, decimals: scale.line.decimals))\(scale.line.unit)")
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(scale.line.color)
                }
            }
        }
        .padding(7)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8))
    }

    private func format(_ value: Double, decimals: Int) -> String {
        if decimals == 0 { return String(Int(value.rounded())) }
        return String(format: "%.*f", decimals, value)
    }
}
