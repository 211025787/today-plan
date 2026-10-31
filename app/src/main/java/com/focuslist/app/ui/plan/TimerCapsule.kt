package com.focuslist.app.ui.plan

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.focuslist.app.MainActivity
import kotlin.math.abs

/**
 * 悬浮玻璃拟态计时胶囊：应用退到后台时在屏幕上方显示当前正在计时的计划。
 *
 * - 外观可自定义：背景 / 文字 / 进度条各自的颜色与不透明度，以及宽度、行高（见 [Config]）；
 * - 多计划并行时逐行展示（状态点 + 标题 + 剩余时间 + 细进度条）；
 * - 支持任意拖动，按住拖到屏幕任意位置，松手持久化；
 * - **刷新不重建窗口**：只有行数变化才重建；配色/尺寸变化只就地更新，
 *   避免每秒刷新或拖动后整窗重加造成的闪烁与拖动手感丢失；
 * - 所有视图操作统一投递到主线程执行（后台协程无 Looper，直接 addView 会失败）。
 *
 * 说明：ColorOS 的「流体云 / 灵动岛」目前不对第三方普通应用开放，
 * 因此用系统悬浮窗（TYPE_APPLICATION_OVERLAY）实现同形态胶囊。
 *
 * @param context 上下文（建议使用 Service）
 */
class TimerCapsule(private val context: Context) {

    /** 单个计时条目数据。 */
    data class Entry(
        /** 计划标题 */
        val title: String,
        /** 当前累计秒数（含已保存部分） */
        val sessionSec: Long,
        /** 每日目标秒数 */
        val goalSec: Long
    )

    /** 胶囊外观与位置配置。 */
    data class Config(
        /** 背景色，格式 "#RRGGBB" */
        val bgColorHex: String = "#FCFDFF",
        /** 背景不透明度百分比（10-100） */
        val alphaPercent: Int = 65,
        /** 宽度（dp）；0 表示自适应内容 */
        val widthDp: Int = 196,
        /** 单行高度（dp）；0 表示自适应内容 */
        val rowHeightDp: Int = 34,
        /** 文字颜色，格式 "#RRGGBB" */
        val textColorHex: String = "#1B1B2F",
        /** 文字不透明度百分比（10-100） */
        val textAlphaPercent: Int = 100,
        /** 进度条颜色，格式 "#RRGGBB" */
        val progressColorHex: String = "#4772FA",
        /** 进度条不透明度百分比（10-100） */
        val progressAlphaPercent: Int = 100,
        /** 持久化的 X 坐标（px）；-1 表示默认顶部居中 */
        val xPx: Int = -1,
        /** 持久化的 Y 坐标（px）；-1 表示默认顶部居中 */
        val yPx: Int = -1
    )

    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    /** 主线程 Handler：悬浮窗操作必须在带 Looper 的线程执行 */
    private val mainHandler = Handler(Looper.getMainLooper())

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null

    /** 行视图持有者（与 Entry 一一对应） */
    private var rows: MutableList<RowHolder> = mutableListOf()

    /** 上次应用的配置（只用于判断外观是否变化） */
    private var lastConfig: Config? = null

    /** 当前位置（px）；MIN_VALUE 表示尚未初始化 */
    private var posX = Int.MIN_VALUE
    private var posY = Int.MIN_VALUE

    /** 当前位置是否来自持久化配置（用于识别"重置位置"） */
    private var posFromPersist = false

    /** 拖动松手后的位置持久化回调（由服务设置） */
    var onPositionChanged: ((x: Int, y: Int) -> Unit)? = null

    /**
     * 展示或更新胶囊：每个运行中的计划一行。
     *
     * @param entries 计时条目列表（为空时隐藏）
     * @param config 外观与位置配置
     */
    fun show(entries: List<Entry>, config: Config = Config()) {
        mainHandler.post {
            showInternal(entries, config)
        }
    }

    /** 隐藏并移除胶囊（幂等）。 */
    fun hide() {
        mainHandler.post {
            hideInternal()
        }
    }

    /**
     * 主线程上的实际展示逻辑：
     * 行数变化（新开/结束计时）才重建窗口；外观变化只就地更新，避免闪烁。
     */
    private fun showInternal(entries: List<Entry>, config: Config) {
        if (entries.isEmpty()) {
            hideInternal()
            return
        }
        val manager = windowManager ?: return
        val view = root
        if (view == null || rows.size != entries.size) {
            hideInternal()
            val built = buildView(entries.size, config)
            try {
                manager.addView(built, windowParams(config))
            } catch (_: Exception) {
                // 未授予悬浮窗权限或系统拒绝时静默放弃
                return
            }
            root = built
            lastConfig = config
            // 自适应宽度、未拖动过：等测量完成后居中
            if (config.widthDp == 0 && !posFromPersist && !dragged) {
                built.post {
                    val lp = params ?: return@post
                    lp.x = ((screenWidth() - built.width) / 2).coerceAtLeast(0)
                    windowManager?.updateViewLayout(built, lp)
                    posX = lp.x
                }
            }
        } else {
            if (styleChanged(lastConfig, config)) {
                applyStyle(view, config)
                applyWindowSize(view, config)
                lastConfig = config
            }
            syncPositionFromConfig(config)
        }
        entries.forEachIndexed { index, entry ->
            rows.getOrNull(index)?.update(entry)
        }
    }

    /**
     * 主线程上的实际隐藏逻辑。
     */
    private fun hideInternal() {
        val view = root ?: return
        try {
            windowManager?.removeView(view)
        } catch (_: Exception) {
            // 已被系统移除时忽略
        }
        root = null
        params = null
        rows.clear()
        lastConfig = null
        posFromPersist = false
    }

    /**
     * 判断外观（不含位置）是否发生变化。
     *
     * @param previous 上一次配置
     * @param current 当前配置
     * @return 是否变化
     */
    private fun styleChanged(previous: Config?, current: Config): Boolean {
        if (previous == null) {
            return true
        }
        return previous.bgColorHex != current.bgColorHex ||
            previous.alphaPercent != current.alphaPercent ||
            previous.widthDp != current.widthDp ||
            previous.rowHeightDp != current.rowHeightDp ||
            previous.textColorHex != current.textColorHex ||
            previous.textAlphaPercent != current.textAlphaPercent ||
            previous.progressColorHex != current.progressColorHex ||
            previous.progressAlphaPercent != current.progressAlphaPercent
    }

    /**
     * 就地更新胶囊外观（背景、文字、进度条、行高），不重建窗口。
     *
     * @param view 胶囊根视图
     * @param config 新配置
     */
    private fun applyStyle(view: LinearLayout, config: Config) {
        view.background = buildBackground(config)
        rows.forEach { it.applyStyle(config) }
    }

    /**
     * 就地更新窗口宽度。
     *
     * @param view 胶囊根视图
     * @param config 新配置
     */
    private fun applyWindowSize(view: LinearLayout, config: Config) {
        val lp = params ?: return
        val target = if (config.widthDp > 0) {
            dp(config.widthDp)
        } else {
            WindowManager.LayoutParams.WRAP_CONTENT
        }
        if (lp.width != target) {
            lp.width = target
            try {
                windowManager?.updateViewLayout(view, lp)
            } catch (_: Exception) {
                // 窗口已失效时忽略
            }
        }
    }

    /**
     * 处理「重置位置」：配置里坐标为 -1 时把胶囊移回默认顶部居中。
     *
     * @param config 当前配置
     */
    private fun syncPositionFromConfig(config: Config) {
        if (dragged) {
            return
        }
        val view = root ?: return
        val lp = params ?: return
        if (config.xPx >= 0 && config.yPx >= 0) {
            if (config.xPx != posX || config.yPx != posY) {
                lp.x = config.xPx
                lp.y = config.yPx
                posX = config.xPx
                posY = config.yPx
                posFromPersist = true
                try {
                    windowManager?.updateViewLayout(view, lp)
                } catch (_: Exception) {
                    // 窗口已失效时忽略
                }
            }
        } else if (posFromPersist) {
            // 从持久化位置恢复默认：居中
            posFromPersist = false
            view.post {
                val x = ((screenWidth() - view.width) / 2).coerceAtLeast(0)
                lp.x = x
                lp.y = dp(52)
                posX = x
                posY = dp(52)
                try {
                    windowManager?.updateViewLayout(view, lp)
                } catch (_: Exception) {
                    // 窗口已失效时忽略
                }
            }
        }
    }

    /**
     * 构建胶囊视图：可自定义的半透明底 + 多行计时 + 拖动支持。
     *
     * @param rowCount 行数（计划数）
     * @param config 外观配置
     * @return 根布局
     */
    private fun buildView(rowCount: Int, config: Config): LinearLayout {
        rows = mutableListOf()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(8))
            background = buildBackground(config)
            attachDrag(this)
            setOnClickListener {
                try {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                } catch (_: Exception) {
                    // 无权限启动时忽略
                }
            }
            addRows(rowCount, config)
        }
    }

    /**
     * 构建胶囊背景：圆角 + 半透明填充 + 细描边。
     *
     * @param config 外观配置
     * @return 背景 drawable
     */
    private fun buildBackground(config: Config): GradientDrawable {
        val bgColor = parseColor(config.bgColorHex, "#FCFDFF")
        return GradientDrawable().apply {
            cornerRadius = dp(20).toFloat()
            setColor(
                Color.argb(
                    config.alphaPercent * 255 / 100,
                    Color.red(bgColor),
                    Color.green(bgColor),
                    Color.blue(bgColor)
                )
            )
            setStroke(dp(1), Color.argb(40, 71, 114, 250))
        }
    }

    /**
     * 构建单行：状态点 + 标题 + 剩余时间 + 细进度条。
     *
     * @param config 外观配置（取行高与配色）
     * @return 行容器（已注册到 [rows]）
     */
    private fun buildRow(config: Config): View {
        val textColor = withAlpha(
            parseColor(config.textColorHex, "#1B1B2F"),
            config.textAlphaPercent
        )
        val progressColor = parseColor(config.progressColorHex, "#4772FA")

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (config.rowHeightDp > 0) {
                minimumHeight = dp(config.rowHeightDp)
            }
        }
        val dot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(withAlpha(progressColor, config.progressAlphaPercent))
            }
            layoutParams = LinearLayout.LayoutParams(dp(5), dp(5)).apply {
                marginEnd = dp(6)
            }
        }
        val title = TextView(context).apply {
            textSize = 11f
            setTextColor(textColor)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f }
        }
        val time = TextView(context).apply {
            textSize = 11f
            setTextColor(textColor)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(6), 0, 0, 0)
        }
        row.addView(dot)
        row.addView(title)
        row.addView(time)

        val bar = ProgressBar(
            context,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(
                withAlpha(progressColor, config.progressAlphaPercent)
            )
            progressBackgroundTintList = ColorStateList.valueOf(
                withAlpha(progressColor, config.progressAlphaPercent * 30 / 100)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(2)
            ).apply { topMargin = dp(4) }
        }

        rows.add(RowHolder(row, dot, title, time, bar))
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(bar)
        }
    }

    /**
     * 给胶囊挂上拖动手势：移动更新窗口位置，松手回调持久化；未拖动时视为点击。
     *
     * @param view 胶囊根视图
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(view: View) {
        view.setOnTouchListener { v, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    // 记录按下瞬间的窗口位置，后续用「绝对偏移」计算，避免在已改动的坐标上反复累加
                    startX = lp.x
                    startY = lp.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val nx = startX + (event.rawX - downRawX).toInt()
                    val ny = startY + (event.rawY - downRawY).toInt()
                    lp.x = nx.coerceIn(0, (screenWidth() - v.width).coerceAtLeast(0))
                    lp.y = ny.coerceIn(0, (screenHeight() - v.height).coerceAtLeast(0))
                    if (abs(event.rawX - downRawX) > 8 || abs(event.rawY - downRawY) > 8) {
                        dragged = true
                    }
                    windowManager?.updateViewLayout(v, lp)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragged) {
                        posX = lp.x
                        posY = lp.y
                        posFromPersist = true
                        onPositionChanged?.invoke(lp.x, lp.y)
                    } else {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** 拖动手势的临时状态 */
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragged = false

    /**
     * 构建悬浮窗参数：左上角锚定（便于自由拖动），首次展示定位到顶部居中或持久化位置。
     *
     * @param config 外观与位置配置
     * @return 布局参数
     */
    private fun windowParams(config: Config): WindowManager.LayoutParams {
        if (posX == Int.MIN_VALUE) {
            if (config.xPx >= 0 && config.yPx >= 0) {
                posX = config.xPx
                posY = config.yPx
                posFromPersist = true
            } else {
                // 固定宽度时顶部居中；自适应宽度先靠左，测量完成后再居中
                posX = if (config.widthDp > 0) {
                    ((screenWidth() - dp(config.widthDp)) / 2).coerceAtLeast(0)
                } else {
                    dp(16)
                }
                posY = dp(52)
                posFromPersist = false
            }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            if (config.widthDp > 0) dp(config.widthDp) else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = posX
            y = posY
        }.also {
            params = it
        }
    }

    /**
     * 首次构建胶囊时把行加进根布局。
     */
    private fun LinearLayout.addRows(count: Int, config: Config) {
        repeat(count) { index ->
            if (index > 0) {
                addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(1, dp(5))
                })
            }
            addView(buildRow(config))
        }
    }

    /**
     * 单行视图持有者：负责就地更新样式与内容。
     */
    private inner class RowHolder(
        private val rowView: LinearLayout,
        private val dotView: View,
        private val titleView: TextView,
        private val timeView: TextView,
        private val barView: ProgressBar
    ) {
        /**
         * 就地更新该行样式（文字颜色、圆点、进度条、行高）。
         *
         * @param config 新配置
         */
        fun applyStyle(config: Config) {
            val textColor = withAlpha(
                parseColor(config.textColorHex, "#1B1B2F"),
                config.textAlphaPercent
            )
            val progressColor = parseColor(config.progressColorHex, "#4772FA")
            titleView.setTextColor(textColor)
            timeView.setTextColor(textColor)
            dotView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(withAlpha(progressColor, config.progressAlphaPercent))
            }
            barView.progressTintList = ColorStateList.valueOf(
                withAlpha(progressColor, config.progressAlphaPercent)
            )
            barView.progressBackgroundTintList = ColorStateList.valueOf(
                withAlpha(progressColor, config.progressAlphaPercent * 30 / 100)
            )
            rowView.minimumHeight = if (config.rowHeightDp > 0) dp(config.rowHeightDp) else 0
        }

        /**
         * 更新内容：时间显示为「距今日目标还剩多少」，超出目标显示负值。
         *
         * @param entry 条目
         */
        fun update(entry: Entry) {
            titleView.text = entry.title
            timeView.text = formatRemaining(entry)
            barView.progress = if (entry.goalSec > 0) {
                (entry.sessionSec * 100L / entry.goalSec).coerceIn(0L, 100L).toInt()
            } else {
                0
            }
        }

        /**
         * 剩余时间文案：有目标时显示剩余（超出目标为负值），无目标时显示已计时间。
         */
        private fun formatRemaining(entry: Entry): String {
            if (entry.goalSec <= 0) {
                return formatClock(entry.sessionSec)
            }
            val remain = entry.goalSec - entry.sessionSec
            return if (remain < 0) "-" + formatClock(-remain) else formatClock(remain)
        }

        private fun formatClock(sec: Long): String {
            val h = sec / 3600
            val m = sec % 3600 / 60
            val s = sec % 60
            return if (h > 0) {
                String.format("%d:%02d:%02d", h, m, s)
            } else {
                String.format("%02d:%02d", m, s)
            }
        }
    }

    /** 屏幕宽度（px）。 */
    private fun screenWidth(): Int = context.resources.displayMetrics.widthPixels

    /** 屏幕高度（px）。 */
    private fun screenHeight(): Int = context.resources.displayMetrics.heightPixels

    /**
     * 安全解析颜色。
     *
     * @param hex 形如 "#RRGGBB"
     * @param fallback 解析失败时的兜底色
     * @return 颜色
     */
    private fun parseColor(hex: String, fallback: String): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            Color.parseColor(fallback)
        }
    }

    /**
     * 给颜色叠加透明度百分比。
     *
     * @param color 原色
     * @param percent 不透明度百分比（10-100）
     * @return 带透明度的颜色
     */
    private fun withAlpha(color: Int, percent: Int): Int {
        return Color.argb(
            percent.coerceIn(10, 100) * 255 / 100,
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    /**
     * dp 转像素。
     *
     * @param value dp 值
     * @return 像素值
     */
    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
