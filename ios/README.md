# Альтиметр · Errarium™ by Aleksey Hermes для iPhone

Нативный высотомер для iOS 17+: SwiftUI, Core Location, Core Motion, MapKit,
HealthKit и запись GPX. Runtime-зависимостей и платных API нет.

Контакт: [errarium.ai@gmail.com](mailto:errarium.ai@gmail.com)

## Что уже реализовано

- плавная высота: барометр iPhone + абсолютная высота GPS, фильтр Калмана;
- режимы калибровки «Авто», известная высота и QNH;
- вертикальная скорость, минимум/максимум, набор/спуск, график за час;
- OpenTopoMap и системная карта Apple;
- фоновая запись GPX, автосохранение, постоянный архив маршрутов и системный Share Sheet;
- название места через системный геокодер;
- пульс и SpO₂ через HealthKit с отображением реального источника (Apple Watch,
  Garmin Connect и совместимые приложения);
- советы о высоте, темпе набора, SpO₂, пульсе и погодном тренде;
- уведомление с высотой, которое может зеркалироваться на Apple Watch;
- WidgetKit-виджет: высота, путь, пульс и SpO₂ (малый и средний размеры);
- privacy manifest и unit-тесты расчётного ядра.

## Установка из репозитория без публикации

Размещение в App Store Connect не требуется. Достаточно вашего Apple Developer
аккаунта, Mac, iPhone и Xcode 16+. Установите XcodeGen и создайте локальный проект:

```bash
brew install xcodegen
cd Altimeter-iOS
xcodegen generate
open Altimeter.xcodeproj
```

В Xcode выберите target `Altimeter` → Signing & Capabilities:

1. выберите свою Apple Developer Team;
2. убедитесь, что включены HealthKit, Background Modes → Location updates и App Group
   `group.ai.errarium.altimeter` для targets `Altimeter` и `AltimeterWidget`;
3. установите на физический iPhone — симулятор не даёт реальных GPS/барометрических данных.

Идентификаторы проекта уже настроены под бренд: приложение `ai.errarium.altimeter`,
виджет `ai.errarium.altimeter.widget`. При Automatic Signing Xcode создаст нужные
development-профили в вашей Team. App Store, TestFlight и карточка приложения для
такой установки не нужны.

Для полной сборки с HealthKit и App Group используйте команду, для которой эти
capabilities доступны в provisioning profile. Бесплатная Personal Team позволяет
тестировать приложения на своих устройствах, но профили действуют 7 дней и часть
расширенных capabilities может потребовать участие в Apple Developer Program. Для
TestFlight и App Store нужны только если позже решите распространять приложение другим.

## Пульс и SpO₂

Приложение читает последние разрешённые измерения из Apple HealthKit. Для Apple Watch
они появятся после синхронизации часов с iPhone. SpO₂ отобразится только если конкретная
модель часов и регион поддерживают измерение кислорода и запись видна в приложении
«Здоровье» → Обзор → Дыхание → Насыщение крови кислородом.

HealthKit не отдаёт приложению «живой» оптический сенсор произвольно: для постоянного
измерения нужен отдельный watchOS workout-сеанс и приложение на Apple Watch.

### Garmin

Garmin Connect официально передаёт в Apple Health круглосуточный пульс после успешной
синхронизации устройства. Garmin Connect должен побыть открытым на переднем плане.
Pulse Ox/SpO₂ в перечень данных, экспортируемых Garmin Connect в Apple Health, не входит.
Для прямого Pulse Ox необходимы Garmin Health API или Garmin Health SDK — доступ к ним
выдаётся Garmin корпоративным партнёрам и может требовать коммерческой лицензии.

## Фоновый трек

При старте записи iOS предложит доступ к геопозиции «Всегда». Во время фоновой записи
виден системный синий индикатор. Файлы находятся в:

`Файлы → На iPhone → Альтиметр → Tracks`

Архив внутри приложения сохраняет дату, длительность, дистанцию, набор высоты и число
точек. Он восстанавливается после перезапуска. Каждая запись доступна для экспорта через
Share Sheet; удаление GPX требует отдельного подтверждения.

## Виджет iPhone

Удерживайте главный экран → «+» → **Альтиметр** и выберите малый или средний размер.
Основное приложение сохраняет в App Group последний снимок высоты, дистанции трека и
разрешённых HealthKit-показателей. Пульс и SpO₂ помечены как конфиденциальные, поэтому
iOS может скрывать их на заблокированном экране. WidgetKit не запускает произвольное
фоновое измерение барометра или HealthKit; виджет обновляется после данных приложения.

## Карта

Топографический слой загружается с OpenTopoMap. Согласно правилам сервиса в интерфейсе
показывается атрибуция OpenStreetMap/OpenTopoMap. Для массового продукта стоит добавить
собственный tile-cache/proxy и соблюдать актуальную tile policy.

## Структура

```text
Altimeter/
  App/          точка входа и AppModel
  Engine/       фьюжн датчиков, статистика, советы
  Models/       состояние и типы
  Services/     GPX, HealthKit, уведомления, геокодер
  Views/        SwiftUI-интерфейс, Chart, MapKit
  Supporting/   Info.plist и entitlements
  Resources/    assets и privacy manifest
  Shared/       снимок данных для App Group
AltimeterWidget/ WidgetKit-расширение
AltimeterTests/ расчётные тесты
project.yml     описание проекта для XcodeGen
```

## Перед публикацией

- добавить финальную иконку приложения в `AppIcon.appiconset`;
- заменить Bundle ID при необходимости;
- заполнить App Privacy в App Store Connect (геопозиция и health data остаются на устройстве);
- протестировать фоновые маршруты, разрешения и энергопотребление на нескольких iPhone;
- проверить актуальные правила OpenTopoMap.
