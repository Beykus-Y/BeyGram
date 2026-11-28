package com.beykus.beygram.ui.screens.chat

import android.content.Context
import androidx.annotation.StringRes
import com.beykus.beygram.R
import com.beykus.beygram.domain.model.RecentEmojiHistory

/** How many recently used emoji the Emoji tab keeps. */
internal const val RECENT_EMOJI_LIMIT = 32

/** How many recently used custom emoji the dock keeps. */
internal const val RECENT_CUSTOM_EMOJI_LIMIT = 36

/** Id of the Recent section, whose emoji come from the stored list rather than from the catalog. */
internal const val RECENT_EMOJI_GROUP_ID = "recent"

/** One section of the Emoji tab: its header, its strip button and the emoji listed under it. */
internal data class EmojiGroup(
    val id: String,
    @StringRes val titleRes: Int,
    val icon: String,
    val emoji: List<String>
)

/**
 * Standard Unicode emoji grouped like the system keyboards. Emoji are content rather than UI text, so they stay in code and
 * only the group titles are string resources. Skin-tone variants and emoji newer than Emoji 11 are left out, so every glyph
 * renders on the Android versions this app supports.
 */
internal object EmojiCatalog {
    private const val RECENT_ICON = "🕒"

    val groups: List<EmojiGroup> = listOf(
        EmojiGroup(
            id = "smileys",
            titleRes = R.string.emoji_category_smileys,
            icon = "😀",
            emoji = listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
                "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😋", "😜", "🤪",
                "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑",
                "😶", "😏", "😒", "🙄", "😬", "😌", "😔", "😴", "😷", "🤒",
                "🤢", "🤮", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "😎",
                "🤓", "🧐", "😕", "😟", "😮", "😲", "😳", "🥺", "😢", "😭",
                "😱", "😡", "😠", "🤬", "😈", "💀", "💩", "🤡", "👻", "👽",
                "👾", "🤖", "😺", "😸", "😹", "😻", "😼", "😿", "❤️", "🧡",
                "💛", "💚", "💙", "💜", "🖤", "💘", "💔", "💕", "👋", "🤚",
                "✋", "👌", "✌️", "🤞", "🤟", "🤘", "👍", "👎", "👏", "🙏",
                "👶", "🧒", "👨", "👩", "🧑", "👴", "👵", "🎅"
            )
        ),
        EmojiGroup(
            id = "animals",
            titleRes = R.string.emoji_category_animals,
            icon = "🐶",
            emoji = listOf(
                "🐵", "🐒", "🦍", "🐶", "🐕", "🐩", "🐺", "🦊", "🦝", "🐱",
                "🐈", "🦁", "🐯", "🐅", "🐆", "🐴", "🐎", "🦄", "🦓", "🦌",
                "🐮", "🐂", "🐷", "🐑", "🐐", "🐘", "🐭", "🐹", "🐰", "🐇",
                "🦔", "🐻", "🐨", "🐼", "🐔", "🐓", "🐣", "🐤", "🐥", "🐦",
                "🐧", "🦅", "🦆", "🦉", "🦜", "🦚", "🐸", "🐊", "🐢", "🦎",
                "🐍", "🐲", "🐉", "🦖", "🐳", "🐋", "🐬", "🐟", "🐠", "🐙",
                "🐌", "🦋", "🐛", "🐜", "🐝", "🐞", "💐", "🌸", "💮", "🌹",
                "🌺", "🌻", "🌼", "🌷", "🌱", "🌲", "🌳", "🌴", "🌵", "🌿",
                "🍀", "🍁", "🍄", "🌾"
            )
        ),
        EmojiGroup(
            id = "food",
            titleRes = R.string.emoji_category_food,
            icon = "🍕",
            emoji = listOf(
                "🍇", "🍈", "🍉", "🍊", "🍋", "🍌", "🍍", "🥭", "🍎", "🍏",
                "🍐", "🍑", "🍒", "🍓", "🥝", "🥥", "🍅", "🥑", "🍆", "🥔",
                "🥕", "🌽", "🥒", "🥬", "🥦", "🏺", "🥜", "🌰", "🍞",
                "🥐", "🥖", "🥨", "🥯", "🥞", "🍽️", "🧀", "🍖", "🍗", "🥩",
                "🥓", "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🌯", "🥙", "🥣",
                "🥚", "🍳", "🥘", "🍲", "🥗", "🍿", "🌶️", "🧂", "🥫", "🍱",
                "🍘", "🍙", "🍚", "🍛", "🍜", "🍝", "🍠", "🍣", "🍤", "🍥",
                "🥮", "🍡", "🥟", "🥠", "🥡", "🦀", "🦞", "🦐", "🦑", "🍦",
                "🍧", "🍨", "🍩", "🍪", "🎂", "🍰", "🧁", "🥧", "🍫", "🍬",
                "🍭", "🍮", "🍯", "🍼", "🥛", "☕", "🍵", "🍶", "🍾", "🍷",
                "🍸", "🍹", "🍺", "🍻", "🥂", "🥃", "🥤",
                "🥢", "🍴", "🥄", "🔪"
            )
        ),
        EmojiGroup(
            id = "activity",
            titleRes = R.string.emoji_category_activity,
            icon = "⚽",
            emoji = listOf(
                "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱",
                "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🥅", "⛳", "🏹", "🎣",
                "🏄", "🥊", "🥋", "🎽", "🛹", "🛷", "🥌", "🎿", "🏂", "🤼",
                "🤸", "🤺", "🤾", "🧘", "🏊", "🚣", "🧗", "🏇",
                "🏆", "🥇", "🥈", "🥉", "🏅", "🎫", "🎪", "🎭", "🎨", "🎬",
                "🎤", "🎧", "🎼", "🎹", "🥁", "🎷", "🎺", "🎸", "🎻", "🎲",
                "🎯", "🎳", "🎮", "🎰", "🧩"
            )
        ),
        EmojiGroup(
            id = "travel",
            titleRes = R.string.emoji_category_travel,
            icon = "🚗",
            emoji = listOf(
                "🚗", "🚕", "🚙", "🚌", "🚎", "🚓", "🚑", "🚒", "🚐", "🚚",
                "🚛", "🚜", "🛵", "🚲", "🛴", "🛶", "🚨", "🚔", "🚍", "🚘",
                "🚖", "🚡", "🚠", "🚟", "🚃", "🚋", "🚞", "🚝", "🚄", "🚅",
                "🚈", "🚂", "🚆", "🚇", "🚊", "🚉", "✈️", "🛫", "🛬", "💺",
                "🚀", "🛸", "🚁", "⛵", "🚤", "🚢", "⚓", "⛽", "🚧", "🚦",
                "🚥", "🚏", "🗿", "🗽", "🗼", "🏰", "🏯", "🎡", "🎢", "🎠",
                "⛲", "🌋", "🗻", "⛺", "🏠", "🏡", "🏢", "🏬", "🏣", "🏥",
                "🏦", "🏨", "🏪", "🏫", "🕌", "⛪", "🌅", "🎆", "🎇", "🌇"
            )
        ),
        EmojiGroup(
            id = "objects",
            titleRes = R.string.emoji_category_objects,
            icon = "💡",
            emoji = listOf(
                "⌚", "📱", "📲", "💻", "💽", "💾", "💿", "📀", "📷", "📸",
                "📹", "🎥", "📞", "📺", "📻", "📡", "⏰", "⏳", "⌛", "🔋",
                "🔌", "💡", "💸", "💵", "💴", "💶", "💷", "💰", "💳", "💎",
                "🧰", "🔧", "🔨", "🔩", "🧱", "💊", "💉", "🧷", "🧬", "🔬",
                "🔭", "🧪", "🧹", "🧺", "🧻", "🚽", "🚰", "🚿", "🛁", "🧼",
                "🧽", "🧴", "🔑", "🚪", "🛋️", "🛒", "🎁", "🎈", "🎀", "🎊",
                "🎉", "🎎", "🏮", "🎐", "🧧", "📩", "📨", "📧", "💌", "📥",
                "📤", "📦", "📪", "📫", "📬", "📮", "📜", "📃", "📄", "📑",
                "🧾", "📊", "📈", "📉", "📆", "📅", "📇", "📋", "📁", "📂",
                "📰", "📓", "📔", "📒", "📕", "📗", "📘", "📙", "📚", "📖",
                "🔖", "🔗", "📎", "📐", "📏", "🧮", "📌", "📍", "📝", "🔍"
            )
        ),
        EmojiGroup(
            id = "symbols",
            titleRes = R.string.emoji_category_symbols,
            icon = "🔣",
            emoji = listOf(
                "✨", "💫", "⭐", "🌟", "💥", "🔥", "💢", "💯", "✅", "❌",
                "❎", "➕", "➖", "➗", "❓", "❔", "❕", "❗", "‼️", "⁉️",
                "⚠️", "🚸", "⛔", "🔴", "🔵", "💠", "🔘", "🔳", "🔲", "🆗",
                "⚫", "⚪", "⬛", "🆒", "🆕", "🆓", "🆙", "🆘", "🆚", "🆎",
                "⬜", "🔶", "🔷", "🔸", "🔹", "🔺", "🔻", "0️⃣", "1️⃣", "2️⃣",
                "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "#️⃣", "➡️", "⬅️",
                "⬆️", "⬇️", "↗️", "↘️", "↙️", "↖️", "↕️", "↔️", "🔼", "🔽",
                "▶️", "⏸️", "⏹️", "⏩", "⏪", "🔀", "🔁", "🔄", "🔊", "🔇",
                "🔉", "🔔", "🔕", "📣", "📢", "💬", "💭", "🚫", "🚭", "🚷",
                "🔞", "♻️", "✔️", "✖️", "➰", "➿", "〰️"
            )
        ),
        EmojiGroup(
            id = "flags",
            titleRes = R.string.emoji_category_flags,
            icon = "🏁",
            emoji = listOf(
                "🏁", "🚩", "🇺🇸", "🇬🇧", "🇨🇦", "🇦🇺", "🇳🇿", "🇮🇪", "🇫🇷", "🇩🇪",
                "🇮🇹", "🇪🇸", "🇵🇹", "🇳🇱", "🇧🇪", "🇨🇭", "🇦🇹", "🇸🇪", "🇳🇴", "🇩🇰",
                "🇫🇮", "🇮🇸", "🇵🇱", "🇨🇿", "🇸🇰", "🇭🇺", "🇷🇴", "🇧🇬", "🇬🇷", "🇹🇷",
                "🇷🇺", "🇺🇦", "🇧🇾", "🇱🇹", "🇱🇻", "🇪🇪", "🇲🇩", "🇬🇪", "🇦🇲", "🇦🇿",
                "🇰🇿", "🇺🇿", "🇨🇳", "🇯🇵", "🇰🇷", "🇹🇼", "🇭🇰", "🇮🇳", "🇵🇰", "🇧🇩",
                "🇹🇭", "🇻🇳", "🇵🇭", "🇮🇩", "🇲🇾", "🇸🇬", "🇦🇪", "🇸🇦", "🇮🇱", "🇪🇬",
                "🇿🇦", "🇳🇬", "🇰🇪", "🇲🇦", "🇧🇷", "🇦🇷", "🇲🇽", "🇨🇴", "🇨🇱", "🇵🇪",
                "🇪🇺", "🇺🇳"
            )
        )
    )

    /** Recent emoji lead the list, and the section is left out until the user has picked one. */
    fun withRecent(recent: List<String>): List<EmojiGroup> {
        if (recent.isEmpty()) return groups
        return listOf(EmojiGroup(RECENT_EMOJI_GROUP_ID, R.string.emoji_category_recent, RECENT_ICON, recent)) + groups
    }
}

/** Puts [emoji] first, drops its older copy and keeps only [RECENT_EMOJI_LIMIT] entries. */
internal fun List<String>.withRecentEmoji(emoji: String): List<String> =
    (listOf(emoji) + filterNot { it == emoji }).take(RECENT_EMOJI_LIMIT)

/**
 * Unicode and custom recents belong to the account that selected them. The recovery state is kept here too: an explicit
 * clear suppresses recovery for good, while a failed attempt is retried only after a pause, so opening the dock never
 * starts a scan on every tap.
 */
internal class RecentEmojiStore(context: Context, private val accountId: String) {
    private val recentKey = "recent_$accountId"
    private val customKey = "custom_recent_$accountId"
    private val restoreDoneKey = "restore_done_$accountId"
    private val restoreSuppressedKey = "restore_suppressed_$accountId"
    private val restoreAttemptKey = "restore_attempt_$accountId"
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun peekUnicode(): List<String>? = unicodeCache[accountId]

    fun peekCustom(): List<Long>? = customCache[accountId]

    suspend fun warmUp(): Pair<List<String>, List<Long>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        migrateLegacyIfNeeded()
        load() to loadCustom()
    }

    fun load(): List<String> = unicodeCache[accountId] ?: run {
        migrateLegacyIfNeeded()
        try {
            preferences.getString(recentKey, null).orEmpty()
                .split(SEPARATOR)
                .filter { it.isNotEmpty() }
                .distinct()
                .take(RECENT_EMOJI_LIMIT)
        } catch (_: Exception) {
            emptyList()
        }.also { unicodeCache[accountId] = it }
    }

    fun loadCustom(): List<Long> = customCache[accountId] ?: try {
        preferences.getString(customKey, null).orEmpty()
            .split(SEPARATOR)
            .mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0L } }
            .distinct()
            .take(RECENT_CUSTOM_EMOJI_LIMIT)
    } catch (_: Exception) {
        emptyList()
    }.also { customCache[accountId] = it }

    /** Stores [emoji] as the newest entry and returns the updated list so the picker can show it at once. */
    fun record(emoji: String): List<String> {
        val updated = load().withRecentEmoji(emoji)
        unicodeCache[accountId] = updated
        persist(recentKey, updated.joinToString(SEPARATOR))
        return updated
    }

    fun recordCustom(customEmojiId: Long): List<Long> {
        if (customEmojiId <= 0L) return loadCustom()
        val updated = (listOf(customEmojiId) + loadCustom().filterNot { it == customEmojiId })
            .take(RECENT_CUSTOM_EMOJI_LIMIT)
        customCache[accountId] = updated
        persist(customKey, updated.joinToString(SEPARATOR))
        return updated
    }

    /**
     * Appends recovered Unicode emoji after the local ones. Local entries are newer and keep their order; an emoji
     * already present is not duplicated and does not move.
     */
    fun mergeRecovered(history: RecentEmojiHistory): List<String> {
        if (restoreSuppressed()) return emptyList()
        if (history.unicode.isEmpty()) return load()
        val merged = (load() + history.unicode).distinct().take(RECENT_EMOJI_LIMIT)
        unicodeCache[accountId] = merged
        persist(recentKey, merged.joinToString(SEPARATOR))
        return merged
    }

    /** Appends recovered custom emoji after the local ones, without duplicates or reordering the local entries. */
    fun mergeRecoveredCustom(ids: List<Long>): List<Long> {
        if (restoreSuppressed()) return emptyList()
        if (ids.isEmpty()) return loadCustom()
        val merged = (loadCustom() + ids.filter { it > 0L }).distinct().take(RECENT_CUSTOM_EMOJI_LIMIT)
        customCache[accountId] = merged
        persist(customKey, merged.joinToString(SEPARATOR))
        return merged
    }

    /** Both lists are cleared by one explicit action; recovery must not bring the old entries back. */
    fun clearAll(): Pair<List<String>, List<Long>> {
        unicodeCache[accountId] = emptyList()
        customCache[accountId] = emptyList()
        preferences.edit()
            .putString(recentKey, "")
            .putString(customKey, "")
            .putBoolean(restoreSuppressedKey, true)
            .apply()
        return emptyList<String>() to emptyList()
    }

    fun restoreSuppressed(): Boolean = preferences.getBoolean(restoreSuppressedKey, false)

    fun restoreDone(): Boolean = preferences.getBoolean(restoreDoneKey, false)

    fun markRestoreDone() {
        if (restoreSuppressed()) return
        preferences.edit().putBoolean(restoreDoneKey, true).apply()
    }

    private fun migrateLegacyIfNeeded() {
        if (accountId != LEGACY_ACCOUNT_ID || restoreSuppressed() || preferences.contains(recentKey)) return
        val legacyValue = runCatching { preferences.getString(LEGACY_RECENT_KEY, null) }.getOrNull() ?: return
        runCatching {
            preferences.edit()
                .putString(recentKey, legacyValue)
                .remove(LEGACY_RECENT_KEY)
                .apply()
        }
    }

    /** A failed or cancelled attempt is retried only after [RESTORE_RETRY_MS], not on every dock opening. */
    fun mayAttemptRestore(nowMs: Long): Boolean {
        if (restoreSuppressed() || restoreDone()) return false
        val last = preferences.getLong(restoreAttemptKey, 0L)
        return last <= 0L || nowMs - last >= RESTORE_RETRY_MS
    }

    fun markRestoreAttempt(nowMs: Long) {
        preferences.edit().putLong(restoreAttemptKey, nowMs).apply()
    }

    private fun persist(key: String, value: String) {
        try {
            preferences.edit().putString(key, value).apply()
        } catch (_: Exception) {
            // The list still shows for this session when the preference cannot be written.
        }
    }

    companion object {
        private const val PREFERENCES = "emoji_panel"
        private const val SEPARATOR = ","
        private const val RESTORE_RETRY_MS = 15 * 60_000L
        private const val LEGACY_ACCOUNT_ID = "legacy"
        private const val LEGACY_RECENT_KEY = "recent"
        private val unicodeCache = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
        private val customCache = java.util.concurrent.ConcurrentHashMap<String, List<Long>>()

        fun clearAccount(context: Context, accountId: String) {
            unicodeCache.remove(accountId)
            customCache.remove(accountId)
            val prefs = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            prefs.edit().apply {
                remove("recent_$accountId")
                remove("custom_recent_$accountId")
                remove("restore_done_$accountId")
                remove("restore_suppressed_$accountId")
                remove("restore_attempt_$accountId")
                if (accountId == LEGACY_ACCOUNT_ID) remove(LEGACY_RECENT_KEY)
            }.apply()
        }

        internal fun clearMemoryCache() {
            unicodeCache.clear()
            customCache.clear()
        }
    }
}
