package com.focuslist.app.data.repo

import com.focuslist.app.data.local.dao.TaskDao
import com.focuslist.app.data.local.entity.SubTaskEntity
import com.focuslist.app.data.local.entity.TaskEntity
import com.focuslist.app.util.DateUtils
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * 任务仓库：封装任务/子任务的业务规则，包括智能清单、重复任务周期生成与垃圾桶。
 *
 * @property taskDao 任务 DAO
 */
class TaskRepository(private val taskDao: TaskDao) {

    /**
     * 观察某清单下的任务。
     *
     * @param categoryId 清单 ID；null 表示收集箱
     * @return 任务流
     */
    fun observeByCategory(categoryId: Long?): Flow<List<TaskEntity>> {
        return taskDao.observeByCategory(categoryId)
    }

    /**
     * 观察"今天"智能清单。
     *
     * @return 今日任务流
     */
    fun observeToday(): Flow<List<TaskEntity>> {
        return taskDao.observeToday(DateUtils.endOfDay(System.currentTimeMillis()))
    }

    /**
     * 观察"明天"智能清单。
     *
     * @return 明日任务流
     */
    fun observeTomorrow(): Flow<List<TaskEntity>> {
        val start = DateUtils.startOfDay(System.currentTimeMillis()) + DateUtils.DAY_MILLIS
        return taskDao.observeTomorrow(start, start + DateUtils.DAY_MILLIS - 1)
    }

    /**
     * 观察"最近 7 天"智能清单。
     *
     * @return 任务流
     */
    fun observeNext7Days(): Flow<List<TaskEntity>> {
        val end = DateUtils.endOfDay(System.currentTimeMillis()) + 6 * DateUtils.DAY_MILLIS
        return taskDao.observeNext7Days(end)
    }

    /**
     * 观察所有未完成任务。
     *
     * @return 未完成任务流
     */
    fun observePending(): Flow<List<TaskEntity>> {
        return taskDao.observePending()
    }

    /**
     * 观察已完成任务。
     *
     * @return 已完成任务流
     */
    fun observeCompleted(): Flow<List<TaskEntity>> {
        return taskDao.observeCompleted()
    }

    /**
     * 观察垃圾桶。
     *
     * @return 已删除任务流
     */
    fun observeTrash(): Flow<List<TaskEntity>> {
        return taskDao.observeTrash()
    }

    /**
     * 观察某标签下的未完成任务。
     *
     * @param tagName 标签名
     * @return 任务流
     */
    fun observeByTag(tagName: String): Flow<List<TaskEntity>> {
        return taskDao.observeByTag("%,${tagName.trim()},%")
    }

    /**
     * 观察日历区间任务。
     *
     * @param start 区间起点（含）
     * @param end 区间终点（含）
     * @return 任务流
     */
    fun observeDueBetween(start: Long, end: Long): Flow<List<TaskEntity>> {
        return taskDao.observeDueBetween(start, end)
    }

    /**
     * 搜索任务。
     *
     * @param keyword 关键字
     * @return 匹配任务流
     */
    fun search(keyword: String): Flow<List<TaskEntity>> {
        return taskDao.search("%${keyword.trim()}%")
    }

    /**
     * 新增或更新任务。
     *
     * @param task 任务实体；id 为 0 时执行插入
     * @return 任务 ID
     */
    suspend fun save(task: TaskEntity): Long {
        return if (task.id == 0L) {
            taskDao.insert(task.copy(updatedAt = System.currentTimeMillis()))
        } else {
            taskDao.update(task.copy(updatedAt = System.currentTimeMillis()))
            task.id
        }
    }

    /**
     * 移入垃圾桶（软删除）。
     *
     * @param id 任务 ID
     */
    suspend fun moveToTrash(id: Long) {
        taskDao.setDeleted(id, System.currentTimeMillis())
    }

    /**
     * 从垃圾桶恢复。
     *
     * @param id 任务 ID
     */
    suspend fun restoreFromTrash(id: Long) {
        taskDao.setDeleted(id, null)
    }

    /**
     * 彻底删除（含子任务）。
     *
     * @param id 任务 ID
     */
    suspend fun deleteForever(id: Long) {
        taskDao.deleteSubTasksOf(id)
        taskDao.deleteById(id)
    }

    /**
     * 清空垃圾桶。
     */
    suspend fun emptyTrash() {
        taskDao.emptyTrash()
    }

    /**
     * 切换完成状态；若任务带重复规则且被标记完成，则自动生成下一周期副本。
     *
     * @param task 目标任务
     * @param done 是否完成
     */
    suspend fun setDone(task: TaskEntity, done: Boolean) {
        val now = System.currentTimeMillis()
        taskDao.setDone(task.id, done, if (done) now else null, now)
        if (done && task.repeatRule != "NONE") {
            spawnNextOccurrence(task)
        }
    }

    /**
     * 根据重复规则生成下一个周期任务副本。
     *
     * @param task 已完成的原任务
     */
    private suspend fun spawnNextOccurrence(task: TaskEntity) {
        val base = task.dueDate ?: System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = base
        when (task.repeatRule) {
            "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
            "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            "MONTHLY" -> cal.add(Calendar.MONTH, 1)
        }
        val next = task.copy(
            id = 0,
            isDone = false,
            doneAt = null,
            dueDate = cal.timeInMillis,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        taskDao.insert(next)
    }

    /**
     * 任务番茄累计数加一（番茄钟完整结束时调用）。
     *
     * @param taskId 任务 ID
     */
    suspend fun incPomo(taskId: Long) {
        taskDao.incCompletedPomo(taskId)
    }

    /**
     * 观察任务的子任务列表。
     *
     * @param taskId 任务 ID
     * @return 子任务流
     */
    fun observeSubTasks(taskId: Long): Flow<List<SubTaskEntity>> {
        return taskDao.observeSubTasks(taskId)
    }

    /**
     * 新增子任务。
     *
     * @param taskId 任务 ID
     * @param title 子任务标题
     */
    suspend fun addSubTask(taskId: Long, title: String) {
        taskDao.insertSubTask(SubTaskEntity(taskId = taskId, title = title.trim()))
    }

    /**
     * 切换子任务完成状态。
     *
     * @param subTask 子任务实体
     * @param done 是否完成
     */
    suspend fun setSubTaskDone(subTask: SubTaskEntity, done: Boolean) {
        taskDao.setSubTaskDone(subTask.id, done)
    }

    /**
     * 删除子任务。
     *
     * @param subTaskId 子任务 ID
     */
    suspend fun deleteSubTask(subTaskId: Long) {
        taskDao.deleteSubTask(subTaskId)
    }
}