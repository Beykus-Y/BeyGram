# Скриншотные эталоны стилей (#634)

Повторяемая проверка вида главного экрана для пяти стилей и двух ручных положений нижней панели. Зонтик: #620.

## Инструмент

**Roborazzi + Robolectric (native graphics)**, обычные JVM unit-тесты варианта `devDebug`.

Почему так:

- облачная сессия не собирает Android локально, единственная проверка — CI, а в CI нет эмулятора; Robolectric рисует
  Compose на JVM через нативный рендерер Android (`@GraphicsMode(NATIVE)`), эмулятор не нужен;
- тесты идут в уже существующем шаге `:app:testDevDebugUnitTest`, отдельный pipeline и плагин Gradle не нужны;
- Paparazzi отвергнут: он привязан к layoutlib конкретной версии AGP/compileSdk, а проект на AGP 9 и compileSdk 36.1;
  Roborazzi подключается как обычная библиотека и не зависит от плагина Android Gradle.

Robolectric запускается на SDK 34: для SDK 35+ он требует JDK 21, а CI работает на JDK 17. Приложение в тестах заменено
на простой `Application`, поэтому TDLib и нативные медиа-библиотеки не загружаются.

## Что снимается

`app/src/test/java/com/beykus/beygram/ui/screenshots/StyleMatrixScreenshotTest.kt` — настоящий `MainTabsScaffold` и
настоящие строки `ChatListItem` на фиксированных данных (закреплённые, непрочитанные, без звука, длинное имя, отметка
«непрочитано»), экран 360×760 dp, xhdpi.

| Стиль | Pinned | Floating |
|---|---|---|
| Default | light, dark, font200 | light, dark, font200 |
| Material 2 | light, dark, font200 | light, dark, font200 |
| Material 3 | light, dark, font200 | light, dark, font200 |
| Liquid Glass (Balanced) | light, dark, font200 | light, dark, font200 |
| Signature | light, dark, font200 | light, dark, font200 |

30 эталонов в `app/src/test/screenshots/<Стиль>_<Положение>_<вид>.png`.

Кроме снимков, для каждой из 10 комбинаций при fontScale 1.0 и 2.0 проверяется геометрия: последняя строка списка после
прокрутки до конца заканчивается выше вкладок, то есть плавающая панель её не перекрывает.

Режим «По стилю» (Auto) не снимается отдельно: он даёт ровно то же положение, что и стиль сам по себе, это проверяет
`NavigationSpecTest` на JVM.

## Как работает в CI

- По умолчанию режим `verify`: отличие от эталона сверх порога Roborazzi (около 1% пикселей, он гасит сдвиги
  сглаживания между образами раннера) валит `testDevDebugUnitTest`. Пропавший эталон тоже валит тест.
- Артефакт `screenshots-<sha>` в каждом прогоне `Android release` содержит эталоны и, при расхождении, картинки
  `*_actual.png` и `*_compare.png` из `app/build/outputs/roborazzi/`.

Намеренное изменение вида: повесить на PR метку `record-screenshots`. Workflow `Record screenshot goldens` перезапишет
эталоны с `-Pscreenshots=record`, закоммитит их в ветку PR и снимет метку (работает только для веток этого репозитория).
Коммит токеном workflow не запускает CI, поэтому после него нужен ещё один push или перезапуск `Android release`.
Локально то же: `./gradlew :app:testDevDebugUnitTest -Pscreenshots=record`. Разницу смотреть в PR; эталон не правят
ради зелёного CI без проверки, что изменение задумано.

## Широкие раскладки (отчёт по двум панелям)

`WideLayoutScreenshotTest` снимает двухпанельный экран (настоящие `ChatsTwoPaneScaffold`, рейка или нижнее меню в панели
списка, строки чатов, пустая правая панель) для пяти стилей в трёх окнах: телефон в ландшафте 851×393, раскрытый складной
673×841, планшет 1280×800; светлая и тёмная тема. Эталоны `app/src/test/screenshots/Wide_<Стиль>_<окно>_<тема>.png`.
Эталоны перезаписаны после P3: плотность строк списка и заливка открытого чата меняют картинку широких окон.
Открытый чат в эталоны не входит: его снимает только устройство. Окно Robolectric 1280×900 dp, размер сцены задаёт `WindowLayout`.

## Чего эти тесты не ловят

- Настоящий blur Liquid Glass High и падения в `RenderThread` (#623): Robolectric — не GPU. Это остаётся проверкой на
  устройстве (Samsung M21, API 29/30/31+).
- AMOLED, жестовая/трёхкнопочная навигация, планшет, RTL, другие экраны (чат, настройки, профиль, плеер) — следующие
  шаги матрицы; добавляются новыми тестами в тот же класс или рядом.
- TalkBack, контраст, frame time — только на устройстве.

## Что эталоны уже показали

Первый набор (fontScale 2.0) нашёл два дефекта крупного текста, оба исправлены и эталоны перезаписаны:

- счётчик непрочитанных обрезал цифры: плашка 22 dp (и её скругление) теперь растёт вместе с системным размером текста, при 1× не меняется ни в одном стиле
  (`ChatListItem`);
- подпись «Contacts» в нижней панели не помещалась: подписи вкладок растут с системным размером текста до 1.5×, дальше
  остаются на этом размере и при нехватке места получают многоточие (`tabLabelSize` в `MainTabs.kt`).

## Локальный запуск в PowerShell

Из корня отдельного клона запустите существующую матрицу BeyGram или выберите отдельный тест. Gradle использует Android SDK и JDK этой среды.

```powershell
.\scripts\visual-tests.ps1
.\scripts\visual-tests.ps1 -Mode compare -TestPattern '*StyleMatrixScreenshotTest'
.\scripts\visual-tests.ps1 -Mode record -TestPattern '*MusicHomeScreenshotTest'
```

`verify` сравнивает с эталонами в `app/src/test/screenshots`. `compare` сохраняет результаты сравнения в `app/build/outputs/roborazzi`. `record` записывает эталоны выбранного теста. Проверьте diff PNG перед коммитом. Для строгого сравнения используйте одинаковые ОС, JDK и версию Robolectric. На Windows JBR 21.0.10 повторный снимок Stealth совпал по SHA-256, а штатный StylePreview отличался от эталонов репозитория. Режим compare сохранил изображения; у Default Chats различия видны главным образом на краях и сглаживании.

Для нового экрана добавьте JVM-тест в `ui/screenshots` по образцу `StylePreviewScreenshotTest`. Передайте в публичную composable тестовые данные, используйте простой `Application`, зафиксируйте SDK 34, размер окна, язык, тему и состояние, затем вызывайте `compose.onRoot().captureRoboImage(...)`. Для загрузки и ошибок задайте воспроизводимые fake-состояния. Для стилей используйте `designFor` и `GlassQuality.Balanced`.

### Независимые PNG Stealth

```powershell
.\scripts\visual-tests.ps1 -StealthCaptureDir 'F:\BeyGram-Agents\Codex\reports\stealth-2-screens' -Stage after
```

Сценарий `StealthUiCapture` отрисовывает настоящий `StealthSettingsScreen` на синтетическом состоянии Maximum с двумя исключениями и 30-секундной задержкой. Размер 360 × 780 dp, xhdpi, русский язык, тёмная тема. `before` снимает Default, `after` — пять стилей и Liquid Glass V2. PNG сохраняются в указанной папке и лежат отдельно от эталонов Git. Имена файлов имеют вид `before-Default.png`, `after-Default.png`, `after-LiquidGlassV2.png`.

Первоначальные снимки получены на DEV `754dd219638962865c632bcffe4625730665b6dd` и ветке исключений `e27440fcfcff6070491ce1192b44b673135c559c`. Компактный вариант снят на `11b508023e23d60efdb031095b2695d47d62faa8`, его PNG лежат в подкаталоге `compact`. Для повторения на произвольном коммите используйте временный клон, перенесите туда `StealthUiCapture.kt` в `app/src/test/java/com/beykus/beygram/ui/screens/settings` и запустите:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Users\Beykus\AppData\Local\Android\Sdk'
$env:GRADLE_USER_HOME = 'F:\BeyGram-Agents\Codex\tmp\gradle'
$env:BEYGRAM_STEALTH_CAPTURE_DIR = 'F:\BeyGram-Agents\Codex\reports\stealth-2-screens'
$env:BEYGRAM_STEALTH_CAPTURE_STAGE = 'before'
.\gradlew.bat --no-daemon :app:testDevDebugUnitTest -Pscreenshots=record --tests '*StealthUiCapture'
```

Для второго коммита установите `BEYGRAM_STEALTH_CAPTURE_STAGE=after` и повторите команду Gradle. Robolectric подтверждает компоновку Compose по теме и токенам. Оптический эффект Liquid Glass V2 с реальным backdrop, TalkBack и производительность требуют проверки на Android.
