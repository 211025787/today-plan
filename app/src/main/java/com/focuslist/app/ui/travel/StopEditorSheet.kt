package com.focuslist.app.ui.travel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslist.app.data.local.entity.TripLegEntity
import com.focuslist.app.data.local.entity.TripStopEntity
import com.focuslist.app.data.repo.TripArrival
import com.focuslist.app.data.travel.TravelRouteResult
import com.focuslist.app.ui.theme.FlagRed
import com.focuslist.app.util.LatLon
import com.focuslist.app.util.TravelFormat
import com.focuslist.app.util.TravelGeo
import com.focuslist.app.util.TravelMode
import com.focuslist.app.util.TravelPlace
import com.focuslist.app.util.TravelPlaces
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 编辑站点时的目标来源。
 */
sealed interface StopTarget {

    /** 已经在行程里的某一站 */
    data class Existing(val stopId: Long) : StopTarget

    /** 从搜索里刚选中的内置地点 */
    data class NewPlace(val place: TravelPlace) : StopTarget

    /** 在地图上点选出来的任意坐标 */
    data class MapPoint(val lat: Double, val lon: Double) : StopTarget
}

/**
 * 站点编辑弹层保存出来的内容。
 *
 * @property name 地点名
 * @property region 所属地区
 * @property lat 纬度
 * @property lon 经度
 * @property stayMinutes 停留分钟数
 * @property note 备注
 * @property arrival 从上一站怎么来；第一站为 null
 */
data class StopSave(
    val name: String,
    val region: String,
    val lat: Double,
    val lon: Double,
    val stayMinutes: Int,
    val note: String,
    val arrival: TripArrival?
)

/** 站点弹层里真实路线的获取状态 */
private sealed interface RouteStatus {
    data object Idle : RouteStatus
    data object Loading : RouteStatus
    data class Done(val distanceMeters: Long, val minutes: Int) : RouteStatus
    data object Failed : RouteStatus
}

/**
 * 地点编辑弹层：名称、停留时长、备注，以及「从上一站怎么来」的完整交通设置。
 *
 * 交通方式决定要收集哪些字段：
 * 飞机 / 高铁 / 火车 / 大巴 / 邮轮要站点与班次，公交要线路，地铁要多选线路；
 * 公交 / 大巴 / 驾车 / 骑行 / 步行会自动请求真实道路折线，地图上不再画直线。
 *
 * @param target 编辑目标
 * @param plan 当前行程
 * @param color 行程主题色
 * @param openTraffic 打开时是否直接滚到交通部分（点地图连线进来时用）
 * @param onDismiss 关闭回调
 * @param onSave 保存回调
 * @param onDelete 删除回调；第一站或新建时为 null
 * @param planRoute 真实路线规划函数
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StopEditorSheet(
    target: StopTarget,
    plan: TravelPlanUi,
    color: Color,
    openTraffic: Boolean,
    onDismiss: () -> Unit,
    onSave: (StopSave) -> Unit,
    onDelete: (() -> Unit)?,
    planRoute: suspend (TravelMode, LatLon, LatLon) -> TravelRouteResult?
) {
    val existing = (target as? StopTarget.Existing)?.let { hit ->
        plan.stops.firstOrNull { it.id == hit.stopId }
    }
    val index = remember(target, plan.stops) {
        existing?.let { stop -> plan.stops.indexOfFirst { it.id == stop.id }.takeIf { it >= 0 } }
            ?: plan.stops.size
    }
    val prev = plan.stops.getOrNull(index - 1)
    val existingLeg = remember(target, plan.legs) {
        if (prev == null) null else plan.legs.firstOrNull { it.sortIndex == index - 1 }
    }

    val initialName = when (target) {
        is StopTarget.Existing -> existing?.name.orEmpty()
        is StopTarget.NewPlace -> target.place.name
        is StopTarget.MapPoint -> "未命名地点"
    }
    val initialRegion = when (target) {
        is StopTarget.Existing -> existing?.region.orEmpty()
        is StopTarget.NewPlace -> target.place.region
        is StopTarget.MapPoint -> TravelPlaces.nearest(target.lat, target.lon, 60_000.0)
            ?.let { "地图选点 · ${it.region}" }
            .orEmpty()
    }
    val initialLat = when (target) {
        is StopTarget.Existing -> existing?.lat ?: 0.0
        is StopTarget.NewPlace -> target.place.lat
        is StopTarget.MapPoint -> target.lat
    }
    val initialLon = when (target) {
        is StopTarget.Existing -> existing?.lon ?: 0.0
        is StopTarget.NewPlace -> target.place.lon
        is StopTarget.MapPoint -> target.lon
    }

    var name by remember(target) { mutableStateOf(initialName) }
    var region by remember(target) { mutableStateOf(initialRegion) }
    var stayMinutes by remember(target) { mutableIntStateOf(existing?.stayMinutes ?: 1440) }
    var note by remember(target) { mutableStateOf(existing?.note.orEmpty()) }

    val straightMeters = remember(prev?.id, initialLat, initialLon) {
        if (prev == null) 0L else TravelGeo.distanceMeters(
            LatLon(prev.lat, prev.lon),
            LatLon(initialLat, initialLon)
        ).toLong()
    }
    val guessedMode = remember(straightMeters) {
        when {
            straightMeters > 200_000 -> TravelMode.HSR
            straightMeters > 8_000 -> TravelMode.CAR
            else -> TravelMode.WALK
        }
    }

    var mode by remember(target) {
        mutableStateOf(existingLeg?.let { TravelMode.of(it.mode) } ?: guessedMode)
    }
    var durationMinutes by remember(target) {
        mutableIntStateOf(existingLeg?.durationMinutes ?: 0)
    }
    var fromStation by remember(target) { mutableStateOf(existingLeg?.fromStation.orEmpty()) }
    var toStation by remember(target) { mutableStateOf(existingLeg?.toStation.orEmpty()) }
    var serviceNo by remember(target) { mutableStateOf(existingLeg?.serviceNo.orEmpty()) }
    var lines by remember(target) {
        mutableStateOf(
            existingLeg?.lines.orEmpty()
                .split('|')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        )
    }
    var polyline by remember(target) { mutableStateOf(existingLeg?.polyline.orEmpty()) }
    var distanceMeters by remember(target) { mutableLongStateOf(existingLeg?.distanceMeters ?: straightMeters) }
    var routeStatus by remember(target) {
        mutableStateOf<RouteStatus>(
            if (existingLeg != null && existingLeg.polyline.isNotBlank()) {
                RouteStatus.Done(existingLeg.distanceMeters, existingLeg.durationMinutes)
            } else {
                RouteStatus.Idle
            }
        )
    }
    var confirmDelete by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val trafficRequester = remember { BringIntoViewRequester() }

    /**
     * 请求真实道路路线；失败就保留弧线示意，不影响保存。
     *
     * @param selected 要规划的方式
     */
    fun fetchRoute(selected: TravelMode) {
        if (prev == null || !selected.isRealRoute) return
        scope.launch {
            routeStatus = RouteStatus.Loading
            val result = planRoute(
                selected,
                LatLon(prev.lat, prev.lon),
                LatLon(initialLat, initialLon)
            )
            if (result != null) {
                // 期间用户可能又换了交通方式，旧请求的结果直接丢弃
                if (mode != selected) return@launch
                polyline = TravelGeo.encodePath(result.points)
                distanceMeters = result.distanceMeters
                if (durationMinutes <= 0) durationMinutes = result.durationMinutes
                routeStatus = RouteStatus.Done(result.distanceMeters, result.durationMinutes)
            } else {
                if (mode != selected) return@launch
                routeStatus = RouteStatus.Failed
            }
        }
    }

    // 打开交通页且还没有真实折线时，自动试一次
    LaunchedEffect(target) {
        if (prev != null && mode.isRealRoute && polyline.isBlank()) fetchRoute(mode)
    }
    // 自动规划在后台写库后，把结果同步进当前弹层，避免保存时用旧的空折线覆盖它
    LaunchedEffect(existingLeg?.polyline, existingLeg?.distanceMeters, existingLeg?.durationMinutes) {
        val latest = existingLeg ?: return@LaunchedEffect
        if (TravelMode.of(latest.mode) != mode) return@LaunchedEffect
        if (polyline.isBlank() && latest.polyline.isNotBlank()) {
            polyline = latest.polyline
            if (distanceMeters <= 0) distanceMeters = latest.distanceMeters
            if (durationMinutes <= 0) durationMinutes = latest.durationMinutes
            routeStatus = RouteStatus.Done(latest.distanceMeters, latest.durationMinutes)
        }
    }
    LaunchedEffect(target, openTraffic) {
        if (openTraffic && prev != null) {
            delay(360)
            runCatching { trafficRequester.bringIntoView() }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (existing == null) "添加地点" else "第 ${index + 1} 站",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = TravelGeo.coordinates(initialLat, initialLon),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (existing != null && onDelete != null) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "删除这一站", tint = FlagRed)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(30) },
                    label = { Text("地点名称") },
                    singleLine = true,
                    leadingIcon = { Text("📍", fontSize = 16.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (region.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = region,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                DurationEditor(
                    value = stayMinutes,
                    onChange = { stayMinutes = it },
                    presets = STAY_PRESETS,
                    color = color,
                    label = "在这里待多久",
                    step = 60
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    label = { Text("备注（可选）") },
                    placeholder = { Text("想去的店、要看的展、住宿区域…") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                if (prev == null) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "这是第一站。添加下一站时，再选择从这一站出发的交通方式。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(modifier = Modifier.bringIntoViewRequester(trafficRequester)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "从「${prev.name}」怎么来",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (straightMeters > 0) {
                                        "直线 ${TravelFormat.distance(straightMeters)}"
                                    } else {
                                        "选择交通方式并填写用时"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(color.copy(alpha = 0.12f))
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        TravelModeGrid(
                            selected = mode,
                            color = color,
                            onSelect = { next ->
                                if (next != mode) {
                                    mode = next
                                    polyline = ""
                                    routeStatus = RouteStatus.Idle
                                    if (!next.isRealRoute) {
                                        distanceMeters = straightMeters
                                    }
                                    if (next.isRealRoute) fetchRoute(next)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        DurationEditor(
                            value = durationMinutes,
                            onChange = { durationMinutes = it },
                            presets = TravelMode.durationPresets(mode).map { it to TravelFormat.duration(it) },
                            color = color,
                            label = "路上用时",
                            step = if (mode == TravelMode.WALK || mode == TravelMode.BIKE) 10 else 30
                        )


                        if (mode.needsService && !mode.lineMode) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = serviceNo,
                                onValueChange = { serviceNo = it.take(24) },
                                label = { Text(mode.serviceLabel.orEmpty()) },
                                placeholder = {
                                    Text(
                                        when (mode) {
                                            TravelMode.BUS -> "如：302 路 / 快速公交 1 号线"
                                            TravelMode.HSR, TravelMode.TRAIN -> "如：G7008 / D8656"
                                            TravelMode.FLIGHT -> "如：MU5101"
                                            TravelMode.CRUISE -> "如：海洋光谱号"
                                            else -> "选填"
                                        }
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (mode.lineMode) {
                            Spacer(modifier = Modifier.height(14.dp))
                            SubwayLineEditor(
                                lines = lines,
                                available = remember(name, region) {
                                    TravelPlaces.subwayLines(name, region)
                                },
                                color = color,
                                onChange = { lines = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        RouteStatusRow(
                            status = routeStatus,
                            mode = mode,
                            distanceMeters = distanceMeters,
                            straightMeters = straightMeters,
                            color = color,
                            onRefresh = { fetchRoute(mode) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            Button(
                onClick = {
                    val finalName = name.trim().ifBlank { "未命名地点" }
                    val arrival = if (prev == null) {
                        null
                    } else {
                        val latest = existingLeg?.takeIf {
                            TravelMode.of(it.mode) == mode && it.polyline.isNotBlank()
                        }
                        val effectivePolyline = if (mode.isRealRoute) {
                            polyline.ifBlank { latest?.polyline.orEmpty() }
                        } else {
                            ""
                        }
                        val effectiveDistance = when {
                            mode.isRealRoute && distanceMeters > 0 -> distanceMeters
                            mode.isRealRoute && latest != null -> latest.distanceMeters
                            else -> straightMeters
                        }
                        TripArrival(
                            mode = mode,
                            durationMinutes = durationMinutes,
                            fromStation = if (mode.needsStations) fromStation.trim() else "",
                            toStation = if (mode.needsStations) toStation.trim() else "",
                            serviceNo = if (mode.needsService) serviceNo.trim() else "",
                            lines = if (mode.lineMode) lines.joinToString("|") else "",
                            distanceMeters = effectiveDistance,
                            polyline = effectivePolyline
                        )
                    }
                    onSave(
                        StopSave(
                            name = finalName,
                            region = region.trim(),
                            lat = initialLat,
                            lon = initialLon,
                            stayMinutes = stayMinutes,
                            note = note.trim(),
                            arrival = arrival
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp)
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (existing == null) "添加这一站" else "保存")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这一站？") },
            text = {
                Text(
                    if (plan.stops.size > 1) {
                        "删除后，前后的地点会自动连成一段新的交通，可以再修改方式。"
                    } else {
                        "删除后这条行程就没有地点了。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete?.invoke()
                        onDismiss()
                    }
                ) { Text("删除", color = FlagRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            }
        )
    }
}

/**

 * 地铁线路多选：先选城市里已有的线路，再支持自定义补充。
 *
 * @param lines 已选线路（顺序即换乘顺序）
 * @param available 该城市内置线路
 * @param color 主题色
 * @param onChange 变化回调
 */
@Composable
private fun SubwayLineEditor(
    lines: List<String>,
    available: List<String>,
    color: Color,
    onChange: (List<String>) -> Unit
) {
    var custom by remember { mutableStateOf("") }
    Column {
        TravelSectionTitle(
            title = "乘坐线路",
            hint = if (lines.isEmpty()) "可多选，按换乘顺序" else lines.joinToString(" → ")
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (available.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                available.forEach { line ->
                    TravelChip(
                        text = line,
                        selected = lines.contains(line),
                        color = color,
                        onClick = {
                            onChange(if (lines.contains(line)) lines - line else lines + line)
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.take(12) },
                placeholder = { Text("其它线路，如：机场线") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val line = custom.trim()
                    if (line.isNotEmpty() && !lines.contains(line)) {
                        onChange(lines + line)
                    }
                    custom = ""
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加线路", tint = color)
            }
        }
    }
}

/**
 * 真实路线状态行：加载中 / 已获取 / 失败，右侧是重新规划。
 *
 * @param status 状态
 * @param mode 交通方式
 * @param distanceMeters 当前里程
 * @param straightMeters 直线距离
 * @param color 主题色
 * @param onRefresh 重新规划
 */
@Composable
private fun RouteStatusRow(
    status: RouteStatus,
    mode: TravelMode,
    distanceMeters: Long,
    straightMeters: Long,
    color: Color,
    onRefresh: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                !mode.isRealRoute -> Text(
                    text = "🌐 ${mode.label}没有公开轨迹，地图按弧线示意",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                status is RouteStatus.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = color
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "正在规划真实路线…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                status is RouteStatus.Done -> {
                    Text(
                        text = "✓ 真实路线 ${TravelFormat.distance(status.distanceMeters)} · ${TravelFormat.duration(status.minutes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Medium
                    )
                }
                status is RouteStatus.Failed -> {
                    Text(
                        text = "未获取到真实路线，地图先显示示意线",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Text(
                        text = "可获取真实道路路线（公交 / 自驾 / 骑行 / 步行）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (mode.isRealRoute && status !is RouteStatus.Loading) {
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(onClick = onRefresh) {
                    Text(
                        text = if (status is RouteStatus.Failed) "重试" else "重新规划",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        if (straightMeters > 0) {
            Text(
                text = "直线距离 ${TravelFormat.distance(straightMeters)}" +
                    if (mode.isRealRoute && distanceMeters > 0 &&
                        kotlin.math.abs(distanceMeters - straightMeters) > 1000
                    ) {
                        " · 路线 ${TravelFormat.distance(distanceMeters)}"
                    } else {
                        ""
                    },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

/** 停留时长快捷选项 */
private val STAY_PRESETS = listOf(
    60 to "1 小时",
    180 to "3 小时",
    720 to "半天",
    1440 to "1 天",
    2880 to "2 天",
    4320 to "3 天",
    10080 to "一周"
)
