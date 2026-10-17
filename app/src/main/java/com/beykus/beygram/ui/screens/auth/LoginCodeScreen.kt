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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.AuthState
import com.beykus.beygram.ui.components.AuthScaffold
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.components.beyGramTextFieldColors
import com.beykus.beygram.ui.text.resolve

@Composable
fun LoginCodeScreen(
    phone: String,
    authState: AuthState,
    onBack: () -> Unit,
    onComplete: (String) -> Unit,
    onResendCode: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    val isLoading = authState is AuthState.Loading
    val errorMessage = when (authState) {
        is AuthState.Error -> authState.text.resolve()
        AuthState.ConnectionTimeout -> stringResource(R.string.auth_code_connection_timeout)
        else -> null
    }
    // У Telegram длина кода зависит от способа доставки и может отличаться.
    val canContinue = code.length in 4..8 && !isLoading
    val palette = beyGramPalette()

    AuthScaffold(
        title = stringResource(R.string.auth_code_title),
        subtitle = stringResource(R.string.auth_code_subtitle, phone),
        onBack = onBack
    ) {
        OutlinedTextField(
            value = code,
            onValueChange = { value ->
                // Если была ошибка, сброс происходит при вводе следующего символа
                code = value.filter { it.isDigit() }.take(8)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.auth_code_label)) },
            placeholder = { Text(stringResource(R.string.auth_code_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            isError = errorMessage != null,
            supportingText = if (errorMessage != null) {
                { Text(text = errorMessage, color = palette.danger) }
            } else null,
            shape = RoundedCornerShape(14.dp),
            colors = beyGramTextFieldColors()
        )

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onResendCode,
            enabled = !isLoading,
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text(stringResource(R.string.auth_code_resend))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onComplete(code) },
            enabled = canContinue,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(vertical = 15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = palette.accent,
                contentColor = palette.onAccent,
                disabledContainerColor = palette.disabled,
                disabledContentColor = palette.textSecondary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = palette.onAccent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = stringResource(R.string.auth_sign_in),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp
                )
            }
        }
    }
}
