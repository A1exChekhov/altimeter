# Приёмник для часов Huawei (заготовка)

Лёгкое JS-приложение (quick app / JS UI) для часов Huawei: принимает по
Wear Engine P2P сообщения от телефонного «Альтиметра · Errarium™» и показывает
высоту, место, пульс и SpO₂ на запястье.

## Что это и что нужно знать

Это **заготовка**: готовая страница (hml/css/js), разбор JSON-сообщения и
конфиг. Собирается она только в **DevEco Studio** от Huawei — здесь важно
создать проект из официального шаблона под вашу модель часов и перенести файлы:

- часы **GT 2/GT 3/GT Runner (Lite Wearable)** → шаблон *Lite Wearable, JS*;
  приём сообщений — модуль `@system.interconnect` (используется в `index.js`);
- часы **Watch 3/Watch 4 (HarmonyOS Smart Wearable)** → шаблон *Wearable*;
  модуль связи в свежих API называется иначе (Wear Engine watch-side SDK) —
  сверьте импорт в `index.js` с актуальной документацией шаблона.

## Порядок действий (один раз)

1. **AppGallery Connect**: создайте проект/приложение, включите API **Wear Engine**
   (заявка «Applying for the Wear Engine Service», одобряется 1–2 дня).
2. **DevEco Studio**: новый проект нужного типа с пакетом
   `com.chelmodeev.altimeter.watch` (или свой — тогда поменяйте его и в
   `gradle.properties` телефонного проекта, ключ `WATCH_APP_PACKAGE`).
3. Скопируйте в проект файлы из `src/main/js/default/pages/index/`
   (и сверьте `config.json`).
4. Соберите **release** и снимите SHA-256 отпечаток подписи → впишите в
   `gradle.properties` телефона (`WATCH_APP_FINGERPRINT`).
5. Установите приложение на часы из DevEco, на телефоне должен быть
   авторизованный **Huawei Health**.
6. В телефонном «Альтиметре» жмите **Wear Engine** — часы покажут данные.

## Формат сообщения (телефон → часы)

UTF-8 JSON одним сообщением:

```json
{"type":"altimeter","ts":1755600000000,"altitude_m":1248.4,"accuracy_m":3.1,
 "pressure_hpa":871.2,"lat":43.65581,"lon":40.31226,"place":"Эсто-Садок",
 "vspeed_mpm":2.4,"hr_bpm":92,"spo2":96.0,"ascent_m":523,"descent_m":118}
```

Все поля, кроме `type` и `ts`, необязательные — проверяйте на null.
