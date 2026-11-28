package com.beykus.beygram.ui.screens.chat

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.GlassEdge
import com.beykus.beygram.ui.design.GlassPanel
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassSheet
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.design.styledTopShape
import com.beykus.beygram.ui.design.usesGlassPanels
import com.beykus.beygram.ui.motion.MotionToken
import com.beykus.beygram.ui.motion.motionEntrance
import com.beykus.beygram.ui.motion.rememberMotionSpec

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
 * The columns of the attachment menu. The menu is one compact surface: every action is an icon with its caption and
 * no round pad of its own, so a cell needs room for the longest label rather than for a circle. [fontScale] widens the
 * cells instead of shrinking the text, and the rows are balanced so the last row is never a lonely tile.
 */
internal fun attachmentTileColumns(widthDp: Float, fontScale: Float, actionCount: Int): Int {
    if (actionCount <= 0) return 1
    val scale = fontScale.coerceIn(1f, 2f)
    val minCell = 76f * scale
    val capacity = ((widthDp - 20f) / minCell).toInt().coerceIn(MIN_ATTACHMENT_COLUMNS, MAX_ATTACHMENT_COLUMNS)
    val rows = (actionCount + capacity - 1) / capacity
    return (actionCount + rows - 1) / rows
}

/** Three columns keep a caption readable on the narrowest phone; six is the widest a compact menu should get. */
private const val MIN_ATTACHMENT_COLUMNS = 3
private const val MAX_ATTACHMENT_COLUMNS = 6

/** The minimum touch target of one action, above the 48dp accessibility floor even before the cell padding. */
private val ActionTileMinHeight = 60.dp
private const val PressedWashAlpha = 0.08f

/**
 * The attachment menu: one shared compact surface with an icon and a caption per action.
 *
 * The concept the owner agreed on: a single panel, icons with labels, no separate round pad under each icon, tidy
 * spacing, an adaptive grid, no built-in gallery and no horizontal scroll. All actions and their availability rules are
 * unchanged — the tiles call the same `onAction`, keep the same enabled state and the same accessibility role.
 *
 * In Liquid Glass the panel is a real pane of the material; on the shared composer plane it stays transparent so the
 * pane behind it shows; the other styles keep their own material.
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
    val menuBody: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            if (!chatObjectsEnabled) {
                Text(
                    stringResource(R.string.composer_menu_chat_unavailable),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 2.dp)
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val columns = attachmentTileColumns(maxWidth.value, LocalDensity.current.fontScale, actions.size)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(actions.size, key = { actions[it].name }) { index ->
                        val action = actions[index]
                        AttachmentActionTile(
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

/**
 * One action of the attachment menu: a bare icon and its caption on the shared surface, with the whole cell as the
 * touch target. The press feedback is a soft rounded wash, so no tile draws a permanent pad behind the icon.
 */
@Composable
private fun AttachmentActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    entranceIndex: Int
) {
    val palette = beyGramPalette()
    val spec = rememberMotionSpec(MotionToken.MicroFeedback)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(
        targetValue = if (pressed && enabled) PressedWashAlpha else 0f,
        animationSpec = spec.animationSpec(),
        label = "attachmentTileWash"
    )
    val shape = styledShape(14.dp)
    val delay = EntranceStepMillis * entranceIndex.coerceIn(0, EntranceMaxSteps)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ActionTileMinHeight)
            .motionEntrance(MotionToken.SheetEnter, EntranceRise, delay)
            .clip(shape)
            .background(palette.textPrimary.copy(alpha = wash))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) palette.accent else palette.accent.copy(alpha = 0.38f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = if (enabled) palette.textPrimary else palette.textPrimary.copy(alpha = 0.38f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** The entrance stagger of one tile, bounded so a long menu never waits on the animation. */
private const val EntranceStepMillis = 18
private const val EntranceMaxSteps = 8