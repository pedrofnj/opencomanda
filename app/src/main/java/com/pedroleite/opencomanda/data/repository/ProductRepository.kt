package com.pedroleite.opencomanda.data.repository

import com.pedroleite.opencomanda.data.local.dao.ProductDao
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val productDao: ProductDao) {

    fun getAll(): Flow<List<ProductEntity>> = productDao.getAll()

    fun getActive(): Flow<List<ProductEntity>> = productDao.getActive()

    suspend fun getById(id: Long): ProductEntity? = productDao.getById(id)

    suspend fun create(
        name: String,
        description: String?,
        priceCents: Long,
        costCents: Long?,
        trackStock: Boolean,
        initialStockQuantity: Double,
    ): Long {
        require(name.isNotBlank()) { "Product name must not be blank" }
        require(priceCents >= 0) { "Product price cannot be negative" }
        require(costCents == null || costCents >= 0) { "Product cost cannot be negative" }
        require(initialStockQuantity >= 0) { "Stock quantity cannot be negative" }
        val now = System.currentTimeMillis()
        return productDao.insert(
            ProductEntity(
                name = name.trim(),
                description = description?.trim()?.ifBlank { null },
                priceCents = priceCents,
                costCents = costCents,
                trackStock = trackStock,
                stockQuantity = if (trackStock) initialStockQuantity else 0.0,
                active = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun update(product: ProductEntity) {
        require(product.name.isNotBlank()) { "Product name must not be blank" }
        require(product.priceCents >= 0) { "Product price cannot be negative" }
        require(product.costCents == null || product.costCents >= 0) { "Product cost cannot be negative" }
        productDao.update(product.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Soft-disable: OpenComanda never hard-deletes products referenced by historical orders. */
    suspend fun setActive(id: Long, active: Boolean) {
        productDao.setActive(id, active, System.currentTimeMillis())
    }
}
