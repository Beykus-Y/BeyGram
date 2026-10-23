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

/** Resets only the message presentation. Palette, brightness, wallpaper and animations stay untouched. */
internal fun AppearanceConfig.resetMessages(): AppearanceConfig = copy(
    bubbleStyle = BubbleStyle.Rounded,
    density = ListDensity.Comfortable,
    messageTextSizeSp = DEFAULT_MESSAGE_TEXT_SIZE_SP,
    bubbleCornerRadiusDp = null,
    messageLineSpacing = MessageLineSpacing.Normal,
    messageTextColor = null,
    messageTimeColor = null
)

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
