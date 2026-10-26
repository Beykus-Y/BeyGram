package com.beykus.beygram.ui.screens.chat

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.SecretChatDetails
import com.beykus.beygram.domain.model.SecretChatKey
import com.beykus.beygram.domain.model.SecretChatState
import com.beykus.beygram.domain.model.SecretChatTtl
import com.beykus.beygram.domain.model.formatTtlDuration
import com.beykus.beygram.domain.repository.ConversationRepository
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.launch

/** One localized label for the chat TTL, used by the header and the settings section. */
@Composable
internal fun secretChatTtlLabel(seconds: Int): String = when (seconds) {
    SecretChatTtl.OFF -> stringResource(R.string.secret_chat_ttl_off)
    SecretChatTtl.ONE_HOUR -> stringResource(R.string.secret_chat_ttl_one_hour)
    SecretChatTtl.ONE_DAY -> stringResource(R.string.secret_chat_ttl_one_day)
    SecretChatTtl.SEVEN_DAYS -> stringResource(R.string.secret_chat_ttl_seven_days)
    else -> formatTtlDuration(
        seconds,
        stringResource(R.string.secret_chat_ttl_unit_days),
        stringResource(R.string.secret_chat_ttl_unit_hours),
        stringResource(R.string.secret_chat_ttl_unit_minutes),
        stringResource(R.string.secret_chat_ttl_unit_seconds)
    )
}

/** E2E indicator and lifecycle state for the chat header; the TTL is shown only when it is enabled. */
@Composable
internal fun secretChatStatusLine(details: SecretChatDetails?): String {
    val state = when (details?.state) {
        SecretChatState.Pending -> stringResource(R.string.secret_chat_status_pending)
        SecretChatState.Closed -> stringResource(R.string.secret_chat_status_closed)
        SecretChatState.Ready, null -> stringResource(R.string.secret_chat_status_ready)
    }
    val ttl = details?.ttlSeconds?.takeIf { it > 0 } ?: return state
    return stringResource(R.string.secret_chat_status_ttl, state, secretChatTtlLabel(ttl))
}

/**
 * TTL and lifecycle controls for a secret chat inside the existing chat settings dialog. The TTL is TDLib's chat-wide
 * messageAutoDeleteTime and only affects new messages; view-once is a separate per-message mechanism in the composer.
 */
@Composable
internal fun SecretChatSettingsSection(
    chatId: Long,
    details: SecretChatDetails?,
    conversation: ConversationRepository,
    onSecretChatClosed: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(chatId) { mutableStateOf(false) }
    var error by remember(chatId) { mutableStateOf<String?>(null) }
    var customOpen by remember(chatId) { mutableStateOf(false) }
    var closeConfirm by remember(chatId) { mutableStateOf(false) }
    var keyOpen by remember(chatId) { mutableStateOf(false) }

    fun setTtl(seconds: Int) {
        if (busy) return
        scope.launch {
            busy = true
            error = null
            try {
                conversation.setSecretChatTtl(chatId, seconds)
                    .onSuccess { customOpen = false }
                    .onFailure { error = it.userMessage(context, R.string.secret_chat_ttl_save_failed) }
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.secret_chat_ttl_title),
            color = palette.accentInk,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(R.string.secret_chat_ttl_description),
            color = palette.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        Text(
            stringResource(R.string.secret_chat_device_notice),
            color = palette.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        TextButton(
            onClick = { keyOpen = true },
            enabled = details != null
        ) {
            Text(stringResource(R.string.secret_chat_key_action), color = palette.accent)
        }
        details?.let {
            Text(
                stringResource(R.string.secret_chat_ttl_current, secretChatTtlLabel(it.ttlSeconds)),
                color = palette.textPrimary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        SecretChatTtl.presets.forEach { seconds ->
            TtlChoice(
                label = secretChatTtlLabel(seconds),
                selected = (details?.ttlSeconds ?: SecretChatTtl.OFF) == seconds,
                enabled = !busy && details != null && details.state != SecretChatState.Closed
            ) { setTtl(seconds) }
        }
        TextButton(
            onClick = { customOpen = true },
            enabled = !busy && details != null && details.state != SecretChatState.Closed
        ) {
            Text(stringResource(R.string.secret_chat_ttl_custom), color = palette.accent)
        }
        error?.let {
            Text(
                it,
                color = palette.danger,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        HorizontalDivider(color = palette.divider, modifier = Modifier.padding(vertical = 8.dp))
        TextButton(
            onClick = { closeConfirm = true },
            enabled = !busy && details?.state != SecretChatState.Closed
        ) {
            Text(stringResource(R.string.secret_chat_close_action), color = palette.danger)
        }
    }

    if (customOpen) SecretChatTtlDialog(
        initialSeconds = details?.ttlSeconds ?: SecretChatTtl.ONE_HOUR,
        busy = busy,
        error = error,
        onDismiss = { if (!busy) customOpen = false },
        onSave = { seconds ->
            if (seconds == null) error = context.getString(R.string.secret_chat_ttl_invalid)
            else setTtl(seconds)
        }
    )
    if (closeConfirm) AlertDialog(
        onDismissRequest = { if (!busy) closeConfirm = false },
        title = { Text(stringResource(R.string.secret_chat_close_title)) },
        text = { Text(stringResource(R.string.secret_chat_close_message)) },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    if (busy) return@TextButton
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            conversation.closeSecretChat(chatId)
                                .onSuccess { closeConfirm = false; onSecretChatClosed() }
                                .onFailure { error = it.userMessage(context, R.string.secret_chat_close_failed); closeConfirm = false }
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(stringResource(R.string.secret_chat_close_confirm), color = palette.danger) }
        },
        dismissButton = {
            TextButton(onClick = { closeConfirm = false }, enabled = !busy) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
    if (keyOpen) details?.let { SecretChatKeyDialog(it, onDismiss = { keyOpen = false }) }
}

/**
 * Shows the key the way official Telegram clients do: the 12x12 picture, five emoji and the hex code. The dialog has its
 * own window, so it sets FLAG_SECURE itself instead of relying on the chat screen.
 */
@Composable
private fun SecretChatKeyDialog(details: SecretChatDetails, onDismiss: () -> Unit) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val fingerprint = remember(details.keyHash) { SecretChatKey.fingerprint(details.keyHash) }
    val emoji = remember(details.keyHash) { SecretChatKey.emoji(details.keyHash) }
    var copied by remember(details.keyHash) { mutableStateOf(false) }
    fun copy() {
        if (fingerprint.isBlank()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(context.getString(R.string.secret_chat_key_title), fingerprint)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard?.setPrimaryClip(clip)
        copied = true
        Toast.makeText(context, context.getString(R.string.secret_chat_key_copied), Toast.LENGTH_SHORT).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(stringResource(R.string.secret_chat_key_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (fingerprint.isBlank()) {
                    Text(
                        stringResource(R.string.secret_chat_key_unavailable),
                        color = palette.warningText,
                        fontSize = 13.sp
                    )
                } else {
                    SecretChatKeyImage(
                        details.keyHash,
                        Modifier.align(Alignment.CenterHorizontally).size(144.dp)
                    )
                    if (emoji.isNotEmpty()) {
                        Text(
                            emoji.joinToString("  "),
                            fontSize = 28.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                        )
                    }
                    Text(
                        stringResource(R.string.secret_chat_key_fingerprint_label),
                        color = palette.accentInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    SelectionContainer {
                        Text(
                            fingerprint,
                            color = palette.textPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(
                        stringResource(R.string.secret_chat_key_fingerprint_hint),
                        color = palette.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                Text(
                    stringResource(R.string.secret_chat_key_device_warning),
                    color = palette.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            if (fingerprint.isNotBlank()) {
                TextButton(onClick = { copy() }) {
                    Text(stringResource(if (copied) R.string.secret_chat_key_copied else R.string.secret_chat_key_copy))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

/** The key picture with Telegram's fixed colors, so it matches the contact's screen in any app theme. */
@Composable
private fun SecretChatKeyImage(keyHash: List<Byte>, modifier: Modifier = Modifier) {
    val side = remember(keyHash) { SecretChatKey.identiconSide(keyHash) }
    val pixels = remember(keyHash) { SecretChatKey.identicon(keyHash) }
    val colors = remember { SecretChatKey.identiconColors.map { Color(it) } }
    val description = stringResource(R.string.secret_chat_key_image_description)
    if (side == 0) return
    Canvas(modifier.semantics { contentDescription = description }) {
        val cell = size.minDimension / side
        val offset = Offset((size.width - cell * side) / 2f, (size.height - cell * side) / 2f)
        pixels.forEachIndexed { index, color ->
            drawRect(
                color = colors[color],
                topLeft = offset + Offset(index % side * cell, index / side * cell),
                size = Size(cell, cell)
            )
        }
    }
}

@Composable
private fun TtlChoice(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    val palette = beyGramPalette()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // One merged focus stop: the row is the radio button for TalkBack.
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, color = palette.textPrimary, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun SecretChatTtlDialog(
    initialSeconds: Int,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit
) {
    val initial = com.beykus.beygram.domain.model.ttlParts(
        initialSeconds.takeIf { it > 0 } ?: SecretChatTtl.ONE_HOUR
    )
    var days by remember { mutableStateOf(initial.days.toString()) }
    var hours by remember { mutableStateOf(initial.hours.toString()) }
    var minutes by remember { mutableStateOf(initial.minutes.toString()) }
    val seconds = SecretChatTtl.customSeconds(
        days.toIntOrNull() ?: -1,
        hours.toIntOrNull() ?: -1,
        minutes.toIntOrNull() ?: -1
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.secret_chat_ttl_custom_title)) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TtlNumberField(days, { days = it }, stringResource(R.string.secret_chat_ttl_days), Modifier.weight(1f))
                    TtlNumberField(hours, { hours = it }, stringResource(R.string.secret_chat_ttl_hours), Modifier.weight(1f))
                    TtlNumberField(minutes, { minutes = it }, stringResource(R.string.secret_chat_ttl_minutes), Modifier.weight(1f))
                }
                Text(
                    if (seconds != null) stringResource(R.string.secret_chat_ttl_current, secretChatTtlLabel(seconds))
                    else stringResource(R.string.secret_chat_ttl_invalid),
                    color = if (seconds != null) beyGramPalette().textSecondary else beyGramPalette().danger,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
                error?.let { Text(it, color = beyGramPalette().danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && seconds != null, onClick = { onSave(seconds) }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun TtlNumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(3)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
