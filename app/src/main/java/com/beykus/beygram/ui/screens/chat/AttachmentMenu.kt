package com.beykus.beygram.ui.screens.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.motionEntrance

private val EntranceRise = 16.dp

/** Every attachment type must stay reachable without a horizontal scroll. */
internal enum class AttachmentAction {
    Camera,
    PhotoVideo,
    File,
    Audio,
    Location,
    Contact,
    Poll,
    Dice;

    internal val isChatObject: Boolean
        get() = this == Location || this == Contact || this == Poll || this == Dice

    @StringRes
    internal fun labelRes(): Int = when (this) {
        Camera -> R.string.composer_type_camera
        PhotoVideo -> R.string.composer_type_photo_video
        File -> R.string.composer_type_file
        Audio -> R.string.composer_type_audio
        Location -> R.string.composer_type_location
        Contact -> R.string.composer_type_contact
        Poll -> R.string.composer_type_poll
        Dice -> R.string.composer_type_dice
    }
}

/** Polls disappear only where Telegram rejects them; every other type is always present. */
internal fun attachmentActions(allowsPolls: Boolean): List<AttachmentAction> =
    AttachmentAction.entries.filter { allowsPolls || it != AttachmentAction.Poll }

/**
 * Compact, vertically scrollable action grid. It deliberately has no sticker/GIF or quality
 * entries: stickers live next to the input, quality belongs to each staged video/audio file.
 */
@Composable
internal fun AttachmentMenu(
    allowsPolls: Boolean,
    enabled: Boolean,
    chatObjectsEnabled: Boolean = true,
    onAction: (AttachmentAction) -> Unit,
    onClose: () -> Unit
) {
    val palette = beyGramPalette()
    val actions = remember(allowsPolls) { attachmentActions(allowsPolls) }
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.5f).dp.coerceIn(160.dp, 380.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .motionEntrance(MotionToken.SheetEnter, EntranceRise)
            .background(palette.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.composer_menu_title),
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.composer_menu_close), tint = palette.textSecondary)
            }
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
            columns = GridCells.Adaptive(84.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "media-section") {
                SectionTitle(stringResource(R.string.composer_menu_section_media))
            }
            items(actions.filterNot { it.isChatObject }, key = { it.name }) { action ->
                AttachmentActionTile(action, enabled, onAction)
            }
            if (actions.any { it.isChatObject }) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "chat-section") {
                    SectionTitle(stringResource(R.string.composer_menu_section_chat))
                }
                items(actions.filter { it.isChatObject }, key = { it.name }) { action ->
                    AttachmentActionTile(action, enabled && chatObjectsEnabled, onAction)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = beyGramPalette().textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
private fun AttachmentActionTile(action: AttachmentAction, enabled: Boolean, onAction: (AttachmentAction) -> Unit) {
    val palette = beyGramPalette()
    val icon = when (action) {
        AttachmentAction.Camera -> Icons.Rounded.PhotoCamera
        AttachmentAction.PhotoVideo -> Icons.Rounded.Image
        AttachmentAction.File -> Icons.Rounded.Description
        AttachmentAction.Audio -> Icons.Rounded.GraphicEq
        AttachmentAction.Location -> Icons.Rounded.LocationOn
        AttachmentAction.Contact -> Icons.Rounded.Person
        AttachmentAction.Poll -> Icons.Rounded.Poll
        AttachmentAction.Dice -> Icons.Rounded.Casino
    }
    val tint = if (enabled) palette.textPrimary else palette.textPrimary.copy(alpha = 0.38f)
    Column(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface2)
            .clickable(enabled = enabled, role = Role.Button) { onAction(action) }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) palette.accent else tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(action.labelRes()),
            color = tint,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
