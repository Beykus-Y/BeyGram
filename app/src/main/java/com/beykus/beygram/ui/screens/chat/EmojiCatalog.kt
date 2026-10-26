package com.beykus.beygram.ui.screens.chat

import android.content.Context
import androidx.annotation.StringRes
import com.beykus.beygram.R

/** How many recently used emoji the Emoji tab keeps. */
internal const val RECENT_EMOJI_LIMIT = 32

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

/** Recent emoji shared by the whole app, kept in private preferences. Storage failures fall back to an empty list. */
internal class RecentEmojiStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): List<String> = try {
        preferences.getString(KEY_RECENT, null).orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotEmpty() }
            .distinct()
            .take(RECENT_EMOJI_LIMIT)
    } catch (_: Exception) {
        emptyList()
    }

    /** Stores [emoji] as the newest entry and returns the updated list so the picker can show it at once. */
    fun record(emoji: String): List<String> {
        val updated = load().withRecentEmoji(emoji)
        try {
            preferences.edit().putString(KEY_RECENT, updated.joinToString(SEPARATOR)).apply()
        } catch (_: Exception) {
            // The emoji still shows in the Recent section for this session when the preference cannot be written.
        }
        return updated
    }

    private companion object {
        const val PREFERENCES = "emoji_panel"
        const val KEY_RECENT = "recent"
        const val SEPARATOR = ","
    }
}
