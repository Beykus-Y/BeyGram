package com.beykus.beygram.playback

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_RESTORED_MUSIC_ITEMS = 80
private const val SESSION_VERSION = 1

/**
 * Only Telegram message identities are persisted. Paths, file IDs, artwork and secret media are never written.
 * Each restored identity must be resolved against the owning account's current TDLib message and permissions.
 */
data class SavedMusicSession(
    val accountId: String,
    val origins: List<PlaybackOrigin>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeat: PlaybackRepeatMode
)

internal fun saveableMusicSession(state: PlaybackState, accountId: String): SavedMusicSession? {
    val selected = state.item ?: return null
    if (!selected.isMusic || selected.systemPrivate || selected.accountId != accountId ||
        selected.origin == null || selected.origin.accountId != accountId) return null
    val queue = state.queue.ifEmpty { listOf(selected) }
    val originalIndex = queue.indexOfFirst { it.key == selected.key }
    if (originalIndex < 0) return null
    val from = (originalIndex - MAX_RESTORED_MUSIC_ITEMS / 2).coerceAtLeast(0)
        .coerceAtMost((queue.size - MAX_RESTORED_MUSIC_ITEMS).coerceAtLeast(0))
    val eligible = queue.drop(from).take(MAX_RESTORED_MUSIC_ITEMS)
    if (eligible.any { it.systemPrivate || !it.isMusic || it.accountId != accountId ||
            it.origin == null || it.origin.accountId != accountId
        }) return null
    return SavedMusicSession(
        accountId = accountId,
        origins = eligible.map { it.origin!! },
        currentIndex = originalIndex - from,
        positionMs = state.positionMs.coerceIn(0L, 86_400_000L),
        shuffle = state.shuffleEnabled,
        repeat = state.repeatMode
    )
}

class MusicSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("music_resume_v1", Context.MODE_PRIVATE)

    fun save(value: SavedMusicSession) {
        val json = JSONObject().apply {
            put("version", SESSION_VERSION)
            put("index", value.currentIndex)
            put("position", value.positionMs)
            put("shuffle", value.shuffle)
            put("repeat", value.repeat.name)
            put("origins", JSONArray().apply {
                value.origins.forEach { origin ->
                    put(JSONObject().put("chat", origin.chatId).put("message", origin.messageId))
                }
            })
        }
        prefs.edit().putString(value.accountId, json.toString()).apply()
    }

    fun load(accountId: String): SavedMusicSession? = runCatching {
        val raw = prefs.getString(accountId, null) ?: return null
        val json = JSONObject(raw)
        require(json.getInt("version") == SESSION_VERSION)
        val array = json.getJSONArray("origins")
        require(array.length() in 1..MAX_RESTORED_MUSIC_ITEMS)
        val origins = (0 until array.length()).map { index ->
            val entry = array.getJSONObject(index)
            PlaybackOrigin(entry.getLong("chat"), entry.getLong("message"), accountId).also {
                require(it.chatId != 0L && it.messageId > 0L)
            }
        }
        require(origins.distinct().size == origins.size)
        val index = json.getInt("index")
        require(index in origins.indices)
        SavedMusicSession(
            accountId, origins, index,
            json.getLong("position").coerceIn(0L, 86_400_000L),
            json.optBoolean("shuffle"),
            PlaybackRepeatMode.valueOf(json.getString("repeat"))
        )
    }.getOrNull()

    fun clear(accountId: String) {
        prefs.edit().remove(accountId).apply()
    }
}
