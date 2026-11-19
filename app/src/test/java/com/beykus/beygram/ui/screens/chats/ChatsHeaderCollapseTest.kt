package com.beykus.beygram.ui.screens.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatsHeaderCollapseTest {
    // Phone metrics in px: a 56 px title block, a 144 x 48 actions group, a 56 px search pill, a 14 px header gap
    // and an 8 px gap between the pill and the actions.
    private val width = 360f
    private val titleH = 56f
    private val actionsW = 144f
    private val actionsH = 48f
    private val searchH = 56f
    private val gap = 14f
    private val actionGap = 8f
    private val titleBottom = (maxOf(titleH, actionsH) + titleH) / 2f

    private fun geometry(progress: Float) =
        chatsHeaderGeometry(progress, width, titleH, actionsW, actionsH, searchH, gap, actionGap)

    @Test fun expandedHeaderShowsTheTitleRowAndTheFullWidthSearch() {
        val g = geometry(0f)
        assertEquals(126f, g.topBlockHeight, 0.01f)
        assertEquals(70f, g.searchY, 0.01f)
        assertEquals(width, g.searchWidth, 0.01f)
        assertEquals(4f, g.actionsY, 0.01f)
        assertEquals(0f, g.titleTranslationY, 0.01f)
        assertEquals(1f, g.titleScale, 0.01f)
        assertEquals(1f, g.titleAlpha, 0.01f)
        assertEquals(70f, g.rangePx, 0.01f)
    }

    @Test fun collapsedHeaderIsOneRowWithTheSearchBesideTheActions() {
        val g = geometry(1f)
        assertEquals(56f, g.topBlockHeight, 0.01f)
        assertEquals(0f, g.searchY, 0.01f)
        assertEquals(width - actionsW - actionGap, g.searchWidth, 0.01f)
        assertEquals(4f, g.actionsY, 0.01f)
        assertEquals(0.8f, g.titleScale, 0.01f)
        assertEquals(0f, g.titleAlpha, 0.01f)
    }

    @Test fun headerHeightShrinksMonotonicallyWithProgress() {
        var previous = Float.MAX_VALUE
        for (step in 0..200) {
            val height = geometry(step / 200f).topBlockHeight
            assertTrue("progress ${step / 200f}", height <= previous)
            previous = height
        }
    }

    @Test fun searchNeverOverlapsTheActionsWhileTheyShareAVerticalBand() {
        for (step in 0..200) {
            val progress = step / 200f
            val g = geometry(progress)
            if (g.searchY < g.actionsY + actionsH) {
                assertTrue("progress $progress", g.searchWidth <= width - actionsW - actionGap + 0.5f)
            }
        }
    }

    @Test fun titleStaysAboveTheSearchWhileItMoves() {
        for (step in 0..200) {
            val progress = step / 200f
            val g = geometry(progress)
            assertTrue("progress $progress", titleBottom + g.titleTranslationY <= g.searchY + 0.01f)
        }
    }

    @Test fun titleShrinksToEightTenthsBeforeItFades() {
        assertEquals(0.8f, geometry(0.35f).titleScale, 0.01f)
        assertEquals(1f, geometry(0.35f).titleAlpha, 0.01f)
        assertEquals(0.5f, geometry(0.7f).titleAlpha, 0.01f)
        assertEquals(0f, geometry(1f).titleAlpha, 0.01f)
    }

    @Test fun smoothStepEasesBetweenItsEdges() {
        assertEquals(0f, smoothStep(0.2f, 0.8f, 0f), 0f)
        assertEquals(1f, smoothStep(0.2f, 0.8f, 1f), 0f)
        assertEquals(0.5f, smoothStep(0f, 1f, 0.5f), 1e-6f)
        assertTrue(smoothStep(0f, 1f, 0.25f) < 0.25f)
        assertEquals(1f, smoothStep(0.5f, 0.5f, 0.6f), 0f)
        assertEquals(0f, smoothStep(0.5f, 0.5f, 0.4f), 0f)
    }

    @Test fun progressIsTheOffsetOverTheRangeAndIsClamped() {
        val state = ChatsHeaderCollapseState(initialOffsetPx = 35f)
        assertEquals(0f, state.progress, 0f)
        state.rangePx = 70f
        assertEquals(0.5f, state.progress, 1e-6f)
        state.offsetPx = 200f
        assertEquals(1f, state.progress, 0f)
    }
}
