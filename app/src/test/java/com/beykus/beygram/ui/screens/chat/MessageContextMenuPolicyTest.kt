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
}
