package com.beykus.beygram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = beyGramPalette()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.screen)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .imePadding()
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text("Назад")
        }

        Spacer(modifier = Modifier.height(52.dp))

        Text(
            text = title,
            color = palette.textPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            color = palette.textSecondary,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        content()
    }
}

@Composable
fun beyGramTextFieldColors(): androidx.compose.material3.TextFieldColors {
    val palette = beyGramPalette()

    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = palette.telegramBlue,
        unfocusedBorderColor = palette.divider,
        focusedLabelColor = palette.telegramBlue,
        cursorColor = palette.telegramBlue,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface
    )
}
