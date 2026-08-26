import Charts
import SwiftUI

private enum ChartMetric: String, CaseIterable, Hashable, Identifiable {
    case altitude
    case heart
    case oxygen
    case steps

    var id: String { rawValue }
}

enum ChartTimeRange: String, CaseIterable, Identifiable {
    case minutes15
    case hour
    case hours3
    case hours6
    case hours12
    case today

    var id: Self { self }

    var title: String {
        switch self {
        case .minutes15: L10n.string("chart.range.15m")
        case .hour: L10n.string("chart.range.1h")
        case .hours3: L10n.string("chart.range.3h")
        case .hours6: L10n.string("chart.range.6h")
        case .hours12: L10n.string("chart.range.12h")
        case .today: L10n.string("chart.range.today")
        }
    }

    func startDate(reference: Date) -> Date {
        switch self {
        case .minutes15: reference.addingTimeInterval(-15 * 60)
        case .hour: reference.addingTimeInterval(-60 * 60)
        case .hours3: reference.addingTimeInterval(-3 * 60 * 60)
        case .hours6: reference.addingTimeInterval(-6 * 60 * 60)
        case .hours12: reference.addingTimeInterval(-12 * 60 * 60)
        case .today: Calendar.current.startOfDay(for: reference)
        }
    }
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
    @Binding var selectedRange: ChartTimeRange

    @State private var connected = Set(ChartMetric.allCases)
    @State private var selectedDate: Date?

    private let altitudeColor = Color(red: 0.84, green: 0.65, blue: 0.34)
    private let heartColor = Color(red: 0.88, green: 0.43, blue: 0.43)
    private let oxygenColor = Color(red: 0.38, green: 0.66, blue: 0.85)
    private let stepsColor = Color(red: 0.45, green: 0.73, blue: 0.55)

    var body: some View {
        let allLines = metricLines
        let lines = allLines.filter { connected.contains($0.id) && !$0.points.isEmpty }
        let timeline = timeline(for: lines)
        let scales = makeScales(lines: lines, timeline: timeline)

        InstrumentCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeading(icon: "chart.xyaxis.line", title: L10n.string("chart.trends"))

                ScrollView(.horizontal) {
                    HStack(spacing: 6) {
                        ForEach(ChartTimeRange.allCases) { range in
                            Button(range.title) { selectedRange = range }
                                .font(.caption.weight(.regular))
                                .buttonStyle(.bordered)
                                .tint(selectedRange == range ? .primary : .secondary)
                        }
                    }
                }
                .scrollIndicators(.hidden)

                HStack(spacing: 6) {
                    ForEach(allLines) { line in
                        metricConnector(line)
                    }
                }

                currentValueStrip(lines: allLines)

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
                title: L10n.string("metric.altitude"),
                unit: " \(unit.symbol)",
                color: altitudeColor,
                points: points.map {
                    VitalPoint(date: $0.date, value: unit.value(fromMeters: $0.altitude))
                },
                decimals: 0
            ),
            MetricLine(
                id: .heart,
                title: L10n.string("metric.heart"),
                unit: " \(L10n.string("unit.bpm"))",
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
                title: L10n.string("metric.steps"),
                unit: "",
                color: stepsColor,
                points: vitals.stepsSeries,
                decimals: 0
            ),
        ]
    }

    private func timeline(for lines: [MetricLine]) -> (start: Date, end: Date)? {
        guard lines.contains(where: { !$0.points.isEmpty }) else { return nil }
        let end = Date()
        return (selectedRange.startDate(reference: end), end)
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
                if let selectedDate,
                   let point = nearestPoint(to: selectedDate, in: scale.points) {
                    PointMark(
                        x: .value("Выбранное время", point.date),
                        y: .value(scale.line.title, scale.normalized(point.value))
                    )
                    .foregroundStyle(scale.line.color)
                    .symbolSize(46)
                }
            }

            if let selectedDate {
                RuleMark(x: .value("Выбранное время", selectedDate))
                    .foregroundStyle(.secondary.opacity(0.82))
                    .lineStyle(StrokeStyle(lineWidth: 1.2))
                    .annotation(position: .top, alignment: .center, spacing: 5) {
                        cursorValues(at: selectedDate, scales: scales)
                    }
                RuleMark(x: .value("Выбранное время", selectedDate))
                    .foregroundStyle(.clear)
                    .annotation(position: .bottom, alignment: .center, spacing: 4) {
                        cursorTime(at: selectedDate)
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

    private func currentValueStrip(lines: [MetricLine]) -> some View {
        HStack(spacing: 8) {
            Spacer(minLength: 0)
            if let altitude = lines.first(where: { $0.id == .altitude }) {
                currentValueBadge(altitude)
            }
            if let heart = lines.first(where: { $0.id == .heart }) {
                currentValueBadge(heart)
            }
        }
    }

    private func currentValueBadge(_ line: MetricLine) -> some View {
        let point = currentPoint(for: line)
        return VStack(alignment: .trailing, spacing: 2) {
            Text(line.title.uppercased())
                .font(.system(size: 9, weight: .bold))
                .tracking(0.7)
                .foregroundStyle(line.color)
            Text(point.map { "\(format($0.value, decimals: line.decimals))\(line.unit)" } ?? "—")
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .monospacedDigit()
                .foregroundStyle(line.color)
        }
        .padding(.horizontal, 11)
        .padding(.vertical, 7)
        .background(line.color.opacity(0.16), in: RoundedRectangle(cornerRadius: 11))
        .overlay {
            RoundedRectangle(cornerRadius: 11)
                .stroke(line.color.opacity(0.62), lineWidth: 1)
        }
    }

    private func currentPoint(for line: MetricLine) -> VitalPoint? {
        let end = Date()
        let start = selectedRange.startDate(reference: end)
        let visible = line.points.filter { $0.date >= start && $0.date <= end }
        guard !visible.isEmpty else { return nil }
        if let selectedDate {
            return visible.min {
                abs($0.date.timeIntervalSince(selectedDate))
                    < abs($1.date.timeIntervalSince(selectedDate))
            }
        }
        return visible.max { $0.date < $1.date }
    }

    private func cursorValues(at date: Date, scales: [MetricScale]) -> some View {
        HStack(spacing: 8) {
            ForEach(scales) { scale in
                if let point = nearestPoint(to: date, in: scale.points) {
                    Text("\(format(point.value, decimals: scale.line.decimals))\(scale.line.unit)")
                        .font(.system(size: 13, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(scale.line.color)
                }
            }
        }
        .padding(.horizontal, 9)
        .padding(.vertical, 7)
        .background(Color(uiColor: .systemBackground).opacity(0.97), in: RoundedRectangle(cornerRadius: 9))
        .overlay {
            RoundedRectangle(cornerRadius: 9)
                .stroke(Color.secondary.opacity(0.48), lineWidth: 1)
        }
    }

    private func cursorTime(at date: Date) -> some View {
        Text(date, format: .dateTime.hour().minute())
            .font(.caption.weight(.bold).monospacedDigit())
            .foregroundStyle(.primary)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color(uiColor: .systemBackground).opacity(0.97), in: Capsule())
            .overlay { Capsule().stroke(Color.secondary.opacity(0.4), lineWidth: 1) }
    }

    private func nearestPoint(to date: Date, in points: [VitalPoint]) -> VitalPoint? {
        points.min {
            abs($0.date.timeIntervalSince(date)) < abs($1.date.timeIntervalSince(date))
        }
    }

    private func format(_ value: Double, decimals: Int) -> String {
        if decimals == 0 { return String(Int(value.rounded())) }
        return String(format: "%.*f", decimals, value)
    }
}
