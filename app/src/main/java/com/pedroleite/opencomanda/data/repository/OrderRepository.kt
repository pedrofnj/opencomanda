package com.pedroleite.opencomanda.data.repository

import androidx.room.withTransaction
import com.pedroleite.opencomanda.core.Money
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.dao.CashSessionDao
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
import com.pedroleite.opencomanda.domain.CashSessionStatus
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
    private val cashSessionDao: CashSessionDao,
) {

    /** The currently OPEN cash session's id, if any — looked up fresh at the moment a Payment
     *  is about to be created, inside the same transaction, so it's never based on UI state that
     *  could be stale. If no session is open, the Payment simply isn't attached to one; it is
     *  NOT retroactively attached to a session opened later (see [CashRegisterRepository]). */
    private suspend fun currentOpenCashSessionId(): Long? = cashSessionDao.getByStatus(CashSessionStatus.OPEN)?.id

    /** Every currently OPEN Comanda, with its item count and total pre-aggregated for the Open
     *  Comandas list. Never includes Quick Sales (a different [OrderType]) or CLOSED/CANCELLED
     *  orders — closing or cancelling a Comanda removes it from this list automatically, since
     *  it's a live Room query keyed on [OrderStatus.OPEN]. */
    fun getOpenComandas(): Flow<List<OrderDao.ComandaSummary>> =
        orderDao.getSummariesByTypeAndStatus(OrderType.COMANDA, OrderStatus.OPEN)

    /** Reactive lookup for a single Comanda's own record (status, name, customer) — the detail
     *  screen combines this with [getItemsForOrder] to reload everything from Room, including
     *  after the operator leaves and returns, or this same order is closed/cancelled. */
    fun getComanda(orderId: Long): Flow<OrderEntity?> = orderDao.observeById(orderId)

    /** One-shot lookup of an order's display name (e.g. "Mesa 4") — used by the Fiado screens to
     *  label a debt by the Comanda it came from without observing the whole order reactively. */
    suspend fun getDisplayName(orderId: Long): String? = orderDao.getById(orderId)?.displayName

    fun getItemsForOrder(orderId: Long): Flow<List<OrderItemEntity>> =
        orderItemDao.getItemsForOrder(orderId)

    /** Always derived from items (see [OrderEntity]'s doc) — never a stored/denormalized column. */
    fun getOrderTotalCents(orderId: Long): Flow<Long> = orderItemDao.getOrderTotalCents(orderId)

    /** Opens a new Comanda, persisted immediately (unlike Quick Sale's in-memory cart) so the
     *  operator can leave and return to it later. [displayName] is required — a Comanda always
     *  needs an identifying name ("Mesa 4", "João") to show in the Open Comandas list. */
    suspend fun createComanda(customerId: Long?, displayName: String): Long {
        val trimmedName = displayName.trim()
        require(trimmedName.isNotEmpty()) { "Comanda name must not be blank" }
        val now = System.currentTimeMillis()
        return orderDao.insert(
            OrderEntity(
                customerId = customerId,
                displayName = trimmedName,
                orderType = OrderType.COMANDA,
                status = OrderStatus.OPEN,
                openedAt = now,
            ),
        )
    }

    /**
     * Adds one product to an OPEN Comanda, reserving its stock immediately (the item has
     * already been served) — atomically: the order's OPEN status, the product's existence,
     * active state and tracked stock are all revalidated against the database's current state,
     * exactly like [confirmQuickSale].
     *
     * If this Comanda already has a line for this product, [quantity] is added onto it rather
     * than creating a second line — and that existing line's original price snapshot is kept
     * as-is, even if the product's current price has since changed. Only a brand-new line
     * snapshots the product's current name/price.
     */
    suspend fun addComandaItem(orderId: Long, productId: Long, quantity: Double = 1.0) {
        require(quantity > 0) { "Quantity must be greater than zero" }
        database.withTransaction {
            val order = orderDao.getById(orderId) ?: error("Order $orderId not found")
            check(order.status == OrderStatus.OPEN) { "Cannot add items to a non-open order" }

            val fresh = productDao.getById(productId)
            if (fresh == null || !fresh.active) {
                throw ProductUnavailableException(productId, fresh?.name.orEmpty())
            }
            if (fresh.trackStock && fresh.stockQuantity < quantity) {
                throw InsufficientStockException(fresh.id, fresh.name, fresh.stockQuantity, quantity)
            }

            val existing = orderItemDao.getByOrderAndProduct(orderId, productId)
            if (existing != null) {
                val newQuantity = existing.quantity + quantity
                val subtotal = OrderTotalCalculator.itemSubtotal(Money(existing.unitPriceCentsSnapshot), newQuantity)
                orderItemDao.update(existing.copy(quantity = newQuantity, subtotalCents = subtotal.minorUnits))
            } else {
                val subtotal = OrderTotalCalculator.itemSubtotal(Money(fresh.priceCents), quantity)
                orderItemDao.insert(
                    OrderItemEntity(
                        orderId = orderId,
                        productId = fresh.id,
                        productNameSnapshot = fresh.name,
                        unitPriceCentsSnapshot = fresh.priceCents,
                        quantity = quantity,
                        subtotalCents = subtotal.minorUnits,
                    ),
                )
            }
            if (fresh.trackStock) {
                productDao.decrementStock(fresh.id, quantity, System.currentTimeMillis())
            }
        }
    }

    /**
     * Removes one unit of [productId] from an OPEN Comanda's existing line, restoring its
     * tracked stock — the mirror image of [addComandaItem]. Decrementing a line's last unit
     * removes the line entirely rather than leaving a zero-quantity row. A no-op if there is no
     * such line (defensive: the UI should never offer this action in that case).
     */
    suspend fun decrementComandaItem(orderId: Long, productId: Long) {
        database.withTransaction {
            val order = orderDao.getById(orderId) ?: error("Order $orderId not found")
            check(order.status == OrderStatus.OPEN) { "Cannot edit items on a non-open order" }

            val existing = orderItemDao.getByOrderAndProduct(orderId, productId) ?: return@withTransaction
            val newQuantity = existing.quantity - 1.0
            if (newQuantity > 0.0) {
                val subtotal = OrderTotalCalculator.itemSubtotal(Money(existing.unitPriceCentsSnapshot), newQuantity)
                orderItemDao.update(existing.copy(quantity = newQuantity, subtotalCents = subtotal.minorUnits))
            } else {
                orderItemDao.delete(existing)
            }

            val product = productDao.getById(productId)
            if (product != null && product.trackStock) {
                productDao.incrementStock(productId, 1.0, System.currentTimeMillis())
            }
        }
    }

    /**
     * Cancels an OPEN Comanda: restores tracked stock for every item it currently holds, then
     * marks it CANCELLED. Order and items are kept (never physically deleted) so the record
     * remains inspectable; [OrderStatus.CANCELLED] makes it immutable the same way CLOSED does,
     * since every mutation above only proceeds when `status == OPEN`.
     */
    suspend fun cancelComanda(orderId: Long) {
        database.withTransaction {
            val order = orderDao.getById(orderId) ?: error("Order $orderId not found")
            check(order.status == OrderStatus.OPEN) { "Only an open comanda can be cancelled" }

            val now = System.currentTimeMillis()
            for (item in orderItemDao.getItemsForOrderOnce(orderId)) {
                val productId = item.productId ?: continue
                val product = productDao.getById(productId) ?: continue
                if (product.trackStock) {
                    productDao.incrementStock(productId, item.quantity, now)
                }
            }
            orderDao.close(orderId, OrderStatus.CANCELLED, now)
        }
    }

    /** Closes an order with a real payment (cash/debit/credit/pix/other) — never used for Fiado.
     *  The payment is attached to whichever cash session is OPEN at this exact moment, if any
     *  (see [currentOpenCashSessionId]) — never a session id supplied by the caller. */
    suspend fun closeOrderWithPayment(orderId: Long, method: PaymentMethod) {
        database.withTransaction {
            val order = orderDao.getById(orderId) ?: error("Order $orderId not found")
            check(order.status == OrderStatus.OPEN) { "Order is not open" }
            val totalCents = orderItemDao.getOrderTotalCentsOnce(orderId)
            check(totalCents > 0) { "Cannot close an order with no items" }
            val now = System.currentTimeMillis()
            paymentDao.insert(
                PaymentEntity(
                    orderId = orderId,
                    cashSessionId = currentOpenCashSessionId(),
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
     * Comanda-only — Quick Sale is always immediate payment (see [confirmQuickSale]'s own,
     * separate `isFiado` path, which this never touches).
     */
    suspend fun closeOrderAsFiado(orderId: Long) {
        database.withTransaction {
            val order = orderDao.getById(orderId) ?: error("Order $orderId not found")
            check(order.orderType == OrderType.COMANDA) { "Fiado can only be recorded by closing a Comanda" }
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
     *
     * A non-Fiado sale's payment is attached to whichever cash session is OPEN at this exact
     * moment, if any (see [currentOpenCashSessionId]) — never a session id supplied by the caller.
     */
    suspend fun confirmQuickSale(
        lines: List<CartLine>,
        method: PaymentMethod?,
        isFiado: Boolean,
        customerId: Long?,
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
                        cashSessionId = currentOpenCashSessionId(),
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
