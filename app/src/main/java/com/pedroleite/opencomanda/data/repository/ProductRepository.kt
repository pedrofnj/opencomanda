package com.pedroleite.opencomanda.data.repository

import androidx.room.withTransaction
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.dao.ProductDao
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.domain.OrderStatus
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode

/** Why a manual stock operation was refused — mapped to a localized message by the UI. */
enum class StockAdjustmentError { PRODUCT_NOT_FOUND, NOT_TRACKED, INVALID_QUANTITY, ZERO_ADJUSTMENT, NEGATIVE_RESULT }

class StockAdjustmentException(val error: StockAdjustmentError, message: String) : RuntimeException(message)

/** The outcome of a manual stock operation, as recalculated from the database's own fresh state —
 *  never echoed back from whatever the UI last displayed. */
data class StockChange(
    val productId: Long,
    val productName: String,
    val previousQuantity: Double,
    val newQuantity: Double,
)

/** Stock settings applied together with a product edit, only when the operator actually changed them. */
data class StockConfig(val trackStock: Boolean, val quantity: Double)

/** Thrown when stock control would be switched on or off for a product that is on an open Comanda:
 *  whether that Comanda's items reserved stock depends on the setting at the time they were added,
 *  so flipping it now would make cancelling or editing the Comanda restore stock that was never
 *  taken (or skip stock that was). */
class StockTrackingLockedException(val productId: Long) :
    RuntimeException("Stock control for product $productId cannot change while it is on an open comanda")

class ProductRepository(
    private val database: AppDatabase,
    private val productDao: ProductDao,
) {

    fun getAll(): Flow<List<ProductEntity>> = productDao.getAll()

    fun getActive(): Flow<List<ProductEntity>> = productDao.getActive()

    /** Products with stock control on (active or not) — the operational Stock screen's list. */
    fun getTrackedStock(): Flow<List<ProductEntity>> = productDao.getTrackedStock()

    suspend fun getById(id: Long): ProductEntity? = productDao.getById(id)

    suspend fun create(
        name: String,
        description: String?,
        priceCents: Long,
        costCents: Long?,
        trackStock: Boolean,
        initialStockQuantity: Double,
        categoryId: Long? = null,
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
                categoryId = categoryId,
            ),
        )
    }

    /**
     * Saves an edit of [product]'s descriptive fields (name, description, price, cost, category).
     *
     * The edit form works from a snapshot taken when it opened, so this deliberately never writes
     * the snapshot's stock or active flag back: it reloads the row inside a transaction and keeps
     * the database's own current `stockQuantity`, `trackStock` and `active`. Otherwise saving a
     * new price would silently undo every sale made while the form was open.
     *
     * Stock is only changed when [stockConfig] is given — i.e. the operator intentionally edited
     * the stock section — and then in the same transaction as the rest of the edit. Switching stock
     * control on or off is refused while the product is on an OPEN Comanda (see
     * [StockTrackingLockedException]); changing the counted quantity of an already tracked
     * product is always allowed.
     *
     * @throws StockTrackingLockedException if [stockConfig] would flip stock control for a product
     * that is on an open Comanda.
     */
    suspend fun update(product: ProductEntity, stockConfig: StockConfig? = null) {
        require(product.name.isNotBlank()) { "Product name must not be blank" }
        require(product.priceCents >= 0) { "Product price cannot be negative" }
        require(product.costCents == null || product.costCents >= 0) { "Product cost cannot be negative" }
        if (stockConfig != null) {
            require(stockConfig.quantity.isFinite() && stockConfig.quantity >= 0) { "Stock quantity cannot be negative" }
        }
        database.withTransaction {
            val current = productDao.getById(product.id) ?: error("Product ${product.id} not found")
            if (stockConfig != null && stockConfig.trackStock != current.trackStock &&
                productDao.countOrderItemsInOrdersWithStatus(current.id, OrderStatus.OPEN) > 0
            ) {
                throw StockTrackingLockedException(current.id)
            }
            val edited = current.copy(
                name = product.name,
                description = product.description,
                priceCents = product.priceCents,
                costCents = product.costCents,
                categoryId = product.categoryId,
                updatedAt = System.currentTimeMillis(),
            )
            productDao.update(
                if (stockConfig == null) {
                    edited
                } else {
                    edited.copy(
                        trackStock = stockConfig.trackStock,
                        stockQuantity = if (stockConfig.trackStock) roundQuantity(stockConfig.quantity) else 0.0,
                    )
                },
            )
        }
    }

    /** Soft-disable: OpenComanda never hard-deletes products referenced by historical orders. */
    suspend fun setActive(id: Long, active: Boolean) {
        productDao.setActive(id, active, System.currentTimeMillis())
    }

    /**
     * Adds [delta] to (or, if negative, removes from) a tracked product's stock. The new total is
     * computed here, inside the transaction, from the row as it is right now — the caller supplies
     * only the amount to change by, so a stale figure on screen can never overwrite sales made in
     * the meantime (stock shown 10, a sale takes it to 8, +5 gives 13 — not 15).
     *
     * Inactive products can still be corrected. Only the stock figure is written: never name,
     * price, category or the active flag, and never any order, payment or cash movement.
     *
     * @throws StockAdjustmentException if the product is missing or untracked, [delta] is zero or
     * not a finite number, or the result would be negative.
     */
    suspend fun adjustStock(productId: Long, delta: Double): StockChange {
        if (!delta.isFinite()) throw StockAdjustmentException(StockAdjustmentError.INVALID_QUANTITY, "Adjustment must be a finite number")
        if (delta == 0.0) throw StockAdjustmentException(StockAdjustmentError.ZERO_ADJUSTMENT, "Adjustment must not be zero")
        return database.withTransaction {
            val product = trackedProduct(productId)
            val newQuantity = roundQuantity(product.stockQuantity + delta)
            if (newQuantity < 0.0) {
                throw StockAdjustmentException(
                    StockAdjustmentError.NEGATIVE_RESULT,
                    "Stock for product $productId would become negative: ${product.stockQuantity} + $delta",
                )
            }
            writeStock(product, newQuantity)
        }
    }

    /** Sets a tracked product's stock to exactly [quantity] — for a physical count. Zero is valid. */
    suspend fun setStock(productId: Long, quantity: Double): StockChange {
        if (!quantity.isFinite() || quantity < 0.0) {
            throw StockAdjustmentException(StockAdjustmentError.INVALID_QUANTITY, "Stock must be a non-negative number")
        }
        return database.withTransaction { writeStock(trackedProduct(productId), roundQuantity(quantity)) }
    }

    private suspend fun trackedProduct(productId: Long): ProductEntity {
        val product = productDao.getById(productId)
            ?: throw StockAdjustmentException(StockAdjustmentError.PRODUCT_NOT_FOUND, "Product $productId not found")
        if (!product.trackStock) {
            throw StockAdjustmentException(StockAdjustmentError.NOT_TRACKED, "Product $productId does not track stock")
        }
        return product
    }

    private suspend fun writeStock(product: ProductEntity, newQuantity: Double): StockChange {
        productDao.setStockQuantity(product.id, newQuantity, System.currentTimeMillis())
        return StockChange(product.id, product.name, product.stockQuantity, newQuantity)
    }

    /** Stock is a [Double] so it can be fractional; rounding to 6 places keeps binary artifacts
     *  (0.1 + 0.2 = 0.30000000000000004) from accumulating or making an exact removal look negative. */
    private fun roundQuantity(value: Double): Double =
        BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).toDouble()
}
