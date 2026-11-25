# Macrobenchmark и Baseline Profile (#1119)

Инфраструктура замеров и профилирования BeyGram. Прирост производительности здесь **не заявляется**: его нужно
измерить на устройстве.

Связано с #1109 (методика замеров для чатов) и #395 (закрыт без профиля). Ветку `PROF` и её эксперименты этот PR
не трогает.

## Что добавлено

| Часть | Файл |
| --- | --- |
| Модуль макробенчмарков (`com.android.test`) | `macrobenchmark/build.gradle.kts` |
| Общие навигационные помощники | `macrobenchmark/src/main/java/.../Benchmarks.kt` |
| Холодный старт (`StartupTimingMetric`, 5 прогонов) | `StartupBenchmark.kt` |
| Открытие чата и возврат, скролл списка и истории (`FrameTimingMetric`) | `ChatNavigationBenchmark.kt` |
| Генератор Baseline Profile | `BaselineProfileGenerator.kt` |
| Release-подобный вариант сборки | `app/build.gradle.kts` → `buildTypes.create("benchmark")` |
| Разрешение профилирования | `app/src/benchmark/AndroidManifest.xml` (`<profileable android:shell="true"/>`) |
| Генерация и копирование профиля | `scripts/baseline-profile.ps1` |
| Версии зависимостей | `gradle/libs.versions.toml`: `benchmark 1.5.0`, `uiautomator 2.4.1`, `androidx.test:runner 1.7.0` |

Версии взяты из Google Maven (`benchmark-macro-junit4` 1.5.0 — последний стабильный, `uiautomator` 2.4.1,
`androidx.test:runner` 1.7.0). Совместимость с AGP 9.2.1 / Gradle 9.4.1 / Kotlin 2.2.10 проверена сборкой модуля.

## Варианты сборки

| Вариант | Назначение | Подпись | R8 |
| --- | --- | --- | --- |
| `devDebug` | разработка | debug | нет |
| `devRelease` / `prodRelease` | публикация | release | **выключен** (решение владельца) |
| `devBenchmark` / `prodBenchmark` | замеры и профиль | debug | выключен, как в release |

`benchmark` не публикуется и не попадает в CI. Он сохраняет поведение release (R8 и shrinker выключены), поэтому
измеряется тот же код, что уходит пользователю, но без необходимости в release-ключе.

## Как запускать на Samsung M21 (финальный сценарий)

Нужно физическое устройство. Основное слабое устройство для проверки — Samsung M21 (SM-M215F). Эмулятор для этих
замеров не годится: он меряет производительность хоста. Запуск на телефоне выполняется только с разрешения владельца.

Подготовка (один раз):

```powershell
# Пакеты и ADB
adb devices                       # ровно одно устройство в состоянии "device"
adb shell getprop ro.product.model      # ожидается SM-M215F
adb shell getprop ro.build.version.sdk # 29+ (у M21 — Android 10/11/12 в зависимости от прошивки)

# Устройство не должно быть под другим агентом: проверьте, что нет активного benchmark/install
adb shell pm list packages | Select-String "com.beykus.beygram"
```

Сборка и установка профилируемого варианта (debug-подпись, R8 выключен как в релизе). У benchmark **свой
applicationId** (`com.beykus.beygram.dev.benchmark` / `com.beykus.beygram.benchmark`), поэтому он никогда не
устанавливается поверх Dev или Stable и не затрагивает аккаунт владельца. Gradle-задачи `connectedDevBenchmarkAndroidTest`
устанавливают этот пакет сами; вручную ставить ничего не нужно.

```powershell
# Только проверить сборку/манифест, не устанавливая
./gradlew :app:assembleDevBenchmark
# Пакет benchmark-сборки (не Dev и не Stable)
adb shell dumpsys package com.beykus.beygram.dev.benchmark | Select-String "profileable|versionName"
```

Перед каждым блоком: закрыть приложение, разблокировать экран, включить авиарежим не нужно (проверка не влияет),
телефон не на зарядке или на зарядке — одинаково для всех прогонов; температура корпуса комнатная, между блоками
пауза 5 минут.

```powershell
# Холодный старт: 5 прогонов, StartupTimingMetric
./gradlew :macrobenchmark:connectedDevBenchmarkAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.beykus.beygram.macrobenchmark.StartupBenchmark

# Навигация и скролл: 5 прогонов, FrameTimingMetric
./gradlew :macrobenchmark:connectedDevBenchmarkAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.beykus.beygram.macrobenchmark.ChatNavigationBenchmark

# Stable-канал вместо DEV: свой пакет com.beykus.beygram.benchmark
./gradlew :macrobenchmark:connectedProdBenchmarkAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.beykus.beygram.macrobenchmark.StartupBenchmark
```

Пакет под тестом определяется автоматически (instrumentation target, то есть фактический flavor + суффикс
`.benchmark`), поэтому `targetPackage` вручную указывать не нужно.

Методика: `setupBlock` останавливает приложение и доводит его до нужного экрана (список чатов или конкретный чат), а
измеряемый блок содержит только действие — открытие чата, возврат или скролл. Каждый такой сценарий проверяет, что
он действительно на нужном экране и что список сдвинулся (`Benchmarks.requireListMoved`), иначе прогон падает, а не
публикует кадры статичного экрана.

Где смотреть результаты:

- `macrobenchmark/build/outputs/connected_android_test_additional_output/` — JSON и логи прогонов;
- `macrobenchmark/build/outputs/androidTest-results/connected/` — отчёты instrumentation.

Что записать в таблицу (одинаково для всех вариантов): устройство и прошивка, APK (вариант, versionName, SHA-256),
дата и температура, `timeToInitialDisplay`/`timeToFullDisplay` (P50/P95/P99, min/max), длительность кадров и jank,
размер APK, PSS/RSS после прогретого сценария.

Удаление: `adb uninstall com.beykus.beygram.dev.benchmark` (или `.benchmark` для Stable). Рабочий Dev/Stable не
затрагивается: у benchmark-сборки отдельный applicationId и отдельные данные.

Результаты: `macrobenchmark/build/outputs/connected_android_test_additional_output/...` и
`macrobenchmark/build/outputs/androidTest-results/connected/...`.

Условия сопоставимости (как в #1109): одинаковые APK-вариант, устройство, температура, сценарий; не сравнивать
debug с release; минимум 5 прогонов; чередование вариантов; между блоками дать устройству остыть.

## Baseline Profile

Профиль генерируется только реальным прогоном на устройстве:

```powershell
./scripts/baseline-profile.ps1 -Flavor dev
./scripts/baseline-profile.ps1 -Flavor prod -Serial R58M1234567
```

Скрипт:

1. проверяет, что подключено ровно одно физическое устройство;
2. запускает `BaselineProfileGenerator` на `devBenchmark`/`prodBenchmark`;
3. находит полученный `baseline-prof.txt` в выводах теста;
4. отказывается копировать файл, если в нём меньше 100 записей (значит прогон почти ничего не прошёл);
5. берёт только результат **этого** запуска **этого** flavor: файл должен быть создан после старта скрипта и лежать в
   каталоге варианта, поэтому старый профиль или профиль другого flavor скопировать нельзя;
6. кладёт профиль в `app/src/main/baseline-prof.txt`, откуда AGP упаковывает его в APK.

Проверка упаковки в release APK (после получения профиля):

```powershell
./gradlew :app:assembleDevRelease
./scripts/baseline-profile.ps1 -ApkPath app/build/outputs/apk/devRelease/app-dev-release.apk
# OK: baseline profile is packaged ... ; при отсутствии файла — ошибка
```

**В репозитории нет `baseline-prof.txt`**, и он не создаётся вручную. Пока устройство не запускало генератор,
файла быть не должно: пустой или выдуманный профиль выглядит как результат профилирования, но им не является.

Проверка, что профиль реально попал в сборку:

```powershell
./gradlew :app:assembleDevRelease
# в APK должен быть assets/dexopt/baseline.prof (baseline.profm рядом)
```

`profileinstaller` уже подключён (`app/build.gradle.kts`), поэтому наличие файла в `app/src/main/` — единственное
условие его установки при первом запуске.

## Почему без Gradle-плагина `androidx.baselineprofile`

Плагин 1.5.0 на AGP 9.2.1 создал задачи генерации только для **release**-вариантов (`generateDevReleaseBaselineProfile`,
`generateProdReleaseBaselineProfile`), а не для `devBenchmark`/`prodBenchmark`. Release в этом проекте подписывается
ключом, которого нет в CI, поэтому такая генерация либо падала бы, либо требовала отдельного ключа. Вместо того чтобы
добавлять в сборку задачу, которая не соответствует реальному варианту замеров, используется прямой путь через
`BaselineProfileRule`: он прозрачен, воспроизводим и не меняет конфигурацию релизной сборки. Когда плагин начнёт
корректно связывать benchmark-вариант с AGP 9, его можно добавить отдельным PR — инфраструктура к этому готова.

## Метрики

| Метрика | Что даёт |
| --- | --- |
| `StartupTimingMetric` | time-to-initial-display и time-to-full-display холодного старта; библиотека печатает min/median/max и P50/P90/P95/P99 |
| `FrameTimingMetric` | длительность кадров навигации и скролла: jank, P50/P95/P99 |
| Размер APK | `Get-Item *.apk` до и после профиля |
| PSS/RSS | `adb shell dumpsys meminfo <package>` после прогретого сценария |

Отдельного замера PSS/RSS в коде нет: он снимается вручную по одной и той же методике, чтобы не превращать
бенчмарк в источник несопоставимых чисел.

## CI

Модуль и задачи не добавлены ни в один workflow. `android-dev-channel.yml`, `android-release.yml` и
`screenshot-goldens.yml` не менялись: бенчмарк требует физического устройства и не должен замедлять каждый PR.
Запуск — только вручную владельцем.

## Что не сделано и почему

- **Профиль не сгенерирован**: физического устройства в этой сессии нет, а эмулятор даёт нерелевантные числа.
- **Замеры не выполнены**: нет ни одного прогона, поэтому нет таблицы «до/после» и нет утверждений о выигрыше.
- **Навигационные селекторы не проверены на устройстве**: приложение полностью на Compose, стабильных `android:id`
  нет, поэтому `Benchmarks.firstChatRow` выбирает первую кликабельную строку ниже шапки. Константы
  `HEADER_HEIGHT_FRACTION`/`MIN_ROW_HEIGHT_FRACTION` — первое место для подстройки, если на M21 сценарий не находит чат.
  Без найденной строки сценарий помечается пропущенным (`assumeTrue`), а не выдаёт пустой результат за измерение.
- **Профиль в APK не проверен**: проверка делается после реальной генерации (`unzip` по APK).

## Чек-лист владельцу

1. Подключить M21 (или другое слабое устройство), убедиться, что на нём установлен DEV/Stable с чатами.
2. Прогнать `StartupBenchmark` и `ChatNavigationBenchmark` по 5 раз, записать медиану и разброс, версию APK, устройство.
3. Сгенерировать профиль `scripts/baseline-profile.ps1 -Flavor dev`, закоммитить `app/src/main/baseline-prof.txt`.
4. Повторить замеры, сравнить таблицу «без профиля / с профилем», размер APK и PSS.
5. Если выигрыша нет — закрыть задачу отрицательным результатом; это допустимый исход.