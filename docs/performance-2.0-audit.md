# Performance 2.0: статический аудит и протокол baseline

Связано с Epic #1207, #1109 (jank), #1119 (измерения), #1121 (видео baseline). Документ фиксирует, что уже известно,
что подтверждено кодом текущего DEV, и что нужно измерить на устройстве. Замеров в этом документе нет: доступ к M21
не запрашивался.

## 1. Что уже есть и не повторяется

- #1109: Perfetto FrameTimeline по PROF, ART-трейс, `sched_switch` по main/RenderThread, гипотезы H1–H5, A/B-флаги
  в PROF (#1112) и разбиение H3/H4 (#1115).
- #1121: baseline потокового видео на M21 (DEV 710): `playing_ms=2006`, `ttff_ms=552`, `seek_count=3`,
  `last_seek_to_ready_ms=149`, `rebuffer=0`, `stalls=0`, `layout=ftyp,moov,mdat`. Это исходная точка, которую новые
  изменения не должны ухудшить.
- #1119 / PR #1168: модуль Macrobenchmark, изолированный пакет `*.benchmark`, сценарии и строгий источник профиля.

Повторно эти исследования не выполняются.

## 2. Подтверждено чтением текущего DEV (`64bc94bf`)

| # | Что | Файл | Класс | Статус |
| --- | --- | --- | --- | --- |
| A1 | Переход маршрутов идёт через `AnimatedContent` с изменяющимися constraints; во время перехода существуют оба экрана | `BeyGramApp.kt:3052` (`AnimatedContent`, `routeTransitionSpec`) | composition + measure | Совпадает с H1 из #1109; A/B уже подготовлен в PROF |
| A2 | `BubbleLayout` вызывает `maxIntrinsicWidth` для нижней панели при каждом measure | `ui/screens/chat/MessageBubbleLayout.kt:155` | measure | Совпадает с H2; в DEV присутствует |
| A3 | Стеклянные панели измеряют содержимое с расширенными constraints и сдвигом | `ui/screens/chat/ChatGlassChrome.kt` (`underChatChrome`) | measure + draw | Совпадает с H3 |
| A4 | Список сообщений уже использует `key` и `contentType` | `ChatScreen.kt:2355` | composition | **Не проблема**: действий не требуется |
| A5 | 13 вызовов `collectAsState()` без lifecycle (часть вне Compose-экранов) | `MainActivity.kt:179`, `ProfileMusic.kt:73`, `GiftsScreen.kt:142` и др. | подписки | Требует проверки по одному: часть — на экранах, часть — в Activity/сервисах |
| A6 | В `Application.onCreate` выполняется синхронная работа: `TdAccountRegistry(filesDir).loadOrCreate()`, `MusicLibraries.get(this)`, установки сервисов | `BeyGramApplication.kt:44-62` | startup + I/O | Кандидат P2; порядок важен для фонового TDLib, менять только с проверкой |
| A7 | Обновления: автопроверка стартует в `Application`, но сеть только по гейту; TDLib не создаёт | `AutomaticUpdateChecks` | startup + сеть | Уже отделено от TDLib |

Ни один пункт не объявляется причиной jank: A1–A3 — те же гипотезы, что в #1109, и их подтверждение требует замеров.

## 3. Гипотезы Performance 2.0 (по одной причине на PR)

| ID | Гипотеза | Где | Как проверить | Риск |
| --- | --- | --- | --- | --- |
| P2.0-1 | Переход маршрута избыточно переизмеряет оба экрана | `BeyGramApp.kt` route transition | A/B instant-transition в PROF, FrameTimingMetric | средний: меняет анимацию, только диагностика |
| P2.0-2 | `maxIntrinsicWidth` пузыря — заметная доля measure | `MessageBubbleLayout.kt` | PROF-флаг стратегии, как в #1109 H2 | средний: влияет на размеры пузырей |
| P2.0-3 | Стеклянные панели вызывают лишний measure/draw | `ChatGlassChrome.kt` | PROF-флаг H3/H4 | средний: визуальный стиль |
| P2.0-4 | Часть `collectAsState` обновляет UI, когда экран не виден | A5 | заменить на `collectAsStateWithLifecycle` по одному, счётчик recomposition | низкий |
| P2.0-5 | Инициализация в `Application.onCreate` задерживает готовность | A6 | cold/warm start + `am start -W` + первый кадр | средний: порядок важен для TDLib |
| P2.0-6 | Тяжёлые чаты: декодирование и кэш миниатюр | media pipeline | PSS/heap до/после прокрутки, повторные циклы | средний |

Правило: изменение без подтверждённой гипотезы не переносится в DEV как «оптимизация».

## 4. Протокол baseline (когда будет доступ к M21)

Условия (фиксировать в отчёте): модель и build fingerprint, Android, display Hz, SoC, температура и заряд, тема и
стиль (включая Liquid Glass), density и fontScale, настройки motion, язык, число аккаунтов и папок, тестовый чат
(text-only и media-heavy отдельно), git SHA и versionCode, подписанный APK и его SHA-256.

Порядок:

1. Установить benchmark-пакет `${applicationId}.benchmark` (не Dev/Stable), прогреть 3 цикла «список → чат → назад».
2. Macrobenchmark (5 прогонов, чередование ABBA, охлаждение между блоками):
   ```
   ./gradlew :macrobenchmark:connectedDevBenchmarkAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.beykus.beygram.macrobenchmark.StartupBenchmark \
     -Pandroid.testInstrumentationRunnerArguments.targetPackage=com.beykus.beygram.dev.benchmark
   ./gradlew :macrobenchmark:connectedDevBenchmarkAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=com.beykus.beygram.macrobenchmark.ChatNavigationBenchmark \
     -Pandroid.testInstrumentationRunnerArguments.targetPackage=com.beykus.beygram.dev.benchmark
   ```
3. Perfetto по методике #1109 (20 циклов, `sched freq idle am wm gfx view binder_driver hal dalvik input res memory`),
   FrameTimeline только по layer тестируемого пакета.
4. Память: `dumpsys meminfo`, `dumpsys gfxinfo ... framestats`, GC-счётчики, число потоков и открытых файлов до и
   после 10 циклов открытия/закрытия тяжёлого чата.
5. Записать: P50/P90/P95/P99 frame duration, frame overrun, missed/dropped, CPU main/RenderThread, доли
   composition/layout/draw, PSS/RSS, время запуска и время до готовности к взаимодействию.

Ограничения: FrameTimeline jank, `gfxinfo` jank и «заметные пользователю» кадры — разные метрики, их нельзя
сравнивать между собой; ART-трейс не является непрерывным CPU-flamegraph; запись на устройстве без разрешения
владельца не выполняется.

## 5. Что требуется от владельца

1. Разрешение на установку benchmark-пакета и запись Perfetto на M21 (данные Dev/Stable не затрагиваются).
2. Подтверждение тестового чата и стиля для A/B.
3. Решение, какие гипотезы (P2.0-1…6) проверять первыми; по умолчанию предлагается P2.0-1 и P2.0-2, так как они уже
   подготовлены в PROF.

## 6. Следующие шаги без устройства

- Подготовить сценарные тесты навигации в Macrobenchmark (переключение чатов, вкладки) — без запуска на устройстве.
- Проверить по коду A5 (подписки) и составить точный список мест для замены на lifecycle-aware.
- Собрать статический отчёт по памяти (кэши сообщений/медиа, области жизни ViewModel) без изменения чужих подсистем.