package com.beykus.beygram.ui.screens.profile

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which profile layout is shown. A is the stable default; C is the experimental adaptive scene (#1138). */
enum class ProfileVariant { A, C }

/** Visual intensity preset for Profile 2.0 C (#1138, #1235). */
enum class ProfileSceneDensity { Calm, Balanced, Rich }

/** Result of tapping the profile avatar. */
enum class AvatarTap { RevealPhoto, OpenGallery }

/**
 * Experiment switch for Profile 2.0 C, the adaptive scene. Off by default in every channel, including
 * Stable. Turning it off always returns the stable profile A. Never switches users to C on update.
 */
object ProfileSceneSettings {
    const val PREFS_NAME = "profile_scene_settings"
    const val KEY_ENABLED = "scene_enabled"
    const val KEY_SCENE_DENSITY = "scene_density"
    private const val KEY_EXPRESSIVE_COVER = "expressive_cover"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _density = MutableStateFlow(ProfileSceneDensity.Balanced)
    val density: StateFlow<ProfileSceneDensity> = _density.asStateFlow()
    private val _expressiveCover = MutableStateFlow(false)
    val expressiveCover: StateFlow<Boolean> = _expressiveCover.asStateFlow()

    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _enabled.value = readBoolean(context, KEY_ENABLED)
            val legacyExpressive = readBoolean(context, KEY_EXPRESSIVE_COVER)
            val storedDensity = readDensity(context, legacyExpressive)
            _density.value = storedDensity
            _expressiveCover.value = storedDensity == ProfileSceneDensity.Rich
            loaded = true
        }
    }

    fun setEnabled(context: Context, value: Boolean) {
        _enabled.value = value
        writeBoolean(context, KEY_ENABLED, value)
    }

    fun setDensity(context: Context, value: ProfileSceneDensity) {
        _density.value = value
        _expressiveCover.value = value == ProfileSceneDensity.Rich
        writeString(context, KEY_SCENE_DENSITY, value.name)
        writeBoolean(context, KEY_EXPRESSIVE_COVER, value == ProfileSceneDensity.Rich)
    }

    fun setExpressiveCover(context: Context, value: Boolean) {
        setDensity(context, if (value) ProfileSceneDensity.Rich else ProfileSceneDensity.Balanced)
    }

    private fun writeBoolean(context: Context, key: String, value: Boolean) {
        try {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(key, value).apply()
        } catch (_: RuntimeException) {
            // The in-memory value still applies for this process.
        }
    }

    private fun writeString(context: Context, key: String, value: String) {
        try {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(key, value).apply()
        } catch (_: RuntimeException) {
            // The in-memory value still applies for this process.
        }
    }

    /** Pure mapping, kept separate so it can be unit tested without Android. */
    fun variantFor(sceneEnabled: Boolean): ProfileVariant = if (sceneEnabled) ProfileVariant.C else ProfileVariant.A

    /** What a tap on the avatar does: in C the first tap reveals the photo, the next opens the gallery. */
    fun avatarTapAction(sceneC: Boolean, photoExpanded: Boolean): AvatarTap =
        if (sceneC && !photoExpanded) AvatarTap.RevealPhoto else AvatarTap.OpenGallery

    /** Back folds a revealed photo first, and only in C. Everywhere else Back keeps its usual meaning. */
    fun backClosesExpansion(sceneC: Boolean, photoExpanded: Boolean): Boolean = sceneC && photoExpanded

    private fun readBoolean(context: Context, key: String): Boolean = try {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(key, false)
    } catch (_: RuntimeException) {
        false
    }

    private fun readDensity(context: Context, legacyExpressive: Boolean): ProfileSceneDensity = try {
        val raw = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SCENE_DENSITY, null)
        parseDensity(raw, legacyExpressive)
    } catch (_: RuntimeException) {
        parseDensity(null, legacyExpressive)
    }

    internal fun parseDensity(raw: String?, legacyExpressive: Boolean): ProfileSceneDensity =
        ProfileSceneDensity.entries.firstOrNull { it.name == raw }
            ?: if (legacyExpressive) ProfileSceneDensity.Rich else ProfileSceneDensity.Balanced
}
