package com.pedroleite.opencomanda.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.domain.DebtStatus
import com.pedroleite.opencomanda.domain.OrderStatus
import com.pedroleite.opencomanda.domain.OrderType
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
            cashSessionDao = database.cashSessionDao(),
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
        orderRepository.addComandaItem(orderId, productId, 3.0)

        val total = orderRepository.getOrderTotalCents(orderId).first()
        assertEquals(3000L, total)
    }

    @Test
    fun cannotAddItemsToAClosedOrder() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 1")
        orderRepository.addComandaItem(orderId, productId, 1.0)
        orderRepository.closeOrderWithPayment(orderId, PaymentMethod.CASH)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.addComandaItem(orderId, productId, 1.0) }
        }
        Unit
    }

    @Test
    fun closingWithPaymentCreatesAPaymentAndClosesTheOrder() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 2")
        orderRepository.addComandaItem(orderId, productId, 2.0)

        orderRepository.closeOrderWithPayment(orderId, PaymentMethod.PIX)

        val order = database.orderDao().getById(orderId)!!
        assertEquals(OrderStatus.CLOSED, order.status)
        val payments = database.paymentDao().getForOrder(orderId).first()
        assertEquals(1, payments.size)
        assertEquals(2000L, payments.single().amountCents)
        assertEquals(0, database.debtDao().getForCustomer(customerId).first().size)
    }

    @Test
    fun closingAsFiadoCreatesADebtInsteadOfAPayment() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = customerId, displayName = "Joao")
        orderRepository.addComandaItem(orderId, productId, 4.0)
        val before = System.currentTimeMillis()

        orderRepository.closeOrderAsFiado(orderId)
        val after = System.currentTimeMillis()

        val order = database.orderDao().getById(orderId)!!
        assertEquals(OrderStatus.CLOSED, order.status)
        assertTrue(order.closedAt != null && order.closedAt!! in before..after)

        val payments = database.paymentDao().getForOrder(orderId).first()
        assertTrue("Fiado must not create a Payment row", payments.isEmpty())

        val debts = database.debtDao().getForCustomer(customerId).first()
        assertEquals(1, debts.size)
        assertEquals(4000L, debts.single().originalAmountCents)
        assertEquals(orderId, debts.single().orderId)
        assertEquals(customerId, debts.single().customerId)
        assertEquals(DebtStatus.OPEN, debts.single().status)
    }

    @Test
    fun closingAsFiadoDoesNotMutateStockASecondTime() = runBlocking {
        val trackedProductId = database.productDao().insert(
            com.pedroleite.opencomanda.data.local.entity.ProductEntity(
                name = "Cerveja",
                priceCents = 800,
                stockQuantity = 20.0,
                trackStock = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val orderId = orderRepository.createComanda(customerId = customerId, displayName = "Joao")
        orderRepository.addComandaItem(orderId, trackedProductId, 3.0)
        val stockAfterAdding = database.productDao().getById(trackedProductId)!!.stockQuantity

        orderRepository.closeOrderAsFiado(orderId)

        val stockAfterFiado = database.productDao().getById(trackedProductId)!!.stockQuantity
        assertEquals(stockAfterAdding, stockAfterFiado, 0.0)
        assertEquals(17.0, stockAfterFiado, 0.0)
    }

    @Test
    fun closingAsFiadoWithoutACustomerFails() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Balcao")
        orderRepository.addComandaItem(orderId, productId, 1.0)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.closeOrderAsFiado(orderId) }
        }
        Unit
    }

    @Test
    fun closingAnEmptyComandaAsFiadoFails() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = customerId, displayName = "Joao")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.closeOrderAsFiado(orderId) }
        }
        Unit
    }

    @Test
    fun closingAnAlreadyClosedComandaAsFiadoFails() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = customerId, displayName = "Joao")
        orderRepository.addComandaItem(orderId, productId, 1.0)
        orderRepository.closeOrderAsFiado(orderId)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.closeOrderAsFiado(orderId) }
        }
        // Exactly one debt — the duplicate attempt must not create a second one.
        assertEquals(1, database.debtDao().getForCustomer(customerId).first().size)
    }

    @Test
    fun closingAComandaClosedWithPaymentAsFiadoFails() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = customerId, displayName = "Joao")
        orderRepository.addComandaItem(orderId, productId, 1.0)
        orderRepository.closeOrderWithPayment(orderId, PaymentMethod.CASH)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.closeOrderAsFiado(orderId) }
        }
        Unit
    }

    @Test
    fun closingACancelledComandaAsFiadoFails() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = customerId, displayName = "Joao")
        orderRepository.addComandaItem(orderId, productId, 1.0)
        orderRepository.cancelComanda(orderId)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.closeOrderAsFiado(orderId) }
        }
        Unit
    }

    @Test
    fun aQuickSaleOrderCannotBeClosedAsFiadoThroughTheComandaPath() = runBlocking {
        // confirmQuickSale creates and closes the order in the same transaction — this
        // directly persists an OPEN Quick Sale row to exercise closeOrderAsFiado's own
        // orderType guard, independent of the status guard the two paths share.
        val now = System.currentTimeMillis()
        val orderId = database.orderDao().insert(
            com.pedroleite.opencomanda.data.local.entity.OrderEntity(
                customerId = customerId,
                orderType = OrderType.QUICK_SALE,
                status = OrderStatus.OPEN,
                openedAt = now,
            ),
        )
        database.orderItemDao().insert(
            com.pedroleite.opencomanda.data.local.entity.OrderItemEntity(
                orderId = orderId,
                productId = productId,
                productNameSnapshot = "Espetinho",
                unitPriceCentsSnapshot = 1000,
                quantity = 1.0,
                subtotalCents = 1000,
            ),
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.closeOrderAsFiado(orderId) }
        }
        Unit
    }

    @Test
    fun createComandaProducesAnOpenComandaOrder() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 5")

        val order = database.orderDao().getById(orderId)!!
        assertEquals(OrderType.COMANDA, order.orderType)
        assertEquals(OrderStatus.OPEN, order.status)
        assertEquals("Mesa 5", order.displayName)
        assertEquals(null, order.closedAt)
    }

    @Test
    fun createComandaRejectsABlankName() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { orderRepository.createComanda(customerId = null, displayName = "   ") }
        }
        Unit
    }

    @Test
    fun createComandaTrimsTheName() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "  Mesa 7  ")
        assertEquals("Mesa 7", database.orderDao().getById(orderId)!!.displayName)
    }

    @Test
    fun createComandaAcceptsAnOptionalActiveCustomer() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = customerId, displayName = "Joao")
        assertEquals(customerId, database.orderDao().getById(orderId)!!.customerId)
    }

    @Test
    fun openComandasListsOnlyOpenComandasNotQuickSales() = runBlocking {
        val comandaId = orderRepository.createComanda(customerId = null, displayName = "Mesa 8")
        orderRepository.confirmQuickSale(
            lines = listOf(CartLine(productId, 1.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
        )

        val openComandas = orderRepository.getOpenComandas().first()
        assertEquals(1, openComandas.size)
        assertEquals(comandaId, openComandas.single().order.id)
    }

    @Test
    fun addComandaItemSnapshotsNameAndPrice() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 9")
        orderRepository.addComandaItem(orderId, productId, 2.0)

        val item = database.orderItemDao().getItemsForOrder(orderId).first().single()
        assertEquals("Espetinho", item.productNameSnapshot)
        assertEquals(1000L, item.unitPriceCentsSnapshot)
        assertEquals(2000L, item.subtotalCents)
    }

    @Test
    fun addingTheSameProductAgainMergesIntoTheExistingLine() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 10")
        orderRepository.addComandaItem(orderId, productId, 2.0)
        orderRepository.addComandaItem(orderId, productId, 1.0)

        val items = database.orderItemDao().getItemsForOrder(orderId).first()
        assertEquals(1, items.size)
        assertEquals(3.0, items.single().quantity, 0.0001)
        assertEquals(3000L, items.single().subtotalCents)
    }

    @Test
    fun aPriceChangeDoesNotAlterAnExistingComandaLineSnapshot() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 11")
        orderRepository.addComandaItem(orderId, productId, 1.0) // snapshots priceCents = 1000

        productRepository().update(product().copy(priceCents = 1500))
        orderRepository.addComandaItem(orderId, productId, 1.0) // must keep the original 1000 snapshot

        val item = database.orderItemDao().getItemsForOrder(orderId).first().single()
        assertEquals(1000L, item.unitPriceCentsSnapshot)
        assertEquals(2.0, item.quantity, 0.0001)
        assertEquals(2000L, item.subtotalCents)
    }

    @Test
    fun addComandaItemDecrementsTrackedStockAndLeavesUntrackedStockAlone() = runBlocking {
        val now = System.currentTimeMillis()
        val trackedId = database.productDao().insert(
            ProductEntity(name = "Cerveja", priceCents = 800, trackStock = true, stockQuantity = 10.0, createdAt = now, updatedAt = now),
        )
        val untrackedId = database.productDao().insert(
            ProductEntity(name = "Agua", priceCents = 300, trackStock = false, stockQuantity = 0.0, createdAt = now, updatedAt = now),
        )
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 12")

        orderRepository.addComandaItem(orderId, trackedId, 2.0)
        orderRepository.addComandaItem(orderId, untrackedId, 5.0)

        assertEquals(8.0, database.productDao().getById(trackedId)!!.stockQuantity, 0.0001)
        assertEquals(0.0, database.productDao().getById(untrackedId)!!.stockQuantity, 0.0001)
    }

    @Test
    fun decrementingAComandaItemRestoresTrackedStock() = runBlocking {
        val now = System.currentTimeMillis()
        val trackedId = database.productDao().insert(
            ProductEntity(name = "Cerveja", priceCents = 800, trackStock = true, stockQuantity = 10.0, createdAt = now, updatedAt = now),
        )
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 13")
        orderRepository.addComandaItem(orderId, trackedId, 2.0) // stock 10 -> 8

        orderRepository.decrementComandaItem(orderId, trackedId) // stock 8 -> 9

        assertEquals(9.0, database.productDao().getById(trackedId)!!.stockQuantity, 0.0001)
        assertEquals(1.0, database.orderItemDao().getItemsForOrder(orderId).first().single().quantity, 0.0001)
    }

    @Test
    fun decrementingAComandaItemsLastUnitRemovesTheLineAndRestoresItsStock() = runBlocking {
        val now = System.currentTimeMillis()
        val trackedId = database.productDao().insert(
            ProductEntity(name = "Cerveja", priceCents = 800, trackStock = true, stockQuantity = 10.0, createdAt = now, updatedAt = now),
        )
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 14")
        orderRepository.addComandaItem(orderId, trackedId, 1.0) // stock 10 -> 9

        orderRepository.decrementComandaItem(orderId, trackedId) // removes the line, stock 9 -> 10

        assertTrue(database.orderItemDao().getItemsForOrder(orderId).first().isEmpty())
        assertEquals(10.0, database.productDao().getById(trackedId)!!.stockQuantity, 0.0001)
    }

    @Test
    fun addComandaItemRejectsInsufficientStockAndChangesNothing() = runBlocking {
        val now = System.currentTimeMillis()
        val trackedId = database.productDao().insert(
            ProductEntity(name = "Cerveja", priceCents = 800, trackStock = true, stockQuantity = 2.0, createdAt = now, updatedAt = now),
        )
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 15")

        assertThrows(InsufficientStockException::class.java) {
            runBlocking { orderRepository.addComandaItem(orderId, trackedId, 3.0) }
        }

        assertEquals(2.0, database.productDao().getById(trackedId)!!.stockQuantity, 0.0001)
        assertTrue(database.orderItemDao().getItemsForOrder(orderId).first().isEmpty())
    }

    @Test
    fun addComandaItemRejectsAnInactiveProductSafely() = runBlocking {
        database.productDao().setActive(productId, false, System.currentTimeMillis())
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 16")

        assertThrows(ProductUnavailableException::class.java) {
            runBlocking { orderRepository.addComandaItem(orderId, productId, 1.0) }
        }
        assertTrue(database.orderItemDao().getItemsForOrder(orderId).first().isEmpty())
    }

    @Test
    fun cannotDecrementItemsOnAClosedOrder() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 17")
        orderRepository.addComandaItem(orderId, productId, 1.0)
        orderRepository.closeOrderWithPayment(orderId, PaymentMethod.CASH)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.decrementComandaItem(orderId, productId) }
        }
        Unit
    }

    @Test
    fun cannotAddItemsToACancelledComanda() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 18")
        orderRepository.addComandaItem(orderId, productId, 1.0)
        orderRepository.cancelComanda(orderId)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.addComandaItem(orderId, productId, 1.0) }
        }
        Unit
    }

    @Test
    fun closingAComandaWithNoItemsIsRejected() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 19")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.closeOrderWithPayment(orderId, PaymentMethod.CASH) }
        }
        Unit
    }

    @Test
    fun closingAComandaSetsClosedAtAndRemovesItFromTheOpenList() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 20")
        orderRepository.addComandaItem(orderId, productId, 1.0)

        orderRepository.closeOrderWithPayment(orderId, PaymentMethod.CASH)

        val order = database.orderDao().getById(orderId)!!
        assertTrue(order.closedAt != null)
        assertTrue(orderRepository.getOpenComandas().first().none { it.order.id == orderId })
    }

    @Test
    fun comandaPaymentAmountIsCalculatedFromPersistedItemsNotATrustedTotal() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 21")
        orderRepository.addComandaItem(orderId, productId, 2.0)

        orderRepository.closeOrderWithPayment(orderId, PaymentMethod.PIX)

        val payment = database.paymentDao().getForOrder(orderId).first().single()
        val itemsTotal = database.orderItemDao().getOrderTotalCentsOnce(orderId)
        assertEquals(itemsTotal, payment.amountCents)
    }

    @Test
    fun closingAComandaTwiceIsRejectedAndNeverCreatesASecondPayment() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 22")
        orderRepository.addComandaItem(orderId, productId, 1.0)
        orderRepository.closeOrderWithPayment(orderId, PaymentMethod.CASH)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.closeOrderWithPayment(orderId, PaymentMethod.CASH) }
        }

        assertEquals(1, database.paymentDao().getForOrder(orderId).first().size)
    }

    @Test
    fun cancellingAComandaRestoresTrackedStockAndCreatesNoPayment() = runBlocking {
        val now = System.currentTimeMillis()
        val trackedId = database.productDao().insert(
            ProductEntity(name = "Cerveja", priceCents = 800, trackStock = true, stockQuantity = 10.0, createdAt = now, updatedAt = now),
        )
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 23")
        orderRepository.addComandaItem(orderId, trackedId, 3.0) // stock 10 -> 7
        orderRepository.addComandaItem(orderId, productId, 1.0) // untracked-by-comparison second line

        orderRepository.cancelComanda(orderId)

        val order = database.orderDao().getById(orderId)!!
        assertEquals(OrderStatus.CANCELLED, order.status)
        assertTrue(order.closedAt != null)
        assertEquals(10.0, database.productDao().getById(trackedId)!!.stockQuantity, 0.0001)
        assertTrue("Cancelling must not create a Payment", database.paymentDao().getForOrder(orderId).first().isEmpty())
        // Items are kept, not physically deleted.
        assertEquals(2, database.orderItemDao().getItemsForOrder(orderId).first().size)
        assertTrue(orderRepository.getOpenComandas().first().none { it.order.id == orderId })
    }

    @Test
    fun cancellingAComandaTwiceIsRejected() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 24")
        orderRepository.addComandaItem(orderId, productId, 1.0)
        orderRepository.cancelComanda(orderId)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.cancelComanda(orderId) }
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
            lines = listOf(CartLine(trackedProduct.id, 2.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
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
                    lines = listOf(CartLine(product().id, 1.0)),
                    method = null,
                    isFiado = true,
                    customerId = null,
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
                )
            }
        }
        Unit
    }

    @Test
    fun confirmQuickSaleRollsBackCompletelyWhenAnItemIsInvalid() = runBlocking {
        val invalidLine = CartLine(product().id, quantity = 0.0) // itemSubtotal() rejects quantity <= 0

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                orderRepository.confirmQuickSale(
                    lines = listOf(CartLine(product().id, 1.0), invalidLine),
                    method = PaymentMethod.CASH,
                    isFiado = false,
                    customerId = null,
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

    @Test
    fun confirmQuickSaleDecrementsMultipleTrackedProducts() = runBlocking {
        val now = System.currentTimeMillis()
        val espetinhoId = database.productDao().insert(
            ProductEntity(
                name = "Espetinho",
                priceCents = 1250,
                trackStock = true,
                stockQuantity = 10.0,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val cocaId = database.productDao().insert(
            ProductEntity(
                name = "Coca-Cola",
                priceCents = 600,
                trackStock = true,
                stockQuantity = 5.0,
                createdAt = now,
                updatedAt = now,
            ),
        )

        orderRepository.confirmQuickSale(
            lines = listOf(
                CartLine(espetinhoId, 2.0),
                CartLine(cocaId, 1.0),
            ),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
        )

        assertEquals(8.0, database.productDao().getById(espetinhoId)!!.stockQuantity, 0.0001)
        assertEquals(4.0, database.productDao().getById(cocaId)!!.stockQuantity, 0.0001)
    }

    @Test
    fun confirmQuickSaleDoesNotChangeStockForUntrackedProducts() = runBlocking {
        val now = System.currentTimeMillis()
        val untrackedId = database.productDao().insert(
            ProductEntity(
                name = "Agua",
                priceCents = 300,
                trackStock = false,
                stockQuantity = 0.0,
                createdAt = now,
                updatedAt = now,
            ),
        )

        orderRepository.confirmQuickSale(
            lines = listOf(CartLine(untrackedId, 5.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
        )

        assertEquals(0.0, database.productDao().getById(untrackedId)!!.stockQuantity, 0.0001)
    }

    @Test
    fun confirmQuickSaleRejectsInsufficientStockAndPersistsNothing() = runBlocking {
        val now = System.currentTimeMillis()
        val trackedId = database.productDao().insert(
            ProductEntity(
                name = "Coca-Cola",
                priceCents = 600,
                trackStock = true,
                stockQuantity = 2.0,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val trackedProduct = database.productDao().getById(trackedId)!!

        assertThrows(InsufficientStockException::class.java) {
            runBlocking {
                orderRepository.confirmQuickSale(
                    lines = listOf(CartLine(trackedProduct.id, 3.0)),
                    method = PaymentMethod.CASH,
                    isFiado = false,
                    customerId = null,
                )
            }
        }

        // Nothing persisted, and stock is untouched.
        assertEquals(2.0, database.productDao().getById(trackedId)!!.stockQuantity, 0.0001)
        assertTrue(database.orderDao().getByTypeAndStatus(OrderType.QUICK_SALE, OrderStatus.CLOSED).first().isEmpty())
        val orderCursor = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM orders")
        orderCursor.moveToFirst()
        assertEquals(0, orderCursor.getInt(0))
        orderCursor.close()
        val paymentCursor = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM payments")
        paymentCursor.moveToFirst()
        assertEquals(0, paymentCursor.getInt(0))
        paymentCursor.close()
    }

    @Test
    fun confirmQuickSaleFailsSafelyWhenAProductWasDeactivatedAfterTheCartWasBuilt() = runBlocking {
        val staleProduct = product()
        database.productDao().setActive(productId, false, System.currentTimeMillis())

        assertThrows(ProductUnavailableException::class.java) {
            runBlocking {
                orderRepository.confirmQuickSale(
                    lines = listOf(CartLine(staleProduct.id, 1.0)),
                    method = PaymentMethod.CASH,
                    isFiado = false,
                    customerId = null,
                )
            }
        }

        val cursor = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM orders")
        cursor.moveToFirst()
        assertEquals(0, cursor.getInt(0))
        cursor.close()
    }

    @Test
    fun confirmQuickSaleUsesTheCurrentPriceAtConfirmationNotAStaleCartSnapshot() = runBlocking {
        val staleProduct = product() // priceCents = 1000, captured before the price change below
        productRepository().update(staleProduct.copy(priceCents = 1500))

        val orderId = orderRepository.confirmQuickSale(
            lines = listOf(CartLine(staleProduct.id, 1.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
        )

        val item = database.orderItemDao().getItemsForOrder(orderId).first().single()
        assertEquals(1500L, item.unitPriceCentsSnapshot)
        assertEquals(1500L, item.subtotalCents)
    }

    @Test
    fun confirmQuickSalePaymentAmountEqualsTheSaleTotal() = runBlocking {
        val orderId = orderRepository.confirmQuickSale(
            lines = listOf(CartLine(product().id, 3.0)),
            method = PaymentMethod.PIX,
            isFiado = false,
            customerId = null,
        )

        val payment = database.paymentDao().getForOrder(orderId).first().single()
        val itemsTotal = database.orderItemDao().getOrderTotalCentsOnce(orderId)
        assertEquals(itemsTotal, payment.amountCents)
        assertEquals(PaymentMethod.PIX, payment.method)
    }

    @Test
    fun confirmQuickSaleDoesNotRequireACustomer() = runBlocking {
        val orderId = orderRepository.confirmQuickSale(
            lines = listOf(CartLine(product().id, 1.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
        )

        val order = database.orderDao().getById(orderId)!!
        assertEquals(null, order.customerId)
    }

    @Test
    fun confirmQuickSalePersistsWithoutACashSessionWhenNoneIsProvided() = runBlocking {
        val orderId = orderRepository.confirmQuickSale(
            lines = listOf(CartLine(product().id, 1.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
        )

        val payment = database.paymentDao().getForOrder(orderId).first().single()
        assertEquals(null, payment.cashSessionId)
    }

    private fun productRepository() = ProductRepository(database, database.productDao())

    @Test
    fun quickSaleReceiptReflectsThePersistedPriceAndPaymentNotTheStaleCart() = runBlocking<Unit> {
        val stale = product() // priceCents = 1000, what a cart built earlier would show
        productRepository().update(stale.copy(priceCents = 1200))

        val orderId = orderRepository.confirmQuickSale(
            lines = listOf(CartLine(stale.id, 2.0)),
            method = PaymentMethod.PIX,
            isFiado = false,
            customerId = null,
        )
        val receipt = orderRepository.getQuickSaleReceipt(orderId)

        val line = receipt.lines.single()
        assertEquals("Espetinho", line.productName)
        assertEquals(1200L, line.unitPriceCents)
        assertEquals(2.0, line.quantity, 0.0)
        assertEquals(2400L, line.subtotalCents)
        assertEquals(2400L, receipt.totalCents)
        assertEquals(PaymentMethod.PIX, receipt.paymentMethod)
        assertEquals(
            "The receipt total must equal the recorded payment",
            database.paymentDao().getForOrder(orderId).first().single().amountCents,
            receipt.totalCents,
        )
    }

    @Test
    fun quickSaleReceiptWithSeveralProductsSumsEveryPersistedLine() = runBlocking<Unit> {
        val now = System.currentTimeMillis()
        val coca = database.productDao().insert(
            ProductEntity(name = "Coca", priceCents = 500, createdAt = now, updatedAt = now),
        )

        val orderId = orderRepository.confirmQuickSale(
            lines = listOf(CartLine(productId, 1.0), CartLine(coca, 3.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
        )
        val receipt = orderRepository.getQuickSaleReceipt(orderId)

        assertEquals(2, receipt.lines.size)
        assertEquals(receipt.lines.sumOf { it.subtotalCents }, receipt.totalCents)
        assertEquals(2500L, receipt.totalCents)
    }

    @Test
    fun quickSaleReceiptIsRejectedForAnOrderWithoutASinglePayment() = runBlocking<Unit> {
        val comanda = orderRepository.createComanda(customerId = null, displayName = "Mesa 9")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { orderRepository.getQuickSaleReceipt(comanda) }
        }
    }
}
