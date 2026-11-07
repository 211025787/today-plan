package com.focuslist.app.util

import kotlin.math.abs
import kotlin.math.roundToInt

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
 * 卡片尺寸，单位是格子。
 *
 * @property cols 占列数
 * @property rows 占行数
 */
data class CardShape(val cols: Int, val rows: Int) {
    /** 展示用标签，如 "2×2" */
    val label: String get() = "${cols}×$rows"
}

/**
 * 日卡片的自由排版算法。
 *
 * 关键约定：
 * - 卡片尺寸就是它占的格子数（1..4），画出来的大小和占位永远一一对应——
 *   这是「缩放时邻居实时被挤开」能对得上的前提；
 * - 手动摆放过的卡片位置不会因为别人变化而漂移，只有 [arrange] 会挪动被压到的卡；
 * - 任意时刻允许的最大占位是 4 列 × 4 行，x 必须满足 x + cols ≤ 4。
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

        // 最后再保证一次「整块板子不重叠」：历史数据里可能有两张卡被摆在同一个格子上
        // （旧版本允许手工叠放），那会让后面的每次整理都拿一个已经重叠的局面去算。
        // 这里按顺序把后到的卡往下让，输出的布局永远不重叠。
        val settled = ArrayList<CardBox>(result.size)
        result.forEach { card ->
            var y = card.y
            while (true) {
                val candidate = card.copy(y = y)
                val hits = settled.filter { overlaps(candidate, it) }
                if (hits.isEmpty()) {
                    settled.add(candidate)
                    return@forEach
                }
                y = hits.maxOf { it.y + it.rows }
            }
        }
        return settled
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
     * 整块板子上是否还有任意两张卡叠在一起。
     *
     * 渲染前的兜底用：只要还叠着就先整理一遍再画，
     * 屏幕上永远不该出现两张压在一起的卡。
     *
     * @param cards 全部卡片
     * @return 存在重叠时为 true
     */
    fun hasOverlap(cards: List<CardBox>): Boolean {
        for (i in cards.indices) {
            for (j in i + 1 until cards.size) {
                if (overlaps(cards[i], cards[j])) return true
            }
        }
        return false
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
                val score = abs(x - target.x) + abs(y - target.y)
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
     * 把实时拖动的浮点尺寸吸附成格子数。
     *
     * 逐轴四舍五入、互不影响——横向拖动只改列、纵向只改行。
     * 早先版本是「在六种预设里找最近的二维点」，会出现「往宽了拖反而变窄」
     * 的怪现象，也让缩放时无法预测；这里改成独立取整。
     *
     * @param cols 实时列数（可带小数）
     * @param rows 实时行数（可带小数）
     * @param maxCols 列数上限（受卡片所在列限制，最多 4）
     * @param maxRows 行数上限
     * @return 合法规格
     */
    fun snapShape(
        cols: Float,
        rows: Float,
        maxCols: Int = COLUMNS,
        maxRows: Int = MAX_SPAN
    ): CardShape = CardShape(
        cols = cols.roundToInt().coerceIn(1, maxCols.coerceAtLeast(1)),
        rows = rows.roundToInt().coerceIn(1, maxRows.coerceAtLeast(1))
    )

    /**
     * 拖动时卡片落到哪一格：带滞回的取整。
     *
     * 直接用 round 的话，手指停在格线上时会左右反复跳，
     * 被推着让位的邻居就跟着抖。这里只有离开当前格中心超过 [threshold] 才换格，
     * 阈值取 0.6 格——足够稳，又不至于让跟手感变钝。
     *
     * @param current 当前所在的格子
     * @param position 手指（卡片左上角）现在的浮点位置，单位是格
     * @param threshold 换格阈值，单位是格
     * @return 新的格子
     */
    fun snapCell(current: Int, position: Float, threshold: Float = 0.6f): Int =
        if (abs(position - current) > threshold) position.roundToInt() else current

    /**
     * 整理：锚点卡留在原地；其余卡**自己家还空着就不动**，被压到的卡
     * 从顶部按行扫描、落进**第一个放得下的空位**，直到互不重叠。
     *
     * 两条能被眼睛验证的性质：
     * - 挪一张卡到空白处，其它卡一张都不动（自己家空着就原地保留）；
     * - 被挤走的卡**往上收**：锚点刚腾出来的上方空洞会被最先补上，
     *   实在没有空位才往下面的新行去——和手机桌面的自动整理方向一致。
     *   结果同样是构造性无重叠的（每张卡落位前都和已落位的全部卡比对过，
     *   落位后不再移动）。
     *
     * @param cards 当前全部卡片
     * @param anchor 刚被拖动 / 缩放过的卡片（已带最终位置）
     * @return 整理后的全部卡片（顺序与输入一致）
     */
    fun arrange(cards: List<CardBox>, anchor: CardBox): List<CardBox> {
        if (cards.isEmpty()) return emptyList()
        val anchorId = anchor.trip to anchor.key
        val placed = LinkedHashMap<Pair<Boolean, Long>, CardBox>()
        placed[anchorId] = normalize(anchor)

        // 处理顺序固定「先上后下、先左后右」，同一局面结果唯一
        val order = cards.indices
            .filter { (cards[it].trip to cards[it].key) != anchorId }
            .sortedWith(compareBy({ cards[it].y }, { cards[it].x }))

        order.forEach { index ->
            val base = normalize(cards[index])
            val home = base.copy(
                x = base.x.coerceIn(0, (COLUMNS - base.cols).coerceAtLeast(0)),
                y = base.y.coerceAtLeast(0)
            )
            // 自己家还空着就不动：「挪一张卡到空白处，别的卡不动」由这句保证
            if (placed.values.none { overlaps(home, it) }) {
                placed[base.trip to base.key] = home
                return@forEach
            }
            // 被挤走了：从顶部开始按行扫描（行优先、从左到右），落进第一个
            // 放得下的空位。锚点刚腾出来的上方空洞会被最先补上——自动整理
            // 是「往上收」，而不是一路往下掉。行号没有上界，循环必然终止。
            var spot: CardBox? = null
            var y = 0
            val maxX = (COLUMNS - base.cols).coerceAtLeast(0)
            // 同一行里先试原本那一列，再往两边找：被挤的卡尽量只上下移动，
            // 不会从最右列瞬间横跳到最左列（那看着就像"自己跑掉了"）。
            val columns = (0..maxX).sortedWith(compareBy({ abs(it - base.x) }, { it }))
            while (spot == null) {
                for (x in columns) {
                    val candidate = base.copy(x = x, y = y)
                    if (placed.values.none { overlaps(candidate, it) }) {
                        spot = candidate
                        break
                    }
                }
                y++
            }
            placed[base.trip to base.key] = spot!!
        }
        return cards.map { placed[it.trip to it.key] ?: it }
    }
}
