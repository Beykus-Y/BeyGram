package com.beykus.beygram.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumMosaicTest {
    @Test fun threePortraitsCanUseAFullHeightLeftTileWithTwoStackedOnTheRight() {
        val result = chooseAlbumMosaic(292, 420, 2, 54, List(3) { 9.0 / 16 })
        val (left, top, bottom) = result.tiles
        assertEquals(0, left.x)
        assertEquals(0, left.y)
        assertEquals(result.height, left.height)
        assertEquals(left.width + 2, top.x)
        assertEquals(top.x, bottom.x)
        assertEquals(top.height + 2, bottom.y)
        assertEquals(result.height, bottom.y + bottom.height)
    }

    @Test fun mixedAndUnknownRatiosStayInBoundsAndStableForEverySupportedCount() {
        val aspects = listOf(0.08, 4.0, 0.57, 1.0, 1.8, Double.NaN, 3.0, 0.4, 0.0, 0.78)
        for (count in 2..10) {
            val input = aspects.take(count)
            val result = chooseAlbumMosaic(292, 420, 2, 54, input)
            assertEquals(result, chooseAlbumMosaic(292, 420, 2, 54, input))
            assertEquals(count, result.tiles.size)
            assertTrue(result.height in 1..420)
            assertEquals(292, result.tiles.maxOf { it.x + it.width })
            result.tiles.forEachIndexed { index, tile ->
                assertTrue(tile.width > 0 && tile.height > 0)
                assertTrue(tile.x >= 0 && tile.x + tile.width <= 292)
                assertTrue(tile.y >= 0 && tile.y + tile.height <= result.height)
                result.tiles.drop(index + 1).forEach { next ->
                    assertTrue(
                        tile.x + tile.width + 2 <= next.x || next.x + next.width + 2 <= tile.x ||
                            tile.y + tile.height + 2 <= next.y || next.y + next.height + 2 <= tile.y
                    )
                }
            }
            assertEquals(result.tiles, result.tiles.sortedWith(compareBy<AlbumTileRect> { it.y }.thenBy { it.x }))
        }
    }

    @Test fun rowsOfThreeLandscapeImagesFillEachRowWithoutAPlaceholder() {
        val result = chooseAlbumMosaic(292, 420, 2, 54, List(3) { 16.0 / 9 })
        assertEquals(3, result.tiles.size)
        result.tiles.groupBy { it.y }.values.forEach { row ->
            assertEquals(0, row.first().x)
            assertEquals(292, row.last().x + row.last().width)
            row.zipWithNext().forEach { (tile, next) -> assertEquals(tile.x + tile.width + 2, next.x) }
        }
    }
}
