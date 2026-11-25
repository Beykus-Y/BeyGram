package com.beykus.beygram.ui.screens.profile

import com.beykus.beygram.ui.design.panelFill
import com.beykus.beygram.ui.design.PanelFloor
import com.beykus.beygram.ui.design.HairlineEdge
import com.beykus.beygram.ui.design.signatureChrome
import android.content.Context
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.design.glassCard
import com.beykus.beygram.ui.design.glassScreenBackground
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.*
import com.beykus.beygram.ui.components.Button
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.TopBarVariant
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.flow.distinctUntilChanged
import com.beykus.beygram.ui.motion.collapsingOverlayActive
import com.beykus.beygram.ui.motion.collapsingTextCrossfade
import com.beykus.beygram.ui.motion.compactToolbarIdentityAlpha
import com.beykus.beygram.ui.motion.expandedBadgesAlpha
import com.beykus.beygram.ui.motion.expandedHeaderIdentityAlpha
import com.beykus.beygram.ui.motion.lerpRect
import com.beykus.beygram.ui.motion.staticToolbarTitleAlpha
import com.beykus.beygram.ui.motion.windowProgress
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.data.media.OutgoingMediaImporter
import com.beykus.beygram.data.presence.LastSeenStyle
import com.beykus.beygram.deeplink.MessageEntityLink
import com.beykus.beygram.domain.model.*
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.navigation.MainTab
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.*
import com.beykus.beygram.ui.screens.chat.LocalMediaImage
import com.beykus.beygram.ui.screens.chat.MediaViewer
import com.beykus.beygram.ui.screens.chat.MediaViewerItem
import com.beykus.beygram.ui.screens.chat.presenceText
import com.beykus.beygram.ui.screens.chat.rememberPresenceClock
import com.beykus.beygram.ui.text.resolve
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.beykus.beygram.R
import com.beykus.beygram.ui.screens.music.ChatLibraryLink
import com.beykus.beygram.ui.screens.music.ChatLibraryMusicRow
import java.time.LocalDate
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProfileScreen(
    state: ProfileState, isOwnProfile: Boolean,
    observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit,
    onBack: () -> Unit, onRefresh: () -> Unit, onEdit: () -> Unit,
    setPhoto: suspend (OutgoingMedia) -> Result<Unit>,
    lastSeenStyle: LastSeenStyle,
    loadProfilePhotos: suspend (Long, Int, Int) -> Result<ProfilePhotosPage> = { _, _, _ -> Result.failure(IllegalStateException("Profile photos unavailable")) },
    deleteMyProfilePhoto: suspend (Long) -> Result<Unit> = { Result.failure(IllegalStateException("Profile photo deletion unavailable")) },
    setPreviousProfilePhoto: suspend (Long) -> Result<Unit> = { Result.failure(IllegalStateException("Previous photo unavailable")) },
    onNotifications: () -> Unit = {}, onMembers: () -> Unit = {}, onOpenUser: (Long) -> Unit = {},
    onManage: (() -> Unit)? = null,
    onStatistics: (() -> Unit)? = null,
    onSetBlocked: suspend (Boolean) -> Result<Unit> = { Result.failure(IllegalStateException()) },
    onReportSpam: suspend () -> Result<Unit> = { Result.failure(IllegalStateException()) },
    onDelete: suspend () -> Result<Unit> = { Result.failure(IllegalStateException()) },
    onDeleted: () -> Unit = {}, onOpenLinkedChannel: (Long) -> Unit = {},
    hiddenTabs: List<MainTab> = emptyList(), onOpenTab: (MainTab) -> Unit = {},
    sharedMessages: (suspend (SharedMediaKind, Long, Boolean) -> Result<MessageSearchPage>)? = null,
    onOpenMessageEntity: ((MessageEntityLink) -> Unit)? = null,
    confirmExport: suspend (Long) -> Boolean = { false }, messageRights: suspend (Long) -> MessageCapabilities? = { null },
    forwardMessage: suspend (Long, Long) -> Result<Unit> = { _, _ -> Result.failure(IllegalStateException()) }, chats: List<ChatPreview> = emptyList(),
    onContentOpened: (Long) -> Unit = {}, onOpenMessage: (messageId: Long, forumTopicId: Long) -> Unit = { _, _ -> },
    onPremium: () -> Unit = {}, onStars: () -> Unit = {},
    onGifts: (profileId: Long, isSelf: Boolean, title: String) -> Unit = { _, _, _ -> },
    /** Repository-backed automatic download for shared media and profile galleries. */
    autoDownloadFile: ((AutoDownloadRequest) -> Unit)? = null,
    /** Creates a device-specific E2E chat for a regular user; null hides the profile entry. */
    startSecretChat: (suspend (Long) -> Result<Long>)? = null,
    /** Chat-level no-forwards restriction: music stays out of public playlists and MediaSession. */
    protectedContent: Boolean = false,
    onSecretChatOpened: (Long) -> Unit = {},
    /** The chat's library tracks, shown as a row when there are some. Null hides the row. */
    chatLibrary: ChatLibraryLink? = null,
    /** Own profile music: removes one track from the account list. Other profiles never call it. */
    removeProfileAudio: suspend (Int) -> Result<Unit> = { Result.failure(IllegalStateException("Profile music unavailable")) },
    /** Own profile music: moves one track to the first place of the account list. */
    moveProfileAudioToFront: suspend (Int) -> Result<Unit> = { Result.failure(IllegalStateException("Profile music unavailable")) },
    loadForwardTopics: (suspend (Long) -> Result<List<ForumTopic>?>)? = null,
    forwardToTopic: (suspend (Long, Long, Long) -> Result<Unit>)? = null,
    recipientRepository: TelegramRepository? = null,
    /** The shared-element transition back to the chat (#999); null keeps the plain navigation. */
    profileTransition: com.beykus.beygram.ui.motion.ChatProfileHeaderTarget? = null
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val loadingText = stringResource(R.string.a11y_state_loading)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val importer = remember { OutgoingMediaImporter(context) }
    var showPhoto by rememberSaveable(state.info?.id) { mutableStateOf(false) }
    // Profile 2.0 C1 (#1138): experimental, off by default. A reveals the avatar's gallery directly.
    remember(context) { ProfileSceneSettings.load(context) }
    val sceneEnabled by ProfileSceneSettings.enabled.collectAsState()
    val sceneC = ProfileSceneSettings.variantFor(sceneEnabled) == ProfileVariant.C
    var photoExpanded by rememberSaveable(state.info?.id) { mutableStateOf(false) }
    var pendingPhoto by rememberSaveable { mutableStateOf<OutgoingMedia?>(null) }
    var preparing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var photoError by rememberSaveable { mutableStateOf<String?>(null) }
    var secretChatConfirm by rememberSaveable(state.info?.id) { mutableStateOf(false) }
    var secretChatBusy by remember { mutableStateOf(false) }
    var secretChatError by rememberSaveable(state.info?.id) { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            preparing = true; photoError = null
            try { pendingPhoto = replacePendingPhoto(pendingPhoto, { importer.prepare(uri, asDocument = false, squarePhoto = true) }, importer::discard) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { photoError = error.userMessage(context, R.string.profile_photo_prepare_failed) }
            finally { preparing = false }
        }
    }
    // Back from the profile flies the shared elements back to the chat header when the gesture or
    // the tap can drive the flight; otherwise it is the plain transition this screen always had.
    if (profileTransition == null) {
        BackHandler { if (!saving && !preparing) onBack() }
    } else {
        PredictiveBackHandler { progress ->
            // The photo editor keeps Back for itself exactly as before.
            if (saving || preparing) {
                progress.collect { }
                return@PredictiveBackHandler
            }
            if (profileTransition.beginBackGesture()) {
                try {
                    progress.collect { event -> profileTransition.updateBackGesture(event.progress) }
                    // The gesture committed: navigate exactly once and finish the flight.
                    profileTransition.commitBackGesture()
                } catch (cancelled: CancellationException) {
                    // The gesture was cancelled: the profile returns without a jump.
                    profileTransition.cancelBackGesture()
                }
            } else {
                // No usable geometry: collect as the contract requires, then navigate plainly.
                progress.collect { }
                onBack()
            }
        }
    }
    // Profile 2.0 C1: while a photo is revealed, Back folds it first. Registered after the handlers above so it
    // takes priority, and disabled otherwise so Back keeps its usual behaviour (including after a rotation).
    BackHandler(enabled = ProfileSceneSettings.backClosesExpansion(sceneC, photoExpanded)) { photoExpanded = false }
    val groupInfo = state.info?.takeIf { it.kind == ProfileKind.Group }
    if (pendingPhoto == null && groupInfo != null) GroupProfileScreen(
        info = groupInfo, state = state, lastSeenStyle = lastSeenStyle, observeFile = observeFile, downloadFile = downloadFile,
        onBack = { if (!saving && !preparing) profileTransition?.back() ?: onBack() }, onRefresh = onRefresh, onAvatarClick = { showPhoto = true },
        onDelete = onDelete, onDeleted = onDeleted,
        onMembers = onMembers, onOpenUser = onOpenUser, onStatistics = onStatistics,
        sharedMessages = sharedMessages, confirmExport = confirmExport,
        messageRights = messageRights, forwardMessage = forwardMessage, chats = chats,
        onContentOpened = onContentOpened, onOpenMessage = onOpenMessage, onManage = onManage, autoDownloadFile = autoDownloadFile,
        chatLibrary = chatLibrary, onOpenMessageEntity = onOpenMessageEntity,
        loadForwardTopics = loadForwardTopics, forwardToTopic = forwardToTopic, recipientRepository = recipientRepository,
        profileTransition = profileTransition
    ) else if (pendingPhoto == null) {
        // The identity block collapses with the real scroll offset: the avatar shrinks into the
        // compact toolbar identity and the title swaps in place of the static screen label (#999).
        val scrollState = rememberScrollState()
        var identityHeightPx by remember { mutableIntStateOf(0) }
        val collapse = remember { derivedStateOf { com.beykus.beygram.ui.motion.collapseFractionForScroll(scrollState.value, identityHeightPx) } }
        val collapseGeometry = remember(state.info?.id) { ProfileCollapseGeometry() }
        val transitionTarget = profileTransition
        val transitionFingerprint = transitionTarget?.let {
            val container = LocalWindowInfo.current.containerSize
            val densityScale = LocalDensity.current.density
            remember(container, densityScale) {
                com.beykus.beygram.ui.motion.ChatProfileWindowFingerprint(container.width, container.height, densityScale)
            }
        }
        LaunchedEffect(transitionTarget, collapse) {
            if (transitionTarget == null) return@LaunchedEffect
            snapshotFlow { collapse.value > com.beykus.beygram.ui.motion.ProfileCollapsedSwitch }
                .distinctUntilChanged()
                .collect { transitionTarget.reportCollapsed(it) }
        }
        val compactTitle = state.info?.profileTitleText()
        val compactStatus = state.info?.let { info -> info.profileSubtitleLine(lastSeenStyle) }
        Box(Modifier.fillMaxSize().glassScreenBackground(design, palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize()) {
        ProfileToolbar(
            if (isOwnProfile) stringResource(R.string.profile_title_own) else stringResource(R.string.profile_title_chat),
            { if (!saving && !preparing) profileTransition?.back() ?: onBack() },
            collapse = if (state.info != null) collapse else null,
            compactTitle = compactTitle,
            compactStatus = compactStatus,
            compactAvatar = state.info?.let { info ->
                {
                    TelegramAvatar(info.initials, palette.accent, 40.dp, info.photo, observeFile, downloadFile,
                        Modifier.clearAndSetSemantics {})
                }
            },
            collapseGeometry = collapseGeometry,
            transition = profileTransition
        ) {
            if (!isOwnProfile) ChatInfoMenu(
                canBlock = state.info?.kind in listOf(ProfileKind.Person, ProfileKind.Bot) && state.info?.isSelf != true,
                canReport = state.info?.kind in listOf(ProfileKind.Person, ProfileKind.Bot) && state.info?.isSelf != true,
                isBlocked = state.info?.isBlocked == true,
                canDelete = true, onSetBlocked = onSetBlocked, onReportSpam = onReportSpam, onDelete = onDelete, onDeleted = onDeleted
            )
        }
        if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = loadingText })
        state.error?.let { ProfileLoadError(it.resolve(), state.isLoading, onRefresh) }
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(bottom = 28.dp)) {
            state.info?.let { info ->
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(
                        Modifier.fillMaxWidth().onSizeChanged { identityHeightPx = it.height },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                    // Only an avatar with a photo is a control; without one there is nothing to open and nothing to announce.
                    // While a flight or mid-scroll collapse carries the avatar it is invisible, so it must not be tappable either.
                    Box(
                        (if (info.photo != null && collapse.value <= 0f && (profileTransition == null || !profileTransition.flightActive())) {
                            Modifier.clickable(role = Role.Button) {
                                if (ProfileSceneSettings.avatarTapAction(sceneC, photoExpanded) == AvatarTap.RevealPhoto) photoExpanded = true
                                else showPhoto = true
                            }
                                .semantics {
                                    contentDescription = context.getString(
                                        if (sceneC && !photoExpanded) R.string.a11y_profile_photo_expand else R.string.a11y_profile_photo_open
                                    )
                                }
                        } else {
                            Modifier
                        }).graphicsLayer {
                            alpha = expandedHeaderIdentityAlpha(collapse.value, collapseGeometry.overlayReady) *
                                (profileTransition?.headerVisibility() ?: 1f)
                        }.onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            val zeroBounds = bounds.translate(0f, scrollState.value.toFloat())
                            collapseGeometry.expandedAvatarAtZero = zeroBounds
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
                    if (sceneC && photoExpanded && info.photo != null) {
                        Spacer(Modifier.height(12.dp))
                        ProfileExpandedPhoto(
                            photo = info.photo, color = palette.accent, observeFile = observeFile, downloadFile = downloadFile,
                            onOpenGallery = { showPhoto = true },
                            scrollPx = { scrollState.value.toFloat() }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(info.profileTitleText(), color = palette.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = expandedHeaderIdentityAlpha(collapse.value, collapseGeometry.overlayReady) *
                                    (profileTransition?.headerVisibility() ?: 1f)
                            }
                            .onGloballyPositioned { coordinates ->
                                val bounds = coordinates.boundsInWindow()
                                val zeroBounds = bounds.translate(0f, scrollState.value.toFloat())
                                collapseGeometry.expandedTitleAtZero = zeroBounds
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
                                val zeroBounds = bounds.translate(0f, scrollState.value.toFloat())
                                collapseGeometry.expandedStatusAtZero = zeroBounds
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
                    }
                    Box(Modifier.graphicsLayer {
                        alpha = expandedBadgesAlpha(collapse.value) * (profileTransition?.headerVisibility() ?: 1f)
                    }) {
                        ProfileBadges(info)
                    }
                    if (info.profileAudios.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        ProfileMusicBar(
                            ownerId = info.id, tracks = info.profileAudios, own = isOwnProfile,
                            observeFile = observeFile, downloadFile = downloadFile, autoDownloadFile = autoDownloadFile,
                            removeTrack = removeProfileAudio, moveTrackToFront = moveProfileAudioToFront
                        )
                    }
                    if (isOwnProfile) {
                        Spacer(Modifier.height(14.dp))
                        // Equal-width actions at normal font sizes; stack with full labels if the screen or text is large.
                        // Keep the complete action names in accessibility semantics even when compact labels are shown.
                        val editDescription = stringResource(R.string.profile_action_edit)
                        val photoDescription = stringResource(R.string.profile_action_change_photo)
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val horizontal = maxWidth >= 320.dp && LocalDensity.current.fontScale < 1.3f
                            // Edit is the primary action (accent fill); the photo change is the secondary one (neutral tonal).
                            // Both used to be accent-tinted, and a crimson accent made the tonal pair read as a destructive wine.
                            val editButton: @Composable (Modifier) -> Unit = { actionModifier ->
                                Button(
                                    onClick = onEdit,
                                    enabled = !state.isLoading && state.error == null && !saving && !preparing,
                                    shape = styledShape(14.dp),
                                    modifier = actionModifier.heightIn(min = 48.dp).semantics { contentDescription = editDescription },
                                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent)
                                ) {
                                    Text(
                                        if (horizontal) stringResource(R.string.profile_action_edit_short) else editDescription,
                                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            val changePhotoButton: @Composable (Modifier) -> Unit = { actionModifier ->
                                FilledTonalButton(
                                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                    enabled = !saving && !preparing,
                                    shape = styledShape(14.dp),
                                    modifier = actionModifier.heightIn(min = 48.dp).semantics { contentDescription = photoDescription },
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.surface2, contentColor = palette.textPrimary)
                                ) {
                                    Text(
                                        if (preparing) stringResource(R.string.profile_photo_preparing)
                                        else if (horizontal) stringResource(R.string.profile_action_change_photo_short)
                                        else photoDescription,
                                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            if (horizontal) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    editButton(Modifier.weight(1f))
                                    changePhotoButton(Modifier.weight(1f))
                                }
                            } else {
                                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    editButton(Modifier.fillMaxWidth())
                                    changePhotoButton(Modifier.fillMaxWidth())
                                }
                            }
                        }
                    } else if (info.kind == ProfileKind.Person || info.kind == ProfileKind.Secret) {
                        Spacer(Modifier.height(14.dp))
                        FilledTonalButton(
                            onClick = { onGifts(info.id, false, info.title) },
                            shape = styledShape(14.dp),
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.accentSoft, contentColor = palette.accentInk)
                        ) {
                            Icon(Icons.Rounded.CardGiftcard, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.profile_monetization_gifts_send), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    val canStartSecretChat = startSecretChat != null && info.kind == ProfileKind.Person && !info.isSelf &&
                        SecretChatEligibility.canStart(
                            userId = info.id,
                            isSelf = info.isSelf,
                            isBot = info.kind == ProfileKind.Bot || info.status is ProfilePresence.Bot,
                            isDeleted = info.status is ProfilePresence.DeletedAccount
                        )
                    if (canStartSecretChat) {
                        TextButton(
                            onClick = { secretChatError = null; secretChatConfirm = true },
                            enabled = !secretChatBusy
                        ) {
                            Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(18.dp), tint = palette.accent)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.secret_chat_start_from_profile), color = palette.accentInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (isOwnProfile) ProfileMonetizationLinks(
                    onPremium = onPremium,
                    onStars = onStars,
                    onGifts = { onGifts(info.id, true, info.title) }
                )
                ProfileRowsCard(profileRows(context, info, withSharedCounts = sharedMessages == null))
                if (onManage != null && info.groupAdmin?.canManageAnything == true) {
                    GroupManagementEntry(info.kind, onManage)
                }
                if (info.personalChatId > 0L) {
                    TextButton(onClick = { onOpenLinkedChannel(info.personalChatId) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text(buildString {
                            append(stringResource(R.string.feature_linked_channel))
                            if (info.personalChatTitle.isNotBlank()) {
                                append(": ")
                                append(info.personalChatTitle)
                            }
                        }, color = palette.accentInk)
                    }
                }
                if (info.administrators.isNotEmpty()) AdministratorsSection(info.administrators, onOpenUser)
                if (info.canListMembers && info.memberPreview.isNotEmpty()) MembersSection(info, observeFile, downloadFile, onMembers, onOpenUser)
                chatLibrary?.let { link -> ChatLibraryMusicRow(info.id, link.accountId) { link.onOpen(info.id) } }
                if (sharedMessages != null) SharedMediaSection(info.id, info.sharedCounts, sharedMessages, observeFile, downloadFile, confirmExport, messageRights, forwardMessage, chats, onContentOpened, onOpenMessage, autoDownloadFile = autoDownloadFile, protectedContent = protectedContent, onOpenMessageEntity = onOpenMessageEntity, loadForwardTopics = loadForwardTopics, forwardToTopic = forwardToTopic, recipientRepository = recipientRepository)
                if (isOwnProfile && hiddenTabs.isNotEmpty()) HiddenTabsSection(hiddenTabs, onOpenTab)
                // A settings-style row in its own card, not a bare accent link that reads as a section header.
                if (isOwnProfile) Column(Modifier.profileCardSurface().padding(vertical = 4.dp)) {
                    ProfileLinkRow(Icons.Rounded.Notifications, stringResource(R.string.profile_notifications), onNotifications)
                }
            }
            // The first read has no profile yet; a spinner says so, where a blank page would look finished.
            if (state.info == null && state.isLoading) Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.semantics { contentDescription = loadingText }, color = palette.accent)
            }
            if (state.info == null && !state.isLoading && state.error == null) Text(stringResource(R.string.profile_info_not_loaded), color = palette.textSecondary, modifier = Modifier.padding(24.dp))
            photoError?.let { Text(it, color = palette.danger, modifier = Modifier.padding(20.dp).errorAnnouncement()) }
        }
        }
        state.info?.let { info ->
            if (collapsingOverlayActive(collapse.value) && collapseGeometry.overlayReady &&
                (profileTransition == null || !profileTransition.flightActive())
            ) {
                ProfileCollapsingOverlay(
                    collapse = collapse.value,
                    geometry = collapseGeometry,
                    title = info.profileTitleText(),
                    status = compactStatus.orEmpty(),
                    avatar = {
                        TelegramAvatar(info.initials, palette.accent, 112.dp, info.photo, observeFile, downloadFile,
                            Modifier.clearAndSetSemantics {})
                    }
                )
            }
        }
        }
    }
    val photo = state.info?.photo
    // Profile photo ids are distinct from message ids. History loads only after the avatar is tapped.
    if (showPhoto && photo != null) key(state.info?.id, photo.photoId, photo.bigFileId) {
        ProfilePhotoGallery(
            profileId = state.info?.id ?: 0L, avatar = photo, own = isOwnProfile,
            observeFile = observeFile, downloadFile = downloadFile, loadPhotos = loadProfilePhotos,
            deletePhoto = deleteMyProfilePhoto, setAsMain = setPreviousProfilePhoto,
            onChanged = onRefresh, onClose = { showPhoto = false },
            autoDownloadFile = autoDownloadFile
        )
    }
    pendingPhoto?.let { selected ->
        fun discard() { if (!saving) { pendingPhoto = null; scope.launch { importer.discard(selected) } } }
        BackHandler(onBack = ::discard)
        Column(Modifier.fillMaxSize().glassScreenBackground(design, palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
            ProfileToolbar(stringResource(R.string.profile_photo_new_title), ::discard)
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                LocalMediaImage(selected.path, Modifier.size(240.dp).clip(CircleShape))
                Text(stringResource(R.string.profile_photo_preview_hint), color = palette.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
                TextButton(enabled = !saving, onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.profile_photo_pick_other), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                if (saving) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp), color = palette.accent)
                photoError?.let { Text(it, color = palette.danger, modifier = Modifier.padding(top = 12.dp).errorAnnouncement()) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = ::discard, enabled = !saving, shape = styledShape(16.dp), modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = palette.accentSoft, contentColor = palette.accentInk)) { Text(stringResource(R.string.common_cancel), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                Button(enabled = !saving, shape = styledShape(16.dp), modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent), onClick = { scope.launch {
                        saving = true; photoError = null
                        try {
                            val result = setPhoto(selected)
                            if (result.isSuccess) pendingPhoto = null
                            else photoError = result.exceptionOrNull().userMessage(context, R.string.profile_photo_update_failed)
                        } finally { saving = false }
                    } }) { Text(if (saving) stringResource(R.string.common_saving) else stringResource(R.string.profile_photo_set), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
    state.info?.takeIf { secretChatConfirm }?.let { info ->
        // The error and the progress bar sit under the message, inside the dialog's own scroll area.
        val statusContent: @Composable () -> Unit = {
            secretChatError?.let {
                Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp).errorAnnouncement())
            }
            if (secretChatBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = palette.accent)
        }
        BeyGramDialog(
            onDismissRequest = { if (!secretChatBusy) { secretChatConfirm = false; secretChatError = null } },
            title = stringResource(R.string.secret_chat_start_title),
            message = stringResource(R.string.secret_chat_start_message),
            content = if (secretChatError != null || secretChatBusy) statusContent else null,
            confirmText = stringResource(R.string.secret_chat_start_confirm),
            confirmEnabled = !secretChatBusy,
            onConfirm = {
                val create = startSecretChat ?: return@BeyGramDialog
                if (secretChatBusy) return@BeyGramDialog
                scope.launch {
                    secretChatBusy = true
                    secretChatError = null
                    try {
                        create(info.id)
                            .onSuccess { chatId ->
                                secretChatConfirm = false
                                onSecretChatOpened(chatId)
                            }
                            .onFailure {
                                secretChatError = it.userMessage(context, R.string.secret_chat_create_failed)
                            }
                    } finally {
                        secretChatBusy = false
                    }
                }
            },
            dismissText = stringResource(R.string.common_cancel),
            dismissEnabled = !secretChatBusy,
            onDismiss = { secretChatConfirm = false; secretChatError = null }
        )
    }
}

/**
 * A failed profile refresh must not hide information already in the state.
 * The error is separate from photo upload/import failures.
 */
@Composable
internal fun ProfileLoadError(message: String, loading: Boolean, onRetry: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).errorAnnouncement(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, color = palette.danger, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry, enabled = !loading, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.common_retry))
        }
    }
}

@Composable internal fun ProfileSubtitle(info: ProfileInfo, lastSeenStyle: LastSeenStyle) {
    val palette = beyGramPalette()
    // Тик времени перерисовывает только подпись, а не весь экран профиля.
    val now by rememberPresenceClock(info.status is ProfilePresence.LastSeen)
    Text(info.subtitle(lastSeenStyle, now), color = palette.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
}

/** The presence line for the compact toolbar identity (#999); the text the big subtitle shows. */
@Composable
internal fun ProfileInfo.profileSubtitleLine(lastSeenStyle: LastSeenStyle): String =
    subtitle(lastSeenStyle, System.currentTimeMillis())

/** The name a profile shows: the server's title, or the placeholder that tells a deleted account from a nameless one. */
@Composable
private fun ProfileInfo.profileTitleText(): String =
    title.ifBlank { stringResource(if (status is ProfilePresence.DeletedAccount) R.string.deleted_account_name else R.string.user_name_unknown) }

@Composable internal fun ProfileInfo.subtitle(lastSeenStyle: LastSeenStyle, nowMillis: Long): String = when (kind) {
    ProfileKind.Group -> memberCount?.let { count ->
        onlineMemberCount?.let { stringResource(R.string.profile_group_members_online_count, count, it) } ?: stringResource(R.string.profile_group_members_count, count)
    } ?: stringResource(R.string.profile_type_group)
    ProfileKind.Channel -> memberCount?.let { stringResource(R.string.profile_channel_subscribers_count, it) } ?: stringResource(R.string.profile_type_channel)
    ProfileKind.Secret -> stringResource(R.string.profile_secret_chat_status, status?.let { presenceText(it, lastSeenStyle, nowMillis).resolve() }.orEmpty())
    ProfileKind.Bot -> stringResource(R.string.profile_type_bot)
    ProfileKind.Person -> if (isSelf) stringResource(R.string.profile_own_account_telegram) else status?.let { presenceText(it, lastSeenStyle, nowMillis).resolve() }.orEmpty()
}

/** Подготавливает новое фото и удаляет прежнее. Если подготовка падает, прежнее фото не трогаем. */
internal suspend fun <T : Any> replacePendingPhoto(previous: T?, prepare: suspend () -> T, discard: suspend (T) -> Unit): T {
    val next = prepare()
    previous?.let { discard(it) }
    return next
}

/** День рождения для строки профиля; год показываем, только если аккаунт его указал. Null, если дата не сходится с календарём. */
internal fun birthdayText(birthday: ProfileBirthday, locale: Locale): String? = runCatching {
    if (birthday.year == null) MonthDay.of(birthday.month, birthday.day).format(DateTimeFormatter.ofPattern("d MMMM", locale))
    else LocalDate.of(birthday.year, birthday.month, birthday.day).format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
}.getOrNull()

/** Строки карточки профиля. Счётчики медиа входят сюда, только если под профилем нет вкладок с ними. */
internal fun profileRows(context: Context, info: ProfileInfo, withSharedCounts: Boolean): List<Pair<String, String>> = buildList {
    if (info.phone.isNotBlank()) add(context.getString(R.string.profile_row_phone) to "+${info.phone.removePrefix("+")}")
    (info.usernames + info.username.takeIf { it.isNotBlank() }).filterNotNull().distinct().forEach { add(context.getString(R.string.profile_username) to "@$it") }
    if (info.bio.isNotBlank()) add((if (info.kind == ProfileKind.Group || info.kind == ProfileKind.Channel) context.getString(R.string.profile_row_description) else context.getString(R.string.profile_about_me)) to info.bio)
    info.birthday?.let { birthdayText(it, Locale.getDefault()) }?.let { add(context.getString(R.string.profile_row_birthday) to it) }
    if (info.address.isNotBlank()) add(context.getString(R.string.profile_row_address) to info.address)
    info.commonGroupCount?.let { add(context.getString(R.string.profile_row_common_groups) to it.toString()) }
    if (info.inviteLink.isNotBlank()) add(context.getString(R.string.profile_row_invite_link) to info.inviteLink)
    if (info.memberCount != null) add((if (info.kind == ProfileKind.Channel) context.getString(R.string.profile_row_subscribers) else context.getString(R.string.profile_row_members)) to info.memberCount.toString())
    info.administratorCount?.let { add(context.getString(R.string.profile_row_administrators) to it.toString()) }
    add(context.getString(R.string.profile_row_type) to when (info.kind) {
        ProfileKind.Person -> if (info.isSelf) context.getString(R.string.profile_type_own_account) else context.getString(R.string.profile_type_private_chat)
        ProfileKind.Bot -> context.getString(R.string.profile_type_bot)
        ProfileKind.Group -> context.getString(R.string.profile_type_group)
        ProfileKind.Channel -> context.getString(R.string.profile_type_channel)
        ProfileKind.Secret -> context.getString(R.string.profile_type_secret_chat)
    })
    // Со вкладками счётчики стоят в заголовках; без них (профиль участника) остаются строками карточки.
    if (withSharedCounts) info.sharedCounts?.let { counts ->
        counts.media?.let { add(context.getString(R.string.profile_shared_media) to it.toString()) }
        counts.files?.let { add(context.getString(R.string.profile_shared_files) to it.toString()) }
        counts.links?.let { add(context.getString(R.string.profile_shared_links) to it.toString()) }
        counts.voice?.let { add(context.getString(R.string.profile_shared_voice) to it.toString()) }
    }
}

/** Карточка строк профиля: подпись над значением, разделители между строками. */
@Composable
internal fun ProfileRowsCard(rows: List<Pair<String, String>>) {
    val palette = beyGramPalette()
    Column(Modifier.profileCardSurface().padding(vertical = 4.dp)) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 20.dp))
            InfoRow(label, value)
        }
    }
}

/** Значки подтверждения и Premium под названием. */
@Composable
internal fun ProfileBadges(info: ProfileInfo) {
    val palette = beyGramPalette()
    if (info.isVerified || info.isPremium) Text(
        listOfNotNull(if (info.isVerified) stringResource(R.string.profile_badge_verified) else null, if (info.isPremium) stringResource(R.string.profile_badge_premium) else null).joinToString(" · "),
        color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)
    )
}

/** One header style for every profile section: small semibold capitals in the secondary text colour. */
@Composable
internal fun ProfileSectionHeader(title: String) {
    Text(
        title.uppercase(),
        color = beyGramPalette().textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp).semantics { heading() }
    )
}

/** Points of entry to the read-only monetization screens; they never buy anything by themselves. */
@Composable
private fun ProfileMonetizationLinks(onPremium: () -> Unit, onStars: () -> Unit, onGifts: () -> Unit) {
    val palette = beyGramPalette()
    ProfileSectionHeader(stringResource(R.string.profile_monetization_header))
    Column(
        Modifier.profileCardSurface()
            .padding(vertical = 4.dp)
    ) {
        ProfileLinkRow(Icons.Rounded.WorkspacePremium, stringResource(R.string.profile_monetization_premium), onPremium)
        HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = ProfileLinkDividerInset))
        ProfileLinkRow(Icons.Rounded.Star, stringResource(R.string.profile_monetization_stars), onStars)
        HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = ProfileLinkDividerInset))
        ProfileLinkRow(Icons.Rounded.CardGiftcard, stringResource(R.string.profile_monetization_gifts_own), onGifts)
    }
}

/** Where a divider of a profile link card starts: under the label, past the icon. */
private val ProfileLinkDividerInset = 52.dp

/**
 * A tappable row of a profile card, drawn like a settings row: the icon takes [settingsIconTint], so the profile and
 * the settings lists use one rule for their icons, and the label takes the primary text colour.
 */
@Composable
private fun ProfileLinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = settingsIconTint(), modifier = Modifier.size(22.dp))
        Text(label, color = palette.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    val palette = beyGramPalette()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).semantics(mergeDescendants = true) {}) {
        Text(label, color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        SelectionContainer { Text(value, color = palette.textPrimary, fontSize = 16.sp, lineHeight = 22.sp) }
    }
}

@Composable
internal fun AdministratorsSection(administrators: List<ProfileAdmin>, onOpenUser: (Long) -> Unit) {
    val palette = beyGramPalette()
    ProfileSectionHeader(stringResource(R.string.profile_admins_header))
    Column(Modifier.profileCardSurface().padding(vertical = 4.dp)) {
        administrators.forEachIndexed { index, admin ->
            if (index > 0) HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 20.dp))
            Box(Modifier.fillMaxWidth().clickable { onOpenUser(admin.userId) }) {
                InfoRow(adminRole(admin), admin.name.ifBlank { stringResource(R.string.user_name_unknown) })
            }
        }
    }
}

@Composable
private fun MembersSection(info: ProfileInfo, observeFile: (Int) -> StateFlow<MediaFileState>, downloadFile: (Int) -> Unit, onMembers: () -> Unit, onOpenUser: (Long) -> Unit) {
    val palette = beyGramPalette()
    ProfileSectionHeader(stringResource(R.string.profile_members_header))
    Column(Modifier.profileCardSurface().padding(vertical = 4.dp)) {
        info.memberPreview.forEachIndexed { index, member ->
            if (index > 0) HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 82.dp))
            MemberRow(member, observeFile, downloadFile) { onOpenUser(member.userId) }
        }
        TextButton(onClick = onMembers, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_members_all, info.memberCount ?: info.memberPreview.size), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun adminRole(admin: ProfileAdmin): String = when {
    admin.customTitle.isNotBlank() -> admin.customTitle
    admin.isOwner -> stringResource(R.string.profile_admin_owner)
    else -> stringResource(R.string.profile_admin_role)
}

/** Разделы, скрытые из нижнего меню, открываются отсюда так же, как с панели. */
@Composable
private fun HiddenTabsSection(hiddenTabs: List<MainTab>, onOpenTab: (MainTab) -> Unit) {
    val palette = beyGramPalette()
    ProfileSectionHeader(stringResource(R.string.profile_hidden_tabs_header))
    Column(Modifier.profileCardSurface().padding(vertical = 4.dp)) {
        hiddenTabs.forEachIndexed { index, tab ->
            if (index > 0) HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onOpenTab(tab) }.padding(horizontal = 20.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(tab.icon, null, tint = palette.accent, modifier = Modifier.size(22.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(tab.title, color = palette.textPrimary, fontSize = 16.sp)
                    Text(stringResource(R.string.profile_hidden_tab_hint), color = palette.textSecondary, fontSize = 12.sp)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Stable
internal class ProfileCollapseGeometry {
    var expandedAvatarAtZero by mutableStateOf<Rect?>(null)
    var expandedTitleAtZero by mutableStateOf<Rect?>(null)
    var expandedStatusAtZero by mutableStateOf<Rect?>(null)
    var compactAvatar by mutableStateOf<Rect?>(null)
    var compactTitle by mutableStateOf<Rect?>(null)
    var compactStatus by mutableStateOf<Rect?>(null)

    val overlayReady: Boolean
        get() = expandedAvatarAtZero != null && compactAvatar != null
}

/**
 * Single scroll-driven collapsing identity layer (#999): moves the avatar, name and status
 * smoothly between the centered expanded header and the compact two-line toolbar without
 * clipping by the scroll container or duplicating elements at intermediate scroll offsets.
 */
@Composable
internal fun ProfileCollapsingOverlay(
    collapse: Float,
    geometry: ProfileCollapseGeometry,
    title: String,
    status: String,
    avatar: @Composable () -> Unit
) {
    val palette = beyGramPalette()
    val density = LocalDensity.current
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val expandedAvatar = geometry.expandedAvatarAtZero
    val compactAvatar = geometry.compactAvatar
    val expandedTitle = geometry.expandedTitleAtZero
    val compactTitle = geometry.compactTitle
    val expandedStatus = geometry.expandedStatusAtZero
    val compactStatus = geometry.compactStatus
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInWindow() }
            .clearAndSetSemantics {}
    ) {
        if (expandedAvatar != null && compactAvatar != null) {
            val avatarLayoutPx = with(density) { 112.dp.toPx() }
            Box(
                Modifier
                    .graphicsLayer {
                        val current = lerpRect(expandedAvatar, compactAvatar, collapse)
                        translationX = current.left - overlayOrigin.x
                        translationY = current.top - overlayOrigin.y
                        scaleX = current.width / avatarLayoutPx
                        scaleY = current.height / avatarLayoutPx
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .size(112.dp)
            ) {
                avatar()
            }
        }
        if (expandedTitle != null && compactTitle != null && title.isNotBlank()) {
            val crossfade = collapsingTextCrossfade(collapse)
            val expandedWidthDp = with(density) { expandedTitle.width.toDp() }
            val compactWidthDp = with(density) { compactTitle.width.toDp() }
            if (crossfade < 1f) {
                Text(
                    text = title,
                    color = palette.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .width(expandedWidthDp)
                        .graphicsLayer {
                            val scale = 1f + (17f / 26f - 1f) * collapse
                            translationX = expandedTitle.left + (compactTitle.left - expandedTitle.left) * collapse - overlayOrigin.x
                            translationY = expandedTitle.top + (compactTitle.top - expandedTitle.top) * collapse - overlayOrigin.y
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - crossfade
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                )
            }
            if (crossfade > 0f) {
                Text(
                    text = title,
                    color = palette.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = compactWidthDp)
                        .graphicsLayer {
                            val scale = 26f / 17f + (1f - 26f / 17f) * collapse
                            val startLeft = expandedTitle.center.x - (compactTitle.width * (26f / 17f)) / 2f
                            translationX = startLeft + (compactTitle.left - startLeft) * collapse - overlayOrigin.x
                            translationY = expandedTitle.top + (compactTitle.top - expandedTitle.top) * collapse - overlayOrigin.y
                            scaleX = scale
                            scaleY = scale
                            alpha = crossfade
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                )
            }
        }
        if (expandedStatus != null && compactStatus != null && status.isNotBlank()) {
            val compactWidthDp = with(density) { compactStatus.width.toDp() }
            Text(
                text = status,
                color = palette.textSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = compactWidthDp * (14f / 13f))
                    .graphicsLayer {
                        val scale = 1f + (13f / 14f - 1f) * collapse
                        translationX = expandedStatus.left + (compactStatus.left - expandedStatus.left) * collapse - overlayOrigin.x
                        translationY = expandedStatus.top + (compactStatus.top - expandedStatus.top) * collapse - overlayOrigin.y
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            )
        }
    }
}

@Composable
internal fun ProfileToolbar(
    title: String, onBack: () -> Unit,
    /** The live collapse of the identity header; null keeps the static-only toolbar. */
    collapse: State<Float>? = null,
    /** The identity that replaces the static title once the header has collapsed. */
    compactTitle: String? = null,
    compactStatus: String? = null,
    compactAvatar: (@Composable () -> Unit)? = null,
    /** Shared geometry holder for the scroll-driven collapsing overlay (#999). */
    collapseGeometry: ProfileCollapseGeometry? = null,
    /** The shared-element transition back to the chat (#999); reports the compact identity bounds. */
    transition: com.beykus.beygram.ui.motion.ChatProfileHeaderTarget? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val density = LocalDensity.current
    val transitionFingerprint = transition?.let {
        val container = LocalWindowInfo.current.containerSize
        val densityScale = density.density
        remember(container, densityScale) {
            com.beykus.beygram.ui.motion.ChatProfileWindowFingerprint(container.width, container.height, densityScale)
        }
    }
    val container = Modifier.fillMaxWidth().then(
        when (design.components.topBar) {
            TopBarVariant.Elevated -> Modifier.topAppBarSurface()
            TopBarVariant.Glass -> Modifier
                .background(design.surfaces.panelFill(palette.surface, PanelFloor.Card))
            TopBarVariant.Signature -> Modifier.signatureChrome(palette.screen, palette.divider, HairlineEdge.Bottom)
            TopBarVariant.Flat -> Modifier
        }
    )
    val collapseValue = collapse
    val staticShown by remember(collapseValue) {
        derivedStateOf { (collapseValue?.value ?: 0f) < 0.30f }
    }
    val compactSemanticsShown by remember(collapseValue, collapseGeometry) {
        derivedStateOf {
            collapseValue != null &&
                compactToolbarIdentityAlpha(collapseValue.value, collapseGeometry?.overlayReady == true) > 0f
        }
    }
    Row(container.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.common_back), tint = palette.textPrimary) }
        if (collapseValue != null && compactTitle != null && compactAvatar != null) {
            // One title slot: the static label ("Chat Info") fades out before the collapsing
            // identity enters, and the compact two-line ChatTopBar-style identity is always
            // measured in place so bounds are ready from frame 1 without layout jumps.
            Box(Modifier.weight(1f).heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                if (staticShown) Text(
                    title,
                    color = palette.textPrimary,
                    fontSize = if (design.style == DesignStyle.Default) 18.sp else 19.sp,
                    fontWeight = when (design.style) {
                        DesignStyle.Material2 -> FontWeight.Medium
                        DesignStyle.Material3 -> FontWeight.Medium
                        else -> FontWeight.SemiBold
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .graphicsLayer {
                            val c = collapseValue.value
                            alpha = staticToolbarTitleAlpha(c)
                            translationY = -with(density) { 6.dp.toPx() } * windowProgress(c, 0f, 0.30f)
                        }
                        .semantics { heading() }
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        // While the compact identity is invisible (header still expanded), keep it
                        // out of the accessibility tree so TalkBack does not read a hidden heading.
                        .then(if (compactSemanticsShown) Modifier else Modifier.clearAndSetSemantics {})
                        .graphicsLayer {
                            alpha = compactToolbarIdentityAlpha(collapseValue.value, collapseGeometry?.overlayReady == true) *
                                (transition?.toolbarVisibility() ?: 1f)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.onGloballyPositioned { coordinates ->
                            val bounds = coordinates.boundsInWindow()
                            collapseGeometry?.compactAvatar = bounds
                            if (transition != null && transitionFingerprint != null) {
                                transition.reportToolbarAvatar(bounds, transitionFingerprint)
                            }
                        }
                    ) {
                        compactAvatar()
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            compactTitle,
                            color = palette.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .onGloballyPositioned { coordinates ->
                                    val bounds = coordinates.boundsInWindow()
                                    collapseGeometry?.compactTitle = bounds
                                    if (transition != null && transitionFingerprint != null) {
                                        transition.reportToolbarTitle(bounds, transitionFingerprint)
                                    }
                                }
                                .semantics { heading() }
                        )
                        if (!compactStatus.isNullOrBlank()) {
                            Text(
                                compactStatus,
                                color = palette.textSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    val bounds = coordinates.boundsInWindow()
                                    collapseGeometry?.compactStatus = bounds
                                    if (transition != null && transitionFingerprint != null) {
                                        transition.reportToolbarStatus(bounds, transitionFingerprint)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            actions()
        } else {
            Text(
                title,
                color = palette.textPrimary,
                fontSize = if (design.style == DesignStyle.Default) 18.sp else 19.sp,
                fontWeight = when (design.style) {
                    DesignStyle.Material2 -> FontWeight.Medium
                    DesignStyle.Material3 -> FontWeight.Medium
                    else -> FontWeight.SemiBold
                },
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            actions()
        }
    }
}

@Composable
fun EditProfileScreen(info: ProfileInfo, bioLimit: Int, onBack: () -> Unit, save: suspend (ProfileEdit) -> Result<Unit>) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var first by rememberSaveable(info.id) { mutableStateOf(info.firstName) }
    var last by rememberSaveable(info.id) { mutableStateOf(info.lastName) }
    var username by rememberSaveable(info.id) { mutableStateOf(info.username) }
    var bio by rememberSaveable(info.id) { mutableStateOf(info.bio) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val changed = first.trim() != info.firstName || last.trim() != info.lastName || username.trim().removePrefix("@") != info.username || bio != info.bio
    val bioCount = bio.codePointCount(0, bio.length)
    fun back() { if (!saving) { if (changed && !discard) discard = true else onBack() } }
    BackHandler(onBack = ::back)
    Column(Modifier.fillMaxSize().glassScreenBackground(design, palette.screen).windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        ProfileToolbar(stringResource(R.string.profile_title_own), ::back) {
            TextButton(enabled = !saving && changed && first.isNotBlank() && bioCount <= bioLimit, onClick = { scope.launch {
                discard = false
                saving = true; error = null
                try {
                    val result = save(ProfileEdit(first, last, username, bio))
                    if (result.isSuccess) onBack()
                    else error = result.exceptionOrNull().userMessage(context, R.string.profile_save_failed) + "\n" + context.getString(R.string.profile_save_partial_hint)
                } finally { saving = false }
            } }) { Text(if (saving) stringResource(R.string.common_saving) else stringResource(R.string.common_save)) }
        }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
        if (discard) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).glassCard(design, palette.warningSurface, palette.textSecondary, styledShape(16.dp)).errorAnnouncement().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.profile_unsaved_changes), color = palette.warningText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text(stringResource(R.string.profile_discard_changes), color = palette.warningText, fontWeight = FontWeight.SemiBold) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(first, { first = it.take(64) }, label = { Text(stringResource(R.string.profile_first_name)) }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), shape = styledShape(14.dp), colors = beyGramTextFieldColors())
            OutlinedTextField(last, { last = it.take(64) }, label = { Text(stringResource(R.string.profile_last_name)) }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), shape = styledShape(14.dp), colors = beyGramTextFieldColors())
            OutlinedTextField(username, { username = it.removePrefix("@").take(32) }, label = { Text(stringResource(R.string.profile_username)) }, prefix = { Text("@") }, supportingText = { Text(stringResource(R.string.profile_username_hint)) }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), shape = styledShape(14.dp), colors = beyGramTextFieldColors())
            OutlinedTextField(bio, { bio = it }, label = { Text(stringResource(R.string.profile_about_me)) }, supportingText = { Text(stringResource(R.string.profile_bio_counter, bioCount, bioLimit)) }, isError = bioCount > bioLimit, minLines = 3, maxLines = 6, enabled = !saving, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), shape = styledShape(14.dp), colors = beyGramTextFieldColors())
            Text(stringResource(R.string.profile_save_hint), color = palette.textSecondary, fontSize = 13.sp)
            error?.let { Text(it, color = palette.danger, modifier = Modifier.errorAnnouncement()) }
        }
    }
}
