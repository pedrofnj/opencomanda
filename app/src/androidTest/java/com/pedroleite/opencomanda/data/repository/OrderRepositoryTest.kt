package com.pedroleite.opencomanda.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.domain.DebtStatus
import com.pedroleite.opencomanda.domain.OrderStatus
import com.pedroleite.opencomanda.domain.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the critical order/comanda/quick-sale rules: closed orders reject edits,
 * Fiado produces a debt instead of a payment, and Quick Sale confirmation is atomic.
 */
@RunWith(AndroidJUnit4::class)
class OrderRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var orderRepository: OrderRepository

    private var productId: Long = 0
    private var customerId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        orderRepository = OrderRepository(
            database = database,
            orderDao = database.orderDao(),
            orderItemDao = database.orderItemDao(),
            paymentDao = database.paymentDao(),
            debtDao = database.debtDao(),
            productDao = database.productDao(),
        )

        val now = System.currentTimeMillis()
        productId = database.productDao().insert(
            ProductEntity(name = "Espetinho", priceCents = 1000, createdAt = now, updatedAt = now),
        )
        customerId = database.customerDao().insert(
            CustomerEntity(name = "Joao", createdAt = now, updatedAt = now),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun product() = database.productDao().getById(productId)!!

    @Test
    fun comandaTotalReflectsAddedItems() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 3")
        orderRepository.addItem(orderId, product(), 3.0)

        val total = orderRepository.getOrderTotalCents(orderId).first()
        assertEquals(3000L, total)
    }

    @Test
    fun cannotAddItemsToAClosedOrder() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 1")
        orderRepository.addItem(orderId, product(), 1.0)
        orderRepository.closeOrderWithPayment(orderId, PaymentMethod.CASH, cashSessionId = null)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.addItem(orderId, product(), 1.0) }
        }
        Unit
    }

    @Test
    fun closingWithPaymentCreatesAPaymentAndClosesTheOrder() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 2")
        orderRepository.addItem(orderId, product(), 2.0)

        orderRepository.closeOrderWithPayment(orderId, PaymentMethod.PIX, cashSessionId = null)

        val order = database.orderDao().getById(orderId)!!
        assertEquals(OrderStatus.CLOSED, order.status)
        val payments = database.paymentDao().getForOrder(orderId).first()
        assertEquals(1, payments.size)
        assertEquals(2000L, payments.single().amountCents)
        assertEquals(0, database.debtDao().getForCustomer(customerId).first().size)
    }

    @Test
    fun closingAsFiadoCreatesADebtInsteadOfAPayment() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = customerId, displayName = null)
        orderRepository.addItem(orderId, product(), 4.0)

        orderRepository.closeOrderAsFiado(orderId)

        val order = database.orderDao().getById(orderId)!!
        assertEquals(OrderStatus.CLOSED, order.status)

        val payments = database.paymentDao().getForOrder(orderId).first()
        assertTrue("Fiado must not create a Payment row", payments.isEmpty())

        val debts = database.debtDao().getForCustomer(customerId).first()
        assertEquals(1, debts.size)
        assertEquals(4000L, debts.single().originalAmountCents)
        assertEquals(DebtStatus.OPEN, debts.single().status)
    }

    @Test
    fun closingAsFiadoWithoutACustomerFails() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Balcao")
        orderRepository.addItem(orderId, product(), 1.0)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.closeOrderAsFiado(orderId) }
        }
        Unit
    }

    @Test
    fun confirmQuickSaleWithPaymentIsAtomicAndDecrementsTrackedStock() = runBlocking {
        val now = System.currentTimeMillis()
        val trackedProductId = database.productDao().insert(
            ProductEntity(
                name = "Cerveja",
                priceCents = 800,
                trackStock = true,
                stockQuantity = 10.0,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val trackedProduct = database.productDao().getById(trackedProductId)!!

        val orderId = orderRepository.confirmQuickSale(
            lines = listOf(CartLine(trackedProduct, 2.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
            cashSessionId = null,
        )

        val order = database.orderDao().getById(orderId)!!
        assertEquals(OrderStatus.CLOSED, order.status)

        val items = database.orderItemDao().getItemsForOrder(orderId).first()
        assertEquals(1, items.size)
        assertEquals(1600L, items.single().subtotalCents)

        val payments = database.paymentDao().getForOrder(orderId).first()
        assertEquals(1600L, payments.single().amountCents)

        val updatedProduct = database.productDao().getById(trackedProductId)!!
        assertEquals(8.0, updatedProduct.stockQuantity, 0.0001)
    }

    @Test
    fun confirmQuickSaleAsFiadoRequiresACustomer() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                orderRepository.confirmQuickSale(
                    lines = listOf(CartLine(product(), 1.0)),
                    method = null,
                    isFiado = true,
                    customerId = null,
                    cashSessionId = null,
                )
            }
        }
        Unit
    }

    @Test
    fun confirmQuickSaleRejectsAnEmptyCart() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                orderRepository.confirmQuickSale(
                    lines = emptyList(),
                    method = PaymentMethod.CASH,
                    isFiado = false,
                    customerId = null,
                    cashSessionId = null,
                )
            }
        }
        Unit
    }

    @Test
    fun confirmQuickSaleRollsBackCompletelyWhenAnItemIsInvalid() = runBlocking {
        val invalidLine = CartLine(product(), quantity = 0.0) // itemSubtotal() rejects quantity <= 0

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                orderRepository.confirmQuickSale(
                    lines = listOf(CartLine(product(), 1.0), invalidLine),
                    method = PaymentMethod.CASH,
                    isFiado = false,
                    customerId = null,
                    cashSessionId = null,
                )
            }
        }

        // The transaction must have rolled back: no dangling order/items/payments left behind.
        val cursor = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM orders")
        cursor.moveToFirst()
        val orderCount = cursor.getInt(0)
        cursor.close()
        assertEquals(0, orderCount)
    }
}
