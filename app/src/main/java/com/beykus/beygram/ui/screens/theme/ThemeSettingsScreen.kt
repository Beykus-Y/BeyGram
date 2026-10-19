package com.beykus.beygram.ui.screens.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.appearance.AppearanceConfig
import com.beykus.beygram.data.appearance.ChatWallpaper
import com.beykus.beygram.data.appearance.ThemeMode
import com.beykus.beygram.data.appearance.WallpaperFiles
import com.beykus.beygram.data.appearance.ThemePreset
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.beykus.beygram.ui.theme.BeyGramTokens
import com.beykus.beygram.ui.theme.SystemColorsAvailable
import com.beykus.beygram.ui.theme.resolveTokens

/**
 * Lets the user pick a palette and a brightness. The preview shows the choice as it is being made.
 * The choice reaches the rest of the app only after «Применить». Unapplied changes survive visiting the editor.
 */
@Composable
fun ThemeSettingsScreen(
    saved: AppearanceConfig,
    draft: AppearanceConfig,
    onDraftChange: (AppearanceConfig) -> Unit,
    onSave: (AppearanceConfig) -> Unit,
    onOpenEditor: (AppearanceConfig) -> Unit,
    onBack: () -> Unit
) {
    val palette = beyGramPalette()
    val systemDark = isSystemInDarkTheme()
    val preset = draft.preset.takeUnless { it == ThemePreset.MaterialYou && !SystemColorsAvailable } ?: ThemePreset.Warm
    val mode = draft.mode
    val wallpaper = draft.wallpaper
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    fun requestBack() {
        if (draft != saved) confirmDiscard = true else onBack()
    }
    // Once a picture is applied, the copies of the pictures that were only tried are no longer needed.
    val context = LocalContext.current
    LaunchedEffect(saved.wallpaper) {
        // The restored draft is kept too, so turning the screen does not delete a picture that is still on screen.
        WallpaperFiles.deleteAllExcept(context, setOfNotNull((saved.wallpaper as? ChatWallpaper.Photo)?.fileName, (wallpaper as? ChatWallpaper.Photo)?.fileName))
    }
    BackHandler(onBack = ::requestBack)

    Column(Modifier.fillMaxSize().background(palette.screen).windowInsetsPadding(WindowInsets.safeDrawing)) {
        ProfileToolbar(stringResource(R.string.theme_title), ::requestBack)
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle(stringResource(R.string.theme_preview_title))
            BeyGramTheme(darkTheme = mode.isDark(systemDark), tokens = resolveTokens(draft, systemDark)) {
                ThemePreview()
            }

            SectionTitle(stringResource(R.string.theme_palette_title))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(vertical = 4.dp)) {
                PresetRow(
                    title = stringResource(R.string.theme_palette_warm),
                    selected = preset == ThemePreset.Warm,
                    swatch = resolveTokens(ThemePreset.Warm, mode, systemDark)
                ) { onDraftChange(draft.copy(preset = ThemePreset.Warm)) }
                PresetRow(
                    title = stringResource(R.string.theme_palette_graphite),
                    selected = preset == ThemePreset.Graphite,
                    swatch = resolveTokens(ThemePreset.Graphite, mode, systemDark)
                ) { onDraftChange(draft.copy(preset = ThemePreset.Graphite)) }
                if (SystemColorsAvailable) {
                    PresetRow(
                        title = stringResource(R.string.theme_palette_system),
                        selected = preset == ThemePreset.MaterialYou,
                        swatch = resolveTokens(ThemePreset.MaterialYou, mode, systemDark)
                    ) { onDraftChange(draft.copy(preset = ThemePreset.MaterialYou)) }
                }
            }

            SectionTitle(stringResource(R.string.theme_brightness_title))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(vertical = 4.dp)) {
                BrightnessRow(stringResource(R.string.theme_brightness_system), ThemeMode.FollowSystem, mode) { onDraftChange(draft.copy(mode = ThemeMode.FollowSystem)) }
                BrightnessRow(stringResource(R.string.theme_brightness_light), ThemeMode.Light, mode) { onDraftChange(draft.copy(mode = ThemeMode.Light)) }
                BrightnessRow(stringResource(R.string.theme_brightness_dark), ThemeMode.Dark, mode) { onDraftChange(draft.copy(mode = ThemeMode.Dark)) }
                BrightnessRow(stringResource(R.string.theme_brightness_amoled), ThemeMode.Amoled, mode) { onDraftChange(draft.copy(mode = ThemeMode.Amoled)) }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface).clickable { onOpenEditor(draft) }.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.theme_editor_open), color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = palette.textSecondary)
            }
            SectionTitle(stringResource(R.string.wallpaper_title))
            WallpaperSection(selected = wallpaper, saved = saved.wallpaper, onSelect = { onDraftChange(draft.copy(wallpaper = it)) })
            Text(stringResource(R.string.theme_footnote), color = palette.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
        }
        Button(
            onClick = { onSave(draft) },
            enabled = draft != saved,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(stringResource(R.string.theme_apply))
        }
    }
    if (confirmDiscard) ThemeDiscardDialog(
        onKeepEditing = { confirmDiscard = false },
        onDiscard = { confirmDiscard = false; onBack() }
    )
}

@Composable
internal fun SectionTitle(text: String) {
    val palette = beyGramPalette()
    Text(text, color = palette.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
}

/** A small chat with fake messages, drawn with the tokens of the choice being made. */
@Composable
internal fun ThemePreview(bubbleRadius: Dp = 16.dp, spacing: Dp = 8.dp) {
    val palette = beyGramPalette()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.screen).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(palette.accentSoft), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.theme_preview_contact).take(1), color = palette.accentInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(stringResource(R.string.theme_preview_contact), color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.theme_preview_last_message), color = palette.textSecondary, fontSize = 13.sp)
            }
            Text(stringResource(R.string.theme_preview_time), color = palette.textSecondary, fontSize = 12.sp)
        }
        PreviewBubble(text = stringResource(R.string.theme_preview_incoming), time = stringResource(R.string.theme_preview_time), outgoing = false, radius = bubbleRadius)
        PreviewBubble(text = stringResource(R.string.theme_preview_outgoing), time = stringResource(R.string.theme_preview_time_reply), outgoing = true, radius = bubbleRadius)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.theme_preview_today),
                color = palette.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(palette.surface2).padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun PreviewBubble(text: String, time: String, outgoing: Boolean, radius: Dp) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(radius))
                .background(if (outgoing) palette.outgoingBubble else palette.incomingBubble)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(text, color = palette.textPrimary, fontSize = 15.sp)
            Spacer(Modifier.width(8.dp))
            Text(time, color = palette.textSecondary, fontSize = 11.sp)
            if (outgoing) Icon(Icons.Rounded.DoneAll, null, tint = palette.accent, modifier = Modifier.padding(start = 2.dp).size(14.dp))
        }
    }
}

@Composable
private fun PresetRow(title: String, selected: Boolean, swatch: BeyGramTokens, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, color = palette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(swatch.bg))
            Box(Modifier.size(22.dp).clip(CircleShape).background(swatch.surface2))
            Box(Modifier.size(22.dp).clip(CircleShape).background(swatch.accent))
        }
    }
}

@Composable
private fun BrightnessRow(title: String, value: ThemeMode, selected: ThemeMode, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected == value, onClick = onClick)
        Text(title, color = palette.textPrimary, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp))
    }
}

