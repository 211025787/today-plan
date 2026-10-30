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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.focuslist.app.data.prefs.TimerSettings
import com.focuslist.app.timer.TimerPhase
import com.focuslist.app.timer.TimerStateHolder
import com.focuslist.app.ui.common.DockItem
import com.focuslist.app.ui.common.GlassBackground
import com.focuslist.app.ui.common.GlassDockBar
import com.focuslist.app.ui.focus.FocusScreen
import com.focuslist.app.ui.habits.HabitsScreen
import com.focuslist.app.ui.home.CalendarHomeScreen
import com.focuslist.app.ui.note.NoteScreen
import com.focuslist.app.ui.note.NotesScreen
import com.focuslist.app.ui.plan.PlanTimerHolder
import com.focuslist.app.ui.settings.SettingsScreen
import com.focuslist.app.ui.theme.FocusListTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 底部 Dock 导航目标：首页（日历）、专注、习惯、我的，共四项。
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

/** 主 Tab 路由集合：仅这些路由显示 Dock 悬浮栏 */
private val mainRoutes = Destinations.entries.map { it.route }.toSet()

/**
 * 主 Activity：iOS 玻璃拟态骨架——光斑背景 + 悬浮 Dock 导航（无侧边栏）。
 */
class MainActivity : ComponentActivity() {

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
        val startRoute = if (intent?.action == "com.focuslist.app.action.OPEN_TIMER") {
            Destinations.FOCUS.route
        } else {
            Destinations.HOME.route
        }

        setContent {
            val app = application as FocusListApp
            val settings by app.container.settingsStore.settings.collectAsState(initial = TimerSettings())
            FocusListTheme(themeMode = settings.themeMode, themeColorHex = settings.themeColor) {
                AppScaffold(container = app.container, startRoute = startRoute)
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

    // 专注中（番茄钟或计划秒表在跑）：Dock 自动隐藏防烧屏，任何触摸临时唤出，静止 5 秒后再隐去
    val pomoState by TimerStateHolder.state.collectAsState()
    val planTimers by PlanTimerHolder.timers.collectAsState()
    val focusing = (pomoState.running && pomoState.phase != TimerPhase.IDLE) ||
        planTimers.values.any { it.running }
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
                modifier = Modifier.fillMaxSize()
            ) {
                // 首页：打开即日历主页；备注统一走全屏页面
                composable(Destinations.HOME.route) {
                    CalendarHomeScreen(
                        container = container,
                        onOpenNote = { id, date ->
                            navController.navigate("note_editor?noteId=$id&date=$date")
                        }
                    )
                }
                composable(Destinations.FOCUS.route) { FocusScreen(container) }
                composable(Destinations.HABITS.route) { HabitsScreen(container) }
                composable(Destinations.MINE.route) {
                    SettingsScreen(container, onOpenNotes = { navController.navigate("notes") })
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