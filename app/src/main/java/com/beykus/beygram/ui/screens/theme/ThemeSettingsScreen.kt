package com.beykus.beygram.ui.screens.theme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.WallpaperFiles
import com.beykus.beygram.data.settings.ChatAnimationSettings
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.rememberMotionSpec
import com.beykus.beygram.ui.motion.MotionSettings
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.theme.contrastIssues
import com.beykus.beygram.ui.theme.resolveTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal enum class AppearancePage {
    Overview,
    Colors,
    Messages,
    Animations,
    Wallpapers,
    CustomColors,
    Preview
}

private val AppearancePage.depth: Int get() = when (this) {
    AppearancePage.Overview -> 0
    AppearancePage.Preview -> 2
    else -> 1
}

/**
 * The single entry point into appearance. Pages share one draft, so moving between sections, opening the full
 * preview, rotation and applying never create separate unsaved copies. Apply stays visible on every editing page.
 */
@Composable
fun ThemeSettingsScreen(
    saved: AppearanceConfig,
    draft: AppearanceConfig,
    onDraftChange: (AppearanceConfig) -> Unit,
    onSave: (AppearanceConfig) -> Unit,
    onBack: () -> Unit,
    manageWallpaperFiles: Boolean = true,
    motion: MotionSettings = MotionSettings(),
    onMotionChange: (MotionSettings) -> Unit = {},
    chatAnimations: ChatAnimationSettings = ChatAnimationSettings(),
    onAnimatedEmojiChange: (Boolean) -> Unit = {},
    onMessageEffectsChange: (Boolean) -> Unit = {}
) {
    val palette = beyGramPalette()
    val systemDark = isSystemInDarkTheme()
    var pageName by rememberSaveable { mutableStateOf(AppearancePage.Overview.name) }
    var previewReturnName by rememberSaveable { mutableStateOf(AppearancePage.Overview.name) }
    var replayKey by rememberSaveable { mutableIntStateOf(0) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmResetAll by rememberSaveable { mutableStateOf(false) }
    val page = AppearancePage.entries.firstOrNull { it.name == pageName } ?: AppearancePage.Overview
    val pushMotion = rememberMotionSpec(MotionToken.NavigationPush)
    val popMotion = rememberMotionSpec(MotionToken.NavigationPop)
    val previewReturn = AppearancePage.entries.firstOrNull { it.name == previewReturnName } ?: AppearancePage.Overview
    val draftTokens = resolveTokens(draft, systemDark)
    val issues = contrastIssues(
        draftTokens,
        draft.outgoingBubbleColor,
        draft.messageTextColor,
        draft.messageTimeColor
    )
    val applyState = appearanceApplyState(draft, saved, issues.blocking && draft != saved)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Photos that were applied during this visit stay on disk until the next visit, because DataStore may still be
    // catching up. Trial photos that were never applied can be removed as soon as Apply or the session ends.
    val retainedAppliedPhotos = remember { mutableSetOf<String>() }
    var finishing by remember { mutableStateOf(false) }
    val history = remember { DraftHistory<AppearanceConfig>() }
    val undoCoalescer = remember { AppearanceDraftUndoCoalescer() }
    val currentDraft by rememberUpdatedState(draft)
    val currentSaved by rememberUpdatedState(saved)
    val currentOnBack by rememberUpdatedState(onBack)

    // A slider gesture gets exactly one checkpoint at its first value. A discrete edit always checkpoints, even when
    // it happens milliseconds after the previous selection or toggle.
    fun recordDraftChange(next: AppearanceConfig, continuous: Boolean) {
        if (next == draft) return
        if (undoCoalescer.shouldCheckpoint(continuous)) history.checkpoint(draft)
        onDraftChange(next)
    }

    fun changeDraft(next: AppearanceConfig) = recordDraftChange(next, continuous = false)

    fun changeDraftContinuous(next: AppearanceConfig) = recordDraftChange(next, continuous = true)

    fun finishContinuousGesture() {
        undoCoalescer.endGesture()
    }

    fun undoDraft() {
        val previous = history.undo() ?: return
        undoCoalescer.endGesture()
        onDraftChange(previous)
    }

    fun photoKeepSet(includeDraft: Boolean): Set<String> {
        val keep = mutableSetOf<String>()
        (saved.wallpaper as? ChatWallpaper.Photo)?.fileName?.let(keep::add)
        if (includeDraft) (draft.wallpaper as? ChatWallpaper.Photo)?.fileName?.let(keep::add)
        keep += retainedAppliedPhotos
        return keep
    }

    fun cleanupPhotos(includeDraft: Boolean) {
        if (!manageWallpaperFiles) return
        val keep = photoKeepSet(includeDraft)
        scope.launch { runCatching { WallpaperFiles.deleteAllExcept(context, keep) } }
    }

    fun finishAppearance() {
        if (finishing) return
        finishing = true
        if (!manageWallpaperFiles) {
            currentOnBack()
            return
        }
        val keep = photoKeepSet(includeDraft = false)
        scope.launch {
            runCatching { WallpaperFiles.deleteAllExcept(context, keep) }
            currentOnBack()
        }
    }

    fun openPage(target: AppearancePage) {
        if (target == AppearancePage.Preview) {
            previewReturnName = page.name
            replayKey++
        }
        pageName = target.name
    }

    fun leaveAppearance() {
        if (currentDraft != currentSaved) confirmDiscard = true else finishAppearance()
    }

    fun navigateBack() {
        val target = appearanceBackTarget(page, previewReturn)
        if (target == null) leaveAppearance() else pageName = target.name
    }

    // Read the state inside the callback. A bare function reference can keep the values from the composition
    // that first registered it, which used to make system Back leave the screen from a subpage.
    BackHandler {
        val currentPage = AppearancePage.entries.firstOrNull { it.name == pageName } ?: AppearancePage.Overview
        val currentReturn = AppearancePage.entries.firstOrNull { it.name == previewReturnName } ?: AppearancePage.Overview
        val target = appearanceBackTarget(currentPage, currentReturn)
        if (target == null) {
            if (currentDraft != currentSaved) confirmDiscard = true else finishAppearance()
        } else {
            pageName = target.name
        }
    }

    // Clean up pictures left by an earlier visit once, but keep the restored draft's photo. Deleting on every
    // draft change would break Undo when it points back to a photo the user only tried.
    LaunchedEffect(Unit) {
        if (!manageWallpaperFiles) return@LaunchedEffect
        WallpaperFiles.deleteAllExcept(
            context,
            setOfNotNull(
                (saved.wallpaper as? ChatWallpaper.Photo)?.fileName,
                (draft.wallpaper as? ChatWallpaper.Photo)?.fileName
            )
        )
    }

    Column(
        Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        ProfileToolbar(
            title = stringResource(page.titleRes()),
            onBack = ::navigateBack
        ) {
            if (page != AppearancePage.Preview && history.canUndo) {
                IconButton(onClick = ::undoDraft) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Undo,
                        stringResource(R.string.theme_editor_undo),
                        tint = palette.textPrimary
                    )
                }
            }
            if (page == AppearancePage.Preview) {
                IconButton(onClick = { replayKey++ }) {
                    Icon(
                        Icons.Rounded.Replay,
                        stringResource(R.string.theme_animations_replay),
                        tint = palette.textPrimary
                    )
                }
            }
        }
        CompositionLocalProvider(LocalAppearanceSliderGestureFinished provides ::finishContinuousGesture) {
            AnimatedContent(
                targetState = page,
                modifier = Modifier.fillMaxWidth().weight(1f),
                transitionSpec = {
                    val forward = targetState.depth >= initialState.depth
                    val motion = if (forward) pushMotion else popMotion
                    val direction = if (forward) 1 else -1
                    // Respect Motion Off, disabled Navigation and system reduced-motion.
                    // Instant transitions do not render a transparent intermediate frame.
                    if (motion.isInstant) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(motion.animationSpec()) +
                            slideInHorizontally(motion.animationSpec()) {
                                (direction * it * motion.intensity / 12f).roundToInt()
                            }) togetherWith
                            (fadeOut(motion.animationSpec()) +
                                slideOutHorizontally(motion.animationSpec()) {
                                    (-direction * it * motion.intensity / 12f).roundToInt()
                                })
                    }
                },
                label = "appearancePage"
            ) { target ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Box(
                        if (target == AppearancePage.Preview) Modifier.fillMaxSize()
                        else Modifier.widthIn(max = 720.dp).fillMaxHeight()
                    ) {
                        when (target) {
                            AppearancePage.Overview -> AppearanceOverviewPage(
                                draft = draft,
                                systemDark = systemDark,
                                onOpenPage = ::openPage,
                                onOpenPreview = { openPage(AppearancePage.Preview) }
                            )
                            AppearancePage.Colors -> AppearanceColorsPage(
                                draft = draft,
                                systemDark = systemDark,
                                onDraftChange = ::changeDraft,
                                onOpenCustomColors = { openPage(AppearancePage.CustomColors) },
                                onOpenPreview = { openPage(AppearancePage.Preview) },
                                onResetAll = { confirmResetAll = true }
                            )
                            AppearancePage.Messages -> AppearanceMessagesPage(
                                draft = draft,
                                systemDark = systemDark,
                                onDraftChange = ::changeDraft,
                                onDraftChangeContinuous = ::changeDraftContinuous,
                                onOpenCustomColors = { openPage(AppearancePage.CustomColors) },
                                onOpenPreview = { openPage(AppearancePage.Preview) }
                            )
                            AppearancePage.Animations -> AppearanceAnimationsPage(
                                draft = draft,
                                systemDark = systemDark,
                                onDraftChange = ::changeDraft,
                                onDraftChangeContinuous = ::changeDraftContinuous,
                                onOpenPreview = { openPage(AppearancePage.Preview) },
                                motion = motion,
                                onMotionChange = onMotionChange,
                                chatAnimations = chatAnimations,
                                onAnimatedEmojiChange = onAnimatedEmojiChange,
                                onMessageEffectsChange = onMessageEffectsChange
                            )
                            AppearancePage.Wallpapers -> AppearanceWallpapersPage(
                                draft = draft,
                                systemDark = systemDark,
                                onDraftChange = ::changeDraft,
                                onDraftChangeContinuous = ::changeDraftContinuous,
                                onOpenPreview = { openPage(AppearancePage.Preview) }
                            )
                            AppearancePage.CustomColors -> AppearanceCustomColorsPage(
                                draft = draft,
                                systemDark = systemDark,
                                issues = issues,
                                onDraftChange = ::changeDraft,
                                onOpenPreview = { openPage(AppearancePage.Preview) }
                            )
                            AppearancePage.Preview -> AppearanceFullPreviewPage(
                                config = draft,
                                systemDark = systemDark,
                                replayKey = replayKey,
                                onReplay = { replayKey++ },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
        // Keep the draft/Apply workflow, but do not reserve a disabled footer when everything is saved.
        // The full preview stays free of editing controls.
        if (page != AppearancePage.Preview && applyState.hasChanges) {
            AppearanceApplyBar(
                hasChanges = applyState.hasChanges,
                canApply = applyState.canApply,
                blockedByContrast = applyState.blockedByContrast,
                onApply = {
                    (draft.wallpaper as? ChatWallpaper.Photo)?.fileName?.let(retainedAppliedPhotos::add)
                    cleanupPhotos(includeDraft = true)
                    onSave(draft)
                    history.clear()
                    undoCoalescer.reset()
                }
            )
        }
    }

    if (confirmDiscard) ThemeDiscardDialog(
        onKeepEditing = { confirmDiscard = false },
        onDiscard = { confirmDiscard = false; finishAppearance() }
    )
    if (confirmResetAll) AppearanceResetDialog(
        title = stringResource(R.string.appearance_reset_all_title),
        message = stringResource(R.string.appearance_reset_all_message),
        onConfirm = {
            confirmResetAll = false
            changeDraft(draft.resetAllAppearance())
        },
        onDismiss = { confirmResetAll = false }
    )
}

private fun AppearancePage.titleRes(): Int = when (this) {
    AppearancePage.Overview -> R.string.theme_title
    AppearancePage.Colors -> R.string.appearance_colors_title
    AppearancePage.Messages -> R.string.appearance_messages_title
    AppearancePage.Animations -> R.string.theme_animations_title
    AppearancePage.Wallpapers -> R.string.wallpaper_title
    AppearancePage.CustomColors -> R.string.appearance_custom_colors_title
    AppearancePage.Preview -> R.string.theme_preview_title
}
