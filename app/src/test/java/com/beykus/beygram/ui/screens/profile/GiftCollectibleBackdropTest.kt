package com.beykus.beygram.ui.screens.profile

import com.beykus.beygram.domain.model.GiftCollectibleDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GiftCollectibleBackdropTest {
    private fun details(center: Int?, edge: Int?) = GiftCollectibleDetails(
        number = 1,
        symbol = null,
        backdropCenterColor = center,
        backdropEdgeColor = edge,
        backdropSymbolColor = null,
        backdropTextColor = null,
        modelRarityPerMille = null,
        modelRarityTier = null
    )

    @Test
    fun bothColoursGiveOpaqueCentreThenEdge() {
        val endpoints = backdropEndpoints(details(center = 0x112233, edge = 0xAABBCC))
        assertEquals(Pair(0xFF112233L, 0xFFAABBCCL), endpoints)
    }

    @Test
    fun noBackdropWithoutOneOfTheColours() {
        assertNull(backdropEndpoints(details(center = 0x112233, edge = null)))
        assertNull(backdropEndpoints(details(center = null, edge = 0xAABBCC)))
        assertNull(backdropEndpoints(details(center = null, edge = null)))
    }

    @Test
    fun noBackdropWithoutCollectibleDetails() {
        assertNull(backdropEndpoints(null))
    }
}
