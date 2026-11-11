package com.beykus.beygram.ui.screenshots

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.beykus.beygram.data.appearance.DesignStyle
import com.beykus.beygram.data.appearance.GlassQuality
import com.beykus.beygram.data.appearance.NavigationPlacement
import com.beykus.beygram.data.music.PlaylistTrack
import com.beykus.beygram.ui.components.SearchFieldButton
import com.beykus.beygram.ui.components.beyGramPalette
import com.beykus.beygram.ui.design.designFor
import com.beykus.beygram.ui.design.styledShape
import com.beykus.beygram.ui.screens.music.MusicCardColumn
import com.beykus.beygram.ui.screens.music.MusicCoverCard
import com.beykus.beygram.ui.screens.music.MusicCoverGrid
import com.beykus.beygram.ui.screens.music.MusicGroupHeading
import com.beykus.beygram.ui.screens.music.MusicHomeSource
import com.beykus.beygram.ui.screens.music.MusicQuickTile
import com.beykus.beygram.ui.screens.music.MusicSourceSwitch
import com.beykus.beygram.ui.screens.music.MusicTileGrid
import com.beykus.beygram.ui.screens.music.MusicTrackRow
import com.beykus.beygram.ui.screens.playback.TrackArtwork
import com.beykus.beygram.ui.theme.BeyGramTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot goldens of the Telegram tab of the music screen for every design style, light and dark. The blocks are
 * the real tiles, cover cards, heading, card and track rows over fixed data; the screen itself needs the library and
 * Telegram, which a JVM test does not have.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h760dp-xhdpi", application = Application::class)
class MusicHomeScreenshotTest(private val style: DesignStyle) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun light() = capture(dark = false)

    @Test
    fun dark() = capture(dark = true)

    private fun capture(dark: Boolean) {
        compose.setContent { MusicHomeSample(style, dark) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/music_home_${style.name}_${if (dark) "dark" else "light"}.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = DesignStyle.entries.map { arrayOf<Any>(it) }
    }
}

@Composable
private fun MusicHomeSample(style: DesignStyle, dark: Boolean) {
    val design = designFor(style, GlassQuality.Balanced, NavigationPlacement.Pinned)
    BeyGramTheme(darkTheme = dark, design = design) {
        val palette = beyGramPalette()
        Column(Modifier.fillMaxSize().background(palette.screen)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SearchFieldButton(placeholder = "Поиск музыки", onClick = {})
            }
            MusicSourceSwitch(selected = MusicHomeSource.Telegram, providerLabel = "Яндекс", onSelect = {})
            Box(Modifier.padding(top = 8.dp)) {
                MusicTileGrid(count = SampleTiles.size) { index ->
                    val (icon, title, count) = SampleTiles[index]
                    MusicQuickTile(icon = icon, title = title, count = count, onClick = {})
                }
            }
            MusicGroupHeading(title = "Мои плейлисты")
            MusicCoverGrid(count = 3) { index ->
                if (index == 0) {
                    MusicCoverCard(title = "Новый плейлист", subtitle = null, onClick = {}) { modifier ->
                        Box(modifier.clip(styledShape(14.dp)).background(palette.accentSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, null, tint = palette.accentInk, modifier = Modifier.size(32.dp))
                        }
                    }
                } else {
                    MusicCoverCard(title = SamplePlaylists[index - 1], subtitle = "${index * 12} треков", onClick = {}) { modifier ->
                        TrackArtwork(
                            artworkPath = null,
                            audioPath = null,
                            modifier = modifier,
                            background = palette.accentSoft,
                            tint = palette.accentInk,
                            symbolSize = 32.dp,
                            cornerRadius = 14.dp
                        )
                    }
                }
            }
            MusicGroupHeading(title = "Недавние", actionLabel = "Все")
            MusicCardColumn {
                SampleTracks.forEach { track -> MusicTrackRow(track = track, unavailable = false, onClick = {}) }
            }
        }
    }
}

private val SampleTiles = listOf(
    Triple(Icons.Rounded.Star, "Любимые", "24 трека"),
    Triple(Icons.Rounded.History, "Недавние", "50 треков"),
    Triple(Icons.Rounded.DownloadDone, "Скачанные", "7 треков"),
    Triple(Icons.Rounded.Whatshot, "Часто слушаемые", "12 треков")
)

private val SamplePlaylists = listOf("В дорогу", "Очень длинное название плейлиста")

private val SampleTracks = listOf(
    sampleTrack("1", "Ночной город", "Северный ветер", 214),
    sampleTrack("2", "Очень длинное название трека, которое не помещается", "Исполнитель", 187),
    sampleTrack("3", "Утро", "Ансамбль", 3_725)
)

private fun sampleTrack(id: String, title: String, performer: String, seconds: Int) = PlaylistTrack(
    itemId = id,
    title = title,
    performer = performer,
    album = "",
    durationSeconds = seconds,
    origin = null,
    addedAt = 0L
)
