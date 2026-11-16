# BeyGram Overlay 2.0: инвентарь листов и меню (#870, вторая половина)

База: DEV 10853910 (после PR #890). В `app/src` нет прямых вызовов `AlertDialog`: диалоги уже на `BeyGramDialog`.

Этот документ покрывает то, что осталось в #870: нижние листы (`ModalBottomSheet`) и выпадающие меню (`DropdownMenu`, `Popup`). Инвентарь собран поиском по коду: `git grep` по `app/src` на DEV 10853910.

## 1. Компоненты (PR 1)

| Компонент | Файл | Что делает |
|---|---|---|
| `BeyGramSheet` | `ui/components/BeyGramSheet.kt` | Нижний лист на `ModalBottomSheet`. Фон `sheetSurfaceColor()`, верхние скругления `styledTopShape(28.dp)`, свой drag handle, заголовок по желанию, прокрутка `scrollableContent` (выключить для `LazyColumn`). |
| `BeyGramPopover` | `ui/components/BeyGramPopover.kt` | Меню у якоря на `Popup`. Позиция от якоря (`alignEnd`, `offset`), границы окна, клавиатура (IME), выход вверх, когда снизу нет места. Анимация `SheetEnter`/`SheetExit` с учётом Motion Off и reduced motion. Стекло (`glassCard`) или фон группы. Высота до 60% экрана, длинное содержимое прокручивается. |
| `BeyGramPopoverItem` | `ui/components/BeyGramPopover.kt` | Строка меню: `leadingIcon`, `enabled`, `danger`. Текст приходит строкой из string resources. |

Новых токенов Motion нет: используются `SheetEnter` и `SheetExit`, как в `ChatPeekSheet`. Чистая функция расположения `popoverOffset` покрыта JUnit-тестом `BeyGramPopoverPlacementTest`.

## 2. Правила миграции (PR 2 и далее)

- `ModalBottomSheet(onDismissRequest = d, containerColor = sheetSurfaceColor()) { … }` → `BeyGramSheet(onDismissRequest = d) { … }`. Если заданы `sheetState` или другие параметры, они переносятся. Для контента с `LazyColumn` ставить `scrollableContent = false`.
- `DropdownMenu(expanded = e, onDismissRequest = d) { … }` → `BeyGramPopover(expanded = e, onDismissRequest = d) { … }`. Меню остаётся внутри `Box` с кнопкой-якорем. Если было `offset = DpOffset(...)`, он переносится.
- `DropdownMenuItem(text = { Text(X) }, leadingIcon = …, onClick = …)` → `BeyGramPopoverItem(text = X, leadingIcon = …, onClick = …)`. Для опасного действия `danger = true`.
- Тексты, условия показа и бизнес-логика не меняются. Меняется только визуальный слой.
- После каждой группы в затронутых файлах не должно остаться `ModalBottomSheet(`, `DropdownMenu(` и `DropdownMenuItem(`, кроме явно оставленных мест из раздела 5.

## 3. Листы (ModalBottomSheet): 16 мест

| Файл:строка | Сценарий | PR |
|---|---|---|
| `screens/chats/ChatActionsSheet.kt:79` | действия с чатом из списка | 2 |
| `screens/chat/ScheduledMessages.kt:220` | отложенные сообщения | 5 |
| `screens/chat/ChatSettingsSheet.kt:159` | настройки чата | 5 |
| `screens/chat/ChatMemoryViews.kt:44` | память чата | 5 |
| `screens/chat/StickerPanel.kt:1578` | панель стикеров (большой файл, `LazyColumn`) | 6 |
| `screens/chat/StickerSetSheet.kt:193` | набор стикеров | 6 |
| `screens/chat/BotCommandMenu.kt:54` | команды бота | 6 |
| `screens/music/AddToPlaylistSheet.kt:69` | добавить в плейлист | 7 |
| `screens/music/ReplacementSheet.kt:88` | замена трека | 7 |
| `screens/music/yandex/YandexWaveSettingsSheet.kt:85` | настройки «Моей волны» | 8 |
| `screens/bgmusic/BgMusicExportSheet.kt:143` | экспорт BG Mix | 8 |
| `screens/playback/MusicPlayerSheet.kt:219` | плеер: очередь, BG Mix | 10 |
| `screens/playback/NowPlayingBar.kt:753` | мини-плеер | 10 |
| `screens/playback/PlayerSoundSheet.kt:209` | звук и таймер сна (по #870: интерактивный sheet с пресетами) | 10 |
| `screens/profile/ProfileMusic.kt:178` | музыка профиля | 11 |
| `screens/settings/AccountSwitcherSheet.kt:386` | переключение аккаунта | 12 |

## 4. Меню (DropdownMenu): 26 мест в 20 файлах

| Файл:строка | Сценарий | PR | Решение |
|---|---|---|---|
| `screens/chats/ChatsScreen.kt:644, 1111, 1128` | меню списка чатов, левый и правый свайп-выбор | 2 | popover |
| `screens/chat/MessageActions.kt:166` | «ещё» у сообщения | 3 | popover (по #870: компактное меню рядом с контекстом) |
| `screens/chat/ChatScreen.kt:3116` | меню чата | 3 | popover; подтверждения остаются диалогами |
| `screens/chat/ChatSearch.kt:212` | фильтр отправителя | 3 | popover (один выбор из списка) |
| `screens/chat/MediaViews.kt:1217` | действия с медиа | 4 | popover |
| `screens/chat/MediaComposer.kt:1643` | меню композера медиа | 4 | popover |
| `screens/chat/ForumTopicsScreen.kt:370` | меню темы форума | 4 | popover; удаление остаётся `BeyGramDialog` |
| `screens/music/MusicPlaylistScreen.kt:204, 320` | меню плейлиста и трека | 7 | popover; сортировка по #870 тоже popover |
| `screens/music/yandex/YandexCollectionScreen.kt` (2) | меню треков Яндекса | 9 | popover |
| `screens/music/yandex/YandexMusicHost.kt` (1) | меню хоста Яндекса | 9 | popover |
| `screens/playback/MusicSearchScreen.kt` (1) | меню результата поиска | 9 | popover |
| `screens/music/ProviderTrackUi.kt` (пункты) | общие пункты меню трека Яндекса и поиска | 9 | `BeyGramPopoverItem` вместе с хостами |
| `screens/playback/MusicPlayerSheet.kt` (3) | меню плеера | 10 | popover |
| `screens/playback/PlaybackSpeedMenu.kt` (1) | скорость воспроизведения | 11 | popover |
| `screens/profile/ChatInfoMenu.kt` (1) | меню информации о чате | 11 | popover |
| `screens/profile/ProfileMusic.kt` (1) | меню музыки профиля | 11 | popover |
| `screens/settings/AccountSwitcherSheet.kt` (1) | меню аккаунта | 12 | popover; выход и удаление остаются диалогами |
| `screens/settings/PrivacySettingsScreen.kt` (1) | меню исключений приватности | 12 | popover |
| `screens/settings/BlockedSendersScreen.kt` (1) | меню заблокированного | 12 | popover |
| `screens/settings/AutoDownloadSettingsScreen.kt` (1) | меню автозагрузки | 13 | popover |
| `screens/contacts/ContactsScreen.kt:374` | меню контакта | 13 | popover |

## 5. Вне этой серии (оставляем)

- `screens/chat/MessageActions.kt:340`: `Popup` контекстной карточки сообщения с реакциями на полноэкранной подложке. Это собственный компонент, не `DropdownMenu`. Пересматривать отдельно.
- `screens/chat/SelectionMenuProvider.kt:53`: панель выделения текста на `Popup` с собственной позицией. Это меню текста, оставляем.
- `screens/chats/ChatPeekSheet.kt`: предпросмотр чата на `Dialog` с fade и scale. Уже использует Motion и свою карточку.
- `screens/chat/AttachmentMenu.kt`: панель вложений, не `ModalBottomSheet`. Меняется отдельной задачей «Прикрепить» (план `attach-and-chat-menu-plan`), поэтому в эту серию не входит.

## 6. Группы миграции

| PR | Группа | Что входит |
|---|---|---|
| 2 | Список чатов | `ChatActionsSheet`, меню `ChatsScreen` (3) |
| 3 | Меню чата и сообщения | `MessageActions`, `ChatScreen`, `ChatSearch` |
| 4 | Медиа и форум | `MediaViews`, `MediaComposer`, `ForumTopicsScreen` |
| 5 | Листы чата | `ScheduledMessages`, `ChatSettingsSheet`, `ChatMemoryViews` |
| 6 | Стикеры и бот | `StickerPanel`, `StickerSetSheet`, `BotCommandMenu` |
| 7 | Плейлисты | `AddToPlaylistSheet`, `ReplacementSheet`, меню `MusicPlaylistScreen` (2) |
| 8 | Листы Яндекса и BG Mix | `YandexWaveSettingsSheet`, `BgMusicExportSheet` |
| 9 | Меню Яндекса и поиска | `YandexCollectionScreen`, `YandexMusicHost`, `MusicSearchScreen`, `ProviderTrackUi` |
| 10 | Плеер | `MusicPlayerSheet` (лист и 3 меню), `NowPlayingBar`, `PlayerSoundSheet` |
| 11 | Скорость и профиль | `PlaybackSpeedMenu`, `ChatInfoMenu`, `ProfileMusic` (лист и меню) |
| 12 | Аккаунты и приватность | `AccountSwitcherSheet` (лист и меню), `PrivacySettingsScreen`, `BlockedSendersScreen` |
| 13 | Автозагрузка и контакты | `AutoDownloadSettingsScreen`, `ContactsScreen` |

Каждый PR идёт в DEV. Перед слиянием проверяются сборка в CI, ревью отдельной сессией и push-run «Android DEV channel».
