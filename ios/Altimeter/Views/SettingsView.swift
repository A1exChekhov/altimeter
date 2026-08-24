import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.dismiss) private var dismiss
    @State private var manualValue = ""
    @State private var qnhValue = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Оформление") {
                    Picker(
                        "Тема",
                        selection: Binding(
                            get: { model.darkTheme },
                            set: { model.darkTheme = $0 }
                        )
                    ) {
                        Text("Светлая").tag(false)
                        Text("Тёмная").tag(true)
                    }
                    .pickerStyle(.segmented)
                }

                Section("Единицы") {
                    Picker("Высота", selection: $model.unit) {
                        Text("Метры").tag(AltitudeUnit.meters)
                        Text("Футы").tag(AltitudeUnit.feet)
                    }
                    .pickerStyle(.segmented)
                }

                Section("Калибровка") {
                    Picker("Режим", selection: $model.calibrationMode) {
                        ForEach(CalibrationMode.allCases) { mode in
                            Text(mode.title).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)

                    switch model.calibrationMode {
                    case .automatic:
                        Text("Барометр непрерывно привязывается к абсолютной высоте GPS. Рекомендуемый режим.")
                            .font(.footnote).foregroundStyle(.secondary)
                    case .manual:
                        LabeledContent("Известная высота") {
                            HStack {
                                TextField("0", text: $manualValue)
                                    .keyboardType(.numbersAndPunctuation)
                                    .multilineTextAlignment(.trailing)
                                    .frame(width: 90)
                                Text(model.unit.symbol).foregroundStyle(.secondary)
                            }
                        }
                        Button("Применить") {
                            if let value = Double(manualValue.replacingOccurrences(of: ",", with: ".")) {
                                model.calibrateManually(displayedValue: value)
                            }
                        }
                    case .qnh:
                        LabeledContent("QNH") {
                            HStack {
                                TextField("1013.25", text: $qnhValue)
                                    .keyboardType(.decimalPad)
                                    .multilineTextAlignment(.trailing)
                                    .frame(width: 100)
                                Text("гПа").foregroundStyle(.secondary)
                            }
                        }
                        Button("Применить") {
                            if let value = Double(qnhValue.replacingOccurrences(of: ",", with: ".")) {
                                model.setQNH(value)
                            }
                        }
                    }
                }

                Section("Экран и карта") {
                    Toggle("Топографическая карта", isOn: $model.useTopographicMap)
                    Toggle("Не выключать экран", isOn: $model.keepScreenAwake)
                }

                Section("Умный авторек") {
                    Toggle("Автоматическая запись", isOn: $model.autoTrackEnabled)
                    Text("Запуск после 90 секунд и 120 м ходьбы. Автопауза после 5 минут без движения, возобновление после 60 м.")
                        .font(.footnote.weight(.light))
                        .foregroundStyle(.secondary)
                }

                Section {
                    Button("Сбросить статистику", role: .destructive) {
                        model.engine.resetStatistics()
                    }
                }

                Section("О приложении") {
                    Text("Высота: барометр + GPS с медленным фильтром Калмана. Файлы GPX сохраняются в приложении «Файлы» → На iPhone → Altimeter → Tracks.")
                    Text("Советы носят информационный характер и не являются медицинской рекомендацией.")
                        .foregroundStyle(.secondary)
                    LabeledContent("Автор") { Text("Aleksey Hermes") }
                    Link("errarium.ai@gmail.com", destination: URL(string: "mailto:errarium.ai@gmail.com")!)
                }
            }
            .navigationTitle("Настройки")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
            .onAppear {
                if let altitude = model.state.altitude {
                    manualValue = String(Int(model.unit.value(fromMeters: altitude).rounded()))
                }
                qnhValue = String(format: "%.2f", model.qnhHPA)
            }
        }
        .presentationDetents([.large])
    }
}
