package com.beykus.beygram.notifications

import android.content.Context
import android.content.SharedPreferences
import com.beykus.beygram.domain.triggers.TriggerMatcher
import com.beykus.beygram.domain.triggers.TriggerMention
import com.beykus.beygram.domain.triggers.TriggerProblem
import com.beykus.beygram.domain.triggers.TriggerRule
import com.beykus.beygram.domain.triggers.TriggerScope
import com.beykus.beygram.domain.triggers.TriggerWordLimits
import com.beykus.beygram.domain.triggers.TriggerWordsCodec
import com.beykus.beygram.domain.triggers.triggerSnippet
import com.beykus.beygram.domain.triggers.validateTriggerRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A new incoming message, reduced to what trigger words need. The text is matched and never stored in full. */
data class TriggerIncoming(
    val accountId: String,
    val chatId: Long,
    val messageId: Long,
    val dateSeconds: Long,
    val chatTitle: String,
    val senderName: String,
    val text: String,
    val muted: Boolean,
    val protectedContent: Boolean,
    /** The app is in the foreground. */
    val foreground: Boolean,
    /** The chat is open on screen, so its matches need no alert. */
    val chatOnScreen: Boolean
)

/**
 * App-wide trigger rules, scope and mentions feed. The rules are shared by every signed-in account,
 * because a name or a project word is the same in each of them. Each mention remembers its account.
 */
class TriggerWordsStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val _rules = MutableStateFlow(TriggerWordsCodec.decodeRules(prefs.getString(KEY_RULES, null)))
    private val _scope = MutableStateFlow(
        TriggerScope.entries.firstOrNull { it.name == prefs.getString(KEY_SCOPE, null) } ?: TriggerScope.MUTED_CHATS
    )
    private val _mentions = MutableStateFlow(TriggerWordsCodec.decodeMentions(prefs.getString(KEY_MENTIONS, null)))

    val rules: StateFlow<List<TriggerRule>> = _rules.asStateFlow()
    val scope: StateFlow<TriggerScope> = _scope.asStateFlow()
    /** Newest first. */
    val mentions: StateFlow<List<TriggerMention>> = _mentions.asStateFlow()

    /** Saves a rule, replacing the one with the same id. Returns the problem, or null when the rule was saved. */
    @Synchronized
    fun saveRule(rule: TriggerRule): TriggerProblem? {
        val clean = rule.copy(pattern = rule.pattern.trim())
        validateTriggerRule(clean)?.let { return it }
        val current = _rules.value
        val index = current.indexOfFirst { it.id == clean.id }
        val updated = when {
            index >= 0 -> current.toMutableList().also { it[index] = clean }
            current.size >= TriggerWordLimits.MAX_RULES -> return TriggerProblem.TOO_MANY
            else -> current + clean
        }
        commitRules(updated)
        return null
    }

    @Synchronized
    fun deleteRule(id: String) {
        commitRules(_rules.value.filterNot { it.id == id })
    }

    @Synchronized
    fun setRuleEnabled(id: String, enabled: Boolean) {
        commitRules(_rules.value.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    @Synchronized
    fun setScope(scope: TriggerScope) {
        if (_scope.value == scope) return
        _scope.value = scope
        prefs.edit().putString(KEY_SCOPE, scope.name).apply()
    }

    @Synchronized
    fun clearMentions() {
        _mentions.value = emptyList()
        prefs.edit().remove(KEY_MENTIONS).apply()
    }

    /** Removes the mentions of an account that was signed out. Rules and the scope stay. */
    @Synchronized
    fun forgetAccount(accountId: String) {
        val kept = _mentions.value.filterNot { it.accountId == accountId }
        if (kept.size == _mentions.value.size) return
        _mentions.value = kept
        prefs.edit().putString(KEY_MENTIONS, TriggerWordsCodec.encodeMentions(kept)).apply()
    }

    /** Whether the feed may show text for an account's mentions. Follows that account's previews switch, older entries too. */
    fun previewsEnabled(accountId: String): Boolean = NotificationPreferencesStore(appContext, accountId).state.value.previews

    /** Alerts skip messages older than this, so a catch-up after a reconnect does not ring for old matches. */
    private val alertMaxAgeSeconds = 10 * 60L

    private fun isRecent(dateSeconds: Long): Boolean = System.currentTimeMillis() / 1000 - dateSeconds <= alertMaxAgeSeconds

    /**
     * Checks one incoming message. Matches in muted chats always alert, because Telegram stays silent there.
     * In the all-chats scope, matches in unmuted chats only go to the feed, since Telegram already alerts for them.
     */
    @Synchronized
    fun onIncoming(event: TriggerIncoming) {
        val active = _rules.value
        if (active.none { it.enabled }) return
        if (_scope.value == TriggerScope.MUTED_CHATS && !event.muted) return
        val fired = TriggerMatcher(active).matches(event.text)
        if (fired.isEmpty()) return
        val preferences = NotificationPreferencesStore(appContext, event.accountId).state.value
        val mention = TriggerMention(
            id = "${event.accountId}:${event.chatId}:${event.messageId}",
            accountId = event.accountId,
            chatId = event.chatId,
            messageId = event.messageId,
            dateSeconds = event.dateSeconds,
            chatTitle = event.chatTitle,
            senderName = event.senderName,
            snippet = if (preferences.previews && !event.protectedContent) triggerSnippet(event.text) else null,
            triggers = fired.map { it.pattern },
            muted = event.muted
        )
        // TDLib can deliver the same update again after a reconnect; one message is recorded and alerted once.
        if (_mentions.value.any { it.id == mention.id }) return
        val kept = (listOf(mention) + _mentions.value).take(TriggerWordLimits.MAX_MENTIONS)
        _mentions.value = kept
        prefs.edit().putString(KEY_MENTIONS, TriggerWordsCodec.encodeMentions(kept)).apply()
        // Alerts follow the delivery rule of other notifications: background switch on, or the app in the foreground.
        // A chat open on screen needs none, and an old match is recorded in the feed without ringing.
        val alert = event.muted && (preferences.background || event.foreground) && !event.chatOnScreen
        if (alert && isRecent(event.dateSeconds)) TriggerAlerts.post(appContext, mention, preferences)
    }

    private fun commitRules(rules: List<TriggerRule>) {
        _rules.value = rules
        prefs.edit().putString(KEY_RULES, TriggerWordsCodec.encodeRules(rules)).apply()
    }

    companion object {
        private const val PREFERENCES = "trigger_words"
        private const val KEY_RULES = "rules"
        private const val KEY_SCOPE = "scope"
        private const val KEY_MENTIONS = "mentions"

        @Volatile private var instance: TriggerWordsStore? = null

        fun get(context: Context): TriggerWordsStore = instance ?: synchronized(this) {
            instance ?: TriggerWordsStore(context.applicationContext).also { instance = it }
        }
    }
}
