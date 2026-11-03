package com.focuslist.app.ui.travel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslist.app.data.local.entity.TripLegEntity
import com.focuslist.app.data.local.entity.TripStopEntity
import com.focuslist.app.util.Gcj02
import com.focuslist.app.util.LatLon
import com.focuslist.app.util.TravelFormat
import com.focuslist.app.util.TravelGeo
import com.focuslist.app.util.TravelMode
import com.focuslist.app.util.TravelPlanGeometry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * 屏幕上一张瓦片的摆放位置。
 *
 * @property z 缩放级别
 * @property x 瓦片横坐标（已绕回 0..2^z-1）
 * @property y 瓦片纵坐标
 * @property left 左上角 X（像素）
 * @property top 左上角 Y（像素）
 * @property size 绘制边长（像素，含小数缩放）
 */
internal data class MapTileRef(
    val z: Int,
    val x: Int,
    val y: Int,
    val left: Float,
    val top: Float,
    val size: Float
) {
    /** 缓存 key */
    val key: String get() = "$z/$x/$y"
}

/**
 * 自绘地图的相机状态：Web 墨卡托中心点 + 缩放级别。
 *
 * 内部所有坐标都是「显示坐标」：国内做过 GCJ-02 纠偏，与高德瓦片对齐；
 * 从地图点选位置时用 [unprojectWgs] 还原成 WGS-84 再存库。
 *
 * @property centerLat 相机中心纬度（显示坐标）
 * @property centerLon 相机中心经度（显示坐标）
 * @property zoom 缩放级别，3 约等于全国，12 约等于城区
 */
@Stable
class TravelMapState(
    center: LatLon = LatLon(34.8, 104.0),
    zoom: Float = 4.8f
) {

    /** 相机中心纬度 */
    var centerLat by mutableDoubleStateOf(center.lat)
        private set

    /** 相机中心经度 */
    var centerLon by mutableDoubleStateOf(center.lon)
        private set

    /** 缩放级别 */
    var zoom by mutableFloatStateOf(zoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
        private set

    /** 地图视口尺寸，由 onSizeChanged 写入 */
    var viewSize by mutableStateOf(IntSize.Zero)
        private set

    /** 相机中心 */
    val center: LatLon get() = LatLon(centerLat, centerLon)

    /**
     * 记录视口尺寸。
     *
     * @param size 视口像素尺寸
     */
    fun onSizeChanged(size: IntSize) {
        viewSize = size
    }

    /**
     * 显示坐标 → 屏幕坐标。
     *
     * @param lat 显示纬度（GCJ）
     * @param lon 显示经度（GCJ）
     * @return 屏幕位置
     */
    fun project(lat: Double, lon: Double): Offset {
        val scale = scale()
        val wx = TravelGeo.worldX(lon)
        val wy = TravelGeo.worldY(lat)
        val cx = TravelGeo.worldX(centerLon)
        val cy = TravelGeo.worldY(centerLat)
        return Offset(
            (viewSize.width / 2.0 + (wx - cx) * scale).toFloat(),
            (viewSize.height / 2.0 + (wy - cy) * scale).toFloat()
        )
    }

    /**
     * 屏幕坐标 → 显示坐标（GCJ）。
     *
     * @param point 屏幕位置
     * @return 显示坐标
     */
    fun unproject(point: Offset): LatLon {
        val scale = scale()
        val cx = TravelGeo.worldX(centerLon) + (point.x - viewSize.width / 2f) / scale
        val cy = TravelGeo.worldY(centerLat) + (point.y - viewSize.height / 2f) / scale
        return TravelGeo.fromWorld(cx, cy)
    }

    /**
     * 屏幕坐标 → 存储坐标（WGS-84）。
     *
     * @param point 屏幕位置
     * @return WGS 经纬度
     */
    fun unprojectWgs(point: Offset): LatLon {
        val display = unproject(point)
        return Gcj02.toWgs(display.lat, display.lon)
    }

    /**
     * 处理拖动 / 双指缩放：保持手指下的地图点在缩放前后不漂移。
     *
     * @param centroid 手势中心
     * @param pan 拖动位移
     * @param zoomChange 缩放倍率
     */
    fun transform(centroid: Offset, pan: Offset, zoomChange: Float) {
        if (viewSize == IntSize.Zero) return
        val scale = scale()
        val cx = TravelGeo.worldX(centerLon)
        val cy = TravelGeo.worldY(centerLat)
        val focusX = cx + (centroid.x - viewSize.width / 2f) / scale
        val focusY = cy + (centroid.y - viewSize.height / 2f) / scale
        val targetZoom = (zoom + log2(zoomChange.coerceAtLeast(0.05f))).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val newScale = TravelGeo.TILE_SIZE * 2.0.pow(targetZoom.toDouble())
        // pan 是手指位移，地图要跟着手指走：手指右拖 pan.x>0 → 相机中心左移（看更西边）→ newCenterX 减小
        val newCenterX = focusX - (centroid.x - viewSize.width / 2f + pan.x) / newScale
        val newCenterY = focusY - (centroid.y - viewSize.height / 2f + pan.y) / newScale
        val next = TravelGeo.fromWorld(newCenterX, newCenterY)
        centerLat = next.lat.coerceIn(-75.0, 80.0)
        centerLon = normalizeLon(next.lon)
        zoom = targetZoom
    }

    /**
     * 以屏幕中心为焦点缩放一档，供 + / − 按钮使用。
     *
     * @param factor 倍率，大于 1 放大
     */
    fun zoomBy(factor: Float) {
        if (viewSize == IntSize.Zero) {
            zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
            return
        }
        transform(Offset(viewSize.width / 2f, viewSize.height / 2f), Offset.Zero, factor)
    }

    /**
     * 把相机对准某个显示坐标点。
     *
     * @param lat 显示纬度
     * @param lon 显示经度
     * @param newZoom 可选的新缩放级别
     */
    fun moveTo(lat: Double, lon: Double, newZoom: Float? = null) {
        centerLat = lat.coerceIn(-75.0, 80.0)
        centerLon = normalizeLon(lon)
        if (newZoom != null) zoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /**
     * 把一组点（显示坐标）放进可视区域，并避开顶部标题与底部行程条。
     *
     * @param points 需要完整显示的显示坐标点
     * @param topPx 顶部被遮挡的高度
     * @param bottomPx 底部被遮挡的高度
     * @param sidePx 左右留白
     */
    fun fit(points: List<LatLon>, topPx: Float, bottomPx: Float, sidePx: Float) {
        if (points.isEmpty() || viewSize == IntSize.Zero) return
        val bounds = TravelGeo.boundsOf(points) ?: return
        val availW = (viewSize.width - sidePx * 2f).coerceAtLeast(64f)
        val availH = (viewSize.height - topPx - bottomPx).coerceAtLeast(64f)
        val topY = TravelGeo.worldY(bounds.maxLat)
        val bottomY = TravelGeo.worldY(bounds.minLat)
        val leftX = TravelGeo.worldX(bounds.minLon)
        val rightX = TravelGeo.worldX(bounds.maxLon)
        val spanX = (rightX - leftX).coerceAtLeast(1e-9)
        val spanY = (bottomY - topY).coerceAtLeast(1e-9)
        val zoomX = log2(availW / (spanX * TravelGeo.TILE_SIZE))
        val zoomY = log2(availH / (spanY * TravelGeo.TILE_SIZE))
        var targetZoom = min(zoomX, zoomY).toFloat().coerceIn(3.2f, 15.2f)
        if (points.size == 1) targetZoom = 11f
        val scale = TravelGeo.TILE_SIZE * 2.0.pow(targetZoom.toDouble())
        val targetScreen = Offset(viewSize.width / 2f, topPx + availH / 2f)
        val centerX = (leftX + rightX) / 2.0 - (targetScreen.x - viewSize.width / 2f) / scale
        val centerY = (topY + bottomY) / 2.0 - (targetScreen.y - viewSize.height / 2f) / scale
        val next = TravelGeo.fromWorld(centerX, centerY)
        centerLat = next.lat.coerceIn(-75.0, 80.0)
        centerLon = normalizeLon(next.lon)
        zoom = targetZoom
    }

    /**
     * 当前视口需要哪些瓦片（含屏幕位置），供绘制与异步加载共用。
     *
     * @return 瓦片列表
     */
    internal fun visibleTiles(): List<MapTileRef> {
        if (viewSize == IntSize.Zero) return emptyList()
        val z = zoom.toInt().coerceIn(3, 18)
        val count = 1 shl z
        val tileScreen = TravelGeo.TILE_SIZE.toFloat() * 2.0.pow((zoom - z).toDouble()).toFloat()
        val centerX = TravelGeo.worldX(centerLon) * count
        val centerY = TravelGeo.worldY(centerLat) * count
        val halfW = (viewSize.width / 2f) / tileScreen
        val halfH = (viewSize.height / 2f) / tileScreen
        val minX = floor(centerX - halfW).toInt()
        val maxX = floor(centerX + halfW).toInt()
        val minY = max(0, floor(centerY - halfH).toInt())
        val maxY = min(count - 1, floor(centerY + halfH).toInt())
        if (minY > maxY) return emptyList()
        val result = ArrayList<MapTileRef>((maxX - minX + 1) * (maxY - minY + 1))
        for (ty in minY..maxY) {
            for (tx in minX..maxX) {
                val wrapped = ((tx % count) + count) % count
                result.add(
                    MapTileRef(
                        z = z,
                        x = wrapped,
                        y = ty,
                        left = viewSize.width / 2f + (tx - centerX).toFloat() * tileScreen,
                        top = viewSize.height / 2f + (ty - centerY).toFloat() * tileScreen,
                        size = tileScreen
                    )
                )
            }
        }
        return result
    }

    /** 当前缩放下的世界像素比例 */
    private fun scale(): Double = TravelGeo.TILE_SIZE * 2.0.pow(zoom.toDouble())

    /** 经度绕回 -180..180 */
    private fun normalizeLon(lon: Double): Double {
        var value = lon
        while (value > 180.0) value -= 360.0
        while (value < -180.0) value += 360.0
        return value
    }

    companion object {
        /** 最小缩放（世界地图视野） */
        const val MIN_ZOOM = 3f

        /** 最大缩放（街道视野） */
        const val MAX_ZOOM = 18f
    }
}

/**
 * 行程地图：底图瓦片 + 路线 + 站点标记 + 停留 / 交通标签，全部自绘。
 *
 * 之所以不用第三方地图 SDK：项目要求零第三方地图依赖、可离线降级，
 * 而 Compose Canvas 足够画出「路线 + 标签 + 编号标记」这套旅行图。
 *
 * @param plan 当前行程
 * @param color 行程主题色
 * @param state 相机状态
 * @param tiles 瓦片仓库
 * @param picking 是否处于「点地图选点」模式
 * @param bottomInset 底部被行程条遮住的高度，比例尺与版权会避开它
 * @param onStopClick 点击站点
 * @param onLegClick 点击路线（参数是起点站顺序）
 * @param onPickPoint 长按 / 选点模式点击地图
 * @param modifier 修饰符
 */
@Composable
fun TravelMap(
    plan: TravelPlanUi,
    color: Color,
    state: TravelMapState,
    tiles: TravelTileStore,
    picking: Boolean,
    bottomInset: Dp,
    onStopClick: (TripStopEntity) -> Unit,
    onLegClick: (Int) -> Unit,
    onPickPoint: (LatLon) -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // 站点 / 路线统一先纠偏成显示坐标，之后每帧只做投影，避免重复三角函数
    val stopsGcj = remember(plan.stops) {
        plan.stops.map { Gcj02.fromWgs(it.lat, it.lon) }
    }
    val pairCount = (plan.stops.size - 1).coerceAtLeast(0)
    // 以「相邻两站」为准组织交通，缺失的段也要画出来（灰色示意 + 待设置），
    // 避免导入旧数据或异常数据时地图上出现断开的路线
    val pairLegs = remember(plan.stops, plan.legs) {
        (0 until pairCount).map { index -> plan.legs.firstOrNull { it.sortIndex == index } }
    }
    val legsGcj = remember(plan.stops, pairLegs) {
        (0 until pairCount).map { index ->
            val from = plan.stops[index]
            val to = plan.stops[index + 1]
            val leg = pairLegs[index]
            val points = if (leg != null) {
                TravelPlanGeometry.legPoints(leg, LatLon(from.lat, from.lon), LatLon(to.lat, to.lon))
            } else {
                TravelGeo.curve(
                    LatLon(from.lat, from.lon),
                    LatLon(to.lat, to.lon),
                    TravelMode.OTHER.bend,
                    28
                )
            }
            points.map { Gcj02.fromWgs(it.lat, it.lon) }
        }
    }

    val stopLabelStyle = remember(onSurface) {
        TextStyle(color = onSurface, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
    val numberStyle = remember(color) {
        TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
    val legLabelStyle = remember(onSurface) {
        TextStyle(color = onSurface, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
    }
    val scaleStyle = remember(onSurface) {
        TextStyle(color = onSurface, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
    val attributionStyle = remember(onSurface) {
        TextStyle(color = onSurface.copy(alpha = 0.75f), fontSize = 8.sp)
    }

    val stopLabels = remember(plan.stops, stopLabelStyle, measurer) {
        plan.stops.map { stop ->
            val name = if (stop.name.length > 7) stop.name.take(7) + "…" else stop.name
            measurer.measure(
                AnnotatedString("$name · ${TravelFormat.stayShort(stop.stayMinutes)}"),
                stopLabelStyle
            )
        }
    }
    val stopNumbers = remember(plan.stops, numberStyle, measurer) {
        plan.stops.indices.map { index ->
            measurer.measure(AnnotatedString("${index + 1}"), numberStyle)
        }
    }
    val legLabels = remember(pairLegs, legLabelStyle, measurer) {
        pairLegs.map { leg ->
            val mode = TravelMode.of(leg?.mode)
            val duration = when {
                leg == null -> "选择交通方式"
                leg.durationMinutes > 0 -> TravelFormat.duration(leg.durationMinutes)
                else -> "时长待定"
            }
            val text = if (leg == null) "＋ 选择交通方式" else "${mode.emoji} ${mode.label} $duration"
            measurer.measure(AnnotatedString(text), legLabelStyle)
        }
    }
    val attribution = remember(attributionStyle, measurer) {
        measurer.measure(AnnotatedString(TravelTiles.ATTRIBUTION), attributionStyle)
    }

    val tileFilter = remember(dark) {
        val matrix = ColorMatrix().apply { setToSaturation(if (dark) 0.28f else 0.62f) }
        ColorFilter.colorMatrix(matrix)
    }

    val tileRefs = remember(state.zoom, state.centerLat, state.centerLon, state.viewSize) {
        state.visibleTiles()
    }
    LaunchedEffect(tileRefs, tiles) {
        if (tileRefs.isEmpty()) return@LaunchedEffect
        tiles.setVisible(tileRefs.mapTo(HashSet()) { it.key })
        // 手指还在快速拖动时先不请求，停下来再加载，省流量也省电
        delay(90)
        tileRefs.chunked(6).forEach { chunk ->
            coroutineScope {
                chunk.map { ref -> async { tiles.load(ref.z, ref.x, ref.y) } }.awaitAll()
            }
        }
    }

    /**
     * 站点标记的屏幕位置：起终点重合（环线回成都）时把后一个稍微错开，
     * 否则两个编号会完全叠在一起，看不出这是一来一回。
     */
    fun stopMarkerPoints(): List<Offset> {
        val collision = with(density) { 36.dp.toPx() }
        val step = Offset(with(density) { -6.dp.toPx() }, with(density) { 62.dp.toPx() })
        val result = ArrayList<Offset>(stopsGcj.size)
        stopsGcj.forEach { gcj ->
            var p = state.project(gcj.lat, gcj.lon)
            var guard = 0
            while (guard < 8 && result.any { (it - p).getDistance() < collision }) {
                p += step
                guard++
            }
            result.add(p)
        }
        return result
    }

    val tapHandler by rememberUpdatedState<(Offset) -> Unit> { point ->
        val stopRadius = with(density) { 26.dp.toPx() }
        val chipTopOffset = with(density) { 19.dp.toPx() }
        val chipHeight = with(density) { 21.dp.toPx() }
        val chipPadding = with(density) { 14.dp.toPx() }
        val markerPoints = stopMarkerPoints()
        val hitStop = plan.stops.indices.firstOrNull { index ->
            val p = markerPoints[index]
            if ((p - point).getDistance() <= stopRadius) return@firstOrNull true
            // 圆标下面的「名称 · 停留」也可以点
            val label = stopLabels.getOrNull(index) ?: return@firstOrNull false
            val halfWidth = (label.size.width + chipPadding) / 2f
            point.x in (p.x - halfWidth)..(p.x + halfWidth) &&
                point.y in (p.y + chipTopOffset)..(p.y + chipTopOffset + chipHeight)
        }
        if (hitStop != null) {
            onStopClick(plan.stops[hitStop])
            return@rememberUpdatedState
        }
        val legRadius = with(density) { 18.dp.toPx() }
        val hitLeg = legsGcj.indices.firstOrNull { index ->
            val line = legsGcj[index].map { state.project(it.lat, it.lon) }
            TravelPlanGeometry.distanceToPolyline(point, line) <= legRadius
        }
        if (hitLeg != null) {
            onLegClick(hitLeg)
            return@rememberUpdatedState
        }
        if (picking) {
            onPickPoint(state.unprojectWgs(point))
        }
    }
    val longPressHandler by rememberUpdatedState<(Offset) -> Unit> { point ->
        val stopRadius = with(density) { 30.dp.toPx() }
        val nearStop = stopMarkerPoints().any { (it - point).getDistance() <= stopRadius }
        if (!nearStop) onPickPoint(state.unprojectWgs(point))
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { state.onSizeChanged(it) }
                .pointerInput(Unit) {
                    detectTransformGestures(panZoomLock = true) { centroid, pan, zoomChange, _ ->
                        state.transform(centroid, pan, zoomChange)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { tapHandler(it) },
                        onLongPress = { longPressHandler(it) }
                    )
                }
        ) {
            val paper = if (dark) Color(0xFF0E1418) else Color(0xFFF2EEE5)
            drawRect(paper)

            // 底图没加载出来时的纸面网格，至少让人知道这是一张地图
            val gridColor = if (dark) Color.White.copy(alpha = 0.05f) else Color(0xFF6F7F72).copy(alpha = 0.10f)
            val gridStep = 44.dp.toPx()
            var gx = 0f
            while (gx < size.width) {
                drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                gx += gridStep
            }
            var gy = 0f
            while (gy < size.height) {
                drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                gy += gridStep
            }

            // 瓦片
            tileRefs.forEach { ref ->
                val image = tiles.images[ref.key] ?: return@forEach
                drawImage(
                    image = image,
                    dstOffset = IntOffset(ref.left.roundToInt(), ref.top.roundToInt()),
                    dstSize = IntSize(
                        ref.size.roundToInt().coerceAtLeast(1) + 1,
                        ref.size.roundToInt().coerceAtLeast(1) + 1
                    ),
                    colorFilter = tileFilter,
                    filterQuality = FilterQuality.Low
                )
            }
            if (dark) {
                drawRect(Color(0xFF070B10).copy(alpha = 0.42f))
            } else {
                drawRect(Color.White.copy(alpha = 0.05f))
            }

            // 路线：先描一圈浅色外发光，再画主色（飞机 / 火车用虚线段区分）
            legsGcj.forEachIndexed { index, gcjPoints ->
                if (gcjPoints.size < 2) return@forEachIndexed
                val screen = gcjPoints.map { state.project(it.lat, it.lon) }
                val path = Path().apply {
                    moveTo(screen.first().x, screen.first().y)
                    for (i in 1 until screen.size) lineTo(screen[i].x, screen[i].y)
                }
                val leg = pairLegs.getOrNull(index)
                val mode = TravelMode.of(leg?.mode)
                // 每段路线按交通方式独立配色，语义清晰且天然多彩；未设置交通时用中性灰
                val lineColor = if (leg == null) {
                    onSurfaceVariant.copy(alpha = 0.55f)
                } else {
                    legColorOf(mode)
                }
                val effect = when {
                    leg == null -> PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
                    mode == TravelMode.FLIGHT -> PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx()))
                    mode == TravelMode.HSR || mode == TravelMode.TRAIN || mode == TravelMode.SUBWAY ->
                        PathEffect.dashPathEffect(floatArrayOf(16.dp.toPx(), 8.dp.toPx()))
                    mode == TravelMode.CRUISE -> PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 8.dp.toPx()))
                    else -> null
                }
                drawPath(
                    path,
                    lineColor,
                    style = Stroke(
                        width = 3.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = effect
                    )
                )
            }

            // 路线标签：交通方式 + 时长，压在线段中点
            legsGcj.forEachIndexed { index, gcjPoints ->
                if (gcjPoints.size < 2) return@forEachIndexed
                val layout = legLabels.getOrNull(index) ?: return@forEachIndexed
                val screen = gcjPoints.map { state.project(it.lat, it.lon) }
                val anchor = TravelPlanGeometry.screenMidpoint(screen) ?: return@forEachIndexed
                val chipW = layout.size.width + 16.dp.toPx()
                val chipH = layout.size.height + 9.dp.toPx()
                if (anchor.x < -chipW || anchor.x > size.width + chipW) return@forEachIndexed
                if (anchor.y < -chipH || anchor.y > size.height + chipH) return@forEachIndexed
                val topLeft = Offset(anchor.x - chipW / 2f, anchor.y - chipH / 2f)
                drawRoundRect(
                    color = surface.copy(alpha = if (dark) 0.88f else 0.93f),
                    topLeft = topLeft,
                    size = Size(chipW, chipH),
                    cornerRadius = CornerRadius(chipH / 2f)
                )
                drawRoundRect(
                    color = color.copy(alpha = 0.35f),
                    topLeft = topLeft,
                    size = Size(chipW, chipH),
                    cornerRadius = CornerRadius(chipH / 2f),
                    style = Stroke(width = 1f)
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        topLeft.x + (chipW - layout.size.width) / 2f,
                        topLeft.y + (chipH - layout.size.height) / 2f
                    )
                )
            }

            // 站点：白色圆标 + 编号，下面挂「名称 · 停留时长」
            val markerPoints = stopMarkerPoints()
            plan.stops.forEachIndexed { index, stop ->
                val p = markerPoints[index]
                if (p.x < -80.dp.toPx() || p.x > size.width + 80.dp.toPx()) return@forEachIndexed
                if (p.y < -80.dp.toPx() || p.y > size.height + 80.dp.toPx()) return@forEachIndexed
                // 标记圆圈缩小一点，减少底图遮挡
                val radius = 11.dp.toPx()
                drawCircle(Color.Black.copy(alpha = 0.16f), radius, Offset(p.x, p.y + 2.dp.toPx()))
                drawCircle(surface, radius, p)
                drawCircle(color, radius, p, style = Stroke(width = 2.dp.toPx()))
                val number = stopNumbers.getOrNull(index)
                if (number != null) {
                    drawText(
                        textLayoutResult = number,
                        topLeft = Offset(p.x - number.size.width / 2f, p.y - number.size.height / 2f)
                    )
                }
                val label = stopLabels.getOrNull(index) ?: return@forEachIndexed
                val chipW = label.size.width + 14.dp.toPx()
                val chipH = 20.dp.toPx()
                val chipTop = p.y + radius + 4.dp.toPx()
                val chipLeft = p.x - chipW / 2f
                drawRoundRect(
                    color = surface.copy(alpha = if (dark) 0.88f else 0.93f),
                    topLeft = Offset(chipLeft, chipTop),
                    size = Size(chipW, chipH),
                    cornerRadius = CornerRadius(chipH / 2f)
                )
                drawRoundRect(
                    color = color.copy(alpha = 0.28f),
                    topLeft = Offset(chipLeft, chipTop),
                    size = Size(chipW, chipH),
                    cornerRadius = CornerRadius(chipH / 2f),
                    style = Stroke(width = 1f)
                )
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(
                        chipLeft + (chipW - label.size.width) / 2f,
                        chipTop + (chipH - label.size.height) / 2f
                    )
                )
            }

            // 比例尺（避开底部行程条）
            val bottomY = size.height - bottomInset.toPx() - 16.dp.toPx()
            val mpp = TravelGeo.metersPerPixel(state.centerLat, state.zoom)
            val niceMeters = niceDistance((mpp * 72.dp.toPx()).toLong())
            val barWidth = (niceMeters / mpp).toFloat()
            val scaleText = measurer.measure(
                AnnotatedString(
                    if (niceMeters >= 1000) "${niceMeters / 1000} 公里" else "$niceMeters 米"
                ),
                scaleStyle
            )
            val scaleLeft = 14.dp.toPx()
            val scaleTop = bottomY - scaleText.size.height - 14.dp.toPx()
            drawRoundRect(
                color = surface.copy(alpha = 0.78f),
                topLeft = Offset(scaleLeft - 6.dp.toPx(), scaleTop - 3.dp.toPx()),
                size = Size(scaleText.size.width + barWidth + 16.dp.toPx(), scaleText.size.height + 16.dp.toPx()),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawText(scaleText, topLeft = Offset(scaleLeft, scaleTop))
            val barY = scaleTop + scaleText.size.height + 5.dp.toPx()
            drawLine(
                if (dark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.9f),
                Offset(scaleLeft, barY),
                Offset(scaleLeft + barWidth, barY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color,
                Offset(scaleLeft, barY),
                Offset(scaleLeft + barWidth, barY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(color, Offset(scaleLeft, barY - 3.dp.toPx()), Offset(scaleLeft, barY + 3.dp.toPx()), strokeWidth = 2.dp.toPx())
            drawLine(
                color,
                Offset(scaleLeft + barWidth, barY - 3.dp.toPx()),
                Offset(scaleLeft + barWidth, barY + 3.dp.toPx()),
                strokeWidth = 2.dp.toPx()
            )

            // 版权 / 路线来源
            val attrW = attribution.size.width + 10.dp.toPx()
            drawRoundRect(
                color = surface.copy(alpha = 0.62f),
                topLeft = Offset(size.width - attrW - 10.dp.toPx(), bottomY - attribution.size.height - 5.dp.toPx()),
                size = Size(attrW, attribution.size.height + 6.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            drawText(
                attribution,
                topLeft = Offset(size.width - attrW - 5.dp.toPx(), bottomY - attribution.size.height - 2.dp.toPx())
            )
        }

        // 底图没加载成功时给一句解释，避免误以为地图坏了
        if (tiles.loadedCount == 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset + 56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(surface.copy(alpha = 0.86f))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = if (tiles.failureCount > 0) "底图需联网 · 行程仍可离线编辑" else "地图加载中…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 比例尺取整：把任意米数收成 1/2/5 的整数档。
 *
 * @param raw 目标米数
 * @return 最接近且不超过目标的「好看」距离
 */
private fun niceDistance(raw: Long): Long {
    val candidates = longArrayOf(
        20, 50, 100, 200, 500,
        1_000, 2_000, 5_000, 10_000, 20_000, 50_000,
        100_000, 200_000, 500_000, 1_000_000, 2_000_000
    )
    var chosen = candidates.first()
    for (value in candidates) {
        if (value <= raw) chosen = value else break
    }
    return chosen
}
/**
 * 按交通方式给路线配色：语义清晰且天然多彩，一眼区分飞的还是走的。
 *
 * @param mode 交通方式
 * @return 该段路线颜色
 */
private fun legColorOf(mode: TravelMode): Color = when (mode) {
    TravelMode.FLIGHT -> Color(0xFF4FC3F7)
    TravelMode.HSR -> Color(0xFF3F51B5)
    TravelMode.TRAIN -> Color(0xFF43A047)
    TravelMode.COACH -> Color(0xFFEF6C00)
    TravelMode.BUS -> Color(0xFFE53935)
    TravelMode.SUBWAY -> Color(0xFF8E24AA)
    TravelMode.CAR -> Color(0xFFFB8C00)
    TravelMode.CRUISE -> Color(0xFF7E57C2)
    TravelMode.BIKE -> Color(0xFF00ACC1)
    TravelMode.WALK -> Color(0xFF6D4C41)
    TravelMode.OTHER -> Color(0xFF757575)
}
