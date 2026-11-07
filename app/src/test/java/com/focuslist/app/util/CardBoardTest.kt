package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun snapShape_roundsEachAxisIndependently() {
        // 横向只影响列、纵向只影响行：往宽了拖绝不会把行数改小
        assertEquals(1 to 1, CardBoard.snapShape(1f, 1f).let { it.cols to it.rows })
        assertEquals(1 to 1, CardBoard.snapShape(1.4f, 1.4f).let { it.cols to it.rows })
        assertEquals(2 to 2, CardBoard.snapShape(1.5f, 1.5f).let { it.cols to it.rows })
        assertEquals(3 to 2, CardBoard.snapShape(2.6f, 1.6f).let { it.cols to it.rows })
        assertEquals(1 to 3, CardBoard.snapShape(1.1f, 2.6f).let { it.cols to it.rows })
    }

    @Test
    fun snapShape_clampsToMaxima() {
        // 从最右列往右拖，列数上限只剩 1，行数仍然可以到 4
        val shape = CardBoard.snapShape(3f, 3.4f, maxCols = 1, maxRows = 4)
        assertEquals(1, shape.cols)
        assertEquals(3, shape.rows)
        assertEquals(1, CardBoard.snapShape(0.2f, 0.2f).cols)
        assertEquals(4, CardBoard.snapShape(9f, 9f).rows)
    }

    @Test
    fun snapShape_allowsArbitrarySpans() {
        // 不再限制成六种预设：2×3、4×1 这类尺寸是合法的
        assertEquals(2 to 3, CardBoard.snapShape(2f, 3f).let { it.cols to it.rows })
        assertEquals(4 to 1, CardBoard.snapShape(4f, 1f).let { it.cols to it.rows })
    }

    @Test
    fun arrange_keepsAnchorAndPushesOverlapAway() {
        val a = card(1, 1, 1, x = 0, y = 0)
        val b = card(2, 1, 1, x = 0, y = 0)
        val result = CardBoard.arrange(listOf(a, b), anchor = a)
        assertEquals(0, result[0].x)
        assertEquals(0, result[0].y)
        // 被压到的 b 挪到最近的空位（右边一格）
        assertEquals(1, result[1].x)
        assertEquals(0, result[1].y)
    }

    @Test
    fun arrange_returnsListInInputOrder() {
        // 锚点是列表里的第二张：结果顺序不能变，否则调用方按 index 比对会串
        val a = card(1, 1, 1, x = 0, y = 0)
        val b = card(2, 2, 2, x = 0, y = 0)
        val result = CardBoard.arrange(listOf(a, b), anchor = b)
        assertEquals(b.key, result[1].key)
        assertEquals(0, result[1].x)
        assertEquals(0, result[1].y)
        assertEquals(a.key, result[0].key)
        assertEquals(2, result[0].x)
    }

    @Test
    fun arrange_leavesNonOverlappingBoardUntouched() {
        val a = card(1, 1, 1, x = 0, y = 0)
        val b = card(2, 1, 1, x = 1, y = 0)
        val c = card(3, 2, 2, x = 2, y = 1)
        val result = CardBoard.arrange(listOf(a, b, c), anchor = a)
        assertEquals(listOf(a, b, c), result)
    }

    @Test
    fun arrange_onlyMovesCardsThatWereHit() {
        val a = card(1, 2, 2, x = 0, y = 0)
        val hit = card(2, 1, 1, x = 1, y = 1)
        val far = card(3, 1, 1, x = 3, y = 3)
        val result = CardBoard.arrange(listOf(a, hit, far), anchor = a)
        assertEquals(far, result[2])
        assertFalse(CardBoard.overlaps(result[0], result[1]))
    }

    @Test
    fun arrange_splitsStackOfThree() {
        val a = card(1, 1, 1, x = 0, y = 0)
        val b = card(2, 1, 1, x = 0, y = 0)
        val c = card(3, 1, 1, x = 0, y = 0)
        val result = CardBoard.arrange(listOf(a, b, c), anchor = c)
        // 三张卡两两不重叠，且只有锚点留在原位
        assertFalse(CardBoard.overlaps(result[0], result[1]))
        assertFalse(CardBoard.overlaps(result[0], result[2]))
        assertFalse(CardBoard.overlaps(result[1], result[2]))
        assertEquals(0, result[2].x)
        assertEquals(0, result[2].y)
    }

    @Test
    fun arrange_isDeterministic() {
        val cards = listOf(
            card(1, 1, 1, x = 0, y = 0),
            card(2, 1, 1, x = 0, y = 0),
            card(3, 2, 1, x = 0, y = 0)
        )
        val first = CardBoard.arrange(cards, anchor = cards[0])
        val second = CardBoard.arrange(cards, anchor = cards[0])
        assertEquals(first, second)
    }

    @Test
    fun snapCell_keepsCurrentWhenHoveringOnGridLine() {
        // 手指停在格线上（0.5 格处）不该换格，否则邻居会被反复挤开又退回
        assertEquals(0, CardBoard.snapCell(0, 0.5f))
        assertEquals(0, CardBoard.snapCell(0, -0.5f))
        assertEquals(1, CardBoard.snapCell(1, 1.55f))
    }

    @Test
    fun snapCell_switchesOncePastThreshold() {
        assertEquals(1, CardBoard.snapCell(0, 0.61f))
        assertEquals(0, CardBoard.snapCell(1, 0.39f))
        assertEquals(3, CardBoard.snapCell(0, 2.8f))
    }

    @Test
    fun snapCell_thresholdIsConfigurable() {
        // 阈值调小就更容易换格
        assertEquals(1, CardBoard.snapCell(0, 0.5f, threshold = 0.4f))
        assertEquals(0, CardBoard.snapCell(0, 0.9f, threshold = 0.95f))
    }

    @Test
    fun arrange_afterGrowthNothingOverlaps() {
        // 照搬用户截图里的那一局面：箍点长高后压住了右边的大卡
        val anchor = card(1, 1, 3, x = 2, y = 0)
        val left = card(2, 2, 2, x = 0, y = 0)
        val under = card(3, 2, 2, x = 0, y = 2)
        val victim = card(4, 2, 2, x = 2, y = 1)
        val result = CardBoard.arrange(listOf(anchor, left, under, victim), anchor)
        for (i in result.indices) {
            for (j in i + 1 until result.size) {
                assertFalse(
                    "${result[i]} 与 ${result[j]} 重叠",
                    CardBoard.overlaps(result[i], result[j])
                )
            }
        }
        // 锚点必须原地不动，被压到的卡挪到它下面
        assertEquals(anchor, result[0])
        assertEquals(2, result[3].x)
        assertEquals(3, result[3].y)
    }

    @Test
    fun arrange_shrinkingBackRestoresOriginalSpot() {
        // 每一帧都从同一份「原始画布」重算，所以缩回去时被挤走的卡会回原位，
        // 不会因为上一帧被挪过就一路往下漂
        val small = card(1, 1, 1, x = 0, y = 0)
        val below = card(2, 1, 1, x = 0, y = 1)
        val base = listOf(small, below)
        assertNotEquals(base, CardBoard.arrange(base, small.copy(rows = 3)))
        assertEquals(base, CardBoard.arrange(base, small))
    }
}
