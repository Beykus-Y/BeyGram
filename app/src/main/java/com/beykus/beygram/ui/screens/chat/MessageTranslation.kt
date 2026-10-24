package com.beykus.beygram.ui.screens.chat

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.TRANSLATION_LANGUAGES
import com.beykus.beygram.domain.model.MessageTranslation
import com.beykus.beygram.ui.components.beyGramPalette

/** UI-only state for one message and one target language. Results are cached for the chat visit by the screen. */
internal sealed interface TranslationUiState {
    data object Loading : TranslationUiState
    data class Ready(val translation: MessageTranslation) : TranslationUiState
    data class Failed(val error: String) : TranslationUiState
}

private val translationLanguageLabels = mapOf(
    "en" to R.string.translation_language_en,
    "ru" to R.string.translation_language_ru,
    "uk" to R.string.translation_language_uk,
    "de" to R.string.translation_language_de,
    "fr" to R.string.translation_language_fr,
    "es" to R.string.translation_language_es,
    "it" to R.string.translation_language_it,
    "tr" to R.string.translation_language_tr,
    "zh" to R.string.translation_language_zh,
    "ar" to R.string.translation_language_ar
)

internal data class TranslationLanguageOption(val code: String, @StringRes val label: Int)

internal val translationLanguageOptions: List<TranslationLanguageOption> =
    TRANSLATION_LANGUAGES.mapNotNull { code -> translationLanguageLabels[code]?.let { TranslationLanguageOption(code, it) } }

internal fun translationLanguageLabel(languageCode: String): Int =
    translationLanguageLabels[languageCode] ?: R.string.translation_language_en

/** Target-language picker shared by the message action and the translation settings row. */
@Composable
internal fun TranslationTargetDialog(currentLanguageCode: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = beyGramPalette()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.translation_target_title), color = palette.textPrimary) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                translationLanguageOptions.forEach { language ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = language.code == currentLanguageCode, role = Role.RadioButton) {
                                onSelect(language.code)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = language.code == currentLanguageCode, onClick = null)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(language.label), color = palette.textPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = palette.accent) }
        }
    )
}

/**
 * Inline translation under the original text. Loading can be cancelled, a failure can be retried, and a ready result
 * can be hidden without deleting its cached copy for the visit.
 */
@Composable
internal fun TranslationBlock(
    state: TranslationUiState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    messageId: Long,
    modifier: Modifier = Modifier
) {
    val palette = beyGramPalette()
    Column(modifier.fillMaxWidth().padding(top = 6.dp)) {
        HorizontalDivider(color = palette.divider)
        when (state) {
            TranslationUiState.Loading -> Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), color = palette.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.translation_translating), color = palette.textSecondary, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel), color = palette.accent) }
            }
            is TranslationUiState.Ready -> Column(Modifier.padding(top = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.translation_translated_to, stringResource(translationLanguageLabel(state.translation.languageCode))),
                        color = palette.accentInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.translation_dismiss), tint = palette.textSecondary,
                            modifier = Modifier.size(18.dp))
                    }
                }
                FormattedMessageText(state.translation.text, state.translation.entities, messageId)
            }
            is TranslationUiState.Failed -> Column(Modifier.padding(top = 6.dp)) {
                Text(state.error, color = palette.danger, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = palette.textSecondary) }
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.common_retry), color = palette.accent) }
                }
            }
        }
    }
}

/** Compact translation settings row: current target opens the same picker used by the message action. */
@Composable
internal fun TranslationSettingsSection(
    autoTranslate: Boolean,
    targetLanguageCode: String,
    onAutoTranslateChange: (Boolean) -> Unit,
    onChooseLanguage: () -> Unit
) {
    val palette = beyGramPalette()
    val targetName = stringResource(translationLanguageLabel(targetLanguageCode))
    HorizontalDivider(color = palette.divider, modifier = Modifier.padding(top = 10.dp))
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.translation_settings_title), color = palette.textSecondary, fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.translation_auto_title), color = palette.textPrimary)
                Text(stringResource(R.string.translation_auto_hint), color = palette.textSecondary, fontSize = 12.sp)
            }
            androidx.compose.material3.Switch(checked = autoTranslate, onCheckedChange = onAutoTranslateChange)
        }
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onChooseLanguage)
                .padding(top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.translation_target_title), color = palette.textPrimary, modifier = Modifier.weight(1f))
            Text(targetName, color = palette.accentInk)
        }
    }
}
