package com.pedroleite.opencomanda.data.repository

import com.pedroleite.opencomanda.data.local.dao.CategoryDao
import com.pedroleite.opencomanda.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/** Thrown when a category name collides with another *active* category's name (compared
 *  trimmed and case-insensitively) — a distinct type so callers can show a specific message. */
class DuplicateCategoryNameException(message: String) : IllegalArgumentException(message)

class CategoryRepository(private val categoryDao: CategoryDao) {

    fun getAll(): Flow<List<CategoryEntity>> = categoryDao.getAll()

    fun getActive(): Flow<List<CategoryEntity>> = categoryDao.getActive()

    suspend fun getById(id: Long): CategoryEntity? = categoryDao.getById(id)

    suspend fun create(name: String): Long {
        require(name.isNotBlank()) { "Category name must not be blank" }
        val trimmedName = name.trim()
        checkNoActiveDuplicate(trimmedName, excludeId = null)
        val now = System.currentTimeMillis()
        return categoryDao.insert(
            CategoryEntity(name = trimmedName, active = true, createdAt = now, updatedAt = now),
        )
    }

    suspend fun rename(id: Long, name: String) {
        require(name.isNotBlank()) { "Category name must not be blank" }
        val existing = categoryDao.getById(id) ?: return
        val trimmedName = name.trim()
        checkNoActiveDuplicate(trimmedName, excludeId = id)
        categoryDao.update(existing.copy(name = trimmedName, updatedAt = System.currentTimeMillis()))
    }

    /** Soft-disable: categories are never physically deleted, and deactivating one never touches
     *  the products already assigned to it — see [ProductRepository]. */
    suspend fun setActive(id: Long, active: Boolean) {
        categoryDao.setActive(id, active, System.currentTimeMillis())
    }

    private suspend fun checkNoActiveDuplicate(name: String, excludeId: Long?) {
        val normalized = normalize(name)
        val duplicate = categoryDao.getActiveSnapshot().any { it.id != excludeId && normalize(it.name) == normalized }
        if (duplicate) throw DuplicateCategoryNameException("An active category named \"$name\" already exists")
    }

    private fun normalize(name: String) = name.trim().lowercase()
}
