package com.pedroleite.opencomanda.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A product category (e.g. "Espetinhos", "Bebidas"), used to organize products for browsing and,
 * later, for Quick Sale. Deactivated instead of deleted ([active] = false) so products already
 * assigned to it — see [ProductEntity.categoryId] — keep that association instead of losing it.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "1")
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)
