package com.beykus.beygram.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class BeyGramPopoverPlacementTest {
    private val window = IntSize(1080, 2400)
    private val menu = IntSize(260, 300)
    private val gap = 18
    private val margin = 24

    @Test
    fun opensBelowTheAnchorAlignedToItsEnd() {
        val anchor = IntRect(left = 900, top = 120, right = 1000, bottom = 200)
        val offset = popoverOffset(anchor, window, menu, alignEnd = true, gapPx = gap, marginPx = margin, imeBottomPx = 0)
        assertEquals(IntOffset(740, 218), offset)
    }

    @Test
    fun opensAboveWhenThereIsNoRoomBelow() {
        val anchor = IntRect(left = 100, top = 2300, right = 160, bottom = 2360)
        val offset = popoverOffset(anchor, window, menu, alignEnd = false, gapPx = gap, marginPx = margin, imeBottomPx = 0)
        assertEquals(IntOffset(100, 1982), offset)
    }

    @Test
    fun clampsToTheRightWindowEdge() {
        val anchor = IntRect(left = 1000, top = 120, right = 1060, bottom = 200)
        val offset = popoverOffset(anchor, window, menu, alignEnd = false, gapPx = gap, marginPx = margin, imeBottomPx = 0)
        assertEquals(IntOffset(796, 218), offset)
    }

    @Test
    fun opensBelowWhenTheKeyboardIsClosed() {
        val anchor = IntRect(left = 900, top = 1300, right = 1000, bottom = 1400)
        val offset = popoverOffset(anchor, window, menu, alignEnd = true, gapPx = gap, marginPx = margin, imeBottomPx = 0)
        assertEquals(IntOffset(740, 1418), offset)
    }

    @Test
    fun opensAboveTheAnchorWhenTheKeyboardCoversTheSpaceBelow() {
        val anchor = IntRect(left = 900, top = 1300, right = 1000, bottom = 1400)
        val offset = popoverOffset(anchor, window, menu, alignEnd = true, gapPx = gap, marginPx = margin, imeBottomPx = 900)
        assertEquals(IntOffset(740, 982), offset)
    }

    @Test
    fun appliesTheOffsetBeforeTheWindowMarginsAreKept() {
        val anchor = IntRect(left = 900, top = 120, right = 1000, bottom = 200)
        val offset = popoverOffset(
            anchor, window, menu, alignEnd = true, gapPx = gap, marginPx = margin, imeBottomPx = 0,
            shift = IntOffset(-12, 8)
        )
        assertEquals(IntOffset(728, 226), offset)
    }

    @Test
    fun staysInsideTheWindowWhenTheMenuIsTallerThanTheSpace() {
        val anchor = IntRect(left = 900, top = 120, right = 1000, bottom = 200)
        val tall = IntSize(260, 3000)
        val offset = popoverOffset(anchor, window, tall, alignEnd = true, gapPx = gap, marginPx = margin, imeBottomPx = 0)
        assertEquals(IntOffset(740, 24), offset)
    }
}
