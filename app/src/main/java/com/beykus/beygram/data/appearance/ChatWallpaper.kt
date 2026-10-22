package com.beykus.beygram.data.appearance

/**
 * The background behind the messages of every chat. Colours are ARGB ints.
 * [None] keeps the plain theme background, which is the look BeyGram has always had.
 */
sealed interface ChatWallpaper {
    data object None : ChatWallpaper
    data class Solid(val color: Int) : ChatWallpaper
    data class Gradient(val top: Int, val bottom: Int) : ChatWallpaper

    /** A built-in, resolution-independent vector wallpaper. */
    data class Preset(val id: String, val intensityPercent: Int = DEFAULT_PATTERN_INTENSITY) : ChatWallpaper

    /**
     * A picture kept in the app's own storage under [fileName]. [dimPercent] darkens it, 0..[MAX_DIM_PERCENT].
     * [blurred] softens it on Android 12 and newer; older systems darken it a little more instead.
     */
    data class Photo(val fileName: String, val dimPercent: Int = DEFAULT_DIM_PERCENT, val blurred: Boolean = false) : ChatWallpaper

    companion object {
        const val DEFAULT_PATTERN_INTENSITY = 24
        const val MAX_PATTERN_INTENSITY = 45
        const val DEFAULT_DIM_PERCENT = 20
        const val MAX_DIM_PERCENT = 70
    }
}

/** Solid colours offered in the wallpaper picker. */
internal val WallpaperSolidChoices: List<Int> = listOf(
    0xFFF3E9DC.toInt(), 0xFFDCE9E1.toInt(), 0xFFD8E4F2.toInt(), 0xFFE8DDF0.toInt(), 0xFF2B2622.toInt(), 0xFF1F2A33.toInt()
)

/** Gradients (top, bottom) offered in the wallpaper picker. */
internal val WallpaperGradientChoices: List<Pair<Int, Int>> = listOf(
    0xFFF6D5B7.toInt() to 0xFFE9A68B.toInt(),
    0xFFBFE3D0.toInt() to 0xFF7FB5A0.toInt(),
    0xFFB9D4F5.toInt() to 0xFF8E9DE0.toInt(),
    0xFF3A2E3F.toInt() to 0xFF1B1822.toInt(),
    0xFF16323A.toInt() to 0xFF0B1418.toInt()
)

/** Writes the wallpaper as one string, so it fits one DataStore key and survives rotation in a saveable. */
fun ChatWallpaper.encode(): String = when (this) {
    ChatWallpaper.None -> "none"
    is ChatWallpaper.Solid -> "solid:${color.toUInt().toString(16)}"
    is ChatWallpaper.Gradient -> "gradient:${top.toUInt().toString(16)}:${bottom.toUInt().toString(16)}"
    is ChatWallpaper.Preset -> "preset:$id:${intensityPercent.coerceIn(0, ChatWallpaper.MAX_PATTERN_INTENSITY)}"
    is ChatWallpaper.Photo -> if (blurred) "blurphoto:$dimPercent:$fileName" else "photo:$dimPercent:$fileName"
}

/** Reads what [encode] wrote. Missing or damaged values read as [ChatWallpaper.None]. */
fun chatWallpaperFromStored(stored: String?): ChatWallpaper {
    if (stored == null) return ChatWallpaper.None
    val parts = stored.split(':', limit = 3)
    return when (parts[0]) {
        "solid" -> parts.getOrNull(1)?.let(::parseColor)?.let { ChatWallpaper.Solid(it) }
        "gradient" -> {
            val top = parts.getOrNull(1)?.let(::parseColor)
            val bottom = parts.getOrNull(2)?.let(::parseColor)
            if (top != null && bottom != null) ChatWallpaper.Gradient(top, bottom) else null
        }
        "preset" -> {
            val id = parts.getOrNull(1)
            val intensity = parts.getOrNull(2)?.toIntOrNull()
            if (id != null && wallpaperPreset(id) != null && intensity != null) {
                ChatWallpaper.Preset(id, intensity.coerceIn(0, ChatWallpaper.MAX_PATTERN_INTENSITY))
            } else null
        }
        "photo", "blurphoto" -> {
            val dim = parts.getOrNull(1)?.toIntOrNull()
            val name = parts.getOrNull(2)
            if (dim != null && !name.isNullOrBlank() && isSafeWallpaperFileName(name)) {
                ChatWallpaper.Photo(name, dim.coerceIn(0, ChatWallpaper.MAX_DIM_PERCENT), blurred = parts[0] == "blurphoto")
            } else null
        }
        else -> null
    } ?: ChatWallpaper.None
}

private fun parseColor(hex: String): Int? = hex.toUIntOrNull(16)?.toInt()

/** A stored file name must stay inside the wallpaper folder. */
internal fun isSafeWallpaperFileName(name: String): Boolean =
    name.isNotEmpty() && name.none { it == '/' || it == '\\' } && name != "." && name != ".."
