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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.ThemePreset
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.theme.SystemColorsAvailable
import com.beykus.beygram.ui.theme.resolveTokens

@Composable
internal fun AppearanceOverviewPage(
    draft: AppearanceConfig,
    systemDark: Boolean,
    onOpenPage: (AppearancePage) -> Unit,
    onOpenPreview: () -> Unit
) {
    val palette = beyGramPalette()
    val tokens = resolveTokens(draft, systemDark)
    val effectivePreset = draft.preset.takeUnless { it == ThemePreset.MaterialYou && !SystemColorsAvailable } ?: ThemePreset.Warm
    val themeSummary = stringResource(
        R.string.appearance_theme_summary,
        stringResource(effectivePreset.labelRes()),
        stringResource(draft.mode.labelRes())
    )
    val messagesSummary = stringResource(
        R.string.appearance_messages_summary,
        stringResource(draft.bubbleStyle.labelRes()),
        draft.messageTextSizeSp
    )
    val animations = draft.messageAnimations
    val animationsSummary = when {
        !animations.enabled -> stringResource(R.string.appearance_animations_off)
        animations.linkedDirections -> stringResource(
            R.string.appearance_animation_linked_summary,
            stringResource(animations.shared.preset.labelRes())
        )
        else -> stringResource(
            R.string.appearance_animation_split_summary,
            stringResource(animations.sent.preset.labelRes()),
            stringResource(animations.received.preset.labelRes())
        )
    }
    val customColors = draft.accentColor != null || draft.outgoingBubbleColor != null ||
        draft.messageTextColor != null || draft.messageTimeColor != null

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = AppearanceDimens.pagePadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppearanceSectionLabel(stringResource(R.string.theme_preview_title), Modifier.weight(1f))
            TextButton(onClick = onOpenPreview) {
                Icon(Icons.Rounded.Fullscreen, null, modifier = Modifier.size(17.dp))
                Text(
                    stringResource(R.string.appearance_preview_open),
                    modifier = Modifier.padding(start = 5.dp),
                    fontSize = 13.sp
                )
            }
        }
        val previewDescription = stringResource(R.string.appearance_preview_open_description)
        Box(
            Modifier.fillMaxWidth().clip(AppearanceDimens.cardShape)
                .border(1.dp, palette.divider, AppearanceDimens.cardShape)
                .clickable(role = Role.Button, onClick = onOpenPreview)
                .semantics { contentDescription = previewDescription }
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

        AppearanceSectionLabel(stringResource(R.string.appearance_section_navigation))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppearanceNavCard(
                title = stringResource(R.string.appearance_colors_title),
                summary = themeSummary,
                onClick = { onOpenPage(AppearancePage.Colors) },
                trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                leading = {
                    Box(
                        Modifier.size(42.dp).clip(AppearanceDimens.leadingTileShape).background(palette.surface2),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            PaletteDot(tokens.accent)
                            PaletteDot(tokens.bubbleOut)
                            PaletteDot(tokens.surface2)
                        }
                    }
                }
            )
            AppearanceNavCard(
                title = stringResource(R.string.appearance_messages_title),
                summary = messagesSummary,
                onClick = { onOpenPage(AppearancePage.Messages) },
                trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                leading = { AppearanceIconTile(Icons.Rounded.ChatBubbleOutline) }
            )
            AppearanceNavCard(
                title = stringResource(R.string.theme_animations_title),
                summary = animationsSummary,
                onClick = { onOpenPage(AppearancePage.Animations) },
                trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                leading = { AppearanceIconTile(Icons.Rounded.Animation) }
            )
            AppearanceNavCard(
                title = stringResource(R.string.wallpaper_title),
                summary = wallpaperLabel(draft.wallpaper),
                onClick = { onOpenPage(AppearancePage.Wallpapers) },
                trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                leading = { AppearanceIconTile(Icons.Rounded.Wallpaper) }
            )
        }

        AppearanceSectionLabel(stringResource(R.string.appearance_section_advanced))
        AppearanceNavCard(
            title = stringResource(R.string.appearance_custom_colors_title),
            summary = stringResource(
                if (customColors) R.string.appearance_custom_colors_summary_set
                else R.string.appearance_custom_colors_summary_default
            ),
            onClick = { onOpenPage(AppearancePage.CustomColors) },
            trailingIcon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            leading = { AppearanceIconTile(Icons.Rounded.Tune) }
        )
    }
}

@Composable
private fun PaletteDot(color: androidx.compose.ui.graphics.Color) {
    val palette = beyGramPalette()
    Box(
        Modifier.size(10.dp).clip(CircleShape).background(color)
            .border(1.dp, palette.textSecondary.copy(alpha = 0.45f), CircleShape)
    )
}
