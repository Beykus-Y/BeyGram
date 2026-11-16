package com.beykus.beygram.ui.screens.bgmusic

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.beykus.beygram.R
import com.beykus.beygram.data.music.FakeClock
import com.beykus.beygram.data.music.FakeMusicPersistence
import com.beykus.beygram.data.music.MusicLibrary
import com.beykus.beygram.data.music.MusicLibraryRepository
import com.beykus.beygram.data.music.MusicPlaylist
import com.beykus.beygram.data.music.TEST_ACCOUNT
import com.beykus.beygram.data.music.bgmusic.BgMusicAuthor
import com.beykus.beygram.data.music.bgmusic.BgMusicParsed
import com.beykus.beygram.data.music.bgmusic.TEST_PLAYLIST_ID
import com.beykus.beygram.data.music.bgmusic.testManifest
import com.beykus.beygram.data.music.repositoryFor
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * The `.bgmusic` preview dialog. The library is a real [MusicLibraryRepository] over an in-memory store, passed in through
 * [BgMusicPreviewScreen]'s `libraryOverride`. No Telegram account is given, so no TDLib client is needed: the preview then
 * shows every track as not in the chats, and offers no playback.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class BgMusicPreviewScreenUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var persistence: FakeMusicPersistence
    private lateinit var library: MusicLibraryRepository

    @Before
    fun setUp() {
        persistence = FakeMusicPersistence()
        library = repositoryFor(persistence, FakeClock())
    }

    private fun str(id: Int): String = context.getString(id)

    /** Shows the test account's library, as the app does before the preview is used. Nothing is saved by this. */
    private fun openAccountLibrary() = runBlocking { library.setActiveAccount(TEST_ACCOUNT) }

    private fun setPreview(
        parsed: BgMusicParsed,
        onDismiss: () -> Unit = {},
        onOpen: ((String) -> Unit)? = null,
    ) {
        compose.setContent {
            BeyGramTheme {
                BgMusicPreviewScreen(
                    parsed = parsed,
                    onDismiss = onDismiss,
                    onOpenLocalPlaylist = onOpen,
                    libraryOverride = library,
                )
            }
        }
    }

    @Test
    fun showsTitleAuthorAndTracks_andMarksEveryTrackNotInChats_withoutAnAccount() {
        openAccountLibrary()
        setPreview(BgMusicParsed.Playlist(testManifest(author = BgMusicAuthor(name = "Ann")), coverPath = null))
        compose.waitForIdle()

        compose.onNodeWithText("Morning mix").assertIsDisplayed()
        compose.onNodeWithText("Coffee and rain").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.bgmusic_author_line, "Ann")).assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.bgmusic_tracks_count, 3, 3))
            .assertIsDisplayed()
        compose.onNodeWithText("Track 1").assertIsDisplayed()
        compose.onNodeWithText("Track 2").assertIsDisplayed()
        compose.onNodeWithText("Track 3").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.bgmusic_preview_no_account)).assertIsDisplayed()

        // Without an account nothing can be found, so every track carries the "not in your chats" state.
        val notInChats = SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription,
            str(R.string.bgmusic_track_unavailable),
        )
        assertEquals(3, compose.onAllNodes(notInChats).fetchSemanticsNodes().size)
        assertTrue(compose.onAllNodesWithText(str(R.string.bgmusic_add_to_playlist)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun opening_savesNothing_dismissesNothing_andPlaysNothing() {
        openAccountLibrary()
        val dismissed = AtomicInteger()
        val opened = CopyOnWriteArrayList<String>()
        setPreview(
            BgMusicParsed.Playlist(testManifest(), coverPath = null),
            onDismiss = { dismissed.incrementAndGet() },
            onOpen = { opened.add(it) },
        )
        compose.waitForIdle()

        // Saving is available, but only a tap on it saves.
        compose.onNodeWithText(str(R.string.bgmusic_action_save)).assertIsEnabled()
        assertEquals(0, persistence.saves)
        assertTrue(persistence.stored.isEmpty())
        assertTrue(library.library.value.playlists.none { it.sourcePlaylistId == TEST_PLAYLIST_ID })
        assertEquals(0, dismissed.get())
        assertTrue(opened.isEmpty())
        // Playback needs an account; without one the play control is not shown.
        assertTrue(compose.onAllNodesWithText(str(R.string.bgmusic_listen_available)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun saveTap_storesTheCollectionOnce_thenOffersOpeningIt() {
        openAccountLibrary()
        val opened = CopyOnWriteArrayList<String>()
        setPreview(BgMusicParsed.Playlist(testManifest(), coverPath = null), onOpen = { opened.add(it) })
        compose.waitForIdle()

        compose.onNodeWithText(str(R.string.bgmusic_action_save)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { persistence.saves == 1 }
        // The library now holds the copy, so the same file reads as already saved and the save action is off.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(str(R.string.bgmusic_import_already_saved)).fetchSemanticsNodes().isNotEmpty()
        }

        val saved = library.library.value.playlists.single { it.sourcePlaylistId == TEST_PLAYLIST_ID }
        assertEquals("Morning mix", saved.title)
        compose.onNodeWithText(str(R.string.bgmusic_import_already_saved)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.bgmusic_action_save)).assertIsNotEnabled()

        compose.onNodeWithText(str(R.string.bgmusic_action_open)).performClick()
        compose.waitForIdle()
        assertEquals(listOf(saved.id), opened.toList())
        assertEquals(1, persistence.saves)
    }

    @Test
    fun newerFileThanSavedCopy_asksFirst_andUpdatesOnlyWhenConfirmed() {
        persistence.stored[TEST_ACCOUNT] = MusicLibrary(
            accountId = TEST_ACCOUNT,
            playlists = listOf(
                MusicPlaylist(
                    id = "local-copy",
                    title = "Old copy",
                    sourcePlaylistId = TEST_PLAYLIST_ID,
                    sourceRevision = 6L,
                ),
            ),
        )
        openAccountLibrary()
        setPreview(BgMusicParsed.Playlist(testManifest(), coverPath = null))
        compose.waitForIdle()

        compose.onNodeWithText(str(R.string.bgmusic_action_save)).performClick()
        compose.onNodeWithText(str(R.string.bgmusic_import_newer_title)).assertIsDisplayed()
        assertEquals(0, persistence.saves)

        compose.onNodeWithText(str(R.string.common_cancel)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(str(R.string.bgmusic_import_newer_title)).fetchSemanticsNodes().isEmpty()
        }
        assertEquals(0, persistence.saves)

        compose.onNodeWithText(str(R.string.bgmusic_action_save)).performClick()
        compose.onNodeWithText(str(R.string.bgmusic_action_update)).performClick()
        compose.waitUntil(timeoutMillis = 5_000) { persistence.saves == 1 }

        assertEquals("Morning mix", library.library.value.playlist("local-copy")?.title)
    }

    @Test
    fun closeButton_dismissesThePreview_andSavesNothing() {
        openAccountLibrary()
        val dismissed = AtomicInteger()
        setPreview(BgMusicParsed.Playlist(testManifest(), coverPath = null), onDismiss = { dismissed.incrementAndGet() })
        compose.waitForIdle()

        compose.onNodeWithContentDescription(str(R.string.bgmusic_close)).performClick()
        compose.waitForIdle()

        assertEquals(1, dismissed.get())
        assertEquals(0, persistence.saves)
    }

    @Test
    fun unsupportedFile_showsTheUpdateNote_insteadOfTracks() {
        setPreview(BgMusicParsed.Unsupported(formatVersion = 5, minReaderVersion = 5))
        compose.waitForIdle()

        compose.onNodeWithText(str(R.string.bgmusic_unsupported_version)).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Track 1").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun damagedFile_showsTheInvalidNote_withTheDefaultTitle() {
        setPreview(BgMusicParsed.Invalid)
        compose.waitForIdle()

        compose.onNodeWithText(str(R.string.bgmusic_invalid_file)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.bgmusic_preview_default_title)).assertIsDisplayed()
    }
}
