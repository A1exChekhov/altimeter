import SwiftUI

struct HealthSourcesView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    sourceRow(
                        icon: "applewatch",
                        color: .pink,
                        title: "Apple Watch",
                        subtitle: "Пульс, SpO₂ и шаги читаются из Apple Health после синхронизации часов. Доступность SpO₂ зависит от модели часов и региона."
                    )
                } header: {
                    Text("Полная поддержка")
                }

                Section {
                    sourceRow(
                        icon: "figure.run.circle.fill",
                        color: .blue,
                        title: "Garmin",
                        subtitle: "Garmin Connect передаёт в Apple Health пульс и шаги. Pulse Ox в Apple Health не экспортируется."
                    )
                    VStack(alignment: .leading, spacing: 8) {
                        instruction(1, "Откройте Garmin Connect → Ещё → Настройки → Подключённые приложения.")
                        instruction(2, "Выберите Apple Health и разрешите передачу пульса и шагов.")
                        instruction(3, "После синхронизации часов оставьте Garmin Connect открытым на переднем плане до завершения передачи.")
                    }
                    .padding(.vertical, 4)
                } header: {
                    Text("Garmin Connect")
                } footer: {
                    Text("Прямой Garmin Pulse Ox возможен через Garmin Health API или Health SDK. Они выдаются Garmin корпоративным партнёрам; коммерческое использование может потребовать лицензии.")
                }

                Section {
                    sourceRow(
                        icon: "heart.text.square.fill",
                        color: .green,
                        title: "Другие часы и датчики",
                        subtitle: "Если приложение производителя записывает пульс, SpO₂ или шаги в Apple Health, Альтиметр прочитает их автоматически."
                    )
                    Text("Проверьте: Здоровье → профиль → Приложения и службы → приложение производителя → разрешённые категории.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section("Конфиденциальность") {
                    Text("Альтиметр только читает выбранные вами показатели. Данные не отправляются на сервер и используются локально для отображения и предупреждений о высоте.")
                        .font(.footnote)
                }
            }
            .navigationTitle("Источники здоровья")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Готово") { dismiss() }
                }
            }
        }
    }

    private func sourceRow(icon: String, color: Color, title: String, subtitle: String) -> some View {
        HStack(alignment: .top, spacing: 13) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(color)
                .frame(width: 32)
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.headline)
                Text(subtitle).font(.footnote).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private func instruction(_ number: Int, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 9) {
            Text("\(number)")
                .font(.caption.weight(.bold))
                .frame(width: 22, height: 22)
                .background(.blue.opacity(0.15), in: Circle())
            Text(text).font(.footnote)
        }
    }
}
