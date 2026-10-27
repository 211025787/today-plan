package com.focuslist.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focuslist.app.data.prefs.TimerSettings
import com.focuslist.app.ui.common.DockItem
import com.focuslist.app.ui.common.GlassBackground
import com.focuslist.app.ui.common.GlassDockBar
import com.focuslist.app.ui.focus.FocusScreen
import com.focuslist.app.ui.habits.HabitsScreen
import com.focuslist.app.ui.home.CalendarHomeScreen
import com.focuslist.app.ui.note.NotesScreen
import com.focuslist.app.ui.plan.PlanTimerHolder
import com.focuslist.app.ui.settings.SettingsScreen
import com.focuslist.app.ui.theme.FocusListTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    GlassBackground {
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier.fillMaxSize()
        ) {
            // 首页：打开即日历主页
            composable(Destinations.HOME.route) { CalendarHomeScreen(container) }
            composable(Destinations.FOCUS.route) { FocusScreen(container) }
            composable(Destinations.HABITS.route) { HabitsScreen(container) }
            composable(Destinations.MINE.route) {
                SettingsScreen(container, onOpenNotes = { navController.navigate("notes") })
            }
            composable("notes") { NotesScreen(container, onBack = { navController.popBackStack() }) }
        }

        // 悬浮 Dock：仅主 Tab 页面显示，底部居中
        if (currentRoute in mainRoutes) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
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