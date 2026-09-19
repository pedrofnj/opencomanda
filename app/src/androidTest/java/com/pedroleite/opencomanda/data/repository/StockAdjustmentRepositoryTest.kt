package com.pedroleite.opencomanda.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.entity.CategoryEntity
import com.pedroleite.opencomanda.domain.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies manual stock correction: the amount is applied to the database's own current value
 * inside a transaction, validation lives in the repository (not just the UI), only the stock
 * figure is ever written, and a correction never touches orders, payments, debts or cash.
 */
@RunWith(AndroidJUnit4::class)
class StockAdjustmentRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ProductRepository
    private lateinit var orderRepository: OrderRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = ProductRepository(database, database.productDao())
        orderRepository = OrderRepository(
            database = database,
            orderDao = database.orderDao(),
            orderItemDao = database.orderItemDao(),
            paymentDao = database.paymentDao(),
            debtDao = database.debtDao(),
            productDao = database.productDao(),
            cashSessionDao = database.cashSessionDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun tracked(name: String = "Espetinho", stock: Double = 10.0, categoryId: Long? = null): Long =
        repository.create(
            name = name,
            description = "Carne",
            priceCents = 1250,
            costCents = 700,
            trackStock = true,
            initialStockQuantity = stock,
            categoryId = categoryId,
        )

    private suspend fun untracked(name: String = "Agua"): Long = repository.create(
        name = name,
        description = null,
        priceCents = 300,
        costCents = null,
        trackStock = false,
        initialStockQuantity = 0.0,
    )

    private suspend fun stockOf(id: Long) = repository.getById(id)!!.stockQuantity

    private fun assertFails(expected: StockAdjustmentError, block: suspend () -> Unit) {
        val e = assertThrows(StockAdjustmentException::class.java) { runBlocking { block() } }
        assertEquals(expected, e.error)
    }

    // ---------------------------------------------------------------------------------------
    // Which products the Stock list shows
    // ---------------------------------------------------------------------------------------

    @Test
    fun theStockListContainsTrackedProductsOnly() = runBlocking {
        val trackedId = tracked()
        untracked()

        val list = repository.getTrackedStock().first()

        assertEquals(listOf(trackedId), list.map { it.id })
    }

    @Test
    fun anInactiveTrackedProductStaysInTheStockList() = runBlocking {
        val id = tracked()
        repository.setActive(id, false)

        assertEquals(listOf(id), repository.getTrackedStock().first().map { it.id })
    }

    @Test
    fun theStockListReactsToAnAdjustment() = runBlocking {
        val id = tracked(stock = 10.0)
        assertEquals(10.0, repository.getTrackedStock().first().single().stockQuantity, 0.0)

        repository.adjustStock(id, 5.0)

        assertEquals(15.0, repository.getTrackedStock().first().single().stockQuantity, 0.0)
    }

    // ---------------------------------------------------------------------------------------
    // Adjust by an amount
    // ---------------------------------------------------------------------------------------

    @Test
    fun addingStockIncreasesTheCurrentQuantity() = runBlocking {
        val id = tracked(stock = 10.0)

        val change = repository.adjustStock(id, 5.0)

        assertEquals(15.0, stockOf(id), 0.0)
        assertEquals(10.0, change.previousQuantity, 0.0)
        assertEquals(15.0, change.newQuantity, 0.0)
        assertEquals("Espetinho", change.productName)
    }

    @Test
    fun removingStockDecreasesTheCurrentQuantity() = runBlocking {
        val id = tracked(stock = 10.0)

        repository.adjustStock(id, -2.0)

        assertEquals(8.0, stockOf(id), 0.0)
    }

    @Test
    fun removingExactlyAllTheStockLeavesZero() = runBlocking {
        val id = tracked(stock = 4.0)

        repository.adjustStock(id, -4.0)

        assertEquals(0.0, stockOf(id), 0.0)
    }

    @Test
    fun fractionalAdjustmentsAreSupported() = runBlocking {
        val id = tracked(stock = 2.5)

        repository.adjustStock(id, 1.25)
        repository.adjustStock(id, -0.5)

        assertEquals(3.25, stockOf(id), 0.0)
    }

    @Test
    fun binaryFloatingPointArtifactsDoNotMakeAnExactRemovalNegative() = runBlocking {
        val id = tracked(stock = 0.0)
        repository.adjustStock(id, 0.1)
        repository.adjustStock(id, 0.2) // 0.1 + 0.2 is 0.30000000000000004 in binary floating point

        repository.adjustStock(id, -0.3)

        assertEquals(0.0, stockOf(id), 0.0)
    }

    // ---------------------------------------------------------------------------------------
    // Fresh state, not a stale screen
    // ---------------------------------------------------------------------------------------

    @Test
    fun theAdjustmentIsAppliedToTheFreshDatabaseValueNotAStaleOne() = runBlocking {
        val id = tracked(stock = 10.0)
        val shownOnScreen = stockOf(id) // The operator's screen says 10 ...

        orderRepository.confirmQuickSale(
            lines = listOf(CartLine(id, 2.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
        ) // ... a sale takes the real stock to 8 ...

        val change = repository.adjustStock(id, 5.0) // ... and the operator adds +5.

        assertEquals(10.0, shownOnScreen, 0.0)
        assertEquals(13.0, stockOf(id), 0.0) // 8 + 5, not the stale 10 + 5.
        assertEquals(8.0, change.previousQuantity, 0.0)
        assertEquals(13.0, change.newQuantity, 0.0)
    }

    @Test
    fun aRemovalThatWasValidOnScreenIsRejectedIfASaleHasSinceTakenTheStock() = runBlocking {
        val id = tracked(stock = 10.0)
        orderRepository.confirmQuickSale(
            lines = listOf(CartLine(id, 8.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
        ) // Real stock is now 2.

        assertFails(StockAdjustmentError.NEGATIVE_RESULT) { repository.adjustStock(id, -5.0) }

        assertEquals(2.0, stockOf(id), 0.0)
    }

    // ---------------------------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------------------------

    @Test
    fun aZeroAdjustmentIsRejected() = runBlocking {
        val id = tracked(stock = 10.0)

        assertFails(StockAdjustmentError.ZERO_ADJUSTMENT) { repository.adjustStock(id, 0.0) }

        assertEquals(10.0, stockOf(id), 0.0)
    }

    @Test
    fun removingMoreThanIsInStockIsRejectedAndChangesNothing() = runBlocking {
        val id = tracked(stock = 3.0)

        assertFails(StockAdjustmentError.NEGATIVE_RESULT) { repository.adjustStock(id, -3.5) }

        assertEquals(3.0, stockOf(id), 0.0)
    }

    @Test
    fun aNonFiniteAdjustmentIsRejected() = runBlocking {
        val id = tracked(stock = 3.0)

        assertFails(StockAdjustmentError.INVALID_QUANTITY) { repository.adjustStock(id, Double.NaN) }
        assertFails(StockAdjustmentError.INVALID_QUANTITY) { repository.adjustStock(id, Double.POSITIVE_INFINITY) }

        assertEquals(3.0, stockOf(id), 0.0)
    }

    @Test
    fun adjustingAMissingProductIsRejected() = runBlocking {
        assertFails(StockAdjustmentError.PRODUCT_NOT_FOUND) { repository.adjustStock(9_999L, 1.0) }
    }

    @Test
    fun adjustingAnUntrackedProductIsRejectedAndDoesNotStartTrackingIt() = runBlocking {
        val id = untracked()

        assertFails(StockAdjustmentError.NOT_TRACKED) { repository.adjustStock(id, 5.0) }

        val product = repository.getById(id)!!
        assertEquals(false, product.trackStock)
        assertEquals(0.0, product.stockQuantity, 0.0)
    }

    // ---------------------------------------------------------------------------------------
    // Inactive products, and nothing but the stock figure changes
    // ---------------------------------------------------------------------------------------

    @Test
    fun anInactiveTrackedProductCanStillBeCorrectedAndStaysInactive() = runBlocking {
        val id = tracked(stock = 10.0)
        repository.setActive(id, false)

        repository.adjustStock(id, 5.0)

        val product = repository.getById(id)!!
        assertEquals(15.0, product.stockQuantity, 0.0)
        assertEquals(false, product.active)
    }

    @Test
    fun anAdjustmentChangesOnlyTheStockFigure() = runBlocking {
        val categoryId = database.categoryDao().insert(
            CategoryEntity(
                name = "Espetinhos",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        val id = tracked(stock = 10.0, categoryId = categoryId)
        val before = repository.getById(id)!!

        repository.adjustStock(id, 5.0)
        repository.setStock(id, 7.0)

        val after = repository.getById(id)!!
        assertEquals(before.copy(stockQuantity = 7.0, updatedAt = after.updatedAt), after)
        assertEquals(before.name, after.name)
        assertEquals(before.priceCents, after.priceCents)
        assertEquals(before.categoryId, after.categoryId)
        assertEquals(before.active, after.active)
    }

    private fun rowCount(table: String): Int =
        database.query("SELECT COUNT(*) FROM $table", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    @Test
    fun anAdjustmentCreatesNoOrderPaymentDebtOrCashMovement() = runBlocking {
        val id = tracked(stock = 10.0)

        repository.adjustStock(id, 5.0)
        repository.adjustStock(id, -2.0)
        repository.setStock(id, 3.0)

        listOf("orders", "order_items", "payments", "debts", "debt_payments", "cash_sessions").forEach { table ->
            assertEquals("$table must stay empty", 0, rowCount(table))
        }
    }

    // ---------------------------------------------------------------------------------------
    // Set to a counted quantity
    // ---------------------------------------------------------------------------------------

    @Test
    fun settingStockReplacesTheCurrentQuantity() = runBlocking {
        val id = tracked(stock = 8.0)

        val change = repository.setStock(id, 7.0)

        assertEquals(7.0, stockOf(id), 0.0)
        assertEquals(8.0, change.previousQuantity, 0.0)
        assertEquals(7.0, change.newQuantity, 0.0)
    }

    @Test
    fun settingStockToZeroIsAllowed() = runBlocking {
        val id = tracked(stock = 8.0)

        repository.setStock(id, 0.0)

        assertEquals(0.0, stockOf(id), 0.0)
    }

    @Test
    fun settingANegativeOrNonFiniteStockIsRejected() = runBlocking {
        val id = tracked(stock = 8.0)

        assertFails(StockAdjustmentError.INVALID_QUANTITY) { repository.setStock(id, -1.0) }
        assertFails(StockAdjustmentError.INVALID_QUANTITY) { repository.setStock(id, Double.NaN) }

        assertEquals(8.0, stockOf(id), 0.0)
    }

    @Test
    fun settingStockOnAnUntrackedOrMissingProductIsRejected() = runBlocking {
        val id = untracked()

        assertFails(StockAdjustmentError.NOT_TRACKED) { repository.setStock(id, 5.0) }
        assertFails(StockAdjustmentError.PRODUCT_NOT_FOUND) { repository.setStock(9_999L, 5.0) }
    }

    @Test
    fun aCountedTotalIsAbsoluteEvenIfASaleChangedTheStockMeanwhile() = runBlocking {
        val id = tracked(stock = 8.0)
        orderRepository.confirmQuickSale(
            lines = listOf(CartLine(id, 1.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
        ) // 8 -> 7

        repository.setStock(id, 7.0) // The physical count says 7.

        assertEquals(7.0, stockOf(id), 0.0)
    }
}
