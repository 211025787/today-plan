package com.focuslist.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.focuslist.app.data.local.dao.CategoryDao
import com.focuslist.app.data.local.dao.FocusRecordDao
import com.focuslist.app.data.local.dao.HabitDao
import com.focuslist.app.data.local.dao.NoteDao
import com.focuslist.app.data.local.dao.PlanDao
import com.focuslist.app.data.local.dao.TagDao
import com.focuslist.app.data.local.dao.TaskDao
import com.focuslist.app.data.local.dao.TripDao
import com.focuslist.app.data.local.entity.CategoryEntity
import com.focuslist.app.data.local.entity.FocusRecordEntity
import com.focuslist.app.data.local.entity.HabitCheckEntity
import com.focuslist.app.data.local.entity.HabitEntity
import com.focuslist.app.data.local.entity.NoteAttachmentEntity
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.data.local.entity.PlanLogEntity
import com.focuslist.app.data.local.entity.QuickNoteEntity
import com.focuslist.app.data.local.entity.SubTaskEntity
import com.focuslist.app.data.local.entity.TagEntity
import com.focuslist.app.data.local.entity.TaskEntity
import com.focuslist.app.data.local.entity.TripEntity
import com.focuslist.app.data.local.entity.TripLegEntity
import com.focuslist.app.data.local.entity.TripStopEntity

/**
 * v3 → v4：备注增加标题与关联日期字段，新增备注附件表。
 * 手写迁移保留既有数据（不走向破坏性重建）。
 */
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE quick_notes ADD COLUMN title TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE quick_notes ADD COLUMN date INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note_attachments` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`noteId` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`uri` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_attachments_noteId` ON `note_attachments` (`noteId`)")
    }
}

/**
 * v4 → v5：新增旅游规划——行程表、行程站点表、站间交通表。
 * 手写建表保留既有数据；站点顺序与交通顺序都从 0 开始。
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trips` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`emoji` TEXT NOT NULL, " +
                "`colorHex` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trip_stops` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tripId` INTEGER NOT NULL, " +
                "`sortIndex` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`region` TEXT NOT NULL, " +
                "`lat` REAL NOT NULL, " +
                "`lon` REAL NOT NULL, " +
                "`stayMinutes` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_stops_tripId` ON `trip_stops` (`tripId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `trip_legs` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`tripId` INTEGER NOT NULL, " +
                "`sortIndex` INTEGER NOT NULL, " +
                "`mode` TEXT NOT NULL, " +
                "`durationMinutes` INTEGER NOT NULL, " +
                "`fromStation` TEXT NOT NULL, " +
                "`toStation` TEXT NOT NULL, " +
                "`serviceNo` TEXT NOT NULL, " +
                "`lines` TEXT NOT NULL, " +
                "`distanceMeters` INTEGER NOT NULL, " +
                "`polyline` TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trip_legs_tripId` ON `trip_legs` (`tripId`)")
    }
}

/**
 * v5 → v6：行程增加起止日期字段，用于在首页日历上显示旅游规划。
 * 手写 ALTER 保留既有数据，旧行程 startDate/endDate 默认 0（不显示到日历）。
 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trips ADD COLUMN startDate INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE trips ADD COLUMN endDate INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v6 → v7：计划增加卡片尺寸字段，用于日历网格中每个计划卡片的自定义大小。
 * 手写 ALTER 保留既有数据，旧计划 cardSize 默认 3（2×2）。
 */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plans ADD COLUMN cardSize INTEGER NOT NULL DEFAULT 3")
    }
}

/**
 * 应用本地数据库（Room）：业务数据全部保存在设备。
 *
 * 除了旅游规划的底图瓦片与真实路线按需联网外，应用不发起其它网络请求，
 * 行程 / 站点 / 交通（含缓存的路线折线）同样只写本机数据库。
 *
 * 版本历史：
 * - v1：任务/子任务/专注记录/习惯/打卡/清单
 * - v2：任务增加标签、软删除（垃圾桶）、含时刻截止时间；新增标签表
 * - v3：新增计划表（时间范围 + 每日目标时长）、计划当日累计表、快速备注表
 * - v4：备注增加标题、关联日期；新增备注附件表（图片/视频）
 * - v5：新增旅游规划表：行程、行程站点、站间交通（含真实路线折线）
 * - v6：行程增加起止日期，用于首页日历显示旅游规划
 * - v7：计划增加卡片尺寸，日历网格中每个计划可自定义 1×1 / 1×2 / 2×1 / 2×2
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
        QuickNoteEntity::class,
        NoteAttachmentEntity::class,
        TripEntity::class,
        TripStopEntity::class,
        TripLegEntity::class
    ],
    version = 7,
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

    /**
     * 获取行程 DAO。
     *
     * @return 行程数据访问对象
     */
    abstract fun tripDao(): TripDao

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
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}