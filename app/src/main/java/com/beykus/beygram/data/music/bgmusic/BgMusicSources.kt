package com.beykus.beygram.data.music.bgmusic

import com.beykus.beygram.domain.model.ProfileKind
import com.beykus.beygram.domain.repository.TelegramRepository
import com.beykus.beygram.playback.PlaybackItem
import com.beykus.beygram.playback.PlaybackOrigin
import com.beykus.beygram.playback.resolveMusicOrigins
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** A track of a `.bgmusic` file that can be played in the recipient's account right now. */
data class BgMusicResolvedTrack(
    val trackId: String,
    val origin: PlaybackOrigin,
    val item: PlaybackItem,
)

/** Public usernames of the account's own chats. Only a public channel or group can carry a public link. */
object BgMusicPublicLinks {
    private const val PROFILE_TIMEOUT_MS = 5_000L

    /**
     * The public username of each chat in [chatIds] that is a public channel or group. Private chats, secret chats, and groups
     * without a valid public username are left out, and so is a chat whose profile does not load in time.
     */
    suspend fun usernamesFor(repository: TelegramRepository, chatIds: Collection<Long>): Map<Long, String> {
        val found = LinkedHashMap<Long, String>()
        for (chatId in chatIds.distinct()) {
            // Negative ids are groups and channels. Positive ids are people and bots, which never carry a public link.
            if (chatId >= 0L) continue
            val username = usernameOf(repository, chatId) ?: continue
            found[chatId] = username
        }
        return found
    }

    private suspend fun usernameOf(repository: TelegramRepository, chatId: Long): String? {
        repository.refreshChatInfo(chatId)
        val state = withTimeoutOrNull(PROFILE_TIMEOUT_MS) {
            repository.observeChatInfo(chatId).first { !it.isLoading && (it.info != null || it.error != null) }
        } ?: return null
        val info = state.info ?: return null
        if (info.kind != ProfileKind.Channel && info.kind != ProfileKind.Group) return null
        return (info.usernames + info.username).firstNotNullOfOrNull { BgMusicFormat.normalizeTelegramUsername(it) }
    }
}

/** Finds the tracks of a file in the recipient's own chats, through the public source hints the file carries. */
object BgMusicSourceResolver {

    /**
     * For each track of [manifest] that has public hints, the first hint that resolves to a playable post in [accountId]. Hints are
     * tried one round at a time, so a dead first hint falls back to the next one. Tracks that cannot be found are left out. The map
     * is keyed by track id.
     */
    suspend fun resolve(
        repository: TelegramRepository,
        accountId: String,
        manifest: BgMusicManifest,
    ): Map<String, BgMusicResolvedTrack> {
        val chatIdByUsername = HashMap<String, Long?>()
        val found = LinkedHashMap<String, BgMusicResolvedTrack>()
        val rounds = manifest.tracks.maxOfOrNull { it.sources.size } ?: 0
        for (round in 0 until rounds) {
            val owners = ArrayList<String>()
            val origins = ArrayList<PlaybackOrigin?>()
            for (track in manifest.tracks) {
                if (track.id in found || round >= track.sources.size) continue
                val hint = track.sources[round] as? BgMusicSourceHint.TelegramPublic ?: continue
                val chatId = chatIdFor(repository, hint.username, chatIdByUsername) ?: continue
                owners += track.id
                origins += PlaybackOrigin(
                    chatId = chatId,
                    messageId = hint.messageId shl BgMusicLibraryMapping.TDLIB_MESSAGE_ID_SHIFT,
                    accountId = accountId,
                )
            }
            if (owners.isEmpty()) continue
            val items = resolveMusicOrigins(repository, accountId, origins)
            owners.forEachIndexed { index, trackId ->
                val item = items.getOrNull(index) ?: return@forEachIndexed
                val origin = origins[index] ?: return@forEachIndexed
                found[trackId] = BgMusicResolvedTrack(trackId, origin, item)
            }
        }
        return found
    }

    /** Resolves a public username once per call. A username that does not resolve is remembered as null. */
    private suspend fun chatIdFor(
        repository: TelegramRepository,
        username: String,
        known: MutableMap<String, Long?>,
    ): Long? {
        if (known.containsKey(username)) return known[username]
        val chatId = repository.resolvePublicChat(username).getOrNull()
        known[username] = chatId
        return chatId
    }
}
