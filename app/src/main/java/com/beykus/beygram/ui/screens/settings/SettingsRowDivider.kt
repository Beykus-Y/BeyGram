package com.beykus.beygram.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beykus.beygram.ui.components.beyGramPalette

/** Отступ разделителя от левого края карточки, если строка начинается с иконки: иконка 20dp + поле 20dp + иконка 24dp + отступ 12dp. */
val SettingsRowInsetWithIcon: Dp = 56.dp

/** Отступ разделителя для строк без иконки. */
val SettingsRowInset: Dp = 16.dp

/**
 * Единый разделитель между строками карточки настроек: цвет из палитры темы, одна толщина, отступ от текста.
 * Ставится только между строками (перед второй и последующими), после последней строки не выводится.
 */
@Composable
fun SettingsRowDivider(startInset: Dp = SettingsRowInsetWithIcon) {
    HorizontalDivider(color = beyGramPalette().divider, modifier = Modifier.padding(start = startInset))
}
