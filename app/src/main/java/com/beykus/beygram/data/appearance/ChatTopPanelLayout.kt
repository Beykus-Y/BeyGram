package com.beykus.beygram.data.appearance

enum class ChatTopPanelLayout { Current, Separate, Combined }

val DEFAULT_CHAT_TOP_PANEL_LAYOUT = ChatTopPanelLayout.Combined

fun chatTopPanelLayoutFromStored(
    value: String?,
    fallback: ChatTopPanelLayout = DEFAULT_CHAT_TOP_PANEL_LAYOUT
): ChatTopPanelLayout = ChatTopPanelLayout.entries.firstOrNull { it.name == value } ?: fallback
