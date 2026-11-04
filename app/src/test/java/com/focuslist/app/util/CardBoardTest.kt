package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 日卡片自由排版算法单元测试：补位、手动位置保护、拖动/缩放边界与画布行数。
 */
class CardBoardTest {

    private fun card(
        key: Long,
        cols: Int = 1,
        rows: Int = 1,
        x: Int = -1,
        y: Int = -1,
        trip: Boolean = false
    ) = CardBox(key, trip, cols, rows, x, y)

    @Test
    fun pack_empty() {
        assertTrue(CardBoard.pack(emptyList()).isEmpty())
        assertEquals(1, CardBoard.canvasRows(emptyList()))
    }

    @Test
    fun pack_singleSmallAtOrigin() {
        val packed = CardBoard.pack(listOf(card(1)))
        assertEquals(0, packed[0].x)
        assertEquals(0, packed[0].y)
        assertEquals(1, CardBoard.canvasRows(packed))
    }

    @Test
    fun pack_twoWideCardsSideBySide() {
        val packed = CardBoard.pack(listOf(card(1, 2, 2), card(2, 2, 2)))
        assertEquals(0, packed[0].x)
        assertEquals(0, packed[0].y)
        assertEquals(2, packed[1].x)
        assertEquals(0, packed[1].y)
        assertEquals(2, CardBoard.canvasRows(packed))
    }

    @Test
    fun pack_thirdWideCardWrapsToNextRow() {
        val packed = CardBoard.pack(
            listOf(card(1, 2, 2), card(2, 2, 2), card(3, 2, 2))
        )
        assertEquals(0, packed[2].x)
        assertEquals(2, packed[2].y)
        assertEquals(4, CardBoard.canvasRows(packed))
    }

    @Test
    fun pack_leavesAlreadyPlacedCardsUntouched() {
        val placed = card(1, 2, 2, x = 2, y = 3)
        val packed = CardBoard.pack(listOf(placed, card(2, 2, 2)))
        assertEquals(2, packed[0].x)
        assertEquals(3, packed[0].y)
        // 第一张手动卡占住右侧中间，第二张会补到左上
        assertEquals(0, packed[1].x)
        assertEquals(0, packed[1].y)
    }

    @Test
    fun pack_missingAvoidsEveryPlacedCard() {
        val placed = listOf(
            card(1, 2, 1, x = 0, y = 0),
            card(2, 2, 1, x = 2, y = 0)
        )
        val packed = CardBoard.pack(placed + card(3, 1, 1))
        assertEquals(0, packed[2].x)
        assertEquals(1, packed[2].y)
    }

    @Test
    fun pack_keepsOrder() {
        val packed = CardBoard.pack(listOf(card(7), card(8), card(9)))
        assertEquals(listOf(7L, 8L, 9L), packed.map { it.key })
        assertEquals(listOf(0, 1, 2), packed.map { it.x })
        assertTrue(packed.all { it.y == 0 })
    }

    @Test
    fun normalize_clampsSpansAndOffsets() {
        val normalized = CardBoard.normalize(card(1, cols = 9, rows = 0, x = 9, y = -3))
        assertEquals(4, normalized.cols)
        assertEquals(1, normalized.rows)
        // 未摆放（y<0）保持 -1
        assertEquals(-1, normalized.x)
        assertEquals(-1, normalized.y)
    }

    @Test
    fun normalize_clampsXInsideFourColumns() {
        val normalized = CardBoard.normalize(card(1, cols = 3, rows = 2, x = 3, y = 2))
        assertEquals(1, normalized.x)
        assertEquals(2, normalized.y)
    }

    @Test
    fun move_clampsToColumnsAndNonNegativeRow() {
        val original = card(1, 2, 1, x = 0, y = 0)
        val moved = CardBoard.move(original, x = 4, y = -2)
        assertEquals(2, moved.x)
        assertEquals(0, moved.y)
    }

    @Test
    fun resize_pushesLeftWhenGrowingPastRightEdge() {
        val original = card(1, 2, 1, x = 2, y = 0)
        val resized = CardBoard.resize(original, cols = 3, rows = 2)
        assertEquals(3, resized.cols)
        assertEquals(2, resized.rows)
        assertEquals(1, resized.x)
        assertEquals(0, resized.y)
    }

    @Test
    fun resize_clampsSpans() {
        val original = card(1, 1, 1, x = 0, y = 0)
        val resized = CardBoard.resize(original, cols = 9, rows = 0)
        assertEquals(4, resized.cols)
        assertEquals(1, resized.rows)
    }

    @Test
    fun canvasRows_usesFarthestBottom() {
        val cards = listOf(card(1, 2, 2, x = 0, y = 2), card(2, 1, 1, x = 3, y = 0))
        assertEquals(4, CardBoard.canvasRows(cards))
    }

    @Test
    fun overlaps_detectsSharedCells() {
        val a = card(1, 2, 2, x = 0, y = 0)
        assertTrue(CardBoard.overlaps(a, card(2, 1, 1, x = 1, y = 1)))
        assertFalse(CardBoard.overlaps(a, card(3, 2, 2, x = 2, y = 0)))
        assertFalse(CardBoard.overlaps(a, card(4, 1, 1)))
    }

    @Test
    fun fitsAt_ignoresSelfAndSeesNeighbors() {
        val moving = card(1, 1, 1, x = 0, y = 0)
        val neighbor = card(2, 1, 1, x = 1, y = 0)
        assertTrue(CardBoard.fitsAt(listOf(moving, neighbor), moving))
        assertFalse(CardBoard.fitsAt(listOf(moving, neighbor), card(9, 1, 1, x = 1, y = 0)))
    }

    @Test
    fun resolveMove_snapsToNearestFreeCell() {
        val moving = card(1, 1, 1, x = 0, y = 0)
        val cards = listOf(
            moving,
            card(2, 1, 1, x = 1, y = 0),
            card(3, 1, 1, x = 2, y = 0)
        )
        val resolved = CardBoard.resolveMove(cards, moving, targetX = 2, targetY = 0)
        assertEquals(3, resolved.x)
        assertEquals(0, resolved.y)
    }

    @Test
    fun resolveMove_doesNotOverlapAnyone() {
        val moving = card(1, 2, 1, x = 0, y = 0)
        val cards = listOf(
            moving,
            card(2, 2, 1, x = 1, y = 0),
            card(3, 2, 1, x = 2, y = 0)
        )
        val resolved = CardBoard.resolveMove(cards, moving, targetX = 1, targetY = 0)
        assertFalse(CardBoard.overlaps(resolved, cards[1]))
        assertFalse(CardBoard.overlaps(resolved, cards[2]))
        // 唯一能放 2 格宽的空位在下一行
        assertEquals(1, resolved.y)
    }

    @Test
    fun maxFreeResize_stopsAtNeighborBoundary() {
        val moving = card(1, 1, 1, x = 0, y = 0)
        val right = card(2, 1, 1, x = 1, y = 0)
        val below = card(3, 1, 1, x = 0, y = 1)
        val (cols, rows) = CardBoard.maxFreeResize(
            listOf(moving, right, below), moving, requestedCols = 2, requestedRows = 2
        )
        assertEquals(1, cols)
        assertEquals(1, rows)
    }

    @Test
    fun maxFreeResize_canGrowOneDirection() {
        val moving = card(1, 1, 1, x = 0, y = 0)
        val right = card(2, 1, 1, x = 1, y = 0)
        val (cols, rows) = CardBoard.maxFreeResize(
            listOf(moving, right), moving, requestedCols = 2, requestedRows = 2
        )
        assertEquals(1, cols)
        assertEquals(2, rows)
    }

    @Test
    fun maxFreeResize_returnsRequestWhenFree() {
        val moving = card(1, 1, 1, x = 0, y = 0)
        val (cols, rows) = CardBoard.maxFreeResize(
            listOf(moving), moving, requestedCols = 3, requestedRows = 2
        )
        assertEquals(3, cols)
        assertEquals(2, rows)
    }
}
