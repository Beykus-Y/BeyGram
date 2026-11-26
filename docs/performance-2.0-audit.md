# Performance 2.0: статический аудит и воспроизводимый протокол измерений

Связано с Epic #1207, #1109 (jank), #1119 (измерения и baseline profile), #1121 (видео baseline), #1048 (память при
воспроизведении). Документ фиксирует, что уже известно, что подтверждено кодом **DEV `b369bee1`** (0.0.7.0), какой
протокол нужно повторить на устройстве и какие инструменты для этого готовы. Замеров в документе нет: доступ к M21
не запрашивался, все числа появятся отдельным отчётом с подписанным APK и SHA-256.

## 1. Что уже есть и не повторяется

- **#1109**: Perfetto FrameTimeline по PROF, ART-трейс, `sched_switch` по main/RenderThread, гипотезы H1–H5, A/B-флаги
  в PROF (#1112) и разбиение H3/H4 (#1115). Замер на M21: P95 89 → 21 мс, P99 101 → 44 мс, janky 8,5 → 3,2 %,
  PSS без роста (H4, удержание вкладок под чатом). A-15 снят как by design.
- **#1121**: baseline потокового видео на M21 (DEV 710): `playing_ms=2006`, `ttff_ms=552`, `seek_count=3`,
  `last_seek_to_ready_ms=149`, `rebuffer=0`, `stalls=0`, `layout=ftyp,moov,mdat`. Новые изменения не должны это ухудшить.
- **#1119 / PR #1168**: модуль Macrobenchmark, изолированный пакет `*.benchmark`, `StartupBenchmark`,
  `ChatNavigationBenchmark`, `TabsBenchmark`, строгая проверка `targetPackage`.
- **Инструмент кадра**: `scripts/perf_framestats.py` разбирает сохранённый `dumpsys gfxinfo … framestats` в
  P50/P90/P95/P99, max и долю кадров сверх бюджета. Считает две длительности отдельно:
  `FrameStartTime → FrameCompleted` (работа кадра) и `IntendedVsync → FrameCompleted` (задержка показа). Janky-счётчик
  Android печатается отдельно: это другая метрика, смешивать нельзя.

Повторно эти исследования не выполняются; новые числа обязаны быть сравнимы с этой базой.

## 2. Подтверждено чтением DEV `b369bee1`

| # | Что | Файл | Класс | Статус |
| --- | --- | --- | --- | --- |
| A1 | Переход маршрутов идёт через `AnimatedContent` с изменяющимися constraints; во время перехода существуют оба экрана | `BeyGramApp.kt:3103` | composition + measure | Совпадает с H1 из #1109; A/B подготовлен в PROF |
| A2 | `BubbleLayout` вызывает `maxIntrinsicWidth` для нижней панели при каждом measure | `MessageBubbleLayout.kt:155` | measure | Совпадает с H2; в DEV присутствует |
| A3 | Стеклянные панели измеряют содержимое с расширенными constraints и сдвигом | `ChatGlassChrome.kt` (`underChatChrome`) | measure + draw | Совпадает с H3 |
| A4 | Список сообщений использует `key` и `contentType` | `ChatScreen.kt:2377` | composition | **Не проблема**, действий не требуется |
| A5 | 14 вызовов `collectAsState()` без lifecycle в 10 файлах (`MainActivity`, `ProfileMusic`, `GiftsScreen`, `ProfileScreen`, `PaidContentViews` и др.) | см. grep в примечании | подписки | Проверять по одному: часть вне Compose-экранов |
| A6 | В `Application.onCreate` синхронно выполняются `TdAccountRegistry(filesDir).loadOrCreate()`, `MihomoProxyClient.install`, `MusicLibraries.get`, установки сервисов | `BeyGramApplication.kt` | startup + I/O | Кандидат P2; порядок важен для фонового TDLib |
| A7 | Автопроверка обновлений стартует в `Application`, но сеть только по гейту; TDLib не создаёт | `AutomaticUpdateChecks` | startup + сеть | Уже отделено от TDLib |
| A8 | Корень приложения и обложка плеера читают весь `PlaybackEngine.state`; позиция обновляется каждые 250 мс (50 мс у конца трека) | `NowPlayingBar.kt:178`, `MusicPlayerSheet.kt:952` | composition | A-03/A-04 аудита; **есть открытый PR #1244**, после слияния перемерить |
| A9 | `MusicPlayerUi`/`MusicQueueUi` уже используют `distinctUntilChanged`; позиция читается отдельным composable | `PlaybackSelectors.kt` | composition | Изоляция позиции существует, её нельзя ломать новыми full-state чтениями |

`collectAsState()`: `git grep -n "collectAsState()" -- app/src/main/java` — 14 совпадений в 10 файлах.

Ни один пункт не объявляется причиной jank: A1–A3 — те же гипотезы, что в #1109, и их подтверждение требует замеров.
A8 — единственная известная регрессия 0.0.7.0 по коду, её и нужно проверить первой на музыке.

## 3. Гипотезы Performance 2.0 (одна причина на PR)

| ID | Гипотеза | Где | Как проверить | Риск |
| --- | --- | --- | --- | --- |
| P2.0-1 | Переход маршрута избыточно переизмеряет оба экрана | `BeyGramApp.kt` route transition | A/B instant-transition в PROF, FrameTimingMetric | средний: меняет анимацию, только диагностика |
| P2.0-2 | `maxIntrinsicWidth` пузыря — заметная доля measure | `MessageBubbleLayout.kt` | PROF-флаг стратегии, как в #1109 H2; сейчас safe H2 в #1113 | средний: влияет на размеры пузырей |
| P2.0-3 | Стеклянные панели вызывают лишний measure/draw | `ChatGlassChrome.kt` | PROF-флаг H3/H4 | средний: визуальный стиль |
| P2.0-4 | Часть `collectAsState` обновляет UI, когда экран не виден | A5 | заменить на lifecycle-aware по одному, счётчик recomposition | низкий |
| P2.0-5 | Инициализация в `Application.onCreate` задерживает готовность | A6 | cold/warm start + `am start -W` + первый кадр | средний: порядок важен для TDLib |
| P2.0-6 | Тяжёлые чаты: декодирование и кэш миниатюр | media pipeline | PSS/heap до/после прокрутки, повторные циклы | средний |
| P2.0-7 | Позиция плеера перекомпоновывает корень и обложку | A8 | Perfetto/счётчик recomposition при играющей музыке, до/после #1244 | низкий (фикс уже есть) |
| P2.0-8 | Фоновые обновления списка чатов перерисовывают закрытый список под открытым чатом | A-15/PROF H4 | A/B H4 на PROF 0.0.7.0, сценарий «открытый чат + входящие» | средний: механизм оставлен by design |

Правило: изменение без подтверждённой гипотезы не переносится в DEV как «оптимизация».

## 4. Протокол измерений

### 4.1. Фиксируемые условия

Модель и build fingerprint, Android, display Hz, SoC, температура и заряд (старт 25–30 °C, 60–80 %), тема и стиль
(включая Liquid Glass и плотность), density и fontScale, настройки motion, язык, число аккаунтов и папок, тип
тестового чата (text-only и media-heavy отдельно), git SHA, versionCode, подписанный APK и его SHA-256, версия
Macrobenchmark. Одно устройство — M21; другое устройство не смешивается в одной таблице.

Перед блоком: `adb shell cmd package compile -m speed -f <pkg>` **не** выполнять для `CompilationMode.None()`; для
`CompilationMode.Partial()` компиляцию делает сама библиотека. Между блоками — 2 минуты покоя, экран выключен.

### 4.2. Сборка и установка

```bash
# benchmark-пакет, отдельный applicationId, данные пользователя не затрагиваются
./gradlew :app:assembleDevBenchmark :macrobenchmark:assembleDevBenchmark
adb install -r app/build/outputs/apk/dev/benchmark/app-dev-benchmark.apk
adb install -r macrobenchmark/build/outputs/apk/dev/benchmark/macrobenchmark-dev-benchmark.apk

# обычный Dev для ручных сценариев (только замена, без удаления данных)
./gradlew :app:assembleDevDebug
adb install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

`targetPackage` передаётся явно и обязан заканчиваться на `.benchmark`; иначе бенчмарк падает до первого кадра.
Пакет `com.beykus.beygram.dev.benchmark` не содержит аккаунта, поэтому сценарии открытия чата и прокрутки требуют
входа или синтетического fixture (см. 4.6). Это ограничение зафиксировано в A-20: сейчас `assumeTrue` молча
пропускает такие прогоны; отдельный PR меняет пропуск на явную ошибку.

### 4.3. Сценарии и команды

| Сценарий | Команда / тест | Метрики |
| --- | --- | --- |
| Cold start, без компиляции | `StartupBenchmark.coldStartupNoCompilation` | time-to-initial-display, time-to-full-display |
| Cold start с baseline profile | `StartupBenchmark.coldStartupWithBaselineProfile` (`BaselineProfileMode.Require`) | то же; падает, если профиля нет |
| Warm start | `StartupBenchmark.warmStartup` | то же, отдельная строка |
| Открыть чат и вернуться | `ChatNavigationBenchmark.openChatAndReturn` | FrameTimingMetric P50/P90/P95/P99 |
| Скролл списка чатов | `ChatNavigationBenchmark.scrollChatList` | то же |
| Скролл истории сообщений | `ChatNavigationBenchmark.scrollMessageHistory` | то же |
| Вкладки | `TabsBenchmark` | FrameTimingMetric |
| Музыка + скролл чата | ручной: играет трек, открыт чат, 20 свайпов; `gfxinfo` до/после | P50/P95/P99, janky, CPU main/RenderThread, PSS |
| Фон + входящие | ручной: открыт чат, второй аккаунт шлёт сообщения; A/B H4 на PROF | frame time, recomposition ChatsScreen, CPU |
| Свёрнутое приложение, музыка | ручной: экран выключен 10 минут | CPU, PSS, wake locks, батарея |

Запуск одного теста:

```bash
adb shell am force-stop com.beykus.beygram.dev.benchmark
adb shell am instrument -w -e class com.beykus.beygram.macrobenchmark.ChatNavigationBenchmark#openChatAndReturn \
  -e targetPackage com.beykus.beygram.dev.benchmark \
  com.beykus.beygram.macrobenchmark.test/androidx.test.runner.AndroidJUnitRunner
```

### 4.4. CPU и кадры

```bash
# кадры: сохранить, разобрать тем же парсером, что и в отчёте
adb shell dumpsys gfxinfo com.beykus.beygram.dev framestats > gfxinfo-scroll.txt
python3 scripts/perf_framestats.py gfxinfo-scroll.txt --refresh-hz 60 --label "chat scroll + music"

# CPU процесса и потоков
adb shell top -b -n 1 -d 1 -p $(adb shell pidof com.beykus.beygram.dev) > top-1s.txt
adb shell dumpsys cpuinfo > cpuinfo.txt

# Perfetto по методике #1109: 20 циклов, main/RenderThread, FrameTimeline только по layer пакета
adb shell perfetto -o /data/misc/perfetto-traces/beygram-scroll.perfetto-trace -t 30s \
  -c /data/local/tmp/beygram.cfg
```

Конфиг Perfetto — из #1109 (`sched freq idle am wm gfx view binder_driver hal dalvik input res memory`), без
`ftrace`-категорий, которые меняют поведение. `dumpsys gfxinfo` нужно сбрасывать перед сценарием
(`adb shell dumpsys gfxinfo <pkg> reset`), иначе проценты смешиваются с прошлым запуском.

### 4.5. Память

```bash
adb shell dumpsys meminfo com.beykus.beygram.dev > mem-before.txt
# 10 циклов: открыть тяжёлый чат, прокрутить вверх/вниз, вернуться
adb shell dumpsys meminfo com.beykus.beygram.dev > mem-after.txt
adb shell dumpsys meminfo -d com.beykus.beygram.dev > mem-details.txt
```

Записывать: Java Heap, Native Heap, Code, Stack, Graphics, Private Other, System, **PSS Total**, **RSS Total**;
отдельно число потоков и открытых файлов (`ls /proc/<pid>/fd | wc -l`). Java heap dump (`am dumpheap`) доступен только
debuggable-сборке; native heapprofd требует `android:profileable` в manifest, которого сейчас нет — поэтому без
отдельной profileable-сборки native heap измеряется только через PSS/RSS, и это ограничение нельзя выдавать за
полный heap-профиль.

### 4.6. Синтетические данные без реальных credentials

Цель — прогнать open/scroll сценарии без входа в аккаунт и без сети.

1. **Benchmark fixture (предпочтительно).** В `benchmark` build type подменить репозиторий на локальный генератор
   чатов/сообщений (Compose-состояние, без TDLib): 200 чатов, 5 000 сообщений, медиа-превью из drawable. Это
   отдельный PR в `:app` (build-type source set) или отдельный `benchmark`-activity; реальные данные и TDLib не
   используются.
2. **Robolectric + Roborazzi** уже покрывают композицию и размеры (`docs/screenshot-tests.md`), но не частоту кадров:
   их нельзя использовать как замену FrameTimingMetric.
3. **Ручной вход** в benchmark-пакет допустим только с согласия владельца; пакет изолирован и не является Dev/Stable.

До появления fixture сценарии open/scroll на benchmark-пакете не считаются измеренными: `assumeTrue`/`check` должны
это явно сообщать, а не молча пропускать.

### 4.7. Формат отчёта

Одна строка на сценарий и режим:

| Сценарий | Режим | Итерации | P50 | P90 | P95 | P99 | max | > бюджет | Janky (Android) | CPU main / Render | PSS / RSS |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

Для каждого сценария — отдельная строка «до» и «после», одинаковые условия, ABBA-чередование. Нельзя сравнивать
FrameTimeline-jank, `gfxinfo`-jank и «заметные пользователю» кадры между собой; ART-трейс не является непрерывным
CPU-flamegraph; числа с разных устройств не смешиваются.

## 5. Что требуется от владельца

1. Разрешение на установку benchmark-пакета и запись Perfetto на M21 (Dev/Stable и их данные не затрагиваются).
2. Тестовый чат (text-only и media-heavy) и стиль для A/B.
3. Решение, какие гипотезы проверять первыми. По умолчанию: P2.0-7 (музыка, после #1244), P2.0-1 и P2.0-2 (уже
   подготовлены в PROF), затем P2.0-8 на PROF 0.0.7.0.

## 6. Следующие шаги без устройства

- Добавить fixture из 4.6 и перевести `assumeTrue` в явную ошибку (A-20).
- Проверить A5 по одному и составить список замен на lifecycle-aware.
- Собрать статический отчёт по памяти (кэши сообщений/медиа, области жизни ViewModel) без правок чужих подсистем.
- Поднять PROF до 0.0.7.0 (A-47), сохранив флаги, R8 и проверки подписи, и повторить A/B H4 на актуальной базе.
- После слияния #1244 перемерить сценарий «музыка + скролл» и закрыть P2.0-7 фактом, а не гипотезой.