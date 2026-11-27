package com.beykus.beygram.ui.screens.chat

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

internal data class AlbumTileRect(val x: Int, val y: Int, val width: Int, val height: Int)

internal data class AlbumMosaic(val height: Int, val tiles: List<AlbumTileRect>)

/**
 * Each candidate fills the available width in chronological order. Score the crop of
 * each real aspect ratio, with a penalty for cells too small to read or tap.
 */
internal fun chooseAlbumMosaic(
    width: Int,
    maxHeight: Int,
    gutter: Int,
    minCell: Int,
    aspectRatios: List<Double>
): AlbumMosaic {
    require(aspectRatios.size in 2..10)
    if (width <= 0 || maxHeight <= 0) return AlbumMosaic(0, emptyList())
    val aspects = aspectRatios.map { if (it.isFinite() && it > 0.0) it.coerceIn(0.08, 12.0) else 1.0 }
    val layoutAspects = aspects.map { it.coerceIn(0.30, 3.0) }
    val gap = gutter.coerceIn(0, minOf(width - 1, maxHeight - 1))
    var best: AlbumMosaic? = null
    var bestScore = Double.NEGATIVE_INFINITY

    fun consider(mosaic: AlbumMosaic, bonus: Double = 0.0) {
        val cropCost = mosaic.tiles.zip(aspects).sumOf { (tile, aspect) ->
            abs(ln((tile.width.toDouble() / tile.height) / aspect))
        } / aspects.size
        val sizeCost = mosaic.tiles.sumOf {
            (minCell - minOf(it.width, it.height)).coerceAtLeast(0).toDouble() / minCell.coerceAtLeast(1)
        } / aspects.size
        val score = bonus - cropCost - sizeCost * 2
        if (score > bestScore) {
            bestScore = score
            best = mosaic
        }
    }

    fun evaluateRows(rows: List<Int>) {
        val heightBudget = maxHeight - gap * (rows.size - 1)
        if (heightBudget < rows.size) return
        var start = 0
        val rowAspects = rows.map { count ->
            layoutAspects.subList(start, start + count).also { start += count }
        }
        if (rows.any { width - gap * (it - 1) < it }) return
        val idealHeights = rowAspects.map { row -> (width - gap * (row.size - 1)) / row.sum() }
        val height = idealHeights.sum().roundToInt().coerceIn(rows.size, heightBudget)
        val rowHeights = splitPixels(height, idealHeights)
        val tiles = ArrayList<AlbumTileRect>(aspects.size)
        var y = 0
        rowAspects.forEachIndexed { index, row ->
            val rowHeight = rowHeights[index]
            val widths = splitPixels(width - gap * (row.size - 1), row)
            var x = 0
            widths.forEach { tileWidth ->
                tiles += AlbumTileRect(x, y, tileWidth, rowHeight)
                x += tileWidth + gap
            }
            y += rowHeight + gap
        }
        consider(AlbumMosaic(y - gap, tiles))
    }

    val rows = ArrayList<Int>(aspects.size)
    fun partitions(remaining: Int) {
        if (remaining == 0) {
            evaluateRows(rows)
            return
        }
        for (count in minOf(3, remaining) downTo 1) {
            rows += count
            partitions(remaining - count)
            rows.removeAt(rows.lastIndex)
        }
    }
    partitions(aspects.size)

    if (aspects.size == 3 && aspects.all { it < 0.85 } && width >= gap + 2 && maxHeight >= gap + 2) {
        val (first, second, third) = layoutAspects
        val rightUnitHeight = 1 / second + 1 / third
        val left = (((width - gap) * rightUnitHeight + gap) / (1 / first + rightUnitHeight))
            .roundToInt().coerceIn(1, width - gap - 1)
        val right = width - gap - left
        val height = (left / first).roundToInt().coerceIn(gap + 2, maxHeight)
        val top = splitPixels(height - gap, listOf(1 / second, 1 / third)).first()
        consider(
            AlbumMosaic(height, listOf(
                AlbumTileRect(0, 0, left, height),
                AlbumTileRect(left + gap, 0, right, top),
                AlbumTileRect(left + gap, top + gap, right, height - gap - top)
            )),
            bonus = 0.035
        )
    }

    return best ?: AlbumMosaic(0, emptyList())
}

/** Fill a run exactly while leaving at least one pixel for each following cell. */
private fun splitPixels(total: Int, weights: List<Double>): List<Int> {
    val sum = weights.sum()
    var weight = 0.0
    var edge = 0
    return weights.mapIndexed { index, size ->
        weight += size
        val end = if (index == weights.lastIndex) total else
            (total * weight / sum).roundToInt().coerceIn(edge + 1, total - (weights.lastIndex - index))
        (end - edge).also { edge = end }
    }
}
