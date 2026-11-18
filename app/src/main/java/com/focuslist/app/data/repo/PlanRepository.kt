package com.focuslist.app.data.repo

import com.focuslist.app.data.local.dao.PlanDao
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.data.local.entity.PlanLogEntity
import com.focuslist.app.util.DateUtils
import com.focuslist.app.util.PlanSchedule
import com.focuslist.app.util.DayRule
import com.focuslist.app.util.WeekdayMask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 计划仓库：计划 CRUD 与当日累计时长的原子累加。
 *
 * @property planDao 计划 DAO
 */
class PlanRepository(private val planDao: PlanDao) {

    /**
     * 观察今天生效中的计划（区间覆盖今天，且今天符合周几/工作日/休息日规则）。
     *
     * SQL 只筛区间，执行日规则交给 [PlanSchedule] 在内存里判——
     * 规则里含法定节假日，没法用一条 WHERE 表达。
     *
     * @return 计划流
     */
    fun observeActiveToday(): Flow<List<PlanEntity>> {
        val today = DateUtils.startOfDay(System.currentTimeMillis())
        return planDao.observeInRange(today, today)
            .map { plans -> plans.filter { PlanSchedule.isActive(it, today) } }
    }

    /**
     * 观察今天的全部累计记录。
     *
     * @return 当日记录流
     */
    fun observeTodayLogs(): Flow<List<PlanLogEntity>> {
        return planDao.observeLogsOfDay(DateUtils.startOfDay(System.currentTimeMillis()))
    }

    /**
     * 观察全部未归档计划（前端按天过滤生效范围）。
     *
     * @return 计划流
     */
    fun observeAllPlans(): Flow<List<PlanEntity>> {
        return planDao.observeAll()
    }

    /**
     * 观察生效范围与 [start, end] 相交的未归档计划（日历按月按需加载用）。
     *
     * @param start 区间起点 0 点（含）
     * @param end 区间终点 0 点（含）
     * @return 生效范围与区间相交的计划流
     */
    fun observePlansInRange(start: Long, end: Long): Flow<List<PlanEntity>> {
        return planDao.observeInRange(start, end)
    }

    /**
     * 观察日期区间内的累计记录（月历完成度聚合用）。
     *
     * @param start 区间起点 0 点（含）
     * @param end 区间终点 0 点（含）
     * @return 记录流
     */
    fun observeLogsBetween(start: Long, end: Long): Flow<List<PlanLogEntity>> {
        return planDao.observeLogsBetween(start, end)
    }

    /**
     * 按计划汇总区间内的累计投入秒数。
     *
     * @param start 区间起点 0 点（含）
     * @param end 区间终点 0 点（含）
     * @return `planId -> 累计秒数`；没有记录的计划不在结果里
     */
    suspend fun totalSecondsByPlan(start: Long, end: Long): Map<Long, Long> {
        return planDao.sumSecondsByPlan(start, end).associate { it.planId to it.seconds }
    }

    /**
     * 新增或更新计划。
     *
     * @param plan 计划实体；id 为 0 时插入
     * @return 计划 ID
     */
    suspend fun save(plan: PlanEntity): Long {
        return if (plan.id == 0L) {
            planDao.insert(plan)
        } else {
            planDao.update(plan)
            plan.id
        }
    }

    /**
     * 删除计划及其日志。
     *
     * @param id 计划 ID
     */
    suspend fun delete(id: Long) {
        planDao.deleteLogsOf(id)
        planDao.deleteById(id)
    }

    /**
     * 更新计划卡片尺寸（日历网格中占用的列数与行数编码）。
     *
     * @param planId 计划 ID
     * @param cardSize 卡片尺寸编码：0=1×1, 1=1×2, 2=2×1, 3=2×2
     */
    suspend fun updateCardSize(planId: Long, cardSize: Int) {
        planDao.updateCardSize(planId, cardSize)
    }

    /**
     * 更新计划卡片的自由画布位置与大小。
     *
     * @param planId 计划 ID
     * @param cols 占列数（1-4）
     * @param rows 占行数（1-4）
     * @param x 左上角列（0 起）
     * @param y 左上角行（0 起）
     * @param z 叠放时间戳；越大越靠上
     */
    suspend fun updateCardLayout(planId: Long, cols: Int, rows: Int, x: Int, y: Int, z: Long) {
        planDao.updateCardLayout(planId, cols, rows, x, y, z)
    }

    /**
     * 仅在指定日期范围内移除计划，范围外的日期保留。
     *
     * 按覆盖情况处理：
     * - 范围完全覆盖计划有效期 → 整体删除计划；
     * - 仅覆盖头部 → 起始日后移到 rangeEnd 次日；
     * - 仅覆盖尾部 → 截止日前移到 rangeStart 前一日；
     * - 覆盖中间 → 计划拆成两段，前后两截各自保留。
     * 同时删除范围内已产生的计时记录；与计划有效期无交集时不做任何操作。
     *
     * @param planId 计划 ID
     * @param rangeStart 删除区间起点 0 点（含）
     * @param rangeEnd 删除区间终点 0 点（含）
     * @return true 表示计划被整体删除；false 表示计划仍存在（可能被截短/拆分或未受影响）
     */
    suspend fun deleteInRange(planId: Long, rangeStart: Long, rangeEnd: Long): Boolean {
        val plan = planDao.getById(planId) ?: return true
        // 无交集：不动
        if (plan.endDate < rangeStart || plan.startDate > rangeEnd) {
            return false
        }
        // 删除范围内已产生的计时记录（交集部分）
        planDao.deleteLogsBetween(planId, maxOf(plan.startDate, rangeStart), minOf(plan.endDate, rangeEnd))
        return when {
            // 范围完全覆盖计划：整体删除
            rangeStart <= plan.startDate && rangeEnd >= plan.endDate -> {
                planDao.deleteById(planId)
                true
            }
            // 只覆盖头部：起始日后移
            rangeStart <= plan.startDate -> {
                planDao.update(plan.copy(startDate = DateUtils.addDays(rangeEnd, 1)))
                false
            }
            // 只覆盖尾部：截止日前移
            rangeEnd >= plan.endDate -> {
                planDao.update(plan.copy(endDate = DateUtils.addDays(rangeStart, -1)))
                false
            }
            // 覆盖中间：原计划保留前段，新建副本承接后段
            else -> {
                planDao.update(plan.copy(endDate = DateUtils.addDays(rangeStart, -1)))
                planDao.insert(
                    plan.copy(
                        id = 0,
                        startDate = DateUtils.addDays(rangeEnd, 1),
                        createdAt = System.currentTimeMillis()
                    )
                )
                false
            }
        }
    }

    /**
     * 按「只删哪些天」从计划里移除部分日期。
     *
     * 与 [deleteInRange] 的区别：区间内不是整段删除，而是只删符合 [selMask] / [selRule]
     * 的那些执行日，其余日期继续保留。实现分两种情况：
     * - 范围覆盖整个计划、且两边都是「按周几」规则：直接从掩码里减掉选中的周几，
     *   一条计划搞定，不拆不复制；
     * - 其他情况：把保留下来的执行日按连续日期切成若干段，每段一条计划，
     *   并把各段已有的计时记录改挂到对应计划上。
     *
     * @param planId 计划 ID
     * @param rangeStart 删除区间起点 0 点（含）
     * @param rangeEnd 删除区间终点 0 点（含）
     * @param selMask 要删除的周几掩码（bit0=周一）
     * @param selRule 要删除的日期类型，见 [DayRule]
     * @return true 表示计划被整体删除
     */
    suspend fun deleteInRangeByDays(
        planId: Long,
        rangeStart: Long,
        rangeEnd: Long,
        selMask: Int,
        selRule: Int
    ): Boolean {
        val plan = planDao.getById(planId) ?: return true
        val from = maxOf(plan.startDate, rangeStart)
        val to = minOf(plan.endDate, rangeEnd)
        if (from > to) {
            return false
        }
        val deleteDays = PlanSchedule.activeDays(from, to, selMask, selRule, 2000)
            .filter { PlanSchedule.isActive(plan, it) }
            .toSet()
        if (deleteDays.isEmpty()) {
            return false
        }
        val allActive = PlanSchedule.activeDays(plan, 4000)
        val kept = allActive.filter { it !in deleteDays }
        if (kept.isEmpty()) {
            planDao.deleteLogsOf(planId)
            planDao.deleteById(planId)
            return true
        }
        // 覆盖整个计划且都是按周几的规则：改掩码即可，避免拆成很多条计划
        val fullCover = rangeStart <= plan.startDate && rangeEnd >= plan.endDate
        if (fullCover && selRule == DayRule.EVERY_DAY && plan.dayRule == DayRule.EVERY_DAY) {
            val all = if (plan.weekdaysMask == WeekdayMask.NONE) WeekdayMask.ALL else plan.weekdaysMask
            val remain = all and selMask.inv() and WeekdayMask.ALL
            if (remain == WeekdayMask.NONE) {
                planDao.deleteLogsOf(planId)
                planDao.deleteById(planId)
                return true
            }
            deleteDays.forEach { day -> planDao.deleteLogAt(planId, day) }
            planDao.update(plan.copy(weekdaysMask = remain))
            return false
        }
        deleteDays.forEach { day -> planDao.deleteLogAt(planId, day) }
        val runs = groupConsecutiveDays(kept)
        val first = runs.first()
        planDao.update(plan.copy(startDate = first.first, endDate = first.second))
        runs.drop(1).forEach { run ->
            val newId = planDao.insert(
                plan.copy(
                    id = 0,
                    startDate = run.first,
                    endDate = run.second,
                    createdAt = System.currentTimeMillis()
                )
            )
            planDao.reassignLogs(planId, newId, run.first, run.second)
        }
        return false
    }

    /**
     * 把升序日期按「连续天」分组。
     *
     * @param days 升序的 0 点时间戳
     * @return 每段的起止日期
     */
    private fun groupConsecutiveDays(days: List<Long>): List<Pair<Long, Long>> {
        if (days.isEmpty()) {
            return emptyList()
        }
        val runs = ArrayList<Pair<Long, Long>>()
        var start = days.first()
        var prev = start
        days.drop(1).forEach { day ->
            if (day - prev > DateUtils.DAY_MILLIS) {
                runs += start to prev
                start = day
            }
            prev = day
        }
        runs += start to prev
        return runs
    }


    /**
     * 按 ID 查询计划实体。
     *
     * @param id 计划 ID
     * @return 计划实体；不存在时为 null
     */
    suspend fun getPlan(id: Long): PlanEntity? {
        return planDao.getById(id)
    }

    /**
     * 把标题为 [oldTitle] 的计划统一改名为 [newTitle]（仅命中时生效，可重复调用）。
     *
     * @param oldTitle 旧标题
     * @param newTitle 新标题
     * @return 受影响行数
     */
    suspend fun renamePlans(oldTitle: String, newTitle: String): Int {
        return planDao.renameByTitle(oldTitle, newTitle)
    }

    /**
     * 查询计划在日期区间内的逐日累计记录（计划详情 / 连续达标用）。
     *
     * @param planId 计划 ID
     * @param start 区间起点 0 点（含）
     * @param end 区间终点 0 点（含）
     * @return 按日期升序的记录
     */
    suspend fun logsOfPlan(planId: Long, start: Long, end: Long): List<PlanLogEntity> {
        return planDao.getLogsOfPlanBetween(planId, start, end)
    }

    /**
     * 查询计划当天已落库的累计秒数（不含未落库的运行段）。
     *
     * @param planId 计划 ID
     * @return 已落库秒数；无记录返回 0
     */
    suspend fun getTodaySeconds(planId: Long): Long {
        val today = DateUtils.startOfDay(System.currentTimeMillis())
        return planDao.getLog(planId, today)?.accumulatedSec ?: 0L
    }

    /**
     * 给计划当天累计追加秒数（不存在记录则先建行，保证多次开始可累加）。
     *
     * @param planId 计划 ID
     * @param deltaSec 本次追加秒数；小于等于 0 时忽略
     */
    suspend fun addSessionSeconds(planId: Long, deltaSec: Long) {
        if (deltaSec <= 0) {
            return
        }
        val today = DateUtils.startOfDay(System.currentTimeMillis())
        val now = System.currentTimeMillis()
        // 先确保当天行存在，再原子累加
        if (planDao.getLog(planId, today) == null) {
            planDao.insertLog(PlanLogEntity(planId = planId, date = today, accumulatedSec = 0, updatedAt = now))
        }
        planDao.addSeconds(planId, today, deltaSec, now)
    }

    /**
     * 手动设置计划当天的累计秒数（补录场景：覆盖原值而非追加）。
     *
     * @param planId 计划 ID
     * @param sec 新的累计秒数；小于 0 时按 0 处理
     */
    suspend fun setTodaySeconds(planId: Long, sec: Long) {
        val safeSec = if (sec < 0) 0L else sec
        val today = DateUtils.startOfDay(System.currentTimeMillis())
        val now = System.currentTimeMillis()
        // 当天行不存在时直接以目标值建行，存在则覆盖
        if (planDao.getLog(planId, today) == null) {
            planDao.insertLog(PlanLogEntity(planId = planId, date = today, accumulatedSec = safeSec, updatedAt = now))
        } else {
            planDao.setSeconds(planId, today, safeSec, now)
        }
    }
}
