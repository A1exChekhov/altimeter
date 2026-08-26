# Altimeter Kailas · Errarium™ by Aleksey Hermes для iPhone

Нативный высотомер для iOS 17+: SwiftUI, Core Location, Core Motion, MapKit,
MapLibre/PMTiles, HealthKit и запись GPX. Платных API нет.

Контакт: [errarium.ai@gmail.com](mailto:errarium.ai@gmail.com)

Готовая подписанная IPA: [релиз iOS 1.2.2](https://github.com/A1exChekhov/altimeter/releases/tag/ios-v1.2.2-ad-hoc).
Установка через Safari: [a1exchekhov.github.io/altimeter/iphone/](https://a1exchekhov.github.io/altimeter/iphone/).

## Что уже реализовано

- плавная высота: барометр iPhone + абсолютная высота GPS, фильтр Калмана;
- режимы калибровки «Авто», известная высота и QNH;
- вертикальная скорость, минимум/максимум, набор/спуск, график за час;
- встроенная офлайн-карта региона Кайласа с тропами, объектами и рельефом,
  OpenTopoMap и системная карта Apple;
- непрерывная фоновая запись GPX до явной остановки: 1 с по умолчанию, режимы
  Авто/1/2/4 с, немедленная фиксация поворотов, сегменты вместо ложных прямых,
  автосохранение, постоянный архив маршрутов и системный Share Sheet;
- название места через системный геокодер;
- пульс, SpO₂, шаги и активные калории через HealthKit с отображением реального источника (Apple Watch,
  Garmin Connect и совместимые приложения);
- советы о высоте, темпе набора, SpO₂, пульсе и погодном тренде;
- уведомление с высотой, которое может зеркалироваться на Apple Watch;
- четыре нативных WidgetKit-виджета: «Высота», «Здоровье», «Поход» и
  комбинированный «Экспедиция» (малый и средний размеры, системная типографика);
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

### Что изменилось после добавления Developer Team

Team ID `76UD6VNBTE` теперь записан прямо в `project.yml` как
`DEVELOPMENT_TEAM`. Поэтому выбирать команду вручную при каждой генерации проекта
больше не нужно. На Mac требуется только войти в Apple Account, которому доступна
эта Team, оставить **Automatically manage signing** включённым и запустить приложение.
Публикация в App Store или создание приложения в App Store Connect для такой установки
по-прежнему не нужны.

В Xcode выберите target `Altimeter` → Signing & Capabilities:

1. проверьте выбранную Apple Developer Team `76UD6VNBTE` (она уже задана в
   `project.yml`);
2. убедитесь, что включены HealthKit, Background Modes → Location updates и App Group
   `group.ai.errarium.altimeter` для targets `Altimeter` и `AltimeterWidget`;
3. установите на физический iPhone — симулятор не даёт реальных GPS/барометрических данных.

Идентификаторы проекта уже настроены под бренд: приложение `ai.errarium.altimeter`,
виджет `ai.errarium.altimeter.widget`. Team ID `76UD6VNBTE` также уже задан для
всех targets. При Automatic Signing Xcode создаст нужные development-профили в
этой Team. App Store, TestFlight и карточка приложения для такой установки не нужны.

Для полной сборки с HealthKit и App Group используйте команду, для которой эти
capabilities доступны в provisioning profile. Бесплатная Personal Team позволяет
тестировать приложения на своих устройствах, но профили действуют 7 дней и часть
расширенных capabilities может потребовать участие в Apple Developer Program. Для
TestFlight и App Store нужны только если позже решите распространять приложение другим.

## Где появляется исполняемый файл

При нажатии Run Xcode собирает `Altimeter.app`, подписывает его Team `76UD6VNBTE` и
сразу устанавливает на выбранный iPhone. Локальный результат сборки обычно лежит в:

```text
~/Library/Developer/Xcode/DerivedData/Altimeter-*/Build/Products/Debug-iphoneos/Altimeter.app
```

Для экспортируемого `.ipa` выполните **Product → Archive**, затем в Organizer выберите
**Distribute App → Development** или **Ad Hoc**. Архивы Xcode хранятся в
`~/Library/Developer/Xcode/Archives/`. Готовый `.ipa` нельзя заранее собрать на Windows,
потому что для него требуются Xcode, сертификат и provisioning profile вашей Team.

## Сборка в GitHub Actions

Workflow `.github/workflows/ios.yml` автоматически компилирует iPhone-проект на
macOS при изменениях в `ios/` и доступен для ручного запуска на вкладке **Actions →
iOS build**. Обычный запуск создаёт simulator-артефакт только для проверки компиляции;
его нельзя установить на физический iPhone.

Для устанавливаемого development IPA запустите workflow вручную с параметром
`distribution = development-ipa`. Репозиторий должен содержать четыре GitHub Actions secret:

- `IOS_CERTIFICATE_BASE64` — сертификат `.p12` в Base64;
- `IOS_CERTIFICATE_PASSWORD` — пароль `.p12`;
- `IOS_APP_PROFILE_BASE64` — development profile приложения в Base64;
- `IOS_WIDGET_PROFILE_BASE64` — отдельный development profile виджета в Base64.

Profiles должны относиться к Team `76UD6VNBTE`, bundle IDs `ai.errarium.altimeter`
и `ai.errarium.altimeter.widget` и включать UDID устанавливаемого iPhone. При успешной
сборке GitHub публикует приватный Actions artifact `Altimeter-signed-ipa`; сертификат
и profiles в исходники или публичный Release не попадают.

### Загрузка в TestFlight

Режим `distribution = testflight` собирает App Store IPA версии 1.2.5 с уникальным
build number из `GITHUB_RUN_NUMBER` и загружает его в App Store Connect. Для него нужны:

- активная Apple Developer Program и карточка `ai.errarium.altimeter` в App Store Connect;
- уже используемые secrets `IOS_CERTIFICATE_BASE64` и `IOS_CERTIFICATE_PASSWORD`;
- API-ключ App Store Connect в secrets `APP_STORE_CONNECT_API_KEY_ID`,
  `APP_STORE_CONNECT_API_ISSUER_ID` и `APP_STORE_CONNECT_API_PRIVATE_KEY_BASE64`.

Workflow автоматически управляет App Store provisioning profiles, экспортирует методом
`app-store-connect` и сохраняет приватный IPA вместе с логами. App Store icon 1024×1024,
privacy manifest, фоновые location updates, HealthKit, App Group и export compliance
уже включены в проект.

## Пульс, SpO₂, шаги и калории

Приложение читает последние разрешённые измерения, сегодняшние шаги и активные
сожжённые калории из Apple HealthKit. Для Apple Watch
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

`Файлы → На iPhone → Altimeter Kailas → Tracks`

Архив внутри приложения сохраняет дату, длительность, дистанцию, набор высоты и число
точек. Он восстанавливается после перезапуска. Каждая запись доступна для экспорта через
Share Sheet; удаление GPX требует отдельного подтверждения.

## Виджет iPhone

Удерживайте главный экран → «+» → **Altimeter Kailas** и выберите «Высота», «Здоровье»
или «Высота и здоровье» в малом либо среднем размере.
Основное приложение сохраняет в App Group последний снимок высоты, дистанции трека и
разрешённых HealthKit-показателей. Пульс, SpO₂ и шаги помечены как конфиденциальные, поэтому
iOS может скрывать их на заблокированном экране. WidgetKit не запускает произвольное
фоновое измерение барометра или HealthKit; виджет обновляется после данных приложения.

## Карта

В приложение встроена карта `Кайлас · кора`: векторная PMTiles-карта до масштаба 15,
рельеф Terrarium и офлайн-шрифты подписей. Общий размер ресурсов — около 12,7 МБ.
В режиме «Авто» приложение использует её внутри региона 81,05–81,55° E,
30,85–31,25° N и переключается на онлайн-карту за его пределами. В настройках карту
Кайласа можно выбрать вручную и открыть без сети из любой точки.

Онлайн-топографический слой загружается с OpenTopoMap. Согласно правилам сервисов в
интерфейсе показывается атрибуция OpenStreetMap, Mapterhorn и OpenTopoMap.

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

- создать карточку `ai.errarium.altimeter` в App Store Connect;
- добавить distribution-сертификат и API-ключ в GitHub Actions secrets;
- заполнить App Privacy в App Store Connect (геопозиция и health data остаются на устройстве);
- протестировать фоновые маршруты, разрешения и энергопотребление на нескольких iPhone;
- проверить актуальные правила OpenTopoMap.
