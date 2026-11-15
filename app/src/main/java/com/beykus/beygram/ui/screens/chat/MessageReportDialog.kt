package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import com.beykus.beygram.ui.components.BeyGramDialog
import com.beykus.beygram.ui.components.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.MessageReportReason
import com.beykus.beygram.ui.components.beyGramPalette

/** Asks for the reason of a message report. Nothing is sent until the user taps one of the reasons. */
@Composable
internal fun MessageReportDialog(onSelect: (MessageReportReason) -> Unit, onDismiss: () -> Unit) {
    val palette = beyGramPalette()
    BeyGramDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.message_report_title),
        content = {
            Column {
                MessageReportReason.entries.forEach { reason ->
                    TextButton(onClick = { onSelect(reason) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(reason.labelRes()), color = palette.textPrimary)
                    }
                }
            }
        },
        // The reasons are the content; Cancel is the only action, so it takes the confirm slot.
        confirmText = stringResource(R.string.common_cancel),
        onConfirm = onDismiss
    )
}

private fun MessageReportReason.labelRes(): Int = when (this) {
    MessageReportReason.Spam -> R.string.message_report_reason_spam
    MessageReportReason.Violence -> R.string.message_report_reason_violence
    MessageReportReason.PersonalDetails -> R.string.message_report_reason_personal
}
