package com.focuslist.app.util

/**
 * 模板里的一站：地点名 + 停留时长 + 从上一站过来的交通。
 *
 * @property place 目的地名称（必须在 [TravelPlaces] 里能查到）
 * @property stayMinutes 停留分钟数，0 表示只是路过 / 回程
 * @property mode 从上一站到这里的交通方式；第一站为 null
 * @property travelMinutes 交通时长（分钟）
 * @property serviceNo 航班号 / 车次 / 线路
 * @property fromStation 出发站
 * @property toStation 到达站
 */
data class TripTemplateStop(
    val place: String,
    val stayMinutes: Int,
    val mode: TravelMode? = null,
    val travelMinutes: Int = 0,
    val serviceNo: String = "",
    val fromStation: String = "",
    val toStation: String = ""
)

/**
 * 行程模板：点一下就能生成一条有地点的行程，再按自己的节奏改。
 *
 * @property key 稳定标识
 * @property title 行程名称
 * @property emoji 封面 emoji
 * @property colorHex 主题色
 * @property subtitle 一句话介绍
 * @property stops 站点与交通
 */
data class TripTemplate(
    val key: String,
    val title: String,
    val emoji: String,
    val colorHex: String,
    val subtitle: String,
    val stops: List<TripTemplateStop>
)

/**
 * 内置行程模板：四条不同气质的路线，覆盖自驾、高铁、火车与城市漫步。
 */
object TravelTemplates {

    /** 模板列表 */
    val all: List<TripTemplate> = listOf(
        TripTemplate(
            key = "western-sichuan",
            title = "川西小环线",
            emoji = "🏔",
            colorHex = "#4772FA",
            subtitle = "雪山、草原与藏寨的一圈",
            stops = listOf(
                TripTemplateStop("成都", 1440),
                TripTemplateStop("四姑娘山", 1440, TravelMode.CAR, 240),
                TripTemplateStop("丹巴", 1440, TravelMode.CAR, 180),
                TripTemplateStop("新都桥", 1440, TravelMode.CAR, 150),
                TripTemplateStop("康定", 1440, TravelMode.CAR, 90),
                TripTemplateStop("成都", 0, TravelMode.CAR, 240)
            )
        ),
        TripTemplate(
            key = "jiangnan",
            title = "江南水乡",
            emoji = "🌊",
            colorHex = "#43A047",
            subtitle = "从外滩到西湖，一路小桥流水",
            stops = listOf(
                TripTemplateStop("上海", 1440),
                TripTemplateStop("苏州", 1440, TravelMode.HSR, 30, "G7008", "上海虹桥站", "苏州站"),
                TripTemplateStop("乌镇", 1440, TravelMode.CAR, 90),
                TripTemplateStop("杭州", 1440, TravelMode.CAR, 60),
                TripTemplateStop("上海", 0, TravelMode.HSR, 45, "G7304", "杭州东站", "上海虹桥站")
            )
        ),
        TripTemplate(
            key = "yunnan",
            title = "云南慢行",
            emoji = "🌿",
            colorHex = "#FB8C00",
            subtitle = "昆明、大理、丽江、香格里拉",
            stops = listOf(
                TripTemplateStop("昆明", 1440),
                TripTemplateStop("大理", 2880, TravelMode.HSR, 120, "D8656", "昆明南站", "大理站"),
                TripTemplateStop("丽江", 2880, TravelMode.TRAIN, 120, "K9611", "大理站", "丽江站"),
                TripTemplateStop("香格里拉", 2880, TravelMode.COACH, 210, "香格里拉专线", "丽江客运站", "香格里拉客运站")
            )
        ),
        TripTemplate(
            key = "kansai",
            title = "关西漫步",
            emoji = "🌸",
            colorHex = "#8E44AD",
            subtitle = "大阪进、京都住、奈良喂鹿",
            stops = listOf(
                TripTemplateStop("大阪", 1440),
                TripTemplateStop("京都", 2880, TravelMode.TRAIN, 30, "JR京都线", "大阪站", "京都站"),
                TripTemplateStop("奈良", 1440, TravelMode.TRAIN, 45, "近铁奈良线", "京都站", "近铁奈良站"),
                TripTemplateStop("大阪", 1440, TravelMode.TRAIN, 40, "近铁奈良线", "近铁奈良站", "大阪难波站")
            )
        )
    )

    /**
     * 按 key 查找模板。
     *
     * @param key 模板标识
     * @return 模板；不存在返回 null
     */
    fun byKey(key: String): TripTemplate? = all.firstOrNull { it.key == key }
}
