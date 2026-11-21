package com.beykus.beygram.ui.screens.playback

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beykus.beygram.R
import com.beykus.beygram.data.music.provider.ProviderTrack
import com.beykus.beygram.data.music.provider.ProviderTracks
import com.beykus.beygram.deeplink.YandexMusicTarget
import com.beykus.beygram.deeplink.yandexMusicTarget
import com.beykus.beygram.share.isShareOutcomeUnknown
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.screens.chat.ForwardChatDialog
import com.beykus.beygram.ui.screens.chat.ForwardTarget
import com.beykus.beygram.ui.screens.music.rememberProviderCover

/** Telegram allows 4096 code points in a text message. */
private const val MAX_SHARED_TEXT_LENGTH = 4096

/** No CDN address, local file, provider access token or mutable player state belongs in this snapshot. */
internal data class ProviderLinkSharePayload(
    val title: String,
    val performer: String,
    val durationMs: Long,
    val coverUrl: String?,
    val publicLink: String,
    val text: String
)

internal data class PendingProviderLinkShare(
    val payload: ProviderLinkSharePayload,
    val source: PlaybackRepositorySource
)

/** Accept only web links already supported by the app's Yandex parser. */
internal fun providerTrackSharePayload(track: ProviderTrack): ProviderLinkSharePayload? {
    val link = ProviderTracks.webLink(track)
    val parsed = yandexMusicTarget(link) as? YandexMusicTarget.Track ?: return null
    if (parsed.trackId != track.trackId) return null
    val title = track.title.trim()
    val performer = track.performer.trim()
    val heading = listOf(performer, title).filter { it.isNotBlank() }.joinToString(" — ")
    val text = if (heading.isEmpty()) link else "$heading\n$link"
    if (text.codePointCount(0, text.length) > MAX_SHARED_TEXT_LENGTH) return null
    return ProviderLinkSharePayload(title, performer, track.durationMs, track.coverUrl, link, text)
}

internal fun providerLinkCommentLimit(payload: ProviderLinkSharePayload): Int =
    (MAX_SHARED_TEXT_LENGTH - payload.text.codePointCount(0, payload.text.length) - 2).coerceAtLeast(0)

/** The same immutable text is used for TDLib and the Android chooser; recipient names are never included. */
internal fun providerLinkMessage(payload: ProviderLinkSharePayload, comment: String): String {
    val note = comment.trim()
    return if (note.isEmpty()) payload.text else "$note\n\n${payload.text}"
}

/**
 * Send a public Yandex Music link as ordinary Telegram text. Reuses the existing recipient/topic picker,
 * partial-failure selection and unknown-outcome guard; never uploads the provider's audio file.
 */
@Composable
internal fun ShareProviderLinkDialog(share: PendingProviderLinkShare, onClose: () -> Unit) {
    val repository = share.source.repository
    val payload = share.payload
    val context = LocalContext.current
    val selectedSource = LocalPlaybackRepositorySource.current
    val selectedAccountMatches = selectedSource?.let {
        it.accountId == share.source.accountId && it.repository === repository
    } == true
    val chats by repository.chats.collectAsStateWithLifecycle()
    val archivedChats by repository.archivedChats.collectAsStateWithLifecycle()
    var sending by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var retryBlocked by remember { mutableStateOf(false) }
    val failedText = stringResource(R.string.share_send_failed)
    val chooserTitle = stringResource(R.string.music_provider_share_link)
    val sourceName = stringResource(R.string.player_source_provider_yandex)

    // An account switch before sending must not reuse old-chat selections on the new account.
    LaunchedEffect(selectedAccountMatches, sending) {
        if (!selectedAccountMatches && !sending) onClose()
    }

    val preview = listOfNotNull(
        payload.title.takeIf { it.isNotBlank() },
        payload.performer.takeIf { it.isNotBlank() },
        sourceName,
        shareTimecode(payload.durationMs).takeIf { payload.durationMs > 0L }
    ).joinToString(" · ")

    ForwardChatDialog(
        chats = chats + archivedChats,
        recipientRepository = repository,
        candidatePolicy = ::shareTargetChats,
        busy = sending || !selectedAccountMatches,
        error = failure,
        retryBlocked = retryBlocked,
        observeFile = repository::observeFile,
        downloadFile = repository::downloadFile,
        titleOverride = stringResource(R.string.player_share),
        previewText = preview,
        previewArtwork = {
            val palette = beyGramPalette()
            TrackArtwork(
                artworkPath = rememberProviderCover(payload.coverUrl),
                audioPath = null,
                modifier = Modifier.size(56.dp),
                background = palette.accentSoft,
                tint = palette.accentInk,
                symbolSize = 28.dp,
                cornerRadius = 12.dp
            )
        },
        sendLabel = stringResource(R.string.share_send),
        commentLimit = providerLinkCommentLimit(payload),
        loadTopics = { chatId -> repository.forumTopics(chatId) },
        onExternalShare = { comment ->
            // Keep the picker, its selection and the comment even if the chooser is cancelled.
            if (!ProviderTracks.sharePublicText(context, providerLinkMessage(payload, comment), chooserTitle)) {
                Toast.makeText(context, R.string.share_external_unavailable, Toast.LENGTH_SHORT).show()
            }
        },
        onSend = { targets, comment ->
            if (!selectedAccountMatches) {
                failure = failedText
                targets
            } else {
                sending = true
                failure = null
                try {
                    val message = providerLinkMessage(payload, comment)
                    val allowedChats = shareTargetChats(
                        (repository.chats.value + repository.archivedChats.value +
                            repository.folderChats.value.values.flatten()).distinctBy { it.id }
                    ).mapTo(mutableSetOf()) { it.id }
                    val failed = mutableListOf<ForwardTarget>()
                    for (target in targets) {
                        val result = if (target.chatId in allowedChats)
                            repository.sendMessage(
                                target.chatId, message,
                                forumTopicId = target.topicId,
                                clearDraft = false
                            )
                        else Result.failure(IllegalStateException("Recipient not writable"))
                        if (result.isFailure) {
                            failed += target
                            if (isShareOutcomeUnknown(result.exceptionOrNull())) retryBlocked = true
                        }
                    }
                    if (failed.isNotEmpty()) failure = failedText
                    failed
                } finally {
                    sending = false
                }
            }
        },
        onClose = onClose
    )
}
