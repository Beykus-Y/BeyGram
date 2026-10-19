package com.beykus.beygram.data.appearance

/**
 * The brightness the user picked. [FollowSystem] tracks the system's dark setting and changes with it.
 * [Amoled] is the dark theme with a pure black background, whatever the system setting says.
 * Choices are stored by name, so a missing or unknown stored value means [FollowSystem].
 */
enum class ThemeMode {
    FollowSystem, Light, Dark, Amoled;

    /** Whether this choice shows the dark theme, given the system's current setting. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        FollowSystem -> systemDark
        Light -> false
        Dark, Amoled -> true
    }
}

/** Reads a stored [ThemeMode] name. Missing or unknown values fall back to [ThemeMode.FollowSystem]. */
internal fun themeModeFromStored(stored: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.FollowSystem
