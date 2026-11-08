package com.pedroleite.opencomanda.data.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pedroleite.opencomanda.data.repository.CartLine
import com.pedroleite.opencomanda.data.repository.CashRegisterRepository
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import com.pedroleite.opencomanda.data.repository.DebtRepository
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.data.repository.ProductRepository
import com.pedroleite.opencomanda.domain.OrderStatus
import com.pedroleite.opencomanda.domain.PaymentMethod
import com.pedroleite.opencomanda.domain.auth.AuditAction
import com.pedroleite.opencomanda.domain.auth.Permission
import com.pedroleite.opencomanda.domain.auth.SystemRole
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
 * The business repositories enforce the matrix themselves: hiding a button is not the only guard.
 * Each test signs in as a built-in profile and calls the repository directly, as a bug or a crafted
 * route would.
 */
@RunWith(AndroidJUnit4::class)
class RepositoryAuthorizationTest {

    private val fixture = AuthTestFixture()
    private val db get() = fixture.database
    private val access get() = fixture.accessControl

    private val products by lazy { ProductRepository(db, db.productDao(), access) }
    private val categories by lazy { CategoryRepository(db.categoryDao(), access) }
    private val customers by lazy { CustomerRepository(db.customerDao()) }
    private val orders by lazy {
        OrderRepository(db, db.orderDao(), db.orderItemDao(), db.paymentDao(), db.debtDao(), db.productDao(), db.cashSessionDao(), access)
    }
    private val debts by lazy { DebtRepository(db, db.debtDao(), db.debtPaymentDao(), db.cashSessionDao(), access) }
    private val cash by lazy { CashRegisterRepository(db, db.cashSessionDao(), db.paymentDao(), db.debtPaymentDao(), access) }

    private var sellerId = 0L
    private var managerId = 0L
    private var productId = 0L
    private var customerId = 0L

    @Before
    fun setUp() = runBlocking {
        fixture.setUpOwner()
        sellerId = fixture.createUser("ana", SystemRole.SELLER)
        managerId = fixture.createUser("beto", SystemRole.MANAGER)
        productId = products.create("Espetinho", null, 1250, null, trackStock = true, initialStockQuantity = 20.0)
        customerId = customers.create("Carla", null, null)
    }

    @After
    fun tearDown() = fixture.close()

    private fun assertDenied(permission: Permission, block: suspend () -> Unit) {
        val error = assertThrows(PermissionDeniedException::class.java) { runBlocking { block() } }
        assertEquals(permission, error.permission)
    }

    @Test
    fun aSellerSellsAndServesButCannotCancelAdjustOrHandleMoney() = runBlocking {
        fixture.signInAs(sellerId)

        // Allowed.
        orders.confirmQuickSale(listOf(CartLine(productId, 1.0)), PaymentMethod.CASH, isFiado = false, customerId = null)
        val comanda = orders.createComanda(customerId, "Mesa 1")
        orders.addComandaItem(comanda, productId, 2.0)
        orders.decrementComandaItem(comanda, productId)

        // Refused, and nothing changed.
        assertDenied(Permission.SALES_CANCEL) { orders.cancelComanda(comanda) }
        assertDenied(Permission.FIADO_CHARGE) { orders.closeOrderAsFiado(comanda) }
        assertDenied(Permission.FIADO_CHARGE) {
            orders.confirmQuickSale(listOf(CartLine(productId, 1.0)), null, isFiado = true, customerId = customerId)
        }
        assertDenied(Permission.STOCK_ADJUST) { products.adjustStock(productId, 5.0) }
        assertDenied(Permission.STOCK_ADJUST) { products.setStock(productId, 50.0) }
        assertDenied(Permission.PRODUCTS_MANAGE) { products.create("Kafta", null, 1300, null, false, 0.0) }
        assertDenied(Permission.PRODUCTS_MANAGE) { products.setActive(productId, false) }
        assertDenied(Permission.PRODUCTS_MANAGE) { categories.create("Bebidas") }
        assertDenied(Permission.CASH_OPEN) { cash.openSession(10_000) }
        assertEquals(OrderStatus.OPEN, db.orderDao().getById(comanda)!!.status)
        assertEquals(18.0, db.productDao().getById(productId)!!.stockQuantity, 0.0)

        // The seller can still close with a payment.
        orders.closeOrderWithPayment(comanda, PaymentMethod.PIX)
        assertEquals(OrderStatus.CLOSED, db.orderDao().getById(comanda)!!.status)
    }

    @Test
    fun aSellerCannotReceiveFiadoOrCloseTheRegister() = runBlocking {
        fixture.signInAs(managerId)
        val sessionId = cash.openSession(5_000)
        val comanda = orders.createComanda(customerId, "Mesa 2")
        orders.addComandaItem(comanda, productId, 1.0)
        orders.closeOrderAsFiado(comanda)
        val debtId = db.debtDao().getForCustomer(customerId).first().single().id

        fixture.signInAs(sellerId)
        assertDenied(Permission.FIADO_RECEIVE) { debts.registerPayment(debtId, 500, PaymentMethod.CASH) }
        assertDenied(Permission.CASH_CLOSE) { cash.closeSession(sessionId, 5_000) }
    }

    @Test
    fun aManagerCanDoEverythingOnTheShopFloorAndItIsAudited() = runBlocking {
        fixture.signInAs(managerId)

        val sessionId = cash.openSession(10_000)
        val comanda = orders.createComanda(null, "Mesa 3")
        orders.addComandaItem(comanda, productId, 3.0)
        orders.cancelComanda(comanda)
        products.adjustStock(productId, 5.0)
        cash.closeSession(sessionId, 10_000)

        assertEquals(OrderStatus.CANCELLED, db.orderDao().getById(comanda)!!.status)
        assertEquals(25.0, db.productDao().getById(productId)!!.stockQuantity, 0.0)
        val entries = db.auditLogDao().getAll()
        listOf(AuditAction.CASH_OPENED, AuditAction.COMANDA_CANCELLED, AuditAction.STOCK_ADJUSTED, AuditAction.CASH_CLOSED).forEach { action ->
            assertTrue(action.name, entries.any { it.action == action && it.userId == managerId })
        }
    }

    @Test
    fun aLockedSessionCannotWriteAnything() {
        runBlocking { fixture.signInAs(managerId) }
        fixture.session.lock()
        assertDenied(Permission.SALES_CREATE) {
            orders.confirmQuickSale(listOf(CartLine(productId, 1.0)), PaymentMethod.CASH, isFiado = false, customerId = null)
        }
    }

    @Test
    fun aCustomProfileIsLimitedToWhatItWasGiven() = runBlocking {
        val roleId = fixture.users.createRole("Estoquista", setOf(Permission.STOCK_VIEW, Permission.STOCK_ADJUST))
        val id = fixture.users.createUser("Ivo", "ivo", "senha-ivo-12", "565656", roleId)
        fixture.signInAs(id, "565656")

        products.adjustStock(productId, 1.0)
        assertDenied(Permission.SALES_CREATE) {
            orders.confirmQuickSale(listOf(CartLine(productId, 1.0)), PaymentMethod.CASH, isFiado = false, customerId = null)
        }
        assertDenied(Permission.CASH_OPEN) { cash.openSession(0) }
    }
}
