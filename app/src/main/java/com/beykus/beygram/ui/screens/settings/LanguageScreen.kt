package com.beykus.beygram.ui.screens.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.beykus.beygram.R
import com.beykus.beygram.data.settings.AppLocale
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.profile.ProfileToolbar

/** Runtime language selection. Activity recreation refreshes stringResource throughout Compose. */
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val palette = beyGramPalette()
    var selected by remember { mutableStateOf(AppLocale.selected(context)) }
    Column {
        ProfileToolbar(stringResource(R.string.feature_language), onBack)
        AppLocale.supported.forEach { language ->
            val title = when (language) {
                AppLocale.SYSTEM -> stringResource(R.string.feature_language_system)
                AppLocale.RUSSIAN -> "Русский"
                else -> "English"
            }
            Row(Modifier.fillMaxWidth().clickable {
                if (selected != language) {
                    selected = language
                    AppLocale.update(context, language)
                    (context as? Activity)?.recreate()
                }
            }.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(title, modifier = Modifier.weight(1f), color = palette.textPrimary)
                RadioButton(selected = selected == language, onClick = {
                    if (selected != language) {
                        selected = language
                        AppLocale.update(context, language)
                        (context as? Activity)?.recreate()
                    }
                })
            }
        }
        Text(stringResource(R.string.feature_language_hint), color = palette.textSecondary, modifier = Modifier.padding(18.dp))
    }
}
