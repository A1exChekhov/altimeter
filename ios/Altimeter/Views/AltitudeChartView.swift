import Charts
import SwiftUI

struct AltitudeChartView: View {
    let points: [ChartPoint]
    let unit: AltitudeUnit
    let accent: Color

    var body: some View {
        InstrumentCard {
            VStack(alignment: .leading, spacing: 14) {
                SectionHeading(icon: "chart.xyaxis.line", title: "Высота · последний час")
                if points.count < 2 {
                    ContentUnavailableView(
                        "Собираем данные",
                        systemImage: "waveform.path.ecg",
                        description: Text("График появится через несколько секунд.")
                    )
                    .frame(height: 150)
                } else {
                    Chart(points) { point in
                        AreaMark(
                            x: .value("Время", point.date),
                            y: .value("Высота", unit.value(fromMeters: point.altitude))
                        )
                        .foregroundStyle(
                            .linearGradient(
                                colors: [accent.opacity(0.35), accent.opacity(0.01)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        LineMark(
                            x: .value("Время", point.date),
                            y: .value("Высота", unit.value(fromMeters: point.altitude))
                        )
                        .foregroundStyle(accent)
                        .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
                    }
                    .chartXAxis {
                        AxisMarks(values: .automatic(desiredCount: 4)) { _ in
                            AxisGridLine().foregroundStyle(.white.opacity(0.06))
                            AxisValueLabel(format: .dateTime.hour().minute())
                                .foregroundStyle(.secondary)
                        }
                    }
                    .chartYAxis {
                        AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { value in
                            AxisGridLine().foregroundStyle(.white.opacity(0.08))
                            AxisValueLabel {
                                if let number = value.as(Double.self) {
                                    Text("\(Int(number.rounded()))")
                                }
                            }
                            .foregroundStyle(.secondary)
                        }
                    }
                    .frame(height: 185)
                }
            }
        }
    }
}

