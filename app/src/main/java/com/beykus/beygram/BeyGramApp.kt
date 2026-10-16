package com.beykus.beygram

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.beykus.beygram.data.mock.MockTelegramRepository
import com.beykus.beygram.data.tdlib.TdLibBootstrap
import com.beykus.beygram.data.tdlib.TdLibReadiness
import com.beykus.beygram.data.tdlib.TdLibTelegramRepository
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
import com.beykus.beygram.ui.screens.settings.NavigationSettingsScreen
import com.beykus.beygram.ui.screens.settings.SettingsScreen
import com.beykus.beygram.ui.screens.start.StartScreen
import com.beykus.beygram.ui.screens.profile.ProfileScreen
import com.beykus.beygram.ui.screens.profile.EditProfileScreen
import com.beykus.beygram.domain.model.NotificationOpenRequest
import com.beykus.beygram.notifications.NotificationRuntime
import com.beykus.beygram.ui.screens.notifications.NotificationSettingsScreen
import com.beykus.beygram.data.vpn.VpnBackend
import com.beykus.beygram.ui.screens.proxy.ProxyConnectScreen
import com.beykus.beygram.ui.screens.proxy.ProxyConnectTab
import com.beykus.beygram.ui.screens.proxy.ProxyListScreen
import com.beykus.beygram.ui.screens.notifications.NotificationPermissionCoordinator

@Composable
fun BeyGramApp(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    notificationRequest: NotificationOpenRequest? = null,
    onNotificationHandled: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val tdLibReadiness = remember(context) {
        TdLibBootstrap(context).readiness()
    }
    val notificationRuntime = remember(context) { NotificationRuntime.get(context) }
    val repository = remember(context, tdLibReadiness) {
        if (tdLibReadiness.canCreateClient) {
            TdLibTelegramRepository.getInstance(context.applicationContext)
        } else {
            MockTelegramRepository()
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
    DisposableEffect(repository, notificationRuntime) { notificationRuntime.attach(repository); onDispose { } }

    BeyGramAppContent(
        repository = repository,
        tdLibReadiness = tdLibReadiness,
        isDarkTheme = isDarkTheme,
        onThemeChange = onThemeChange,
        notificationRuntime = notificationRuntime,
        vpnBackend = vpnBackend,
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
    notificationRequest: NotificationOpenRequest?,
    onNotificationHandled: (Long) -> Unit
) {
    // ---- Реактивное состояние ------------------------------------------------
    val authState by repository.authState.collectAsStateWithLifecycle()
    val chats by repository.chats.collectAsStateWithLifecycle()
    val archivedChats by repository.archivedChats.collectAsStateWithLifecycle()
    val markChatRead: suspend (ChatPreview) -> Boolean = { chat -> repository.markChatRead(chat.id).isSuccess }
    val archiveChat: suspend (ChatPreview) -> Boolean = { chat -> repository.setChatArchived(chat.id, true).isSuccess }
    val unarchiveChat: suspend (ChatPreview) -> Boolean = { chat -> repository.setChatArchived(chat.id, false).isSuccess }
    val connectionState by repository.connectionState.collectAsStateWithLifecycle()
    val myProfile by repository.myProfile.collectAsStateWithLifecycle()
    val bioLimit by repository.bioLengthLimit.collectAsStateWithLifecycle()
    LifecycleResumeEffect(repository) {
        repository.setAppForeground(true)
        onPauseOrDispose { repository.setAppForeground(false) }
    }

    // ---- Локальный UI-state --------------------------------------------------
    var routeName by rememberSaveable { mutableStateOf(AppRoute.Start.name) }
    var phone by rememberSaveable { mutableStateOf("") }
    var selectedChatId by rememberSaveable { mutableStateOf(0L) }
    // Куда вернуться из открытого чата: из архива — в архив, из контактов — в контакты, иначе — в список чатов.
    var chatBackRouteName by rememberSaveable { mutableStateOf(AppRoute.Chats.name) }
    var notificationError by remember { mutableStateOf<String?>(null) }
    // Скрытый архив остаётся скрытым после перезапуска: флаг лежит в настройках приложения.
    val context = LocalContext.current
    val chatPreferences = remember(context) { context.applicationContext.getSharedPreferences("chats", Context.MODE_PRIVATE) }
    var archiveHidden by remember { mutableStateOf(chatPreferences.getBoolean("archive_hidden", false)) }
    val onArchiveHiddenChange: (Boolean) -> Unit = { hidden ->
        archiveHidden = hidden
        chatPreferences.edit().putBoolean("archive_hidden", hidden).apply()
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
    if (authState == AuthState.Ready) NotificationPermissionCoordinator(notificationRuntime)
    LaunchedEffect(authState, notificationRequest?.token) {
        val request = notificationRequest ?: return@LaunchedEffect
        if (authState != AuthState.Ready) return@LaunchedEffect
        if (request.settings) openNotifications(AppRoute.Chats)
        else request.chatId?.let { chatId ->
            val result = repository.loadChat(chatId)
            if (result.isSuccess) { selectedChatId = chatId; chatBackRouteName = AppRoute.Chats.name; routeName = AppRoute.Chat.name }
            else notificationError = result.exceptionOrNull()?.message ?: "Не удалось открыть чат"
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

    when (route) {
        AppRoute.Start -> StartScreen(
            isDarkTheme = isDarkTheme,
            tdLibReadiness = tdLibReadiness,
            connectionState = connectionState,
            onThemeChange = onThemeChange,
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

        AppRoute.Chats -> MainTabsScaffold(MainTab.Chats.route.name, MainTab.Chats, tabVisibility, selectTab, tabStateHolder) {
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

        AppRoute.Archive -> ArchiveScreen(
            chats = archivedChats,
            observeFile = repository::observeFile,
            downloadFile = repository::downloadFile,
            onMarkRead = markChatRead,
            onUnarchive = unarchiveChat,
            onBack = { routeName = AppRoute.Chats.name },
            onChatClick = { chat ->
                selectedChatId = chat.id
                chatBackRouteName = AppRoute.Archive.name
                routeName = AppRoute.Chat.name
            }
        )

        AppRoute.Chat -> {
            val selectedChat by remember(repository, selectedChatId) { repository.observeChat(selectedChatId) }.collectAsStateWithLifecycle()
            val chat = selectedChat ?: chats.firstOrNull { it.id == selectedChatId }
            LaunchedEffect(authState, selectedChatId) {
                if (authState == AuthState.Ready && chat == null && selectedChatId != 0L) repository.loadChat(selectedChatId)
            }
            if (chat == null) {
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
                        onChatClick = { selectedChat ->
                            selectedChatId = selectedChat.id
                            chatBackRouteName = AppRoute.Chats.name
                            routeName = AppRoute.Chat.name
                        }
                    )
                }
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
                ChatScreen(
                    chat = chat,
                    info = chatInfo.info,
                    history = history,
                    conversation = repository,
                    chats = chats,
                    observeFile = repository::observeFile,
                    onDownloadFile = repository::downloadFile,
                    onBack = { routeName = chatBackRouteName },
                    onChatInfo = { routeName = AppRoute.ChatInfo.name },
                    connectionState = connectionState
                )
            }
        }
        AppRoute.ChatInfo -> {
            val infoFlow = remember(repository, selectedChatId) { repository.observeChatInfo(selectedChatId) }
            val info by infoFlow.collectAsStateWithLifecycle()
            LaunchedEffect(repository, selectedChatId) { repository.refreshChatInfo(selectedChatId) }
            ProfileScreen(info, false, repository::observeFile, repository::downloadFile,
                onBack = { routeName = AppRoute.Chat.name }, onRefresh = { repository.refreshChatInfo(selectedChatId) },
                onEdit = { routeName = AppRoute.MyProfile.name }, setPhoto = repository::setMyProfilePhoto,
                isDarkTheme = isDarkTheme, onThemeChange = onThemeChange)
        }
        AppRoute.MyProfile -> {
            LaunchedEffect(repository) { repository.refreshMyProfile() }
            ProfileScreen(myProfile, true, repository::observeFile, repository::downloadFile,
                onBack = { routeName = AppRoute.Chats.name }, onRefresh = repository::refreshMyProfile,
                onEdit = { routeName = AppRoute.EditProfile.name }, setPhoto = repository::setMyProfilePhoto,
                onNotifications = { openNotifications(AppRoute.MyProfile) },
                hiddenTabs = MainTab.entries.filter { !tabVisibility.isVisible(it) },
                onOpenTab = { tab -> openTab(tab, AppRoute.MyProfile) },
                isDarkTheme = isDarkTheme, onThemeChange = onThemeChange)
        }
        AppRoute.EditProfile -> {
            myProfile.info?.let { info ->
                EditProfileScreen(info, bioLimit, onBack = { routeName = AppRoute.MyProfile.name }, save = repository::updateMyProfile)
            } ?: LaunchedEffect(Unit) { routeName = AppRoute.MyProfile.name }
        }
        AppRoute.Notifications -> NotificationSettingsScreen(notificationRuntime, connectionState, onBack = { routeName = notificationsReturnRouteName })
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
                onNotifications = { openNotifications(AppRoute.Settings) },
                onProxy = { openProxy(AppRoute.Settings) }
            )
        }
        AppRoute.NavigationSettings -> NavigationSettingsScreen(
            visibility = tabVisibility,
            onVisibilityChange = onTabVisibilityChange,
            onBack = { routeName = AppRoute.Settings.name }
        )
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
    notificationError?.let { error -> androidx.compose.material3.AlertDialog(onDismissRequest = { notificationError = null }, title = { androidx.compose.material3.Text("Уведомление") },
        text = { androidx.compose.material3.Text(error) }, confirmButton = { androidx.compose.material3.TextButton(onClick = { notificationError = null }) { androidx.compose.material3.Text("Закрыть") } }) }
}
