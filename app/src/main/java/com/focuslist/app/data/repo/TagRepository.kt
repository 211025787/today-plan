package com.focuslist.app.data.repo

import com.focuslist.app.data.local.dao.TagDao
import com.focuslist.app.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/**
 * 标签仓库：标签的增删查。
 *
 * @property tagDao 标签 DAO
 */
class TagRepository(private val tagDao: TagDao) {

    /**
     * 观察全部标签。
     *
     * @return 标签流
     */
    fun observeAll(): Flow<List<TagEntity>> {
        return tagDao.observeAll()
    }

    /**
     * 新建标签（重名自动忽略）。
     *
     * @param name 标签名
     * @param colorHex 标签颜色
     */
    suspend fun add(name: String, colorHex: String = "#4772FA") {
        if (name.isNotBlank()) {
            tagDao.insert(TagEntity(name = name.trim(), colorHex = colorHex))
        }
    }

    /**
     * 删除标签。
     *
     * @param id 标签 ID
     */
    suspend fun delete(id: Long) {
        tagDao.deleteById(id)
    }
}