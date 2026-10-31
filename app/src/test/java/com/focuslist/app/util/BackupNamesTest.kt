package com.focuslist.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份文件名工具的单元测试。
 */
class BackupNamesTest {

    @Test
    fun suggestFileName_hasPrefixTimestampAndJsonExtension() {
        val name = BackupNames.suggestFileName(0L)
        assertTrue(name.startsWith("今日规划-备份-"))
        assertTrue(name.endsWith(BackupNames.EXTENSION))
        // 形如 ...-19700101-080000.json：日期与时间各一段
        val stamp = name.removePrefix("今日规划-备份-").removeSuffix(BackupNames.EXTENSION)
        assertEquals(15, stamp.length)
        assertTrue(stamp[8] == '-')
    }

    @Test
    fun suggestFileName_differsByTime() {
        val a = BackupNames.suggestFileName(0L)
        val b = BackupNames.suggestFileName(1_000_000L)
        assertTrue(a != b)
    }

    @Test
    fun looksLikeBackup_onlyAcceptsJson() {
        assertTrue(BackupNames.looksLikeBackup("今日规划-备份-20261031-231500.json"))
        assertTrue(BackupNames.looksLikeBackup("backup.JSON"))
        assertFalse(BackupNames.looksLikeBackup("backup.txt"))
        assertFalse(BackupNames.looksLikeBackup(""))
    }
}
