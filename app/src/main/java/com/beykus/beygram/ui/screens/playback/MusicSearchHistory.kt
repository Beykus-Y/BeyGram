package com.beykus.beygram.ui.screens.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** How many recent queries the music search keeps per account. */
internal const val MUSIC_SEARCH_HISTORY_LIMIT = 8

/**
 * [query] moved to the front of [history], without a case-insensitive duplicate, cut to [limit]. A blank query
 * leaves the list as it is.
 */
internal fun pushRecentMusicQuery(history: List<String>, query: String, limit: Int = MUSIC_SEARCH_HISTORY_LIMIT): List<String> {
    val text = query.trim()
    if (text.isEmpty()) return history
    return (listOf(text) + history.filterNot { it.equals(text, ignoreCase = true) }).take(limit)
}

/**
 * Recent queries of the music search, kept on this device per account. A query is kept once the user did something
 * with its results (played, queued or saved a track), so half-typed words never get in.
 */
internal object MusicSearchHistory {
    private const val PREFERENCES = "music_search_history"
    private val flows = HashMap<String, MutableStateFlow<List<String>>>()

    fun observe(context: Context, accountId: String): StateFlow<List<String>> = flow(context, accountId).asStateFlow()

    fun remember(context: Context, accountId: String, query: String) = update(context, accountId) {
        pushRecentMusicQuery(it, query)
    }

    fun remove(context: Context, accountId: String, query: String) = update(context, accountId) { list ->
        list.filterNot { it == query }
    }

    fun clear(context: Context, accountId: String) = update(context, accountId) { emptyList() }

    @Synchronized
    private fun flow(context: Context, accountId: String): MutableStateFlow<List<String>> =
        flows.getOrPut(accountId) { MutableStateFlow(read(context, accountId)) }

    @Synchronized
    private fun update(context: Context, accountId: String, change: (List<String>) -> List<String>) {
        if (accountId.isBlank()) return
        val flow = flow(context, accountId)
        val next = change(flow.value)
        if (next == flow.value) return
        flow.value = next
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (next.isEmpty()) {
            preferences.edit().remove(accountId).apply()
        } else {
            preferences.edit().putString(accountId, JSONArray(next).toString()).apply()
        }
    }

    private fun read(context: Context, accountId: String): List<String> {
        if (accountId.isBlank()) return emptyList()
        val raw = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(accountId, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }.take(MUSIC_SEARCH_HISTORY_LIMIT)
        }.getOrDefault(emptyList())
    }
}
