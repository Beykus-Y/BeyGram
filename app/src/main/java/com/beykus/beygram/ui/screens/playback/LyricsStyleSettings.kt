package com.beykus.beygram.ui.screens.playback

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class LyricsVisualStyle { Editorial, Focus, Reading }

internal enum class LyricsMotionStyle { Smooth, Dynamic, Calm }

internal enum class LyricsTextSize { Compact, Normal, Large }

internal enum class LyricsAlignment { Start, Center }

internal data class LyricsStyleConfig(
    val visual: LyricsVisualStyle = LyricsVisualStyle.Editorial,
    val motion: LyricsMotionStyle = LyricsMotionStyle.Smooth,
    val size: LyricsTextSize = LyricsTextSize.Normal,
    val alignment: LyricsAlignment = LyricsAlignment.Start
)

/**
 * Lightweight, persistent display preferences for the full-height lyrics panel.
 * Stored in SharedPreferences so style changes apply immediately and survive restarts.
 */
internal object LyricsStyleStore {
    private const val PREFS_NAME = "beygram_lyrics_style"
    private const val KEY_VISUAL = "visual"
    private const val KEY_MOTION = "motion"
    private const val KEY_SIZE = "size"
    private const val KEY_ALIGNMENT = "alignment"

    private val lock = Any()
    @Volatile
    private var loaded = false
    private val _config = MutableStateFlow(LyricsStyleConfig())

    fun flow(context: Context): StateFlow<LyricsStyleConfig> {
        ensureLoaded(context)
        return _config.asStateFlow()
    }

    fun update(context: Context, transform: (LyricsStyleConfig) -> LyricsStyleConfig) {
        ensureLoaded(context)
        val next = synchronized(lock) {
            val updated = transform(_config.value)
            _config.value = updated
            updated
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VISUAL, next.visual.name)
            .putString(KEY_MOTION, next.motion.name)
            .putString(KEY_SIZE, next.size.name)
            .putString(KEY_ALIGNMENT, next.alignment.name)
            .apply()
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val visual = prefs.getString(KEY_VISUAL, null)
                ?.let { runCatching { LyricsVisualStyle.valueOf(it) }.getOrNull() }
                ?: LyricsVisualStyle.Editorial
            val motion = prefs.getString(KEY_MOTION, null)
                ?.let { runCatching { LyricsMotionStyle.valueOf(it) }.getOrNull() }
                ?: LyricsMotionStyle.Smooth
            val size = prefs.getString(KEY_SIZE, null)
                ?.let { runCatching { LyricsTextSize.valueOf(it) }.getOrNull() }
                ?: LyricsTextSize.Normal
            val alignment = prefs.getString(KEY_ALIGNMENT, null)
                ?.let { runCatching { LyricsAlignment.valueOf(it) }.getOrNull() }
                ?: LyricsAlignment.Start
            _config.value = LyricsStyleConfig(visual, motion, size, alignment)
            loaded = true
        }
    }
}

@Composable
internal fun rememberLyricsStyleConfig(): LyricsStyleConfig {
    val context = LocalContext.current
    val flow = remember(context) { LyricsStyleStore.flow(context) }
    return flow.collectAsStateWithLifecycle().value
}
