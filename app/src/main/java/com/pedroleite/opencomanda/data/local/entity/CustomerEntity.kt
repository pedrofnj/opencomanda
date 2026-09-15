package com.pedroleite.opencomanda.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A customer. Deactivated instead of deleted ([active] = false) so historical orders/debts keep working. */
@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String? = null,
    val notes: String? = null,
    @ColumnInfo(defaultValue = "1")
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)
