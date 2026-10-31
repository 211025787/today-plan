package com.focuslist.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份文件命名工具（纯 Java 标准库，便于单元测试）。
 */
object BackupNames {

    /** 备份文件名前缀 */
    private const val PREFIX = "今日规划-备份-"

    /** 备份文件扩展名 */
    const val EXTENSION = ".json"

    /**
     * 生成建议的备份文件名，形如「今日规划-备份-20261031-231500.json」。
     *
     * @param timeMillis 导出时刻（毫秒）
     * @return 文件名（含扩展名）
     */
    fun suggestFileName(timeMillis: Long): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date(timeMillis))
        return "$PREFIX$stamp$EXTENSION"
    }

    /**
     * 判断文件名是否像备份文件（仅看扩展名，避免用户选错文件）。
     *
     * @param name 文件名
     * @return 是 .json 结尾时为 true
     */
    fun looksLikeBackup(name: String): Boolean = name.endsWith(EXTENSION, ignoreCase = true)
}
