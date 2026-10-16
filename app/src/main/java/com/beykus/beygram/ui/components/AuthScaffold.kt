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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 8.dp, start = 0.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад", tint = palette.textPrimary)
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = title,
            color = palette.textPrimary,
            fontSize = 30.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
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
        focusedBorderColor = palette.accent,
        unfocusedBorderColor = palette.divider,
        focusedLabelColor = palette.accentInk,
        errorBorderColor = palette.danger,
        errorLabelColor = palette.danger,
        errorSupportingTextColor = palette.danger,
        cursorColor = palette.accent,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun beyGramFilledTextFieldColors(): androidx.compose.material3.TextFieldColors {
    val palette = beyGramPalette()

    return androidx.compose.material3.TextFieldDefaults.colors(
        focusedContainerColor = palette.surface2,
        unfocusedContainerColor = palette.surface2,
        disabledContainerColor = palette.surface2,
        errorContainerColor = palette.surface2,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = palette.accent,
        focusedTextColor = palette.textPrimary,
        unfocusedTextColor = palette.textPrimary
    )
}
