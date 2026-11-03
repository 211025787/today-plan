package com.focuslist.app.util

/**
 * 行程交通方式：决定路线怎么画、编辑弹层要收集哪些字段。
 *
 * - **有 [roadProfile] 的方式**会向在线路径服务要真实道路折线（公交、大巴、驾车、骑行、步行）；
 * - 飞机 / 高铁 / 火车 / 地铁 / 邮轮没有公开的免费轨迹服务，地图上按两点之间的平滑弧线绘制，
 *   其中飞机弧度最大、邮轮其次，观感上能一眼区分「飞的」和「走的」；
 * - [stationLabels] 不为空时，编辑弹层会给出「出发站 / 到达站」一类的两个输入框；
 * - [serviceLabel] 不为空时，会多一个「航班号 / 车次 / 线路」输入框；
 * - [lineMode] 为 true 时（地铁）改为多选线路，按「1号线 → 2号线」记录换乘。
 *
 * @property key 持久化用的稳定标识（不要改名，历史数据靠它还原）
 * @property label 展示名称
 * @property emoji 地图标签与列表里的小图标
 * @property roadProfile 在线路径服务的出行方式；null 表示无真实道路轨迹
 * @property bend 没有真实轨迹时，两点连线向侧面弯曲的比例（0 为直线）
 * @property stationLabels 起止站点两个输入框的标题；null 表示不需要
 * @property serviceLabel 班次 / 线路输入框的标题；null 表示不需要
 * @property lineMode 是否用「多选地铁线路」代替班次输入框
 */
enum class TravelMode(
    val key: String,
    val label: String,
    val emoji: String,
    val roadProfile: String? = null,
    val bend: Double = 0.05,
    val stationLabels: Pair<String, String>? = null,
    val serviceLabel: String? = null,
    val lineMode: Boolean = false
) {
    /** 飞机：机场 + 航班号，地图上画大弧度航线 */
    FLIGHT(
        key = "flight",
        label = "飞机",
        emoji = "✈️",
        bend = 0.18,
        stationLabels = "出发机场" to "到达机场",
        serviceLabel = "航班号"
    ),

    /** 高铁：车站 + 车次，轨迹按弧线示意 */
    HSR(
        key = "hsr",
        label = "高铁",
        emoji = "🚄",
        bend = 0.10,
        stationLabels = "出发站" to "到达站",
        serviceLabel = "车次"
    ),

    /** 普速火车：车站 + 车次 */
    TRAIN(
        key = "train",
        label = "火车",
        emoji = "🚆",
        bend = 0.10,
        stationLabels = "出发站" to "到达站",
        serviceLabel = "车次"
    ),

    /** 长途大巴：走真实道路，但站点/班次另记 */
    COACH(
        key = "coach",
        label = "大巴",
        emoji = "🚌",
        roadProfile = "driving",
        bend = 0.03,
        stationLabels = "出发站" to "到达站",
        serviceLabel = "班次"
    ),

    /** 城市公交：真实道路 + 公交线路号 */
    BUS(
        key = "bus",
        label = "公交",
        emoji = "🚌",
        roadProfile = "driving",
        bend = 0.02,
        stationLabels = "上车站" to "下车站",
        serviceLabel = "公交线路"
    ),

    /** 地铁：真实线路在隧道里拿不到，按换乘线路画平滑连接 */
    SUBWAY(
        key = "subway",
        label = "地铁",
        emoji = "🚇",
        bend = 0.04,
        stationLabels = "上车站" to "下车站",
        lineMode = true
    ),

    /** 自驾：真实道路 */
    CAR(
        key = "car",
        label = "驾车",
        emoji = "🚗",
        roadProfile = "driving",
        bend = 0.03
    ),

    /** 邮轮：港口 + 船次，弧线示意 */
    CRUISE(
        key = "cruise",
        label = "邮轮",
        emoji = "🚢",
        bend = 0.14,
        stationLabels = "登船港" to "到达港",
        serviceLabel = "船次 / 航线"
    ),

    /** 骑行：真实道路 */
    BIKE(
        key = "bike",
        label = "骑行",
        emoji = "🚲",
        roadProfile = "bike",
        bend = 0.02
    ),

    /** 步行：真实道路 */
    WALK(
        key = "walk",
        label = "步行",
        emoji = "🚶",
        roadProfile = "foot",
        bend = 0.01
    ),

    /** 其他：留一个兜底，避免旧数据无法还原 */
    OTHER(
        key = "other",
        label = "其他",
        emoji = "🚩",
        bend = 0.06
    );

    /** 是否有真实道路轨迹（在线路径服务可以画） */
    val isRealRoute: Boolean get() = roadProfile != null

    /** 是否需要填写起止站点 */
    val needsStations: Boolean get() = stationLabels != null

    /** 是否需要填写班次 / 线路 */
    val needsService: Boolean get() = serviceLabel != null || lineMode

    companion object {
        /** 编辑弹层里的展示顺序：陆路常用在前，邮轮与其他在后 */
        val pickerOrder: List<TravelMode> = listOf(
            CAR, HSR, TRAIN, FLIGHT, COACH, BUS, SUBWAY, CRUISE, BIKE, WALK, OTHER
        )

        /**
         * 从持久化字符串还原交通方式，未知值归到 [OTHER]。
         *
         * @param key 持久化标识
         * @return 交通方式
         */
        fun of(key: String?): TravelMode = entries.firstOrNull { it.key == key } ?: OTHER

        /**
         * 在线路径服务可能使用的 profile 名称，按优先级排列。
         *
         * OSRM 官方演示服务用 driving / bike / foot；部分镜像用 cycling / walking，
         * 请求失败时按顺序重试，尽量拿到真实路线而不是直接退化成示意线。
         *
         * @param mode 交通方式
         * @return profile 名称列表；空列表表示该方式不需要真实路线
         */
        fun profiles(mode: TravelMode): List<String> = when (mode.roadProfile) {
            "driving" -> listOf("driving", "car")
            "bike" -> listOf("bike", "cycling")
            "foot" -> listOf("foot", "walking")
            else -> emptyList()
        }

        /**
         * 编辑弹层里的时长快捷选项（分钟），按交通方式给不同的常用值。
         *
         * @param mode 交通方式
         * @return 快捷时长列表
         */
        fun durationPresets(mode: TravelMode): List<Int> = when (mode) {
            WALK, BIKE, BUS, SUBWAY -> listOf(10, 20, 30, 45, 60)
            CAR -> listOf(30, 60, 120, 180, 300)
            HSR, TRAIN, COACH -> listOf(60, 120, 180, 240, 360)
            FLIGHT -> listOf(90, 120, 150, 180, 240)
            CRUISE -> listOf(240, 480, 720, 1440)
            OTHER -> listOf(30, 60, 120, 240, 480)
        }
    }
}
