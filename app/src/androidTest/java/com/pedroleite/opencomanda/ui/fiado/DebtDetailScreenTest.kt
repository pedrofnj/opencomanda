package com.pedroleite.opencomanda.ui.fiado

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
import com.pedroleite.opencomanda.data.repository.CashRegisterRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the Debt detail / register-payment screen against a real (in-memory) Room database:
 *  validation (zero, overpayment, missing method), "Pagar tudo", partial vs. full settlement
 *  feedback, payment history, and that a registered payment is attached to whichever cash
 *  session is OPEN at that moment — never one the UI could supply itself. */
@RunWith(AndroidJUnit4::class)
class DebtDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var debtRepository: DebtRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var orderRepository: OrderRepository
    private var productId: Long = 0
    private var customerId: Long = 0
    private var debtId: Long = 0

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(resId: Int): String = context.getString(resId)
    private fun string(resId: Int, vararg args: Any): String = context.getString(resId, *args)

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
            ProductEntity(name = "Espetinho", priceCents = 1000, createdAt = now, updatedAt = now),
        )
        customerId = customerRepository.create(name = "Joao", phone = null, notes = null)

        val orderId = orderRepository.createComanda(customerId = customerId, displayName = "Mesa 4")
        orderRepository.addComandaItem(orderId, productId, 3.0) // R$ 30,00
        orderRepository.closeOrderAsFiado(orderId)
        debtId = database.debtDao().getForCustomer(customerId).first().single().id
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Suppress("ViewModelConstructorInComposable")
    private fun setScreenContent(id: Long = debtId, onBack: () -> Unit = {}): DebtDetailViewModel {
        val viewModel = DebtDetailViewModel(id, debtRepository, customerRepository, orderRepository)
        composeTestRule.setContent {
            OpenComandaTheme {
                DebtDetailScreen(debtId = id, onBack = onBack, viewModel = viewModel)
            }
        }
        return viewModel
    }

    @Test
    fun aDebtThatDoesNotExistShowsTheNotFoundState() {
        setScreenContent(id = 99_999L)

        waitForText(string(R.string.fiado_debt_not_found))
    }

    @Test
    fun showsTheOutstandingAmountAndAnEmptyHistoryInitially() {
        val viewModel = setScreenContent()

        waitForText(string(R.string.fiado_payment_history_empty))
        assertEquals(3_000L, viewModel.uiState.value.remainingCents)
    }

    @Test
    fun payAllPrefillsTheFullRemainingAmount() {
        val viewModel = setScreenContent()
        waitForText("Mesa 4")

        composeTestRule.onNodeWithTag(DebtDetailTestTags.PAY_ALL_BUTTON).performClick()

        assertEquals(3_000L, viewModel.uiState.value.amountCents)
    }

    @Test
    fun confirmingWithZeroAmountShowsAValidationErrorAndRegistersNothing() = runBlocking {
        setScreenContent()
        waitForText("Mesa 4")
        composeTestRule.onNodeWithTag(DebtDetailTestTags.paymentMethodChip(PaymentMethod.CASH)).performClick()

        composeTestRule.onNodeWithTag(DebtDetailTestTags.CONFIRM_BUTTON).performClick()

        waitForText(string(R.string.fiado_error_zero_amount))
        assertTrue(debtRepository.getPaymentsForDebt(debtId).first().isEmpty())
    }

    @Test
    fun confirmingWithoutAPaymentMethodShowsAValidationError() {
        val viewModel = setScreenContent()
        waitForText("Mesa 4")
        composeTestRule.onNodeWithTag(DebtDetailTestTags.AMOUNT_FIELD).performTextInput("1000")

        composeTestRule.onNodeWithTag(DebtDetailTestTags.CONFIRM_BUTTON).performClick()

        waitForText(string(R.string.fiado_error_payment_method_required))
        assertEquals(DebtDetailPhase.FORM, viewModel.uiState.value.phase)
    }

    @Test
    fun anOverpaymentAttemptIsRejectedAndLeavesTheRemainingBalanceUnchanged() = runBlocking {
        val viewModel = setScreenContent()
        waitForText("Mesa 4")
        composeTestRule.onNodeWithTag(DebtDetailTestTags.AMOUNT_FIELD).performTextInput("3001")
        composeTestRule.onNodeWithTag(DebtDetailTestTags.paymentMethodChip(PaymentMethod.CASH)).performClick()

        composeTestRule.onNodeWithTag(DebtDetailTestTags.CONFIRM_BUTTON).performClick()

        waitForText(string(R.string.fiado_error_overpayment))
        assertEquals(3_000L, viewModel.uiState.value.remainingCents)
        assertTrue(debtRepository.getPaymentsForDebt(debtId).first().isEmpty())
    }

    @Test
    fun aPartialPaymentShowsTheSuccessScreenWithTheRemainingBalance() = runBlocking {
        val viewModel = setScreenContent()
        waitForText("Mesa 4")
        composeTestRule.onNodeWithTag(DebtDetailTestTags.AMOUNT_FIELD).performTextInput("1000")
        composeTestRule.onNodeWithTag(DebtDetailTestTags.paymentMethodChip(PaymentMethod.CASH)).performClick()

        composeTestRule.onNodeWithTag(DebtDetailTestTags.CONFIRM_BUTTON).performClick()

        waitForText(string(R.string.fiado_payment_registered_title))
        composeTestRule.onNodeWithText(string(R.string.fiado_debt_settled_message)).assertDoesNotExist()
        val result = viewModel.uiState.value.lastPayment!!
        assertEquals(1_000L, result.amountCents)
        assertEquals(2_000L, result.remainingAfterCents)
        assertTrue(!result.settled)

        val debt = database.debtDao().getById(debtId)!!
        assertEquals(DebtStatus.PARTIALLY_PAID, debt.status)
    }

    @Test
    fun payingTheFullRemainingAmountShowsTheSettledMessage() = runBlocking {
        val viewModel = setScreenContent()
        waitForText("Mesa 4")
        composeTestRule.onNodeWithTag(DebtDetailTestTags.PAY_ALL_BUTTON).performClick()
        composeTestRule.onNodeWithTag(DebtDetailTestTags.paymentMethodChip(PaymentMethod.PIX)).performClick()

        composeTestRule.onNodeWithTag(DebtDetailTestTags.CONFIRM_BUTTON).performClick()

        waitForText(string(R.string.fiado_debt_settled_message))
        assertTrue(viewModel.uiState.value.lastPayment!!.settled)

        val debt = database.debtDao().getById(debtId)!!
        assertEquals(DebtStatus.PAID, debt.status)
    }

    @Test
    fun doneOnTheSuccessScreenInvokesOnBack() = runBlocking {
        var backInvoked = false
        setScreenContent(onBack = { backInvoked = true })
        waitForText("Mesa 4")
        composeTestRule.onNodeWithTag(DebtDetailTestTags.PAY_ALL_BUTTON).performClick()
        composeTestRule.onNodeWithTag(DebtDetailTestTags.paymentMethodChip(PaymentMethod.CASH)).performClick()
        composeTestRule.onNodeWithTag(DebtDetailTestTags.CONFIRM_BUTTON).performClick()
        waitForText(string(R.string.fiado_payment_registered_title))

        composeTestRule.onNodeWithTag(DebtDetailTestTags.DONE_BUTTON).performClick()

        assertTrue(backInvoked)
    }

    @Test
    fun existingPaymentHistoryIsVisibleOnTheForm() = runBlocking {
        debtRepository.registerPayment(debtId, amountCents = 500, method = PaymentMethod.PIX)

        setScreenContent()

        waitForText(string(R.string.payment_method_pix))
        composeTestRule.onNodeWithText(string(R.string.fiado_payment_history_empty)).assertDoesNotExist()
    }

    @Test
    fun aRegisteredPaymentIsAttachedToTheCurrentlyOpenCashSession() = runBlocking {
        val cashRegisterRepository = CashRegisterRepository(
            database,
            database.cashSessionDao(),
            database.paymentDao(),
            database.debtPaymentDao(),
        )
        val sessionId = cashRegisterRepository.openSession(0)

        setScreenContent()
        waitForText("Mesa 4")
        composeTestRule.onNodeWithTag(DebtDetailTestTags.PAY_ALL_BUTTON).performClick()
        composeTestRule.onNodeWithTag(DebtDetailTestTags.paymentMethodChip(PaymentMethod.CASH)).performClick()
        composeTestRule.onNodeWithTag(DebtDetailTestTags.CONFIRM_BUTTON).performClick()
        waitForText(string(R.string.fiado_payment_registered_title))

        val payment = debtRepository.getPaymentsForDebt(debtId).first().single()
        assertEquals(sessionId, payment.cashSessionId)
    }
}
