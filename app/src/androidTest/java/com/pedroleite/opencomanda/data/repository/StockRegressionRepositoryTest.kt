package com.pedroleite.opencomanda.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.domain.DebtStatus
import com.pedroleite.opencomanda.domain.OrderStatus
import com.pedroleite.opencomanda.domain.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cross-module regression for stock: it must be reserved exactly once (when a product is sold or
 * served), restored exactly once (when removed or cancelled), and never moved by anything that is
 * only about money — payment, Fiado, debt repayment, or the cash register.
 */
@RunWith(AndroidJUnit4::class)
class StockRegressionRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var products: ProductRepository
    private lateinit var orders: OrderRepository
    private lateinit var debts: DebtRepository
    private lateinit var cash: CashRegisterRepository
    private lateinit var customers: CustomerRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        products = ProductRepository(database, database.productDao())
        customers = CustomerRepository(database.customerDao())
        orders = OrderRepository(
            database = database,
            orderDao = database.orderDao(),
            orderItemDao = database.orderItemDao(),
            paymentDao = database.paymentDao(),
            debtDao = database.debtDao(),
            productDao = database.productDao(),
            cashSessionDao = database.cashSessionDao(),
        )
        debts = DebtRepository(database, database.debtDao(), database.debtPaymentDao(), database.cashSessionDao())
        cash = CashRegisterRepository(database, database.cashSessionDao(), database.paymentDao(), database.debtPaymentDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun tracked(name: String, price: Long, stock: Double): Long = products.create(
        name = name, description = null, priceCents = price, costCents = null,
        trackStock = true, initialStockQuantity = stock,
    )

    private suspend fun untracked(name: String, price: Long): Long = products.create(
        name = name, description = null, priceCents = price, costCents = null,
        trackStock = false, initialStockQuantity = 0.0,
    )

    private suspend fun stockOf(id: Long) = products.getById(id)!!.stockQuantity

    private suspend fun quickSale(vararg lines: CartLine, method: PaymentMethod = PaymentMethod.CASH): Long =
        orders.confirmQuickSale(lines.toList(), method, isFiado = false, customerId = null)

    private fun count(table: String): Int =
        database.query("SELECT COUNT(*) FROM $table", null).use { c -> c.moveToFirst(); c.getInt(0) }

    // ---------------------------------------------------------------------------------------
    // Quick Sale
    // ---------------------------------------------------------------------------------------

    @Test
    fun aQuickSaleTakesTheSoldQuantityOutOfTrackedStock() = runBlocking {
        val id = tracked("Espetinho", 1250, 10.0)

        quickSale(CartLine(id, 2.0))

        assertEquals(8.0, stockOf(id), 0.0)
    }

    @Test
    fun aQuickSaleThatWantsMoreThanIsInStockIsRejectedWithNothingPersisted() = runBlocking {
        val id = tracked("Espetinho", 1250, 1.0)

        assertThrows(InsufficientStockException::class.java) { runBlocking { quickSale(CartLine(id, 2.0)) } }

        assertEquals(1.0, stockOf(id), 0.0)
        assertEquals(0, count("orders"))
        assertEquals(0, count("payments"))
    }

    @Test
    fun aTrackedProductAtZeroCannotBeSold() = runBlocking {
        val id = tracked("Cerveja", 800, 0.0)

        assertThrows(InsufficientStockException::class.java) { runBlocking { quickSale(CartLine(id, 1.0)) } }

        assertEquals(0.0, stockOf(id), 0.0)
        assertEquals(0, count("orders"))
    }

    @Test
    fun twoCartLinesForTheSameProductAreCheckedAgainstTheirCombinedQuantity() = runBlocking {
        val id = tracked("Espetinho", 1250, 5.0)

        // 3 + 3 = 6 > 5. Each line alone fits; together they must not take stock below zero.
        assertThrows(InsufficientStockException::class.java) {
            runBlocking { quickSale(CartLine(id, 3.0), CartLine(id, 3.0)) }
        }

        assertEquals(5.0, stockOf(id), 0.0)
        assertEquals(0, count("orders"))
    }

    @Test
    fun twoCartLinesForTheSameProductThatFitAreMergedAndDecrementedOnce() = runBlocking {
        val id = tracked("Espetinho", 1250, 5.0)

        val orderId = quickSale(CartLine(id, 2.0), CartLine(id, 1.0))

        assertEquals(2.0, stockOf(id), 0.0)
        val items = database.orderItemDao().getItemsForOrder(orderId).first()
        assertEquals(1, items.size)
        assertEquals(3.0, items.single().quantity, 0.0)
        assertEquals(3750L, items.single().subtotalCents)
    }

    @Test
    fun aQuickSaleNeverChangesStockOfUntrackedProducts() = runBlocking {
        val id = untracked("Agua", 300)

        quickSale(CartLine(id, 4.0))

        assertEquals(0.0, stockOf(id), 0.0)
        assertEquals(false, products.getById(id)!!.trackStock)
    }

    @Test
    fun aQuickSaleStillAttachesItsPaymentToTheOpenCashSession() = runBlocking {
        val id = tracked("Espetinho", 1250, 10.0)
        val sessionId = cash.openSession(0)

        val orderId = quickSale(CartLine(id, 1.0))

        val payment = database.paymentDao().getForOrder(orderId).first().single()
        assertEquals(sessionId, payment.cashSessionId)
        assertEquals(1250L, payment.amountCents)
    }

    // ---------------------------------------------------------------------------------------
    // Comanda
    // ---------------------------------------------------------------------------------------

    @Test
    fun aComandaReservesStockAsItemsAreAddedAndRestoresItAsTheyAreRemoved() = runBlocking {
        val id = tracked("Espetinho", 1250, 10.0)
        val comanda = orders.createComanda(null, "Mesa 1")

        orders.addComandaItem(comanda, id, 2.0)
        assertEquals(8.0, stockOf(id), 0.0)

        orders.decrementComandaItem(comanda, id)
        assertEquals(9.0, stockOf(id), 0.0)

        orders.decrementComandaItem(comanda, id)
        assertEquals(10.0, stockOf(id), 0.0)
        assertTrue(database.orderItemDao().getItemsForOrder(comanda).first().isEmpty())
    }

    @Test
    fun aTrackedProductAtZeroCannotBeAddedToAComanda() = runBlocking {
        val id = tracked("Cerveja", 800, 0.0)
        val comanda = orders.createComanda(null, "Mesa 1")

        assertThrows(InsufficientStockException::class.java) { runBlocking { orders.addComandaItem(comanda, id, 1.0) } }

        assertEquals(0.0, stockOf(id), 0.0)
        assertTrue(database.orderItemDao().getItemsForOrder(comanda).first().isEmpty())
    }

    @Test
    fun cancellingAComandaRestoresEverythingItHeld() = runBlocking {
        val a = tracked("Espetinho", 1250, 10.0)
        val b = tracked("Coca-Cola", 600, 5.0)
        val comanda = orders.createComanda(null, "Mesa 1")
        orders.addComandaItem(comanda, a, 3.0)
        orders.addComandaItem(comanda, b, 2.0)

        orders.cancelComanda(comanda)

        assertEquals(10.0, stockOf(a), 0.0)
        assertEquals(5.0, stockOf(b), 0.0)
        assertEquals(OrderStatus.CANCELLED, database.orderDao().getById(comanda)!!.status)
        assertEquals(0, count("payments"))
    }

    @Test
    fun closingAComandaWithAPaymentDoesNotDecrementStockASecondTime() = runBlocking {
        val id = tracked("Espetinho", 1250, 10.0)
        val comanda = orders.createComanda(null, "Mesa 1")
        orders.addComandaItem(comanda, id, 2.0)

        orders.closeOrderWithPayment(comanda, PaymentMethod.PIX)

        assertEquals(8.0, stockOf(id), 0.0)
    }

    @Test
    fun aClosedOrCancelledComandaCannotChangeStockAnyMore() = runBlocking {
        val id = tracked("Espetinho", 1250, 10.0)
        val closed = orders.createComanda(null, "Mesa 1")
        orders.addComandaItem(closed, id, 2.0)
        orders.closeOrderWithPayment(closed, PaymentMethod.CASH)
        val cancelled = orders.createComanda(null, "Mesa 2")
        orders.addComandaItem(cancelled, id, 1.0)
        orders.cancelComanda(cancelled)

        listOf(closed, cancelled).forEach { comanda ->
            assertThrows(IllegalStateException::class.java) { runBlocking { orders.addComandaItem(comanda, id, 1.0) } }
            assertThrows(IllegalStateException::class.java) { runBlocking { orders.decrementComandaItem(comanda, id) } }
            assertThrows(IllegalStateException::class.java) { runBlocking { orders.cancelComanda(comanda) } }
        }

        assertEquals(8.0, stockOf(id), 0.0) // 10 - 2 (closed); the cancelled one gave its 1 back.
    }

    // ---------------------------------------------------------------------------------------
    // Fiado and debt repayments never move stock
    // ---------------------------------------------------------------------------------------

    @Test
    fun fiadoAndItsRepaymentsNeverMoveStock() = runBlocking {
        val id = tracked("Espetinho", 1000, 10.0)
        val customer = customers.create("Joao", null, null)
        val comanda = orders.createComanda(customer, "Mesa 4")
        orders.addComandaItem(comanda, id, 3.0)
        assertEquals(7.0, stockOf(id), 0.0)

        orders.closeOrderAsFiado(comanda)
        assertEquals(7.0, stockOf(id), 0.0)

        val debtId = database.debtDao().getForCustomer(customer).first().single().id
        debts.registerPayment(debtId, 1000, PaymentMethod.CASH)
        assertEquals(7.0, stockOf(id), 0.0)
        debts.registerPayment(debtId, 2000, PaymentMethod.PIX)
        assertEquals(7.0, stockOf(id), 0.0)

        assertEquals(DebtStatus.PAID, database.debtDao().getById(debtId)!!.status)
        assertEquals(0, count("payments"))
    }

    // ---------------------------------------------------------------------------------------
    // Cash register is independent of stock
    // ---------------------------------------------------------------------------------------

    @Test
    fun stockCorrectionsNeverMoveTheCashRegister() = runBlocking {
        val id = tracked("Espetinho", 1250, 10.0)
        val sessionId = cash.openSession(10_000)
        quickSale(CartLine(id, 1.0))
        val before = cash.currentSummary(sessionId)

        products.adjustStock(id, 50.0)
        products.adjustStock(id, -20.0)
        products.setStock(id, 3.0)

        val after = cash.currentSummary(sessionId)
        assertEquals(before.totalsByMethod, after.totalsByMethod)
        assertEquals(before.totalReceivedCents, after.totalReceivedCents)
        assertEquals(before.expectedCashCents, after.expectedCashCents)
        assertEquals(1, count("payments"))
    }

    // ---------------------------------------------------------------------------------------
    // The whole small-business day, end to end
    // ---------------------------------------------------------------------------------------

    @Test
    fun aWholeDayOfStockSalesFiadoAndCashStaysConsistent() = runBlocking {
        val espetinho = tracked("Espetinho de Carne", 1250, 10.0)
        val coca = tracked("Coca-Cola", 600, 5.0)
        val agua = untracked("Agua", 300)
        val joao = customers.create("Joao Silva", null, null)

        // Stock management.
        products.adjustStock(espetinho, 5.0)
        assertEquals(15.0, stockOf(espetinho), 0.0)
        products.adjustStock(espetinho, -2.0)
        assertEquals(13.0, stockOf(espetinho), 0.0)
        assertThrows(StockAdjustmentException::class.java) { runBlocking { products.adjustStock(espetinho, -14.0) } }
        assertEquals(13.0, stockOf(espetinho), 0.0)

        // Quick Sale: 2 Espetinho + 1 Coca + 1 Agua, paid in cash.
        quickSale(CartLine(espetinho, 2.0), CartLine(coca, 1.0), CartLine(agua, 1.0))
        assertEquals(11.0, stockOf(espetinho), 0.0)
        assertEquals(4.0, stockOf(coca), 0.0)
        assertEquals(0.0, stockOf(agua), 0.0)

        // Comanda for Joao closed as Fiado: 2 Espetinho + 1 Coca, one Espetinho removed and re-added.
        val mesa4 = orders.createComanda(joao, "Mesa 4")
        orders.addComandaItem(mesa4, espetinho, 2.0)
        orders.addComandaItem(mesa4, coca, 1.0)
        assertEquals(9.0, stockOf(espetinho), 0.0)
        assertEquals(3.0, stockOf(coca), 0.0)
        orders.decrementComandaItem(mesa4, espetinho)
        assertEquals(10.0, stockOf(espetinho), 0.0)
        orders.addComandaItem(mesa4, espetinho, 1.0)
        assertEquals(9.0, stockOf(espetinho), 0.0)
        orders.closeOrderAsFiado(mesa4)
        assertEquals(9.0, stockOf(espetinho), 0.0)
        assertEquals(3.0, stockOf(coca), 0.0)

        // Fiado: R$ 31,00 owed; R$ 10 cash before any cash session is open.
        val debt = database.debtDao().getForCustomer(joao).first().single()
        assertEquals(3100L, debt.originalAmountCents)
        debts.registerPayment(debt.id, 1000, PaymentMethod.CASH)
        assertNull(database.debtPaymentDao().getForDebt(debt.id).first().single().cashSessionId)

        // Cash session: R$ 100 opening, R$ 5 cash Fiado payment, a R$ 6 PIX sale, then the rest of the debt.
        val session = cash.openSession(10_000)
        debts.registerPayment(debt.id, 500, PaymentMethod.CASH)
        var summary = cash.currentSummary(session)
        assertEquals(10_500L, summary.expectedCashCents)
        quickSale(CartLine(coca, 1.0), method = PaymentMethod.PIX)
        summary = cash.currentSummary(session)
        assertEquals(1_100L, summary.totalReceivedCents) // 5,00 cash Fiado + 6,00 PIX sale
        assertEquals(10_500L, summary.expectedCashCents) // PIX never touches the drawer
        debts.registerPayment(debt.id, 1600, PaymentMethod.CASH)
        assertEquals(DebtStatus.PAID, database.debtDao().getById(debt.id)!!.status)
        val closed = cash.closeSession(session, countedCashCents = 12_100)
        assertEquals(12_100L, closed.expectedCashCents) // 100,00 + 5,00 + 16,00
        assertEquals(0L, closed.differenceCents)

        // Cancelling a Comanda gives every reserved unit back.
        val before = stockOf(espetinho) to stockOf(coca)
        val mesa5 = orders.createComanda(null, "Mesa 5")
        orders.addComandaItem(mesa5, espetinho, 2.0)
        orders.addComandaItem(mesa5, coca, 1.0)
        orders.cancelComanda(mesa5)
        assertEquals(before, stockOf(espetinho) to stockOf(coca))
    }

    // ---------------------------------------------------------------------------------------
    // Stock control cannot be switched on/off while the product is on an open Comanda: whether an
    // item reserved stock depends on the setting when it was added, so a later flip would make
    // cancelling or editing the Comanda restore stock that was never taken.
    // ---------------------------------------------------------------------------------------

    private suspend fun setTracking(id: Long, track: Boolean, quantity: Double = 0.0) =
        products.update(products.getById(id)!!, StockConfig(track, quantity))

    @Test
    fun enablingStockControlOnAProductOnAnOpenComandaIsRejectedAndChangesNothing() = runBlocking<Unit> {
        val espetinho = untracked("Espetinho", 1000)
        val mesa = orders.createComanda(null, "Mesa 1")
        orders.addComandaItem(mesa, espetinho, 3.0)

        val error = assertThrows(StockTrackingLockedException::class.java) {
            runBlocking { setTracking(espetinho, true, 10.0) }
        }

        assertEquals(espetinho, error.productId)
        val after = products.getById(espetinho)!!
        assertEquals(false, after.trackStock)
        assertEquals(0.0, after.stockQuantity, 0.0)
    }

    @Test
    fun disablingStockControlOnAProductOnAnOpenComandaIsRejectedAndKeepsTheReservation() = runBlocking<Unit> {
        val coca = tracked("Coca", 500, 10.0)
        val mesa = orders.createComanda(null, "Mesa 1")
        orders.addComandaItem(mesa, coca, 3.0) // 10 -> 7

        assertThrows(StockTrackingLockedException::class.java) {
            runBlocking { setTracking(coca, false) }
        }

        val after = products.getById(coca)!!
        assertEquals(true, after.trackStock)
        assertEquals(7.0, after.stockQuantity, 0.0)
        orders.cancelComanda(mesa)
        assertEquals(10.0, stockOf(coca), 0.0)
    }

    @Test
    fun aRejectedFlipLeavesNoPhantomStockWhenTheComandaIsCancelledOrEdited() = runBlocking<Unit> {
        val espetinho = untracked("Espetinho", 1000)
        val mesa = orders.createComanda(null, "Mesa 1")
        orders.addComandaItem(mesa, espetinho, 3.0)
        assertThrows(StockTrackingLockedException::class.java) { runBlocking { setTracking(espetinho, true, 10.0) } }

        orders.decrementComandaItem(mesa, espetinho)
        orders.cancelComanda(mesa)

        val after = products.getById(espetinho)!!
        assertEquals(false, after.trackStock)
        assertEquals(0.0, after.stockQuantity, 0.0)
    }

    @Test
    fun stockControlCanBeSwitchedOnceTheComandaIsCancelled() = runBlocking<Unit> {
        val espetinho = untracked("Espetinho", 1000)
        val mesa = orders.createComanda(null, "Mesa 1")
        orders.addComandaItem(mesa, espetinho, 3.0)
        orders.cancelComanda(mesa)

        setTracking(espetinho, true, 10.0)

        val after = products.getById(espetinho)!!
        assertEquals(true, after.trackStock)
        assertEquals(10.0, after.stockQuantity, 0.0)
    }

    @Test
    fun stockControlCanBeSwitchedOnceTheComandaIsClosed() = runBlocking<Unit> {
        val espetinho = untracked("Espetinho", 1000)
        val mesa = orders.createComanda(null, "Mesa 1")
        orders.addComandaItem(mesa, espetinho, 3.0)
        orders.closeOrderWithPayment(mesa, PaymentMethod.CASH)

        setTracking(espetinho, true, 10.0)

        assertEquals(true, products.getById(espetinho)!!.trackStock)
    }

    @Test
    fun aQuickSaleNeverBlocksSwitchingStockControl() = runBlocking<Unit> {
        val espetinho = untracked("Espetinho", 1000)
        quickSale(CartLine(espetinho, 1.0))

        setTracking(espetinho, true, 10.0)

        assertEquals(true, products.getById(espetinho)!!.trackStock)
    }

    @Test
    fun anotherProductsOpenComandaDoesNotBlockSwitchingStockControl() = runBlocking<Unit> {
        val espetinho = untracked("Espetinho", 1000)
        val coca = untracked("Coca", 500)
        val mesa = orders.createComanda(null, "Mesa 1")
        orders.addComandaItem(mesa, coca, 1.0)

        setTracking(espetinho, true, 10.0)

        assertEquals(true, products.getById(espetinho)!!.trackStock)
    }

    @Test
    fun changingTheCountOfAnAlreadyTrackedProductOnAnOpenComandaIsStillAllowed() = runBlocking<Unit> {
        val coca = tracked("Coca", 500, 10.0)
        val mesa = orders.createComanda(null, "Mesa 1")
        orders.addComandaItem(mesa, coca, 3.0) // 10 -> 7

        setTracking(coca, true, 20.0)

        assertEquals(20.0, stockOf(coca), 0.0)
        orders.cancelComanda(mesa)
        assertEquals(23.0, stockOf(coca), 0.0)
    }

    @Test
    fun aDescriptiveEditWithoutStockChangesIsAllowedOnAnOpenComanda() = runBlocking<Unit> {
        val espetinho = untracked("Espetinho", 1000)
        val mesa = orders.createComanda(null, "Mesa 1")
        orders.addComandaItem(mesa, espetinho, 1.0)

        products.update(products.getById(espetinho)!!.copy(priceCents = 1200))

        assertEquals(1200L, products.getById(espetinho)!!.priceCents)
    }
}
