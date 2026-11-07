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
 * 卡片允许的规格（与旧版六个档位一致）。
 *
 * @property cols 占列数
 * @property rows 占行数
 */
data class CardShape(val cols: Int, val rows: Int) {
    /** 展示用标签，如 "2×2" */
    val label: String get() = "${cols}×${rows}"
}

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

    /** 拖拽缩放最终只允许落到这六种规格 */
    val ALLOWED_SHAPES: List<CardShape> = listOf(
        CardShape(1, 1),
        CardShape(1, 2),
        CardShape(2, 1),
        CardShape(2, 2),
        CardShape(1, 3),
        CardShape(3, 1)
    )

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
     * 指定宽高是否是允许的六种规格。
     *
     * @param cols 列数
     * @param rows 行数
     * @return 是否合法
     */
    fun isAllowed(cols: Int, rows: Int): Boolean =
        ALLOWED_SHAPES.any { it.cols == cols && it.rows == rows }

    /**
     * 把实时拖动得到的浮点宽高吸附到最近的允许规格。
     *
     * 比如拖到 2.6×1.2 会吸附到 3×1，拖到 1.4×2.4 会吸附到 1×2。
     *
     * @param cols 实时列数（可带小数）
     * @param rows 实时行数（可带小数）
     * @return 最近的允许规格
     */
    fun nearestShape(cols: Float, rows: Float): CardShape {
        val c = cols.coerceIn(1f, MAX_SPAN.toFloat())
        val r = rows.coerceIn(1f, MAX_SPAN.toFloat())
        return ALLOWED_SHAPES.minBy { shape ->
            val dc = shape.cols - c
            val dr = shape.rows - r
            dc * dc + dr * dr
        }
    }

    /**
     * 把已有的列/行归一化到允许的六种规格。
     *
     * 旧数据 / 之前自由拖出来的 4×1、3×2 等规格会吸附到最近的合法档位。
     *
     * @param cols 原列数
     * @param rows 原行数
     * @return 合法规格
     */
    fun allowedShape(cols: Int, rows: Int): CardShape {
        val c = cols.coerceIn(1, MAX_SPAN)
        val r = rows.coerceIn(1, MAX_SPAN)
        return ALLOWED_SHAPES.firstOrNull { it.cols == c && it.rows == r }
            ?: nearestShape(c.toFloat(), r.toFloat())
    }

    /**
     * 按允许规格设置卡片大小，并把 x 收进四列内。
     *
     * @param card 原占位
     * @param shape 目标规格
     * @return 新占位
     */
    fun resizeToAllowed(card: CardBox, shape: CardShape): CardBox {
        val base = normalize(card)
        val x = base.x.coerceIn(0, COLUMNS - shape.cols)
        return base.copy(
            cols = shape.cols,
            rows = shape.rows,
            x = x,
            y = base.y.coerceAtLeast(0)
        )
    }

    /**
     * 在当前位置、保持 [rows] 行不变的前提下，最多能占几列（不会压到别的卡片）。
     *
     * 缩放时用它做实时上限：拖到边界就停住，卡片不会盖到邻居身上。
     *
     * @param cards 当前全部卡片（含自己）
     * @param card 正在缩放的卡片
     * @param rows 当前行数
     * @param limit 期望的列数上限（默认 3，即允许的最大跨列）
     * @return 放得下的最大列数，至少 1
     */
    fun maxFitCols(cards: List<CardBox>, card: CardBox, rows: Int, limit: Int = 3): Int {
        val base = normalize(card)
        val max = limit.coerceIn(1, COLUMNS - base.x)
        for (cols in max downTo 1) {
            if (fitsAt(cards, base.copy(cols = cols, rows = rows))) return cols
        }
        return 1
    }

    /**
     * 在当前位置、保持 [cols] 列不变的前提下，最多能占几行（不会压到别的卡片）。
     *
     * @param cards 当前全部卡片（含自己）
     * @param card 正在缩放的卡片
     * @param cols 当前列数
     * @param limit 期望的行数上限（默认 3）
     * @return 放得下的最大行数，至少 1
     */
    fun maxFitRows(cards: List<CardBox>, card: CardBox, cols: Int, limit: Int = 3): Int {
        val base = normalize(card)
        val max = limit.coerceAtLeast(1)
        for (rows in max downTo 1) {
            if (fitsAt(cards, base.copy(cols = cols, rows = rows))) return rows
        }
        return 1
    }

    /**
     * 挑一个离期望宽高最近、又不会压到别人的规格。
     *
     * 松手时用它做最终吸附：先按与期望值的距离排序，再逐个验证是否放得下；
     * 在当前位置一个都放不下时**保持原规格**——宁可不变，也不叠上去。
     *
     * @param cards 当前全部卡片（含自己）
     * @param card 正在缩放的卡片
     * @param desiredCols 期望列数（可带小数）
     * @param desiredRows 期望行数（可带小数）
     * @return 吸附到的规格
     */
    fun fitShapeAt(
        cards: List<CardBox>,
        card: CardBox,
        desiredCols: Float,
        desiredRows: Float
    ): CardShape {
        val base = normalize(card)
        val dc = desiredCols.coerceIn(1f, MAX_SPAN.toFloat())
        val dr = desiredRows.coerceIn(1f, MAX_SPAN.toFloat())
        return ALLOWED_SHAPES
            // 超出右边界的规格直接排除（缩放不挪位置，只在原位长）
            .filter { it.cols <= COLUMNS - base.x }
            .sortedBy { shape ->
                val dx = shape.cols - dc
                val dy = shape.rows - dr
                dx * dx + dy * dy
            }
            .firstOrNull { shape ->
                fitsAt(cards, base.copy(cols = shape.cols, rows = shape.rows))
            }
            ?: CardShape(base.cols, base.rows)
    }
}
