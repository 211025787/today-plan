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
    fun allowedShapes_areExactlySix() {
        assertEquals(
            listOf(1 to 1, 1 to 2, 2 to 1, 2 to 2, 1 to 3, 3 to 1),
            CardBoard.ALLOWED_SHAPES.map { it.cols to it.rows }
        )
        assertTrue(CardBoard.isAllowed(2, 2))
        assertFalse(CardBoard.isAllowed(3, 2))
        assertFalse(CardBoard.isAllowed(4, 1))
    }

    @Test
    fun nearestShape_snapsToSixPresets() {
        assertEquals(1 to 1, CardBoard.nearestShape(1f, 1f).let { it.cols to it.rows })
        assertEquals(2 to 1, CardBoard.nearestShape(2.2f, 1.1f).let { it.cols to it.rows })
        assertEquals(1 to 2, CardBoard.nearestShape(1.1f, 2.2f).let { it.cols to it.rows })
        assertEquals(2 to 2, CardBoard.nearestShape(2.1f, 2.1f).let { it.cols to it.rows })
        assertEquals(3 to 1, CardBoard.nearestShape(3.1f, 1.1f).let { it.cols to it.rows })
        assertEquals(1 to 3, CardBoard.nearestShape(1.1f, 3.1f).let { it.cols to it.rows })
    }

    @Test
    fun nearestShape_tiesPreferEarlierLargerShape() {
        // 3×2 到 2×2 与 3×1 距离都是 1；列表里 2×2 在前
        val shape = CardBoard.nearestShape(3f, 2f)
        assertEquals(2, shape.cols)
        assertEquals(2, shape.rows)
    }

    @Test
    fun allowedShape_normalizesLegacyArbitrarySizes() {
        assertEquals(3, CardBoard.allowedShape(4, 1).cols)
        assertEquals(1, CardBoard.allowedShape(4, 1).rows)
        assertEquals(2, CardBoard.allowedShape(3, 2).cols)
        assertEquals(2, CardBoard.allowedShape(3, 2).rows)
        assertEquals(1, CardBoard.allowedShape(1, 4).cols)
        assertEquals(3, CardBoard.allowedShape(1, 4).rows)
    }

    @Test
    fun resizeToAllowed_clampsXIntoColumns() {
        val original = card(1, 1, 1, x = 3, y = 2)
        val resized = CardBoard.resizeToAllowed(original, CardShape(2, 2))
        assertEquals(2, resized.cols)
        assertEquals(2, resized.rows)
        assertEquals(2, resized.x)
        assertEquals(2, resized.y)
    }

    @Test
    fun maxFitCols_stopsBeforeNeighbour() {
        // 自己 1×1 在 (0,0)，邻居在 (2,0)：能长到 2 列，第 3 列被邻居占着
        val self = card(1, 1, 1, x = 0, y = 0)
        val other = card(2, 1, 1, x = 2, y = 0)
        assertEquals(2, CardBoard.maxFitCols(listOf(self, other), self, rows = 1))
    }

    @Test
    fun maxFitCols_oneWhenNeighbourIsAdjacent() {
        val self = card(1, 1, 1, x = 0, y = 0)
        val other = card(2, 1, 1, x = 1, y = 0)
        assertEquals(1, CardBoard.maxFitCols(listOf(self, other), self, rows = 1))
    }

    @Test
    fun maxFitCols_respectsRightEdgeAndLimit() {
        // 贴着右边界（x = 3）时无论多空都只能 1 列
        val self = card(1, 1, 1, x = 3, y = 0)
        assertEquals(1, CardBoard.maxFitCols(listOf(self), self, rows = 1))
        // limit 可以比可用空间更小
        val wide = card(2, 1, 1, x = 0, y = 0)
        assertEquals(2, CardBoard.maxFitCols(listOf(wide), wide, rows = 1, limit = 2))
    }

    @Test
    fun maxFitRows_stopsBeforeNeighbour() {
        val self = card(1, 1, 1, x = 0, y = 0)
        val other = card(2, 1, 1, x = 0, y = 2)
        assertEquals(2, CardBoard.maxFitRows(listOf(self, other), self, cols = 1))
        val near = card(3, 1, 1, x = 0, y = 1)
        assertEquals(1, CardBoard.maxFitRows(listOf(self, near), self, cols = 1))
    }

    @Test
    fun fitShapeAt_prefersClosestAllowedShape() {
        val self = card(1, 1, 1, x = 0, y = 0)
        val shape = CardBoard.fitShapeAt(listOf(self), self, 2.4f, 0.9f)
        assertEquals(2, shape.cols)
        assertEquals(1, shape.rows)
    }

    @Test
    fun fitShapeAt_skipsShapesThatWouldOverlap() {
        // 邻居在 (1,0)：3×1 和 2×1 都会压上去，只能维持 1×1
        val self = card(1, 1, 1, x = 0, y = 0)
        val other = card(2, 1, 1, x = 1, y = 0)
        val shape = CardBoard.fitShapeAt(listOf(self, other), self, 3f, 1f)
        assertEquals(1, shape.cols)
        assertEquals(1, shape.rows)
    }

    @Test
    fun fitShapeAt_keepsCurrentShapeWhenNothingFits() {
        // 老数据已经叠在一起：任何规格都放不下，保持原样而不是再叠一层
        val self = card(1, 2, 2, x = 0, y = 0)
        val other = card(2, 2, 2, x = 0, y = 0)
        val shape = CardBoard.fitShapeAt(listOf(self, other), self, 3f, 3f)
        assertEquals(2, shape.cols)
        assertEquals(2, shape.rows)
    }

    @Test
    fun fitShapeAt_neverExceedsRightEdge() {
        val self = card(1, 1, 1, x = 3, y = 0)
        val shape = CardBoard.fitShapeAt(listOf(self), self, 3f, 1f)
        assertEquals(1, shape.cols)
        assertEquals(1, shape.rows)
    }
}
