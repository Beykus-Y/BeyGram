package com.beykus.beygram.ui.screens.theme

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.ChatTopPanelLayout
import com.beykus.beygram.data.appearance.DEFAULT_CHAT_TOP_PANEL_LAYOUT
import com.beykus.beygram.data.appearance.DEFAULT_MESSAGE_TEXT_SIZE_SP
import com.beykus.beygram.data.appearance.bubbleStyleFromStored
import com.beykus.beygram.data.appearance.chatWallpaperFromStored
import com.beykus.beygram.data.appearance.designStyleFromStored
import com.beykus.beygram.data.appearance.encode
import com.beykus.beygram.data.appearance.glassQualityFromStored
import com.beykus.beygram.data.appearance.listDensityFromStored
import com.beykus.beygram.data.appearance.messageAnimationConfigFromStored
import com.beykus.beygram.data.appearance.messageLineSpacingFromStored
import com.beykus.beygram.data.appearance.navigationPlacementFromStored
import com.beykus.beygram.data.appearance.chatTopPanelLayoutFromStored
import com.beykus.beygram.data.appearance.themeModeFromStored
import com.beykus.beygram.data.appearance.themePresetFromStored
import com.beykus.beygram.data.appearance.validBubbleCornerRadius
import com.beykus.beygram.data.appearance.validMessageTextSize
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.theme.resolveTokens
import org.json.JSONObject
import com.beykus.beygram.data.appearance.liquidGlassLensPresetFromStored

internal const val APPEARANCE_PRESET_SLOTS = 3

/**
 * Lightweight local store for up to 3 user-saved appearance presets (1-tap switch between favorite
 * combinations such as Daytime Signature and Night Liquid Glass).
 */
internal object AppearanceUserPresetStore {
    private const val PREFS_NAME = "beygram_appearance_user_presets"

    private fun key(slot: Int) = "slot_$slot"

    fun load(
        context: Context,
        slot: Int,
        currentLayout: ChatTopPanelLayout = DEFAULT_CHAT_TOP_PANEL_LAYOUT
    ): AppearanceConfig? {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key(slot), null) ?: return null
        return decode(raw, currentLayout)
    }

    fun save(context: Context, slot: Int, config: AppearanceConfig) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key(slot), encode(config))
            .apply()
    }

    internal fun encode(config: AppearanceConfig): String = JSONObject().apply {
        put("preset", config.preset.name)
        put("mode", config.mode.name)
        put("bubbleStyle", config.bubbleStyle.name)
        put("density", config.density.name)
        config.accentColor?.let { put("accentColor", it) }
        config.outgoingBubbleColor?.let { put("outgoingBubbleColor", it) }
        put("wallpaper", config.wallpaper.encode())
        put("messageTextSizeSp", config.messageTextSizeSp)
        config.bubbleCornerRadiusDp?.let { put("bubbleCornerRadiusDp", it) }
        put("messageLineSpacing", config.messageLineSpacing.name)
        config.messageTextColor?.let { put("messageTextColor", it) }
        config.messageTimeColor?.let { put("messageTimeColor", it) }
        put("messageAnimations", config.messageAnimations.encode())
        put("designStyle", config.designStyle.name)
        put("glassQuality", config.glassQuality.name)
        put("liquidGlassV2", config.liquidGlassV2)
        put("liquidGlassPreset", config.liquidGlassPreset.name)
        put("navigationPlacement", config.navigationPlacement.name)
        put("chatTopPanelLayout", config.chatTopPanelLayout.name)
    }.toString()

    internal fun decode(
        raw: String,
        currentLayout: ChatTopPanelLayout = DEFAULT_CHAT_TOP_PANEL_LAYOUT
    ): AppearanceConfig? = runCatching {
        val o = JSONObject(raw)
        AppearanceConfig(
            preset = themePresetFromStored(o.optString("preset")),
            mode = themeModeFromStored(o.optString("mode")),
            bubbleStyle = bubbleStyleFromStored(o.optString("bubbleStyle")),
            density = listDensityFromStored(o.optString("density")),
            accentColor = if (o.has("accentColor")) o.getInt("accentColor") else null,
            outgoingBubbleColor = if (o.has("outgoingBubbleColor")) o.getInt("outgoingBubbleColor") else null,
            wallpaper = chatWallpaperFromStored(o.optString("wallpaper")),
            messageTextSizeSp = validMessageTextSize(o.optInt("messageTextSizeSp", DEFAULT_MESSAGE_TEXT_SIZE_SP)),
            bubbleCornerRadiusDp = if (o.has("bubbleCornerRadiusDp")) validBubbleCornerRadius(o.getInt("bubbleCornerRadiusDp")) else null,
            messageLineSpacing = messageLineSpacingFromStored(o.optString("messageLineSpacing")),
            messageTextColor = if (o.has("messageTextColor")) o.getInt("messageTextColor") else null,
            messageTimeColor = if (o.has("messageTimeColor")) o.getInt("messageTimeColor") else null,
            messageAnimations = messageAnimationConfigFromStored(o.optString("messageAnimations")),
            designStyle = designStyleFromStored(o.optString("designStyle")),
            glassQuality = glassQualityFromStored(o.optString("glassQuality")),
            liquidGlassV2 = o.optBoolean("liquidGlassV2", true),
            liquidGlassPreset = liquidGlassLensPresetFromStored(o.optString("liquidGlassPreset")),
            navigationPlacement = navigationPlacementFromStored(o.optString("navigationPlacement")),
            chatTopPanelLayout = chatTopPanelLayoutFromStored(o.optString("chatTopPanelLayout"), currentLayout)
        )
    }.getOrNull()
}

@Composable
internal fun AppearanceUserPresetsSection(
    draft: AppearanceConfig,
    systemDark: Boolean,
    onApplyPreset: (AppearanceConfig) -> Unit
) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    var revision by remember { mutableIntStateOf(0) }
    val slots = remember(context, revision, draft.chatTopPanelLayout) {
        (1..APPEARANCE_PRESET_SLOTS).map { slot ->
            slot to AppearanceUserPresetStore.load(context, slot, draft.chatTopPanelLayout)
        }
    }

    AppearanceSectionLabel(stringResource(R.string.appearance_presets_title))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        slots.forEach { (slot, saved) ->
            val shape = styledShape(14.dp)
            val isCurrent = saved != null && saved == draft
            val previewTokens = saved?.let { resolveTokens(it, systemDark) }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (isCurrent) palette.accentSoft else palette.surface2, shape)
                    .border(1.dp, if (isCurrent) palette.accent else palette.divider, shape)
                    .clickable(role = Role.Button) {
                        if (saved != null) {
                            onApplyPreset(saved)
                            Toast.makeText(context, R.string.appearance_preset_applied, Toast.LENGTH_SHORT).show()
                        } else {
                            AppearanceUserPresetStore.save(context, slot, draft)
                            revision += 1
                            Toast.makeText(context, R.string.appearance_preset_saved, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (previewTokens != null) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(previewTokens.accent)
                        )
                    }
                    Text(
                        text = stringResource(R.string.appearance_preset_slot, slot),
                        color = if (isCurrent) palette.accentInk else palette.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (saved != null) stringResource(saved.designStyle.labelRes()) else stringResource(R.string.appearance_preset_save),
                    color = palette.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (saved != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.appearance_preset_save),
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(styledShape(8.dp))
                            .clickable(role = Role.Button) {
                                AppearanceUserPresetStore.save(context, slot, draft)
                                revision += 1
                                Toast.makeText(context, R.string.appearance_preset_saved, Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
