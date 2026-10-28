package com.beykus.beygram.data.settings

import android.content.Context
import com.beykus.beygram.data.tdlib.TdAccountStorageLayout
import com.beykus.beygram.domain.model.InteractionEffect
import com.beykus.beygram.domain.model.InteractionPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Per-chat exception. [Inherit] follows the account-wide switches. */
enum class ChatStealthMode { Inherit, Allow, Block }

/**
 * Account-wide stealth switches and per-chat exceptions. [failClosed] means the stored data could not be read
 * while protection was on, so every effect is refused until a valid state is written again.
 */
data class StealthSettings(
    val hideTyping: Boolean = false,       // Typing effect: typing, recording, uploading
    val noReadReceipts: Boolean = false,   // ReadHistory
    val noContentOpen: Boolean = false,    // OpenContent
    val hideOnline: Boolean = false,       // OnlinePresence (account-wide only, never per chat)
    val noStoryViews: Boolean = false,     // StoryView
    val warnRiskyActions: Boolean = false, // UI-only flag for later packages
    /** Sends go out as Telegram scheduled messages that the server publishes later (account-wide). */
    val serverScheduledSend: Boolean = false,
    /** Lead of a stealth send in seconds; a preset from [StealthScheduleDelayChoices] or a custom value in [StealthScheduleDelayRange]. */
    val scheduledSendDelaySeconds: Int = DEFAULT_STEALTH_SCHEDULE_DELAY_SECONDS,
    val chats: Map<Long, ChatStealthMode> = emptyMap(),
    val failClosed: Boolean = false
) {
    val protectionActive: Boolean
        get() = failClosed || hideTyping || noReadReceipts || noContentOpen || hideOnline || noStoryViews ||
            serverScheduledSend || chats.values.any { it == ChatStealthMode.Block }

    /** The lead for a server-scheduled send, or null when sends go out at once. Fail-closed data always schedules. */
    val scheduledSendDelay: Int?
        get() = if (failClosed || serverScheduledSend) scheduledSendDelaySeconds else null

    /** True when the stealth lead is long enough for Telegram's scheduled queue; a shorter one is only waited out locally. */
    val schedulesOnServer: Boolean
        get() = scheduledSendDelay?.let(::stealthDelayUsesServer) == true

    fun allows(effect: InteractionEffect, chatId: Long?): Boolean {
        if (failClosed) return false
        // Presence is account-wide, so a chat exception never applies to it.
        if (effect == InteractionEffect.OnlinePresence) return !hideOnline
        val exception = chatId?.let { chats[it] }
        if (exception == ChatStealthMode.Allow) return true
        if (exception == ChatStealthMode.Block) return false
        return when (effect) {
            InteractionEffect.Typing -> !hideTyping
            InteractionEffect.ReadHistory -> !noReadReceipts
            InteractionEffect.OpenContent -> !noContentOpen
            InteractionEffect.StoryView -> !noStoryViews
            InteractionEffect.OnlinePresence -> !hideOnline
        }
    }

    companion object {
        val FailClosed = StealthSettings(
            hideTyping = true, noReadReceipts = true, noContentOpen = true, hideOnline = true,
            noStoryViews = true, warnRiskyActions = true, serverScheduledSend = true, failClosed = true
        )
    }
}

/**
 * The preset leads for a server-scheduled send, in seconds. Telegram sends a message scheduled 10 seconds ahead or less
 * at once, so the presets start at 15 seconds: the margin covers a slow request.
 */
val StealthScheduleDelayChoices: List<Int> = listOf(15, 30, 60, 120)

/** The leads «Своё время» accepts, from one second to one hour. */
val StealthScheduleDelayRange: IntRange = 1..3600

/**
 * The shortest lead sent to Telegram's scheduled queue. TDLib sends a date at or before now+10 seconds at once, and the
 * extra 5 seconds cover a slow request, as for the presets. A shorter lead is waited out by the app, and the message then
 * goes out as an ordinary send that can show the account online.
 */
const val STEALTH_SERVER_SCHEDULE_MIN_SECONDS = 15

fun stealthDelayUsesServer(seconds: Int): Boolean = seconds >= STEALTH_SERVER_SCHEDULE_MIN_SECONDS

const val DEFAULT_STEALTH_SCHEDULE_DELAY_SECONDS = 15

/** Storage behind [StealthSettingsStore]; values use the SharedPreferences types (Boolean, Int, String). */
internal interface StealthStorage {
    fun entries(): Map<String, *>

    /** Replaces everything that is stored, in one synchronous write. */
    fun replace(values: Map<String, Any>): Boolean
}

private class SharedPreferencesStealthStorage(context: Context, accountId: String) : StealthStorage {
    private val preferences = context.applicationContext.getSharedPreferences(
        stealthPreferencesName(accountId), Context.MODE_PRIVATE
    )

    override fun entries(): Map<String, *> = preferences.all

    override fun replace(values: Map<String, Any>): Boolean {
        val editor = preferences.edit().clear()
        for ((key, value) in values) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value)
                else -> error("Unsupported stealth value for $key")
            }
        }
        // commit() rather than apply(): the caller must know whether the protection really reached the disk.
        return editor.commit()
    }
}

/** The legacy account keeps the plain name; each secondary account gets its own file. */
internal fun stealthPreferencesName(accountId: String): String {
    require(TdAccountStorageLayout.isValidAccountId(accountId)) { "Invalid account id for stealth settings" }
    return if (accountId == TdAccountStorageLayout.LEGACY_ACCOUNT_ID) "stealth-mode" else "stealth-mode_$accountId"
}

// Version 2 added the server-scheduled send; version 1 data reads with it off.
private const val CURRENT_VERSION = 2
private const val VERSION_WITHOUT_SCHEDULED_SEND = 1
private const val KEY_VERSION = "version"
private const val KEY_PROTECTION_ON = "protection_on"
private const val KEY_HIDE_TYPING = "hide_typing"
private const val KEY_NO_READ_RECEIPTS = "no_read_receipts"
private const val KEY_NO_CONTENT_OPEN = "no_content_open"
private const val KEY_HIDE_ONLINE = "hide_online"
private const val KEY_NO_STORY_VIEWS = "no_story_views"
private const val KEY_WARN_RISKY_ACTIONS = "warn_risky_actions"
private const val KEY_SCHEDULED_SEND = "scheduled_send"
private const val KEY_SCHEDULED_SEND_DELAY = "scheduled_send_delay"
private const val CHAT_KEY_PREFIX = "chat:"

/**
 * The account's stealth switches, held in memory and written with one synchronous commit per change.
 * The state is read once when the store is created, so a policy decision never waits for the disk.
 */
class StealthSettingsStore internal constructor(private val storage: StealthStorage) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<StealthSettings> = _state.asStateFlow()
    val current: StealthSettings get() = state.value
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /**
     * Applies [transform] and commits the result. Returns false when the commit failed. A failed commit still
     * enforces the change in this process when it is stricter or equal; a looser change that was not stored is dropped.
     */
    fun update(transform: (StealthSettings) -> StealthSettings): Boolean {
        val (saved, changed) = synchronized(this) {
            val previous = _state.value
            // A valid write clears failClosed: the stored values are trusted again once they are rewritten.
            val candidate = transform(previous).copy(failClosed = false).normalized()
            val written = storage.replace(candidate.toStoredValues())
            val next = if (written || isAtLeastAsStrict(candidate, previous)) candidate else previous
            _state.value = next
            Pair(written, next != previous)
        }
        if (changed) notifyListeners()
        return saved
    }

    /** Removes every stored switch and resets to the defaults, for example when the account logs out. */
    fun clear(): Boolean {
        val (saved, changed) = synchronized(this) {
            val previous = _state.value
            val written = storage.replace(emptyMap())
            val next = if (written) StealthSettings() else previous
            _state.value = next
            Pair(written, next != previous)
        }
        if (changed) notifyListeners()
        return saved
    }

    /** Called after every change, outside the lock. */
    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    private fun load(): StealthSettings {
        val stored = try {
            storage.entries()
        } catch (_: Exception) {
            return StealthSettings.FailClosed
        }
        if (stored.isEmpty()) return StealthSettings()
        return try {
            parse(stored)
        } catch (_: Exception) {
            // Only a readable "protection off" falls back to normal behaviour. A missing or broken flag next to other
            // stored values may hide protection the user turned on, so everything stays refused.
            if (stored[KEY_PROTECTION_ON] == false) StealthSettings() else StealthSettings.FailClosed
        }
    }

    companion object {
        private val instances = ConcurrentHashMap<String, StealthSettingsStore>()

        /**
         * One store per account. The UI and the account's repository share it, so the switch the user changes
         * is the one the gate enforces.
         */
        fun forAccount(context: Context, accountId: String): StealthSettingsStore =
            instances.computeIfAbsent(accountId) {
                StealthSettingsStore(SharedPreferencesStealthStorage(context.applicationContext, accountId))
            }

        /** Forgets the in-memory store of a removed secondary account; the caller deletes its preferences. */
        fun releaseAccount(accountId: String) {
            instances.remove(accountId)
        }
    }
}

/** Reads the switches on every call, so a change applies to the next decision without rebuilding the repository. */
class StealthInteractionPolicy(private val store: StealthSettingsStore) : InteractionPolicy {
    override fun allows(effect: InteractionEffect, chatId: Long?): Boolean = store.current.allows(effect, chatId)
}

private fun parse(stored: Map<String, *>): StealthSettings {
    val version = stored[KEY_VERSION]
    check(version == CURRENT_VERSION || version == VERSION_WITHOUT_SCHEDULED_SEND) { "Unknown stealth settings version" }
    val chats = HashMap<Long, ChatStealthMode>()
    for ((key, value) in stored) {
        if (!key.startsWith(CHAT_KEY_PREFIX)) continue
        val chatId = key.removePrefix(CHAT_KEY_PREFIX).toLongOrNull() ?: error("Unreadable chat key")
        chats[chatId] = when (value) {
            "allow" -> ChatStealthMode.Allow
            "block" -> ChatStealthMode.Block
            else -> error("Unknown chat stealth value")
        }
    }
    return StealthSettings(
        hideTyping = stored.flag(KEY_HIDE_TYPING),
        noReadReceipts = stored.flag(KEY_NO_READ_RECEIPTS),
        noContentOpen = stored.flag(KEY_NO_CONTENT_OPEN),
        hideOnline = stored.flag(KEY_HIDE_ONLINE),
        noStoryViews = stored.flag(KEY_NO_STORY_VIEWS),
        warnRiskyActions = stored.flag(KEY_WARN_RISKY_ACTIONS),
        serverScheduledSend = if (version == CURRENT_VERSION) stored.flag(KEY_SCHEDULED_SEND) else false,
        scheduledSendDelaySeconds = if (version == CURRENT_VERSION) {
            (stored[KEY_SCHEDULED_SEND_DELAY] as Int).also { check(it in StealthScheduleDelayRange) { "Unknown scheduled send delay" } }
        } else {
            DEFAULT_STEALTH_SCHEDULE_DELAY_SECONDS
        },
        chats = chats
    )
}

/** A missing or differently typed flag is a read error, never a silent default. */
private fun Map<String, *>.flag(key: String): Boolean = get(key) as Boolean

private fun StealthSettings.toStoredValues(): Map<String, Any> {
    val values = HashMap<String, Any>()
    values[KEY_VERSION] = CURRENT_VERSION
    values[KEY_PROTECTION_ON] = protectionActive
    values[KEY_HIDE_TYPING] = hideTyping
    values[KEY_NO_READ_RECEIPTS] = noReadReceipts
    values[KEY_NO_CONTENT_OPEN] = noContentOpen
    values[KEY_HIDE_ONLINE] = hideOnline
    values[KEY_NO_STORY_VIEWS] = noStoryViews
    values[KEY_WARN_RISKY_ACTIONS] = warnRiskyActions
    values[KEY_SCHEDULED_SEND] = serverScheduledSend
    values[KEY_SCHEDULED_SEND_DELAY] = scheduledSendDelaySeconds
    for ((chatId, mode) in chats) {
        val stored = when (mode) {
            ChatStealthMode.Allow -> "allow"
            ChatStealthMode.Block -> "block"
            ChatStealthMode.Inherit -> null
        }
        if (stored != null) values["$CHAT_KEY_PREFIX$chatId"] = stored
    }
    return values
}

/** Inherit means "no exception", so it is never stored or compared. A lead outside the accepted range is clamped into it. */
private fun StealthSettings.normalized(): StealthSettings = copy(
    chats = chats.filterValues { it != ChatStealthMode.Inherit },
    scheduledSendDelaySeconds = scheduledSendDelaySeconds.coerceIn(StealthScheduleDelayRange)
)

/**
 * True when [candidate] allows nothing that [previous] refuses, for the account-wide switches and for every chat
 * that either one names. A failed commit may still enforce such a value; it must never enforce a looser one.
 */
private fun isAtLeastAsStrict(candidate: StealthSettings, previous: StealthSettings): Boolean {
    val contexts = listOf<Long?>(null) + (candidate.chats.keys + previous.chats.keys).toList()
    val effects = InteractionEffect.entries.all { effect ->
        contexts.all { chatId -> !candidate.allows(effect, chatId) || previous.allows(effect, chatId) }
    }
    // Turning the scheduled send off, or shortening its lead, is looser.
    val previousDelay = previous.scheduledSendDelay ?: return effects
    val candidateDelay = candidate.scheduledSendDelay ?: return false
    return effects && candidateDelay >= previousDelay
}
