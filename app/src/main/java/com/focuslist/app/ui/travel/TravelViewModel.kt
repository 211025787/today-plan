package com.focuslist.app.ui.travel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focuslist.app.data.local.entity.TripEntity
import com.focuslist.app.data.local.entity.TripLegEntity
import com.focuslist.app.data.local.entity.TripStopEntity
import com.focuslist.app.data.prefs.TravelPrefs
import com.focuslist.app.data.repo.TripArrival
import com.focuslist.app.data.repo.TripRepository
import com.focuslist.app.data.travel.TravelRouteClient
import com.focuslist.app.data.travel.TravelRouteResult
import com.focuslist.app.util.LatLon
import com.focuslist.app.util.TravelGeo
import com.focuslist.app.util.TravelMode
import com.focuslist.app.util.TravelPlace
import com.focuslist.app.util.TripTemplate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 一条行程的完整界面状态。
 *
 * @property trip 行程本身；没有选中行程时 null
 * @property stops 按顺序排列的站点
 * @property legs 第 i 段连接第 i 站与第 i+1 站
 */
data class TravelPlanUi(
    val trip: TripEntity? = null,
    val stops: List<TripStopEntity> = emptyList(),
    val legs: List<TripLegEntity> = emptyList()
) {
    /** 是否有选中的行程 */
    val hasTrip: Boolean get() = trip != null

    /** 是否有地点可画 */
    val hasStops: Boolean get() = stops.isNotEmpty()

    /**
     * 取第 [index] 站出发的交通段。
     *
     * @param index 起点站顺序
     * @return 交通段；没有为 null
     */
    fun legAfter(index: Int): TripLegEntity? = legs.firstOrNull { it.sortIndex == index }
}

/**
 * 旅游规划 ViewModel：行程库、当前行程、站点与交通的增删改，
 * 以及「有道路的方式」自动在线补真实路线。
 *
 * @property repository 行程仓库
 * @property prefs 轻量偏好（上次打开的行程）
 * @property routes 真实路线服务
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TravelViewModel(
    private val repository: TripRepository,
    private val prefs: TravelPrefs,
    private val routes: TravelRouteClient
) : ViewModel() {

    /** 当前选中的行程 ID，0 表示未选择 */
    private val activeId = MutableStateFlow(prefs.activeTripId)

    /** 行程库是否已经完成第一次读取，用来避免首帧闪一下空态 */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** 全部行程（最近更新在前） */
    val trips: StateFlow<List<TripEntity>> = repository.observeTrips()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 当前行程的完整内容 */
    val plan: StateFlow<TravelPlanUi> = activeId
        .flatMapLatest { id ->
            if (id <= 0L) {
                flowOf(TravelPlanUi())
            } else {
                combine(
                    repository.observeTrip(id),
                    repository.observeStops(id),
                    repository.observeLegs(id)
                ) { trip, stops, legs -> TravelPlanUi(trip, stops, legs) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, TravelPlanUi())

    /** 正在在线规划路线的交通段 ID，用于弹层里的加载态 */
    private val _routingLegs = MutableStateFlow<Set<Long>>(emptySet())
    val routingLegs: StateFlow<Set<Long>> = _routingLegs.asStateFlow()

    /** 已经尝试过自动规划的交通段 ID，避免离线时反复请求 */
    private val attemptedRoutes = mutableSetOf<Long>()

    /** 自动规划最多同时跑两条，别把公共路线服务打爆 */
    private val routeGate = Semaphore(2)

    init {
        viewModelScope.launch {
            trips.collect { list ->
                _ready.value = true
                val current = list.firstOrNull { it.id == activeId.value }
                if (current == null) {
                    selectTrip(list.firstOrNull()?.id ?: 0L)
                }
            }
        }
        // 有道路的交通段如果还没有真实折线，进页面后自动补一次
        viewModelScope.launch {
            plan.collect { current -> autoRoute(current) }
        }
    }

    /**
     * 选择一条行程并记住它。
     *
     * @param tripId 行程 ID；0 表示清空选择
     */
    fun selectTrip(tripId: Long) {
        activeId.value = tripId
        prefs.activeTripId = tripId
    }

    /**
     * 新建空行程。
     *
     * @param title 名称
     * @param emoji 封面 emoji
     * @param colorHex 主题色
     */
    fun createTrip(title: String, emoji: String = "🏔", colorHex: String = "#4772FA") {
        viewModelScope.launch {
            val id = repository.createTrip(title, emoji, colorHex)
            selectTrip(id)
        }
    }

    /**
     * 从模板生成行程。
     *
     * @param template 模板
     */
    fun createFromTemplate(template: TripTemplate) {
        viewModelScope.launch {
            val id = repository.createFromTemplate(template)
            selectTrip(id)
        }
    }

    /**
     * 保存行程标题 / 颜色 / 备注。
     *
     * @param trip 行程实体
     */
    fun updateTrip(trip: TripEntity) {
        viewModelScope.launch { repository.updateTrip(trip) }
    }

    /**
     * 删除行程。
     *
     * @param tripId 行程 ID
     */
    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            repository.deleteTrip(tripId)
            if (activeId.value == tripId) selectTrip(0L)
        }
    }

    /**
     * 添加一站。
     *
     * @param place 地点
     * @param stayMinutes 停留分钟数
     * @param arrival 从上一站来的交通；第一站传 null
     */
    fun addStop(place: TravelPlace, stayMinutes: Int, arrival: TripArrival?) {
        val tripId = activeId.value
        if (tripId <= 0L) return
        viewModelScope.launch {
            repository.addStop(
                tripId = tripId,
                name = place.name,
                region = place.region,
                lat = place.lat,
                lon = place.lon,
                stayMinutes = stayMinutes,
                arrival = arrival
            )
        }
    }

    /**
     * 保存站点与它的上一段交通。
     *
     * @param stop 站点
     * @param arrival 从上一站来的交通；null 表示不改交通
     */
    fun updateStop(stop: TripStopEntity, arrival: TripArrival?) {
        viewModelScope.launch { repository.updateStop(stop, arrival) }
    }

    /**
     * 删除站点（中间站会自动补上相邻两站的新交通）。
     *
     * @param stop 站点
     */
    fun deleteStop(stop: TripStopEntity) {
        viewModelScope.launch { repository.deleteStop(stop) }
    }

    /**
     * 保存一段交通（弹层里点保存时调用）。
     *
     * @param leg 交通实体
     */
    fun saveLeg(leg: TripLegEntity) {
        viewModelScope.launch { repository.saveLeg(leg) }
    }

    /**
     * 主动为一段交通规划真实路线（弹层里的「重新规划」）。
     *
     * @param mode 交通方式
     * @param from 起点
     * @param to 终点
     * @return 路线结果；失败为 null
     */
    suspend fun planRoute(mode: TravelMode, from: LatLon, to: LatLon): TravelRouteResult? {
        return routes.route(mode, from, to)
    }

    /**
     * 自动为有道路的交通段补真实路线：已有折线的不动，失败过的本次会话不再重试。
     *
     * @param current 当前行程
     */
    private fun autoRoute(current: TravelPlanUi) {
        val tripId = current.trip?.id ?: return
        current.legs.forEach { leg ->
            val mode = TravelMode.of(leg.mode)
            if (!mode.isRealRoute || leg.polyline.isNotBlank()) return@forEach
            val from = current.stops.getOrNull(leg.sortIndex) ?: return@forEach
            val to = current.stops.getOrNull(leg.sortIndex + 1) ?: return@forEach
            if (!attemptedRoutes.add(leg.id)) return@forEach
            viewModelScope.launch {
                _routingLegs.update { it + leg.id }
                routeGate.withPermit {
                    val result = routes.route(mode, LatLon(from.lat, from.lon), LatLon(to.lat, to.lon))
                    if (result != null) {
                        repository.saveLegRoute(
                            tripId = tripId,
                            sortIndex = leg.sortIndex,
                            polyline = TravelGeo.encodePath(result.points),
                            distanceMeters = result.distanceMeters,
                            durationMinutes = if (leg.durationMinutes == 0) result.durationMinutes else 0
                        )
                    }
                }
                _routingLegs.update { it - leg.id }
            }
        }
    }
}
