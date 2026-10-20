package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.TelegramChatFolder
import org.drinkless.tdlib.TdApi

/**
 * TDLib switched ChatFolderInfo.name from String to ChatFolderName(FormattedText)
 * in recent releases. Read the public generated Java fields so a jar update won't
 * silently turn server folder names into Java class names.
 *
 * This compatibility adapter is UI-only; it never writes or invents a server folder.
 */
internal fun tdFolderDisplayName(raw: Any?): String {
    if (raw is String) return raw.trim()
    fun member(owner: Any?, field: String): Any? = runCatching {
        owner?.javaClass?.getField(field)?.get(owner)
    }.getOrNull()
    val inner = member(raw, "text")
    val text = when (inner) {
        is String -> inner
        else -> member(inner, "text") as? String
    }
    return text?.trim().orEmpty()
}

internal fun TdApi.ChatFolderInfo.toFolderOverview(unreadCount: Int? = null): TelegramChatFolder {
    val nameField = runCatching { javaClass.getField("name").get(this) }.getOrNull()
    return TelegramChatFolder(
        id = id,
        title = tdFolderDisplayName(nameField).ifBlank { "📁" },
        iconName = icon?.name.orEmpty(),
        unreadChats = unreadCount
    )
}
