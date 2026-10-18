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
import com.beykus.beygram.ui.components.MainTabsScaffold
import com.beykus.beygram.ui.screens.auth.LoginCodeScreen
import com.beykus.beygram.ui.screens.auth.LoginPasswordScreen
import com.beykus.beygram.ui.screens.auth.LoginPhoneScreen
import com.beykus.beygram.ui.screens.archive.ArchiveScreen
import com.beykus.beygram.ui.screens.calls.CallsScreen
import com.beykus.beygram.ui.screens.chat.ChatScreen
import com.beykus.beygram.ui.screens.chats.ChatsScreen
import com.beykus.beygram.ui.screens.contacts.ContactsScreen
import com.beykus.beygram.ui.screens.diagnostics.ConnectionDiagnosticsScreen
import com.beykus.beygram.ui.screens.diagnostics.DiagnosticsScreen
import com.beykus.beygram.ui.screens.diagnostics.ReportProblemScreen
import com.beykus.beygram.ui.screens.settings.LastSeenSettingsScreen
import com.beykus.beygram.ui.screens.settings.NavigationSettingsScreen
import com.beykus.beygram.ui.screens.settings.StorageSettingsScreen
import com.beykus.beygram.ui.screens.settings.VideoMessageSettingsScreen
import com.beykus.beygram.ui.screens.settings.SettingsScreen
import com.beykus.beygram.ui.screens.settings.StableUpdatesScreen
import com.beykus.beygram.ui.screens.start.StartScreen
import com.beykus.beygram.ui.screens.profile.ChatMembersScreen
import com.beykus.beygram.ui.screens.profile.ProfileScreen
import com.beykus.beygram.ui.screens.profile.EditProfileScreen
import com.beykus.beygram.domain.model.NotificationOpenRequest
import com.beykus.beygram.notifications.NotificationRuntime
import com.beykus.beygram.ui.screens.notifications.NotificationSettingsScreen
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.data.media.VideoMessageSettingsStore
import com.beykus.beygram.data.presence.LastSeenSettingsStore
import com.beykus.beygram.ui.screens.proxy.ProxyConnectScreen
import com.beykus.beygram.ui.screens.proxy.ProxyConnectTab
import com.beykus.beygram.ui.screens.proxy.ProxyListScreen
import com.beykus.beygram.ui.screens.proxy.ProxyRestoreDialog
import com.beykus.beygram.ui.screens.notifications.NotificationPermissionCoordinator
import com.beykus.beygram.ui.screens.playback.NowPlayingBar
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.launch

/** Экраны, где мини-плеер встроен в свою разметку: над нижним меню или над полем ввода. На остальных экранах он стоит снизу. */
private val playerBarHostRoutes = setOf(AppRoute.Chats, AppRoute.Calls, AppRoute.Contacts, AppRoute.Settings, AppRoute.Chat)

@Composable
fun BeyGramApp(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    notificationRequest: NotificationOpenRequest? = null,
    onNotificationHandled: (Long) -> Unit = {}
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
        isDarkTheme = isDarkTheme,
        onThemeChange = onThemeChange,
        notificationRuntime = notificationRuntime,
        vpnBackend = vpnBackend,
        recordingSettings = videoMessageSettings,
        lastSeenSettings = lastSeenSettings,
        notificationRequest = notificationRequest,
        onNotificationHandled = onNotificationHandled
    )
}

@Composable
private fun BeyGramAppContent(
    repository: TelegramRepository,
    tdLibReadiness: TdLibReadiness,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    notificationRuntime: NotificationRuntime,
    vpnBackend: VpnBackend,
    recordingSettings: VideoMessageSettingsStore,
    lastSeenSettings: LastSeenSettingsStore,
    notificationRequest: NotificationOpenRequest?,
    onNotificationHandled: (Long) -> Unit
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
        onPauseOrDispose { repository.setAppForeground(false) }
    }

    // ---- Локальный UI-state --------------------------------------------------
    var routeName by rememberSaveable { mutableStateOf(AppRoute.Start.name) }
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
    BackHandler(enabled = route == AppRoute.Diagnostics || route == AppRoute.ReportProblem) { routeName = AppRoute.Settings.name }

    // Потоки собирает сам раздел, которому они нужны: обновление списка чатов перерисовывает только открытый раздел, а не всё приложение.
    val chatsTab: @Composable () -> Unit = {
        val chats by repository.chats.collectAsStateWithLifecycle()
        val archivedChats by repository.archivedChats.collectAsStateWithLifecycle()
        MainTabsScaffold(MainTab.Chats.route.name, MainTab.Chats, tabVisibility, selectTab, tabStateHolder) {
            ChatsScreen(
                chats = chats,
                archivedChats = archivedChats,
                myProfile = myProfile.info,
                onNotifications = { openNotifications(AppRoute.Chats) },
                observeFile = repository::observeFile,
                downloadFile = repository::downloadFile,
                onMyProfile = { routeName = AppRoute.MyProfile.name },
                onArchiveClick = { routeName = AppRoute.Archive.name },
                onMarkRead = markChatRead,
                onArchive = archiveChat,
                archiveHidden = archiveHidden,
                onArchiveHiddenChange = onArchiveHiddenChange,
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
        val infoFlow = remember(repository, selectedChatId) { repository.observeChatInfo(selectedChatId) }
        val info by infoFlow.collectAsStateWithLifecycle()
        LaunchedEffect(repository, selectedChatId) { repository.refreshChatInfo(selectedChatId) }
        ProfileScreen(info, false, repository::observeFile, repository::downloadFile,
            onBack = { routeName = AppRoute.Chat.name }, onRefresh = { repository.refreshChatInfo(selectedChatId) },
            onMembers = { routeName = AppRoute.ChatMembers.name }, onOpenUser = { userId -> openUserProfile(userId, AppRoute.ChatInfo) },
            onEdit = { routeName = AppRoute.MyProfile.name }, setPhoto = repository::setMyProfilePhoto,
            isDarkTheme = isDarkTheme, onThemeChange = onThemeChange, lastSeenStyle = lastSeenStyle,
            sharedMessages = { kind, fromMessageId -> repository.sharedMessages(selectedChatId, kind, fromMessageId) },
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
            isDarkTheme = isDarkTheme, onThemeChange = onThemeChange, lastSeenStyle = lastSeenStyle)
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
                        onNotifications = { openNotifications(AppRoute.MyProfile) },
                        hiddenTabs = MainTab.entries.filter { !tabVisibility.isVisible(it) },
                        onOpenTab = { tab -> openTab(tab, AppRoute.MyProfile) },
                        isDarkTheme = isDarkTheme, onThemeChange = onThemeChange, lastSeenStyle = lastSeenStyle)
                }
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
                        onLastSeen = { routeName = AppRoute.LastSeenSettings.name },
                        onNotifications = { openNotifications(AppRoute.Settings) },
                        onStorage = { routeName = AppRoute.StorageSettings.name },
                        onProxy = { openProxy(AppRoute.Settings) },
                        onVideoRecording = { routeName = AppRoute.VideoMessageSettings.name },
                        onDiagnostics = { routeName = AppRoute.Diagnostics.name },
                        onUpdates = { routeName = AppRoute.Updates.name }
                    )
                }
                AppRoute.Updates -> StableUpdatesScreen(onBack = { routeName = AppRoute.Settings.name })
                AppRoute.Diagnostics -> DiagnosticsScreen(
                    connectionState = connectionState,
                    onBack = { routeName = AppRoute.Settings.name },
                    onReportProblem = { routeName = AppRoute.ReportProblem.name }
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
        if (route !in playerBarHostRoutes) NowPlayingBar()
    }
    noticeError?.let { error -> androidx.compose.material3.AlertDialog(onDismissRequest = { noticeError = null }, title = { androidx.compose.material3.Text(stringResource(R.string.app_notice_title)) },
        text = { androidx.compose.material3.Text(error.resolve()) }, confirmButton = { androidx.compose.material3.TextButton(onClick = { noticeError = null }) { androidx.compose.material3.Text(stringResource(R.string.common_close)) } }) }
    ProxyRestoreDialog(vpnBackend = vpnBackend)
}
