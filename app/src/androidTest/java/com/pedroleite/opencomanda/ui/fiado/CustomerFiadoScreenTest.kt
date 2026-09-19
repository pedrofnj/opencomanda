package com.pedroleite.opencomanda.ui.fiado

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import com.pedroleite.opencomanda.data.repository.DebtRepository
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.domain.DebtStatus
import com.pedroleite.opencomanda.domain.PaymentMethod
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies one customer's Fiado detail: the outstanding total, individual debts with their
 *  original/paid/remaining amounts and status, the not-found state, and that a debt for an
 *  inactive customer still appears (Fiado never hides money owed). */
@RunWith(AndroidJUnit4::class)
class CustomerFiadoScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var debtRepository: DebtRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var orderRepository: OrderRepository
    private var productId: Long = 0
    private var customerId: Long = 0

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(resId: Int): String = context.getString(resId)

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        debtRepository = DebtRepository(database, database.debtDao(), database.debtPaymentDao(), database.cashSessionDao())
        customerRepository = CustomerRepository(database.customerDao())
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
            ProductEntity(name = "Espetinho", priceCents = 1250, createdAt = now, updatedAt = now),
        )
        customerId = customerRepository.create(name = "Joao", phone = null, notes = null)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createDebt(quantity: Double = 1.0, displayName: String = "Mesa 1"): Long {
        val orderId = orderRepository.createComanda(customerId = customerId, displayName = displayName)
        orderRepository.addComandaItem(orderId, productId, quantity)
        orderRepository.closeOrderAsFiado(orderId)
        return database.debtDao().getForCustomer(customerId).first().first { it.orderId == orderId }.id
    }

    @Suppress("ViewModelConstructorInComposable")
    private fun setScreenContent(id: Long = customerId, onDebtSelected: (Long) -> Unit = {}): CustomerFiadoViewModel {
        val viewModel = CustomerFiadoViewModel(id, debtRepository, customerRepository, orderRepository)
        composeTestRule.setContent {
            OpenComandaTheme {
                CustomerFiadoScreen(customerId = id, onBack = {}, onDebtSelected = onDebtSelected, viewModel = viewModel)
            }
        }
        return viewModel
    }

    @Test
    fun aCustomerThatDoesNotExistShowsTheNotFoundState() {
        setScreenContent(id = 99_999L)

        waitForText(string(R.string.fiado_debt_not_found))
    }

    @Test
    fun aCustomerWithNoOutstandingDebtsShowsTheEmptyState() {
        setScreenContent()

        waitForText("Joao")
        waitForText(string(R.string.fiado_customer_no_debts))
    }

    @Test
    fun anOutstandingDebtShowsOriginalPaidRemainingAndStatus() = runBlocking {
        val debtId = createDebt(quantity = 2.0, displayName = "Mesa 4") // R$ 25,00
        debtRepository.registerPayment(debtId, amountCents = 1_000, method = PaymentMethod.CASH)

        val viewModel = setScreenContent()
        waitForText("Mesa 4")

        val debt = viewModel.uiState.value.debts.single()
        assertEquals(2_500L, debt.originalAmountCents)
        assertEquals(1_000L, debt.paidCents)
        assertEquals(1_500L, debt.remainingCents)
        assertEquals(DebtStatus.PARTIALLY_PAID, debt.status)
        assertEquals(1_500L, viewModel.uiState.value.totalOutstandingCents)
    }

    @Test
    fun tappingADebtInvokesTheCallbackWithItsId() = runBlocking {
        val debtId = createDebt()
        var selectedId: Long? = null

        setScreenContent(onDebtSelected = { selectedId = it })
        waitForText("Mesa 1")
        composeTestRule.onNodeWithTag(CustomerFiadoTestTags.debtRow(debtId)).performClick()

        assertEquals(debtId, selectedId)
    }

    @Test
    fun aFullySettledDebtLeavesTheOperationalListButTheCustomerStillShows() = runBlocking {
        val debtId = createDebt()
        debtRepository.registerPayment(debtId, amountCents = 1_250, method = PaymentMethod.CASH)

        setScreenContent()

        waitForText("Joao")
        waitForText(string(R.string.fiado_customer_no_debts))
    }

    @Test
    fun anInactiveCustomersOutstandingDebtRemainsVisible() = runBlocking {
        createDebt()
        customerRepository.setActive(customerId, false)

        setScreenContent()

        waitForText("Mesa 1")
    }
}
