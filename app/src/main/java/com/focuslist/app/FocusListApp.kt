package com.focuslist.app

import android.app.Application
import android.content.Context
import com.focuslist.app.data.local.AppDatabase
import com.focuslist.app.data.prefs.SettingsStore
import com.focuslist.app.data.repo.CategoryRepository
import com.focuslist.app.data.repo.FocusRepository
import com.focuslist.app.data.repo.HabitRepository
import com.focuslist.app.data.repo.NoteRepository
import com.focuslist.app.data.repo.PlanRepository
import com.focuslist.app.data.repo.TagRepository
import com.focuslist.app.data.repo.TaskRepository
import com.focuslist.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手动依赖注入容器：按需提供数据库、仓库与设置的单例。
 *
 * @property appContext 应用上下文
 */
class AppContainer(private val appContext: Context) {

    /** 数据库单例 */
    val database: AppDatabase by lazy { AppDatabase.get(appContext) }

    /** 任务仓库单例 */
    val taskRepository: TaskRepository by lazy { TaskRepository(database.taskDao()) }

    /** 专注记录仓库单例 */
    val focusRepository: FocusRepository by lazy { FocusRepository(database.focusRecordDao()) }

    /** 习惯仓库单例 */
    val habitRepository: HabitRepository by lazy { HabitRepository(database.habitDao()) }

    /** 清单仓库单例 */
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database.categoryDao()) }

    /** 标签仓库单例 */
    val tagRepository: TagRepository by lazy { TagRepository(database.tagDao()) }

    /** 计划仓库单例 */
    val planRepository: PlanRepository by lazy { PlanRepository(database.planDao()) }

    /** 快速备注仓库单例 */
    val noteRepository: NoteRepository by lazy { NoteRepository(database.noteDao()) }

    /** 设置存储单例 */
    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
}

/**
 * 应用入口：初始化通知渠道、依赖容器，并执行首启数据初始化。
 */
class FocusListApp : Application() {

    /** 全局依赖容器 */
    lateinit var container: AppContainer
        private set

    /** 应用级协程作用域：用于首启初始化等长生命周期任务 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // 提前创建通知渠道，保证 Android 8+ 提醒可送达
        Notifier(this).ensureChannels()
        // 首启写入内置清单
        appScope.launch {
            container.categoryRepository.seedIfEmpty()
        }
    }
}