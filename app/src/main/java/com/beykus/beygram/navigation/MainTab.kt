package com.beykus.beygram.navigation

import android.content.Context

/** Разделы нижнего меню. Чаты всегда видны, остальные можно скрыть в «Нижнем меню». */
enum class MainTab(val route: AppRoute) {
    Chats(AppRoute.Chats),
    Calls(AppRoute.Calls),
    Contacts(AppRoute.Contacts),
    Settings(AppRoute.Settings);
}

/** Какие разделы скрыты из нижнего меню. Чаты скрыть нельзя, поэтому в [hidden] их никогда нет. */
data class TabVisibility(val hidden: Set<MainTab> = emptySet()) {
    fun isVisible(tab: MainTab): Boolean = tab !in hidden

    fun withVisible(tab: MainTab, visible: Boolean): TabVisibility = when {
        tab == MainTab.Chats -> this
        visible -> copy(hidden = hidden - tab)
        else -> copy(hidden = hidden + tab)
    }
}

/** Настройки нижнего меню хранятся отдельно, чтобы скрытые разделы переживали перезапуск приложения. */
class MainTabPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("navigation", Context.MODE_PRIVATE)

    fun load(): TabVisibility {
        val names = preferences.getStringSet(KEY_HIDDEN, emptySet()).orEmpty()
        val hidden = names.mapNotNull { name -> MainTab.entries.firstOrNull { it.name == name } }.toSet()
        return TabVisibility(hidden - MainTab.Chats)
    }

    fun save(visibility: TabVisibility) {
        preferences.edit().putStringSet(KEY_HIDDEN, visibility.hidden.map { it.name }.toSet()).apply()
    }

    private companion object {
        const val KEY_HIDDEN = "hidden_tabs"
    }
}
