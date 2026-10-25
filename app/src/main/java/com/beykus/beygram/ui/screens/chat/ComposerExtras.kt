package com.beykus.beygram.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ContactPreview
import com.beykus.beygram.domain.model.OutgoingPoll
import com.beykus.beygram.domain.model.PollDraftProblem
import com.beykus.beygram.ui.components.beyGramPalette

/** Composes a regular poll or a quiz. The result is placed in the attachment panel, so reply/send options still apply. */
@Composable
internal fun PollComposerDialog(onDismiss: () -> Unit, onSend: (OutgoingPoll) -> Unit) {
    val palette = beyGramPalette()
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var quiz by remember { mutableStateOf(false) }
    var anonymous by remember { mutableStateOf(true) }
    var multiple by remember { mutableStateOf(false) }
    var correct by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var explanation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<PollDraftProblem?>(null) }
    val questionLength = question.codePointCount(0, question.length)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.poll_composer_title)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { if (it.codePointCount(0, it.length) <= OutgoingPoll.MAX_POLL_QUESTION_LENGTH) question = it },
                    label = { Text(stringResource(R.string.poll_composer_question)) },
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.poll_composer_counter, questionLength, OutgoingPoll.MAX_POLL_QUESTION_LENGTH),
                    color = palette.textSecondary, fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
                Spacer(Modifier.size(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !quiz, onClick = { quiz = false }, label = { Text(stringResource(R.string.chat_poll_title)) })
                    FilterChip(selected = quiz, onClick = { quiz = true; anonymous = true; if (!multiple) correct = correct.take(1).toSet() }, label = { Text(stringResource(R.string.chat_poll_quiz)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = anonymous, onCheckedChange = { anonymous = it }, enabled = !quiz)
                    Text(stringResource(R.string.poll_composer_anonymous), color = palette.textPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = multiple, onCheckedChange = {
                        multiple = it
                        if (!it) correct = correct.take(1).toSet()
                    })
                    Text(stringResource(R.string.poll_composer_multiple), color = palette.textPrimary)
                }
                options.forEachIndexed { index, option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = option,
                            onValueChange = { value ->
                                if (value.codePointCount(0, value.length) <= OutgoingPoll.MAX_POLL_OPTION_LENGTH) {
                                    options = options.toMutableList().also { it[index] = value }
                                }
                            },
                            label = { Text(stringResource(R.string.poll_composer_option, index + 1)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (quiz) {
                            val correctLabel = stringResource(R.string.poll_composer_correct)
                            IconButton(
                                onClick = {
                                    correct = if (index in correct) correct - index else if (multiple) correct + index else setOf(index)
                                },
                                modifier = Modifier.size(48.dp).semantics {
                                    contentDescription = correctLabel
                                    role = Role.Checkbox
                                }
                            ) {
                                Icon(
                                    if (index in correct) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (index in correct) palette.successText else palette.textSecondary
                                )
                            }
                        }
                        if (options.size > OutgoingPoll.MIN_POLL_OPTIONS) IconButton(
                            onClick = {
                                options = options.toMutableList().also { it.removeAt(index) }
                                correct = correct.filter { it != index }.map { if (it > index) it - 1 else it }.toSet()
                            },
                            modifier = Modifier.size(48.dp)
                        ) { Icon(Icons.Rounded.Close, stringResource(R.string.poll_composer_remove_option), tint = palette.textSecondary) }
                    }
                }
                if (options.size < OutgoingPoll.MAX_POLL_OPTIONS) TextButton(
                    onClick = { options = options + "" },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = palette.accent)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.poll_composer_add_option), color = palette.accent)
                }
                if (quiz) OutlinedTextField(
                    value = explanation,
                    onValueChange = { if (it.length <= 200) explanation = it },
                    label = { Text(stringResource(R.string.poll_composer_explanation)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(pollProblemText(it), color = palette.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val poll = OutgoingPoll(
                        question = question,
                        options = options,
                        isAnonymous = anonymous,
                        allowsMultipleAnswers = multiple,
                        isQuiz = quiz,
                        correctOptionIndices = if (quiz) correct else emptySet(),
                        explanation = explanation
                    )
                    val problem = poll.problem()
                    if (problem != null) error = problem else onSend(poll)
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) { Text(stringResource(R.string.poll_composer_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun pollProblemText(problem: PollDraftProblem): String = when (problem) {
    PollDraftProblem.QuestionEmpty -> stringResource(R.string.poll_error_question_empty)
    PollDraftProblem.QuestionTooLong -> stringResource(R.string.poll_error_question_long, OutgoingPoll.MAX_POLL_QUESTION_LENGTH)
    PollDraftProblem.TooFewOptions -> stringResource(R.string.poll_error_options_few)
    PollDraftProblem.TooManyOptions -> stringResource(R.string.poll_error_options_many, OutgoingPoll.MAX_POLL_OPTIONS)
    PollDraftProblem.OptionEmpty -> stringResource(R.string.poll_error_option_empty)
    PollDraftProblem.OptionTooLong -> stringResource(R.string.poll_error_option_long, OutgoingPoll.MAX_POLL_OPTION_LENGTH)
    PollDraftProblem.CorrectAnswerMissing -> stringResource(R.string.poll_error_correct_missing)
    PollDraftProblem.CorrectAnswerInvalid -> stringResource(R.string.poll_error_correct_invalid)
    PollDraftProblem.CorrectAnswerCount -> stringResource(R.string.poll_error_correct_count)
}

/** A searchable picker over the account's Telegram address book. Only the chosen user id is sent to TDLib. */
@Composable
internal fun ContactPickerDialog(
    contacts: List<ContactPreview>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onPick: (ContactPreview) -> Unit
) {
    val palette = beyGramPalette()
    var query by remember { mutableStateOf("") }
    val shown = remember(contacts, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) contacts else contacts.filter {
            it.title.lowercase().contains(needle) || it.phone.contains(needle) || it.subtitle.lowercase().contains(needle)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contact_picker_title)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.contact_picker_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = palette.accent, strokeWidth = 2.dp)
                    }
                    error != null -> Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(error, color = palette.danger, fontSize = 13.sp)
                        TextButton(onClick = onRetry, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                            Text(stringResource(R.string.common_retry), color = palette.accent)
                        }
                    }
                    shown.isEmpty() -> Text(stringResource(R.string.contact_picker_empty), color = palette.textSecondary,
                        modifier = Modifier.padding(12.dp))
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(shown, key = { it.userId }) { contact ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp))
                                    .clickable(role = Role.Button) { onPick(contact) }.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(36.dp).clip(CircleShape).background(palette.accentSoft), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Person, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(contact.title, color = palette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (contact.subtitle.isNotBlank()) Text(contact.subtitle, color = palette.textSecondary, fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } }
    )
}

@Composable
internal fun DicePickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val palette = beyGramPalette()
    val choices = listOf("🎲", "🎯", "🏀", "⚽", "🎳", "🎰")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dice_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { emoji ->
                            val description = stringResource(R.string.dice_picker_description, emoji)
                            Box(
                                Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(palette.accentSoft)
                                    .clickable(role = Role.Button) { onPick(emoji) }
                                    .semantics { contentDescription = description },
                                contentAlignment = Alignment.Center
                            ) { Text(emoji, fontSize = 32.sp) }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } }
    )
}
