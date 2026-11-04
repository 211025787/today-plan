package com.focuslist.app.util

/**
 * 日历日卡片在自由画布上的占位。
 *
 * 坐标以「格子」为单位：左上角 (x, y)，宽 [cols] 格、高 [rows] 格。
 * 4 列固定；y 可以一直向下长。x/y 为 -1 表示还没有手动摆放，
 * 由 [CardBoard.pack] 自动补一个不重叠的位置。
 *
 * @property key 计划 / 行程 ID
 * @property trip true 表示旅游行程卡
 * @property cols 占用列数（1-4）
 * @property rows 占用行数（1-4）
 * @property x 左上角列（0 起）；-1 表示未摆放
 * @property y 左上角行（0 起）；-1 表示未摆放
 */
data class CardBox(
    val key: Long,
    val trip: Boolean,
    val cols: Int,
    val rows: Int,
    val x: Int = -1,
    val y: Int = -1
)

/**
 * 日卡片的自由排版算法。
 *
 * 关键约定：
 * - **手动摆放过的卡片位置永远不再自动移动**（resize 只钳制边界）；
 * - 只有新卡片 / 旧数据（x/y < 0）才会被补位，补位时避开所有已占格子；
 * - 允许手动把两张卡叠在一起，用户自己负责；补位不会主动叠。
 *
 * 纯函数，不依赖 Compose / Android，方便单测。
 */
object CardBoard {

    /** 固定列数 */
    const val COLUMNS = 4

    /** 单张卡最大跨列 / 跨行 */
    const val MAX_SPAN = 4

    /**
     * 把列数、行数夹到合法范围；未摆放的卡片保持 -1。
     *
     * @param card 原始占位
     * @return 规整后的占位
     */
    fun normalize(card: CardBox): CardBox {
        val cols = card.cols.coerceIn(1, MAX_SPAN)
        val rows = card.rows.coerceIn(1, MAX_SPAN)
        if (card.x < 0 || card.y < 0) {
            return card.copy(cols = cols, rows = rows, x = -1, y = -1)
        }
        return card.copy(
            cols = cols,
            rows = rows,
            x = card.x.coerceIn(0, COLUMNS - cols),
            y = card.y.coerceAtLeast(0)
        )
    }

    /**
     * 给没有任何手动位置的卡片补位。
     *
     * 先铺所有已经手动摆放的卡，再按输入顺序逐张找「最上面、最左边」的空位：
     * 这样旧数据第一次打开时的默认排版也是稳定的，之后写入数据库就不会再变。
     *
     * @param cards 全部卡片
     * @return 每张卡片都带合法 x/y 的列表（顺序与输入一致）
     */
    fun pack(cards: List<CardBox>): List<CardBox> {
        if (cards.isEmpty()) return emptyList()
        val normalized = cards.map(::normalize)
        val occupancy = ArrayList<BooleanArray>()

        fun ensure(rows: Int) {
            while (occupancy.size < rows) occupancy.add(BooleanArray(COLUMNS))
        }

        fun isFree(x: Int, y: Int, cols: Int, rows: Int): Boolean {
            ensure(y + rows)
            for (row in y until y + rows) {
                for (col in x until x + cols) {
                    if (occupancy[row][col]) return false
                }
            }
            return true
        }

        fun occupy(x: Int, y: Int, cols: Int, rows: Int) {
            ensure(y + rows)
            for (row in y until y + rows) {
                for (col in x until x + cols) occupancy[row][col] = true
            }
        }

        val result = normalized.toMutableList()

        // 先占住用户手动摆好的位置，补位时才不会压上去
        normalized.forEach { card ->
            if (card.x >= 0 && card.y >= 0) {
                occupy(card.x, card.y, card.cols, card.rows)
            }
        }

        normalized.forEachIndexed { index, card ->
            if (card.x >= 0 && card.y >= 0) return@forEachIndexed
            var placedX = 0
            var placedY = 0
            var found = false
            var y = 0
            while (!found) {
                val maxX = COLUMNS - card.cols
                for (x in 0..maxX) {
                    if (isFree(x, y, card.cols, card.rows)) {
                        placedX = x
                        placedY = y
                        found = true
                        break
                    }
                }
                if (!found) y++
            }
            result[index] = card.copy(x = placedX, y = placedY)
            occupy(placedX, placedY, card.cols, card.rows)
        }
        return result
    }

    /**
     * 拖动到新格子：x 限制在 0..4-cols，y 不小于 0。
     *
     * @param card 原占位
     * @param x 目标列
     * @param y 目标行
     * @return 新占位
     */
    fun move(card: CardBox, x: Int, y: Int): CardBox {
        val base = normalize(card)
        return base.copy(
            x = x.coerceIn(0, COLUMNS - base.cols),
            y = y.coerceAtLeast(0)
        )
    }

    /**
     * 调整大小：列/行夹到 1..4；右边界超出时把 x 往左推，而不是缩回去。
     *
     * @param card 原占位
     * @param cols 新列数
     * @param rows 新行数
     * @return 新占位
     */
    fun resize(card: CardBox, cols: Int, rows: Int): CardBox {
        val base = normalize(card)
        val c = cols.coerceIn(1, MAX_SPAN)
        val r = rows.coerceIn(1, MAX_SPAN)
        return base.copy(
            cols = c,
            rows = r,
            x = base.x.coerceIn(0, COLUMNS - c),
            y = base.y.coerceAtLeast(0)
        )
    }

    /**
     * 画布需要多少行，至少 1 行。
     *
     * @param cards 全部卡片
     * @return 行数
     */
    fun canvasRows(cards: List<CardBox>): Int {
        if (cards.isEmpty()) return 1
        return cards.maxOf { it.y.coerceAtLeast(0) + it.rows }.coerceAtLeast(1)
    }

    /**
     * 两张卡片是否占用了同一个格子。
     *
     * @param a 卡片 A
     * @param b 卡片 B
     * @return 是否重叠
     */
    fun overlaps(a: CardBox, b: CardBox): Boolean {
        if (a.x < 0 || b.x < 0 || a.y < 0 || b.y < 0) return false
        return a.x < b.x + b.cols && b.x < a.x + a.cols &&
            a.y < b.y + b.rows && b.y < a.y + a.rows
    }

    /**
     * 目标位置是否和除自己以外的卡片都不重叠。
     *
     * @param cards 当前全部卡片
     * @param candidate 目标占位
     * @return 是否放得下
     */
    fun fitsAt(cards: List<CardBox>, candidate: CardBox): Boolean {
        if (candidate.x < 0 || candidate.y < 0) return false
        return cards.none { other ->
            !(other.trip == candidate.trip && other.key == candidate.key) &&
                overlaps(candidate, other)
        }
    }

    /**
     * 把卡片挪到离目标最近的空位，避免盖住别人。
     *
     * 拖动过程中允许视觉上经过别的卡，松手时在这里吸附到最近空位。
     * 若附近都没有位置，就放到所有卡片下方的最左边。
     *
     * @param cards 当前全部卡片（含移动中的卡片）
     * @param moving 移动前的占位
     * @param targetX 目标列
     * @param targetY 目标行
     * @return 不会重叠的落点
     */
    fun resolveMove(cards: List<CardBox>, moving: CardBox, targetX: Int, targetY: Int): CardBox {
        val base = normalize(moving)
        val target = move(base, targetX, targetY)
        if (fitsAt(cards, target)) return target

        var best: CardBox? = null
        var bestScore = Int.MAX_VALUE
        val maxY = cards.maxOfOrNull { it.y.coerceAtLeast(0) + it.rows } ?: 0
        // 在目标周围找最近的空位：先看上下左右，再看更远的行
        for (y in 0..(maxY + 2)) {
            for (x in 0..(COLUMNS - base.cols)) {
                val candidate = base.copy(x = x, y = y, cols = base.cols, rows = base.rows)
                if (!fitsAt(cards, candidate)) continue
                val score = kotlin.math.abs(x - target.x) + kotlin.math.abs(y - target.y)
                if (score < bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
        }
        if (best != null) return best

        // 理论上上一轮一定找得到；兜底放到最下面
        val fallbackY = maxY
        return base.copy(x = target.x.coerceIn(0, COLUMNS - base.cols), y = fallbackY)
    }

    /**
     * 缩放时在不压到别人的前提下最多能长到几列几行。
     *
     * 用户拖动手柄想长到 [requestedCols]×[requestedRows]，如果右侧/下方有邻居，
     * 就自动停在他的边界上（返回较小的尺寸），而不是盖上去。
     *
     * @param cards 当前全部卡片（含自己）
     * @param moving 缩放前的占位
     * @param requestedCols 期望列数
     * @param requestedRows 期望行数
     * @return 实际可用的列数、行数
     */
    fun maxFreeResize(
        cards: List<CardBox>,
        moving: CardBox,
        requestedCols: Int,
        requestedRows: Int
    ): Pair<Int, Int> {
        val base = normalize(moving)
        val maxCols = requestedCols.coerceIn(1, MAX_SPAN)
        val maxRows = requestedRows.coerceIn(1, MAX_SPAN)
        // 优先保留更多列，再在列数下找最多行；第一个能放下的就是最大的
        for (cols in maxCols downTo 1) {
            for (rows in maxRows downTo 1) {
                val candidate = resize(base, cols, rows)
                if (fitsAt(cards, candidate)) return cols to rows
            }
        }
        return 1 to 1
    }
}
