package com.beykus.beygram.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/** The delays a user can pick, in seconds. Zero turns delayed sending off. */
val DelayedSendChoices: List<Int> = listOf(0, 1, 3, 5, 10, 30, 60)

/** The legacy account keeps the plain name; each secondary account gets its own file, as for the stealth settings. */
internal fun delayedSendPreferencesName(accountId: String): String {
    require(TdAccountStorageLayout.isValidAccountId(accountId)) { "Invalid account id for delayed send settings" }
    return if (accountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID) "delayed-send" else "delayed-send_$accountId"
}

private const val KEY_DELAY_SECONDS = "delay_seconds"

/**
 * The account's delay before a plain text message is sent. Zero is off. A stored value that is not one of [DelayedSendChoices]
 * reads as off. [delaySeconds] changes only after the new value is committed.
 */
class DelayedSendSettingsStore private constructor(private val preferences: SharedPreferences) {
    private val _delaySeconds = MutableStateFlow(load())
    val delaySeconds: StateFlow<Int> = _delaySeconds.asStateFlow()

    /** Stores [seconds] and returns false when the commit failed; the previous delay stays in effect then. */
    fun update(seconds: Int): Boolean {
        require(seconds in DelayedSendChoices) { "Unsupported delay" }
        synchronized(this) {
            val saved = preferences.edit().putInt(KEY_DELAY_SECONDS, seconds).commit()
            if (saved) _delaySeconds.value = seconds
            return saved
        }
    }

    private fun load(): Int {
        // A value of another type makes getInt() throw; that is treated like any other unreadable value.
        val stored = try {
            preferences.getInt(KEY_DELAY_SECONDS, 0)
        } catch (_: Exception) {
            0
        }
        return if (stored in DelayedSendChoices) stored else 0
    }

    companion object {
        private val instances = ConcurrentHashMap<String, DelayedSendSettingsStore>()

        /** One store per account, shared by the settings screen and the chat screen. */
        fun forAccount(context: Context, accountId: String): DelayedSendSettingsStore =
            instances.computeIfAbsent(accountId) {
                DelayedSendSettingsStore(
                    context.applicationContext.getSharedPreferences(delayedSendPreferencesName(accountId), Context.MODE_PRIVATE)
                )
            }

        /** Forgets the store of a removed secondary account; the caller deletes its preferences. */
        fun releaseAccount(accountId: String) {
            instances.remove(accountId)
        }
    }
}
