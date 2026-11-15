# BeyGram Overlay 2.0: инвентарь AlertDialog (DEV 28eafe81)

Сгенерировано grep по app/**/*.kt. Учитывает строки вызова `AlertDialog(` и импорт `material3.AlertDialog`. Полный аудит alias/полных имён ещё впереди (подзадача миграции).

| Файл | Вызовов `AlertDialog(` | Строки |
|---|---|---|
| BeyGramApp.kt | 7 | 1426 1450 1468 1513 3232 3305 3362 |
| ui/screens/bgmusic/BgMusicPreviewScreen.kt | 2 | 348 385 |
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
| ui/screens/chats/ChatActionsSheet.kt | 1 | 180 |
| ui/screens/chats/ChatsScreen.kt | 2 | 1043 1101 |
| ui/screens/chats/FolderManagerScreen.kt | 1 | 279 |
| ui/screens/chats/MarkReadConfirmation.kt | 1 | 45 |
| ui/screens/chats/NewChatScreen.kt | 1 | 322 |
| ui/screens/contacts/ContactsScreen.kt | 4 | 432 478 520 561 |
| ui/screens/diagnostics/DiagnosticsScreen.kt | 1 | 151 |
| ui/screens/diagnostics/ReportProblemScreen.kt | 1 | 182 |
| ui/screens/diagnostics/ResourcesScreen.kt | 1 | 119 |
| ui/screens/music/MusicPlaylistScreen.kt | 2 | 424 443 |
| ui/screens/music/MusicScreenSupport.kt | 1 | 368 |
| ui/screens/notifications/TriggerMentionsScreen.kt | 1 | 59 |
| ui/screens/notifications/TriggerWordsScreen.kt | 1 | 360 |
| ui/screens/playback/MusicPlayerSheet.kt | 2 | 809 1616 |
| ui/screens/playback/PlayerSoundSheet.kt | 1 | 352 |
| ui/screens/playback/ShareWave.kt | 1 | 106 |
| ui/screens/profile/ChatInfoMenu.kt | 2 | 52 100 |
| ui/screens/profile/GiftsScreen.kt | 1 | 533 |
| ui/screens/profile/GroupInvitesScreen.kt | 3 | 182 259 415 |
| ui/screens/profile/GroupMembersScreen.kt | 3 | 309 358 419 |
| ui/screens/profile/GroupSettingsScreen.kt | 1 | 323 |
| ui/screens/profile/PremiumScreen.kt | 2 | 895 1065 |
| ui/screens/profile/ProfilePhotoGallery.kt | 1 | 146 |
| ui/screens/profile/ProfileScreen.kt | 1 | 350 |
| ui/screens/profile/StarsScreen.kt | 2 | 624 698 |
| ui/screens/proxy/ProxyListScreen.kt | 1 | 266 |
| ui/screens/proxy/ProxyRestoreDialog.kt | 1 | 27 |
| ui/screens/proxy/SecondaryProxySettingsScreen.kt | 1 | 114 |
| ✅ ui/screens/settings/AccountSecurityScreen.kt · #880 | 3 | 845 953 1033 |
| ✅ ui/screens/settings/AccountSwitcherSheet.kt · #880 | 4 | 483 511 534 1127 |
| ✅ ui/screens/settings/ActiveSessionsScreen.kt · #880 | 1 | 149 |
| ✅ ui/screens/settings/AppLockSettingsScreen.kt · #880 | 2 | 394 529 |
| ✅ ui/screens/settings/BlockedSendersScreen.kt · #880 | 1 | 217 |
| ✅ ui/screens/settings/PhoneNumberChangeDialog.kt · #880 | 1 | 69 |
| ✅ ui/screens/settings/PrivacySettingsScreen.kt · #880 | 1 | 493 |
| ✅ ui/screens/settings/StealthSettingsScreen.kt · #880 | 1 | 278 |
| ✅ ui/screens/settings/StorageSettingsScreen.kt · #880 | 2 | 142 359 |
| ui/screens/settings/VideoMessageSettingsScreen.kt | 10 | 407 418 433 460 496 540 588 603 624 653 |
| ui/screens/stories/StoryArchiveViewerScreen.kt | 1 | 365 |
| ui/screens/stories/StoryPublisherScreen.kt | 1 | 923 |
| ui/screens/stories/StoryViewerScreen.kt | 2 | 886 1201 |
| ui/screens/theme/AppearanceDesign.kt | 1 | 435 |
| ui/screens/theme/ThemeDiscardDialog.kt | 1 | 15 |

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
- Осталось: `VideoMessageSettingsScreen.kt`, 10 вызовов (часть 2).
- Группы «профили», «список чатов», «музыка», «истории» и «оформление» в этих PR не трогались.
