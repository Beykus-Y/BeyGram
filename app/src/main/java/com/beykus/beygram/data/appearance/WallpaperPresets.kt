package com.beykus.beygram.data.appearance

/** Original, built-in wallpapers rendered entirely in Compose; no images or network requests. */
enum class WallpaperMotif { Waves, Dots, Orbits, Doodles, Leaves, Arches, Grid, Confetti }

data class WallpaperPresetSpec(
    val id: String,
    val motif: WallpaperMotif,
    val lightColors: List<Int>,
    val darkColors: List<Int>,
    val lightInk: Int,
    val darkInk: Int
) {
    init {
        require(lightColors.size == 3 && darkColors.size == 3)
    }

    /** Every named preset has a dark counterpart; AMOLED stays truly black under the ornament. */
    fun colors(dark: Boolean, amoled: Boolean): List<Int> = when {
        amoled -> List(3) { 0xFF000000.toInt() }
        dark -> darkColors
        else -> lightColors
    }

    fun patternInk(dark: Boolean): Int = if (dark) darkInk else lightInk
}

private fun spec(
    id: String, motif: WallpaperMotif,
    a: Long, b: Long, c: Long,
    d: Long, e: Long, f: Long,
    inkLight: Long, inkDark: Long
) = WallpaperPresetSpec(
    id, motif,
    listOf(a.toInt(), b.toInt(), c.toInt()),
    listOf(d.toInt(), e.toInt(), f.toInt()),
    inkLight.toInt(), inkDark.toInt()
)

/** Stable identifiers are persisted via `ChatWallpaper.Preset`. Never reorder by changing the IDs. */
val WallpaperPresets: List<WallpaperPresetSpec> = listOf(
    spec("lavender-flow", WallpaperMotif.Waves,
        0xFFF9F4FF, 0xFFF1E7FF, 0xFFE8E4FF, 0xFF18132B, 0xFF272040, 0xFF19172F, 0xFF6A4FBB, 0xFFC5ACFF),
    spec("arctic-blue", WallpaperMotif.Arches,
        0xFFF3FAFF, 0xFFDFEFFF, 0xFFCFE7F8, 0xFF0E1B2C, 0xFF122C46, 0xFF172033, 0xFF4476AB, 0xFF9CCEF9),
    spec("mint-air", WallpaperMotif.Leaves,
        0xFFF1FFF8, 0xFFDDF6E8, 0xFFD0ECE2, 0xFF102821, 0xFF173A33, 0xFF102B27, 0xFF3A9376, 0xFF94E2BB),
    spec("sunset-peach", WallpaperMotif.Doodles,
        0xFFFFF5EB, 0xFFFFDDCD, 0xFFF7D4E3, 0xFF32212A, 0xFF482D3B, 0xFF2C2134, 0xFFCE7B75, 0xFFFFB6AB),
    spec("soft-rose", WallpaperMotif.Orbits,
        0xFFFFF5FA, 0xFFF8E0ED, 0xFFEADFF7, 0xFF2B1728, 0xFF3C203B, 0xFF271F39, 0xFFAE5D8E, 0xFFFFADD4),
    spec("blue-notes", WallpaperMotif.Doodles,
        0xFFF0F5FF, 0xFFDDE9FF, 0xFFE2E1FA, 0xFF15223E, 0xFF202C55, 0xFF181D3C, 0xFF516DB5, 0xFFABC0FF),
    spec("graphite-wave", WallpaperMotif.Waves,
        0xFFF5F6F8, 0xFFE6E9F0, 0xFFDDE1EA, 0xFF15171E, 0xFF20242E, 0xFF191C26, 0xFF69748B, 0xFFAAB8CF),
    spec("forest-whisper", WallpaperMotif.Leaves,
        0xFFF1F7EB, 0xFFD7E8D2, 0xFFBBDAC9, 0xFF12251F, 0xFF213D31, 0xFF142C28, 0xFF527E60, 0xFFA3DAB2),
    spec("deep-night", WallpaperMotif.Orbits,
        0xFFF0EEFF, 0xFFD9DDF7, 0xFFC6D5F0, 0xFF101323, 0xFF1E2343, 0xFF141D35, 0xFF6861B4, 0xFFABA7FF),
    spec("violet-mesh", WallpaperMotif.Grid,
        0xFFF9F2FF, 0xFFE7DDFA, 0xFFDCE3FA, 0xFF231830, 0xFF33234C, 0xFF25213A, 0xFF9269B3, 0xFFD5AFFF),
    spec("ocean-current", WallpaperMotif.Waves,
        0xFFF0FDFF, 0xFFD4F1F5, 0xFFBCE4EE, 0xFF0C2530, 0xFF103945, 0xFF0E2535, 0xFF378899, 0xFF84D8DF),
    spec("amber-glow", WallpaperMotif.Confetti,
        0xFFFFF8E8, 0xFFFFE6BB, 0xFFF6D6AF, 0xFF2F2518, 0xFF453022, 0xFF2D241D, 0xFFBF8548, 0xFFFFD287),
    spec("sky-paper", WallpaperMotif.Arches,
        0xFFF8FCFF, 0xFFEDF4FF, 0xFFE0EDF8, 0xFF152A38, 0xFF213B4C, 0xFF172B3C, 0xFF6386A5, 0xFFA7D6EC),
    spec("terracotta-dust", WallpaperMotif.Dots,
        0xFFFFF2E9, 0xFFF4DED1, 0xFFF5DDD3, 0xFF2E211E, 0xFF493029, 0xFF2A2322, 0xFFBA7562, 0xFFF1AE93),
    spec("silver-mist", WallpaperMotif.Grid,
        0xFFF9FAFD, 0xFFEAEEF4, 0xFFDDE5EE, 0xFF1C202A, 0xFF2C3341, 0xFF1C2534, 0xFF79889B, 0xFFB7C5E1),
    spec("emerald-tide", WallpaperMotif.Orbits,
        0xFFF3FFF6, 0xFFD6F4DE, 0xFFBAE7D2, 0xFF0A2B26, 0xFF12463D, 0xFF0A302B, 0xFF3E9675, 0xFF91E8C8)
)

private val WallpaperPresetsById: Map<String, WallpaperPresetSpec> = WallpaperPresets.associateBy { it.id }

fun wallpaperPreset(id: String): WallpaperPresetSpec? = WallpaperPresetsById[id]
