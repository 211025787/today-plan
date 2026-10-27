package com.focuslist.app.data.repo

import com.focuslist.app.data.local.dao.CategoryDao
import com.focuslist.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 清单（分类）仓库：清单管理与应用首启的内置数据初始化。
 *
 * @property categoryDao 清单 DAO
 */
class CategoryRepository(private val categoryDao: CategoryDao) {

    /**
     * 观察全部清单。
     *
     * @return 清单流
     */
    fun observeAll(): Flow<List<CategoryEntity>> {
        return categoryDao.observeAll()
    }

    /**
     * 新增或更新清单。
     *
     * @param category 清单实体；id 为 0 时插入
     * @return 清单 ID
     */
    suspend fun save(category: CategoryEntity): Long {
        return if (category.id == 0L) {
            categoryDao.insert(category)
        } else {
            categoryDao.update(category)
            category.id
        }
    }

    /**
     * 删除清单并将其任务移回收集箱。
     *
     * @param id 清单 ID
     */
    suspend fun delete(id: Long) {
        categoryDao.resetTasksOf(id)
        categoryDao.deleteById(id)
    }

    /**
     * 首启时写入内置清单（仅当表为空）。
     */
    suspend fun seedIfEmpty() {
        if (categoryDao.count() == 0) {
            categoryDao.insert(CategoryEntity(name = "工作", colorHex = "#E94B3C", sortOrder = 1))
            categoryDao.insert(CategoryEntity(name = "学习", colorHex = "#1E88E5", sortOrder = 2))
            categoryDao.insert(CategoryEntity(name = "生活", colorHex = "#43A047", sortOrder = 3))
        }
    }
}