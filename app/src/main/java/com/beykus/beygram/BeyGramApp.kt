package com.beykus.beygram

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.beykus.beygram.ui.screens.auth.LoginCodeScreen
import com.beykus.beygram.ui.screens.auth.LoginPasswordScreen
import com.beykus.beygram.ui.screens.auth.LoginPhoneScreen
import com.beykus.beygram.ui.screens.chat.ChatScreen
import com.beykus.beygram.ui.screens.chats.ChatsScreen
import com.beykus.beygram.ui.screens.start.StartScreen
import com.beykus.beygram.ui.screens.profile.ProfileScreen
import com.beykus.beygram.ui.screens.profile.EditProfileScreen
import com.beykus.beygram.domain.model.NotificationOpenRequest
import com.beykus.beygram.notifications.NotificationRuntime
import com.beykus.beygram.ui.screens.notifications.NotificationSettingsScreen
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
    DisposableEffect(repository, notificationRuntime) { notificationRuntime.attach(repository); onDispose { } }

    BeyGramAppContent(
        repository = repository,
        tdLibReadiness = tdLibReadiness,
        isDarkTheme = isDarkTheme,
        onThemeChange = onThemeChange,
        notificationRuntime = notificationRuntime,
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
    notificationRequest: NotificationOpenRequest?,
    onNotificationHandled: (Long) -> Unit
) {
    // ---- Реактивное состояние ------------------------------------------------
    val authState by repository.authState.collectAsStateWithLifecycle()
    val chats by repository.chats.collectAsStateWithLifecycle()
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
    var notificationError by remember { mutableStateOf<String?>(null) }
    if (authState == AuthState.Ready) NotificationPermissionCoordinator(notificationRuntime)
    LaunchedEffect(authState, notificationRequest?.token) {
        val request = notificationRequest ?: return@LaunchedEffect
        if (authState != AuthState.Ready) return@LaunchedEffect
        if (request.settings) routeName = AppRoute.Notifications.name
        else request.chatId?.let { chatId ->
            val result = repository.loadChat(chatId)
            if (result.isSuccess) { selectedChatId = chatId; routeName = AppRoute.Chat.name }
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

    when (route) {
        AppRoute.Start -> StartScreen(
            isDarkTheme = isDarkTheme,
            tdLibReadiness = tdLibReadiness,
            connectionState = connectionState,
            onThemeChange = onThemeChange,
            onAddProxy = repository::addProxy,
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
            onAddProxy = repository::addProxy,
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

        AppRoute.Chats -> ChatsScreen(
            chats = chats,
            myProfile = myProfile.info,
            onNotifications = { routeName = AppRoute.Notifications.name },
            observeFile = repository::observeFile,
            downloadFile = repository::downloadFile,
            onMyProfile = { routeName = AppRoute.MyProfile.name },
            onChatClick = { chat ->
                selectedChatId = chat.id
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
                ChatsScreen(
                    chats = chats,
                    myProfile = myProfile.info,
                    onNotifications = { routeName = AppRoute.Notifications.name },
                    observeFile = repository::observeFile,
                    downloadFile = repository::downloadFile,
                    onMyProfile = { routeName = AppRoute.MyProfile.name },
                    onChatClick = { selectedChat ->
                        selectedChatId = selectedChat.id
                        routeName = AppRoute.Chat.name
                    }
                )
            } else {
                val historyFlow = remember(repository, selectedChatId) { repository.observeChatHistory(selectedChatId) }
                val history by historyFlow.collectAsStateWithLifecycle()
                DisposableEffect(repository, selectedChatId) {
                    val openedChatId = selectedChatId
                    repository.openChat(openedChatId)
                    onDispose { repository.closeChat(openedChatId) }
                }
                ChatScreen(
                    chat = chat,
                    history = history,
                    conversation = repository,
                    observeFile = repository::observeFile,
                    onDownloadFile = repository::downloadFile,
                    onBack = { routeName = AppRoute.Chats.name },
                    onChatInfo = { routeName = AppRoute.ChatInfo.name }
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
                onNotifications = { routeName = AppRoute.Notifications.name },
                isDarkTheme = isDarkTheme, onThemeChange = onThemeChange)
        }
        AppRoute.EditProfile -> {
            myProfile.info?.let { info ->
                EditProfileScreen(info, bioLimit, onBack = { routeName = AppRoute.MyProfile.name }, save = repository::updateMyProfile)
            } ?: LaunchedEffect(Unit) { routeName = AppRoute.MyProfile.name }
        }
        AppRoute.Notifications -> NotificationSettingsScreen(notificationRuntime, connectionState, onBack = { routeName = AppRoute.Chats.name })
    }
    notificationError?.let { error -> androidx.compose.material3.AlertDialog(onDismissRequest = { notificationError = null }, title = { androidx.compose.material3.Text("Уведомление") },
        text = { androidx.compose.material3.Text(error) }, confirmButton = { androidx.compose.material3.TextButton(onClick = { notificationError = null }) { androidx.compose.material3.Text("Закрыть") } }) }
}
