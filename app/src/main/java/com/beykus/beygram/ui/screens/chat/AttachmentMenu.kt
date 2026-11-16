package com.beykus.beygram.ui.screens.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.ActionBubble
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassSheet
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

/**
 * Compact grid of round action buttons, four per row. A grip at the top replaces the title row; the panel closes
 * with the paperclip or the system back action. It deliberately has no sticker/GIF or quality entries: stickers
 * live next to the input, quality belongs to each staged video/audio file.
 */
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
    val configuration = LocalConfiguration.current
    val columns = if (configuration.screenWidthDp >= 560) GridCells.Fixed(actions.size) else GridCells.Fixed(4)
    Column(
        Modifier
            .fillMaxWidth()
            .motionEntrance(MotionToken.SheetEnter, EntranceRise)
            .glassSheet(design, palette.surface, palette.textSecondary)
    ) {
        Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.divider)
            )
        }
        if (!chatObjectsEnabled) {
            Text(
                stringResource(R.string.composer_menu_chat_unavailable),
                color = palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            )
        }
        LazyVerticalGrid(
            columns = columns,
            modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
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
                    entranceIndex = index
                )
            }
        }
    }
}
