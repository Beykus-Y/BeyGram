package com.beykus.beygram.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.ChatAnimationSettings
import com.beykus.beygram.ui.accessibility.systemReducedMotion
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar

/**
 * Global switches for decorative chat animation. The preferences stay saved when the system asks for reduced motion;
 * the chat renderer simply shows the static fallback until the system signal goes away.
 */
@Composable
fun ChatAnimationsScreen(
    settings: ChatAnimationSettings,
    onAnimatedEmojiChange: (Boolean) -> Unit,
    onMessageEffectsChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val reducedMotion = systemReducedMotion()
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.chat_animations_title), onBack)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.chat_animations_intro),
                color = palette.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.surface)
                    .padding(vertical = 4.dp)
            ) {
                AnimationSwitchRow(
                    icon = Icons.Rounded.EmojiEmotions,
                    title = stringResource(R.string.chat_animations_emoji_title),
                    detail = stringResource(R.string.chat_animations_emoji_detail),
                    checked = settings.animatedEmoji,
                    onCheckedChange = onAnimatedEmojiChange
                )
                HorizontalDivider(color = palette.divider, modifier = Modifier.padding(start = 56.dp))
                AnimationSwitchRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.chat_animations_effects_title),
                    detail = stringResource(R.string.chat_animations_effects_detail),
                    checked = settings.messageEffects,
                    onCheckedChange = onMessageEffectsChange
                )
            }
            Text(
                stringResource(if (reducedMotion) R.string.chat_animations_reduced_motion else R.string.chat_animations_footnote),
                color = if (reducedMotion) palette.accentInk else palette.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun AnimationSwitchRow(
    icon: ImageVector,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = palette.accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 12.dp)) {
            Text(title, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = palette.textSecondary, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
