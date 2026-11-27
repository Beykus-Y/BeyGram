package com.beykus.beygram.data.appearance

enum class ChatTopPanelLayout { Current, Separate, Combined }

fun chatTopPanelLayoutFromStored(value: String?): ChatTopPanelLayout =
    ChatTopPanelLayout.entries.firstOrNull { it.name == value } ?: ChatTopPanelLayout.Current
