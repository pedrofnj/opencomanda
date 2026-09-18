package com.pedroleite.opencomanda.data.repository

import androidx.room.withTransaction
import com.pedroleite.opencomanda.core.Money
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.dao.DebtDao
import com.pedroleite.opencomanda.data.local.dao.OrderDao
import com.pedroleite.opencomanda.data.local.dao.OrderItemDao
import com.pedroleite.opencomanda.data.local.dao.PaymentDao
import com.pedroleite.opencomanda.data.local.dao.ProductDao
import com.pedroleite.opencomanda.data.local.entity.DebtEntity
import com.pedroleite.opencomanda.data.local.entity.OrderEntity
import com.pedroleite.opencomanda.data.local.entity.OrderItemEntity
import com.pedroleite.opencomanda.data.local.entity.PaymentEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.domain.DebtStatus
import com.pedroleite.opencomanda.domain.OrderStatus
import com.pedroleite.opencomanda.domain.OrderTotalCalculator
import com.pedroleite.opencomanda.domain.OrderType
import com.pedroleite.opencomanda.domain.PaymentMethod
import kotlinx.coroutines.flow.Flow

/** A single requested line when confirming a Quick Sale. Deliberately just an id + quantity,
 *  not a [ProductEntity] snapshot: [OrderRepository.confirmQuickSale] re-reads the real row by
 *  [productId] and revalidates it before trusting anything about it (name, price, stock), so a
 *  richer snapshot here would only invite accidentally trusting stale cart-time data. */
data class CartLine(val productId: Long, val quantity: Double)

/** Thrown when confirming a Quick Sale would take a tracked product's stock below zero. */
class InsufficientStockException(
    val productId: Long,
    val productName: String,
    val availableQuantity: Double,
    val requestedQuantity: Double,
) : RuntimeException("Insufficient stock for product $productId: available=$availableQuantity requested=$requestedQuantity")

/** Thrown when a cart line references a product that no longer exists or is no longer active. */
class ProductUnavailableException(
    val productId: Long,
    val productName: String,
) : RuntimeException("Product $productId ($productName) is not available")

class OrderRepository(
    private val database: AppDatabase,
    private val orderDao: OrderDao,
    private val orderItemDao: OrderItemDao,
    private val paymentDao: PaymentDao,
    private val debtDao: DebtDao,
    private val productDao: ProductDao,
) {

    fun getOpenComandas(): Flow<List<OrderEntity>> =
        orderDao.getByTypeAndStatus(OrderType.COMANDA, OrderStatus.OPEN)

    fun getItemsForOrder(orderId: Long): Flow<List<OrderItemEntity>> =
        orderItemDao.getItemsForOrder(orderId)

    /** Always derived from items (see [OrderEntity]'s doc) — never a stored/denormalized column. */
    fun getOrderTotalCents(orderId: Long): Flow<Long> = orderItemDao.getOrderTotalCents(orderId)

    suspend fun createComanda(customerId: Long?, displayName: String?): Long {
        val now = System.currentTimeMillis()
        return orderDao.insert(
            OrderEntity(
                customerId = customerId,
                displayName = displayName?.trim()?.ifBlank { null },
                orderType = OrderType.COMANDA,
                status = OrderStatus.OPEN,
                openedAt = now,
            ),
        )
    }

    suspend fun addItem(orderId: Long, product: ProductEntity, quantity: Double) {
        database.withTransaction {
            val order = orderDao.getById(orderId) ?: error("Order $orderId not found")
            check(order.status == OrderStatus.OPEN) { "Cannot add items to a closed order" }
            val subtotal = OrderTotalCalculator.itemSubtotal(Money(product.priceCents), quantity)
            orderItemDao.insert(
                OrderItemEntity(
                    orderId = orderId,
                    productId = product.id,
                    productNameSnapshot = product.name,
                    unitPriceCentsSnapshot = product.priceCents,
                    quantity = quantity,
                    subtotalCents = subtotal.minorUnits,
                ),
            )
        }
    }

    suspend fun updateItemQuantity(item: OrderItemEntity, newQuantity: Double) {
        database.withTransaction {
            val order = orderDao.getById(item.orderId) ?: error("Order ${item.orderId} not found")
            check(order.status == OrderStatus.OPEN) { "Cannot edit items on a closed order" }
            val subtotal = OrderTotalCalculator.itemSubtotal(Money(item.unitPriceCentsSnapshot), newQuantity)
            orderItemDao.update(item.copy(quantity = newQuantity, subtotalCents = subtotal.minorUnits))
        }
    }

    suspend fun removeItem(item: OrderItemEntity) {
        database.withTransaction {
            val order = orderDao.getById(item.orderId) ?: error("Order ${item.orderId} not found")
            check(order.status == OrderStatus.OPEN) { "Cannot remove items from a closed order" }
            orderItemDao.delete(item)
        }
    }

    /** Closes an order with a real payment (cash/debit/credit/pix/other) — never used for Fiado. */
    suspend fun closeOrderWithPayment(orderId: Long, method: PaymentMethod, cashSessionId: Long?) {
        database.withTransaction {
            val order = orderDao.getById(orderId) ?: error("Order $orderId not found")
            check(order.status == OrderStatus.OPEN) { "Order is not open" }
            val totalCents = orderItemDao.getOrderTotalCentsOnce(orderId)
            check(totalCents > 0) { "Cannot close an order with no items" }
            val now = System.currentTimeMillis()
            paymentDao.insert(
                PaymentEntity(
                    orderId = orderId,
                    cashSessionId = cashSessionId,
                    method = method,
                    amountCents = totalCents,
                    createdAt = now,
                ),
            )
            orderDao.close(orderId, OrderStatus.CLOSED, now)
        }
    }

    /**
     * Closes an order as Fiado: creates a [DebtEntity] for the order's total instead of a
     * [PaymentEntity], since no money is received at this moment. Requires a known customer.
     */
    suspend fun closeOrderAsFiado(orderId: Long) {
        database.withTransaction {
            val order = orderDao.getById(orderId) ?: error("Order $orderId not found")
            check(order.status == OrderStatus.OPEN) { "Order is not open" }
            val customerId = order.customerId ?: error("Fiado requires a customer on the order")
            val totalCents = orderItemDao.getOrderTotalCentsOnce(orderId)
            check(totalCents > 0) { "Cannot close an order with no items" }
            val now = System.currentTimeMillis()
            debtDao.insert(
                DebtEntity(
                    customerId = customerId,
                    orderId = orderId,
                    originalAmountCents = totalCents,
                    status = DebtStatus.OPEN,
                    createdAt = now,
                ),
            )
            orderDao.close(orderId, OrderStatus.CLOSED, now)
        }
    }

    /**
     * Confirms a Quick Sale atomically: creates the order (already closed), its items, and
     * either a payment or a Fiado debt, plus stock movement for tracked products — all in a
     * single transaction. If any step fails, nothing is persisted.
     *
     * Every line is revalidated against the database's current state before anything is
     * written — [CartLine] only carries a product id and quantity, so the product's existence,
     * active state, current selling price, and tracked stock are all read fresh here and are
     * what actually gets persisted.
     */
    suspend fun confirmQuickSale(
        lines: List<CartLine>,
        method: PaymentMethod?,
        isFiado: Boolean,
        customerId: Long?,
        cashSessionId: Long?,
    ): Long {
        require(lines.isNotEmpty()) { "Cannot confirm a sale with no items" }
        require(isFiado != (method != null)) { "Provide either a payment method or Fiado, not both/neither" }
        if (isFiado) require(customerId != null) { "Fiado requires a customer" }

        return database.withTransaction {
            // Revalidate against the current database state — never trust product data
            // captured when the cart was built. Validating every line before writing anything
            // means a problem with one item never leaves earlier items half-persisted (the
            // surrounding transaction would roll those back anyway, but failing fast here keeps
            // the intent explicit).
            val freshLines = lines.map { line ->
                require(line.quantity > 0) { "Quantity must be greater than zero" }
                val fresh = productDao.getById(line.productId)
                if (fresh == null || !fresh.active) {
                    throw ProductUnavailableException(line.productId, fresh?.name.orEmpty())
                }
                if (fresh.trackStock && fresh.stockQuantity < line.quantity) {
                    throw InsufficientStockException(fresh.id, fresh.name, fresh.stockQuantity, line.quantity)
                }
                fresh to line.quantity
            }

            val now = System.currentTimeMillis()
            val orderId = orderDao.insert(
                OrderEntity(
                    customerId = customerId,
                    orderType = OrderType.QUICK_SALE,
                    status = OrderStatus.OPEN,
                    openedAt = now,
                ),
            )

            var totalCents = 0L
            for ((product, quantity) in freshLines) {
                val subtotal = OrderTotalCalculator.itemSubtotal(Money(product.priceCents), quantity)
                orderItemDao.insert(
                    OrderItemEntity(
                        orderId = orderId,
                        productId = product.id,
                        productNameSnapshot = product.name,
                        unitPriceCentsSnapshot = product.priceCents,
                        quantity = quantity,
                        subtotalCents = subtotal.minorUnits,
                    ),
                )
                totalCents += subtotal.minorUnits
                if (product.trackStock) {
                    productDao.decrementStock(product.id, quantity, now)
                }
            }

            if (isFiado) {
                debtDao.insert(
                    DebtEntity(
                        customerId = requireNotNull(customerId),
                        orderId = orderId,
                        originalAmountCents = totalCents,
                        status = DebtStatus.OPEN,
                        createdAt = now,
                    ),
                )
            } else {
                paymentDao.insert(
                    PaymentEntity(
                        orderId = orderId,
                        cashSessionId = cashSessionId,
                        method = requireNotNull(method),
                        amountCents = totalCents,
                        createdAt = now,
                    ),
                )
            }

            orderDao.close(orderId, OrderStatus.CLOSED, now)
            orderId
        }
    }
}
