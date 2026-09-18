package com.pedroleite.opencomanda.ui.cashregister

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.data.repository.CartLine
import com.pedroleite.opencomanda.data.repository.CashRegisterRepository
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.domain.CashSessionStatus
import com.pedroleite.opencomanda.domain.PaymentMethod
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the Cash Register screen end to end against a real (in-memory) Room database: the
 *  closed/empty state, opening a session, the live dashboard reacting to payments recorded
 *  elsewhere (Quick Sale/Comanda), closing with a counted amount, and the final summary. */
@RunWith(AndroidJUnit4::class)
class CashRegisterScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var cashRegisterRepository: CashRegisterRepository
    private lateinit var orderRepository: OrderRepository
    private var productId: Long = 0

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(resId: Int): String = context.getString(resId)
    private fun string(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTextGone(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        cashRegisterRepository = CashRegisterRepository(
            database,
            database.cashSessionDao(),
            database.paymentDao(),
            database.debtPaymentDao(),
        )
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
            ProductEntity(name = "Agua", priceCents = 400, createdAt = now, updatedAt = now),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Suppress("ViewModelConstructorInComposable")
    private fun setScreenContent(onBack: () -> Unit = {}): CashRegisterViewModel {
        val viewModel = CashRegisterViewModel(cashRegisterRepository)
        composeTestRule.setContent {
            OpenComandaTheme {
                CashRegisterScreen(onBack = onBack, viewModel = viewModel)
            }
        }
        return viewModel
    }

    private suspend fun sell(method: PaymentMethod, quantity: Double = 1.0) {
        orderRepository.confirmQuickSale(
            lines = listOf(CartLine(productId, quantity)),
            method = method,
            isFiado = false,
            customerId = null,
        )
    }

    @Test
    fun showsTheEmptyStateWhenNoSessionIsOpen() {
        setScreenContent()

        waitForText(string(R.string.cash_register_empty_title))
        composeTestRule.onNodeWithTag(CashRegisterTestTags.OPEN_BUTTON).assertExists()
    }

    @Test
    fun tappingOpenShowsTheOpeningFormWithAZeroDefaultBalance() {
        setScreenContent()
        waitForText(string(R.string.cash_register_empty_title))

        composeTestRule.onNodeWithTag(CashRegisterTestTags.OPEN_BUTTON).performClick()

        waitForText(string(R.string.cash_register_confirm_open_action))
        composeTestRule.onNodeWithTag(CashRegisterTestTags.OPENING_BALANCE_FIELD).assertExists()
    }

    @Test
    fun openingWithAZeroBalanceOpensASessionAndShowsTheDashboard() = runBlocking {
        setScreenContent()
        waitForText(string(R.string.cash_register_empty_title))
        composeTestRule.onNodeWithTag(CashRegisterTestTags.OPEN_BUTTON).performClick()
        waitForText(string(R.string.cash_register_confirm_open_action))

        composeTestRule.onNodeWithTag(CashRegisterTestTags.CONFIRM_OPEN_BUTTON).performClick()

        waitForText(string(R.string.cash_register_close_action))
        val session = database.cashSessionDao().getByStatus(CashSessionStatus.OPEN)
        assertTrue(session != null)
        assertEquals(0L, session!!.openingBalanceCents)
    }

    @Test
    fun openingWithAPositiveBalancePersistsItAndShowsItInTheDashboard() = runBlocking {
        setScreenContent()
        waitForText(string(R.string.cash_register_empty_title))
        composeTestRule.onNodeWithTag(CashRegisterTestTags.OPEN_BUTTON).performClick()
        waitForText(string(R.string.cash_register_confirm_open_action))

        composeTestRule.onNodeWithTag(CashRegisterTestTags.OPENING_BALANCE_FIELD).performTextInput("10000")
        composeTestRule.onNodeWithTag(CashRegisterTestTags.CONFIRM_OPEN_BUTTON).performClick()

        waitForText(string(R.string.cash_register_close_action))
        val session = database.cashSessionDao().getByStatus(CashSessionStatus.OPEN)
        assertEquals(100_00L, session!!.openingBalanceCents)
        composeTestRule.onNodeWithText(string(R.string.cash_register_no_payments_message)).assertExists()
        Unit
    }

    @Test
    fun dashboardUpdatesReactivelyAsPaymentsAreRecordedElsewhere() = runBlocking {
        cashRegisterRepository.openSession(0)
        setScreenContent()
        waitForText(string(R.string.cash_register_no_payments_message))

        sell(PaymentMethod.CASH)

        waitForText(string(R.string.payment_method_cash))
        waitForTextGone(string(R.string.cash_register_no_payments_message))
    }

    @Test
    fun expectedCashExcludesNonCashPaymentsWhileTotalReceivedIncludesThem() = runBlocking {
        cashRegisterRepository.openSession(openingBalanceCents = 1_000)
        sell(PaymentMethod.PIX)
        val viewModel = setScreenContent()

        waitForText(string(R.string.payment_method_pix))
        // Expected cash stays exactly the opening balance — PIX never touches the drawer —
        // while total received counts it.
        assertEquals(1_000L, viewModel.uiState.value.expectedCashCents)
        assertEquals(400L, viewModel.uiState.value.totalReceivedCents)
    }

    @Test
    fun closingScreenShowsTheLiveDifferenceAsCountedCashIsTyped() = runBlocking {
        cashRegisterRepository.openSession(openingBalanceCents = 0)
        sell(PaymentMethod.CASH)
        val viewModel = setScreenContent()
        waitForText(string(R.string.payment_method_cash))

        composeTestRule.onNodeWithTag(CashRegisterTestTags.CLOSE_BUTTON).performClick()
        waitForText(string(R.string.cash_register_confirm_close_action))

        // Expected cash is 400 cents — typing a counted amount of 500 cents is 100 over.
        composeTestRule.onNodeWithTag(CashRegisterTestTags.COUNTED_CASH_FIELD).performTextInput("500")

        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.countedCashCents == 500L }
        assertEquals(100L, viewModel.uiState.value.liveDifferenceCents)
    }

    @Test
    fun confirmingCloseShowsTheSummaryAndPersistsTheClosedSession() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(openingBalanceCents = 0)
        sell(PaymentMethod.CASH)
        setScreenContent()
        waitForText(string(R.string.payment_method_cash))

        composeTestRule.onNodeWithTag(CashRegisterTestTags.CLOSE_BUTTON).performClick()
        waitForText(string(R.string.cash_register_confirm_close_action))
        composeTestRule.onNodeWithTag(CashRegisterTestTags.COUNTED_CASH_FIELD).performTextInput("400")
        composeTestRule.onNodeWithTag(CashRegisterTestTags.CONFIRM_CLOSE_BUTTON).performClick()

        waitForText(string(R.string.cash_register_closed_summary_title))
        waitForText(string(R.string.cash_register_difference_zero))

        val session = database.cashSessionDao().getById(sessionId)!!
        assertEquals(CashSessionStatus.CLOSED, session.status)
        assertEquals(400L, session.closingBalanceCents)
    }

    @Test
    fun afterClosingTheOperatorCanOpenASecondIndependentSession() = runBlocking {
        val firstId = cashRegisterRepository.openSession(0)
        sell(PaymentMethod.CASH)
        cashRegisterRepository.closeSession(firstId, countedCashCents = 400)
        setScreenContent()
        waitForText(string(R.string.cash_register_empty_title))

        composeTestRule.onNodeWithTag(CashRegisterTestTags.OPEN_BUTTON).performClick()
        waitForText(string(R.string.cash_register_confirm_open_action))
        composeTestRule.onNodeWithTag(CashRegisterTestTags.CONFIRM_OPEN_BUTTON).performClick()

        waitForText(string(R.string.cash_register_no_payments_message))
        val secondSession = database.cashSessionDao().getByStatus(CashSessionStatus.OPEN)
        assertTrue(secondSession != null && secondSession.id != firstId)
    }

    @Test
    fun doneOnTheSummaryScreenReturnsToTheEmptyState() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)
        setScreenContent()
        waitForText(string(R.string.cash_register_no_payments_message))
        composeTestRule.onNodeWithTag(CashRegisterTestTags.CLOSE_BUTTON).performClick()
        waitForText(string(R.string.cash_register_confirm_close_action))
        composeTestRule.onNodeWithTag(CashRegisterTestTags.CONFIRM_CLOSE_BUTTON).performClick()
        waitForText(string(R.string.cash_register_closed_summary_title))

        composeTestRule.onNodeWithTag(CashRegisterTestTags.DONE_BUTTON).performClick()

        waitForText(string(R.string.cash_register_empty_title))
        assertEquals(CashSessionStatus.CLOSED, database.cashSessionDao().getById(sessionId)!!.status)
    }
}
