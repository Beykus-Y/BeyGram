package com.beykus.beygram.ui.screens.theme

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.bubbleStyleFromStored
import com.beykus.beygram.data.appearance.chatWallpaperFromStored
import com.beykus.beygram.data.appearance.designStyleFromStored
import com.beykus.beygram.data.appearance.encode
import com.beykus.beygram.data.appearance.glassQualityFromStored
import com.beykus.beygram.data.appearance.listDensityFromStored
import com.beykus.beygram.data.appearance.messageAnimationConfigFromStored
import com.beykus.beygram.data.appearance.messageLineSpacingFromStored
import com.beykus.beygram.data.appearance.navigationPlacementFromStored
import com.beykus.beygram.data.appearance.themeModeFromStored
import com.beykus.beygram.data.appearance.themePresetFromStored
import com.beykus.beygram.data.appearance.DEFAULT_MESSAGE_TEXT_SIZE_SP
import com.beykus.beygram.data.appearance.validBubbleCornerRadius
import com.beykus.beygram.data.appearance.validMessageTextSize

/**
 * Keeps the unsaved theme draft through Activity recreation. Values are written the way DataStore writes them,
 * so a restored draft reads back exactly as it was. No draft is saved as an empty list, and an empty or damaged list restores as no draft.
 */
internal val AppearanceDraftSaver: Saver<AppearanceConfig?, Any> = listSaver<AppearanceConfig?, String>(
    save = { encodeAppearanceDraft(it) },
    restore = { decodeAppearanceDraft(it) }
)

/** The persisted representation of a pending appearance draft; empty means there is no draft. */
internal fun encodeAppearanceDraft(draft: AppearanceConfig?): List<String> = draft?.let {
    listOf(
        it.preset.name,
        it.mode.name,
        it.bubbleStyle.name,
        it.density.name,
        it.accentColor?.toString().orEmpty(),
        it.outgoingBubbleColor?.toString().orEmpty(),
        it.wallpaper.encode(),
        it.messageTextSizeSp.toString(),
        it.bubbleCornerRadiusDp?.toString().orEmpty(),
        it.messageLineSpacing.name,
        it.messageTextColor?.toString().orEmpty(),
        it.messageTimeColor?.toString().orEmpty(),
        it.messageAnimations.encode(),
        it.designStyle.name,
        it.glassQuality.name,
        it.navigationPlacement.name,
        APPEARANCE_DRAFT_VERSION.toString()
    )
}.orEmpty()

/** Index of the draft version that added the design style and the glass quality; earlier drafts end at the version. */
private const val APPEARANCE_DRAFT_V3_SIZE = 16

/** Size of the draft that also carries the bottom bar placement, which was appended after the glass quality. */
private const val APPEARANCE_DRAFT_V4_SIZE = 17

/**
 * Reads a draft written by [encodeAppearanceDraft]. Older drafts are shorter: the design style, the glass quality and
 * the bar placement were added at the end, so a draft saved by an earlier build still restores with the defaults for
 * the new choices. Damaged values fall back the same way DataStore does.
 */
internal fun decodeAppearanceDraft(parts: List<String>): AppearanceConfig? {
    if (parts.size !in 7..APPEARANCE_DRAFT_V4_SIZE) return null
    val hasDesignChoices = parts.size >= APPEARANCE_DRAFT_V3_SIZE
    val hasNavigationPlacement = parts.size >= APPEARANCE_DRAFT_V4_SIZE
    return AppearanceConfig(
        preset = themePresetFromStored(parts[0]),
        mode = themeModeFromStored(parts[1]),
        bubbleStyle = bubbleStyleFromStored(parts[2]),
        density = listDensityFromStored(parts[3]),
        accentColor = parts[4].toIntOrNull(),
        outgoingBubbleColor = parts[5].toIntOrNull(),
        wallpaper = chatWallpaperFromStored(parts[6]),
        messageTextSizeSp = validMessageTextSize(parts.getOrNull(7)?.toIntOrNull() ?: DEFAULT_MESSAGE_TEXT_SIZE_SP),
        bubbleCornerRadiusDp = validBubbleCornerRadius(parts.getOrNull(8)?.toIntOrNull()),
        messageLineSpacing = messageLineSpacingFromStored(parts.getOrNull(9)),
        messageTextColor = parts.getOrNull(10)?.toIntOrNull(),
        messageTimeColor = parts.getOrNull(11)?.toIntOrNull(),
        messageAnimations = messageAnimationConfigFromStored(parts.getOrNull(12)),
        designStyle = designStyleFromStored(parts.getOrNull(13)?.takeIf { hasDesignChoices }),
        glassQuality = glassQualityFromStored(parts.getOrNull(14)?.takeIf { hasDesignChoices }),
        navigationPlacement = navigationPlacementFromStored(
            parts.getOrNull(15)?.takeIf { hasNavigationPlacement }
        )
    )
}

private const val APPEARANCE_DRAFT_VERSION = 4
