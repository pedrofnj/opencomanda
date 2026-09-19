package com.pedroleite.opencomanda.ui.quicksale

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.data.repository.ProductRepository
import com.pedroleite.opencomanda.domain.PaymentMethod
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Quick Sale screen end to end against a real (in-memory) Room database: product
 * display/filtering, cart manipulation, review/confirmation, success, error handling and the
 * discard-sale confirmation.
 */
@RunWith(AndroidJUnit4::class)
class QuickSaleScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var viewModel: QuickSaleViewModel

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(resId: Int): String = context.getString(resId)
    private fun string(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    /** Waits for [text] to appear — Room's Flow emission can trail slightly behind
     *  setContent()'s own idle wait, so a plain assertion right after it can be flaky. */
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
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        productRepository = ProductRepository(database, database.productDao())
        categoryRepository = CategoryRepository(database.categoryDao())
        orderRepository = OrderRepository(
            database = database,
            orderDao = database.orderDao(),
            orderItemDao = database.orderItemDao(),
            paymentDao = database.paymentDao(),
            debtDao = database.debtDao(),
            productDao = database.productDao(),
            cashSessionDao = database.cashSessionDao(),
        )
        viewModel = QuickSaleViewModel(productRepository, categoryRepository, orderRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Constructing the ViewModel directly (bypassing the app-container-backed factory) is
    // deliberate here: it's what lets this test inject an isolated in-memory-database repository.
    @Suppress("ViewModelConstructorInComposable")
    private fun setScreenContent(onBack: () -> Unit = {}, onGoToProducts: () -> Unit = {}) {
        composeTestRule.setContent {
            OpenComandaTheme {
                QuickSaleScreen(
                    onBack = onBack,
                    onGoToProducts = onGoToProducts,
                    viewModel = viewModel,
                )
            }
        }
    }

    private fun createProduct(
        name: String,
        priceCents: Long = 1000,
        trackStock: Boolean = false,
        initialStockQuantity: Double = 0.0,
        categoryId: Long? = null,
    ): Long = runBlocking {
        productRepository.create(
            name = name,
            description = null,
            priceCents = priceCents,
            costCents = null,
            trackStock = trackStock,
            initialStockQuantity = initialStockQuantity,
            categoryId = categoryId,
        )
    }

    @Test
    fun showsEmptyStateWhenThereAreNoActiveProducts() {
        setScreenContent()

        val emptyTitle = string(R.string.quicksale_empty_products_title)
        waitForText(emptyTitle)
        composeTestRule.onNodeWithText(emptyTitle).assertExists()
    }

    @Test
    fun tappingGoToProductsInEmptyStateInvokesTheCallback() {
        var invoked = false
        setScreenContent(onGoToProducts = { invoked = true })

        waitForText(string(R.string.quicksale_empty_products_title))
        composeTestRule.onNodeWithText(string(R.string.quicksale_empty_products_cta)).performClick()

        assertTrue(invoked)
    }

    @Test
    fun inactiveProductsAreNotDisplayed() = runBlocking {
        createProduct("Espetinho")
        val inactiveId = createProduct("Refrigerante")
        productRepository.setActive(inactiveId, false)

        setScreenContent()

        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Refrigerante").assertDoesNotExist()
    }

    @Test
    fun aTrackedProductAtZeroIsLabelledOutOfStockAndCannotBeAdded() = runBlocking {
        createProduct("Cerveja", trackStock = true, initialStockQuantity = 0.0)

        setScreenContent()

        waitForText("Cerveja")
        composeTestRule.onNodeWithText(string(R.string.stock_out_of_stock)).assertExists()
        composeTestRule.onNodeWithText("Cerveja").performClick()
        // Nothing was added: there is no cart bar and no quantity stepper.
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONTINUE_BUTTON).assertDoesNotExist()
        composeTestRule.onAllNodesWithContentDescription(string(R.string.quicksale_increase_quantity, "Cerveja"))
            .assertCountEquals(0)
        Unit
    }

    @Test
    fun aTrackedProductWithStockIsNotLabelledOutOfStock() = runBlocking {
        createProduct("Cerveja", trackStock = true, initialStockQuantity = 3.0)

        setScreenContent()

        waitForText("Cerveja")
        composeTestRule.onNodeWithText(string(R.string.stock_out_of_stock)).assertDoesNotExist()
        Unit
    }

    @Test
    fun anUntrackedProductIsNeverLabelledOutOfStock() = runBlocking {
        createProduct("Agua", trackStock = false)

        setScreenContent()

        waitForText("Agua")
        composeTestRule.onNodeWithText(string(R.string.stock_out_of_stock)).assertDoesNotExist()
        Unit
    }

    @Test
    fun filteringByCategoryShowsOnlyItsProducts() = runBlocking {
        val espetinhosId = categoryRepository.create("Espetinhos")
        createProduct("Espetinho", categoryId = espetinhosId)
        createProduct("Refrigerante")

        setScreenContent()
        waitForText("Espetinho")

        composeTestRule.onNodeWithTag(QuickSaleTestTags.categoryFilterChip(espetinhosId)).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Refrigerante").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("Espetinho").assertExists()
        composeTestRule.onNodeWithText("Refrigerante").assertDoesNotExist()
    }

    @Test
    fun filteringByUncategorizedShowsOnlyUncategorizedProducts() = runBlocking {
        val espetinhosId = categoryRepository.create("Espetinhos")
        createProduct("Espetinho", categoryId = espetinhosId)
        createProduct("Refrigerante")

        setScreenContent()
        waitForText("Espetinho")

        composeTestRule.onNodeWithTag(QuickSaleTestTags.CATEGORY_FILTER_UNCATEGORIZED).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Espetinho").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("Refrigerante").assertExists()
        composeTestRule.onNodeWithText("Espetinho").assertDoesNotExist()
    }

    @Test
    fun addingAProductShowsTheQuantityStepperAndTheCartSummaryBar(): Unit = runBlocking {
        createProduct("Espetinho")

        setScreenContent()
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()

        val increaseDescription = string(R.string.quicksale_increase_quantity, "Espetinho")
        waitForContentDescription(increaseDescription)
        composeTestRule.onNodeWithContentDescription(increaseDescription).assertExists()
        composeTestRule.onNodeWithContentDescription(
            string(R.string.quicksale_quantity_label, "Espetinho", "1"),
        ).assertExists()
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONTINUE_BUTTON).assertExists()
    }

    @Test
    fun incrementingAndDecrementingQuantityUpdatesTheCart() = runBlocking {
        createProduct("Espetinho")

        setScreenContent()
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()

        val increaseDescription = string(R.string.quicksale_increase_quantity, "Espetinho")
        val decreaseDescription = string(R.string.quicksale_decrease_quantity, "Espetinho")
        waitForContentDescription(increaseDescription)

        composeTestRule.onNodeWithContentDescription(increaseDescription).performClick()
        waitForContentDescription(string(R.string.quicksale_quantity_label, "Espetinho", "2"))

        composeTestRule.onNodeWithContentDescription(decreaseDescription).performClick()
        waitForContentDescription(string(R.string.quicksale_quantity_label, "Espetinho", "1"))
    }

    @Test
    fun decrementingTheLastUnitRemovesTheProductFromTheCart() = runBlocking {
        createProduct("Espetinho")

        setScreenContent()
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()

        val decreaseDescription = string(R.string.quicksale_decrease_quantity, "Espetinho")
        val addDescription = string(R.string.quicksale_add_action, "Espetinho")
        waitForContentDescription(decreaseDescription)

        composeTestRule.onNodeWithContentDescription(decreaseDescription).performClick()

        waitForContentDescription(addDescription)
        composeTestRule.onNodeWithContentDescription(decreaseDescription).assertDoesNotExist()
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONTINUE_BUTTON).assertDoesNotExist()
    }

    @Test
    fun proceedingToReviewShowsTheItemAndTheTotal(): Unit = runBlocking {
        createProduct("Espetinho", priceCents = 1250)

        setScreenContent()
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()
        waitForContentDescription(string(R.string.quicksale_increase_quantity, "Espetinho"))

        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONTINUE_BUTTON).performClick()

        waitForText(string(R.string.quicksale_review_title))
        composeTestRule.onNodeWithText("Espetinho").assertExists()
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONFIRM_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun confirmButtonIsDisabledUntilAPaymentMethodIsSelected(): Unit = runBlocking {
        createProduct("Espetinho")

        setScreenContent()
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()
        waitForContentDescription(string(R.string.quicksale_increase_quantity, "Espetinho"))
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONTINUE_BUTTON).performClick()
        waitForText(string(R.string.quicksale_review_title))

        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONFIRM_BUTTON).assertIsNotEnabled()

        composeTestRule.onNodeWithTag(QuickSaleTestTags.paymentMethodChip(PaymentMethod.CASH)).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.uiState.value.paymentMethod == PaymentMethod.CASH
        }
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONFIRM_BUTTON).assertIsEnabled()
    }

    @Test
    fun tappingBackOnReviewReturnsToSelectingWithTheCartIntact() = runBlocking {
        createProduct("Espetinho")

        setScreenContent()
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()
        waitForContentDescription(string(R.string.quicksale_increase_quantity, "Espetinho"))
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONTINUE_BUTTON).performClick()
        waitForText(string(R.string.quicksale_review_title))

        composeTestRule.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        waitForText(string(R.string.action_quick_sale))
        waitForContentDescription(string(R.string.quicksale_quantity_label, "Espetinho", "1"))
    }

    @Test
    fun confirmingASaleShowsTheSuccessScreenWithTheCorrectSummary() = runBlocking {
        createProduct("Espetinho", priceCents = 1250)

        setScreenContent()
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()
        waitForContentDescription(string(R.string.quicksale_increase_quantity, "Espetinho"))
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONTINUE_BUTTON).performClick()
        waitForText(string(R.string.quicksale_review_title))
        composeTestRule.onNodeWithTag(QuickSaleTestTags.paymentMethodChip(PaymentMethod.CASH)).performClick()

        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONFIRM_BUTTON).performClick()

        waitForText(string(R.string.quicksale_sale_completed))
        composeTestRule.onNodeWithText("Espetinho").assertExists()
        composeTestRule.onNodeWithText(string(R.string.payment_method_cash)).assertExists()

        val cursor = database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM orders")
        cursor.moveToFirst()
        val orderCount = cursor.getInt(0)
        cursor.close()
        assertTrue(orderCount == 1)
    }

    @Test
    fun newSaleFromTheSuccessScreenResetsToAnEmptyCart() = runBlocking {
        createProduct("Espetinho")

        setScreenContent()
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()
        waitForContentDescription(string(R.string.quicksale_increase_quantity, "Espetinho"))
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONTINUE_BUTTON).performClick()
        waitForText(string(R.string.quicksale_review_title))
        composeTestRule.onNodeWithTag(QuickSaleTestTags.paymentMethodChip(PaymentMethod.CASH)).performClick()
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONFIRM_BUTTON).performClick()
        waitForText(string(R.string.quicksale_sale_completed))

        composeTestRule.onNodeWithTag(QuickSaleTestTags.NEW_SALE_BUTTON).performClick()

        waitForText(string(R.string.action_quick_sale))
        composeTestRule.onNodeWithContentDescription(
            string(R.string.quicksale_add_action, "Espetinho"),
        ).assertExists()
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONTINUE_BUTTON).assertDoesNotExist()
    }

    @Test
    fun doneButtonOnTheSuccessScreenInvokesTheBackCallback() = runBlocking {
        createProduct("Espetinho")
        var backInvoked = false

        setScreenContent(onBack = { backInvoked = true })
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()
        waitForContentDescription(string(R.string.quicksale_increase_quantity, "Espetinho"))
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONTINUE_BUTTON).performClick()
        waitForText(string(R.string.quicksale_review_title))
        composeTestRule.onNodeWithTag(QuickSaleTestTags.paymentMethodChip(PaymentMethod.CASH)).performClick()
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONFIRM_BUTTON).performClick()
        waitForText(string(R.string.quicksale_sale_completed))

        composeTestRule.onNodeWithTag(QuickSaleTestTags.DONE_BUTTON).performClick()

        assertTrue(backInvoked)
    }

    @Test
    fun insufficientStockErrorSurfacesAndPreservesTheCart(): Unit = runBlocking {
        createProduct("Espetinho", trackStock = true, initialStockQuantity = 1.0)

        setScreenContent()
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()
        val increaseDescription = string(R.string.quicksale_increase_quantity, "Espetinho")
        waitForContentDescription(increaseDescription)
        composeTestRule.onNodeWithContentDescription(increaseDescription).performClick()
        waitForContentDescription(string(R.string.quicksale_quantity_label, "Espetinho", "2"))

        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONTINUE_BUTTON).performClick()
        waitForText(string(R.string.quicksale_review_title))
        composeTestRule.onNodeWithTag(QuickSaleTestTags.paymentMethodChip(PaymentMethod.CASH)).performClick()
        composeTestRule.onNodeWithTag(QuickSaleTestTags.CONFIRM_BUTTON).performClick()

        val errorMessage = string(R.string.quicksale_error_insufficient_stock, "Espetinho", "1")
        waitForText(errorMessage)

        // Still on Review (never reached Success), with the cart untouched — the failed sale
        // must not be lost. ("Confirmar venda" is ambiguous here: it's both the Review title and
        // the Confirm button's label, so check the Success-only text instead.)
        composeTestRule.onNodeWithText(string(R.string.quicksale_sale_completed)).assertDoesNotExist()
        composeTestRule.onNodeWithText("Espetinho").assertExists()
    }

    @Test
    fun backWithAnEmptyCartNavigatesImmediatelyWithoutADiscardDialog() {
        var backInvoked = false
        setScreenContent(onBack = { backInvoked = true })

        waitForText(string(R.string.action_quick_sale))
        composeTestRule.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        assertTrue(backInvoked)
        composeTestRule.onNodeWithText(string(R.string.quicksale_discard_sale_title)).assertDoesNotExist()
    }

    @Test
    fun backWithItemsInCartShowsADiscardDialogAndKeepSellingPreservesTheCart() = runBlocking {
        createProduct("Espetinho")
        var backInvoked = false

        setScreenContent(onBack = { backInvoked = true })
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()
        waitForContentDescription(string(R.string.quicksale_increase_quantity, "Espetinho"))

        composeTestRule.onNodeWithContentDescription(string(R.string.action_back)).performClick()
        waitForText(string(R.string.quicksale_discard_sale_title))

        composeTestRule.onNodeWithText(string(R.string.quicksale_keep_selling_action)).performClick()

        composeTestRule.onNodeWithText(string(R.string.quicksale_discard_sale_title)).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(
            string(R.string.quicksale_quantity_label, "Espetinho", "1"),
        ).assertExists()
        assertFalse(backInvoked)
    }

    @Test
    fun discardingTheSaleClearsTheCartAndInvokesTheBackCallback() = runBlocking {
        createProduct("Espetinho")
        var backInvoked = false

        setScreenContent(onBack = { backInvoked = true })
        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").performClick()
        waitForContentDescription(string(R.string.quicksale_increase_quantity, "Espetinho"))

        composeTestRule.onNodeWithContentDescription(string(R.string.action_back)).performClick()
        waitForText(string(R.string.quicksale_discard_sale_title))

        composeTestRule.onNodeWithText(string(R.string.quicksale_discard_action)).performClick()

        assertTrue(backInvoked)
        waitForContentDescription(string(R.string.quicksale_add_action, "Espetinho"))
    }
}
