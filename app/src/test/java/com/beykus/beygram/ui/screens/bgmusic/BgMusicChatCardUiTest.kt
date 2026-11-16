package com.beykus.beygram.ui.screens.bgmusic

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.beykus.beygram.R
import com.beykus.beygram.data.music.bgmusic.BgMusicCacheKey
import com.beykus.beygram.data.music.bgmusic.BgMusicFileParser
import com.beykus.beygram.data.music.bgmusic.BgMusicFormat
import com.beykus.beygram.data.music.bgmusic.BgMusicParsed
import com.beykus.beygram.data.music.bgmusic.manifestArchive
import com.beykus.beygram.data.music.bgmusic.manifestJson
import com.beykus.beygram.data.music.bgmusic.testManifest
import com.beykus.beygram.data.music.bgmusic.testTrack
import com.beykus.beygram.data.music.bgmusic.writeToBytes
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.playback.LocalPlaybackAccountId
import com.beykus.beygram.ui.theme.BeyGramTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The `.bgmusic` chat card and its fallbacks. The files are real archives written by the app's writer and read by the app's
 * parser, so the card is tested on the same path as in the app. Parsing runs on Dispatchers.IO, so tests that need the card
 * on the very first frame read the file ahead of time through the same parser; the cache then answers without I/O.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class BgMusicChatCardUiTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val context get() = RuntimeEnvironment.getApplication()
    private val account = "bgmusic-card-test-account"
    private val fallbackMarker = "fallback-document"

    private val openLabel get() = context.getString(R.string.bgmusic_open_collection)
    private val downloadLabel get() = context.getString(R.string.bgmusic_download)
    private val longPressLabel get() = context.getString(R.string.a11y_message_actions)
    private val unsupportedNote get() = context.getString(R.string.bgmusic_unsupported_version)
    private val noAccountNote get() = context.getString(R.string.bgmusic_preview_no_account)

    private fun bgMusicFile(bytes: ByteArray): File = folder.newFile().apply { writeBytes(bytes) }

    private fun document(fileId: Int, fileName: String = "Morning mix.bgmusic") = MessageMedia(
        kind = MediaKind.Document,
        fileId = fileId,
        fileName = fileName,
        mimeType = BgMusicFormat.MIME_TYPE,
    )

    /** A file that is on the device, as TDLib reports it once the download is complete. */
    private fun readyState(fileId: Int, file: File) = MediaFileState(
        id = fileId,
        size = file.length(),
        localPath = file.absolutePath,
    )

    /** Reads [file] through the app's parser ahead of time, the way the chat does once the file is on the device. */
    private fun parseFirst(fileId: Int, file: File): BgMusicParsed =
        BgMusicFileParser.parse(context, BgMusicCacheKey(account, fileId, file.length()), file)

    private fun setDocument(
        media: MessageMedia,
        file: MediaFileState,
        selectionActive: Boolean = false,
        onDownload: (Int) -> Unit = {},
        onOpen: () -> Unit = {},
        onLongPress: (() -> Unit)? = null,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalPlaybackAccountId provides account) {
                BeyGramTheme {
                    BgMusicChatDocument(
                        media = media,
                        file = file,
                        downloadFile = onDownload,
                        selectionActive = selectionActive,
                        onOpen = onOpen,
                        onLongPress = onLongPress,
                        fallback = { Text(fallbackMarker) },
                    )
                }
            }
        }
    }

    @Test
    fun validCollection_showsTitleTrackCountDurationAndOpenAction() {
        val file = bgMusicFile(writeToBytes(testManifest()))
        parseFirst(1101, file)
        setDocument(document(1101), readyState(1101, file))

        // Test tracks are 181, 182 and 183 seconds long, so the card shows their sum.
        val count = context.resources.getQuantityString(R.plurals.bgmusic_tracks_count, 3, 3)
        val duration = bgMusicDurationText(181L + 182L + 183L)
        compose.onNodeWithText("Morning mix").assertIsDisplayed()
        compose.onNodeWithText(count + " · " + duration).assertIsDisplayed()
        compose.onNodeWithText(openLabel).assertIsDisplayed()
    }

    @Test
    fun collectionWithoutKnownDurations_showsOnlyTheCount() {
        val manifest = testManifest(tracks = listOf(testTrack(1).copy(durationSec = 0L)))
        val file = bgMusicFile(writeToBytes(manifest))
        parseFirst(1102, file)
        setDocument(document(1102), readyState(1102, file))

        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.bgmusic_tracks_count, 1, 1))
            .assertIsDisplayed()
    }

    @Test
    fun selectionMode_tapOpensTheCollection() {
        val file = bgMusicFile(writeToBytes(testManifest()))
        parseFirst(1103, file)
        val opens = AtomicInteger()
        setDocument(document(1103), readyState(1103, file), selectionActive = true, onOpen = { opens.incrementAndGet() })

        compose.onNode(hasClickAction()).performClick()
        compose.waitForIdle()

        assertEquals(1, opens.get())
    }

    @Test
    fun plainTap_opensThePreview_andDoesNotOpenTheCollection() {
        val file = bgMusicFile(writeToBytes(testManifest()))
        parseFirst(1104, file)
        val opens = AtomicInteger()
        setDocument(document(1104), readyState(1104, file), selectionActive = false, onOpen = { opens.incrementAndGet() })

        compose.onNode(hasClickAction()).performClick()
        // The preview is a dialog that shows the no-account note, because the test has no Telegram account.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(noAccountNote).fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(0, opens.get())
    }

    @Test
    fun card_isAButton_withOpenAndLongPressActionLabels() {
        val file = bgMusicFile(writeToBytes(testManifest()))
        parseFirst(1105, file)
        setDocument(document(1105), readyState(1105, file), onLongPress = {})

        val config = compose.onNode(hasClickAction()).fetchSemanticsNode().config
        assertEquals(Role.Button, config[SemanticsProperties.Role])
        assertEquals(openLabel, config.getOrNull(SemanticsActions.OnClick)?.label)
        assertEquals(longPressLabel, config.getOrNull(SemanticsActions.OnLongClick)?.label)
    }

    @Test
    fun notDownloaded_showsDownloadControl_andTapRequestsTheDownload() {
        val downloads = CopyOnWriteArrayList<Int>()
        setDocument(
            document(1106, "Demo mix.bgmusic"),
            MediaFileState(id = 1106, size = 2048L),
            onDownload = { downloads.add(it) },
        )

        compose.onNodeWithText("Demo mix.bgmusic").assertIsDisplayed()
        compose.onNodeWithContentDescription(downloadLabel).assertIsDisplayed()
        val config = compose.onNode(hasClickAction()).fetchSemanticsNode().config
        assertEquals(downloadLabel, config.getOrNull(SemanticsActions.OnClick)?.label)

        compose.onNode(hasClickAction()).performClick()
        compose.waitForIdle()

        assertEquals(listOf(1106), downloads.toList())
    }

    @Test
    fun whileDownloading_hidesDownloadControl_andTapStartsNoSecondDownload() {
        val downloads = CopyOnWriteArrayList<Int>()
        val state = MediaFileState(id = 1107, size = 2048L, downloadedBytes = 1024L, isDownloading = true)
        setDocument(document(1107, "Demo mix.bgmusic"), state, onDownload = { downloads.add(it) })

        compose.onNodeWithText("Demo mix.bgmusic").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithContentDescription(downloadLabel).fetchSemanticsNodes().isEmpty())

        compose.onNode(hasClickAction()).performClick()
        compose.waitForIdle()

        assertTrue(downloads.isEmpty())
    }

    @Test
    fun brokenFile_rendersTheFallbackInsteadOfTheCard() {
        val file = bgMusicFile("this is not a collection".toByteArray())
        assertEquals(BgMusicParsed.Invalid, parseFirst(1108, file))
        setDocument(document(1108), readyState(1108, file))

        compose.onNodeWithText(fallbackMarker).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText(openLabel).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText(unsupportedNote).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun newerFormat_rendersTheFallbackWithAnUpdateNote() {
        val file = bgMusicFile(manifestArchive(manifestJson(formatVersion = "5", minReaderVersion = "5")))
        assertEquals(BgMusicParsed.Unsupported(formatVersion = 5, minReaderVersion = 5), parseFirst(1109, file))
        setDocument(document(1109), readyState(1109, file))

        compose.onNodeWithText(fallbackMarker).assertIsDisplayed()
        compose.onNodeWithText(unsupportedNote).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText(openLabel).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun downloadedFile_isReadOnFirstShow() {
        // No parse ahead of time here: the card has to read the file itself once it is shown.
        val file = bgMusicFile(writeToBytes(testManifest(title = "Async mix")))
        setDocument(document(1110), readyState(1110, file))

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(openLabel).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Async mix").assertIsDisplayed()
    }

    private fun media(
        kind: MediaKind = MediaKind.Document,
        name: String = "",
        mime: String = "application/octet-stream",
    ) = MessageMedia(kind = kind, fileId = 1, fileName = name, mimeType = mime)

    @Test
    fun isBgMusicDocument_nameOrTypeOnlyMakesACandidate() {
        assertTrue(isBgMusicDocument(media(name = "mix.bgmusic")))
        assertTrue(isBgMusicDocument(media(name = "  MIX.BGMUSIC ")))
        assertTrue(isBgMusicDocument(media(mime = BgMusicFormat.MIME_TYPE)))
        assertTrue(isBgMusicDocument(media(mime = "Application/Vnd.Beygram.Music+Zip; charset=binary")))
        // A renamed or mistyped file is still a candidate here; its content is checked only when it is read.
        assertTrue(isBgMusicDocument(media(name = "mix.bgmusic", mime = "text/plain")))

        assertFalse(isBgMusicDocument(media(name = "mix.zip", mime = "application/zip")))
        assertFalse(isBgMusicDocument(media(name = "song.mp3", mime = "audio/mpeg")))
        assertFalse(isBgMusicDocument(media(kind = MediaKind.Audio, name = "mix.bgmusic")))
    }

    @Test
    fun bgMusicDurationText_isMinutesAndSeconds_orHoursWhenNeeded() {
        assertEquals("0:00", bgMusicDurationText(0L))
        assertEquals("0:05", bgMusicDurationText(5L))
        assertEquals("0:59", bgMusicDurationText(59L))
        assertEquals("1:00", bgMusicDurationText(60L))
        assertEquals("59:59", bgMusicDurationText(3_599L))
        assertEquals("1:00:00", bgMusicDurationText(3_600L))
        assertEquals("1:01:01", bgMusicDurationText(3_661L))
        assertEquals("23:59:59", bgMusicDurationText(86_399L))
        assertEquals("0:00", bgMusicDurationText(-5L))
    }
}
