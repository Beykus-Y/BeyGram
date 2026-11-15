# BeyGram Overlay 2.0: инвентарь AlertDialog (DEV 28eafe81)

Сгенерировано grep по app/**/*.kt. Учитывает строки вызова `AlertDialog(` и импорт `material3.AlertDialog`. Аудит alias и полных имён выполнен в #890: прямых вызовов `AlertDialog` в app/src не осталось, в комментарии `ChatSettingsSheet.kt` и в KDoc `BeyGramDialog.kt` упоминается только имя.

| Файл | Вызовов `AlertDialog(` | Строки |
|---|---|---|
| ✅ BeyGramApp.kt · #886 | 7 | 1426 1450 1468 1513 3232 3305 3362 |
| ✅ ui/screens/bgmusic/BgMusicPreviewScreen.kt · #887 | 2 | 348 385 |
| ✅ ui/screens/chat/BotButtons.kt · #877 | 1 | 77 |
| ✅ ui/screens/chat/ChatExportDialog.kt · #879 | 6 | 134 253 290 306 335 366 |
| ✅ ui/screens/chat/ChatScreen.kt · #877 | 3 | 2338 2802 2881 |
| ✅ ui/screens/chat/ChatSearch.kt · #877 | 1 | 197 |
| ✅ ui/screens/chat/ComposerExtras.kt · #878 | 3 | 76 222 277 |
| ✅ ui/screens/chat/FormattingTextField.kt · #878 | 2 | 122 137 |
| ✅ ui/screens/chat/ForumTopicsScreen.kt · #879 | 2 | 227 242 |
| ✅ ui/screens/chat/MediaComposer.kt · #878 | 1 | 1316 |
| ✅ ui/screens/chat/MessageActions.kt · #877 | 1 | 686 |
| ✅ ui/screens/chat/MessageReportDialog.kt · #877 | 1 | 19 |
| ✅ ui/screens/chat/MessageTranslation.kt · #877 | 1 | 75 |
| ✅ ui/screens/chat/PaidContentViews.kt · #878 | 2 | 288 309 |
| ✅ ui/screens/chat/PaidReactionDialog.kt · #878 | 1 | 109 |
| ✅ ui/screens/chat/PinnedMessages.kt · #877 | 1 | 154 |
| ✅ ui/screens/chat/RecordingPreviewDialog.kt · #878 | 1 | 62 |
| ✅ ui/screens/chat/ScheduledMessages.kt · #879 | 2 | 86 374 |
| ✅ ui/screens/chat/SecretChatSettings.kt · #879 | 3 | 199 255 379 |
| ✅ ui/screens/chat/SelectionActionBar.kt · #877 | 1 | 126 |
| ✅ ui/screens/chat/SpecialMessageViews.kt · #879 | 3 | 269 338 415 |
| ✅ ui/screens/chat/StealthSendGuard.kt · #877 | 2 | 55 104 |
| ✅ ui/screens/chat/TextSendQueue.kt · #877 | 1 | 500 |
| ✅ ui/screens/chat/ViewOnceStealthGate.kt · #877 | 1 | 53 |
| ✅ ui/screens/chats/ChatActionsSheet.kt · #885 | 1 | 180 |
| ✅ ui/screens/chats/ChatsScreen.kt · #885 | 2 | 1043 1101 |
| ✅ ui/screens/chats/FolderManagerScreen.kt · #885 | 1 | 279 |
| ✅ ui/screens/chats/MarkReadConfirmation.kt · #885 | 1 | 45 |
| ✅ ui/screens/chats/NewChatScreen.kt · #886 | 1 | 322 |
| ✅ ui/screens/contacts/ContactsScreen.kt · #886 | 4 | 432 478 520 561 |
| ✅ ui/screens/diagnostics/DiagnosticsScreen.kt · #890 | 1 | 151 |
| ✅ ui/screens/diagnostics/ReportProblemScreen.kt · #890 | 1 | 182 |
| ✅ ui/screens/diagnostics/ResourcesScreen.kt · #890 | 1 | 119 |
| ✅ ui/screens/music/MusicPlaylistScreen.kt · #887 | 2 | 424 443 |
| ✅ ui/screens/music/MusicScreenSupport.kt · #887 | 1 | 368 |
| ✅ ui/screens/notifications/TriggerMentionsScreen.kt · #890 | 1 | 59 |
| ✅ ui/screens/notifications/TriggerWordsScreen.kt · #890 | 1 | 360 |
| ✅ ui/screens/playback/MusicPlayerSheet.kt · #888 | 2 | 809 1616 |
| ✅ ui/screens/playback/PlayerSoundSheet.kt · #888 | 1 | 352 |
| ✅ ui/screens/playback/ShareWave.kt · #888 | 1 | 106 |
| ✅ ui/screens/profile/ChatInfoMenu.kt · #882 | 2 | 52 100 |
| ✅ ui/screens/profile/GiftsScreen.kt · #884 | 1 | 533 |
| ✅ ui/screens/profile/GroupInvitesScreen.kt · #883 | 3 | 182 259 415 |
| ✅ ui/screens/profile/GroupMembersScreen.kt · #883 | 3 | 309 358 419 |
| ✅ ui/screens/profile/GroupSettingsScreen.kt · #882 | 1 | 323 |
| ✅ ui/screens/profile/PremiumScreen.kt · #884 | 2 | 895 1065 |
| ✅ ui/screens/profile/ProfilePhotoGallery.kt · #882 | 1 | 146 |
| ✅ ui/screens/profile/ProfileScreen.kt · #882 | 1 | 350 |
| ✅ ui/screens/profile/StarsScreen.kt · #884 | 2 | 624 698 |
| ✅ ui/screens/proxy/ProxyListScreen.kt · #890 | 1 | 266 |
| ✅ ui/screens/proxy/ProxyRestoreDialog.kt · #890 | 1 | 27 |
| ✅ ui/screens/proxy/SecondaryProxySettingsScreen.kt · #890 | 1 | 114 |
| ✅ ui/screens/settings/AccountSecurityScreen.kt · #880 | 3 | 845 953 1033 |
| ✅ ui/screens/settings/AccountSwitcherSheet.kt · #880 | 4 | 483 511 534 1127 |
| ✅ ui/screens/settings/ActiveSessionsScreen.kt · #880 | 1 | 149 |
| ✅ ui/screens/settings/AppLockSettingsScreen.kt · #880 | 2 | 394 529 |
| ✅ ui/screens/settings/BlockedSendersScreen.kt · #880 | 1 | 217 |
| ✅ ui/screens/settings/PhoneNumberChangeDialog.kt · #880 | 1 | 69 |
| ✅ ui/screens/settings/PrivacySettingsScreen.kt · #880 | 1 | 493 |
| ✅ ui/screens/settings/StealthSettingsScreen.kt · #880 | 1 | 278 |
| ✅ ui/screens/settings/StorageSettingsScreen.kt · #880 | 2 | 142 359 |
| ✅ ui/screens/settings/VideoMessageSettingsScreen.kt · #881 | 10 | 407 418 433 460 496 540 588 603 624 653 |
| ✅ ui/screens/stories/StoryArchiveViewerScreen.kt · #889 | 1 | 365 |
| ✅ ui/screens/stories/StoryPublisherScreen.kt · #889 | 1 | 923 |
| ✅ ui/screens/stories/StoryViewerScreen.kt · #889 | 2 | 886 1201 |
| ✅ ui/screens/theme/AppearanceDesign.kt · #889 | 1 | 435 |
| ✅ ui/screens/theme/ThemeDiscardDialog.kt · #889 | 1 | 15 |

Итого вызовов: 122, файлов: 68.

## Статус группы «переписка» (#870)

Файлы `ui/screens/chat/` из таблицы выше: 40 вызовов `AlertDialog` переведены на `BeyGramDialog`.
Отметка ✅ с номером PR стоит у каждого файла.

- #877: 14 вызовов, 11 файлов.
- #878: 10 вызовов, 6 файлов.
- #879: 16 вызовов, 5 файлов. Базовый `BeyGramDialog` получил параметр `secure`; диалог ключа секретного чата
  использует его, чтобы сохранить FLAG_SECURE.

Группы «список чатов», «настройки», «профили», «музыка» и «истории» в этих PR не трогались.

## Статус группы «настройки и аккаунты» (#870)

- #880 (часть 1): 16 вызовов, 9 файлов. Отмечено ✅ в таблице выше.
- #881 (часть 2): 10 вызовов, 1 файл (`VideoMessageSettingsScreen.kt`). Отмечено ✅ в таблице выше.
- Группа закрыта: 26 вызовов в 10 файлах.
- Группы «профили», «список чатов», «музыка», «истории» и «оформление» в этих PR не трогались.

## Статус группы «профили и группы» (#870)

- #882 (часть 1): ProfileScreen, ChatInfoMenu, ProfilePhotoGallery, GroupSettingsScreen. 5 вызовов в 4 файлах. Отмечено ✅ в таблице выше.
- Части 2 и 3: см. разделы ниже.

## Статус группы «профили и группы», часть 2 (#870, PR #883)

- GroupMembersScreen и GroupInvitesScreen: 6 вызовов в 2 файлах. Отмечено ✅ в таблице выше.
- Часть 3: см. раздел ниже.

## Статус группы «профили и группы», часть 3 (#870, PR #884)

- PremiumScreen, StarsScreen и GiftsScreen: 5 вызовов в 3 файлах. Отмечено ✅ в таблице выше.
- Группа закрыта после слияния #884: 16 вызовов в 9 файлах (части 1–3).
- Другие группы в этом PR не трогались.

## Статус группы «список чатов» (#870, PR #885)

- Часть 1: ChatsScreen.kt (подтверждение «прочитать все» и настройки смахивания), ChatActionsSheet.kt (удаление чата), FolderManagerScreen.kt (удаление папки), MarkReadConfirmation.kt (отметка прочитанным, которую отклонил режим скрытности). 5 вызовов в 4 файлах. Отмечено ✅ в таблице выше.
- Часть 2 (ContactsScreen.kt, NewChatScreen.kt, BeyGramApp.kt) и опция прокрутки содержимого в BeyGramDialog: отдельный PR после слияния части 1.

## Статус группы «список чатов», часть 2 (#870, PR #886)

- ContactsScreen.kt (добавление, изменение, удаление и отправка контакта), NewChatScreen.kt (предпросмотр приглашения) и BeyGramApp.kt (запросы на отправку из других приложений, пути входа по ссылке, уведомление). 12 вызовов в 3 файлах. Отмечено ✅ в таблице выше.
- В BeyGramDialog добавлен необязательный параметр scrollableContent (по умолчанию true). Диалог отправки контакта передаёт false, потому что его список уже прокручивается сам.
- Группа «список чатов» закрыта после слияния #885 и #886: 17 вызовов в 7 файлах (части 1 и 2).

## Статус группы «музыка», часть 1 (#870, PR #887)

- BgMusicPreviewScreen.kt (2), MusicPlaylistScreen.kt (2), MusicScreenSupport.kt (1): 5 вызовов в 3 файлах. Отмечено ✅ в таблице выше.
- В BeyGramDialog добавлены необязательные параметры neutralText и onNeutral: третье действие слева в ряду кнопок, для выбора «сохранить копию» при импорте BG Mix.
- Часть 2 (MusicPlayerSheet.kt, PlayerSoundSheet.kt, ShareWave.kt): отдельный PR после слияния части 1.

## Статус группы «музыка», часть 2 (#870, PR #888)

- MusicPlayerSheet.kt (2), PlayerSoundSheet.kt (1), ShareWave.kt (1): 4 вызова в 3 файлах. Отмечено ✅ в таблице выше.
- BeyGramDialog в этой части не менялся. Подтверждение таймера сна теперь в акцентном цвете, а не в цвете акцентного текста.
- Группа «музыка» закрыта после слияния #887 и #888: 9 вызовов в 6 файлах (части 1 и 2).

## Статус групп «истории» и «оформление», часть 1 (#870, PR #889)

- StoryPublisherScreen.kt (1), StoryArchiveViewerScreen.kt (1), StoryViewerScreen.kt (2): 4 вызова в 3 файлах. Подтверждение удаления и ответ на историю. Отмечено ✅ в таблице выше.
- AppearanceDesign.kt (1), ThemeDiscardDialog.kt (1): 2 вызова в 2 файлах. Отмечено ✅ в таблице выше.
- Часть 2 (proxy, diagnostics, notifications и остаток прямых вызовов): см. раздел ниже (PR #890).

## Статус части 2: proxy, diagnostics, notifications (#870, PR #890)

- DiagnosticsScreen.kt, ReportProblemScreen.kt, ResourcesScreen.kt: 3 вызова в 3 файлах. Предпросмотр отчёта и отчёт о ресурсах, `scrollableContent = false`, потому что содержимое уже прокручивается. Отмечено ✅ в таблице выше.
- TriggerMentionsScreen.kt, TriggerWordsScreen.kt: 2 вызова в 2 файлах. Очистка упоминаний и удаление правила, оба подтверждения `destructive`. Цвета диалога теперь из палитры BeyGramDialog. Отмечено ✅ в таблице выше.
- ProxyListScreen.kt, ProxyRestoreDialog.kt, SecondaryProxySettingsScreen.kt: 3 вызова в 3 файлах. Отмечено ✅ в таблице выше. У ProxyRestoreDialog появился заголовок `proxy_list_title`, вопрос остался текстом.
- Итог части 2: 8 вызовов в 8 файлах. Группы «истории», «оформление», «proxy», «diagnostics» и «notifications» закрыты, прямых вызовов `AlertDialog` в коде не осталось.
