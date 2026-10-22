package com.beykus.beygram.ui.screens.theme

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.bubbleStyleFromStored
import com.beykus.beygram.data.appearance.chatWallpaperFromStored
import com.beykus.beygram.data.appearance.encode
import com.beykus.beygram.data.appearance.listDensityFromStored
import com.beykus.beygram.data.appearance.themeModeFromStored
import com.beykus.beygram.data.appearance.themePresetFromStored
import com.beykus.beygram.data.appearance.DEFAULT_MESSAGE_TEXT_SIZE_SP
import com.beykus.beygram.data.appearance.validMessageTextSize

/**
 * Keeps the unsaved theme draft through Activity recreation. Values are written the way DataStore writes them,
 * so a restored draft reads back exactly as it was. No draft is saved as an empty list, and an empty or damaged list restores as no draft.
 */
internal val AppearanceDraftSaver: Saver<AppearanceConfig?, Any> = listSaver<AppearanceConfig?, String>(
    save = { draft ->
        draft?.let {
            listOf(
                it.preset.name,
                it.mode.name,
                it.bubbleStyle.name,
                it.density.name,
                it.accentColor?.toString().orEmpty(),
                it.outgoingBubbleColor?.toString().orEmpty(),
                it.wallpaper.encode(),
                it.messageTextSizeSp.toString()
            )
        }.orEmpty()
    },
    restore = { parts ->
        if (parts.size !in 7..8) null else AppearanceConfig(
            preset = themePresetFromStored(parts[0]),
            mode = themeModeFromStored(parts[1]),
            bubbleStyle = bubbleStyleFromStored(parts[2]),
            density = listDensityFromStored(parts[3]),
            accentColor = parts[4].toIntOrNull(),
            outgoingBubbleColor = parts[5].toIntOrNull(),
            wallpaper = chatWallpaperFromStored(parts[6]),
            messageTextSizeSp = validMessageTextSize(parts.getOrNull(7)?.toIntOrNull() ?: DEFAULT_MESSAGE_TEXT_SIZE_SP)
        )
    }
)
