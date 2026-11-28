# Liquid Glass V2 — карта аудита поверхностей (#1358)

Документ фиксирует этап **E0** эпика #1358: инвентаризацию всех UI-поверхностей BeyGram Android по
отношению к материалу Liquid Glass V2, подтверждённые дефекты, корректные реализации, намеренные
исключения и места, которые нельзя оценить без физической проверки.

- База аудита: `DEV` = `7ebd32a79c10135138098905907b4391e8f36c70` (merge #1355).
- Целевой релиз: `0.0.7.1`. Issue остаётся открытым до физической приёмки владельцем.
- Аудит — чтение исходников; рендер и производительность на устройстве здесь **не** измерялись.
  Там, где вывод требует устройства, статус так и помечен.

## 1. Метод

1. Снят SHA `DEV`, получен список маршрутов (`AppRoute`, 63 маршрута) и четырёх корневых вкладок
   (`MainTab`: Chats, Calls, Contacts, Settings), включая two-pane раскладку.
2. Все 919 файлов `app/src/main/java/**.kt` просканированы на:
   `GlassPanel(`, `glassCard`, `glassSheet`, `glassHighlight`, `glassBackdropSource`, `LocalGlassBackdrop`,
   `LocalChatChromeOnGlass`, `LocalComposerOnGlassPlane`, `recordsGlassBackdrop`, `usesGlassPanels`,
   `BeyGramSheet(`, `BeyGramDialog(`, `BeyGramPopover(`, `ModalBottomSheet(`, `Popup(`, `Dialog(`,
   `Surface(`, `background(`, `graphicsLayer`, `blur(`, `renderEffect`, а также `warningSurface` /
   `errorSurface`.
3. Каждый найденный call site прочитан в контексте: владелец слоя, источник backdrop, наличие
   реального `blur`/`lens`, маскирующие заливки, риск двойной записи, окно (main / dialog / popup).
4. Имена компонентов взяты из исходников; ничего не додумано.

Сводка объёма:

| Показатель | Значение |
|---|---|
| `GlassPanel(` call sites | 103 упоминания / 22 файла |
| `glassCard` | 111 упоминаний / 41 файл |
| `glassSheet` (модификатор-имитация) | 4 файла: `AttachmentMenu`, `MediaComposer`, `StickerPanel`, `StickerPanelGif` |
| `glassBackdropSource` | 6 файлов |
| `BeyGramSheet(` call sites | 24 (все → один `ModalBottomSheet`) |
| `ModalBottomSheet(` напрямую | 1 (`BeyGramSheet.kt:64`) |
| `BeyGramDialog(` call sites | 139 |
| `BeyGramPopover(` call sites | 8 |
| `Popup(` напрямую | 3 (`BeyGramPopover`, `MessageActions`, `SelectionMenuProvider`) |

## 2. Как стекло устроено сейчас

### 2.1 Материал

- `ui/design/GlassSurface.kt:64` — `GlassBackdrop`: держит `GraphicsLayer`, `originInWindow`,
  `hasContent` и флаг `recording`.
- `GlassSurface.kt:93` — `Modifier.glassBackdropSource(backdrop)`: записывает поддерево в слой
  каждый кадр и сразу рисует его обратно. Пока идёт запись, `recording = true`, и панель внутри
  записываемого поддерева **не** семплирует слой (иначе render-thread падает).
- `GlassSurface.kt:136` — `GlassPanel`: единственный настоящий материал. При
  `GlassStyle.samplesBackdrop` (Quality = High) берёт слой, сдвигает его на
  `sampled.originInWindow - originInWindow`, накладывает `RenderEffect` (blur + vibrancy) и
  AGSL-линзу (`graphicsLayerWithBlur` / `lensLayer`), затем tint, rim и `glassHighlight`.
  Без источника рисует `GlassUnsampledMaskAlpha = 0.65` (`GlassSurface.kt:266`), то есть
  «плотную имитацию», а без backdrop — `alpha ≥ 0.93` (`GlassSurface.kt:293`).
- `GlassSurface.kt:401` — `Modifier.glassSheet`: **не стекло**. Это `background(fill, alpha 0.94)`
  + `glassHighlight` + горизонтальная линия. Используется как «стеклянная» поверхность там, где
  настоящей панели нет.
- `GlassSurface.kt:377` — `Modifier.glassCard`: tint `0.9` + обводка. По замыслу — карточка списка,
  без семплирования; это корректно и не является дефектом.
- `GlassSurface.kt:451` — `recordsGlassBackdrop`: `true` только для Liquid Glass + High (API 31+)
  или Balanced (frosted).

### 2.2 Режимы качества (`ui/design/DesignPresets.kt:164`–`177`)

| Режим | tint | blur | Семплирование | Линза |
|---|---|---|---|---|
| `High` | 0.48 | 30 dp | да, `RenderEffect` (API 31+) | да, AGSL (API 33+) |
| `Balanced` | 0.76 | — | да, frosted 1/8 без RenderEffect | нет |
| `Lite` | 0.96 | — | нет, матовая плита | нет |
| `Auto` | → `High`/`Balanced`/`Lite` по `autoGlassQuality` (`GlassQuality.kt`/`GlassCapabilities.kt`) | | | |

`Lite`/`Balanced` — **намеренный** fallback, а не дефект.

### 2.3 Где реально пишется backdrop

| Владелец | Файл:строка | Что записывается |
|---|---|---|
| Список чатов (шапка) | `chats/ChatsScreen.kt:1123` | список без шапки |
| Чат (шапка/composer) | `chat/ChatScreen.kt:2030` → `ChatGlassChrome.kt:119`–`169` | лента, обои, composer-плоскость |
| Плавающий док | `components/MainTabs.kt:815`–`854` | viewport обеих страниц |
| Шапка вкладки | `components/MainTabTitle.kt:172`–`179` | контент под шапкой |
| Превью оформления | `theme/AppearancePreview.kt`, `theme/StyleScenes.kt` | сцена превью |
| Корень приложения | `MainActivity.kt:242`, `267` | **ничего** — `glassBackdrop` создан и роздан, но `glassBackdropSource` на корне нет |

Последняя строка важна: любая `GlassPanel` вне экранов, которые сами пишут слой, получает
`hasContent = false` и рисует маску 0.65, то есть визуально «плотную» панель.

### 2.4 Ключевое ограничение

`Dialog`, `Popup` и `ModalBottomSheet` Material 3 живут в **отдельном окне**. Слой, записанный в
главном окне, физически не является частью содержимого окна диалога: координаты
`positionInWindow()` принадлежат разным окнам, а рисование чужого `RenderNode` в другом окне — не
поддерживаемый сценарий. Поэтому «обернуть `BeyGramSheet`/`BeyGramDialog` в `GlassPanel`» даёт
имитацию, а не стекло. Это подтверждено и самим кодом: `BeyGramPopover.kt:303`–`304` явно
передаёт `LocalGlassBackdrop provides null` в `Popup` с комментарием «Popup coordinates belong to a
different window from the host backdrop».

## 3. Подтверждённые дефекты (код)

| # | Поверхность / сценарий | Файл:строки | Сейчас | Backdrop | Blur / линза | Маскирующие заливки | Почему дефект |
|---|---|---|---|---|---|---|---|
| **D1** | Любой `BeyGramSheet` в Liquid Glass (24 call site) | `components/BeyGramSheet.kt:47`–`91`; `components/SurfaceColors.kt:30`–`39` | `ModalBottomSheet` в отдельном окне, `containerColor = surface @ 0.94` | нет | нет | сплошная заливка 0.94 | Заявлен как «dense glass», фактически непрозрачная плита; линзы/размытия нет |
| **D2** | Открытие набора стикеров (из сообщения, браузера паков, дока) — жалоба владельца №1 | `chat/StickerSetSheet.kt:192` | `BeyGramSheet` (см. D1) | нет | нет | 0.94 | Настоящего стекла нет |
| **D3** | Предпросмотры стикера / GIF / пака, меню паков — жалоба №2 | `chat/StickerPanel.kt:1969`–`2038` (`BeyGramSheet` на 1996) | `BeyGramSheet` (см. D1) | нет | нет | 0.94; в превью дополнительно `surface2 @ 0.45` (`StickerPanel.kt:2072`) | Стекло отсутствует, поверхность «устаревшая» |
| **D4** | Медиадок (эмодзи/стикеры/GIF) в shared-composer — жалоба №2 | `chat/StickerPanel.kt:1128`–`1147`; `chat/ChatGlassChrome.kt:119`–`158` | при `onSharedGlass` — `background(surface @ 0.25)` поверх общей стеклянной плоскости; иначе отдельная `GlassPanel` | наследуется (`LocalGlassBackdrop = null`) | наследуется от composer-панели | 25 % заливка + непрозрачная полоса табов `background(palette.surface2)` (`StickerPanel.kt:1346`) + `surface2` в поиске (`1330`) | Единственный стеклянный материал дока гасится заливками; при этом док рисует собственные непрозрачные полосы |
| **D5** | Меню прикрепления — жалоба №4 | `chat/AttachmentMenu.kt:105`–`170`; `components/ActionBubble.kt:78`–`84` | сетка `ActionBubble(compact = true)`: круги 38 dp с `glassCard` на каждую иконку | только `GlassPanel`-обёртка (152) при `!onSharedGlass`; часто наследует общий composer-план | наследуется | круглые подложки под каждой иконкой | Дизайн не соответствует согласованной концепции «одна общая компактная поверхность, иконки с подписями без отдельных подложек» |
| **D6** | Ошибки и предупреждения — жалоба №5 | `chat/ComposerExtraPickers.kt:76`–`83` → `AttachmentState.reportError` → `chat/MediaComposer.kt:1362` (`glassSheet`) и `1391`–`1406`; `chat/ChatScreen.kt:2067`, `2084`; `chat/MessageThreadScreen.kt:679`, `697`; `chat/ChatConnectionBanner.kt:87`–`92` | `glassSheet`-имитация и `background(warningSurface)` | нет | нет | сплошной `warningSurface` | Отказ в доступе к геопозиции и прочие ошибки выглядят как «плоская плашка», а не стекло |
| **D7** | Все модальные подтверждения/вводы (`BeyGramDialog`, 139 call site) | `components/BeyGramDialog.kt:81`–`163`, `Surface(color = palette.surface)` на 96 | Material `Surface` + Compose `Dialog` (отдельное окно) | нет | нет | полностью непрозрачный `palette.surface` | В Liquid Glass диалоги — единственный крупный класс поверхностей без материала; наивная обёртка в `GlassPanel` тут не работает (см. 2.4) |
| **D8** | Контекстное меню сообщения — риск, не дизайн | `chat/MessageActions.kt:398` (`Popup`), `MessageContextSurface` на `729` | `Popup` (отдельное окно) + `GlassPanel`, при этом `LocalGlassBackdrop` **не** обнулён | наследует `chatBackdrop` из главного окна | пытается рисовать чужой слой | — | `GlassPanel` в окне popup семплирует слой главного окна: координаты другого окна → неверный/пустой семпл. Нужен явный `provides null` (как в `BeyGramPopover.kt:304`). Дизайн меню при этом не меняется — это согласованное исключение |
| **D9** | Трёхточечные меню вне чата (`BeyGramPopover` в `Popup`) | `components/BeyGramPopover.kt:286`–`307` | `Popup` + `glassCard` (tint 0.9 + обводка) | нет | нет | заливка | Единственный хост `SameWindowPopoverOverlay` — в `ChatScreen.kt:2596`; на остальных экранах popover остаётся без стекла. Само меню трёх точек — согласованное исключение, поэтому здесь фиксируется несогласованность, а не задача «сделать стекло» |
| **D10** | `GlassPanel` без записанного backdrop | корневой `glassBackdrop` из `MainActivity.kt:242` нигде не пишется | маска `0.65` | `hasContent = false` | нет | — | Панель вне экранов со своим слоем не получает стекла и не сообщает об этом |

## 4. Корректные реализации

| # | Поверхность | Файл:строки | Что подтверждено |
|---|---|---|---|
| **C1** | Шапка списка чатов | `chats/ChatsScreen.kt:1120`–`1149` | список записан в отдельный слой, шапка — `GlassPanel` с `BottomHairline`, `lensOnFreeEdge`, `pointerInput`-барьер |
| **C2** | Шапка/composer чата | `chat/ChatGlassChrome.kt:84`–`169` | один план на стек; `sharedComposer` включает `LocalComposerOnGlassPlane = true` и `LocalGlassBackdrop = null`, чтобы не семплировать дважды |
| **C3** | Плавающий док навигации + мини-плеер | `components/MainTabs.kt:805`–`855` | запись viewport только когда `recordsGlassBackdrop`; панель и плеер вне записываемого поддерева |
| **C4** | Шапка вкладки | `components/MainTabTitle.kt:155`–`179` | `MainTabLayout` пишет только контент, шапка — sibling; комментарий про #623 |
| **C5** | Меню долгого нажатия на чат | `chats/ChatActionsSheet.kt:207`–`254` | **эталон in-window модала**: `BackHandler` + scrim + `GlassPanel` с реальным `glassBackdrop`, `zIndex(20f)` |
| **C6** | Меню трёх точек в чате | `components/BeyGramPopover.kt:120`–`201` | `SameWindowPopoverHost` рисует popover в главном окне, `GlassPanel` с реальным слоем |
| **C7** | Send-кнопка | `chat/ChatScreen.kt:2687`–`2707` | `GlassPanel` только если есть источник и не shared plane |
| **C8** | Плавающие карточки в чате (дата, jump-стек, закрепы) | `chat/FloatingMessageDate.kt:65`, `chat/ChatJumpStack.kt:248`,`350`, `chat/PinnedMessages.kt:708` | панели с семплированием `chatBackdrop` |
| **C9** | Карточки списков/настроек | 41 файл, `glassCard` | по замыслу — tint 0.9 + обводка без семплирования; это не стекло и не должно им быть |
| **C10** | Превью оформления | `theme/AppearancePreview.kt:318`,`331`, `theme/StyleScenes.kt:307` | реальные панели на собственной записанной сцене |

## 5. Намеренные исключения (не менять)

| # | Поверхность | Файл:строка | Обоснование |
|---|---|---|---|
| **X1** | Контекстное меню сообщения | `chat/MessageActions.kt:398`, `729` | согласовано в #1238 S; менять дизайн нельзя (D8 — только safety-фикс семплирования) |
| **X2** | Меню долгого нажатия на чат | `chats/ChatActionsSheet.kt` | согласовано; уже настоящее стекло, не переделывать |
| **X3** | Индикатор «печатает» | `chat/ChatPresence.kt:136`–`159` | согласованный plain-элемент |
| **X4** | Меню трёх точек | `components/BeyGramPopover.kt` | согласовано; в чате уже стекло |
| **X5** | Карточки сообщений, изображения, медиа-контент | `chat/MessageBubbleLayout.kt`, `chat/MediaViews.kt` | контент не получает стекло |
| **X6** | Камера, редакторы, просмотр медиа | `chat/CameraCaptureScreen.kt`, `chat/PhotoEditorScreen.kt`, `chat/MediaViews.kt` | собственный тёмный chrome поверх контента |
| **X7** | Системные окна разрешений Android | — | не кастомизируются |
| **X8** | Другие темы (Material 2/3, Signature, Default) | `ui/design/DesignPresets.kt` | стекло не добавляется; меняется только layout скрепки |

## 6. Требует физической проверки

| # | Что проверить | Почему нельзя закрыть кодом |
|---|---|---|
| **V1** | Реальный blur/lens на Samsung M21 (Android 16) | `RenderEffect`/AGSL зависят от GPU; эмулятор и CI этого не показывают |
| **V2** | Поведение in-window модала при IME (adjustResize + edge-to-edge) | позиция sheet относительно клавиатуры проверяется только на устройстве |
| **V3** | Back / predictive back / drag-dismiss / scrim для нового модала | жестовая навигация и TalkBack на устройстве |
| **V4** | Frame time и PSS при открытых панелях | нужны измерения, а не зелёный CI |
| **V5** | Поворот, font scale, узкие/широкие экраны | визуальная геометрия линзы |
| **V6** | Секретные чаты и `FLAG_SECURE` при новых модалах | приватность подтверждается только вручную |
| **V7** | Отсутствие двойного размытия на медиадоке | нужен глаз на устройстве |

## 7. Целевая архитектура (E1)

Одна плоскость материала на поверхность, реальный источник под ней, никаких циклов
самосемплирования, отсутствие чужих окон:

1. **In-window модальный хост.** Общий `BeyGramModalHost` (по образцу уже работающего
   `ChatActionsSheet`/`SameWindowPopoverHost`) рисует sheet и dialog внутри главного окна:
   scrim, `BackHandler`, drag/fling-dismiss, IME-insets, `paneTitle`, focus/semantics-барьер для
   контента под модалом. Материал — `GlassPanel` с настоящим `LocalGlassBackdrop`.
2. **Корневая запись backdrop по требованию.** `glassBackdrop` из `MainActivity` записывается
   только пока модал открыт; оверлей рисуется вне записываемого поддерева. Нулевая цена, когда
   ничего не открыто.
3. **Единый выбор материала.** `samplesBackdrop`/`samplesFrosted`/`Lite` уже описаны в
   `GlassStyle`; имитационный `glassSheet` остаётся только для не-Liquid Glass и для мест, где
   источник физически недоступен, и больше не выдаётся за стекло.
4. **Изоляция окон.** В любом `Dialog`/`Popup`/`ModalBottomSheet`, который остаётся отдельным
   окном (secure-диалоги, системные случаи), `LocalGlassBackdrop` явно `null`.
5. **Уведомления.** Общая стеклянная поверхность уведомлений/ошибок с сохранением
   `warningSurface`-семантики, контраста, `liveRegion`, retry/dismiss.

## 8. План работ

| Этап | Содержание | Статус |
|---|---|---|
| E0 | этот документ + отчёт в #1358 | готово |
| E1 | in-window модальный хост, `BeyGramSheet`/`BeyGramDialog` на настоящем стекле, изоляция окон | PR #1360 |
| E2 | уведомления/ошибки, медиадок, наборы/превью/меню паков | PR #1360 |
| E3 | меню скрепки (Attachment Menu 2.0) | PR #1360 |
| E4 | регрессионные тесты, профиль, матрица тем, отчёт | тесты в #1360; профиль и матрица — за владельцем |
| **F1** | **R1–R3: общая стеклянная шапка, тред, окно пересылки** | **PR #1362** |

## 8.1. Что сделано в E1–E3 (PR #1360)

| Дефект | Как исправлено |
|---|---|
| **D1** | `BeyGramModalHost` рисует sheet внутри окна и семплирует реальный backdrop; Material-окно осталось только для тем без стекла |
| **D2**, **D3** | окно набора и предпросмотры идут через тот же `BeyGramSheet` → настоящая панель, линза и blur |
| **D4** | убрана заливка `surface @ 0.25` поверх общей плоскости; сегменты табов стали полупрозрачными, выделение сохраняет accent |
| **D5** | новое меню скрепки: одна поверхность, иконка + подпись без круглых подложек, сетка 3–6 колонок, touch target ≥ 60 dp |
| **D6** | `GlassNotice` (Info/Warning/Danger): настоящая панель при собственном backdrop, тональная заливка на общей плоскости, прежняя плоская полоса в остальных темах; применено к ошибкам composer, чата, треда и баннеру соединения |
| **D7** | `BeyGramDialog` в Liquid Glass — hosted dialog; `secure = true` остаётся в отдельном окне ради `FLAG_SECURE` |
| **D8** | `MessageActions` больше не семплирует слой главного окна в `Popup` (`LocalGlassBackdrop provides null`) |
| **D10** | у модала теперь собственный записываемый backdrop; корневой `glassBackdrop` не тронут |

## 8.2. Остаточные разрывы (после F1 / PR #1362)

Закрыты в F1: **R1** (тред и комментарии каналов), **R2** (шапки 44 вторичных экранов), **R3** (`ForwardChatDialog`).

### F1.1. Общий механизм

`ui/components/SecondaryScreenChrome.kt`:

- `GlassScreenScaffold(toolbar, bottomBar, content)` — одна стеклянная шапка (и, при необходимости, нижняя панель) на всё приложение. Записывается только тело; панели — сиблинги, нарисованные после него, поэтому панель не семплирует слой, в который пишется.
- `ScreenChromeInsets(top, bottom)` — измеренная высота панелей, которую тело кладёт внутрь своего скролла (`contentPadding` / `Spacer`), чтобы строки уходили **под** стекло.
- `LocalScreenChromeOnGlass`, `Modifier.screenChromeBar()`, `screenChromeBottomInsets()` — бар на панели не рисует свою заливку и не добавляет второй раз IME/навигацию.
- `ProfileToolbar` в режиме `TopBarVariant.Glass` больше не рисует `panelFill`: на панели он прозрачный.
- Вне Liquid Glass и вне сэмплирующих режимов (`Lite`, старый Android) структура прежняя: тулбар сверху, тело ниже, ничего не записывается.

### F1.2. R1 — тред и комментарии каналов

`MessageThreadScreen` переведён на «один стеклянный тулбар + один стеклянный composer»: комментарии прокручиваются под обеими панелями, уведомления очереди и ошибки отправки живут в нижней панели над composer, сохранены черновик, `replyTarget`, фокус, `sendGuard`, обратный список, загрузка старых комментариев, реакция-пикер и мини-плеер.

### F1.3. R2 — вторичные экраны (44 файла)

`AccountSecurityScreen`, `ActiveSessionsScreen`, `AppLockSettingsScreen`, `ArchiveScreen`, `AutoDownloadSettingsScreen`, `BlockedSendersScreen`, `ChannelStatisticsScreen`, `ChatMembersScreen`, `ConnectionDiagnosticsScreen`, `CreateChannelScreen`, `DiagnosticsScreen`, `EqualizerScreen`, `ExperimentsScreen`, `FolderManagerScreen`, `GiftsScreen`, `GlobalSearchScreen`, `GroupInvitesScreen`, `GroupJoinRequestsScreen`, `GroupManagementScreen`, `GroupMembersScreen`, `GroupSettingsScreen`, `LanguageScreen`, `LastSeenSettingsScreen`, `MyMusicScreen`, `NavigationSettingsScreen`, `NewChatScreen`, `NotificationSettingsScreen`, `PremiumScreen`, `PrivacySettingsScreen`, `ReportProblemScreen`, `ResourcesScreen`, `StableUpdatesScreen`, `StarsScreen`, `StealthExceptionsScreen`, `StealthSettingsScreen`, `StorageSettingsScreen`, `StoryPublisherScreen`, `ThemeSettingsScreen`, `TriggerMentionsScreen`, `TriggerWordsScreen`, `VideoMessageSettingsScreen`, `YandexSettingsHost`, а также экран редактирования профиля в `ProfileScreen`.

Все — через один `GlassScreenScaffold`: настоящая панель, записанное тело, blur и AGSL-линза из E1, без отдельной реализации стекла на экран.

### F1.4. R3 — окно пересылки

`ForwardChatDialog` больше не `glassEligible = false`: причина (двойной `imePadding`) устранена через `LocalSheetOnGlass`. Внутри панели `Scaffold` прозрачный, IME-вставки остаются только у контента, панель ограничена пространством над клавиатурой. Поиск, выбор получателей, темы, ограничения и отправка не менялись.

### F1.5. R5 — проверка модала в корне окна

| Что проверялось | Результат |
|---|---|
| CompositionLocal | контент модала получает значения окна (тема, палитра, motion, дизайн, метрики чата, обои, плотность); чат-скоупные читаются как `false`, что для модала корректно (он не на общей плоскости) |
| Контекст аккаунта и чата | модал живёт в том же composition и пересоздаётся вместе с `key(selectedAccountId, routeTwoPane)`; при смене аккаунта хост сбрасывается |
| Lifecycle | маршрут модала снимается через `DisposableEffect`; закрытие экрана при открытом модале убирает модал вместе с вызывающей композицией |
| Фокус | контент под модалом теряет семантику и фокус, пока модал открыт |
| Приватность / `FLAG_SECURE` | `secure = true` всегда остаётся в отдельном окне; в модал другого окна ничего не регистрируется (`BeyGramModalHost.ownsWindow`) |
| Вложенные модальные окна | sheet → dialog рисуются слоями 30/40; модал внутри `Dialog`/`Popup` остаётся в своём окне |

### F1.6. Что осталось

| # | Поверхность | Почему | Что нужно |
|---|---|---|---|
| **R2a** | Карточка профиля (`ProfileScreen` ChatInfo) и `GroupProfile` | Сворачивающаяся шапка со shared-element геометрией (#999): панель требует пере-настройки замеров и проверки на устройстве | Отдельная задача: перенести их на `GlassScreenScaffold` с проверкой collapse/flight на устройстве |
| **R2b** | `MusicSearchScreen`, `MusicSettingsScreen`, `MusicHomeUi`, `YandexCollectionScreen`, `MusicPlaylistScreen` | Свои `Scaffold`/прозрачные оболочки с собственными вставками; `MyMusicScreen` уже переведён как образец | По одному экрану тем же способом |
| **R4** | Панель выделения текста | Системоподобный контрол в `Popup`, не притворяется стеклом | Оставлено по решению владельца |
| **R6** | `ProxyListScreen`, `ProxyConnectScreen` | Своя шапка без `ProfileToolbar` | Перевести на общий механизм, если владелец сочтёт нужным |

## 9. Проверки E4, которые выполнены и которые остались

### F1 (PR #1362): что добавлено

- `SecondaryScreenChromeTest` (7 тестов): панели — настоящие `GlassPanel`, тело получает их высоту, отсутствие стекла вне Liquid Glass, `Lite` без записи, бар не рисует свою заливку на панели, все пять тем, нижняя панель, пересчёт при изменении высоты шапки.
- `SecondaryScreenGlassUiTest` (5 тестов): реальный вторичный экран (`PrivacySettingsScreen`) в Liquid Glass (панель + заголовок + Back), Material 3 (панели нет), Default, Material 2, Signature.
- Локально: `:app:testDevDebugUnitTest` — 4641+ тест, падения только известного baseline (Roborazzi-эталоны + TDLib/Windows + `TextSendCorruptRecoveryTest`).

### Выполнено (локально, Windows, JDK 17, offline)

- `:app:compileDevDebugKotlin` — чисто.
- Полный `:app:testDevDebugUnitTest`: **4641 тест, 137 падений, 19 пропущено**. Все 137 — известный baseline этой машины, воспроизводится на чистом `origin/DEV`:
  - Roborazzi-эталоны (85): `StyleMatrixScreenshotTest` 30, `WideLayoutScreenshotTest` 30, `StylePreviewScreenshotTest` 15, `MusicHomeScreenshotTest` 10 — сравнение с эталонами, снятыми в Linux CI;
  - TDLib (48): `TdSecondarySessionCoordinatorTest`, `TdAccountRegistryTest`, `TdAccountUpdateRoutingTest`, `TdAccountNativeClientOwnerTest`, `TdAccountStorageLayoutTest`, `TdAccountSessionStateTest` — временные каталоги Windows;
  - `ChatMemoryRecorderLifecycleTest` 3 и `TextSendCorruptRecoveryTest` 1 — тот же класс проблем окружения.
- Ни один тест стекла, модалов, скрепки, уведомлений, шапок или медиадока не падает.
- `git diff --check` — чисто.
- screenshot-эталоны **не обновлялись**: сцены набора (`StyleScenes`) рисуют синтетические компоненты, а не мигрированные экраны, поэтому их эталоны не менялись.

### Не выполнено и почему

- **Реальный blur/AGSL, IME, predictive back, drag, frame time, PSS** — нужен Samsung M21 владельца; эмулятор и CI этого не показывают. CI зелёного статуса здесь недостаточно. Для F1 особенно важно проверить: шапку и composer треда с клавиатурой, прокрутку строк под панелью, окно пересылки с поиском и клавиатурой, отсутствие двойного размытия.
- **Матрица тем × качества × light/dark × ориентация × font scale** — частично покрыта Roborazzi-набором, но эталоны на этой машине не совпадают; сравнение валидно только в CI/Linux. Владельцу нужен проход глазами на устройстве.
- **Сравнение производительности с build 832** — требует инструментированного замера на устройстве (open latency, p95/p99 frame time/jank, PSS/GC, GraphicsLayer и RenderEffect counts, 20–50 циклов открытия/закрытия).

## 10. Что этот документ не утверждает

- Он не подтверждает визуальную корректность: рендер на устройстве не выполнялся.
- Он не подтверждает производительность: замеров нет.
- Он не закрывает #1358, #1342, #1343, #1238 — это делает владелец после физической приёмки.