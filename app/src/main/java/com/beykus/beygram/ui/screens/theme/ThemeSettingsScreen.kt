package com.beykus.beygram.ui.screens.theme

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.WallpaperFiles
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.theme.contrastIssues
import com.beykus.beygram.ui.theme.resolveTokens

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
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val systemDark = isSystemInDarkTheme()
    var pageName by rememberSaveable { mutableStateOf(AppearancePage.Overview.name) }
    var previewReturnName by rememberSaveable { mutableStateOf(AppearancePage.Overview.name) }
    var replayKey by rememberSaveable { mutableIntStateOf(0) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmResetAll by rememberSaveable { mutableStateOf(false) }
    val page = AppearancePage.entries.firstOrNull { it.name == pageName } ?: AppearancePage.Overview
    val previewReturn = AppearancePage.entries.firstOrNull { it.name == previewReturnName } ?: AppearancePage.Overview
    val draftTokens = resolveTokens(draft, systemDark)
    val issues = contrastIssues(
        draftTokens,
        draft.outgoingBubbleColor,
        draft.messageTextColor,
        draft.messageTimeColor
    )
    val applyState = appearanceApplyState(draft, saved, issues.blocking && draft != saved)
    val history = remember { DraftHistory<AppearanceConfig>() }
    var lastCheckpointAt by remember { mutableLongStateOf(0L) }
    val currentDraft by rememberUpdatedState(draft)
    val currentSaved by rememberUpdatedState(saved)
    val currentOnBack by rememberUpdatedState(onBack)

    // A slider emits many values in a row; one checkpoint per short burst keeps Undo meaningful without recording
    // every pixel. Discrete edits are separated by more than the window and keep their own step.
    fun changeDraft(next: AppearanceConfig) {
        if (next == draft) return
        val now = SystemClock.uptimeMillis()
        if (!history.canUndo || now - lastCheckpointAt > 450L) {
            history.checkpoint(draft)
        }
        lastCheckpointAt = now
        onDraftChange(next)
    }

    fun undoDraft() {
        val previous = history.undo() ?: return
        lastCheckpointAt = 0L
        onDraftChange(previous)
    }

    fun openPage(target: AppearancePage) {
        if (target == AppearancePage.Preview) {
            previewReturnName = page.name
            replayKey++
        }
        pageName = target.name
    }

    fun leaveAppearance() {
        if (currentDraft != currentSaved) confirmDiscard = true else currentOnBack()
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
            if (currentDraft != currentSaved) confirmDiscard = true else currentOnBack()
        } else {
            pageName = target.name
        }
    }

    // Clean up pictures left by an earlier visit once, but keep the restored draft's photo. Deleting on every
    // draft change would break Undo when it points back to a photo the user only tried.
    val context = LocalContext.current
    LaunchedEffect(Unit) {
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
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxWidth().weight(1f),
            transitionSpec = {
                val forward = targetState.depth >= initialState.depth
                val direction = if (forward) 1 else -1
                (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { direction * it / 12 }) togetherWith
                    (fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { -direction * it / 12 })
            },
            label = "appearancePage"
        ) { target ->
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
                    onOpenCustomColors = { openPage(AppearancePage.CustomColors) },
                    onOpenPreview = { openPage(AppearancePage.Preview) }
                )
                AppearancePage.Animations -> AppearanceAnimationsPage(
                    draft = draft,
                    systemDark = systemDark,
                    onDraftChange = ::changeDraft,
                    onOpenPreview = { openPage(AppearancePage.Preview) }
                )
                AppearancePage.Wallpapers -> AppearanceWallpapersPage(
                    draft = draft,
                    systemDark = systemDark,
                    onDraftChange = ::changeDraft,
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
        if (page != AppearancePage.Preview) {
            AppearanceApplyBar(
                hasChanges = applyState.hasChanges,
                canApply = applyState.canApply,
                blockedByContrast = applyState.blockedByContrast,
                onApply = {
                    onSave(draft)
                    history.clear()
                    lastCheckpointAt = 0L
                }
            )
        }
    }

    if (confirmDiscard) ThemeDiscardDialog(
        onKeepEditing = { confirmDiscard = false },
        onDiscard = { confirmDiscard = false; onBack() }
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
