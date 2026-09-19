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

/** Verifies the main Fiado screen against a real (in-memory) Room database: the empty state,
 *  and that it answers "who owes money?" — grouping debts by customer with a combined
 *  outstanding total — rather than listing raw debt rows. Asserts on the ViewModel's own
 *  numeric state rather than parsing locale-formatted currency text (see
 *  [com.pedroleite.opencomanda.ui.cashregister.CashRegisterScreenTest] for the same convention). */
@RunWith(AndroidJUnit4::class)
class FiadoScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var debtRepository: DebtRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var orderRepository: OrderRepository
    private var productId: Long = 0

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
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Creates a Comanda for [customerId], adds [quantity] of the test product, and closes it
     *  as Fiado — returning the id of the [com.pedroleite.opencomanda.data.local.entity.DebtEntity] created. */
    private suspend fun createDebtForCustomer(customerId: Long, quantity: Double = 1.0): Long {
        val orderId = orderRepository.createComanda(customerId = customerId, displayName = "Mesa 1")
        orderRepository.addComandaItem(orderId, productId, quantity)
        orderRepository.closeOrderAsFiado(orderId)
        return database.debtDao().getForCustomer(customerId).first().first { it.orderId == orderId }.id
    }

    @Suppress("ViewModelConstructorInComposable")
    private fun setScreenContent(onCustomerSelected: (Long) -> Unit = {}): FiadoViewModel {
        val viewModel = FiadoViewModel(debtRepository, customerRepository)
        composeTestRule.setContent {
            OpenComandaTheme {
                FiadoScreen(onBack = {}, onCustomerSelected = onCustomerSelected, viewModel = viewModel)
            }
        }
        return viewModel
    }

    @Test
    fun showsTheEmptyStateWhenNoCustomerOwesMoney() {
        setScreenContent()

        waitForText(string(R.string.fiado_empty_title))
    }

    @Test
    fun aCustomerWithAnOutstandingDebtAppearsWithTheCorrectTotal() = runBlocking {
        val customerId = customerRepository.create(name = "Joao", phone = null, notes = null)
        createDebtForCustomer(customerId, quantity = 2.0) // R$ 25,00

        val viewModel = setScreenContent()
        waitForText("Joao")

        val summary = viewModel.uiState.value.customers.single()
        assertEquals(customerId, summary.customerId)
        assertEquals(2_500L, summary.totalOutstandingCents)
        assertEquals(1, summary.debtCount)
    }

    @Test
    fun tappingACustomerInvokesTheCallbackWithTheirId() = runBlocking {
        val customerId = customerRepository.create(name = "Joao", phone = null, notes = null)
        createDebtForCustomer(customerId)
        var selectedId: Long? = null

        setScreenContent(onCustomerSelected = { selectedId = it })
        waitForText("Joao")
        composeTestRule.onNodeWithTag(FiadoTestTags.customerRow(customerId)).performClick()

        assertEquals(customerId, selectedId)
    }

    @Test
    fun aFullyPaidDebtNoLongerCountsTowardTheCustomersOutstandingTotal() = runBlocking {
        val customerId = customerRepository.create(name = "Joao", phone = null, notes = null)
        val debtId = createDebtForCustomer(customerId)
        debtRepository.registerPayment(debtId, amountCents = 1_250, method = PaymentMethod.CASH)

        setScreenContent()

        waitForText(string(R.string.fiado_empty_title))
    }

    @Test
    fun anInactiveCustomerWithAnOutstandingDebtStillAppears() = runBlocking {
        val customerId = customerRepository.create(name = "Joao", phone = null, notes = null)
        createDebtForCustomer(customerId)
        customerRepository.setActive(customerId, false)

        setScreenContent()

        waitForText("Joao")
    }

    @Test
    fun multipleDebtsForTheSameCustomerAreCombinedIntoOneSummaryRow() = runBlocking {
        val customerId = customerRepository.create(name = "Joao", phone = null, notes = null)
        createDebtForCustomer(customerId)
        createDebtForCustomer(customerId)

        val viewModel = setScreenContent()
        waitForText("Joao")

        val summary = viewModel.uiState.value.customers.single()
        assertEquals(2, summary.debtCount)
        assertEquals(2_500L, summary.totalOutstandingCents)
    }
}
