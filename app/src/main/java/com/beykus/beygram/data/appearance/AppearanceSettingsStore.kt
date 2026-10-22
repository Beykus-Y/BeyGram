package com.beykus.beygram.data.appearance

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** SharedPreferences file that held the dark-theme switch before the theme moved to DataStore. */
private const val LEGACY_PREFERENCES = "appearance"
private const val LEGACY_DARK_THEME = "dark_theme"

private val ThemeModeKey = stringPreferencesKey("theme_mode")
private val ThemePresetKey = stringPreferencesKey("theme_preset")
private val BubbleStyleKey = stringPreferencesKey("bubble_style")
private val ListDensityKey = stringPreferencesKey("list_density")
private val AccentColorKey = intPreferencesKey("accent_color")
private val OutgoingBubbleColorKey = intPreferencesKey("outgoing_bubble_color")
private val WallpaperKey = stringPreferencesKey("chat_wallpaper")
private val MessageTextSizeKey = intPreferencesKey("message_text_size_sp")
private val BubbleCornerRadiusKey = intPreferencesKey("bubble_corner_radius_dp")
private val MessageLineSpacingKey = stringPreferencesKey("message_line_spacing")

private val Context.appearanceDataStore by preferencesDataStore(
    name = "appearance",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
    produceMigrations = { context -> listOf(LegacyDarkThemeMigration(context)) }
)

/** Keeps the appearance choice in DataStore. Missing or damaged data reads as the default choice. */
class AppearanceSettingsStore(context: Context) {
    private val dataStore = context.applicationContext.appearanceDataStore

    val config: Flow<AppearanceConfig> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences ->
            AppearanceConfig(
                preset = themePresetFromStored(preferences[ThemePresetKey]),
                mode = themeModeFromStored(preferences[ThemeModeKey]),
                bubbleStyle = bubbleStyleFromStored(preferences[BubbleStyleKey]),
                density = listDensityFromStored(preferences[ListDensityKey]),
                accentColor = preferences[AccentColorKey],
                outgoingBubbleColor = preferences[OutgoingBubbleColorKey],
                wallpaper = chatWallpaperFromStored(preferences[WallpaperKey]),
                messageTextSizeSp = validMessageTextSize(preferences[MessageTextSizeKey] ?: DEFAULT_MESSAGE_TEXT_SIZE_SP),
                bubbleCornerRadiusDp = validBubbleCornerRadius(preferences[BubbleCornerRadiusKey]),
                messageLineSpacing = messageLineSpacingFromStored(preferences[MessageLineSpacingKey])
            )
        }

    suspend fun save(config: AppearanceConfig) {
        dataStore.edit { preferences ->
            preferences[ThemePresetKey] = config.preset.name
            preferences[ThemeModeKey] = config.mode.name
            preferences[BubbleStyleKey] = config.bubbleStyle.name
            preferences[ListDensityKey] = config.density.name
            val accent = config.accentColor
            if (accent != null) preferences[AccentColorKey] = accent else preferences.remove(AccentColorKey)
            val bubble = config.outgoingBubbleColor
            if (bubble != null) preferences[OutgoingBubbleColorKey] = bubble else preferences.remove(OutgoingBubbleColorKey)
            preferences[WallpaperKey] = config.wallpaper.encode()
            preferences[MessageTextSizeKey] = validMessageTextSize(config.messageTextSizeSp)
            val radius = validBubbleCornerRadius(config.bubbleCornerRadiusDp)
            if (radius != null) preferences[BubbleCornerRadiusKey] = radius else preferences.remove(BubbleCornerRadiusKey)
            preferences[MessageLineSpacingKey] = config.messageLineSpacing.name
        }
    }
}

/** Copies the old dark-theme switch into DataStore. It runs only while no theme choice has been stored. */
private class LegacyDarkThemeMigration(private val context: Context) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        migratedThemeMode(currentData.contains(ThemeModeKey), legacyDarkTheme()) != null

    override suspend fun migrate(currentData: Preferences): Preferences {
        val mode = migratedThemeMode(currentData.contains(ThemeModeKey), legacyDarkTheme()) ?: return currentData
        return currentData.toMutablePreferences().apply { this[ThemeModeKey] = mode.name }.toPreferences()
    }

    override suspend fun cleanUp() = Unit

    private fun legacyDarkTheme(): Boolean? {
        val preferences = context.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
        return if (preferences.contains(LEGACY_DARK_THEME)) preferences.getBoolean(LEGACY_DARK_THEME, false) else null
    }
}

/** The mode to store when migrating, or null when there is nothing to migrate. */
internal fun migratedThemeMode(hasStoredMode: Boolean, legacyDarkTheme: Boolean?): ThemeMode? {
    if (hasStoredMode || legacyDarkTheme == null) return null
    return if (legacyDarkTheme) ThemeMode.Dark else ThemeMode.Light
}
