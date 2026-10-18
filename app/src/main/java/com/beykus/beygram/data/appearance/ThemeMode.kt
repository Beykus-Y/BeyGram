package com.beykus.beygram.data.appearance

/**
 * The theme the user picked. [FollowSystem] tracks the system's dark setting and changes with it.
 * Choices are stored by name, so a missing or unknown stored value means [FollowSystem].
 */
enum class ThemeMode {
    FollowSystem, Light, Dark;

    /** Whether this choice shows the dark theme, given the system's current setting. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        FollowSystem -> systemDark
        Light -> false
        Dark -> true
    }
}

/** Reads a stored [ThemeMode] name. Missing or unknown values fall back to [ThemeMode.FollowSystem]. */
internal fun themeModeFromStored(stored: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.FollowSystem
