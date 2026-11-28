package com.beykus.beygram.ui.screens.settings

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.AppLocale
import com.beykus.beygram.data.settings.TranslationSettingsStore
import com.beykus.beygram.data.settings.translationTargetLanguage
import com.beykus.beygram.ui.components.Switch
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.LocalDesignSystem
import com.beykus.beygram.ui.design.glassScreenBackground
import com.beykus.beygram.ui.screens.chat.TranslationTargetDialog
import com.beykus.beygram.ui.screens.chat.translationLanguageLabel
import com.beykus.beygram.ui.screens.profile.ProfileToolbar
import com.beykus.beygram.ui.components.GlassScreenScaffold
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

/** Text inset of the rows on this page; the dividers start where the row text starts. */
private val LanguageRowInset = 20.dp

/**
 * Language and translation settings. The interface language and the message translation are two sections with their
 * own headings, so the translation target never reads as one more app language. Activity recreation refreshes
 * stringResource throughout Compose.
 */
@Composable
fun LanguageScreen(translationSettings: TranslationSettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    val design = LocalDesignSystem.current
    var selected by remember { mutableStateOf(AppLocale.selected(context)) }
    val translation by translationSettings.state.collectAsStateWithLifecycle()
    // Saved, so the target picker stays open when the activity is recreated (rotation or a language change).
    var choosingTranslationLanguage by rememberSaveable { mutableStateOf(false) }
    val targetLanguage = translationTargetLanguage(translation.targetLanguageCode,
        AppLocale.selected(context).ifBlank { java.util.Locale.getDefault().language })
    GlassScreenScaffold(
        modifier = Modifier.fillMaxSize()
            .glassScreenBackground(design, palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        toolbar = {
            ProfileToolbar(stringResource(R.string.feature_language), onBack)
        }
    ) { chrome ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(chrome.top))
        SettingsGroup(stringResource(R.string.feature_language_group_interface)) {
            AppLocale.supported.forEachIndexed { index, language ->
                if (index > 0) SettingsRowDivider(startInset = LanguageRowInset)
                LanguageChoiceRow(
                    language = language,
                    selected = selected == language,
                    onSelect = {
                        // Tapping the current language does nothing, so the change and the recreation run once.
                        if (selected != language) {
                            selected = language
                            AppLocale.update(context, language)
                            (context as? Activity)?.recreate()
                        }
                    }
                )
            }
        }
        Text(
            stringResource(R.string.feature_language_hint),
            color = palette.textSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 14.dp)
        )
        SettingsGroup(stringResource(R.string.translation_settings_title)) {
            AutoTranslateRow(
                checked = translation.autoTranslate,
                onCheckedChange = translationSettings::setAutoTranslate
            )
            SettingsRowDivider(startInset = LanguageRowInset)
            TranslationTargetRow(
                targetName = stringResource(translationLanguageLabel(targetLanguage)),
                onClick = { choosingTranslationLanguage = true }
            )
        }
        if (choosingTranslationLanguage) TranslationTargetDialog(
            currentLanguageCode = targetLanguage,
            onSelect = { translationSettings.setTargetLanguage(it); choosingTranslationLanguage = false },
            onDismiss = { choosingTranslationLanguage = false }
        )
        }
    }
}

/**
 * One app-language choice. The whole row is the radio target and carries the selected state for TalkBack; the radio
 * itself has no action of its own. The selected name takes the accent and a heavier weight, so the choice is visible
 * without relying on the small radio alone.
 */
@Composable
private fun LanguageChoiceRow(language: String, selected: Boolean, onSelect: () -> Unit) {
    val palette = beyGramPalette()
    val names = languageNames(language)
    val title = stringResource(names.title)
    // The name in the current interface language is shown only when it differs from the native name.
    val localizedRes = names.localizedName
    val localized = if (localizedRes == null) null else stringResource(localizedRes).takeIf { it != title }
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = LanguageRowInset, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                color = if (selected) palette.accentInk else palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            if (localized != null) Text(localized, color = palette.textSecondary, fontSize = 13.sp)
        }
        RadioButton(selected = selected, onClick = null)
    }
}

/** Names of one app language: its native name, and the same language in the current interface language. */
private data class LanguageNames(@StringRes val title: Int, @StringRes val localizedName: Int?)

private fun languageNames(language: String): LanguageNames = when (language) {
    AppLocale.SYSTEM -> LanguageNames(R.string.feature_language_system, null)
    AppLocale.RUSSIAN -> LanguageNames(R.string.settings_language_ru, R.string.translation_language_ru)
    AppLocale.ENGLISH -> LanguageNames(R.string.settings_language_en, R.string.translation_language_en)
    AppLocale.GERMAN -> LanguageNames(R.string.settings_language_de, R.string.translation_language_de)
    AppLocale.SPANISH -> LanguageNames(R.string.settings_language_es, R.string.translation_language_es)
    AppLocale.FRENCH -> LanguageNames(R.string.settings_language_fr, R.string.translation_language_fr)
    else -> LanguageNames(R.string.settings_language_en, null)
}

/** Automatic translation as one switch row. The whole row toggles, so TalkBack reads the title, the hint and the state. */
@Composable
private fun AutoTranslateRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = LanguageRowInset, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(R.string.translation_auto_title), color = palette.textPrimary, fontSize = 16.sp)
            Text(stringResource(R.string.translation_auto_hint), color = palette.textSecondary, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The translation target. It opens the same picker as the message action, and the row is one button for TalkBack. */
@Composable
private fun TranslationTargetRow(targetName: String, onClick: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = LanguageRowInset, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.translation_target_title),
            color = palette.textPrimary,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Text(targetName, color = palette.accentInk, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.textSecondary,
            modifier = Modifier.size(22.dp))
    }
}
