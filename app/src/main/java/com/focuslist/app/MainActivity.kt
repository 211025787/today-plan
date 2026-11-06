package com.focuslist.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.focuslist.app.ui.splash.WarmSplash
import com.focuslist.app.util.WarmQuotes
import com.focuslist.app.timer.TimerPhase
import com.focuslist.app.timer.TimerStateHolder
import com.focuslist.app.ui.common.BottomFadeEdge
import com.focuslist.app.ui.common.DayDetailState
import com.focuslist.app.ui.common.DockItem
import com.focuslist.app.ui.common.GlassBackground
import com.focuslist.app.ui.common.GlassDockBar
import com.focuslist.app.ui.focus.FocusScreen
import com.focuslist.app.ui.habits.HabitsScreen
import com.focuslist.app.ui.home.CalendarHomeScreen
import com.focuslist.app.ui.note.NoteScreen
import com.focuslist.app.ui.note.NotesScreen
import com.focuslist.app.ui.plan.PlanTimerHolder
import com.focuslist.app.ui.settings.AppearanceSettingsScreen
import com.focuslist.app.ui.settings.BackupSettingsScreen
import com.focuslist.app.ui.settings.CapsuleSettingsScreen
import com.focuslist.app.ui.settings.DailyGoalSettingsScreen
import com.focuslist.app.ui.settings.MapSearchSettingsScreen
import com.focuslist.app.ui.settings.PlanTemplateSettingsScreen
import com.focuslist.app.ui.settings.ReminderSettingsScreen
import com.focuslist.app.ui.settings.SettingsRoutes
import com.focuslist.app.ui.settings.SettingsScreen
import com.focuslist.app.ui.stats.AchievementsScreen
import com.focuslist.app.ui.stats.MonthReviewScreen
import com.focuslist.app.ui.stats.PlanReviewScreen
import com.focuslist.app.ui.stats.PlanStatsScreen
import com.focuslist.app.ui.stats.StatsScreen
import com.focuslist.app.ui.theme.FocusListTheme
import com.focuslist.app.ui.travel.TravelScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 底部 Dock 导航目标：首页（日历）、专注、习惯、我的，共四项。
 * 旅游规划不在 Dock，从首页 FAB「添加旅游规划」进入。
 *
 * @property route 路由名
 * @property label 标签文案（无障碍）
 * @property icon 图标
 */
enum class Destinations(val route: String, val label: String, val icon: ImageVector) {
    /** 首页：日历主页 */
    HOME("home", "首页", Icons.Default.DateRange),

    /** 专注页 */
    FOCUS("focus", "专注", Icons.Default.CheckCircle),

    /** 习惯打卡页 */
    HABITS("habits", "习惯", Icons.Default.Star),

    /** 我的页 */
    MINE("mine", "我的", Icons.Default.Person)
}

/** 旅游规划页路由与参数名 */
private const val TRAVEL_ROUTE = "travel?tripId={tripId}"
private const val TRAVEL_ARG_TRIP_ID = "tripId"

/** 旅游页进出：整屏交叉淡化 + 极轻微缩放，不做方向性平移，也不做跟手返回 */
private val travelFadeIn = fadeIn(tween(durationMillis = 280))
private val travelFadeOut = fadeOut(tween(durationMillis = 240))
private val travelEnter = travelFadeIn + scaleIn(tween(durationMillis = 300), initialScale = 0.96f)
private val travelExit = travelFadeOut + scaleOut(tween(durationMillis = 260), targetScale = 0.98f)
private val travelPopEnter = travelFadeIn + scaleIn(tween(durationMillis = 300), initialScale = 0.98f)
private val travelPopExit = travelFadeOut + scaleOut(tween(durationMillis = 260), targetScale = 0.96f)

/** 主 Tab 路由集合：仅这些路由显示 Dock 悬浮栏 */
/** 通知点击直达习惯页 */
const val ACTION_OPEN_HABITS = "com.focuslist.app.action.OPEN_HABITS"

private val mainRoutes = Destinations.entries.map { it.route }.toSet()

/**
 * 主 Activity：iOS 玻璃拟态骨架——光斑背景 + 悬浮 Dock 导航（无侧边栏）。
 */
class MainActivity : ComponentActivity() {

    /**
     * 强制应用内使用简体中文：界面文案本就是中文写死的，
     * 不锁语言的话，系统语言为英文时日期选择器、月份标题会变成英文。
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(java.util.Locale.CHINA)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    /** 通知权限申请回调 */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighestRefreshRate()
        requestNotificationPermissionIfNeeded()

        // 小组件点击"番茄钟"快捷入口：跳到专注页
        val startRoute = if (intent?.action == ACTION_OPEN_HABITS) {
            Destinations.HABITS.route
        } else if (intent?.action == "com.focuslist.app.action.OPEN_TIMER") {
            Destinations.FOCUS.route
        } else {
            Destinations.HOME.route
        }

        setContent {
            val app = application as FocusListApp
            val settings by app.settingsFlow.collectAsState()
            FocusListTheme(themeMode = settings.themeMode, themeColorHex = settings.themeColor) {
                // 每次冷启动抽一条开屏格言；从通知 / 小组件直达时跳过开屏，不耽误正事
                val warmQuote = remember { WarmQuotes.random() }
                val skipSplash = intent?.action == ACTION_OPEN_HABITS ||
                    intent?.action == "com.focuslist.app.action.OPEN_TIMER"
                var splashVisible by remember { mutableStateOf(!skipSplash) }
                val darkTheme = when (settings.themeMode) {
                    "LIGHT" -> false
                    "DARK" -> true
                    else -> isSystemInDarkTheme()
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    // 首帧只画开屏页。主界面（NavHost + 日历翻页器 + 卡片板 + 骨架屏）
                    // 一起挤进首帧的话，窗口里只有底色、要等更久才出画面，
                    // 看起来就是「点开先白一下」。
                    // 这里等开屏页画出第一帧、动画已经起步之后再在后台组合主界面：
                    // 开屏还要演两秒多，足够它组合完并加载好数据，淡出时仍是完整日历。
                    // 主界面由 WarmSplash 在「名言写完、画面静止」时回调触发组合：
                    // 动画期间一次重活都不做，组合掉帧也不会被看见。
                    var warmUpApp by remember { mutableStateOf(skipSplash) }
                    if (warmUpApp) {
                        AppScaffold(container = app.container, startRoute = startRoute)
                    }

                    AnimatedVisibility(
                        visible = splashVisible,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(280)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        WarmSplash(
                            quote = warmQuote,
                            darkTheme = darkTheme,
                            onQuoteWritten = { warmUpApp = true },
                            onFinished = { splashVisible = false }
                        )
                    }
                }
            }
        }
    }

    /**
     * 请求设备支持的最高刷新率显示模式。
     *
     * 部分机型默认把普通应用锁在 60/90Hz，通过 preferredDisplayModeId
     * 显式选择最高刷新率模式（如 120Hz）；此为应用侧请求，最终以系统调度为准
     * （省电模式、系统白名单等仍可能限制）。
     */
    private fun requestHighestRefreshRate() {
        val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        } ?: return
        val best = currentDisplay.supportedModes.maxByOrNull { it.refreshRate } ?: return
        val attrs = window.attributes
        attrs.preferredDisplayModeId = best.modeId
        window.attributes = attrs
    }

    /**
     * Android 13+ 需要运行时申请通知权限。
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 兜底：退到后台把全部未落库段写库（运行中的不清表继续跑），再持久化防杀
        val flushed = PlanTimerHolder.flushAll()
        if (flushed.isNotEmpty()) {
            val repo = (application as FocusListApp).container.planRepository
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                flushed.forEach { (planId, sec) ->
                    repo.addSessionSeconds(planId, sec)
                }
                PlanTimerHolder.persist(applicationContext)
            }
        } else {
            PlanTimerHolder.persist(applicationContext)
        }
    }
}

/**
 * 应用骨架：光斑玻璃背景 + 悬浮 Dock 导航 + 路由（无抽屉）。
 *
 * @param container 依赖容器
 * @param startRoute 起始路由
 */
@Composable
fun AppScaffold(container: AppContainer, startRoute: String) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 专注中才隐藏 Dock，且只在真正的"计时页"生效：
    // - 专注页：番茄钟运行中
    // - 日历页：已展开某天的卡片页且有计划秒表在跑（月视图不隐藏）
    // 切到其他 Tab 或返回月历后 Dock 正常显示
    val pomoState by TimerStateHolder.state.collectAsState()
    val planTimers by PlanTimerHolder.timers.collectAsState()
    val dayDetailOpen by DayDetailState.open.collectAsState()
    val focusing = when (currentRoute) {
        Destinations.FOCUS.route -> pomoState.running && pomoState.phase != TimerPhase.IDLE
        Destinations.HOME.route -> dayDetailOpen && planTimers.values.any { it.running }
        else -> false
    }
    var dockVisible by remember { mutableStateOf(true) }
    // 触摸计数：每次按下自增，用于重新计时隐藏
    var touchTick by remember { mutableStateOf(0) }

    LaunchedEffect(focusing, touchTick) {
        if (!focusing) {
            dockVisible = true
            return@LaunchedEffect
        }
        dockVisible = true
        delay(5000)
        dockVisible = false
    }

    GlassBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 只在 Initial 阶段旁听，不消费事件，不影响页面内点击与滑动
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                touchTick++
                            }
                        }
                    }
                }
        ) {
            NavHost(
                navController = navController,
                startDestination = startRoute,
                // 保持正常绘制/组合：home 目的地不能在这里被销毁，旅游页里那份
                // 只读日历页要和它共用同一个 Activity 作用域的 ViewModel。
                // 旅游页有自己的不透明底，会把它完整盖住，两层不会重影。
                modifier = Modifier.fillMaxSize()
            ) {
                // 首页：打开即日历主页；备注统一走全屏页面。
                // 与旅游页之间做交叉淡化：主页淡出/淡入，地图页淡入/淡出，整屏一起过渡，
                // 不做方向性平移，也不需要「露出左边上一页」。
                composable(
                    Destinations.HOME.route,
                    exitTransition = { travelFadeOut },
                    popEnterTransition = { travelFadeIn }
                ) {
                    CalendarHomeScreen(
                        container = container,
                        onOpenNote = { id, date ->
                            navController.navigate("note_editor?noteId=$id&date=$date")
                        },
                        // 旅游页是独立导航目的地：进出由 NavHost 的交叉淡化转场接管
                        onOpenTravel = { tripId -> navController.navigate("travel?tripId=$tripId") },
                        // 主页顶栏：日历计划统计
                        onOpenPlanStats = { navController.navigate(SettingsRoutes.PLAN_STATS) }
                    )
                }
                // 旅游规划地图页：进出都是一次完整的渐进过渡，收到返回指令后播放完动画再退出
                composable(
                    route = TRAVEL_ROUTE,
                    arguments = listOf(
                        navArgument(TRAVEL_ARG_TRIP_ID) {
                            type = NavType.LongType
                            defaultValue = -1L
                        }
                    ),
                    enterTransition = { travelEnter },
                    exitTransition = { travelExit },
                    popEnterTransition = { travelPopEnter },
                    popExitTransition = { travelPopExit }
                ) { entry ->
                    TravelScreen(
                        container = container,
                        focusTripId = entry.arguments?.getLong(TRAVEL_ARG_TRIP_ID) ?: -1L,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Destinations.FOCUS.route) {
                    // 专注页顶栏：专注统计
                    FocusScreen(
                        container = container,
                        onOpenStats = { navController.navigate(SettingsRoutes.STATS) }
                    )
                }
                composable(Destinations.HABITS.route) { HabitsScreen(container) }
                composable(Destinations.MINE.route) {
                    SettingsScreen(
                        container = container,
                        onOpen = { route -> navController.navigate(route) }
                    )
                }
                // 「我的」页的各设置子页面：入口只留一行，具体设置点进去再改
                composable(SettingsRoutes.APPEARANCE) {
                    AppearanceSettingsScreen(container = container, onBack = { navController.popBackStack() })
                }
                composable(SettingsRoutes.DAILY_GOAL) {
                    DailyGoalSettingsScreen(container = container, onBack = { navController.popBackStack() })
                }
                composable(SettingsRoutes.REMINDER) {
                    ReminderSettingsScreen(container = container, onBack = { navController.popBackStack() })
                }
                composable(SettingsRoutes.TEMPLATES) {
                    PlanTemplateSettingsScreen(container = container, onBack = { navController.popBackStack() })
                }
                composable(SettingsRoutes.MAP_SEARCH) {
                    MapSearchSettingsScreen(container = container, onBack = { navController.popBackStack() })
                }
                composable(SettingsRoutes.BACKUP) {
                    BackupSettingsScreen(container = container, onBack = { navController.popBackStack() })
                }
                // 日历计划维度的统计：数据源是 plan_logs，不是番茄专注记录
                composable(SettingsRoutes.PLAN_STATS) {
                    PlanStatsScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                        onOpenReview = { navController.navigate(SettingsRoutes.PLAN_REVIEW) }
                    )
                }
                composable(SettingsRoutes.PLAN_REVIEW) {
                    PlanReviewScreen(container = container, onBack = { navController.popBackStack() })
                }
                // 专注统计页：今日/周/月/累计 + 年度总览与年度热力图
                composable(SettingsRoutes.STATS) {
                    StatsScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                        onOpenAchievements = { navController.navigate("achievements") },
                        onOpenMonths = { navController.navigate("month_review") }
                    )
                }
                // 月度回顾：整月逐日日历 + 上月同期对比
                composable(SettingsRoutes.MONTH_REVIEW) {
                    MonthReviewScreen(container = container, onBack = { navController.popBackStack() })
                }
                // 成就殿堂：长期专注沉淀的徽章墙
                composable(SettingsRoutes.ACHIEVEMENTS) {
                    AchievementsScreen(container = container, onBack = { navController.popBackStack() })
                }
                // 悬浮胶囊设置页（外观 / 权限 / 预览）
                composable(SettingsRoutes.CAPSULE) {
                    CapsuleSettingsScreen(container = container, onBack = { navController.popBackStack() })
                }
                composable("notes") {
                    NotesScreen(
                        container = container,
                        onOpenNote = { id -> navController.navigate("note_editor?noteId=$id&date=0") },
                        onBack = { navController.popBackStack() }
                    )
                }
                // 备注全屏页：新建（noteId=-1）与查看/编辑共用
                composable(
                    route = "note_editor?noteId={noteId}&date={date}",
                    arguments = listOf(
                        navArgument("noteId") {
                            type = NavType.LongType
                            defaultValue = -1L
                        },
                        navArgument("date") {
                            type = NavType.LongType
                            defaultValue = 0L
                        }
                    )
                ) { entry ->
                    NoteScreen(
                        container = container,
                        noteId = entry.arguments?.getLong("noteId") ?: -1L,
                        defaultDate = entry.arguments?.getLong("date") ?: 0L,
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            // 底部渐变遮罩：从 Dock 栏顶部渐变到系统导航条，内容底部淡出融入背景（仅主 Tab 显示，与 Dock 同步）
            if (currentRoute in mainRoutes && dockVisible) {
                BottomFadeEdge(
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // 悬浮 Dock：仅主 Tab 页面显示，底部居中；专注中自动收起
            AnimatedVisibility(
                visible = currentRoute in mainRoutes && dockVisible,
                enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 3 },
                exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { it / 3 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                GlassDockBar(
                    items = Destinations.entries.map { DockItem(it.route, it.label, it.icon) },
                    currentRoute = currentRoute,
                    onSelect = { dest ->
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}