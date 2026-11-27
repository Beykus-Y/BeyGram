package com.beykus.beygram.ui.screens.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.ActionBubble
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.GlassEdge
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassSheet
import com.beykus.beygram.ui.design.styledTopShape
import com.beykus.beygram.ui.design.usesGlassPanels
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.motionEntrance

private val EntranceRise = 16.dp

/** Every attachment type must stay reachable without a horizontal scroll. */
internal enum class AttachmentAction {
    Camera,
    PhotoVideo,
    File,
    Music,
    Audio,
    Location,
    Contact,
    Poll,
    Dice;

    internal val isChatObject: Boolean
        get() = this == Location || this == Contact || this == Poll || this == Dice

    /** A chosen track replaces the staged files like a poll does, so it is refused while files are staged. */
    internal val replacesTray: Boolean
        get() = isChatObject || this == Music

    @StringRes
    internal fun labelRes(): Int = when (this) {
        Camera -> R.string.composer_type_camera
        PhotoVideo -> R.string.composer_type_gallery
        File -> R.string.composer_type_file
        Audio -> R.string.composer_type_audio
        Music -> R.string.composer_type_music
        Location -> R.string.composer_type_location
        Contact -> R.string.composer_type_contact
        Poll -> R.string.composer_type_poll
        Dice -> R.string.composer_type_dice
    }

    internal fun icon(): ImageVector = when (this) {
        Camera -> Icons.Rounded.PhotoCamera
        PhotoVideo -> Icons.Rounded.PhotoLibrary
        File -> Icons.Rounded.InsertDriveFile
        Audio -> Icons.Rounded.AudioFile
        Music -> Icons.Rounded.LibraryMusic
        Location -> Icons.Rounded.LocationOn
        Contact -> Icons.Rounded.Person
        Poll -> Icons.Rounded.Poll
        Dice -> Icons.Rounded.Casino
    }
}

/** Polls and music disappear only where Telegram rejects them; every other type is always present. */
internal fun attachmentActions(allowsPolls: Boolean, allowsMusic: Boolean = true): List<AttachmentAction> =
    AttachmentAction.entries.filter { (allowsPolls || it != AttachmentAction.Poll) && (allowsMusic || it != AttachmentAction.Music) }

internal fun attachmentMenuColumns(widthDp: Float, fontScale: Float, actionCount: Int): Int {
    val cellWidth = 64f * fontScale.coerceAtLeast(1f)
    val capacity = ((widthDp - 16f + 4f) / (cellWidth + 4f)).toInt().coerceIn(1, actionCount)
    val rows = (actionCount + capacity - 1) / capacity
    return (actionCount + rows - 1) / rows
}

/** The panel closes with the paperclip or the system Back action. */
@Composable
internal fun AttachmentMenu(
    allowsPolls: Boolean,
    enabled: Boolean,
    allowsMusic: Boolean = true,
    chatObjectsEnabled: Boolean = true,
    onAction: (AttachmentAction) -> Unit
) {
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    val actions = remember(allowsPolls, allowsMusic) { attachmentActions(allowsPolls, allowsMusic) }
    val menuBody: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            if (!chatObjectsEnabled) {
                Text(
                    stringResource(R.string.composer_menu_chat_unavailable),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = attachmentMenuColumns(maxWidth.value, LocalDensity.current.fontScale, actions.size)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(actions.size, key = { actions[it].name }) { index ->
                        val action = actions[index]
                        ActionBubble(
                            icon = action.icon(),
                            label = stringResource(action.labelRes()),
                            onClick = { onAction(action) },
                            enabled = enabled && (chatObjectsEnabled || !action.replacesTray),
                            entranceIndex = index,
                            compact = true
                        )
                    }
                }
            }
        }
    }
    val onSharedGlass = LocalComposerOnGlassPlane.current || LocalChatChromeOnGlass.current
    val baseModifier = Modifier.fillMaxWidth().motionEntrance(MotionToken.SheetEnter, EntranceRise)
    if (design.usesGlassPanels && !onSharedGlass) {
        GlassPanel(
            design = design,
            tint = palette.surface,
            rim = palette.textSecondary,
            backdropBase = palette.screen,
            shape = styledTopShape(20.dp),
            edge = GlassEdge.TopHairline,
            lensOnFreeEdge = true,
            modifier = baseModifier
        ) {
            menuBody()
        }
    } else {
        Column(
            if (design.usesGlassPanels) baseModifier else baseModifier.glassSheet(design, palette.surface, palette.textSecondary)
        ) {
            menuBody()
        }
    }
}
