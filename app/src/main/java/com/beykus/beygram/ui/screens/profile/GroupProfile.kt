package com.beykus.beygram.ui.screens.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import com.beykus.beygram.ui.components.topAppBarSurface
import com.beykus.beygram.ui.design.styledShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.presence.LastSeenStyle
import com.beykus.beygram.deeplink.MessageEntityLink
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.ui.components.TelegramAvatar
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.SharedMediaWindow
import com.beykus.beygram.ui.screens.music.ChatLibraryLink
import com.beykus.beygram.ui.screens.music.ChatLibraryMusicRow
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.motion.collapsingOverlayActive
import com.beykus.beygram.ui.motion.expandedBadgesAlpha
import com.beykus.beygram.ui.motion.expandedHeaderIdentityAlpha
import com.beykus.beygram.ui.motion.lerpRect
import com.beykus.beygram.ui.text.resolve
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Вкладки профиля группы. «Участники» показываются списком из профиля, остальные берут окна вложений. */
internal enum class GroupTab(val kind: SharedMediaKind?) {
    Members(null), Media(SharedMediaKind.Media), Files(SharedMediaKind.Files), Links(SharedMediaKind.Links), Voice(SharedMediaKind.Voice),
    Music(SharedMediaKind.Music)
}

/** Во сколько раз уменьшается аватар, когда шапка свёрнута. */
private const val AVATAR_SHRINK = 0.5f
private val SHARED_STATUS_HEIGHT = 240.dp

/** Доля сжатия шапки: 0 — раскрыта, 1 — свёрнута. Свёрнута, как только первым на экране стал элемент после шапки. */
internal fun collapseFraction(firstVisibleIndex: Int, scrollOffsetPx: Int, collapseRangePx: Int): Float {
    if (firstVisibleIndex > 0) return 1f
    if (collapseRangePx <= 0) return 0f
    return (scrollOffsetPx.toFloat() / collapseRangePx).coerceIn(0f, 1f)
}

/**
 * Профиль группы одним списком: шапка сжимается при прокрутке, название переезжает в панель, полоска вкладок закрепляется сверху.
 * Вкладки вложений догружают страницы по мере прокрутки, как в профиле чата.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GroupProfileScreen(
    info: ProfileInfo, state: ProfileState, lastSeenStyle: LastSeenStyle,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit,
    onBack: () -> Unit, onRefresh: () -> Unit, onAvatarClick: () -> Unit,
    onDelete: suspend () -> Result<Unit>, onDeleted: () -> Unit,
    onMembers: () -> Unit, onOpenUser: (Long) -> Unit,
    onStatistics: (() -> Unit)?,
    sharedMessages: (suspend (SharedMediaKind, Long, Boolean) -> Result<MessageSearchPage>)?, confirmExport: suspend (Long) -> Boolean,
    messageRights: suspend (Long) -> MessageCapabilities?, forwardMessage: suspend (Long, Long) -> Result<Unit>, chats: List<ChatPreview>,
    onContentOpened: (Long) -> Unit, onOpenMessage: (messageId: Long, forumTopicId: Long) -> Unit,
    onManage: (() -> Unit)? = null,
    /** Repository-backed automatic download for the shared-media viewer. */
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null,
    /** The chat's library tracks, shown as a row when there are some. Null hides the row. */
    chatLibrary: ChatLibraryLink? = null,
    /** Opens a Yandex Music link from the links tab natively; null keeps it in the browser. */
    onOpenMessageEntity: ((MessageEntityLink) -> Unit)? = null,
    loadForwardTopics: (suspend (Long) -> Result<List<ForumTopic>?>)? = null,
    forwardToTopic: (suspend (Long, Long, Long) -> Result<Unit>)? = null,
    recipientRepository: TelegramRepository? = null,
    /** The shared-element transition back to the chat (#999); null keeps the plain navigation. */
    profileTransition: com.beykus.beygram.ui.motion.ChatProfileHeaderTarget? = null
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    // Прокрутку читаем только при рисовании: иначе каждый кадр прокрутки перекомпоновывал бы весь экран.
    val collapse = remember { derivedStateOf { collapseFraction(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, headerHeightPx) } }
    val collapseGeometry = remember(info.id) { ProfileCollapseGeometry() }
    val windows = rememberSharedMediaWindows(info.id) { kind, from, newer -> sharedMessages?.invoke(kind, from, newer) ?: Result.failure(IllegalStateException()) }
    val openMessage: (Long) -> Unit = { id -> onOpenMessage(id, windows.topicOf(id)) }
    val tabs = if (sharedMessages == null) listOf(GroupTab.Members) else GroupTab.entries
    var selectedTab by rememberSaveable(info.id) { mutableStateOf(GroupTab.Members) }
    // Сохранённая вкладка может пропасть из полоски, если источника вложений больше нет: тогда показываем участников.
    val activeTab = selectedTab.takeIf { it in tabs } ?: GroupTab.Members
    var viewing by remember(info.id) { mutableStateOf<Long?>(null) }
    val window = activeTab.kind?.let { windows.getValue(it) }
    val title = info.title.ifBlank { stringResource(R.string.user_name_unknown) }
    val compactStatus = info.profileSubtitleLine(lastSeenStyle)
    // Индекс полоски вкладок в списке: перед ней идут шапка, строки, статистика (если доступна) и администраторы.
    val tabsIndex = 2 + (if (onStatistics != null) 1 else 0) + (if (info.administrators.isEmpty()) 0 else 1)
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.let { it.index >= listState.layoutInfo.totalItemsCount - PREFETCH_ROWS } ?: true } }
    // Пока список дошёл до конца вкладки, грузим её страницы; первая страница вкладки грузится тем же путём.
    if (window != null) LaunchedEffect(window, nearEnd, window.messages.size, window.hasOlder) {
        if (nearEnd && window.hasOlder) window.loadOlder()
    }
    Box(Modifier.fillMaxSize().glassScreenBackground(design, palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
    Column(Modifier.fillMaxSize()) {
        GroupProfileToolbar(title, collapse, info, compactStatus, observeFile, downloadFile, collapseGeometry = collapseGeometry, onBack = onBack, onDelete = onDelete, onDeleted = onDeleted, profileTransition = profileTransition)
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        // Ошибку показываем над списком: в конце длинной вкладки она оказалась бы за сотнями строк.
        state.error?.let { error ->
            ProfileLoadError(error.resolve(), state.isLoading, onRefresh)
        }
        // The compact toolbar identity is the return flight's source from a collapsed profile.
        LaunchedEffect(profileTransition, collapse) {
            if (profileTransition == null) return@LaunchedEffect
            snapshotFlow { collapse.value > com.beykus.beygram.ui.motion.ProfileCollapsedSwitch }
                .distinctUntilChanged()
                .collect { profileTransition.reportCollapsed(it) }
        }
        val transitionFingerprint = profileTransition?.let {
            val container = LocalWindowInfo.current.containerSize
            val densityScale = LocalDensity.current.density
            remember(container, densityScale) {
                com.beykus.beygram.ui.motion.ChatProfileWindowFingerprint(container.width, container.height, densityScale)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = 28.dp)) {
            item(key = "header") {
                Column(
                    Modifier.fillMaxWidth().onSizeChanged { headerHeightPx = it.height }.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .graphicsLayer {
                                alpha = expandedHeaderIdentityAlpha(collapse.value, collapseGeometry.overlayReady) *
                                    (profileTransition?.headerVisibility() ?: 1f)
                            }
                            .clickable(
                                enabled = info.photo != null && collapse.value <= 0f && (profileTransition == null || !profileTransition.flightActive()),
                                role = Role.Button,
                                onClick = onAvatarClick
                            )
                            .semantics { contentDescription = context.getString(R.string.a11y_profile_photo_open) }
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                val scrollOffset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f
                                val zeroBounds = bounds.translate(0f, scrollOffset)
                                if (listState.firstVisibleItemIndex == 0) collapseGeometry.expandedAvatarAtZero = zeroBounds
                                if (profileTransition != null && transitionFingerprint != null) {
                                    val visualBounds = collapseGeometry.compactAvatar?.let { compact ->
                                        if (collapse.value > 0f) lerpRect(zeroBounds, compact, collapse.value) else bounds
                                    } ?: bounds
                                    profileTransition.reportAvatar(visualBounds, transitionFingerprint)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        TelegramAvatar(info.initials, palette.accent, 112.dp, info.photo, observeFile, downloadFile,
                            Modifier.clearAndSetSemantics {})
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, color = palette.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                            modifier = Modifier
                                .graphicsLayer {
                                    alpha = expandedHeaderIdentityAlpha(collapse.value, collapseGeometry.overlayReady) *
                                        (profileTransition?.headerVisibility() ?: 1f)
                                }
                                .onGloballyPositioned { coordinates ->
                                    val bounds = coordinates.boundsInWindow()
                                    val scrollOffset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f
                                    val zeroBounds = bounds.translate(0f, scrollOffset)
                                    if (listState.firstVisibleItemIndex == 0) collapseGeometry.expandedTitleAtZero = zeroBounds
                                    if (profileTransition != null && transitionFingerprint != null) {
                                        val visualBounds = collapseGeometry.compactTitle?.let { compact ->
                                            if (collapse.value > 0f) lerpRect(zeroBounds, compact, collapse.value) else bounds
                                        } ?: bounds
                                        profileTransition.reportTitle(visualBounds, transitionFingerprint)
                                    }
                                }
                                .semantics { heading() })
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .graphicsLayer {
                                    alpha = expandedHeaderIdentityAlpha(collapse.value, collapseGeometry.overlayReady) *
                                        (profileTransition?.headerVisibility() ?: 1f)
                                }
                                .onGloballyPositioned { coordinates ->
                                    val bounds = coordinates.boundsInWindow()
                                    val scrollOffset = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 0f
                                    val zeroBounds = bounds.translate(0f, scrollOffset)
                                    if (listState.firstVisibleItemIndex == 0) collapseGeometry.expandedStatusAtZero = zeroBounds
                                    if (profileTransition != null && transitionFingerprint != null) {
                                        val visualBounds = collapseGeometry.compactStatus?.let { compact ->
                                            if (collapse.value > 0f) lerpRect(zeroBounds, compact, collapse.value) else bounds
                                        } ?: bounds
                                        profileTransition.reportStatus(visualBounds, transitionFingerprint)
                                    }
                                }
                        ) {
                            ProfileSubtitle(info, lastSeenStyle)
                        }
                        Box(Modifier.graphicsLayer {
                            alpha = expandedBadgesAlpha(collapse.value) * (profileTransition?.headerVisibility() ?: 1f)
                        }) {
                            ProfileBadges(info)
                        }
                    }
                }
            }
            item(key = "rows") {
                Column {
                    ProfileRowsCard(profileRows(context, info, withSharedCounts = sharedMessages == null))
                    chatLibrary?.let { link -> ChatLibraryMusicRow(info.id, link.accountId) { link.onOpen(info.id) } }
                    if (onManage != null && info.groupAdmin?.canManageAnything == true) {
                        GroupManagementEntry(info.kind, onManage)
                    }
                }
            }
            if (onStatistics != null) item(key = "statistics") { GroupStatisticsEntry(onStatistics) }
            if (info.administrators.isNotEmpty()) item(key = "administrators") { AdministratorsSection(info.administrators, onOpenUser) }
            stickyHeader(key = "tabs") {
                GroupTabRow(
                    tabs = tabs, selected = activeTab,
                    countText = { tab -> if (tab == GroupTab.Members) info.memberCount?.toString() else tab.kind?.let { info.sharedCounts?.countOf(it)?.toString() } },
                    onSelect = { tab ->
                        selectedTab = tab
                        // Если список ушёл вниз по прежней вкладке, возвращаем его к полоске: иначе новая вкладка окажется за своим концом.
                        scope.launch { if (listState.firstVisibleItemIndex >= tabsIndex) listState.scrollToItem(tabsIndex) }
                    }
                )
            }
            val kind = activeTab.kind
            if (kind == null || window == null) item(key = "members") { GroupMembers(info, state.isLoading, observeFile, downloadFile, onMembers, onOpenUser) }
            else sharedItems(kind, info.id, window, observeFile, downloadFile, confirmExport, onContentOpened, onOpenMedia = { viewing = it }, onOpenMessage = openMessage, onRetry = { scope.launch { window.loadOlder() } }, autoDownloadFile = autoDownloadFile, onOpenMessageEntity = onOpenMessageEntity)
        }
    }
    if (collapsingOverlayActive(collapse.value) && collapseGeometry.overlayReady &&
        (profileTransition == null || !profileTransition.flightActive())
    ) {
        ProfileCollapsingOverlay(
            collapse = collapse.value,
            geometry = collapseGeometry,
            title = title,
            status = compactStatus,
            avatar = {
                TelegramAvatar(info.initials, palette.accent, 112.dp, info.photo, observeFile, downloadFile,
                    Modifier.clearAndSetSemantics {})
            }
        )
    }
    }
    SharedMediaViewer(media = windows.getValue(SharedMediaKind.Media), viewing = viewing, onViewing = { viewing = it },
        chatId = info.id,
        observeFile = observeFile, downloadFile = downloadFile, onOpenMessage = openMessage,
        confirmExport = confirmExport, messageRights = messageRights, forwardMessage = forwardMessage, chats = chats,
        onContentOpened = onContentOpened, autoDownloadFile = autoDownloadFile,
        loadForwardTopics = loadForwardTopics, forwardToTopic = forwardToTopic, recipientRepository = recipientRepository)
}

/** Entry point shown only when TDLib's canGetStatistics allowed the current admin to open stats. */
@Composable
private fun GroupStatisticsEntry(onClick: () -> Unit) {
    val palette = beyGramPalette()
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 48.dp)
    ) {
        Text(
            stringResource(R.string.group_statistics_title),
            color = palette.accentInk,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Панель профиля группы. Делегирует общему [ProfileToolbar], чтобы компактная шапка совпадала с шапкой чата. */
@Composable
private fun GroupProfileToolbar(
    title: String, collapse: State<Float>,
    info: ProfileInfo, compactStatus: String,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit,
    collapseGeometry: ProfileCollapseGeometry,
    onBack: () -> Unit,
    onDelete: suspend () -> Result<Unit>, onDeleted: () -> Unit,
    profileTransition: com.beykus.beygram.ui.motion.ChatProfileHeaderTarget? = null
) {
    val palette = beyGramPalette()
    ProfileToolbar(
        title = stringResource(R.string.profile_title_chat),
        onBack = onBack,
        collapse = collapse,
        compactTitle = title,
        compactStatus = compactStatus,
        compactAvatar = {
            TelegramAvatar(info.initials, palette.accent, 40.dp, info.photo, observeFile, downloadFile,
                Modifier.clearAndSetSemantics {})
        },
        collapseGeometry = collapseGeometry,
        transition = profileTransition
    ) {
        ChatInfoMenu(
            canBlock = false, canReport = false, isBlocked = false, canDelete = true,
            onSetBlocked = { Result.success(Unit) }, onReportSpam = { Result.success(Unit) },
            onDelete = onDelete, onDeleted = onDeleted
        )
    }
}

/** Полоска вкладок. Закрепляется сверху, когда список прокручен до неё; на узком экране прокручивается по горизонтали. */
@Composable
private fun GroupTabRow(tabs: List<GroupTab>, selected: GroupTab, countText: (GroupTab) -> String?, onSelect: (GroupTab) -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().background(palette.screen).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            Row(
                Modifier.clip(RoundedCornerShape(percent = 50))
                    .background(if (isSelected) palette.accentSoft else palette.surface2)
                    .heightIn(min = 48.dp)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(tab.titleRes()), color = if (isSelected) palette.accentInk else palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                countText(tab)?.let { Text(it, color = if (isSelected) palette.accentInk else palette.textSecondary, fontSize = 13.sp, maxLines = 1) }
            }
        }
    }
}

/** Участники группы из профиля. Полный список открывается отдельным экраном. */
@Composable
private fun GroupMembers(info: ProfileInfo, isLoading: Boolean, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, onMembers: () -> Unit, onOpenUser: (Long) -> Unit) {
    val palette = beyGramPalette()
    if (!info.canListMembers) {
        Text(stringResource(R.string.profile_members_hidden), color = palette.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
        return
    }
    // Пока превью не пришло, показываем загрузку, а не одну кнопку «Все участники». Пустое превью без загрузки ничего не рисует.
    if (info.memberPreview.isEmpty()) {
        if (isLoading) Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), color = palette.accent, strokeWidth = 2.dp)
        }
        return
    }
    Column(Modifier.padding(horizontal = 16.dp).clip(styledShape(20.dp)).background(palette.surface).padding(vertical = 4.dp)) {
        info.memberPreview.forEachIndexed { index, member ->
            if (index > 0) HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 82.dp))
            MemberRow(member, observeFile, downloadFile) { onOpenUser(member.userId) }
        }
        TextButton(onClick = onMembers, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_members_all, info.memberCount ?: info.memberPreview.size), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Вкладка вложений: сетка для «Медиа», строки для остальных. Пустая вкладка показывает загрузку, пустоту или ошибку. */
private fun LazyListScope.sharedItems(
    kind: SharedMediaKind, chatId: Long, window: SharedMediaWindow,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, confirmExport: suspend (Long) -> Boolean,
    onContentOpened: (Long) -> Unit, onOpenMedia: (Long) -> Unit, onOpenMessage: (Long) -> Unit, onRetry: () -> Unit,
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null,
    onOpenMessageEntity: ((MessageEntityLink) -> Unit)? = null
) {
    // «Ссылки» бывают и в тексте, поэтому им вложение не нужно; остальные вкладки показывают только сообщения с вложением.
    val shown = window.messages.asReversed().filter { kind == SharedMediaKind.Links || it.media != null }
    if (shown.isEmpty()) {
        item(key = "status") {
            Box(Modifier.fillMaxWidth().height(SHARED_STATUS_HEIGHT), contentAlignment = Alignment.Center) { SharedMediaStatus(kind, window, onRetry) }
        }
        return
    }
    if (kind == SharedMediaKind.Media) {
        items(shown.chunked(GRID_COLUMNS), key = { row -> row.first().id }) { row ->
            SharedMediaGridRow(row, observeFile, downloadFile, onOpenMedia, onOpenMessage)
        }
    } else {
        items(shown, key = { it.id }) { message ->
            if (kind == SharedMediaKind.Music) {
                message.media?.let { SharedMusicRow(chatId, shown, message, it, observeFile, downloadFile, onOpenMessage, autoDownloadFile) }
            } else {
                SharedMediaRowItem(kind, chatId, message, observeFile, downloadFile, confirmExport, onContentOpened, onOpenMessage, onOpenMessageEntity)
            }
        }
    }
    item(key = "footer") { SharedMediaFooter(window, onRetry) }
}

/** Ряд сетки «Медиа»: до трёх ячеек. Недостающие места заполняются пустыми весами, чтобы ячейки не растягивались. */
@Composable
private fun SharedMediaGridRow(
    row: List<ChatMessage>, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit,
    onOpenMedia: (Long) -> Unit, onOpenMessage: (Long) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 1.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        row.forEach { message ->
            message.media?.let { media ->
                SharedMediaCell(media, observeFile, downloadFile, onClick = { if (media.isSecret) onOpenMessage(message.id) else onOpenMedia(message.id) }, modifier = Modifier.weight(1f))
            }
        }
        repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

private fun GroupTab.titleRes(): Int = when (this) {
    GroupTab.Members -> R.string.profile_members_tab
    GroupTab.Media -> R.string.profile_shared_media
    GroupTab.Files -> R.string.profile_shared_files
    GroupTab.Links -> R.string.profile_shared_links
    GroupTab.Voice -> R.string.profile_shared_voice
    GroupTab.Music -> R.string.profile_shared_music
}
