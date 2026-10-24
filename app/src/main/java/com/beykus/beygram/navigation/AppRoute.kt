package com.beykus.beygram.navigation

enum class AppRoute {
    Start,
    LoginPhone,
    LoginCode,
    LoginPassword,
    Chats,
    Archive,
    Chat,
    MessageThread,
    ChatInfo,
    ChannelStatistics,
    ChatMembers,
    MemberProfile,
    MyProfile,
    EditProfile,
    Notifications,
    ConnectionDiagnostics,
    Calls,
    Contacts,
    Settings,
    SecondaryAccounts,
    AccountPicker,
    SecondaryChats,
    NavigationSettings,
    LastSeenSettings,
    PrivacySettings,
    BlockedSenders,
    Updates,
    VideoMessageSettings,
    StorageSettings,
    Diagnostics,
    Resources,
    GlobalSearch,
    ReportProblem,
    Proxy,
    ProxyConnect,
    Appearance,
    Language,
    Sessions,
    NewChat,
    CreateGroup,
    CreateChannel
}

/**
 * Reads a route saved by an earlier process. The old ThemeEditor route now lives inside Appearance, so a task
 * restored after an in-place update opens the new section instead of crashing on a removed enum value.
 */
internal fun appRouteFromStored(value: String?): AppRoute {
    if (value == LEGACY_THEME_EDITOR_ROUTE) return AppRoute.Appearance
    // The experimental account manager and picker were replaced by the shared
    // bottom-sheet switcher. A task restored from an older build opens Chats.
    if (value == AppRoute.AccountPicker.name ||
        value == AppRoute.SecondaryAccounts.name ||
        value == AppRoute.SecondaryChats.name
    ) return AppRoute.Chats
    return AppRoute.entries.firstOrNull { it.name == value } ?: AppRoute.Start
}

private const val LEGACY_THEME_EDITOR_ROUTE = "ThemeEditor"
