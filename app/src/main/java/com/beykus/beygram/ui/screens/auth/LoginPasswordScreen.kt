package com.beykus.beygram.ui.screens.auth

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.ui.components.AuthScaffold
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors

@Composable
fun LoginPasswordScreen(
    hint: String,
    authState: AuthState,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    val isLoading = authState is AuthState.Loading
    val errorMessage = when (authState) {
        is AuthState.Error -> authState.message
        AuthState.ConnectionTimeout -> "Не удалось связаться с Telegram. Проверьте сеть и попробуйте снова."
        else -> null
    }
    val canSubmit = password.isNotBlank() && !isLoading
    val palette = beyGramPalette()

    val subtitle = buildString {
        append("Введите облачный пароль Telegram.")
        if (hint.isNotBlank()) append("\nПодсказка: $hint")
    }

    AuthScaffold(
        title = "Двухфакторная\nаутентификация",
        subtitle = subtitle,
        onBack = onBack
    ) {
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            isError = errorMessage != null,
            supportingText = if (errorMessage != null) {
                { Text(text = errorMessage, color = palette.warningText) }
            } else null,
            colors = beyGramTextFieldColors()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onSubmit(password) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(vertical = 15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.telegramBlue,
                contentColor = Color.White,
                disabledContainerColor = palette.disabled,
                disabledContentColor = palette.textSecondary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "Войти",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp
                )
            }
        }
    }
}
