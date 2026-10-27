package com.focuslist.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.focuslist.app.data.local.dao.CategoryDao
import com.focuslist.app.data.local.dao.FocusRecordDao
import com.focuslist.app.data.local.dao.HabitDao
import com.focuslist.app.data.local.dao.NoteDao
import com.focuslist.app.data.local.dao.PlanDao
import com.focuslist.app.data.local.dao.TagDao
import com.focuslist.app.data.local.dao.TaskDao
import com.focuslist.app.data.local.entity.CategoryEntity
import com.focuslist.app.data.local.entity.FocusRecordEntity
import com.focuslist.app.data.local.entity.HabitCheckEntity
import com.focuslist.app.data.local.entity.HabitEntity
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.data.local.entity.PlanLogEntity
import com.focuslist.app.data.local.entity.QuickNoteEntity
import com.focuslist.app.data.local.entity.SubTaskEntity
import com.focuslist.app.data.local.entity.TagEntity
import com.focuslist.app.data.local.entity.TaskEntity

/**
 * 应用本地数据库（Room）：全部数据离线存储于设备，无任何网络依赖。
 *
 * 版本历史：
 * - v1：任务/子任务/专注记录/习惯/打卡/清单
 * - v2：任务增加标签、软删除（垃圾桶）、含时刻截止时间；新增标签表
 * - v3：新增计划表（时间范围 + 每日目标时长）、计划当日累计表、快速备注表
 */
@Database(
    entities = [
        TaskEntity::class,
        SubTaskEntity::class,
        FocusRecordEntity::class,
        HabitEntity::class,
        HabitCheckEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        PlanEntity::class,
        PlanLogEntity::class,
        QuickNoteEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /**
     * 获取任务 DAO。
     *
     * @return 任务数据访问对象
     */
    abstract fun taskDao(): TaskDao

    /**
     * 获取专注记录 DAO。
     *
     * @return 专注记录数据访问对象
     */
    abstract fun focusRecordDao(): FocusRecordDao

    /**
     * 获取习惯 DAO。
     *
     * @return 习惯数据访问对象
     */
    abstract fun habitDao(): HabitDao

    /**
     * 获取清单 DAO。
     *
     * @return 清单数据访问对象
     */
    abstract fun categoryDao(): CategoryDao

    /**
     * 获取标签 DAO。
     *
     * @return 标签数据访问对象
     */
    abstract fun tagDao(): TagDao

    /**
     * 获取计划 DAO。
     *
     * @return 计划数据访问对象
     */
    abstract fun planDao(): PlanDao

    /**
     * 获取快速备注 DAO。
     *
     * @return 备注数据访问对象
     */
    abstract fun noteDao(): NoteDao

    companion object {
        /** 数据库文件名 */
        private const val DB_NAME = "focus_list.db"

        @Volatile
        private var instance: AppDatabase? = null

        /**
         * 获取数据库单例（双重检查锁）。
         *
         * @param context 上下文
         * @return 数据库实例
         */
        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                // 结构变更走破坏性重建（离线单机数据，开发阶段可接受）
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}