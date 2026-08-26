import SwiftUI

private struct AltitudePeriodStatistics {
    let ascent: Double
    let descent: Double
    let minimum: Double
    let maximum: Double
}

struct AltimeterScreen: View {
    let page: AppTab
    @EnvironmentObject private var model: AppModel
    @State private var showsSettings = false
    @State private var showsTracks = false
    @State private var showsHealthSources = false
    @State private var chartRange: ChartTimeRange = .hour

    private var state: AltimeterState { model.state }
    private var accent: Color { .primary }

    var body: some View {
        NavigationStack {
            ZStack {
                background
                ScrollView {
                    LazyVStack(spacing: 14) {
                        pageContent
                    }
                    .padding(.horizontal, 14)
                    .padding(.bottom, 30)
                }
                .scrollIndicators(.hidden)
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Text(page.title)
                        .font(.headline.weight(.regular))
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showsSettings = true } label: {
                        Image(systemName: "gearshape.fill")
                            .frame(width: 34, height: 34)
                            .background(.thinMaterial, in: Circle())
                    }
                    .accessibilityLabel("Настройки")
                }
            }
            .toolbarBackground(.hidden, for: .navigationBar)
        }
        .sheet(isPresented: $showsSettings) { SettingsView() }
        .sheet(isPresented: $showsTracks) { SavedTracksView() }
        .sheet(isPresented: $showsHealthSources) { HealthSourcesView() }
        .task { model.start() }
        .refreshable {
            if model.health.hasRequestedAccess { await model.health.refresh() }
        }
    }

    @ViewBuilder
    private var pageContent: some View {
        switch page {
        case .home:
            hero
            permissionCard
            MapCardView(
                state: state,
                topographic: $model.useTopographicMap,
                sourceMode: $model.mapSourceMode
            )
            healthCard
            adviceSection
        case .track:
            trackCard
        case .data:
            healthCard
            AltitudeChartView(
                points: state.history,
                vitals: model.vitals,
                unit: model.unit,
                selectedRange: $chartRange
            )
            statisticsCard
            adviceSection
            watchCard
            footer
        case .map:
            EmptyView()
        }
    }

    private var background: some View {
        Color(uiColor: .systemBackground)
        .overlay(alignment: .topTrailing) {
            Circle()
                .fill(Color.primary.opacity(0.055))
                .frame(width: 290, height: 290)
                .blur(radius: 70)
                .offset(x: 120, y: -90)
        }
        .ignoresSafeArea()
    }

    private var hero: some View {
        VStack(spacing: 13) {
            Spacer(minLength: 6)
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(AltimeterFormat.altitude(state.altitude, unit: model.unit))
                    .font(.system(size: 82, weight: .thin, design: .default))
                    .monospacedDigit()
                    .minimumScaleFactor(0.55)
                    .foregroundStyle(accent)
                Text(model.unit.symbol)
                    .font(.title2.weight(.medium))
                    .foregroundStyle(.secondary)
            }

            if state.isCalibrating {
                Label("Калибровка по GPS…", systemImage: "scope")
                    .font(.subheadline).foregroundStyle(.secondary)
            } else if let accuracy = state.altitudeAccuracy {
                Text("±\(Int(model.unit.value(fromMeters: accuracy).rounded())) \(model.unit.symbol)")
                    .font(.subheadline.monospacedDigit())
                    .foregroundStyle(.secondary)
            } else if state.altitude == nil {
                Text("Ищем спутники и данные барометра…")
                    .font(.subheadline).foregroundStyle(.secondary)
            }

            Text(state.placeName ?? "Определяем место…")
                .font(.headline)
            if let coordinate = state.coordinate {
                Text(String(format: "%.5f, %.5f", coordinate.latitude, coordinate.longitude))
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
            }

            HStack(spacing: 8) {
                StatusChip(
                    icon: state.hasFix ? "location.fill" : "location.magnifyingglass",
                    text: state.hasFix ? "GPS" : "поиск GPS",
                    color: state.hasFix ? .green : .orange
                )
                StatusChip(
                    icon: "gauge.with.dots.needle.50percent",
                    text: state.pressureHPA.map { L10n.string("format.pressure", $0) }
                        ?? (state.hasBarometer ? "барометр" : "без барометра"),
                    color: .secondary
                )
                StatusChip(icon: "scope", text: model.calibrationMode.title, color: accent)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
    }

    @ViewBuilder
    private var permissionCard: some View {
        if state.authorization != .authorizedAlways && state.authorization != .authorizedWhenInUse {
            InstrumentCard {
                HStack(spacing: 13) {
                    Image(systemName: "location.circle.fill")
                        .font(.largeTitle).foregroundStyle(.orange)
                    VStack(alignment: .leading, spacing: 5) {
                        Text("Нужен доступ к геопозиции").font(.headline)
                        Text("GPS задаёт абсолютную высоту и позволяет записывать маршрут.")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Button(state.authorization == .denied ? "Настройки" : "Разрешить") {
                        if state.authorization == .denied || state.authorization == .restricted {
                            UIApplication.shared.open(URL(string: UIApplication.openSettingsURLString)!)
                        } else {
                            model.engine.requestLocationAccess()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.orange)
                }
            }
        }
    }

    private var statisticsCard: some View {
        let period = altitudeStatistics
        return InstrumentCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeading(
                    icon: "mountain.2.fill",
                    title: L10n.string("section.metrics", chartRange.title)
                )
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 9) {
                    MetricCell(
                        title: "Скорость",
                        value: AltimeterFormat.speed(state.verticalSpeedMetersPerMinute, unit: model.unit),
                        unit: model.unit.speedSymbol
                    )
                    MetricCell(
                        title: "Давление",
                        value: state.pressureHPA.map { String(format: "%.1f", $0) } ?? "—",
                        unit: "гПа"
                    )
                    MetricCell(title: "Набор", value: AltimeterFormat.altitude(period?.ascent, unit: model.unit), unit: model.unit.symbol)
                    MetricCell(title: "Спуск", value: AltimeterFormat.altitude(period?.descent, unit: model.unit), unit: model.unit.symbol)
                    MetricCell(title: "Минимум", value: AltimeterFormat.altitude(period?.minimum, unit: model.unit), unit: model.unit.symbol)
                    MetricCell(title: "Максимум", value: AltimeterFormat.altitude(period?.maximum, unit: model.unit), unit: model.unit.symbol)
                }
            }
        }
    }

    private var altitudeStatistics: AltitudePeriodStatistics? {
        let now = Date()
        let visible = state.history.filter {
            $0.date >= chartRange.startDate(reference: now) && $0.date <= now
        }
        guard let first = visible.first else { return nil }

        var anchor = first.altitude
        var ascent = 0.0
        var descent = 0.0
        for point in visible.dropFirst() {
            let delta = point.altitude - anchor
            if abs(delta) >= 3 {
                if delta > 0 { ascent += delta } else { descent -= delta }
                anchor = point.altitude
            }
        }
        return AltitudePeriodStatistics(
            ascent: ascent,
            descent: descent,
            minimum: visible.map(\.altitude).min() ?? first.altitude,
            maximum: visible.map(\.altitude).max() ?? first.altitude
        )
    }

    private var trackCard: some View {
        let track = state.track
        let movingSpeed = track.movingTime > 0
            ? track.distanceMeters / track.movingTime * 3.6
            : nil
        let estimatedCalories = track.distanceMeters / 1_000 * 50 + track.ascentMeters * 0.1
        return InstrumentCard {
            VStack(alignment: .leading, spacing: 13) {
                SectionHeading(icon: "figure.hiking", title: L10n.string("section.track"))
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 9) {
                    MetricCell(title: "Дистанция", value: AltimeterFormat.distance(track.distanceMeters), unit: "")
                    MetricCell(title: "В движении", value: AltimeterFormat.duration(track.movingTime), unit: "")
                    MetricCell(title: "Остановки", value: AltimeterFormat.duration(track.stoppedTime), unit: "")
                    MetricCell(
                        title: "Средняя в движении",
                        value: movingSpeed.map { String(format: "%.1f", $0) } ?? "—",
                        unit: "км/ч"
                    )
                    MetricCell(title: "Подъём", value: "\(Int(track.ascentMeters.rounded()))", unit: "м")
                    MetricCell(title: "Спуск", value: "\(Int(track.descentMeters.rounded()))", unit: "м")
                }
                Text(L10n.string(
                    "track.calories.points",
                    Int(estimatedCalories.rounded()),
                    track.pointCount
                ))
                    .font(.caption.weight(.regular))
                    .foregroundStyle(.secondary)

                if model.vitals.heartRateSource != nil || model.vitals.oxygenSource != nil {
                    VStack(alignment: .leading, spacing: 4) {
                        if let source = model.vitals.heartRateSource {
                            Label(L10n.string("health.source.heart", source), systemImage: "waveform.path.ecg")
                        }
                        if let source = model.vitals.oxygenSource {
                            Label("SpO₂: \(source)", systemImage: "lungs.fill")
                        }
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }

                Button {
                    if state.track.isRecording {
                        model.engine.stopRecording()
                    } else if state.authorization == .authorizedAlways || state.authorization == .authorizedWhenInUse {
                        model.engine.startRecording()
                    } else {
                        model.engine.requestLocationAccess()
                    }
                } label: {
                    Label(
                        state.track.isRecording ? "Остановить и сохранить" : "Начать запись",
                        systemImage: state.track.isRecording ? "stop.fill" : "record.circle"
                    )
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(state.track.isRecording ? .red : accent)

                if let url = state.track.lastSavedURL {
                    ShareLink(item: url) {
                        Label(
                            L10n.string("track.share.file", url.lastPathComponent),
                            systemImage: "square.and.arrow.up"
                        )
                    }
                }
                Button { showsTracks = true } label: {
                    HStack {
                        Label("Сохранённые маршруты", systemImage: "folder.fill")
                        Spacer()
                        Text("\(model.engine.savedTracks.count)")
                            .foregroundStyle(.secondary)
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(.tertiary)
                    }
                }
                .buttonStyle(.plain)
                .padding(.vertical, 5)
                Text("При записи приложение использует геопозицию в фоне. GPX доступен в приложении «Файлы».")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private var healthCard: some View {
        InstrumentCard {
            VStack(alignment: .leading, spacing: 13) {
                HStack {
                    SectionHeading(icon: "heart.text.square.fill", title: L10n.string("section.health"))
                    Spacer()
                    if model.health.isLoading { ProgressView().controlSize(.small) }
                }

                HStack(spacing: 10) {
                    MetricCell(
                        title: "Пульс",
                        value: model.vitals.heartRateBPM.map { "\(Int($0.rounded()))" } ?? "—",
                        unit: "уд/мин"
                    )
                    MetricCell(
                        title: "SpO₂",
                        value: model.vitals.oxygenPercent.map { "\(Int($0.rounded()))" } ?? "—",
                        unit: "%"
                    )
                }

                HStack(spacing: 10) {
                    MetricCell(
                        title: "Шаги",
                        value: model.vitals.stepsToday.map { "\(Int($0.rounded()).formatted())" } ?? "—",
                        unit: ""
                    )
                    MetricCell(
                        title: "Активность",
                        value: model.vitals.activeCaloriesToday.map { "\(Int($0.rounded()).formatted())" } ?? "—",
                        unit: "ккал"
                    )
                }

                if model.health.hasRequestedAccess {
                    HStack {
                        Text(L10n.string(
                            "health.freshness",
                            AltimeterFormat.freshness(model.vitals.heartRateDate),
                            AltimeterFormat.freshness(model.vitals.oxygenDate)
                        ))
                            .font(.caption).foregroundStyle(.secondary)
                        Spacer()
                        Button("Обновить") { Task { await model.health.refresh() } }
                            .font(.caption.weight(.semibold))
                    }
                } else {
                    Button { Task { await model.health.requestAccess() } } label: {
                        Label("Дать доступ к приложению «Здоровье»", systemImage: "heart.fill")
                    }
                    .buttonStyle(.bordered)
                }

                Text("Пульс, насыщение кислорода, шаги и активные калории читаются из Apple Health; поддерживаются Apple Watch и данные совместимых приложений.")
                    .font(.caption).foregroundStyle(.secondary)
                if model.vitals.heartRateSource?.localizedCaseInsensitiveContains("Garmin") == true && model.vitals.oxygenPercent == nil {
                    Text("Garmin передаёт в Apple Health пульс, но не экспортирует Pulse Ox. Для SpO₂ нужен Garmin Health API/SDK.")
                        .font(.caption).foregroundStyle(.orange)
                }
                Button("Настроить Apple Watch, Garmin и другие источники") {
                    showsHealthSources = true
                }
                .font(.caption.weight(.semibold))
                if let error = model.health.errorMessage {
                    Text(error).font(.caption).foregroundStyle(.red)
                }
            }
        }
    }

    private var watchCard: some View {
        InstrumentCard {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeading(icon: "applewatch", title: L10n.string("section.watch"))
                Text("Создаёт уведомление с текущей высотой. Часы покажут его, когда iPhone заблокирован и зеркалирование уведомлений Altimeter Kailas включено в Watch.")
                    .font(.footnote).foregroundStyle(.secondary)
                Button {
                    Task { await model.notifications.sendAltitude(state, unit: model.unit) }
                } label: {
                    Label("Отправить высоту", systemImage: "paperplane.fill")
                }
                .buttonStyle(.bordered)
                if let status = model.notifications.statusMessage {
                    Text(status).font(.caption).foregroundStyle(.secondary)
                }
            }
        }
    }

    @ViewBuilder
    private var adviceSection: some View {
        let fieldInformation = model.advices.filter(\.isFieldInformation)
        if !fieldInformation.isEmpty {
            InstrumentCard {
                VStack(alignment: .leading, spacing: 12) {
                    SectionHeading(icon: "info.circle.fill", title: L10n.string("section.information"))
                    ForEach(fieldInformation) { advice in
                        HStack(alignment: .top, spacing: 11) {
                            Image(systemName: advice.icon)
                                .foregroundStyle(advice.color)
                                .frame(width: 24)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(advice.title).font(.subheadline.weight(.semibold))
                                Text(advice.message).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        if advice.id != fieldInformation.last?.id { Divider().opacity(0.25) }
                    }
                    Text("Только сведения о воде, погоде и качестве GPS.")
                        .font(.caption2).foregroundStyle(.tertiary)
                }
            }
        }
    }

    private var footer: some View {
        VStack(spacing: 5) {
            Text("Errarium™ by Aleksey Hermes")
                .font(.caption2.weight(.semibold))
            Link("errarium.ai@gmail.com", destination: URL(string: "mailto:errarium.ai@gmail.com")!)
                .font(.caption2)
        }
        .foregroundStyle(.tertiary)
        .padding(.top, 6)
    }
}
