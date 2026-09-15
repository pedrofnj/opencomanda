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

/** A single requested line when confirming a Quick Sale. */
data class CartLine(val product: ProductEntity, val quantity: Double)

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
            for (line in lines) {
                require(line.quantity > 0) { "Quantity must be greater than zero" }
                val subtotal = OrderTotalCalculator.itemSubtotal(Money(line.product.priceCents), line.quantity)
                orderItemDao.insert(
                    OrderItemEntity(
                        orderId = orderId,
                        productId = line.product.id,
                        productNameSnapshot = line.product.name,
                        unitPriceCentsSnapshot = line.product.priceCents,
                        quantity = line.quantity,
                        subtotalCents = subtotal.minorUnits,
                    ),
                )
                totalCents += subtotal.minorUnits
                if (line.product.trackStock) {
                    productDao.decrementStock(line.product.id, line.quantity, now)
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
