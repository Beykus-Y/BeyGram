package com.beykus.beygram.ui.screens.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.BubbleStyle
import com.beykus.beygram.data.appearance.ListDensity
import com.beykus.beygram.data.appearance.MAX_BUBBLE_CORNER_RADIUS_DP
import com.beykus.beygram.data.appearance.MAX_MESSAGE_TEXT_SIZE_SP
import com.beykus.beygram.data.appearance.MIN_BUBBLE_CORNER_RADIUS_DP
import com.beykus.beygram.data.appearance.MIN_MESSAGE_TEXT_SIZE_SP
import com.beykus.beygram.data.appearance.MessageLineSpacing
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.bubbleMetricsFor
import kotlin.math.roundToInt

@Composable
internal fun AppearanceMessagesPage(
    draft: AppearanceConfig,
    systemDark: Boolean,
    onDraftChange: (AppearanceConfig) -> Unit,
    onDraftChangeContinuous: (AppearanceConfig) -> Unit,
    onOpenCustomColors: () -> Unit,
    onOpenPreview: () -> Unit
) {
    val palette = beyGramPalette()
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val shapeRadius = bubbleMetricsFor(draft.bubbleStyle, draft.density).cornerRadius.value.roundToInt()
    val effectiveRadius = draft.bubbleCornerRadiusDp ?: shapeRadius
    val customTextColors = draft.messageTextColor != null || draft.messageTimeColor != null

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = AppearanceDimens.pagePadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppearanceSectionLabel(stringResource(R.string.theme_preview_title), Modifier.weight(1f))
            TextButton(onClick = onOpenPreview) {
                Icon(Icons.Rounded.Fullscreen, null, modifier = Modifier.size(17.dp))
                Text(stringResource(R.string.appearance_preview_open), Modifier.padding(start = 5.dp), fontSize = 13.sp)
            }
        }
        Box(
            Modifier.fillMaxWidth().clip(AppearanceDimens.cardShape)
                .border(1.dp, palette.divider, AppearanceDimens.cardShape)
                .clickable(role = Role.Button, onClick = onOpenPreview)
        ) {
            AppearanceChatPreview(
                config = draft,
                compact = true,
                animate = false,
                replayKey = 0,
                systemDark = systemDark,
                showComposer = false,
                modifier = Modifier.fillMaxWidth()
            )
        }

        AppearanceSectionLabel(stringResource(R.string.appearance_bubbles_section))
        AppearanceCard {
            AppearanceChoiceFlow(
                options = BubbleStyle.entries.map {
                    stringResource(it.labelRes()) to (draft.bubbleStyle == it)
                },
                onSelect = { index ->
                    onDraftChange(draft.copy(bubbleStyle = BubbleStyle.entries[index]))
                }
            )
            AppearanceSliderRow(
                title = stringResource(R.string.appearance_corner_radius),
                valueText = stringResource(R.string.appearance_dp_value, effectiveRadius),
                value = effectiveRadius.toFloat(),
                range = MIN_BUBBLE_CORNER_RADIUS_DP.toFloat()..MAX_BUBBLE_CORNER_RADIUS_DP.toFloat()
            ) { value ->
                onDraftChangeContinuous(
                    draft.copy(
                        bubbleCornerRadiusDp = value.roundToInt()
                            .coerceIn(MIN_BUBBLE_CORNER_RADIUS_DP, MAX_BUBBLE_CORNER_RADIUS_DP)
                    )
                )
            }
            AppearanceChoiceChip(
                text = stringResource(R.string.theme_editor_corner_radius_by_shape),
                selected = draft.bubbleCornerRadiusDp == null,
                onClick = { onDraftChange(draft.copy(bubbleCornerRadiusDp = null)) }
            )
            Text(
                stringResource(R.string.appearance_corner_radius_hint),
                color = palette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        AppearanceSectionLabel(stringResource(R.string.appearance_text_section))
        AppearanceCard {
            AppearanceSliderRow(
                title = stringResource(R.string.appearance_text_size),
                valueText = stringResource(R.string.appearance_sp_value, draft.messageTextSizeSp),
                value = draft.messageTextSizeSp.toFloat(),
                range = MIN_MESSAGE_TEXT_SIZE_SP.toFloat()..MAX_MESSAGE_TEXT_SIZE_SP.toFloat()
            ) { value ->
                onDraftChangeContinuous(
                    draft.copy(
                        messageTextSizeSp = value.roundToInt()
                            .coerceIn(MIN_MESSAGE_TEXT_SIZE_SP, MAX_MESSAGE_TEXT_SIZE_SP)
                    )
                )
            }
            Text(
                stringResource(R.string.theme_editor_text_size_hint),
                color = palette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Text(
                stringResource(R.string.theme_editor_line_spacing),
                color = palette.textPrimary,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            AppearanceChoiceFlow(
                options = MessageLineSpacing.entries.map {
                    stringResource(it.labelRes()) to (draft.messageLineSpacing == it)
                },
                onSelect = { index ->
                    onDraftChange(draft.copy(messageLineSpacing = MessageLineSpacing.entries[index]))
                }
            )
        }

        AppearanceSectionLabel(stringResource(R.string.appearance_density_section))
        AppearanceCard {
            AppearanceChoiceFlow(
                options = ListDensity.entries.map {
                    stringResource(it.labelRes()) to (draft.density == it)
                },
                onSelect = { index ->
                    onDraftChange(draft.copy(density = ListDensity.entries[index]))
                }
            )
            Text(
                stringResource(R.string.appearance_density_hint),
                color = palette.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        AppearanceSectionLabel(stringResource(R.string.appearance_message_colors_section))
        AppearanceNavCard(
            title = stringResource(R.string.appearance_message_colors_title),
            summary = stringResource(
                if (customTextColors) R.string.appearance_message_colors_custom
                else R.string.appearance_message_colors_theme
            ),
            onClick = onOpenCustomColors,
            trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            leading = { AppearanceIconTile(Icons.Rounded.Tune) }
        )
        TextButton(onClick = { confirmReset = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.appearance_reset_messages), color = palette.danger, fontSize = 13.sp)
        }
        Text(
            stringResource(R.string.appearance_messages_note),
            color = palette.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
    if (confirmReset) AppearanceResetDialog(
        title = stringResource(R.string.appearance_reset_messages_title),
        message = stringResource(R.string.appearance_reset_messages_message),
        onConfirm = {
            confirmReset = false
            onDraftChange(draft.resetMessages())
        },
        onDismiss = { confirmReset = false }
    )
}
