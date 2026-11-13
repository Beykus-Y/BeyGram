package com.beykus.beygram.ui.screens.chat

import androidx.compose.ui.geometry.Rect
import com.beykus.beygram.domain.model.MessageCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageContextMenuPolicyTest {
    @Test
    fun placesBelowIncomingBubbleWhenSpaceAllows() {
        val viewport = Rect(0f, 100f, 1080f, 2200f)
        val anchor = Rect(40f, 400f, 600f, 520f)
        val placement = computeMessagePopoverPlacement(
            anchorBounds = anchor,
            viewportBounds = viewport,
            menuWidthPx = 500,
            menuHeightPx = 600,
            gapPx = 16,
            marginPx = 24,
            isOutgoing = false
        )
        assertFalse(placement.useBottomSheetFallback)
        assertFalse(placement.placedAbove)
        assertEquals(40, placement.x)
        assertEquals(536, placement.y)
    }

    @Test
    fun flipsAboveOutgoingBubbleNearBottomAndAlignsRight() {
        val viewport = Rect(0f, 100f, 1080f, 2200f)
        val anchor = Rect(420f, 1700f, 1040f, 1850f)
        val placement = computeMessagePopoverPlacement(
            anchorBounds = anchor,
            viewportBounds = viewport,
            menuWidthPx = 500,
            menuHeightPx = 600,
            gapPx = 16,
            marginPx = 24,
            isOutgoing = true
        )
        assertFalse(placement.useBottomSheetFallback)
        assertTrue(placement.placedAbove)
        assertEquals(540, placement.x)
        assertEquals(1084, placement.y)
    }

    @Test
    fun fallsBackToBottomSheetWhenGiantBubbleLeavesNoRoom() {
        val viewport = Rect(0f, 100f, 1080f, 1200f)
        val anchor = Rect(40f, 120f, 700f, 1180f)
        val placement = computeMessagePopoverPlacement(
            anchorBounds = anchor,
            viewportBounds = viewport,
            menuWidthPx = 500,
            menuHeightPx = 600,
            gapPx = 16,
            marginPx = 24,
            isOutgoing = false,
            minPopoverHeightPx = 360
        )
        assertTrue(placement.useBottomSheetFallback)
    }

    @Test
    fun actionHierarchyPrioritizesEditAndKeepsDeleteLastInSecondary() {
        val caps = MessageCapabilities(
            canReply = true,
            canEdit = true,
            canDeleteForSelf = true,
            canDeleteForAll = true,
            canForward = true,
            canCopy = true,
            canPin = true,
            canGetLink = true
        )
        val planned = planMessageContextActions(
            capabilities = caps,
            hasText = true,
            canPin = true,
            hasCopyEntity = true,
            canTranslate = true,
            hasPlayNext = false,
            hasEnqueue = false,
            hasAddToPlaylist = false,
            canReport = false
        )
        assertEquals(
            listOf(MessageContextActionId.Edit, MessageContextActionId.Reply, MessageContextActionId.Copy, MessageContextActionId.Forward),
            planned.primary
        )
        assertEquals(MessageContextActionId.Delete, planned.secondary.last())
        assertTrue(MessageContextActionId.Select in planned.secondary)
    }

    @Test
    fun restrictedCapabilitiesHideCopyForwardEditAndKeepAllowedActions() {
        val caps = MessageCapabilities(
            canReply = true,
            canEdit = false,
            canDeleteForSelf = false,
            canDeleteForAll = false,
            canForward = false,
            canCopy = false,
            canPin = false,
            canGetLink = false
        )
        val planned = planMessageContextActions(
            capabilities = caps,
            hasText = true,
            canPin = false,
            hasCopyEntity = false,
            canTranslate = false,
            hasPlayNext = false,
            hasEnqueue = false,
            hasAddToPlaylist = false,
            canReport = true
        )
        assertEquals(listOf(MessageContextActionId.Reply), planned.primary)
        assertEquals(listOf(MessageContextActionId.Select, MessageContextActionId.Report), planned.secondary)
    }

    @Test
    fun offscreenOrStaleAnchorFallsBackToBottomSheet() {
        val viewport = Rect(0f, 100f, 1080f, 1800f)
        val aboveViewport = Rect(40f, -200f, 600f, 80f)
        val belowViewport = Rect(40f, 1820f, 600f, 2000f)
        assertTrue(
            computeMessagePopoverPlacement(aboveViewport, viewport, 500, 600, 16, 24, isOutgoing = false).useBottomSheetFallback
        )
        assertTrue(
            computeMessagePopoverPlacement(belowViewport, viewport, 500, 600, 16, 24, isOutgoing = false).useBottomSheetFallback
        )
    }

    @Test
    fun landscapeWithImeConstrainsPopoverMaxHeightInsideViewport() {
        // Simulated landscape + IME viewport: only 420px usable height.
        val viewport = Rect(0f, 80f, 2200f, 500f)
        val maxHeight = computePopoverMaxHeightPx(viewport, marginPx = 24)
        assertEquals(372, maxHeight)
        // Even with a tight viewport, the fallback is chosen instead of overflowing above viewport.top.
        val anchor = Rect(100f, 120f, 700f, 360f)
        val placement = computeMessagePopoverPlacement(anchor, viewport, 500, maxHeight, 16, 24, isOutgoing = false, minPopoverHeightPx = 300)
        assertTrue(placement.useBottomSheetFallback)
    }

    @Test
    fun midScreenAnchorWithTallMenuNeverOverlapsSelectedBubble() {
        // Exact scenario from review: viewport 0..800, anchor 300..350, menuHeightPx=600, gap=8, margin=12.
        // spaceAbove=280, spaceBelow=430. Menu must either constrain height to 430 below (y=358) or fall back, never overlap 300..350.
        val viewport = Rect(0f, 0f, 1080f, 800f)
        val anchor = Rect(40f, 300f, 540f, 350f)
        val placement = computeMessagePopoverPlacement(
            anchorBounds = anchor,
            viewportBounds = viewport,
            menuWidthPx = 480,
            menuHeightPx = 600,
            gapPx = 8,
            marginPx = 12,
            isOutgoing = false,
            minPopoverHeightPx = 280
        )
        assertFalse(placement.useBottomSheetFallback)
        assertFalse(placement.placedAbove)
        assertEquals(358, placement.y)
        assertEquals(430, placement.maxHeightPx)
        assertTrue(placement.y >= anchor.bottom + 8)
    }
}
