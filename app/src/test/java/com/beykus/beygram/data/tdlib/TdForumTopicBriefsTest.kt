package com.beykus.beygram.data.tdlib

import com.beykus.beygram.domain.model.ForumTopic
import com.beykus.beygram.domain.model.ForumTopicBrief
import com.beykus.beygram.domain.model.ForumTopicIcon
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TdForumTopicBriefsTest {
    private val chat = 500L

    /** Records every question sent to TDLib and keeps its answer callback until the test answers it. */
    private class Questions {
        val asked = mutableListOf<ForumTopicKey>()
        private val open = HashMap<ForumTopicKey, (ForumTopicBrief?) -> Unit>()

        val fetch: (ForumTopicKey, (ForumTopicBrief?) -> Unit) -> Unit = { key, onAnswer ->
            asked += key
            open[key] = onAnswer
        }

        fun answer(key: ForumTopicKey, brief: ForumTopicBrief?) {
            open.remove(key)?.invoke(brief)
        }
    }

    private fun listedTopic(id: Long, title: String) = ForumTopic(id = id, title = title)

    private fun info(chatId: Long, topicId: Int, name: String) = TdApi.ForumTopicInfo(
        chatId, topicId, name, null, 0, null, false, false, false, false, false
    )

    @Test fun anUnknownTopicIsAskedOnceAndTakesTheAnswer() {
        val questions = Questions()
        val briefs = TdForumTopicBriefs(questions.fetch)

        val flow = briefs.observe(chat, 7L)
        assertNull(flow.value)
        assertEquals(listOf(ForumTopicKey(chat, 7L)), questions.asked)

        questions.answer(ForumTopicKey(chat, 7L), ForumTopicBrief(id = 7L, title = "Ideas"))
        assertEquals("Ideas", flow.value?.title)
    }

    @Test fun aSecondReaderDoesNotAskAgainWhileTheAnswerIsOpen() {
        val questions = Questions()
        val briefs = TdForumTopicBriefs(questions.fetch)

        assertNull(briefs.observe(chat, 7L).value)
        assertNull(briefs.observe(chat, 7L).value)

        assertEquals(1, questions.asked.size)
    }

    @Test fun aTopicTdlibCannotDescribeGetsAnUnresolvedBriefAndIsNotAskedAgain() {
        val questions = Questions()
        val briefs = TdForumTopicBriefs(questions.fetch)

        val flow = briefs.observe(chat, 7L)
        assertNull(flow.value)
        questions.answer(ForumTopicKey(chat, 7L), null)

        assertEquals(7L, flow.value?.id)
        assertEquals("", flow.value?.title)
        assertEquals("", briefs.observe(chat, 7L).value?.title)
        assertEquals(1, questions.asked.size)
    }

    @Test fun aQuestionThatCannotBeSentIsRecordedAsUnresolved() {
        val briefs = TdForumTopicBriefs(fetch = { _, _ -> throw IllegalStateException("client closed") })

        val flow = briefs.observe(chat, 7L)

        assertEquals("", flow.value?.title)
    }

    @Test fun anUpdateNamesATopicWithoutAsking() {
        val questions = Questions()
        val briefs = TdForumTopicBriefs(questions.fetch)

        briefs.receive(info(chat, 7, "Renamed"))

        assertEquals("Renamed", briefs.observe(chat, 7L).value?.title)
        assertTrue(questions.asked.isEmpty())
    }

    @Test fun aRenameReachesAnObserverThatIsAlreadyWatching() {
        val questions = Questions()
        val briefs = TdForumTopicBriefs(questions.fetch)
        briefs.remember(chat, listOf(listedTopic(7L, "Old name")))
        val flow = briefs.observe(chat, 7L)
        assertEquals("Old name", flow.value?.title)

        briefs.receive(info(chat, 7, "New name"))

        assertEquals("New name", flow.value?.title)
        assertTrue(questions.asked.isEmpty())
    }

    @Test fun aForumListingNamesItsTopicsWithTheirIconAndGeneralFlagWithoutAsking() {
        val questions = Questions()
        val briefs = TdForumTopicBriefs(questions.fetch)

        briefs.remember(
            chat,
            listOf(
                ForumTopic(id = 1L, title = "General", isGeneral = true, isNameImplicit = true),
                ForumTopic(id = 9L, title = "Ideas", icon = ForumTopicIcon(colorArgb = 0xFFD67E, customEmojiId = 42L))
            )
        )

        assertEquals(
            ForumTopicBrief(id = 1L, title = "General", isGeneral = true, isNameImplicit = true),
            briefs.observe(chat, 1L).value
        )
        assertEquals(
            ForumTopicBrief(id = 9L, title = "Ideas", icon = ForumTopicIcon(colorArgb = 0xFFD67E, customEmojiId = 42L)),
            briefs.observe(chat, 9L).value
        )
        assertTrue(questions.asked.isEmpty())
    }

    @Test fun aTopicInfoKeepsItsColourAndGeneralFlags() {
        val brief = forumTopicBrief(
            TdApi.ForumTopicInfo(chat, 1, "General", TdApi.ForumTopicIcon(0x6FB9F0, 0L), 0, null, true, false, false, false, true)
        )

        assertEquals(
            ForumTopicBrief(
                id = 1L,
                title = "General",
                icon = ForumTopicIcon(colorArgb = 0x6FB9F0, customEmojiId = 0L),
                isGeneral = true,
                isNameImplicit = true
            ),
            brief
        )
    }

    @Test fun anIdleBriefBeyondTheCapacityIsAskedAgainWhenShownAgain() {
        val questions = Questions()
        val briefs = TdForumTopicBriefs(questions.fetch, capacity = 8)
        briefs.remember(chat, (1L..20L).map { listedTopic(it, "Topic $it") })

        assertNull(briefs.observe(chat, 1L).value)
        assertEquals(listOf(ForumTopicKey(chat, 1L)), questions.asked)

        assertEquals("Topic 20", briefs.observe(chat, 20L).value?.title)
        assertEquals(1, questions.asked.size)
    }

    @Test fun aFailedTopicIsForgottenWithTheOthersWhenTheCacheIsFull() {
        val questions = Questions()
        val briefs = TdForumTopicBriefs(questions.fetch, capacity = 8)
        assertNull(briefs.observe(chat, 1L).value)
        questions.answer(ForumTopicKey(chat, 1L), null)

        briefs.remember(chat, (2L..20L).map { listedTopic(it, "Topic $it") })

        assertNull(briefs.observe(chat, 1L).value)
        assertEquals(2, questions.asked.size)
    }
}
