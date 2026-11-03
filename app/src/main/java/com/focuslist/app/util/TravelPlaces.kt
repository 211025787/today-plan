package com.focuslist.app.util

/**
 * 地点类型，只用于搜索结果的角标与排序。
 *
 * @property label 展示名称
 * @property emoji 搜索结果左侧的小图标
 */
enum class PlaceKind(val label: String, val emoji: String) {
    /** 城市 */
    CITY("城市", "🏙"),

    /** 小镇 / 古镇 */
    TOWN("小镇", "🏘"),

    /** 景区 / 山湖 */
    SCENIC("景区", "🏞"),

    /** 海岛 */
    ISLAND("海岛", "🏝"),

    /** 境外目的地 */
    OVERSEAS("境外", "🌏")
}

/**
 * 一个可搜索的目的地。
 *
 * @property name 名称（唯一）
 * @property region 所属省份 / 国家；景区会带上级地区，如「四川 · 阿坝」
 * @property lat WGS-84 纬度
 * @property lon WGS-84 经度
 * @property kind 类型
 * @property alias 其它叫法，多个用 `|` 分隔
 */
data class TravelPlace(
    val name: String,
    val region: String,
    val lat: Double,
    val lon: Double,
    val kind: PlaceKind = PlaceKind.CITY,
    val alias: String = ""
)

/**
 * 内置目的地库：城市、古镇、景区与境外热门目的地。
 *
 * 行程规划的核心是「在地图上搜索位置」。为了让断网时也能搜索与规划，
 * 这里内置一份常用目的地清单（约 250 条），按名称 / 别名 / 省份搜索；
 * 清单里没有的地方，可以在地图上直接点选任意坐标。
 *
 * 坐标取城市中心或景区入口附近，规划行程够用；不用于导航。
 */
object TravelPlaces {

    /** 常用目的地（热门推荐顺序） */
    private val popularNames = listOf(
        "成都", "上海", "北京", "杭州", "西安", "重庆", "三亚", "丽江",
        "大理", "厦门", "青岛", "长沙", "广州", "南京", "苏州", "香港",
        "东京", "大阪", "京都", "曼谷", "新加坡", "巴黎", "伦敦", "纽约"
    )

    /** 城市 → 常用车站 / 机场（用于站点输入建议） */
    private val stations: Map<String, List<String>> = mapOf(
        "北京" to listOf("北京南站", "北京西站", "北京站", "北京朝阳站", "北京首都国际机场", "北京大兴国际机场"),
        "上海" to listOf("上海虹桥站", "上海站", "上海南站", "上海虹桥国际机场", "上海浦东国际机场"),
        "广州" to listOf("广州南站", "广州东站", "广州站", "广州白云国际机场"),
        "深圳" to listOf("深圳北站", "深圳站", "深圳宝安国际机场"),
        "成都" to listOf("成都东站", "成都南站", "成都西站", "成都天府国际机场", "成都双流国际机场"),
        "重庆" to listOf("重庆北站", "重庆西站", "重庆江北国际机场"),
        "杭州" to listOf("杭州东站", "杭州站", "杭州西站", "杭州萧山国际机场"),
        "西安" to listOf("西安北站", "西安站", "西安咸阳国际机场"),
        "南京" to listOf("南京南站", "南京站", "南京禄口国际机场"),
        "武汉" to listOf("武汉站", "汉口站", "武昌站", "武汉天河国际机场"),
        "长沙" to listOf("长沙南站", "长沙站", "长沙黄花国际机场"),
        "昆明" to listOf("昆明南站", "昆明站", "昆明长水国际机场"),
        "青岛" to listOf("青岛站", "青岛北站", "青岛胶东国际机场"),
        "厦门" to listOf("厦门站", "厦门北站", "厦门高崎国际机场"),
        "三亚" to listOf("三亚站", "三亚凤凰国际机场"),
        "海口" to listOf("海口东站", "海口美兰国际机场"),
        "拉萨" to listOf("拉萨站", "拉萨贡嘎国际机场"),
        "乌鲁木齐" to listOf("乌鲁木齐站", "乌鲁木齐地窝堡国际机场"),
        "哈尔滨" to listOf("哈尔滨西站", "哈尔滨站", "哈尔滨太平国际机场"),
        "东京" to listOf("东京站", "新宿站", "东京羽田机场", "成田国际机场"),
        "大阪" to listOf("大阪站", "新大阪站", "关西国际机场"),
        "京都" to listOf("京都站", "京都嵯峨岚山站"),
        "首尔" to listOf("首尔站", "仁川国际机场", "金浦国际机场"),
        "曼谷" to listOf("素万那普国际机场", "廊曼国际机场", "华南蓬站"),
        "新加坡" to listOf("樟宜机场"),
        "巴黎" to listOf("巴黎北站", "戴高乐机场", "奥利机场"),
        "伦敦" to listOf("国王十字车站", "希思罗机场", "盖特威克机场"),
        "罗马" to listOf("罗马特米尼站", "菲乌米奇诺机场"),
        "纽约" to listOf("纽约中央车站", "宾夕法尼亚车站", "肯尼迪国际机场"),
        "洛杉矶" to listOf("洛杉矶联合车站", "洛杉矶国际机场"),
        "悉尼" to listOf("悉尼中央车站", "悉尼机场"),
        "香港" to listOf("香港西九龙站", "香港国际机场", "红磡站")
    )

    /** 城市 → 地铁线路（只列常用城市，可在编辑里自行添加其它线路） */
    private val subwayCityLines: Map<String, List<String>> = mapOf(
        "北京" to listOf("1号线", "2号线", "4号线", "5号线", "6号线", "7号线", "8号线", "9号线", "10号线", "13号线", "14号线", "16号线", "八通线", "昌平线", "大兴机场线"),
        "上海" to listOf("1号线", "2号线", "3号线", "4号线", "7号线", "8号线", "9号线", "10号线", "11号线", "12号线", "13号线", "16号线", "17号线", "磁浮线"),
        "广州" to listOf("1号线", "2号线", "3号线", "4号线", "5号线", "6号线", "8号线", "APM线", "广佛线"),
        "深圳" to listOf("1号线", "2号线", "3号线", "4号线", "5号线", "6号线", "7号线", "9号线", "10号线", "11号线", "20号线"),
        "成都" to listOf("1号线", "2号线", "3号线", "4号线", "5号线", "6号线", "7号线", "8号线", "9号线", "10号线", "18号线"),
        "杭州" to listOf("1号线", "2号线", "4号线", "5号线", "6号线", "7号线", "9号线", "16号线", "19号线"),
        "西安" to listOf("1号线", "2号线", "3号线", "4号线", "5号线", "6号线", "14号线", "16号线"),
        "重庆" to listOf("1号线", "2号线", "3号线", "4号线", "5号线", "6号线", "9号线", "10号线", "18号线", "环线"),
        "南京" to listOf("1号线", "2号线", "3号线", "4号线", "5号线", "7号线", "10号线", "S1号线", "S3号线", "S8号线"),
        "武汉" to listOf("1号线", "2号线", "3号线", "4号线", "5号线", "6号线", "7号线", "8号线", "11号线", "21号线"),
        "长沙" to listOf("1号线", "2号线", "3号线", "4号线", "5号线", "6号线"),
        "天津" to listOf("1号线", "2号线", "3号线", "4号线", "5号线", "6号线", "9号线"),
        "苏州" to listOf("1号线", "2号线", "3号线", "4号线", "5号线", "11号线"),
        "郑州" to listOf("1号线", "2号线", "3号线", "4号线", "5号线", "14号线"),
        "沈阳" to listOf("1号线", "2号线", "4号线", "9号线", "10号线")
    )

    /** 全部目的地，按写入顺序展示 */
    val all: List<TravelPlace> = buildList {
        // ---- 直辖市与省会 ----
        add(city("北京", "北京", 39.9042, 116.4074))
        add(city("上海", "上海", 31.2304, 121.4737))
        add(city("天津", "天津", 39.0842, 117.2009))
        add(city("重庆", "重庆", 29.5630, 106.5516))
        add(city("石家庄", "河北", 38.0428, 114.5149))
        add(city("太原", "山西", 37.8706, 112.5489))
        add(city("呼和浩特", "内蒙古", 40.8424, 111.7490))
        add(city("沈阳", "辽宁", 41.8057, 123.4315))
        add(city("长春", "吉林", 43.8171, 125.3235))
        add(city("哈尔滨", "黑龙江", 45.8038, 126.5350))
        add(city("南京", "江苏", 32.0603, 118.7969))
        add(city("杭州", "浙江", 30.2741, 120.1551))
        add(city("合肥", "安徽", 31.8206, 117.2272))
        add(city("福州", "福建", 26.0745, 119.2965))
        add(city("南昌", "江西", 28.6820, 115.8579))
        add(city("济南", "山东", 36.6512, 117.1201))
        add(city("郑州", "河南", 34.7466, 113.6254))
        add(city("武汉", "湖北", 30.5928, 114.3055))
        add(city("长沙", "湖南", 28.2282, 112.9388))
        add(city("广州", "广东", 23.1291, 113.2644))
        add(city("南宁", "广西", 22.8170, 108.3665))
        add(city("海口", "海南", 20.0440, 110.1999))
        add(city("成都", "四川", 30.5728, 104.0668))
        add(city("贵阳", "贵州", 26.6470, 106.6302))
        add(city("昆明", "云南", 25.0389, 102.7183))
        add(city("拉萨", "西藏", 29.6520, 91.1721))
        add(city("西安", "陕西", 34.3416, 108.9398))
        add(city("兰州", "甘肃", 36.0611, 103.8343))
        add(city("西宁", "青海", 36.6171, 101.7782))
        add(city("银川", "宁夏", 38.4872, 106.2309))
        add(city("乌鲁木齐", "新疆", 43.8256, 87.6168))
        add(city("香港", "香港", 22.3193, 114.1694))
        add(city("澳门", "澳门", 22.1987, 113.5439))
        add(city("台北", "台湾", 25.0330, 121.5654))

        // ---- 热门旅游城市 ----
        add(city("三亚", "海南", 18.2528, 109.5120))
        add(city("丽江", "云南", 26.8721, 100.2299))
        add(city("大理", "云南", 25.6065, 100.2676))
        add(city("桂林", "广西", 25.2736, 110.2900))
        add(town("阳朔", "广西 · 桂林", 24.7785, 110.4966))
        add(city("张家界", "湖南", 29.1170, 110.4792))
        add(city("厦门", "福建", 24.4798, 118.0894))
        add(city("青岛", "山东", 36.0671, 120.3826))
        add(city("苏州", "江苏", 31.2989, 120.5853))
        add(city("无锡", "江苏", 31.4912, 120.3119))
        add(city("扬州", "江苏", 32.3942, 119.4129))
        add(city("绍兴", "浙江", 30.0303, 120.5802))
        add(city("宁波", "浙江", 29.8683, 121.5440))
        add(city("舟山", "浙江", 29.9853, 122.2072))
        add(city("黄山", "安徽", 29.7147, 118.3376))
        add(city("景德镇", "江西", 29.2687, 117.1784))
        add(city("洛阳", "河南", 34.6197, 112.4540))
        add(city("开封", "河南", 34.7973, 114.3076))
        add(town("平遥", "山西 · 晋中", 37.1896, 112.1760))
        add(city("大同", "山西", 40.0768, 113.3001))
        add(city("敦煌", "甘肃 · 酒泉", 40.1421, 94.6619))
        add(city("张掖", "甘肃", 38.9259, 100.4499))
        add(city("嘉峪关", "甘肃", 39.7720, 98.2890))
        add(city("喀什", "新疆", 39.4704, 75.9898))
        add(city("吐鲁番", "新疆", 42.9513, 89.1895))
        add(city("伊宁", "新疆 · 伊犁", 43.9160, 81.3240))
        add(city("呼伦贝尔", "内蒙古", 49.2122, 119.7658, "海拉尔"))
        add(city("满洲里", "内蒙古 · 呼伦贝尔", 49.5978, 117.3788))
        add(town("阿尔山", "内蒙古 · 兴安盟", 47.1770, 119.9430))
        add(city("延吉", "吉林 · 延边", 42.8913, 129.5080))
        add(city("大连", "辽宁", 38.9140, 121.6147))
        add(city("秦皇岛", "河北", 39.9354, 119.5996))
        add(city("承德", "河北", 40.9515, 117.9634))
        add(city("烟台", "山东", 37.4638, 121.4479))
        add(city("威海", "山东", 37.5131, 122.1204))
        add(city("泰安", "山东", 36.1993, 117.0879))
        add(town("曲阜", "山东 · 济宁", 35.5806, 116.9865))
        add(city("泉州", "福建", 24.8741, 118.6757))
        add(city("潮州", "广东", 23.6567, 116.6226))
        add(city("汕头", "广东", 23.3535, 116.6820))
        add(city("珠海", "广东", 22.2707, 113.5767))
        add(city("佛山", "广东", 23.0215, 113.1214))
        add(city("惠州", "广东", 23.1115, 114.4161))
        add(city("北海", "广西", 21.4733, 109.1192))
        add(city("湛江", "广东", 21.2707, 110.3594))
        add(city("肇庆", "广东", 23.0469, 112.4653))
        add(city("韶关", "广东", 24.8104, 113.5972))
        add(city("清远", "广东", 23.6818, 113.0561))
        add(city("宜昌", "湖北", 30.6919, 111.2865))
        add(city("恩施", "湖北", 30.2722, 109.4882))
        add(city("十堰", "湖北", 32.6292, 110.7980))
        add(city("襄阳", "湖北", 32.0090, 112.1224))
        add(city("荆州", "湖北", 30.3353, 112.2389))
        add(city("岳阳", "湖南", 29.3570, 113.1289))
        add(city("衡阳", "湖南", 26.8938, 112.5720))
        add(city("郴州", "湖南", 25.7706, 113.0147))
        add(town("韶山", "湖南 · 湘潭", 27.9150, 112.5267))
        add(town("凤凰", "湖南 · 湘西", 27.9480, 109.5990))
        add(town("芙蓉镇", "湖南 · 湘西", 28.7430, 109.9440))
        add(city("西昌", "四川 · 凉山", 27.8940, 102.2640))
        add(city("都江堰", "四川 · 成都", 31.0026, 103.6170))
        add(city("乐山", "四川", 29.5521, 103.7656))
        add(city("峨眉山", "四川 · 乐山", 29.6012, 103.4844))
        add(town("康定", "四川 · 甘孜", 30.0493, 101.9648))
        add(town("丹巴", "四川 · 甘孜", 30.8779, 101.8861))
        add(town("新都桥", "四川 · 甘孜", 30.0442, 101.4966))
        add(scenic("四姑娘山", "四川 · 阿坝", 31.0883, 102.8950, "日隆"))
        add(scenic("稻城亚丁", "四川 · 甘孜", 28.4350, 100.3300, "亚丁|稻城"))
        add(city("稻城", "四川 · 甘孜", 29.0379, 100.2978, "稻城县"))
        add(town("理塘", "四川 · 甘孜", 29.9960, 100.2700))
        add(town("色达", "四川 · 甘孜", 32.2680, 100.3320))
        add(scenic("九寨沟", "四川 · 阿坝", 33.2600, 103.9180))
        add(scenic("黄龙", "四川 · 阿坝", 32.7500, 103.8200))
        add(city("马尔康", "四川 · 阿坝", 31.8990, 102.2210))
        add(scenic("泸沽湖", "云南 · 丽江", 27.7000, 100.7830))
        add(city("腾冲", "云南 · 保山", 25.0203, 98.4900))
        add(city("瑞丽", "云南 · 德宏", 24.0100, 97.8550))
        add(city("景洪", "云南 · 西双版纳", 22.0017, 100.7979, "西双版纳"))
        add(city("普洱", "云南", 22.7770, 100.9660))
        add(town("建水", "云南 · 红河", 23.6200, 102.8200))
        add(scenic("元阳梯田", "云南 · 红河", 23.2200, 102.8300, "元阳"))
        add(scenic("普者黑", "云南 · 文山", 24.1000, 104.1000))
        add(city("香格里拉", "云南 · 迪庆", 27.8268, 99.7060, "中甸"))
        add(scenic("梅里雪山", "云南 · 迪庆", 28.4800, 98.9100, "德钦"))
        add(scenic("虎跳峡", "云南 · 迪庆", 27.1900, 100.1000))
        add(scenic("玉龙雪山", "云南 · 丽江", 27.1000, 100.2000))
        add(city("遵义", "贵州", 27.7256, 106.9270))
        add(city("安顺", "贵州", 26.2455, 105.9400))
        add(town("荔波", "贵州 · 黔南", 25.4120, 107.8860))
        add(scenic("西江千户苗寨", "贵州 · 黔东南", 26.4900, 108.1700))
        add(town("镇远", "贵州 · 黔东南", 27.0500, 108.4200))
        add(scenic("梵净山", "贵州 · 铜仁", 27.9100, 108.6900))
        add(city("兴义", "贵州 · 黔西南", 25.0900, 104.9000))
        add(scenic("黄果树", "贵州 · 安顺", 25.9900, 105.6700))
        add(town("乌镇", "浙江 · 嘉兴", 30.7442, 120.4900))
        add(town("西塘", "浙江 · 嘉兴", 30.9470, 120.8860))
        add(town("周庄", "江苏 · 苏州", 31.1140, 120.8500))
        add(town("同里", "江苏 · 苏州", 31.1600, 120.7200))
        add(town("南浔", "浙江 · 湖州", 30.8800, 120.4300))
        add(scenic("千岛湖", "浙江 · 杭州", 29.6000, 119.0400))
        add(island("普陀山", "浙江 · 舟山", 30.0100, 122.3870))
        add(scenic("雁荡山", "浙江 · 温州", 28.3700, 121.0300))
        add(scenic("楠溪江", "浙江 · 温州", 28.1500, 120.7000))
        add(town("宏村", "安徽 · 黄山", 30.0050, 117.9900))
        add(town("西递", "安徽 · 黄山", 29.9000, 117.9900))
        add(town("婺源", "江西 · 上饶", 29.2470, 117.8610))
        add(scenic("三清山", "江西 · 上饶", 28.9200, 118.0700))
        add(scenic("庐山", "江西 · 九江", 29.5500, 115.9900))
        add(scenic("九华山", "安徽 · 池州", 30.4800, 117.8000))
        add(scenic("天柱山", "安徽 · 安庆", 30.7300, 116.4500))
        add(scenic("武夷山", "福建 · 南平", 27.7300, 117.6700))
        add(island("鼓浪屿", "福建 · 厦门", 24.4470, 118.0670))
        add(island("平潭岛", "福建 · 福州", 25.5030, 119.7900, "平潭"))
        add(island("涠洲岛", "广西 · 北海", 21.0430, 109.1150))
        add(scenic("亚龙湾", "海南 · 三亚", 18.2200, 109.6300))
        add(island("蜈支洲岛", "海南 · 三亚", 18.3150, 109.7650))
        add(city("万宁", "海南", 18.7950, 110.3900))
        add(town("博鳌", "海南 · 琼海", 19.2580, 110.4740))
        add(scenic("武功山", "江西 · 萍乡", 27.4600, 114.1700))
        add(scenic("神农架", "湖北", 31.5000, 110.3500))
        add(scenic("武当山", "湖北 · 十堰", 32.4000, 111.0000))
        add(scenic("三峡大坝", "湖北 · 宜昌", 30.8230, 111.0030))
        add(scenic("恩施大峡谷", "湖北 · 恩施", 30.4000, 109.2000))
        add(scenic("屏山峡谷", "湖北 · 恩施", 29.9800, 110.1000))
        add(city("武隆", "重庆", 29.3200, 107.7600))
        add(scenic("衡山", "湖南 · 衡阳", 27.2500, 112.7000))
        add(scenic("丹霞山", "广东 · 韶关", 25.0300, 113.7500))
        add(town("开平", "广东 · 江门", 22.3700, 112.6800))
        add(scenic("喀纳斯", "新疆 · 阿勒泰", 48.7000, 87.0000, "布尔津"))
        add(town("禾木", "新疆 · 阿勒泰", 48.5700, 87.4300))
        add(scenic("赛里木湖", "新疆 · 博尔塔拉", 44.6000, 81.2000))
        add(scenic("那拉提", "新疆 · 伊犁", 43.3000, 84.0000))
        add(scenic("天山天池", "新疆 · 昌吉", 43.8900, 88.1200))
        add(town("可可托海", "新疆 · 阿勒泰", 47.2000, 89.7800))
        add(city("库尔勒", "新疆 · 巴音郭楞", 41.7250, 86.1740))
        add(city("阿克苏", "新疆", 41.1700, 80.2600))
        add(city("和田", "新疆", 37.1140, 79.9220))
        add(town("塔县", "新疆 · 喀什", 37.7720, 75.2290, "塔什库尔干"))
        add(city("林芝", "西藏", 29.6490, 94.3624))
        add(city("日喀则", "西藏", 29.2670, 88.8800))
        add(scenic("珠峰大本营", "西藏 · 日喀则", 28.1420, 86.8510, "珠峰"))
        add(city("阿里", "西藏", 32.5030, 80.1050, "狮泉河"))
        add(scenic("青海湖", "青海 · 海南州", 36.9000, 100.2000))
        add(town("茶卡", "青海 · 海西", 36.7830, 99.0830, "茶卡盐湖"))
        add(town("夏河", "甘肃 · 甘南", 35.2020, 102.5120, "拉卜楞"))
        add(town("郎木寺", "甘肃 · 甘南", 34.0890, 102.6360))
        add(city("天水", "甘肃", 34.5809, 105.7249))
        add(scenic("华山", "陕西 · 渭南", 34.5660, 110.0900, "华阴"))
        add(city("延安", "陕西", 36.5853, 109.4898))
        add(city("榆林", "陕西", 38.2850, 109.7340))
        add(city("中卫", "宁夏", 37.5000, 105.1900))
        add(scenic("沙坡头", "宁夏 · 中卫", 37.4900, 104.9500))
        add(scenic("五台山", "山西 · 忻州", 39.0080, 113.5960))
        add(city("运城", "山西", 35.0260, 111.0070))
        add(city("赤峰", "内蒙古", 42.2586, 118.8877))
        add(city("锡林浩特", "内蒙古 · 锡林郭勒", 43.9330, 116.0860))
        add(city("包头", "内蒙古", 40.6570, 109.8400))
        add(city("鄂尔多斯", "内蒙古", 39.6083, 109.7810))
        add(scenic("长白山", "吉林 · 延边", 42.0000, 128.0560))
        add(city("漠河", "黑龙江 · 大兴安岭", 52.9720, 122.5380))
        add(town("雪乡", "黑龙江 · 牡丹江", 44.3310, 128.9200))
        add(scenic("五大连池", "黑龙江 · 黑河", 48.7200, 126.1900))
        add(city("丹东", "辽宁", 40.0000, 124.3540))
        add(city("吉林", "吉林", 43.8378, 126.5496))
        add(city("台中", "台湾", 24.1477, 120.6736))
        add(city("台南", "台湾", 22.9999, 120.2270))
        add(city("高雄", "台湾", 22.6273, 120.3014))
        add(city("花莲", "台湾", 23.9910, 121.6110))
        add(town("垦丁", "台湾 · 屏东", 21.9490, 120.7990))
        add(town("九份", "台湾 · 新北", 25.1090, 121.8440))
        add(scenic("阿里山", "台湾 · 嘉义", 23.5100, 120.8000))

        // ---- 境外热门目的地 ----
        add(abroad("东京", "日本", 35.6762, 139.6503))
        add(abroad("大阪", "日本", 34.6937, 135.5023))
        add(abroad("京都", "日本", 35.0116, 135.7681))
        add(abroad("奈良", "日本", 34.6851, 135.8048))
        add(abroad("札幌", "日本", 43.0618, 141.3545))
        add(abroad("冲绳", "日本", 26.2124, 127.6809, "那霸"))
        add(abroad("首尔", "韩国", 37.5665, 126.9780))
        add(abroad("釜山", "韩国", 35.1796, 129.0756))
        add(abroad("济州", "韩国", 33.4996, 126.5312, "济州岛"))
        add(abroad("曼谷", "泰国", 13.7563, 100.5018))
        add(abroad("清迈", "泰国", 18.7883, 98.9853))
        add(abroad("普吉", "泰国", 7.8804, 98.3923, "普吉岛"))
        add(abroad("新加坡", "新加坡", 1.3521, 103.8198))
        add(abroad("吉隆坡", "马来西亚", 3.1390, 101.6869))
        add(abroad("巴厘岛", "印度尼西亚", -8.6500, 115.2167, "登巴萨"))
        add(abroad("河内", "越南", 21.0278, 105.8342))
        add(abroad("胡志明市", "越南", 10.8231, 106.6297, "西贡"))
        add(abroad("暹粒", "柬埔寨", 13.3671, 103.8448, "吴哥窟"))
        add(abroad("马尼拉", "菲律宾", 14.5995, 120.9842))
        add(abroad("迪拜", "阿联酋", 25.2048, 55.2708))
        add(abroad("伊斯坦布尔", "土耳其", 41.0082, 28.9784))
        add(abroad("巴黎", "法国", 48.8566, 2.3522))
        add(abroad("伦敦", "英国", 51.5074, -0.1278))
        add(abroad("罗马", "意大利", 41.9028, 12.4964))
        add(abroad("威尼斯", "意大利", 45.4408, 12.3155))
        add(abroad("米兰", "意大利", 45.4642, 9.1900))
        add(abroad("佛罗伦萨", "意大利", 43.7696, 11.2558))
        add(abroad("巴塞罗那", "西班牙", 41.3874, 2.1686))
        add(abroad("马德里", "西班牙", 40.4168, -3.7038))
        add(abroad("里斯本", "葡萄牙", 38.7223, -9.1393))
        add(abroad("阿姆斯特丹", "荷兰", 52.3676, 4.9041))
        add(abroad("柏林", "德国", 52.5200, 13.4050))
        add(abroad("慕尼黑", "德国", 48.1351, 11.5820))
        add(abroad("维也纳", "奥地利", 48.2082, 16.3738))
        add(abroad("苏黎世", "瑞士", 47.3769, 8.5417))
        add(abroad("因特拉肯", "瑞士", 46.6863, 7.8632))
        add(abroad("布拉格", "捷克", 50.0755, 14.4378))
        add(abroad("布达佩斯", "匈牙利", 47.4979, 19.0402))
        add(abroad("雅典", "希腊", 37.9838, 23.7275))
        add(abroad("圣托里尼", "希腊", 36.3932, 25.4615))
        add(abroad("莫斯科", "俄罗斯", 55.7558, 37.6173))
        add(abroad("圣彼得堡", "俄罗斯", 59.9311, 30.3609))
        add(abroad("雷克雅未克", "冰岛", 64.1466, -21.9426))
        add(abroad("斯德哥尔摩", "瑞典", 59.3293, 18.0686))
        add(abroad("哥本哈根", "丹麦", 55.6761, 12.5683))
        add(abroad("奥斯陆", "挪威", 59.9139, 10.7522))
        add(abroad("赫尔辛基", "芬兰", 60.1699, 24.9384))
        add(abroad("纽约", "美国", 40.7128, -74.0060))
        add(abroad("洛杉矶", "美国", 34.0522, -118.2437))
        add(abroad("旧金山", "美国", 37.7749, -122.4194))
        add(abroad("西雅图", "美国", 47.6062, -122.3321))
        add(abroad("芝加哥", "美国", 41.8781, -87.6298))
        add(abroad("波士顿", "美国", 42.3601, -71.0589))
        add(abroad("华盛顿", "美国", 38.9072, -77.0369))
        add(abroad("拉斯维加斯", "美国", 36.1699, -115.1398))
        add(abroad("迈阿密", "美国", 25.7617, -80.1918))
        add(abroad("夏威夷", "美国", 21.3069, -157.8583, "檀香山"))
        add(abroad("温哥华", "加拿大", 49.2827, -123.1207))
        add(abroad("多伦多", "加拿大", 43.6532, -79.3832))
        add(abroad("墨西哥城", "墨西哥", 19.4326, -99.1332))
        add(abroad("坎昆", "墨西哥", 21.1619, -86.8515))
        add(abroad("里约热内卢", "巴西", -22.9068, -43.1729))
        add(abroad("圣保罗", "巴西", -23.5505, -46.6333))
        add(abroad("布宜诺斯艾利斯", "阿根廷", -34.6037, -58.3816))
        add(abroad("利马", "秘鲁", -12.0464, -77.0428))
        add(abroad("库斯科", "秘鲁", -13.5319, -71.9675))
        add(abroad("开罗", "埃及", 30.0444, 31.2357))
        add(abroad("卢克索", "埃及", 25.6872, 32.6396))
        add(abroad("开普敦", "南非", -33.9249, 18.4241))
        add(abroad("约翰内斯堡", "南非", -26.2041, 28.0473))
        add(abroad("内罗毕", "肯尼亚", -1.2921, 36.8219))
        add(abroad("马拉喀什", "摩洛哥", 31.6295, -7.9811))
        add(abroad("悉尼", "澳大利亚", -33.8688, 151.2093))
        add(abroad("墨尔本", "澳大利亚", -37.8136, 144.9631))
        add(abroad("奥克兰", "新西兰", -36.8485, 174.7633))
        add(abroad("皇后镇", "新西兰", -45.0312, 168.6626))
        add(abroad("楠迪", "斐济", -17.7765, 177.4356, "斐济"))
        add(abroad("马累", "马尔代夫", 4.1755, 73.5093, "马尔代夫"))
    }

    /** 名称 → 地点，便于模板按名字取坐标 */
    private val byNameMap: Map<String, TravelPlace> = all.associateBy { it.name }

    /** 名称 → 地铁线路 */
    private val subwayLinesByCity: Map<String, List<String>> = subwayCityLines

    /** 名称 → 常用车站 */
    private val stationsByCity: Map<String, List<String>> = stations

    /**
     * 按名称精确查找。
     *
     * @param name 名称
     * @return 地点；不存在返回 null
     */
    fun byName(name: String): TravelPlace? = byNameMap[name.trim()]

    /**
     * 热门目的地。
     *
     * @return 按推荐顺序排列的地点
     */
    fun popular(): List<TravelPlace> = popularNames.mapNotNull { byNameMap[it] }

    /**
     * 搜索目的地：全等 > 开头 > 别名 > 包含 > 省份，城市略优先于景区。
     *
     * @param query 关键词
     * @param limit 最多返回条数
     * @return 排序后的结果；空关键词返回热门
     */
    fun search(query: String, limit: Int = 24): List<TravelPlace> {
        val raw = query.trim().lowercase()
        if (raw.isEmpty()) return popular()
        // 允许用户顺手把「成都市」「婺源县」这类行政后缀敲进来
        val q = if (raw.length > 1) {
            raw.removeSuffix("市").removeSuffix("县").removeSuffix("区")
        } else {
            raw
        }
        return all.asSequence().mapNotNull { place ->
            val name = place.name.lowercase()
            val alias = place.alias.lowercase()
            val region = place.region.lowercase()
            val score = when {
                name == q || name == raw -> 120
                name.startsWith(q) -> 100
                alias.isNotEmpty() && alias.split('|').any { it == q || it == raw } -> 96
                alias.split('|').any { it.isNotBlank() && it.startsWith(q) } -> 88
                name.contains(q) -> 76
                region.startsWith(q) || region.contains(q) -> 58
                alias.contains(q) -> 54
                else -> return@mapNotNull null
            }
            place to score
        }.sortedWith(
            compareByDescending<Pair<TravelPlace, Int>> { it.second }
                .thenBy { it.first.kind.ordinal }
                .thenBy { it.first.name }
        ).take(limit).map { it.first }.toList()
    }

    /**
     * 离某个坐标最近的内置地点。
     *
     * @param lat 纬度
     * @param lon 经度
     * @param maxMeters 超过这个距离就不算「附近」
     * @return 最近的地点；没有则 null
     */
    fun nearest(lat: Double, lon: Double, maxMeters: Double = 40_000.0): TravelPlace? {
        val origin = LatLon(lat, lon)
        var best: TravelPlace? = null
        var bestDistance = Double.MAX_VALUE
        all.forEach { place ->
            val d = TravelGeo.distanceMeters(origin, LatLon(place.lat, place.lon))
            if (d < bestDistance) {
                bestDistance = d
                best = place
            }
        }
        return if (bestDistance <= maxMeters) best else null
    }

    /**
     * 推断一个地点所属的城市：本身是城市就用自己，景区取最近的城市。
     *
     * @param name 地点名
     * @param region 所属地区
     * @return 城市名；判断不出来返回空串
     */
    fun cityOf(name: String, region: String): String {
        val cleaned = name.trim().removeSuffix("市")
        if (byNameMap[cleaned]?.kind == PlaceKind.CITY) return cleaned
        if (stationsByCity.containsKey(cleaned)) return cleaned
        if (subwayLinesByCity.containsKey(cleaned)) return cleaned
        val place = byNameMap[cleaned] ?: return ""
        val origin = LatLon(place.lat, place.lon)
        val city = all.asSequence()
            .filter { it.kind == PlaceKind.CITY }
            .minByOrNull { TravelGeo.distanceMeters(origin, LatLon(it.lat, it.lon)) }
            ?: return region.substringBefore(" · ").trim()
        val distance = TravelGeo.distanceMeters(origin, LatLon(city.lat, city.lon))
        return if (distance <= 160_000.0) city.name else ""
    }

    /**
     * 站点输入建议：机场 / 车站 / 港口，优先给该城市真实存在的大站。
     *
     * @param name 地点名
     * @param region 所属地区
     * @param mode 交通方式
     * @return 建议列表，最多 6 条
     */
    fun stationSuggestions(name: String, region: String, mode: TravelMode): List<String> {
        if (!mode.needsStations) return emptyList()
        val place = byNameMap[name.trim()]
        val city = cityOf(name, region)
        if (city.isBlank()) return emptyList()
        // 景区 / 古镇 / 海岛没有对应的机场车站，宁可不建议也不要给错站
        val cityLike = place == null || place.kind == PlaceKind.CITY ||
            stationsByCity.containsKey(name.trim()) || subwayLinesByCity.containsKey(name.trim())
        if (!cityLike) return emptyList()
        val curated = stationsByCity[city].orEmpty()
        val generic = when (mode) {
            TravelMode.FLIGHT -> listOf("${city}机场", "${city}国际机场")
            TravelMode.HSR, TravelMode.TRAIN -> listOf("${city}站", "${city}东站", "${city}南站", "${city}西站")
            TravelMode.COACH -> listOf("${city}客运站", "${city}汽车站")
            TravelMode.CRUISE -> listOf("${city}港", "${city}国际邮轮港")
            else -> emptyList()
        }
        val filtered = when (mode) {
            TravelMode.FLIGHT -> curated.filter { it.contains("机场") }
            TravelMode.HSR, TravelMode.TRAIN -> curated.filter { it.contains("站") && !it.contains("客运") }
            TravelMode.COACH -> curated.filter { it.contains("客运") || it.contains("汽车") }
            TravelMode.CRUISE -> curated.filter { it.contains("港") }
            else -> emptyList()
        }
        return (filtered + generic).distinct().take(6)
    }

    /**
     * 某地点所在城市的地铁线路。
     *
     * @param name 地点名
     * @param region 所属地区
     * @return 线路名列表；没有收录时为空
     */
    fun subwayLines(name: String, region: String): List<String> {
        val city = cityOf(name, region)
        return subwayLinesByCity[city].orEmpty()
    }

    /**
     * 是否是中国境内坐标（用于决定要不要做 GCJ 纠偏）。
     *
     * @param lat 纬度
     * @param lon 经度
     * @return true 表示境内
     */
    fun isInChina(lat: Double, lon: Double): Boolean = !Gcj02.isOutOfChina(lat, lon)

    // ---- 数据构造小工具 ----

    private fun city(name: String, region: String, lat: Double, lon: Double, alias: String = "") =
        TravelPlace(name, region, lat, lon, PlaceKind.CITY, alias)

    private fun town(name: String, region: String, lat: Double, lon: Double, alias: String = "") =
        TravelPlace(name, region, lat, lon, PlaceKind.TOWN, alias)

    private fun scenic(name: String, region: String, lat: Double, lon: Double, alias: String = "") =
        TravelPlace(name, region, lat, lon, PlaceKind.SCENIC, alias)

    private fun island(name: String, region: String, lat: Double, lon: Double, alias: String = "") =
        TravelPlace(name, region, lat, lon, PlaceKind.ISLAND, alias)

    private fun abroad(name: String, region: String, lat: Double, lon: Double, alias: String = "") =
        TravelPlace(name, region, lat, lon, PlaceKind.OVERSEAS, alias)
}
