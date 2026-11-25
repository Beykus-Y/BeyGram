package com.beykus.beygram.ui.screens.chat

import android.app.Application
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.beykus.beygram.domain.model.ChatMessage
import com.beykus.beygram.domain.model.MediaFileState
import com.beykus.beygram.domain.model.MediaKind
import com.beykus.beygram.domain.model.MessageMedia
import com.beykus.beygram.domain.model.MessageSearchPage
import com.beykus.beygram.ui.theme.BeyGramTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end Compose integration test verifying real touch swipes on [MediaViewer] wired to
 * [SharedMediaWindow] and [resolveMediaViewerPages] under TDLib's `offset = 0` pagination contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi", application = Application::class)
class MediaViewerSwipeIntegrationTest {

    @get:Rule
    val compose = createComposeRule()

    private fun mediaMessage(
        id: Long,
        kind: MediaKind = MediaKind.Photo,
    ) = ChatMessage(
        id = id,
        chatId = 1L,
        text = "msg-$id",
        time = "12:00",
        isOutgoing = false,
        media = MessageMedia(kind = kind, fileId = id.toInt()),
    )

    @Test
    fun swipeFromNewestPhotoToOlderAndBackPreservesNewestPhoto() {
        val allMedia = mutableListOf(
            mediaMessage(50L),
            mediaMessage(40L, MediaKind.Video),
            mediaMessage(30L),
            mediaMessage(20L),
            mediaMessage(10L),
        )
        var history by mutableStateOf(listOf(allMedia[0], allMedia[1]))
        var selectedId by mutableStateOf<Long?>(50L)
        var resolvedIds by mutableStateOf<List<Long>>(emptyList())
        val fileState = MutableStateFlow(MediaFileState(id = 0))

        compose.setContent {
            BeyGramTheme {
                val window = remember {
                    SharedMediaWindow(
                        load = { from, newer ->
                            val page = if (newer) {
                                allMedia.filter { it.id >= from }
                            } else {
                                allMedia.filter { from == 0L || it.id < from }
                            }
                            Result.success(MessageSearchPage(messages = page, nextFromMessageId = 0L))
                        },
                        failure = { "failed" },
                    )
                }
                LaunchedEffect(selectedId) {
                    val id = selectedId ?: return@LaunchedEffect
                    val msg = history.firstOrNull { it.id == id }
                    if (window.messages.none { it.id == id }) {
                        window.restart(id, anchorMessage = msg?.takeUnless { it.isViewOnce })
                        window.loadOlder()
                    }
                    window.loadNear(id) { !it.isViewOnce }
                }
                val id = selectedId
                if (id != null) {
                    val windowPages = window.messages.mapNotNull { m ->
                        m.media?.let { MediaViewerItem(m.id, it, m.isViewOnce, 1L, m.text) }
                    }
                    val historyPages = history.mapNotNull { m ->
                        m.media?.let { MediaViewerItem(m.id, it, m.isViewOnce, 1L, m.text) }
                    }.sortedBy { it.messageId }
                    val pages = resolveMediaViewerPages(
                        windowPages = windowPages,
                        historyPages = historyPages,
                        selectedId = id,
                        fallbackItem = null,
                        historyMessageIdRange = history.minOf { it.id }..history.maxOf { it.id },
                        liveHistoryIds = history.mapTo(HashSet()) { it.id },
                    )
                    resolvedIds = pages.map { it.messageId }
                    MediaViewer(
                        pages = pages,
                        selectedId = id,
                        onSelect = { selectedId = it },
                        observeFile = { fileState },
                        downloadFile = {},
                        onViewed = {},
                        onClose = { selectedId = null },
                        title = "Test",
                        subtitle = "",
                    )
                }
            }
        }

        compose.waitUntil(timeoutMillis = 3_000) {
            resolvedIds == listOf(10L, 20L, 30L, 40L, 50L)
        }
        assertEquals(50L, selectedId)

        // Swipe right -> move to older photo (40)
        compose.onNode(isDialog()).performTouchInput { swipeRight() }
        compose.waitUntil(timeoutMillis = 3_000) { selectedId == 40L }
        assertTrue(50L in resolvedIds)

        // Swipe left -> return to newest photo (50)
        compose.onNode(isDialog()).performTouchInput { swipeLeft() }
        compose.waitUntil(timeoutMillis = 3_000) { selectedId == 50L }
        assertEquals(50L, selectedId)

        // Close and reopen on newest photo -> still works
        compose.runOnUiThread { selectedId = null }
        compose.waitForIdle()
        compose.runOnUiThread { selectedId = 50L }
        compose.waitUntil(timeoutMillis = 3_000) {
            selectedId == 50L && resolvedIds == listOf(10L, 20L, 30L, 40L, 50L)
        }
    }

    @Test
    fun viewOnceStealthGateIsolatesUnconsentedViewOnceFromSwipes() {
        val gate = ViewOnceStealthGate()
        val pages = listOf(
            MediaViewerItem(10L, MessageMedia(MediaKind.Photo, 10), viewOnce = false, chatId = 1L),
            MediaViewerItem(20L, MessageMedia(MediaKind.Photo, 20), viewOnce = true, chatId = 1L),
            MediaViewerItem(30L, MessageMedia(MediaKind.Photo, 30), viewOnce = false, chatId = 1L),
        )
        // When Stealth blocks opening without consent, swiping from 10L skips unconsented view-once 20L.
        val filtered = gate.swipeable(pages, chatId = 1L, selectedId = 10L, allowed = false)
        assertEquals(listOf(10L, 30L), filtered.map { it.messageId })
    }
}
