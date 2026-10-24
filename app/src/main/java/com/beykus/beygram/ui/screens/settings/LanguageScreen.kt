package com.beykus.beygram.ui.screens.settings

import android.app.Activity
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.AppLocale
import com.beykus.beygram.data.settings.TranslationSettingsStore
import com.beykus.beygram.data.settings.translationTargetLanguage
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.TranslationSettingsSection
import com.beykus.beygram.ui.screens.chat.TranslationTargetDialog
import com.beykus.beygram.ui.screens.profile.ProfileToolbar

/** Runtime language selection. Activity recreation refreshes stringResource throughout Compose. */
@Composable
fun LanguageScreen(translationSettings: TranslationSettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    var selected by remember { mutableStateOf(AppLocale.selected(context)) }
    val translation by translationSettings.state.collectAsStateWithLifecycle()
    var choosingTranslationLanguage by remember { mutableStateOf(false) }
    val targetLanguage = translationTargetLanguage(translation.targetLanguageCode,
        AppLocale.selected(context).ifBlank { java.util.Locale.getDefault().language })
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())) {
        ProfileToolbar(stringResource(R.string.feature_language), onBack)
        AppLocale.supported.forEach { language ->
            val title = when (language) {
                AppLocale.SYSTEM -> stringResource(R.string.feature_language_system)
                AppLocale.RUSSIAN -> stringResource(R.string.settings_language_ru)
                AppLocale.ENGLISH -> stringResource(R.string.settings_language_en)
                AppLocale.GERMAN -> stringResource(R.string.settings_language_de)
                AppLocale.SPANISH -> stringResource(R.string.settings_language_es)
                else -> stringResource(R.string.settings_language_en)
            }
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = selected == language, role = Role.RadioButton) {
                        if (selected != language) {
                            selected = language
                            AppLocale.update(context, language)
                            (context as? Activity)?.recreate()
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(title, modifier = Modifier.weight(1f), color = palette.textPrimary)
                RadioButton(selected = selected == language, onClick = null)
            }
        }
        Text(stringResource(R.string.feature_language_hint), color = palette.textSecondary, modifier = Modifier.padding(18.dp))
        TranslationSettingsSection(
            autoTranslate = translation.autoTranslate,
            targetLanguageCode = targetLanguage,
            onAutoTranslateChange = translationSettings::setAutoTranslate,
            onChooseLanguage = { choosingTranslationLanguage = true }
        )
        if (choosingTranslationLanguage) TranslationTargetDialog(
            currentLanguageCode = targetLanguage,
            onSelect = { translationSettings.setTargetLanguage(it); choosingTranslationLanguage = false },
            onDismiss = { choosingTranslationLanguage = false }
        )
    }
}
