package com.beykus.beygram.ui.screens.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.GeoPoint
import com.beykus.beygram.domain.model.MessageContactInfo
import com.beykus.beygram.domain.model.MessageDiceInfo
import com.beykus.beygram.domain.model.MessageLiveLocationInfo
import com.beykus.beygram.domain.model.MessageLocationInfo
import com.beykus.beygram.domain.model.MessagePoll
import com.beykus.beygram.domain.model.MessagePollOption
import com.beykus.beygram.domain.model.MessageSpecialContent
import com.beykus.beygram.domain.model.MessageVenueInfo
import com.beykus.beygram.domain.model.OutgoingPoll
import com.beykus.beygram.domain.model.PollOptionAddProblem
import com.beykus.beygram.domain.model.PollVoteRestriction
import com.beykus.beygram.domain.model.PollVoterInfo
import com.beykus.beygram.domain.model.PollVotersPage
import com.beykus.beygram.domain.model.appendPollVoters
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.text.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Renders everything that is not a regular media message. Polls and locations are interactive; the unsupported
 * placeholder guarantees that an unknown content type never becomes an empty bubble with only a timestamp.
 */
@Composable
internal fun SpecialMessageContentView(
    special: MessageSpecialContent,
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onPollVote: ((Long, List<Int>) -> Unit)? = null,
    onPollStop: ((Long) -> Unit)? = null,
    onStopLiveLocation: ((ChatMessage) -> Unit)? = null,
    onWriteContact: ((Long) -> Unit)? = null,
    onLoadPollVoters: (suspend (messageId: Long, optionIndex: Int, offset: Int) -> Result<PollVotersPage>)? = null,
    onAddPollOption: (suspend (messageId: Long, text: String) -> Result<Unit>)? = null
) {
    when (special) {
        is MessageSpecialContent.Poll -> PollCard(special.poll, message, modifier, onPollVote, onPollStop, onLoadPollVoters, onAddPollOption)
        is MessageSpecialContent.Location -> LocationCard(special.location, modifier)
        is MessageSpecialContent.LiveLocation -> LiveLocationCard(special.location, message, modifier, onStopLiveLocation)
        is MessageSpecialContent.Venue -> VenueCard(special.venue, modifier)
        is MessageSpecialContent.Contact -> ContactCard(special.contact, modifier, onWriteContact)
        is MessageSpecialContent.Dice -> DiceCard(special.dice, modifier)
        is MessageSpecialContent.Unsupported -> UnsupportedMessageCard(modifier)
    }
}

@Composable
private fun PollCard(
    poll: MessagePoll,
    message: ChatMessage,
    modifier: Modifier,
    onVote: ((Long, List<Int>) -> Unit)?,
    onStop: ((Long) -> Unit)?,
    onLoadVoters: (suspend (messageId: Long, optionIndex: Int, offset: Int) -> Result<PollVotersPage>)?,
    onAddOption: (suspend (messageId: Long, text: String) -> Result<Unit>)?
) {
    val palette = beyGramPalette()
    val chosen = poll.chosenOptionIndices
    var selected by remember(poll.id) { mutableStateOf(chosen.toSet()) }
    LaunchedEffect(chosen, poll.isClosed) { selected = chosen.toSet() }
    var confirmStop by remember(poll.id) { mutableStateOf(false) }
    var votersOpen by remember(poll.id) { mutableStateOf(false) }
    var addOptionOpen by remember(poll.id) { mutableStateOf(false) }
    val pending = poll.options.any { it.isBeingChosen }
    val canInteract = poll.canVote && onVote != null && !pending
    val showResults = poll.revealsResults
    val canShowVoters = poll.canShowVoters && onLoadVoters != null
    val canAddOption = poll.canAddOption && !poll.isClosed && onAddOption != null
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Poll, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(if (poll.isQuiz) R.string.chat_poll_quiz else R.string.chat_poll_title),
                color = palette.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            if (poll.isAnonymous) {
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.chat_poll_anonymous_short), color = palette.messageTime, fontSize = 11.sp)
            }
        }
        Text(
            poll.question.ifBlank { stringResource(R.string.chat_poll_untitled) },
            color = palette.messageText, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (poll.description.isNotBlank()) Text(
            poll.description, color = palette.messageTime, fontSize = 13.sp, lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(6.dp))
        poll.options.forEachIndexed { index, option ->
            PollOptionRow(
                option = option,
                index = index,
                poll = poll,
                selected = option.optionIndex in selected,
                showResults = showResults,
                correct = poll.isCorrect(option.optionIndex) && (poll.isClosed || poll.hasVoted),
                enabled = canInteract,
                onClick = {
                    if (!canInteract) return@PollOptionRow
                    if (poll.allowsMultipleAnswers) {
                        selected = if (option.optionIndex in selected) selected - option.optionIndex else selected + option.optionIndex
                    } else {
                        selected = setOf(option.optionIndex)
                        onVote?.invoke(message.id, listOf(option.optionIndex))
                    }
                }
            )
        }
        if (poll.allowsMultipleAnswers && canInteract) {
            val canSubmit = selected.isNotEmpty() && selected != chosen.toSet()
            TextButton(
                onClick = { if (canSubmit) onVote?.invoke(message.id, selected.sorted()) },
                enabled = canSubmit,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    if (poll.hasVoted) stringResource(R.string.chat_poll_change_vote) else stringResource(R.string.chat_poll_vote),
                    color = if (canSubmit) palette.accent else palette.textSecondary
                )
            }
        }
        PollFooter(poll, showResults)
        if (canShowVoters) TextButton(
            onClick = { votersOpen = true },
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.chat_poll_voters_button), color = palette.accent)
        }
        if (canAddOption) TextButton(
            onClick = { addOptionOpen = true },
            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
            Text(stringResource(R.string.chat_poll_add_option), color = palette.accent)
        }
        if (poll.isQuiz && showResults && poll.explanation.isNotBlank()) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = palette.divider)
            Text(poll.explanation, color = palette.messageText, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (message.isOutgoing && !poll.isClosed && onStop != null) {
            TextButton(onClick = { confirmStop = true }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text(stringResource(R.string.chat_poll_stop), color = palette.danger)
            }
        }
    }
    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text(stringResource(R.string.chat_poll_stop_title)) },
        text = { Text(stringResource(R.string.chat_poll_stop_text)) },
        confirmButton = {
            TextButton(onClick = { confirmStop = false; onStop?.invoke(message.id) }) {
                Text(stringResource(R.string.chat_poll_stop_confirm), color = palette.danger)
            }
        },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.common_cancel)) } }
    )
    val loadVoters = onLoadVoters
    val addOption = onAddOption
    if (votersOpen && loadVoters != null) PollVotersDialog(
        poll = poll,
        onDismiss = { votersOpen = false },
        load = { optionIndex, offset -> loadVoters.invoke(message.id, optionIndex, offset) }
    )
    if (addOptionOpen && addOption != null) AddPollOptionDialog(
        poll = poll,
        onDismiss = { addOptionOpen = false },
        onAdd = { text -> addOption.invoke(message.id, text) }
    )
}

@Composable
private fun PollVotersDialog(
    poll: MessagePoll,
    onDismiss: () -> Unit,
    load: suspend (optionIndex: Int, offset: Int) -> Result<PollVotersPage>
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedOption by remember(poll.id) {
        mutableIntStateOf(poll.options.firstOrNull { it.voterCount > 0 }?.optionIndex ?: poll.options.firstOrNull()?.optionIndex ?: 0)
    }
    var voters by remember(poll.id) { mutableStateOf<List<PollVoterInfo>>(emptyList()) }
    var totalCount by remember(poll.id) { mutableIntStateOf(0) }
    var nextOffset by remember(poll.id) { mutableIntStateOf(0) }
    var loading by remember(poll.id) { mutableStateOf(false) }
    var error by remember(poll.id) { mutableStateOf<String?>(null) }
    var generation by remember(poll.id) { mutableIntStateOf(0) }

    fun loadPage(reset: Boolean) {
        val request = ++generation
        if (reset) {
            voters = emptyList()
            totalCount = 0
            nextOffset = 0
            error = null
        }
        val offset = if (reset) 0 else nextOffset
        loading = true
        scope.launch {
            val result = load(selectedOption, offset)
            if (request != generation) return@launch
            loading = false
            result.onSuccess { page ->
                voters = if (reset) page.voters else appendPollVoters(voters, page)
                totalCount = page.totalCount
                nextOffset = page.nextOffset
            }.onFailure { failure ->
                error = failure.userMessage(context, R.string.chat_poll_voters_failed)
            }
        }
    }
    LaunchedEffect(selectedOption) { loadPage(reset = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_poll_voters_title)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp)) {
                if (poll.options.size > 1) Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    poll.options.forEach { option ->
                        FilterChip(
                            selected = option.optionIndex == selectedOption,
                            onClick = { selectedOption = option.optionIndex },
                            label = { Text(option.text.take(28), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
                when {
                    loading && voters.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = palette.accent, strokeWidth = 2.dp)
                    }
                    error != null && voters.isEmpty() -> Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(error.orEmpty(), color = palette.danger, fontSize = 13.sp)
                        TextButton(onClick = { loadPage(reset = true) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                            Text(stringResource(R.string.common_retry), color = palette.accent)
                        }
                    }
                    voters.isEmpty() -> Text(
                        stringResource(R.string.chat_poll_voters_empty), color = palette.textSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        itemsIndexed(voters, key = { index, _ -> "$selectedOption:$index" }) { _, voter ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(32.dp).clip(CircleShape).background(palette.accentSoft), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Person, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(voter.name, color = palette.textPrimary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (loading && voters.isNotEmpty()) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = palette.accent, strokeWidth = 2.dp)
                }
                if (error != null && voters.isNotEmpty()) {
                    Text(error.orEmpty(), color = palette.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    TextButton(onClick = { loadPage(reset = false) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(stringResource(R.string.common_retry), color = palette.accent)
                    }
                }
                if (!loading && nextOffset >= 0 && (totalCount <= 0 || voters.size < totalCount)) TextButton(
                    onClick = { loadPage(reset = false) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(stringResource(R.string.chat_poll_voters_more), color = palette.accent)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } }
    )
}

@Composable
private fun AddPollOptionDialog(
    poll: MessagePoll,
    onDismiss: () -> Unit,
    onAdd: suspend (String) -> Result<Unit>
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<PollOptionAddProblem?>(null) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.chat_poll_add_option_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        if (it.codePointCount(0, it.length) <= OutgoingPoll.MAX_POLL_OPTION_LENGTH) {
                            text = it
                            problem = null
                            serverError = null
                        }
                    },
                    label = { Text(stringResource(R.string.chat_poll_add_option_hint)) },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                )
                val shownError = problem?.let { pollOptionProblemText(it) } ?: serverError
                if (shownError != null) Text(shownError, color = palette.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val next = poll.addOptionProblem(text)
                    if (next != null) {
                        problem = next
                        return@TextButton
                    }
                    scope.launch {
                        busy = true
                        serverError = null
                        val result = onAdd(text.trim())
                        busy = false
                        if (result.isSuccess) onDismiss()
                        else serverError = result.exceptionOrNull()
                            .userMessage(context, R.string.chat_poll_add_option_failed)
                    }
                },
                enabled = !busy,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = palette.accent)
                else Text(stringResource(R.string.chat_poll_add_option_send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun pollOptionProblemText(problem: PollOptionAddProblem): String = when (problem) {
    PollOptionAddProblem.Empty -> stringResource(R.string.poll_error_option_empty)
    PollOptionAddProblem.TooLong -> stringResource(R.string.poll_error_option_long, OutgoingPoll.MAX_POLL_OPTION_LENGTH)
    PollOptionAddProblem.Duplicate -> stringResource(R.string.poll_option_error_duplicate)
    PollOptionAddProblem.LimitReached -> stringResource(R.string.poll_error_options_many, OutgoingPoll.MAX_POLL_OPTIONS)
}

@Composable
private fun PollOptionRow(
    option: MessagePollOption,
    index: Int,
    poll: MessagePoll,
    selected: Boolean,
    showResults: Boolean,
    correct: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    val percent = poll.percentageOf(index)
    val stateText = when {
        correct -> stringResource(R.string.chat_poll_correct)
        option.isChosen -> stringResource(R.string.chat_poll_your_answer)
        selected && poll.allowsMultipleAnswers -> stringResource(R.string.chat_poll_selected)
        else -> null
    }
    val a11y = if (showResults) context.getString(R.string.a11y_poll_option, option.text, percent) else option.text
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (option.isChosen || selected) palette.accentSoft else palette.surface2)
            .clickable(enabled = enabled, role = if (poll.allowsMultipleAnswers) Role.Checkbox else Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = a11y
                role = if (poll.allowsMultipleAnswers) Role.Checkbox else Role.RadioButton
                stateDescription = stateText.orEmpty()
            },
        contentAlignment = Alignment.CenterStart
    ) {
        if (showResults) LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.matchParentSize(),
            color = palette.accent.copy(alpha = 0.20f),
            trackColor = Color.Transparent
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (correct) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = palette.successText, modifier = Modifier.size(18.dp))
            else if (selected || option.isChosen) Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(18.dp)
            )
            if (correct || selected || option.isChosen) Spacer(Modifier.width(8.dp))
            Text(option.text, color = palette.messageText, fontSize = 14.sp, lineHeight = 19.sp,
                modifier = Modifier.weight(1f))
            if (option.isBeingChosen) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.accentInk)
            if (showResults) {
                Spacer(Modifier.width(8.dp))
                Text("$percent%", color = palette.messageTime, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PollFooter(poll: MessagePoll, showResults: Boolean) {
    val palette = beyGramPalette()
    val restriction = when (poll.voteRestriction) {
        PollVoteRestriction.None -> null
        PollVoteRestriction.Closed -> R.string.chat_poll_restriction_closed
        PollVoteRestriction.CountryRestricted -> R.string.chat_poll_restriction_country
        PollVoteRestriction.MembershipRequired -> R.string.chat_poll_restriction_membership
        PollVoteRestriction.Other -> R.string.chat_poll_restriction_other
        PollVoteRestriction.Scheduled -> R.string.chat_poll_restriction_scheduled
        PollVoteRestriction.YetUnsent -> R.string.chat_poll_restriction_unsent
    }
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (poll.isClosed) Text(stringResource(R.string.chat_poll_closed), color = palette.messageTime, fontSize = 12.sp)
        else if (poll.allowsMultipleAnswers) Text(stringResource(R.string.chat_poll_multiple_short), color = palette.messageTime, fontSize = 12.sp)
        if (showResults && poll.totalVoterCount > 0) {
            if (poll.isClosed || poll.allowsMultipleAnswers) Text(" · ", color = palette.messageTime, fontSize = 12.sp)
            Text(pluralStringResource(R.plurals.chat_poll_votes, poll.totalVoterCount, poll.totalVoterCount),
                color = palette.messageTime, fontSize = 12.sp)
        }
    }
    restriction?.let { Text(stringResource(it), color = palette.warningText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
}

@Composable
private fun LocationCard(location: MessageLocationInfo, modifier: Modifier) {
    val palette = beyGramPalette()
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LocationBadge(live = false)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.chat_location_title), color = palette.messageText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                CoordinatesLine(location.point)
            }
        }
        OpenMapButton(location.point, label = "")
    }
}

@Composable
private fun LiveLocationCard(
    location: MessageLiveLocationInfo,
    message: ChatMessage,
    modifier: Modifier,
    onStop: ((ChatMessage) -> Unit)?
) {
    val palette = beyGramPalette()
    var now by remember(location.expiresAtSeconds) { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(location.expiresAtSeconds) {
        while (true) {
            now = System.currentTimeMillis() / 1000L
            delay(30_000L)
        }
    }
    val remaining = (location.expiresAtSeconds - now).coerceAtLeast(0L)
    val remainingMinutes = ((remaining + 59) / 60).toInt()
    val active = location.expiresAtSeconds == 0L || remaining > 0L
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LocationBadge(live = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (active) R.string.chat_location_live else R.string.chat_location_live_ended),
                    color = if (active) palette.successText else palette.messageTime,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                )
                if (active) {
                    val minutes = if (location.expiresAtSeconds > 0L) remainingMinutes
                    else (location.livePeriodSeconds / 60).coerceAtLeast(1)
                    Text(pluralStringResource(R.plurals.chat_location_live_remaining, minutes, minutes),
                        color = palette.messageTime, fontSize = 12.sp)
                }
                CoordinatesLine(location.point)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OpenMapButton(location.point, label = "", compact = true)
            if (message.isOutgoing && active && onStop != null) {
                TextButton(onClick = { onStop.invoke(message) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, tint = palette.danger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.chat_location_stop), color = palette.danger)
                }
            }
        }
    }
}

@Composable
private fun VenueCard(venue: MessageVenueInfo, modifier: Modifier) {
    val palette = beyGramPalette()
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            LocationBadge(live = false)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(venue.title.ifBlank { stringResource(R.string.chat_location_venue) }, color = palette.messageText,
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (venue.address.isNotBlank()) Text(venue.address, color = palette.messageTime, fontSize = 13.sp,
                    lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (venue.provider.isNotBlank()) Text(venue.provider, color = palette.messageTime, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                CoordinatesLine(venue.point)
            }
        }
        OpenMapButton(venue.point, label = venue.title)
    }
}

@Composable
private fun ContactCard(contact: MessageContactInfo, modifier: Modifier, onWrite: ((Long) -> Unit)?) {
    val palette = beyGramPalette()
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(palette.accentSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = palette.accentInk, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.displayName.ifBlank { stringResource(R.string.chat_contact_title) }, color = palette.messageText,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (contact.phone.isNotBlank()) Text(contact.phone, color = palette.messageTime, fontSize = 13.sp)
        }
        if (contact.userId > 0L && onWrite != null) {
            TextButton(onClick = { onWrite.invoke(contact.userId) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text(stringResource(R.string.chat_contact_write), color = palette.accent)
            }
        }
    }
}

@Composable
private fun DiceCard(dice: MessageDiceInfo, modifier: Modifier) {
    val palette = beyGramPalette()
    val motion = systemMotionEnabled()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(dice.value, dice.emoji, motion) {
        if (motion && dice.value > 0) {
            scale.snapTo(0.72f)
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f))
        } else {
            scale.snapTo(1f)
        }
    }
    val result = if (dice.value > 0) stringResource(R.string.chat_dice_result, dice.value) else stringResource(R.string.chat_dice_rolling)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = result
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(dice.emoji.ifBlank { "🎲" }, fontSize = 64.sp, modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
        Text(result, color = palette.messageTime, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** The explicit fallback that keeps unknown/future/paid message types readable instead of leaving an empty bubble. */
@Composable
internal fun UnsupportedMessageCard(modifier: Modifier = Modifier) {
    val palette = beyGramPalette()
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(stringResource(R.string.chat_unsupported_message), color = palette.messageText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.chat_unsupported_hint), color = palette.messageTime, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun LocationBadge(live: Boolean) {
    val palette = beyGramPalette()
    Box(Modifier.size(40.dp).clip(CircleShape).background(if (live) palette.successSurface else palette.accentSoft), contentAlignment = Alignment.Center) {
        Icon(if (live) Icons.Rounded.MyLocation else Icons.Rounded.LocationOn, contentDescription = null,
            tint = if (live) palette.successText else palette.accentInk, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CoordinatesLine(point: GeoPoint) {
    val palette = beyGramPalette()
    Text(point.formatted(), color = palette.messageTime, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    if (point.accuracyMeters > 0) Text(
        stringResource(R.string.chat_location_accuracy, point.accuracyMeters.toInt()),
        color = palette.messageTime, fontSize = 11.sp
    )
}

@Composable
private fun OpenMapButton(point: GeoPoint, label: String, compact: Boolean = false) {
    val palette = beyGramPalette()
    val context = LocalContext.current
    TextButton(
        onClick = { if (!openMap(context, point, label)) Toast.makeText(context, R.string.chat_location_no_map_app, Toast.LENGTH_SHORT).show() },
        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Icon(Icons.Rounded.Map, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.chat_location_open_map), color = palette.accent, fontSize = if (compact) 13.sp else 14.sp)
    }
}

/** Opens an installed map app through the geo: scheme, then falls back to a browser map. */
internal fun openMap(context: Context, point: GeoPoint, label: String): Boolean {
    if (!point.isValid) return false
    val coordinates = "${point.latitude},${point.longitude}"
    val labelPart = label.takeIf { it.isNotBlank() }?.let { "(${Uri.encode(it.take(120))})" }.orEmpty()
    val geo = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$coordinates?q=$coordinates$labelPart")))
    }.isSuccess
    if (geo) return true
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(coordinates)}"))
        )
    }.isSuccess
}
