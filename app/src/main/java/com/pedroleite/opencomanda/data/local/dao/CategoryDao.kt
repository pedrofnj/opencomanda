package com.pedroleite.opencomanda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pedroleite.opencomanda.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE active = 1 ORDER BY name ASC")
    fun getActive(): Flow<List<CategoryEntity>>

    /** One-shot snapshot of active categories, used to check for duplicate names on save. */
    @Query("SELECT * FROM categories WHERE active = 1")
    suspend fun getActiveSnapshot(): List<CategoryEntity>

    @Query("UPDATE categories SET active = :active, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean, updatedAt: Long)
}
