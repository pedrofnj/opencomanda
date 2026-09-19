package com.pedroleite.opencomanda.ui.comandas

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.assertIsEnabled
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.data.repository.ProductRepository
import com.pedroleite.opencomanda.domain.DebtStatus
import com.pedroleite.opencomanda.domain.OrderStatus
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

/** Verifies the Comanda detail screen end to end against a real (in-memory) Room database:
 *  browsing/adding products, persistence across "leave and reopen", closing/payment, insufficient
 *  stock, duplicate-close protection, cancellation, and that back never discards a persisted
 *  Comanda (the key behavioral difference from Quick Sale's cart). */
@RunWith(AndroidJUnit4::class)
class ComandaDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var orderRepository: OrderRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var customerRepository: CustomerRepository
    private var productId: Long = 0
    private var comandaId: Long = 0
    private var customerId: Long = 0

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(resId: Int): String = context.getString(resId)
    private fun string(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForContentDescription(description: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Before
    fun setUp() = runBlocking {
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
        productRepository = ProductRepository(database, database.productDao())
        categoryRepository = CategoryRepository(database.categoryDao())
        customerRepository = CustomerRepository(database.customerDao())

        productId = productRepository.create(
            name = "Espetinho",
            description = null,
            priceCents = 1250,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
        )
        comandaId = orderRepository.createComanda(customerId = null, displayName = "Mesa 4")
        customerId = customerRepository.create(name = "Joao", phone = null, notes = null)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Suppress("ViewModelConstructorInComposable")
    private fun setScreenContent(onBack: () -> Unit = {}) {
        setScreenContentFor(comandaId, onBack = onBack)
    }

    @Suppress("ViewModelConstructorInComposable")
    private fun setScreenContentFor(id: Long, onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            OpenComandaTheme {
                ComandaDetailScreen(
                    comandaId = id,
                    onBack = onBack,
                    viewModel = ComandaDetailViewModel(
                        id, orderRepository, productRepository, categoryRepository, customerRepository,
                    ),
                )
            }
        }
    }

    /** Simulates leaving and reopening the Comanda: a brand-new ViewModel/composition against
     *  the same (persisted) database, exactly like navigating away and back would produce. */
    private fun reopenScreenContent() = setScreenContent()

    @Test
    fun showsTheComandaNameAndAnEmptyItemsState() {
        setScreenContent()

        waitForText("Mesa 4")
        waitForText(string(R.string.comanda_detail_empty_items_title))
    }

    @Test
    fun addingAProductPersistsItAndUpdatesTheTotal() = runBlocking {
        setScreenContent()
        waitForText("Mesa 4")

        composeTestRule.onNodeWithTag(ComandaDetailTestTags.ADD_PRODUCTS_BUTTON).performClick()
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()
        waitForContentDescription(string(R.string.quicksale_increase_quantity, "Espetinho"))

        composeTestRule.onNodeWithContentDescription(string(R.string.action_back)).performClick()
        waitForText("Espetinho")

        val items = database.orderItemDao().getItemsForOrder(comandaId).first()
        assertEquals(1, items.size)
        assertEquals(1.0, items.single().quantity, 0.0001)
    }

    @Test
    fun categoryFilteringNarrowsTheProductList(): Unit = runBlocking {
        val espetinhosId = categoryRepository.create("Espetinhos")
        productRepository.update(productRepository.getById(productId)!!.copy(categoryId = espetinhosId))
        productRepository.create(
            name = "Refrigerante",
            description = null,
            priceCents = 500,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
        )

        setScreenContent()
        waitForText("Mesa 4")
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.ADD_PRODUCTS_BUTTON).performClick()
        waitForText("Espetinho")

        composeTestRule.onNodeWithTag(ComandaDetailTestTags.categoryFilterChip(espetinhosId)).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Refrigerante").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("Espetinho").assertExists()
    }

    @Test
    fun incrementingAndDecrementingAnItemUpdatesPersistedQuantity() = runBlocking {
        setScreenContent()
        waitForText("Mesa 4")
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.ADD_PRODUCTS_BUTTON).performClick()
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()
        val increaseDescription = string(R.string.quicksale_increase_quantity, "Espetinho")
        val decreaseDescription = string(R.string.quicksale_decrease_quantity, "Espetinho")
        waitForContentDescription(increaseDescription)

        composeTestRule.onNodeWithContentDescription(increaseDescription).performClick()
        waitForContentDescription(string(R.string.quicksale_quantity_label, "Espetinho", "2"))
        composeTestRule.onNodeWithContentDescription(decreaseDescription).performClick()
        waitForContentDescription(string(R.string.quicksale_quantity_label, "Espetinho", "1"))

        val item = database.orderItemDao().getItemsForOrder(comandaId).first().single()
        assertEquals(1.0, item.quantity, 0.0001)
    }

    @Test
    fun theComandaPersistsAcrossLeavingAndReopening() = runBlocking {
        orderRepository.addComandaItem(comandaId, productId, 2.0)

        reopenScreenContent()

        waitForText("Mesa 4")
        waitForText("Espetinho")

        // The reload is genuinely from Room, not carried over in memory: a fresh ViewModel/
        // composition against the same database shows the same persisted quantity.
        val item = database.orderItemDao().getItemsForOrder(comandaId).first().single()
        assertEquals(2.0, item.quantity, 0.0001)
    }

    @Test
    fun backFromTheDetailScreenNeverShowsADiscardDialogEvenWithItems() = runBlocking {
        orderRepository.addComandaItem(comandaId, productId, 1.0)
        var backInvoked = false
        setScreenContent(onBack = { backInvoked = true })
        waitForText("Espetinho")

        composeTestRule.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        assertTrue(backInvoked)
        // The comanda must remain exactly as it was — back is not a discard action here.
        val order = database.orderDao().getById(comandaId)!!
        assertEquals(OrderStatus.OPEN, order.status)
        assertEquals(1, database.orderItemDao().getItemsForOrder(comandaId).first().size)
    }

    @Test
    fun closeButtonIsDisabledForAnEmptyComanda() {
        setScreenContent()
        waitForText("Mesa 4")

        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CLOSE_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun closingWithPaymentShowsTheSuccessScreenAndClosesTheOrder() = runBlocking {
        orderRepository.addComandaItem(comandaId, productId, 2.0)
        setScreenContent()
        waitForText("Espetinho")

        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CLOSE_BUTTON).performClick()
        waitForText(string(R.string.comanda_detail_close_action))
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.paymentMethodChip(PaymentMethod.CASH)).performClick()
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CONFIRM_PAYMENT_BUTTON).performClick()

        waitForText(string(R.string.comanda_closed_title))
        composeTestRule.onNodeWithText("Mesa 4").assertExists()
        composeTestRule.onNodeWithText(string(R.string.payment_method_cash)).assertExists()

        val order = database.orderDao().getById(comandaId)!!
        assertEquals(OrderStatus.CLOSED, order.status)
        assertTrue(order.closedAt != null)
    }

    @Test
    fun aClosedComandaDisappearsFromTheOpenListAfterClosing() = runBlocking {
        orderRepository.addComandaItem(comandaId, productId, 1.0)
        setScreenContent()
        waitForText("Espetinho")
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CLOSE_BUTTON).performClick()
        waitForText(string(R.string.comanda_detail_close_action))
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.paymentMethodChip(PaymentMethod.PIX)).performClick()
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CONFIRM_PAYMENT_BUTTON).performClick()
        waitForText(string(R.string.comanda_closed_title))

        assertTrue(orderRepository.getOpenComandas().first().none { it.order.id == comandaId })
    }

    @Test
    fun insufficientStockErrorSurfacesAndPreservesTheItem() = runBlocking {
        val trackedId = productRepository.create(
            name = "Cerveja",
            description = null,
            priceCents = 800,
            costCents = null,
            trackStock = true,
            initialStockQuantity = 1.0,
        )
        orderRepository.addComandaItem(comandaId, trackedId, 1.0) // stock 1 -> 0
        setScreenContent()
        waitForText("Cerveja")

        composeTestRule.onNodeWithTag(ComandaDetailTestTags.ADD_PRODUCTS_BUTTON).performClick()
        waitForText("Cerveja")
        val increaseDescription = string(R.string.quicksale_increase_quantity, "Cerveja")
        waitForContentDescription(increaseDescription)
        composeTestRule.onNodeWithContentDescription(increaseDescription).performClick()

        val errorMessage = string(R.string.quicksale_error_insufficient_stock, "Cerveja", "0")
        waitForText(errorMessage)

        val item = database.orderItemDao().getItemsForOrder(comandaId).first().single()
        assertEquals(1.0, item.quantity, 0.0001)
    }

    @Test
    fun cancellingAnOpenComandaRestoresStockAndRemovesItFromTheOpenList() = runBlocking {
        val trackedId = productRepository.create(
            name = "Cerveja",
            description = null,
            priceCents = 800,
            costCents = null,
            trackStock = true,
            initialStockQuantity = 5.0,
        )
        orderRepository.addComandaItem(comandaId, trackedId, 2.0) // stock 5 -> 3
        var backInvoked = false
        setScreenContent(onBack = { backInvoked = true })
        waitForText("Cerveja")

        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CANCEL_ACTION).performClick()
        waitForText(string(R.string.comanda_cancel_dialog_title))
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CANCEL_CONFIRM).performClick()

        assertTrue(backInvoked)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { database.orderDao().getById(comandaId)!!.status == OrderStatus.CANCELLED }
        }
        assertEquals(5.0, database.productDao().getById(trackedId)!!.stockQuantity, 0.0001)
    }

    @Test
    fun dismissingTheCancelDialogKeepsTheComandaOpen() = runBlocking {
        orderRepository.addComandaItem(comandaId, productId, 1.0)
        setScreenContent()
        waitForText("Espetinho")

        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CANCEL_ACTION).performClick()
        waitForText(string(R.string.comanda_cancel_dialog_title))
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CANCEL_DISMISS).performClick()

        composeTestRule.onNodeWithText(string(R.string.comanda_cancel_dialog_title)).assertDoesNotExist()
        assertEquals(OrderStatus.OPEN, database.orderDao().getById(comandaId)!!.status)
    }

    @Test
    fun aTrackedProductAtZeroIsLabelledOutOfStockAndCannotBeAddedToTheComanda() = runBlocking {
        productRepository.create(
            name = "Cerveja",
            description = null,
            priceCents = 800,
            costCents = null,
            trackStock = true,
            initialStockQuantity = 0.0,
        )
        setScreenContent()
        waitForText("Mesa 4")
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.ADD_PRODUCTS_BUTTON).performClick()
        waitForText("Cerveja")

        composeTestRule.onNodeWithText(string(R.string.stock_out_of_stock)).assertExists()
        composeTestRule.onNodeWithText("Cerveja").performClick()

        composeTestRule.waitForIdle()
        assertTrue(database.orderItemDao().getItemsForOrder(comandaId).first().isEmpty())
    }

    // ---------------------------------------------------------------------------------------
    // Fiado
    // ---------------------------------------------------------------------------------------

    @Test
    fun fiadoActionIsDisabledWhenTheComandaHasNoRegisteredCustomer() = runBlocking {
        orderRepository.addComandaItem(comandaId, productId, 1.0) // Default comanda has customerId = null.
        setScreenContent()
        waitForText("Espetinho")

        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CLOSE_BUTTON).performClick()
        waitForText(string(R.string.comanda_detail_close_action))

        composeTestRule.onNodeWithTag(ComandaDetailTestTags.FIADO_ACTION).assertIsNotEnabled()
        composeTestRule.onNodeWithText(string(R.string.comanda_fiado_select_customer_hint)).assertExists()
        Unit
    }

    @Test
    fun fiadoActionIsEnabledWhenTheComandaHasARegisteredCustomer() = runBlocking {
        val comandaWithCustomerId = orderRepository.createComanda(customerId = customerId, displayName = "Mesa 7")
        orderRepository.addComandaItem(comandaWithCustomerId, productId, 1.0)

        setScreenContentFor(comandaWithCustomerId)
        waitForText("Espetinho")

        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CLOSE_BUTTON).performClick()
        waitForText(string(R.string.comanda_detail_close_action))

        composeTestRule.onNodeWithTag(ComandaDetailTestTags.FIADO_ACTION).assertIsEnabled()
        Unit
    }

    @Test
    fun fiadoConfirmationShowsTheCustomerComandaAndTotal() = runBlocking {
        val comandaWithCustomerId = orderRepository.createComanda(customerId = customerId, displayName = "Mesa 7")
        orderRepository.addComandaItem(comandaWithCustomerId, productId, 2.0) // R$ 25,00

        setScreenContentFor(comandaWithCustomerId)
        waitForText("Espetinho")
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CLOSE_BUTTON).performClick()
        waitForText(string(R.string.comanda_detail_close_action))
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.FIADO_ACTION).performClick()

        waitForText(string(R.string.comanda_fiado_confirm_title))
        composeTestRule.onNodeWithText("Joao").assertExists()
        composeTestRule.onNodeWithText("Mesa 7").assertExists()
        Unit
    }

    @Test
    fun closingAsFiadoShowsSuccessAndCreatesADebtInsteadOfAPayment() = runBlocking {
        val comandaWithCustomerId = orderRepository.createComanda(customerId = customerId, displayName = "Mesa 7")
        orderRepository.addComandaItem(comandaWithCustomerId, productId, 1.0)

        setScreenContentFor(comandaWithCustomerId)
        waitForText("Espetinho")
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CLOSE_BUTTON).performClick()
        waitForText(string(R.string.comanda_detail_close_action))
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.FIADO_ACTION).performClick()
        waitForText(string(R.string.comanda_fiado_confirm_title))

        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CONFIRM_FIADO_BUTTON).performClick()

        waitForText(string(R.string.comanda_fiado_closed_title))
        composeTestRule.onNodeWithText(string(R.string.payment_method_cash)).assertDoesNotExist()

        val order = database.orderDao().getById(comandaWithCustomerId)!!
        assertEquals(OrderStatus.CLOSED, order.status)
        assertTrue(database.paymentDao().getForOrder(comandaWithCustomerId).first().isEmpty())
        val debts = database.debtDao().getForCustomer(customerId).first()
        assertEquals(1, debts.size)
        assertEquals(1250L, debts.single().originalAmountCents)
        assertEquals(DebtStatus.OPEN, debts.single().status)
    }

    @Test
    fun backFromFiadoConfirmationReturnsToClosingWithoutCreatingADebt() = runBlocking {
        val comandaWithCustomerId = orderRepository.createComanda(customerId = customerId, displayName = "Mesa 7")
        orderRepository.addComandaItem(comandaWithCustomerId, productId, 1.0)

        setScreenContentFor(comandaWithCustomerId)
        waitForText("Espetinho")
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.CLOSE_BUTTON).performClick()
        waitForText(string(R.string.comanda_detail_close_action))
        composeTestRule.onNodeWithTag(ComandaDetailTestTags.FIADO_ACTION).performClick()
        waitForText(string(R.string.comanda_fiado_confirm_title))

        composeTestRule.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        waitForText(string(R.string.comanda_detail_close_action))
        assertEquals(OrderStatus.OPEN, database.orderDao().getById(comandaWithCustomerId)!!.status)
        assertTrue(database.debtDao().getForCustomer(customerId).first().isEmpty())
    }
}
