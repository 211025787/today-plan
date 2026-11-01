package com.focuslist.app.data.backup

/**
 * 成就解锁记录与备份结构之间的转换与恢复策略（纯 Kotlin，无 Android 依赖）。
 *
 * 这里的规则决定了「换机 / 重装 / 误删数据」之后徽章还剩几枚：
 * - 导出：按解锁时间倒序排列，同刻按 id 升序，保证同样的记录永远产出同样的 JSON；
 * - 导入：空列表（schema 2 及更早的备份）一律**不动本机记录**，避免旧备份把徽章清空；
 * - 覆盖式：用备份里的记录整体替换本机；
 * - 合并式：只补本机没有的 id，本机已有的解锁日期保持不变。
 *
 * 解锁时间取「记录里最早的合法值」：同一 id 出现多次（手工编辑过的 JSON）时，
 * 保留第一次点亮的那一刻，而不是最后一次。
 */
object BackupAchievements {

    /**
     * 把本机解锁记录转成备份用的有序列表。
     *
     * @param record 本机记录 `成就 id -> 解锁时刻`
     * @return 只含合法项的列表，按解锁时间倒序、同刻按 id 升序
     */
    fun snapshot(record: Map<String, Long>): List<BackupAchievement> {
        return record.entries
            .filter { isValid(it.key, it.value) }
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .map { BackupAchievement(id = it.key, unlockedAt = it.value) }
    }

    /**
     * 把备份里的列表还原成记录表。
     *
     * 非法项（空 id / 时间非正）直接丢弃；同一 id 多次出现时取最早的解锁时间。
     * 未知 id 原样保留，便于高版本备份降级导入时仍不丢数据（界面按定义表过滤展示）。
     *
     * @param entries 备份中的成就列表
     * @return 解锁记录
     */
    fun toRecord(entries: List<BackupAchievement>): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        entries.forEach { entry ->
            if (!isValid(entry.id, entry.unlockedAt)) {
                return@forEach
            }
            val known = out[entry.id]
            out[entry.id] = if (known == null) entry.unlockedAt else minOf(known, entry.unlockedAt)
        }
        return out
    }

    /**
     * 按导入方式决定最终记录。
     *
     * @param local 本机现有记录
     * @param incoming 备份中的记录（已由 [toRecord] 规整）
     * @param overwrite true 为覆盖式导入
     * @return 应写入本机的记录
     */
    fun restore(
        local: Map<String, Long>,
        incoming: Map<String, Long>,
        overwrite: Boolean
    ): Map<String, Long> {
        // 旧版本备份没有成就字段：保留本机徽章，不做任何清空
        if (incoming.isEmpty()) {
            return local
        }
        if (overwrite) {
            return incoming
        }
        val out = LinkedHashMap(local)
        incoming.forEach { (id, at) ->
            if (!out.containsKey(id)) {
                out[id] = at
            }
        }
        return out
    }

    /**
     * 合并式导入能补充多少枚本机还没有的徽章。
     *
     * @param local 本机现有记录
     * @param incoming 备份中的记录
     * @return 新增条数
     */
    fun addedCount(local: Map<String, Long>, incoming: Map<String, Long>): Int {
        return incoming.keys.count { !local.containsKey(it) }
    }

    /**
     * 判断一条记录是否可用。
     *
     * @param id 成就 id
     * @param unlockedAt 解锁时刻
     * @return id 非空且时间大于 0 时返回 true
     */
    fun isValid(id: String, unlockedAt: Long): Boolean {
        return id.isNotBlank() && unlockedAt > 0L
    }

    /**
     * 成就数量文案，如「成就 5 枚」；空记录返回「暂无成就」。
     *
     * @param record 解锁记录
     * @return 文案
     */
    fun countText(record: Map<String, Long>): String {
        val count = record.count { isValid(it.key, it.value) }
        return if (count == 0) "暂无成就" else "成就 $count 枚"
    }
}
