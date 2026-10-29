package com.focuslist.app.data.repo

import com.focuslist.app.data.local.dao.PlanDao
import com.focuslist.app.data.local.entity.PlanEntity
import com.focuslist.app.data.local.entity.PlanLogEntity
import com.focuslist.app.util.DateUtils
import kotlinx.coroutines.flow.Flow

/**
 * 计划仓库：计划 CRUD 与当日累计时长的原子累加。
 *
 * @property planDao 计划 DAO
 */
class PlanRepository(private val planDao: PlanDao) {

    /**
     * 观察今天生效中的计划。
     *
     * @return 计划流
     */
    fun observeActiveToday(): Flow<List<PlanEntity>> {
        return planDao.observeActiveToday(DateUtils.startOfDay(System.currentTimeMillis()))
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

     * 按 ID 查询计划实体。
     *
     * @param id 计划 ID
     * @return 计划实体；不存在时为 null
     */
    suspend fun getPlan(id: Long): PlanEntity? {
        return planDao.getById(id)
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