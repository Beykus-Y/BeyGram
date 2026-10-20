package com.beykus.beygram

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.beykus.beygram.data.archive.ArchiveSettingsStore
import com.beykus.beygram.data.tdlib.TdLibBootstrap
import com.beykus.beygram.data.tdlib.TdLibReadiness
import com.beykus.beygram.data.tdlib.TdLibTelegramRepository
import com.beykus.beygram.data.unavailable.UnavailableTelegramRepository
import com.beykus.beygram.domain.UiText
import com.beykus.beygram.domain.UserMessageException
import com.beykus.beygram.domain.model.ArchiveSettings
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.navigation.AppRoute
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.navigation.MainTabPreferences
import com.beykus.beygram.navigation.TabVisibility
import com.beykus.beygram.domain.model.ChatPreview
import com.beykus.beygram.playback.PlaybackEngine
import com.beykus.beygram.ui.components.MainTabsScaffold
import com.beykus.beygram.ui.screens.auth.LoginCodeScreen
import com.beykus.beygram.ui.screens.auth.LoginPasswordScreen
import com.beykus.beygram.ui.screens.auth.LoginPhoneScreen
import com.beykus.beygram.ui.screens.archive.ArchiveScreen
import com.beykus.beygram.ui.screens.calls.CallsScreen
import com.beykus.beygram.ui.screens.chat.ChatScreen
import com.beykus.beygram.ui.screens.chats.ChatsScreen
import com.beykus.beygram.ui.screens.chats.CreateChannelScreen
import com.beykus.beygram.ui.screens.chats.GlobalSearchScreen
import com.beykus.beygram.ui.screens.contacts.ContactsScreen
import com.beykus.beygram.ui.screens.diagnostics.ConnectionDiagnosticsScreen
import com.beykus.beygram.ui.screens.diagnostics.DiagnosticsScreen
import com.beykus.beygram.ui.screens.diagnostics.ResourcesScreen
import com.beykus.beygram.ui.screens.diagnostics.ReportProblemScreen
import com.beykus.beygram.ui.screens.settings.LastSeenSettingsScreen
import com.beykus.beygram.ui.screens.settings.NavigationSettingsScreen
import com.beykus.beygram.ui.screens.settings.StorageSettingsScreen
import com.beykus.beygram.ui.screens.settings.VideoMessageSettingsScreen
import com.beykus.beygram.ui.screens.settings.SettingsScreen
import com.beykus.beygram.ui.screens.settings.ActiveSessionsScreen
import com.beykus.beygram.ui.screens.settings.LanguageScreen
import com.beykus.beygram.ui.screens.settings.StableUpdatesScreen
import com.beykus.beygram.ui.screens.start.StartScreen
import com.beykus.beygram.ui.screens.profile.ChatMembersScreen
import com.beykus.beygram.ui.screens.profile.ProfileScreen
import com.beykus.beygram.ui.screens.theme.AppearanceDraftSaver
import com.beykus.beygram.ui.screens.theme.ThemeEditorScreen
import com.beykus.beygram.ui.screens.theme.ThemeSettingsScreen
import com.beykus.beygram.ui.screens.profile.EditProfileScreen
import com.beykus.beygram.domain.model.NotificationOpenRequest
import com.beykus.beygram.notifications.NotificationRuntime
import com.beykus.beygram.ui.screens.notifications.NotificationSettingsScreen
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.data.media.VideoMessageSettingsStore
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.presence.LastSeenSettingsStore
import com.beykus.beygram.ui.screens.proxy.ProxyConnectScreen
import com.beykus.beygram.ui.screens.proxy.ProxyConnectTab
import com.beykus.beygram.ui.screens.proxy.ProxyListScreen
import com.beykus.beygram.ui.screens.proxy.ProxyRestoreDialog
import com.beykus.beygram.ui.screens.notifications.NotificationPermissionCoordinator
import com.beykus.beygram.share.IncomingShare
import com.beykus.beygram.share.ShareSessions
import com.beykus.beygram.ui.screens.share.ShareTargetScreen
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.beykus.beygram.ui.screens.playback.NowPlayingBar
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.launch

/** Экраны, где мини-плеер встроен в свою разметку: над нижним меню или над полем ввода. На остальных экранах он стоит снизу. */
private val playerBarHostRoutes = setOf(AppRoute.Chats, AppRoute.Calls, AppRoute.Contacts, AppRoute.Settings, AppRoute.Chat)

@Composable
fun BeyGramApp(
    appearance: AppearanceConfig,
    onAppearanceChange: (AppearanceConfig) -> Unit,
    notificationRequest: NotificationOpenRequest? = null,
    onNotificationHandled: (Long) -> Unit = {},
    incomingShare: IncomingShare? = null,
    onIncomingShareHandled: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val videoMessageSettings = remember(context) { VideoMessageSettingsStore(context) }
    val lastSeenSettings = remember(context) { LastSeenSettingsStore(context) }
    val tdLibReadiness = remember(context) {
        TdLibBootstrap(context).readiness()
    }
    val notificationRuntime = remember(context) { NotificationRuntime.get(context) }
    val repository = remember(context, tdLibReadiness) {
        if (tdLibReadiness.canCreateClient) {
            TdLibTelegramRepository.getInstance(context.applicationContext)
        } else {
            UnavailableTelegramRepository()
        }
    }
    // Бэкенд прокси живёт в процессе: его область работы — область приложения, а не экрана.
    val vpnBackend = remember(context, repository) {
        VpnBackend.getInstance(
            context = context.applicationContext,
            connectionState = repository.connectionState,
            scope = (context.applicationContext as BeyGramApplication).applicationScope,
            setTdLibProxy = repository::setMihomoProxyEnabled,
            isTdLibProxyEnabled = repository::isMihomoProxyEnabled
        )
    }
    // A background restore that could not start the listener is retried here, where the system allows starting it.
    LaunchedEffect(vpnBackend) { vpnBackend.restoreFromScreen() }
    DisposableEffect(repository, notificationRuntime) { notificationRuntime.attach(repository); onDispose { } }

    BeyGramAppContent(
        repository = repository,
        tdLibReadiness = tdLibReadiness,
        appearance = appearance,
        onAppearanceChange = onAppearanceChange,
        notificationRuntime = notificationRuntime,
        vpnBackend = vpnBackend,
        recordingSettings = videoMessageSettings,
        lastSeenSettings = lastSeenSettings,
        notificationRequest = notificationRequest,
        onNotificationHandled = onNotificationHandled,
        incomingShare = incomingShare,
        onIncomingShareHandled = onIncomingShareHandled
    )
}

@Composable
private fun BeyGramAppContent(
    repository: TelegramRepository,
    tdLibReadiness: TdLibReadiness,
    appearance: AppearanceConfig,
    onAppearanceChange: (AppearanceConfig) -> Unit,
    notificationRuntime: NotificationRuntime,
    vpnBackend: VpnBackend,
    recordingSettings: VideoMessageSettingsStore,
    lastSeenSettings: LastSeenSettingsStore,
    notificationRequest: NotificationOpenRequest?,
    onNotificationHandled: (Long) -> Unit,
    incomingShare: IncomingShare?,
    onIncomingShareHandled: (Long) -> Unit
) {
    // ---- Реактивное состояние ------------------------------------------------
    val authState by repository.authState.collectAsStateWithLifecycle()
    val markChatRead: suspend (ChatPreview) -> Boolean = { chat -> repository.markChatRead(chat.id).isSuccess }
    val archiveChat: suspend (ChatPreview) -> Boolean = { chat -> repository.setChatArchived(chat.id, true).isSuccess }
    val unarchiveChat: suspend (ChatPreview) -> Boolean = { chat -> repository.setChatArchived(chat.id, false).isSuccess }
    val connectionState by repository.connectionState.collectAsStateWithLifecycle()
    val myProfile by repository.myProfile.collectAsStateWithLifecycle()
    val bioLimit by repository.bioLengthLimit.collectAsStateWithLifecycle()
    val lastSeenStyle by lastSeenSettings.style.collectAsStateWithLifecycle()
    LifecycleResumeEffect(repository) {
        repository.setAppForeground(true)
        // После репозитория: отметка «прослушано» уходит через него, и ему нужен уже действующий передний план.
        PlaybackEngine.setForeground(true)
        onPauseOrDispose {
            repository.setAppForeground(false)
            PlaybackEngine.setForeground(false)
        }
    }

    // ---- Локальный UI-state --------------------------------------------------
    var routeName by rememberSaveable { mutableStateOf(AppRoute.Start.name) }
    var globalSearchQuery by rememberSaveable { mutableStateOf("") }
    // The choice made on the theme screen but not applied yet, so the editor starts from it.
    // One draft shared across the palette screen and its editor; neither navigation, cancel nor Activity recreation loses pending choices.
    var appearanceDraft by rememberSaveable(stateSaver = AppearanceDraftSaver) { mutableStateOf<AppearanceConfig?>(null) }
    var editorSeed by rememberSaveable(stateSaver = AppearanceDraftSaver) { mutableStateOf<AppearanceConfig?>(null) }
    var phone by rememberSaveable { mutableStateOf("") }
    var selectedChatId by rememberSaveable { mutableStateOf(0L) }
    // Вложение из профиля открывает чат у своего сообщения; 0 значит, что переход не нужен.
    var chatFocusMessageId by rememberSaveable { mutableStateOf(0L) }
    // Профиль участника открывается по его личному чату; запоминаем чат и экран, куда вернуться.
    var memberChatId by rememberSaveable { mutableStateOf(0L) }
    var memberReturnRouteName by rememberSaveable { mutableStateOf(AppRoute.ChatInfo.name) }
    // Куда вернуться из открытого чата: из архива — в архив, из контактов — в контакты, иначе — в список чатов.
    var chatBackRouteName by rememberSaveable { mutableStateOf(AppRoute.Chats.name) }
    var noticeError by remember { mutableStateOf<UiText?>(null) }
    // «Показать в чате» из мини-плеера открывает сообщение, из которого запущен файл. Назад из чата: из архива — в архив, из контактов — в контакты, иначе — в список чатов.
    val currentAuthState by rememberUpdatedState(authState)
    // Выход из аккаунта останавливает общий плеер: его файл и «Показать в чате» относятся к прошлой сессии.
    LaunchedEffect(authState) { if (authState != AuthState.Ready) PlaybackEngine.stop() }
    LaunchedEffect(Unit) {
        PlaybackEngine.showInChat.collect { origin ->
            if (currentAuthState != AuthState.Ready) return@collect
            if (routeName != AppRoute.Chat.name) {
                chatBackRouteName = when (routeName) {
                    AppRoute.Archive.name, AppRoute.Contacts.name -> routeName
                    else -> AppRoute.Chats.name
                }
            }
            selectedChatId = origin.chatId
            chatFocusMessageId = origin.messageId
            routeName = AppRoute.Chat.name
        }
    }
    // Скрытый архив остаётся скрытым после перезапуска: флаг лежит в настройках приложения.
    val context = LocalContext.current
    val chatPreferences = remember(context) { context.applicationContext.getSharedPreferences("chats", Context.MODE_PRIVATE) }
    var archiveHidden by remember { mutableStateOf(chatPreferences.getBoolean("archive_hidden", false)) }
    val onArchiveHiddenChange: (Boolean) -> Unit = { hidden ->
        archiveHidden = hidden
        chatPreferences.edit().putBoolean("archive_hidden", hidden).apply()
    }
    val archiveSettingsStore = remember(context) { ArchiveSettingsStore.get(context) }
    val archiveSettings by archiveSettingsStore.state.collectAsStateWithLifecycle()
    val archiveSettingsScope = rememberCoroutineScope()
    val onArchiveSettingsChange: (ArchiveSettings) -> Unit = { next ->
        val previous = archiveSettingsStore.state.value
        archiveSettingsStore.update(next)
        // Возврат из архива — настройка аккаунта: при ошибке записи возвращаем переключатель назад.
        if (next.returnOnNewMessage != previous.returnOnNewMessage) {
            archiveSettingsScope.launch {
                repository.setArchiveReturnsOnNewMessage(next.returnOnNewMessage).onFailure {
                    // Запись не прошла: показываем значение аккаунта, а не то, что было до нажатия.
                    val actual = repository.archiveReturnsOnNewMessage().getOrDefault(previous.returnOnNewMessage)
                    archiveSettingsStore.update(archiveSettingsStore.state.value.copy(returnOnNewMessage = actual))
                }
            }
        }
    }
    // Нижнее меню: какие разделы видны. Скрытые разделы открываются из профиля; настройка сохраняется между запусками.
    val tabPreferences = remember(context) { MainTabPreferences(context) }
    var tabVisibility by remember { mutableStateOf(tabPreferences.load()) }
    val onTabVisibilityChange: (TabVisibility) -> Unit = { visibility ->
        tabVisibility = visibility
        tabPreferences.save(visibility)
    }
    val tabStateHolder = rememberSaveableStateHolder()
    // Куда вернуться из раздела нижнего меню и из уведомлений: их открывают из разных мест.
    var tabReturnRouteName by rememberSaveable { mutableStateOf(AppRoute.Chats.name) }
    var notificationsReturnRouteName by rememberSaveable { mutableStateOf(AppRoute.Chats.name) }
    // Оформление открывают из профиля и из настроек: запоминаем, откуда пришли.
    var appearanceReturnRouteName by rememberSaveable { mutableStateOf(AppRoute.MyProfile.name) }
    val openTab: (MainTab, AppRoute) -> Unit = { tab, returnTo ->
        tabReturnRouteName = returnTo.name
        routeName = tab.route.name
    }
    val selectTab: (MainTab) -> Unit = { tab -> openTab(tab, AppRoute.Chats) }
    val openNotifications: (AppRoute) -> Unit = { returnTo ->
        notificationsReturnRouteName = returnTo.name
        routeName = AppRoute.Notifications.name
    }
    // Экраны прокси открываются из нескольких мест: запоминаем, куда вернуться, и какую вкладку показать.
    var proxyReturnRouteName by rememberSaveable { mutableStateOf(AppRoute.Start.name) }
    var proxyConnectTabName by rememberSaveable { mutableStateOf(ProxyConnectTab.MTPROTO.name) }
    val openProxy: (AppRoute) -> Unit = { returnTo ->
        proxyReturnRouteName = returnTo.name
        routeName = AppRoute.Proxy.name
    }
    val openProxyConnect: (ProxyConnectTab) -> Unit = { tab ->
        proxyConnectTabName = tab.name
        routeName = AppRoute.ProxyConnect.name
    }
    // Профиль участника: открываем его личный чат и показываем сведения; назад возвращаемся туда, откуда открыли.
    val memberScope = rememberCoroutineScope()
    // Ответ на прежнее нажатие может прийти позже нового: тогда он не должен заменить выбранного участника.
    var latestMemberUserId by rememberSaveable { mutableStateOf(0L) }
    val openUserProfile: (Long, AppRoute) -> Unit = { userId, returnTo ->
        latestMemberUserId = userId
        memberScope.launch {
            repository.openPrivateChat(userId)
                .onSuccess { chatId ->
                    if (latestMemberUserId == userId) {
                        memberChatId = chatId
                        memberReturnRouteName = returnTo.name
                        routeName = AppRoute.MemberProfile.name
                    }
                }
                .onFailure { error ->
                    if (latestMemberUserId == userId) noticeError = (error as? UserMessageException)?.text ?: UiText.Res(R.string.profile_member_open_failed)
                }
        }
    }
    if (authState == AuthState.Ready) NotificationPermissionCoordinator(notificationRuntime)
    LaunchedEffect(authState, notificationRequest?.token) {
        val request = notificationRequest ?: return@LaunchedEffect
        if (authState != AuthState.Ready) return@LaunchedEffect
        if (request.proxy) openProxy(AppRoute.Chats)
        else if (request.settings) openNotifications(AppRoute.Chats)
        else request.chatId?.let { chatId ->
            val result = repository.loadChat(chatId)
            if (result.isSuccess) { selectedChatId = chatId; chatBackRouteName = AppRoute.Chats.name; routeName = AppRoute.Chat.name }
            else noticeError = (result.exceptionOrNull() as? UserMessageException)?.text ?: UiText.Res(R.string.err_app_open_chat_failed)
        }
        onNotificationHandled(request.token)
    }
    // Текст из другого приложения выбирает чат и подтверждает отправку на отдельном экране поверх приложения.
    if (incomingShare != null && authState == AuthState.Ready) {
        // Архивные чаты тоже можно выбрать: отправка в них не требует вынимать их из архива.
        val shareChats by repository.chats.collectAsStateWithLifecycle()
        val shareArchivedChats by repository.archivedChats.collectAsStateWithLifecycle()
        val shareCaptionLimit = repository.messageLimits.collectAsStateWithLifecycle().value.caption
        // Отправка идёт в этой сессии. Пока она не закончилась, диалог не закрывается: иначе результат и частичная отправка пропадают вместе с ним.
        val shareSession = remember(incomingShare.requestId) { ShareSessions.of(context, incomingShare.requestId, incomingShare.uris, incomingShare.text) }
        val shareSending = shareSession.sending
        Dialog(
            onDismissRequest = { if (!shareSending) onIncomingShareHandled(incomingShare.requestId) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = !shareSending,
                dismissOnClickOutside = !shareSending
            )
        ) {
            // Новый запрос из «Поделиться» получает свежее состояние выбора, даже если диалог уже открыт.
            key(incomingShare.requestId) {
                ShareTargetScreen(
                    requestId = incomingShare.requestId,
                    text = incomingShare.text,
                    uris = incomingShare.uris,
                    session = shareSession,
                    captionLimit = shareCaptionLimit,
                    chats = shareChats + shareArchivedChats,
                    loadTopics = { chatId -> repository.forumTopics(chatId) },
                    sendText = { chatId, topicId, text ->
                        repository.sendMessage(chatId, text, forumTopicId = topicId, clearDraft = false)
                    },
                    sendMedia = { chatId, topicId, media, caption ->
                        if (media.size == 1) repository.sendMedia(chatId, media.single(), caption, forumTopicId = topicId)
                        else repository.sendMediaAlbum(chatId, media, caption, forumTopicId = topicId)
                    },
                    onSent = { chatId ->
                        onIncomingShareHandled(incomingShare.requestId)
                        selectedChatId = chatId
                        chatBackRouteName = AppRoute.Chats.name
                        routeName = AppRoute.Chat.name
                    },
                    onCancel = { onIncomingShareHandled(incomingShare.requestId) }
                )
            }
        }
    }

    // ---- Реактивная навигация по AuthState -----------------------------------
    // Следим за authState и автоматически переходим на нужный экран.
    // Это гарантирует, что переход происходит ПОСЛЕ того, как TDLib подтвердил
    // успешную смену состояния, а не при нажатии кнопки.
    LaunchedEffect(authState) {
        val currentRoute = AppRoute.valueOf(routeName)
        when (authState) {
            is AuthState.WaitingForPhone -> {
                // Попадаем сюда только если были на начальном экране или пришли из ошибки
                if (currentRoute == AppRoute.Start || currentRoute == AppRoute.LoginPhone) {
                    routeName = AppRoute.LoginPhone.name
                }
            }
            is AuthState.WaitingForCode -> {
                phone = (authState as AuthState.WaitingForCode).phone.ifEmpty { phone }
                routeName = AppRoute.LoginCode.name
            }
            is AuthState.WaitingForPassword -> {
                routeName = AppRoute.LoginPassword.name
            }
            is AuthState.Ready -> {
                if (currentRoute in listOf(AppRoute.Start, AppRoute.LoginPhone, AppRoute.LoginCode, AppRoute.LoginPassword)) routeName = AppRoute.Chats.name
            }
            else -> Unit // Idle, Loading, Error — навигацию не трогаем
        }
    }

    // ---- Маршрутизация -------------------------------------------------------
    val route = AppRoute.valueOf(routeName)
    // Назад из разделов нижнего меню ведёт туда, откуда раздел открыли: в чаты или в профиль.
    BackHandler(enabled = route == AppRoute.Calls || route == AppRoute.Contacts || route == AppRoute.Settings) {
        routeName = tabReturnRouteName
    }
    BackHandler(enabled = route == AppRoute.Proxy || route == AppRoute.ProxyConnect) {
        routeName = if (route == AppRoute.ProxyConnect) AppRoute.Proxy.name else proxyReturnRouteName
    }
    BackHandler(enabled = route == AppRoute.Updates) { routeName = AppRoute.Settings.name }
    BackHandler(enabled = route == AppRoute.VideoMessageSettings) { routeName = AppRoute.Settings.name }
    BackHandler(enabled = route == AppRoute.StorageSettings) { routeName = AppRoute.Settings.name }
    BackHandler(enabled = route == AppRoute.Language || route == AppRoute.Sessions) { routeName = AppRoute.Settings.name }
    BackHandler(enabled = route == AppRoute.CreateChannel) { routeName = AppRoute.Chats.name }
    BackHandler(enabled = route == AppRoute.Diagnostics || route == AppRoute.ReportProblem) { routeName = AppRoute.Settings.name }

    // Потоки собирает сам раздел, которому они нужны: обновление списка чатов перерисовывает только открытый раздел, а не всё приложение.
    val chatsTab: @Composable () -> Unit = {
        val chats by repository.chats.collectAsStateWithLifecycle()
        val archivedChats by repository.archivedChats.collectAsStateWithLifecycle()
        val folders by repository.chatFolders.collectAsStateWithLifecycle()
        val folderLists by repository.folderChats.collectAsStateWithLifecycle()
        val folderLoading by repository.folderLoadStates.collectAsStateWithLifecycle()
        MainTabsScaffold(MainTab.Chats.route.name, MainTab.Chats, tabVisibility, selectTab, tabStateHolder) {
            ChatsScreen(
                chats = chats,
                archivedChats = archivedChats,
                folderSnapshot = folders,
                folderChats = folderLists,
                folderLoadStates = folderLoading,
                onFolderLoadMore = { id ->
                    archiveSettingsScope.launch { repository.loadMoreFolderChats(id) }
                },
                onPinFolderChat = repository::setFolderChatPinned,
                myProfile = myProfile.info,
                observeFile = repository::observeFile,
                downloadFile = repository::downloadFile,
                onMyProfile = { routeName = AppRoute.MyProfile.name },
                onArchiveClick = { routeName = AppRoute.Archive.name },
                onMarkRead = markChatRead,
                onArchive = archiveChat,
                onPinChat = repository::setChatPinned,
                onMuteChat = repository::setChatMuted,
                onDeleteChat = repository::deleteChatForSelf,
                onCreateChannel = { routeName = AppRoute.CreateChannel.name },
                archiveHidden = archiveHidden,
                onArchiveHiddenChange = onArchiveHiddenChange,
                onGlobalSearch = { typed ->
                    globalSearchQuery = typed
                    routeName = AppRoute.GlobalSearch.name
                },
                onChatClick = { chat ->
                    selectedChatId = chat.id
                    chatBackRouteName = AppRoute.Chats.name
                    routeName = AppRoute.Chat.name
                }
            )
        }
    }
    val archiveRoute: @Composable () -> Unit = {
        val archivedChats by repository.archivedChats.collectAsStateWithLifecycle()
        // Переключатель показывает настройку аккаунта: её могли поменять на другом устройстве.
        LaunchedEffect(repository) {
            repository.archiveReturnsOnNewMessage().onSuccess { returns ->
                archiveSettingsStore.update(archiveSettingsStore.state.value.copy(returnOnNewMessage = returns))
            }
        }
        ArchiveScreen(
            chats = archivedChats,
            observeFile = repository::observeFile,
            downloadFile = repository::downloadFile,
            onMarkRead = markChatRead,
            onUnarchive = unarchiveChat,
            settings = archiveSettings,
            onSettingsChange = onArchiveSettingsChange,
            onBack = { routeName = AppRoute.Chats.name },
            onChatClick = { chat ->
                selectedChatId = chat.id
                chatBackRouteName = AppRoute.Archive.name
                routeName = AppRoute.Chat.name
            }
        )
    }
    val chatRoute: @Composable () -> Unit = {
        val chats by repository.chats.collectAsStateWithLifecycle()
        val selectedChat by remember(repository, selectedChatId) { repository.observeChat(selectedChatId) }.collectAsStateWithLifecycle()
        val chat = selectedChat ?: chats.firstOrNull { it.id == selectedChatId }
        LaunchedEffect(authState, selectedChatId) {
            if (authState == AuthState.Ready && chat == null && selectedChatId != 0L) repository.loadChat(selectedChatId)
        }
        if (chat == null) {
            chatsTab()
        } else {
            val historyFlow = remember(repository, selectedChatId) { repository.observeChatHistory(selectedChatId) }
            val history by historyFlow.collectAsStateWithLifecycle()
            val chatInfoFlow = remember(repository, selectedChatId) { repository.observeChatInfo(selectedChatId) }
            val chatInfo by chatInfoFlow.collectAsStateWithLifecycle()
            DisposableEffect(repository, selectedChatId) {
                val openedChatId = selectedChatId
                repository.openChat(openedChatId)
                onDispose { repository.closeChat(openedChatId) }
            }
            key(selectedChatId) {
                ChatScreen(
                    chat = chat,
                    info = chatInfo.info,
                    lastSeenStyle = lastSeenStyle,
                    history = history,
                    conversation = repository,
                    chats = chats,
                    observeFile = repository::observeFile,
                    onDownloadFile = repository::downloadFile,
                    onBack = { routeName = chatBackRouteName },
                    onChatInfo = { routeName = AppRoute.ChatInfo.name },
                    connectionState = connectionState,
                    recordingSettings = recordingSettings,
                    focusMessageId = chatFocusMessageId, onFocusHandled = { chatFocusMessageId = 0L }
                )
            }
        }
    }
    val chatInfoRoute: @Composable () -> Unit = {
        val chats by repository.chats.collectAsStateWithLifecycle()
        val infoFlow = remember(repository, selectedChatId) { repository.observeChatInfo(selectedChatId) }
        val info by infoFlow.collectAsStateWithLifecycle()
        LaunchedEffect(repository, selectedChatId) { repository.refreshChatInfo(selectedChatId) }
        ProfileScreen(info, false, repository::observeFile, repository::downloadFile,
            onBack = { routeName = AppRoute.Chat.name }, onRefresh = { repository.refreshChatInfo(selectedChatId) },
            onMembers = { routeName = AppRoute.ChatMembers.name }, onOpenUser = { userId -> openUserProfile(userId, AppRoute.ChatInfo) },
            onEdit = { routeName = AppRoute.MyProfile.name }, setPhoto = repository::setMyProfilePhoto,
            loadProfilePhotos = repository::loadProfilePhotos, deleteMyProfilePhoto = repository::deleteMyProfilePhoto,
            setPreviousProfilePhoto = repository::setPreviousProfilePhoto,
            onBlock = { repository.blockPrivateChat(selectedChatId) },
            onDelete = { repository.deleteChatForSelf(selectedChatId) },
            onDeleted = { routeName = AppRoute.Chats.name },
            onOpenLinkedChannel = { channelId ->
                selectedChatId = channelId
                chatBackRouteName = AppRoute.Chats.name
                routeName = AppRoute.Chat.name
            },
            lastSeenStyle = lastSeenStyle,
            sharedMessages = { kind, fromMessageId, newer -> repository.sharedMessages(selectedChatId, kind, fromMessageId, newer) },
            confirmExport = { messageId -> repository.messageCapabilities(selectedChatId, messageId).getOrNull()?.canSave == true },
            messageRights = { messageId -> repository.messageCapabilities(selectedChatId, messageId).getOrNull() },
            forwardMessage = { messageId, toChatId -> repository.forwardMessage(selectedChatId, messageId, toChatId) }, chats = chats,
            onContentOpened = { messageId -> repository.openMessageContent(selectedChatId, messageId) },
            onOpenMessage = { messageId -> chatFocusMessageId = messageId; routeName = AppRoute.Chat.name })
    }

    val chatMembersRoute: @Composable () -> Unit = {
        key(selectedChatId) {
            ChatMembersScreen(loadPage = { offset, limit -> repository.loadChatMembers(selectedChatId, offset, limit) },
                observeFile = repository::observeFile, downloadFile = repository::downloadFile,
                onOpenUser = { userId -> openUserProfile(userId, AppRoute.ChatMembers) },
                onBack = { routeName = AppRoute.ChatInfo.name })
        }
    }
    val memberProfileRoute: @Composable () -> Unit = {
        val infoFlow = remember(repository, memberChatId) { repository.observeChatInfo(memberChatId) }
        val info by infoFlow.collectAsStateWithLifecycle()
        LaunchedEffect(repository, memberChatId) { repository.refreshChatInfo(memberChatId) }
        ProfileScreen(info, false, repository::observeFile, repository::downloadFile,
            onBack = { routeName = memberReturnRouteName }, onRefresh = { repository.refreshChatInfo(memberChatId) },
            onEdit = {}, setPhoto = repository::setMyProfilePhoto,
            loadProfilePhotos = repository::loadProfilePhotos, deleteMyProfilePhoto = repository::deleteMyProfilePhoto,
            setPreviousProfilePhoto = repository::setPreviousProfilePhoto,
            onBlock = { repository.blockPrivateChat(memberChatId) },
            onDelete = { repository.deleteChatForSelf(memberChatId) },
            onDeleted = { routeName = memberReturnRouteName },
            onOpenLinkedChannel = { channelId ->
                selectedChatId = channelId; chatBackRouteName = AppRoute.Chats.name; routeName = AppRoute.Chat.name
            },
            lastSeenStyle = lastSeenStyle)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (route) {
                AppRoute.Start -> StartScreen(
                    tdLibReadiness = tdLibReadiness,
                    authState = authState,
                    onProxySettings = { openProxy(AppRoute.Start) },
                    onStartClick = {
                        repository.startAuthorization()
                        // Явно переходим на экран телефона, не дожидаясь LaunchedEffect
                        // (startAuthorization не всегда меняет authState синхронно в real impl)
                        routeName = AppRoute.LoginPhone.name
                    }
                )

                AppRoute.LoginPhone -> LoginPhoneScreen(
                    authState = authState,
                    connectionState = connectionState,
                    onReconnect = repository::reconnect,
                    onProxySettings = { openProxy(AppRoute.LoginPhone) },
                    onBack = {
                        repository.resetError()
                        routeName = AppRoute.Start.name
                    },
                    onContinue = { submittedPhone ->
                        phone = submittedPhone
                        repository.submitPhoneNumber(submittedPhone)
                        // Навигация произойдёт через LaunchedEffect(authState) при WaitingForCode
                    }
                )

                AppRoute.LoginCode -> LoginCodeScreen(
                    phone = phone,
                    authState = authState,
                    onBack = {
                        repository.resetError()
                        routeName = AppRoute.LoginPhone.name
                    },
                    onComplete = { code ->
                        repository.submitCode(code)
                        // Навигация произойдёт через LaunchedEffect(authState) при Ready или WaitingForPassword
                    },
                    onResendCode = {
                        repository.resendCode()
                    }
                )

                AppRoute.LoginPassword -> {
                    val hint = (authState as? AuthState.WaitingForPassword)?.hint ?: ""
                    LoginPasswordScreen(
                        hint = hint,
                        authState = authState,
                        onBack = {
                            repository.resetError()
                            routeName = AppRoute.LoginPhone.name
                        },
                        onSubmit = { password ->
                            repository.submitPassword(password)
                            // Навигация произойдёт через LaunchedEffect(authState) при Ready
                        }
                    )
                }

                AppRoute.Chats -> chatsTab()

                AppRoute.Archive -> archiveRoute()

                AppRoute.Chat -> chatRoute()
                AppRoute.ChatInfo -> chatInfoRoute()
                AppRoute.ChatMembers -> chatMembersRoute()
                AppRoute.MemberProfile -> memberProfileRoute()
                AppRoute.MyProfile -> {
                    LaunchedEffect(repository) { repository.refreshMyProfile() }
                    ProfileScreen(myProfile, true, repository::observeFile, repository::downloadFile,
                        onBack = { routeName = AppRoute.Chats.name }, onRefresh = repository::refreshMyProfile,
                        onEdit = { routeName = AppRoute.EditProfile.name }, setPhoto = repository::setMyProfilePhoto,
                        loadProfilePhotos = repository::loadProfilePhotos, deleteMyProfilePhoto = repository::deleteMyProfilePhoto,
            setPreviousProfilePhoto = repository::setPreviousProfilePhoto,
                        onNotifications = { openNotifications(AppRoute.MyProfile) },
                        hiddenTabs = MainTab.entries.filter { !tabVisibility.isVisible(it) },
                        onOpenTab = { tab -> openTab(tab, AppRoute.MyProfile) },
                        onOpenAppearance = { appearanceDraft = appearance; editorSeed = null; appearanceReturnRouteName = AppRoute.MyProfile.name; routeName = AppRoute.Appearance.name },
                        onOpenLinkedChannel = { channelId ->
                            selectedChatId = channelId; chatBackRouteName = AppRoute.Chats.name; routeName = AppRoute.Chat.name
                        }, lastSeenStyle = lastSeenStyle)
                }
                AppRoute.Appearance -> ThemeSettingsScreen(
                    saved = appearance,
                    draft = appearanceDraft ?: appearance,
                    onDraftChange = { appearanceDraft = it },
                    onSave = { config -> appearanceDraft = config; onAppearanceChange(config) },
                    onOpenEditor = { pending -> editorSeed = pending; routeName = AppRoute.ThemeEditor.name },
                    onBack = { appearanceDraft = null; editorSeed = null; routeName = appearanceReturnRouteName }
                )
                AppRoute.ThemeEditor -> ThemeEditorScreen(
                    saved = editorSeed ?: appearanceDraft ?: appearance,
                    current = appearance,
                    onSave = { config -> appearanceDraft = config; onAppearanceChange(config) },
                    onBack = { editorSeed = null; routeName = AppRoute.Appearance.name }
                )
                AppRoute.EditProfile -> {
                    myProfile.info?.let { info ->
                        EditProfileScreen(info, bioLimit, onBack = { routeName = AppRoute.MyProfile.name }, save = repository::updateMyProfile)
                    } ?: LaunchedEffect(Unit) { routeName = AppRoute.MyProfile.name }
                }
                AppRoute.Notifications -> NotificationSettingsScreen(
                    runtime = notificationRuntime,
                    connectionState = connectionState,
                    onBack = { routeName = notificationsReturnRouteName },
                    onDiagnostics = { routeName = AppRoute.ConnectionDiagnostics.name }
                )
                AppRoute.ConnectionDiagnostics -> ConnectionDiagnosticsScreen(
                    repository = repository,
                    vpnBackend = vpnBackend,
                    connectionState = connectionState,
                    onBack = { routeName = AppRoute.Notifications.name }
                )
                AppRoute.Calls -> MainTabsScaffold(MainTab.Calls.route.name, MainTab.Calls, tabVisibility, selectTab, tabStateHolder) {
                    CallsScreen(loadCalls = repository::loadCallHistory, observeFile = repository::observeFile, downloadFile = repository::downloadFile)
                }
                AppRoute.Contacts -> MainTabsScaffold(MainTab.Contacts.route.name, MainTab.Contacts, tabVisibility, selectTab, tabStateHolder) {
                    ContactsScreen(
                        loadContacts = repository::loadContacts,
                        addContact = repository::addContact,
                        openPrivateChat = repository::openPrivateChat,
                        observeFile = repository::observeFile,
                        downloadFile = repository::downloadFile,
                        onChatOpened = { chatId ->
                            selectedChatId = chatId
                            chatBackRouteName = AppRoute.Contacts.name
                            routeName = AppRoute.Chat.name
                        }
                    )
                }
                AppRoute.Settings -> MainTabsScaffold(MainTab.Settings.route.name, MainTab.Settings, tabVisibility, selectTab, tabStateHolder) {
                    SettingsScreen(
                        onNavigationSettings = { routeName = AppRoute.NavigationSettings.name },
                        onAppearance = { appearanceDraft = appearance; editorSeed = null; appearanceReturnRouteName = AppRoute.Settings.name; routeName = AppRoute.Appearance.name },
                        onLastSeen = { routeName = AppRoute.LastSeenSettings.name },
                        onNotifications = { openNotifications(AppRoute.Settings) },
                        onStorage = { routeName = AppRoute.StorageSettings.name },
                        onProxy = { openProxy(AppRoute.Settings) },
                        onVideoRecording = { routeName = AppRoute.VideoMessageSettings.name },
                        onDiagnostics = { routeName = AppRoute.Diagnostics.name },
                        onUpdates = { routeName = AppRoute.Updates.name },
                        onLanguage = { routeName = AppRoute.Language.name },
                        onSessions = { routeName = AppRoute.Sessions.name }
                    )
                }
                AppRoute.Language -> LanguageScreen(onBack = { routeName = AppRoute.Settings.name })
                AppRoute.Sessions -> ActiveSessionsScreen(
                    onBack = { routeName = AppRoute.Settings.name },
                    load = repository::activeSessions,
                    terminate = repository::terminateOtherSession,
                    terminateAll = repository::terminateAllOtherSessions
                )
                AppRoute.CreateChannel -> CreateChannelScreen(
                    onBack = { routeName = AppRoute.Chats.name },
                    create = repository::createChannel,
                    onCreated = { chatId ->
                        selectedChatId = chatId
                        chatBackRouteName = AppRoute.Chats.name
                        routeName = AppRoute.Chat.name
                    }
                )
                AppRoute.Updates -> StableUpdatesScreen(onBack = { routeName = AppRoute.Settings.name })
                AppRoute.Diagnostics -> DiagnosticsScreen(
                    connectionState = connectionState,
                    onBack = { routeName = AppRoute.Settings.name },
                    onReportProblem = { routeName = AppRoute.ReportProblem.name },
                    onResources = { routeName = AppRoute.Resources.name }
                )
                AppRoute.Resources -> ResourcesScreen(onBack = { routeName = AppRoute.Diagnostics.name })
                AppRoute.GlobalSearch -> GlobalSearchScreen(
                    initialQuery = globalSearchQuery,
                    conversation = repository,
                    observeFile = repository::observeFile,
                    downloadFile = repository::downloadFile,
                    onBack = { routeName = AppRoute.Chats.name }
                )
                AppRoute.ReportProblem -> ReportProblemScreen(
                    repository = repository,
                    connectionState = connectionState,
                    onBack = { routeName = AppRoute.Diagnostics.name }
                )
                AppRoute.VideoMessageSettings -> VideoMessageSettingsScreen(recordingSettings) { routeName = AppRoute.Settings.name }
                AppRoute.StorageSettings -> StorageSettingsScreen(repository = repository, onBack = { routeName = AppRoute.Settings.name })
                AppRoute.NavigationSettings -> NavigationSettingsScreen(
                    visibility = tabVisibility,
                    onVisibilityChange = onTabVisibilityChange,
                    onBack = { routeName = AppRoute.Settings.name }
                )
                AppRoute.LastSeenSettings -> LastSeenSettingsScreen(lastSeenStyle, onStyleChange = lastSeenSettings::setStyle, onBack = { routeName = AppRoute.Settings.name })
                AppRoute.Proxy -> ProxyListScreen(
                    repository = repository,
                    vpnBackend = vpnBackend,
                    onBack = { routeName = proxyReturnRouteName },
                    onAddProxy = { openProxyConnect(ProxyConnectTab.MTPROTO) },
                    onSubscriptionServers = { openProxyConnect(ProxyConnectTab.SUBSCRIPTION) }
                )

                AppRoute.ProxyConnect -> ProxyConnectScreen(
                    repository = repository,
                    vpnBackend = vpnBackend,
                    connectionState = connectionState,
                    initialTab = ProxyConnectTab.valueOf(proxyConnectTabName),
                    onBack = { routeName = AppRoute.Proxy.name }
                )
            }
        }
        if (route !in playerBarHostRoutes) NowPlayingBar(bottomInset = true)
    }
    noticeError?.let { error -> androidx.compose.material3.AlertDialog(onDismissRequest = { noticeError = null }, title = { androidx.compose.material3.Text(stringResource(R.string.app_notice_title)) },
        text = { androidx.compose.material3.Text(error.resolve()) }, confirmButton = { androidx.compose.material3.TextButton(onClick = { noticeError = null }) { androidx.compose.material3.Text(stringResource(R.string.common_close)) } }) }
    ProxyRestoreDialog(vpnBackend = vpnBackend)
}
