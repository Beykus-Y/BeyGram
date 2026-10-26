package com.beykus.beygram.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.PhoneChangeCode
import com.beykus.beygram.domain.model.PhoneCodeDelivery
import com.beykus.beygram.domain.model.isPlausiblePhoneNumber
import com.beykus.beygram.domain.model.normalizePhoneNumberInput
import com.beykus.beygram.ui.accessibility.errorAnnouncement
import com.beykus.beygram.ui.components.beyGramPalette

private const val MAX_CODE_DIGITS = 12

/**
 * Both steps of a phone-number change in one dialog. While [pending] is null the user types the new
 * number and asks for a code. Once Telegram has sent one, the dialog asks for that code and offers a
 * resend only when Telegram reports another delivery route.
 *
 * The typed number and code live only in this dialog; the screen owns the request state and errors.
 */
@Composable
internal fun PhoneNumberChangeDialog(
    pending: PhoneChangeCode?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRequestCode: (phoneNumber: String) -> Unit,
    onResend: () -> Unit,
    onConfirm: (code: String) -> Unit
) {
    val palette = beyGramPalette()
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.account_security_phone_dialog_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pending == null) {
                    Text(
                        stringResource(R.string.account_security_phone_dialog_body),
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = normalizePhoneNumberInput(it) },
                        label = { Text(stringResource(R.string.account_security_phone_new_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (phone.isNotEmpty() && !isPlausiblePhoneNumber(phone)) {
                        Text(
                            stringResource(R.string.account_security_phone_invalid),
                            color = palette.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        stringResource(
                            R.string.account_security_phone_code_sent,
                            pending.phoneNumber.ifBlank { phone }
                        ),
                        color = palette.textPrimary,
                        fontSize = 14.sp
                    )
                    Text(
                        stringResource(deliveryText(pending.delivery)),
                        color = palette.textSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { typed ->
                            code = typed.filter { digit -> digit in '0'..'9' }.take(MAX_CODE_DIGITS)
                        },
                        label = { Text(stringResource(R.string.account_security_phone_code_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pending.canResend) {
                        TextButton(
                            onClick = onResend,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                stringResource(R.string.account_security_phone_resend),
                                color = palette.accentInk
                            )
                        }
                    }
                }
                error?.let {
                    Text(it, color = palette.danger, fontSize = 13.sp, modifier = Modifier.errorAnnouncement())
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = palette.accent)
            }
        },
        confirmButton = {
            if (pending == null) {
                TextButton(
                    enabled = !busy && isPlausiblePhoneNumber(phone),
                    onClick = { onRequestCode(phone) }
                ) {
                    Text(stringResource(R.string.account_security_phone_send_code), color = palette.accentInk)
                }
            } else {
                TextButton(
                    enabled = !busy && code.isNotEmpty(),
                    onClick = { onConfirm(code) }
                ) {
                    Text(stringResource(R.string.account_security_phone_confirm), color = palette.accentInk)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@StringRes
private fun deliveryText(delivery: PhoneCodeDelivery): Int = when (delivery) {
    PhoneCodeDelivery.TelegramApp -> R.string.account_security_phone_delivery_app
    PhoneCodeDelivery.Sms -> R.string.account_security_phone_delivery_sms
    PhoneCodeDelivery.Call -> R.string.account_security_phone_delivery_call
    PhoneCodeDelivery.FlashCall -> R.string.account_security_phone_delivery_flash_call
    PhoneCodeDelivery.MissedCall -> R.string.account_security_phone_delivery_missed_call
    PhoneCodeDelivery.Fragment -> R.string.account_security_phone_delivery_fragment
    PhoneCodeDelivery.Firebase, PhoneCodeDelivery.Other -> R.string.account_security_phone_delivery_other
}
