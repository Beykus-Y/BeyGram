package com.beykus.beygram.ui.screens.theme

import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.BubbleStyle
import com.beykus.beygram.data.appearance.DEFAULT_MESSAGE_TEXT_SIZE_SP
import com.beykus.beygram.data.appearance.ListDensity
import com.beykus.beygram.data.appearance.MessageAnimationConfig
import com.beykus.beygram.data.appearance.MessageLineSpacing

/** The state of the shared Apply action. A contrast problem blocks saving until the user fixes the colours. */
internal data class AppearanceApplyState(
    val hasChanges: Boolean,
    val blockedByContrast: Boolean
) {
    val canApply: Boolean get() = hasChanges && !blockedByContrast
}

internal fun appearanceApplyState(
    draft: AppearanceConfig,
    saved: AppearanceConfig,
    blockedByContrast: Boolean
): AppearanceApplyState = AppearanceApplyState(
    hasChanges = draft != saved,
    blockedByContrast = blockedByContrast
)

/** Where Back goes on a subpage. Null means the caller should leave Appearance (after the discard check). */
internal fun appearanceBackTarget(page: AppearancePage, previewReturn: AppearancePage): AppearancePage? = when (page) {
    AppearancePage.Preview -> previewReturn
    AppearancePage.Overview -> null
    else -> AppearancePage.Overview
}

/** Resets only the message presentation. Palette, brightness, wallpaper, animations and custom message colours stay untouched. */
internal fun AppearanceConfig.resetMessages(): AppearanceConfig = copy(
    bubbleStyle = BubbleStyle.Rounded,
    density = ListDensity.Comfortable,
    messageTextSizeSp = DEFAULT_MESSAGE_TEXT_SIZE_SP,
    bubbleCornerRadiusDp = null,
    messageLineSpacing = MessageLineSpacing.Normal
)

/**
 * Decides when the shared draft history needs a checkpoint. A discrete edit always gets one. A slider gesture gets
 * exactly one at its first value; [endGesture] is called when the slider reports `onValueChangeFinished`, so the next
 * gesture (even on another slider or the same one) starts a new Undo step.
 */
internal class AppearanceDraftUndoCoalescer {
    private var continuousGestureActive = false

    fun shouldCheckpoint(continuous: Boolean): Boolean {
        if (!continuous) {
            continuousGestureActive = false
            return true
        }
        if (continuousGestureActive) return false
        continuousGestureActive = true
        return true
    }

    fun endGesture() {
        continuousGestureActive = false
    }

    fun reset() {
        continuousGestureActive = false
    }
}

/** Clears the optional colours that were chosen in the expert editor. */
internal fun AppearanceConfig.resetCustomColors(): AppearanceConfig = copy(
    accentColor = null,
    outgoingBubbleColor = null,
    messageTextColor = null,
    messageTimeColor = null
)

/** Resets the animation choice without touching the rest of the appearance. */
internal fun AppearanceConfig.resetAnimations(): AppearanceConfig = copy(messageAnimations = MessageAnimationConfig())

/** Resets every appearance choice to the values BeyGram uses for a new installation. */
internal fun AppearanceConfig.resetAllAppearance(): AppearanceConfig = AppearanceConfig()
