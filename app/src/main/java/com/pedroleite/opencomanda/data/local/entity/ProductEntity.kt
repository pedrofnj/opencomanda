package com.pedroleite.opencomanda.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A sellable product/item. Deactivated instead of deleted ([active] = false) so historical
 * orders that reference it keep working — see [OrderItemEntity], which additionally snapshots
 * the name/price so future edits here never alter past sales.
 */
@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            // A category is only ever soft-disabled, never physically deleted, by the app itself
            // — SET_NULL is a defensive fallback, not a path the app exercises, so a product can
            // never be dragged down (or its data corrupted) by whatever happens to its category.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId")],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val priceCents: Long,
    val costCents: Long? = null,
    /**
     * Stock quantity as a [Double], not an [Int]: this lets a product's stock later be tracked
     * in fractional units (e.g. kilograms of meat) without a schema migration. No fractional-unit
     * UX (unit-of-measure selection/conversion) is implemented yet — only whole-number entry is
     * exposed in this phase.
     */
    @ColumnInfo(defaultValue = "0")
    val stockQuantity: Double = 0.0,
    /** Stock control is opt-in per product; most quick-service items won't track stock. */
    @ColumnInfo(defaultValue = "0")
    val trackStock: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    /** Optional: a product may exist without a category, and keeps its category even if that
     *  category is later deactivated (see [CategoryEntity.active]). */
    val categoryId: Long? = null,
)
