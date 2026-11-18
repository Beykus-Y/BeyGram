package com.beykus.beygram.ui.screens.chat

import com.beykus.beygram.domain.model.ForumTopic
import org.junit.Assert.assertEquals
import org.junit.Test

class ForumTopicPinnedOrderTest {
    @Test fun movingAnItemUpOrDownPlacesItAtTheFinalIndex() {
        assertEquals(listOf(2L, 3L, 1L), moveForumTopicId(listOf(1L, 2L, 3L), fromIndex = 0, toIndex = 2))
        assertEquals(listOf(3L, 1L, 2L), moveForumTopicId(listOf(1L, 2L, 3L), fromIndex = 2, toIndex = 0))
        assertEquals(listOf(2L, 1L, 3L), moveForumTopicId(listOf(1L, 2L, 3L), fromIndex = 0, toIndex = 1))
        assertEquals(listOf(1L, 3L, 2L), moveForumTopicId(listOf(1L, 2L, 3L), fromIndex = 2, toIndex = 1))
    }

    @Test fun aMoveKeepsEveryIdExactlyOnce() {
        assertEquals(listOf(8L, 5L, 6L, 7L), moveForumTopicId(listOf(5L, 6L, 7L, 8L), fromIndex = 3, toIndex = 0))
    }

    @Test fun equalOrOutOfRangeIndicesKeepTheOrder() {
        val order = listOf(1L, 2L, 3L)
        assertEquals(order, moveForumTopicId(order, fromIndex = 1, toIndex = 1))
        assertEquals(order, moveForumTopicId(order, fromIndex = -1, toIndex = 0))
        assertEquals(order, moveForumTopicId(order, fromIndex = 0, toIndex = 3))
        assertEquals(emptyList<Long>(), moveForumTopicId(emptyList(), fromIndex = 0, toIndex = 0))
    }

    @Test fun aDraftMovesOnlyPinnedTopicsAndLeavesRegularTopicsBelow() {
        val ordered = orderedForumTopics(
            listOf(
                ForumTopic(2L, "Regular"),
                ForumTopic(3L, "Pinned A", isPinned = true),
                ForumTopic(5L, "Pinned B", isPinned = true),
                ForumTopic(4L, "Regular 2")
            )
        )
        assertEquals(listOf(5L, 3L, 2L, 4L), applyPinnedOrder(ordered, listOf(5L, 3L)).map(ForumTopic::id))
    }

    @Test fun noDraftShowsTheServerOrder() {
        val ordered = orderedForumTopics(
            listOf(ForumTopic(3L, "Pinned A", isPinned = true), ForumTopic(5L, "Pinned B", isPinned = true))
        )
        assertEquals(ordered, applyPinnedOrder(ordered, pinnedOrder = null))
    }

    @Test fun aDraftThatNamesUnknownOrUnpinnedTopicsStillShowsEveryPinnedTopicOnce() {
        val ordered = listOf(
            ForumTopic(3L, "A", isPinned = true),
            ForumTopic(5L, "B", isPinned = true),
            ForumTopic(7L, "C", isPinned = true),
            ForumTopic(2L, "Regular")
        )
        // 9 is no longer pinned, so the draft ignores it; 3 was not named, so it follows the named ones.
        assertEquals(listOf(7L, 5L, 3L, 2L), applyPinnedOrder(ordered, listOf(7L, 5L, 9L)).map(ForumTopic::id))
    }

    @Test fun aRowLandsAfterEveryPinnedRowWhoseCentreIsAboveItsCentre() {
        // Four pinned rows of 100 px with centres 50, 150, 250 and 350; the dragged row starts at rank 0.
        val others = listOf(150f, 250f, 350f)
        assertEquals(0, pinnedDropIndex(baseRank = 0, otherCentres = others, draggedCentre = 120f))
        assertEquals(1, pinnedDropIndex(baseRank = 0, otherCentres = others, draggedCentre = 170f))
        assertEquals(3, pinnedDropIndex(baseRank = 0, otherCentres = others, draggedCentre = 900f))
    }

    @Test fun rowsOffScreenAboveShiftTheDropRank() {
        // Ranks 0 and 1 are off screen; the rows on screen have centres 150 and 250, the dragged row is on screen too.
        assertEquals(3, pinnedDropIndex(baseRank = 2, otherCentres = listOf(150f, 250f), draggedCentre = 200f))
        assertEquals(4, pinnedDropIndex(baseRank = 2, otherCentres = listOf(150f, 250f), draggedCentre = 400f))
    }
}
