package com.pedroleite.opencomanda.ui.stock

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.core.formatQuantity
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.repository.CartLine
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.data.repository.ProductRepository
import com.pedroleite.opencomanda.data.repository.StockConfig
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

/** Verifies the Stock screen end to end against a real (in-memory) Room database: which products
 *  it lists, the out-of-stock wording, searching, and the add / remove / set adjustment flow with
 *  its validation, feedback and reactive list. Asserts persisted values rather than pixels. */
@RunWith(AndroidJUnit4::class)
class StockScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var orderRepository: OrderRepository

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
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun tracked(name: String, stock: Double, categoryId: Long? = null): Long = productRepository.create(
        name = name, description = null, priceCents = 1000, costCents = null,
        trackStock = true, initialStockQuantity = stock, categoryId = categoryId,
    )

    private suspend fun untracked(name: String): Long = productRepository.create(
        name = name, description = null, priceCents = 300, costCents = null,
        trackStock = false, initialStockQuantity = 0.0,
    )

    private suspend fun stockOf(id: Long) = productRepository.getById(id)!!.stockQuantity

    @Suppress("ViewModelConstructorInComposable")
    private fun setScreenContent(onBack: () -> Unit = {}, onGoToProducts: () -> Unit = {}) {
        composeTestRule.setContent {
            OpenComandaTheme {
                StockScreen(
                    onBack = onBack,
                    onGoToProducts = onGoToProducts,
                    viewModel = StockViewModel(productRepository, categoryRepository),
                )
            }
        }
    }

    private fun openAdjustment(productId: Long, name: String) {
        waitForText(name)
        composeTestRule.onNodeWithTag(StockTestTags.adjustButton(productId)).performClick()
        waitForText(string(R.string.stock_adjust_title))
    }

    private fun typeAmountAndConfirm(amount: String) {
        composeTestRule.onNodeWithTag(StockTestTags.AMOUNT_FIELD).performTextInput(amount)
        composeTestRule.onNodeWithTag(StockTestTags.CONFIRM_BUTTON).performClick()
    }

    // ---------------------------------------------------------------------------------------
    // The list
    // ---------------------------------------------------------------------------------------

    @Test
    fun showsTheEmptyStateWhenNoProductTracksStock() = runBlocking {
        untracked("Agua")
        setScreenContent()

        waitForText(string(R.string.stock_empty_title))
        composeTestRule.onNodeWithText("Agua").assertDoesNotExist()
        Unit
    }

    @Test
    fun theEmptyStateLeadsToProductManagement() {
        var openedProducts = false
        setScreenContent(onGoToProducts = { openedProducts = true })
        waitForText(string(R.string.stock_empty_title))

        composeTestRule.onNodeWithTag(StockTestTags.GO_TO_PRODUCTS_BUTTON).performClick()

        assertTrue(openedProducts)
    }

    @Test
    fun listsTrackedProductsWithTheirQuantityAndLeavesUntrackedOnesOut() = runBlocking {
        tracked("Espetinho", 10.0)
        tracked("Coca-Cola", 5.0)
        untracked("Agua")
        setScreenContent()

        waitForText("Espetinho")
        composeTestRule.onNodeWithText(string(R.string.stock_current_label, "10")).assertExists()
        composeTestRule.onNodeWithText("Coca-Cola").assertExists()
        composeTestRule.onNodeWithText(string(R.string.stock_current_label, "5")).assertExists()
        composeTestRule.onNodeWithText("Agua").assertDoesNotExist()
        Unit
    }

    @Test
    fun aFractionalQuantityIsShownWithoutTrailingZeros() = runBlocking {
        tracked("Carne", 1.5)
        setScreenContent()

        waitForText("Carne")
        // Locale-dependent separator: assert on the number of digits shown, not the exact glyph.
        val shown = formatQuantity(1.5)
        composeTestRule.onNodeWithText(string(R.string.stock_current_label, shown)).assertExists()
        assertTrue(!shown.endsWith("0"))
        Unit
    }

    @Test
    fun anOutOfStockProductIsLabelledInWords() = runBlocking {
        tracked("Cerveja", 0.0)
        setScreenContent()

        waitForText("Cerveja")
        composeTestRule.onNodeWithText(string(R.string.stock_out_of_stock)).assertExists()
        Unit
    }

    @Test
    fun anInactiveTrackedProductIsListedAndMarkedInactive() = runBlocking {
        val id = tracked("Antigo", 3.0)
        productRepository.setActive(id, false)
        setScreenContent()

        waitForText("Antigo")
        composeTestRule.onNodeWithText(string(R.string.product_status_inactive)).assertExists()
        Unit
    }

    @Test
    fun searchNarrowsTheListByNameAndShowsANoResultsMessage() = runBlocking {
        tracked("Espetinho", 10.0)
        tracked("Coca-Cola", 5.0)
        setScreenContent()
        waitForText("Espetinho")

        composeTestRule.onNodeWithTag(StockTestTags.SEARCH_FIELD).performTextInput("coca")
        waitForTextGone("Espetinho")
        composeTestRule.onNodeWithText("Coca-Cola").assertExists()

        composeTestRule.onNodeWithTag(StockTestTags.SEARCH_FIELD).performTextInput("zzz")
        waitForText(string(R.string.stock_no_results))
        Unit
    }

    @Test
    fun searchAlsoMatchesTheCategoryName() = runBlocking {
        val categoryId = categoryRepository.create("Bebidas")
        tracked("Coca-Cola", 5.0, categoryId)
        tracked("Espetinho", 10.0)
        setScreenContent()
        waitForText("Espetinho")

        composeTestRule.onNodeWithTag(StockTestTags.SEARCH_FIELD).performTextInput("bebidas")

        waitForTextGone("Espetinho")
        composeTestRule.onNodeWithText("Coca-Cola").assertExists()
        Unit
    }

    @Test
    fun theListUpdatesReactivelyWhenAnotherOperationChangesStock() = runBlocking {
        val id = tracked("Espetinho", 10.0)
        setScreenContent()
        waitForText(string(R.string.stock_current_label, "10"))

        orderRepository.confirmQuickSale(listOf(CartLine(id, 2.0)), PaymentMethod.CASH, false, null)

        waitForText(string(R.string.stock_current_label, "8"))
    }

    @Test
    fun backOnTheListInvokesOnBack() {
        var backInvoked = false
        setScreenContent(onBack = { backInvoked = true })
        waitForText(string(R.string.stock_empty_title))

        composeTestRule.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        assertTrue(backInvoked)
    }

    // ---------------------------------------------------------------------------------------
    // Adjusting
    // ---------------------------------------------------------------------------------------

    @Test
    fun addingStockShowsTheResultAndTheListReflectsIt() = runBlocking {
        val id = tracked("Espetinho", 10.0)
        setScreenContent()
        openAdjustment(id, "Espetinho")

        typeAmountAndConfirm("5")

        waitForText(string(R.string.stock_updated_title))
        composeTestRule.onNodeWithText(string(R.string.stock_change_arrow, "10", "15")).assertExists()
        assertEquals(15.0, stockOf(id), 0.0)

        composeTestRule.onNodeWithTag(StockTestTags.DONE_BUTTON).performClick()
        waitForText(string(R.string.stock_current_label, "15"))
    }

    @Test
    fun removingStockShowsTheResult() = runBlocking {
        val id = tracked("Espetinho", 10.0)
        setScreenContent()
        openAdjustment(id, "Espetinho")
        composeTestRule.onNodeWithTag(StockTestTags.MODE_REMOVE).performClick()

        typeAmountAndConfirm("2")

        waitForText(string(R.string.stock_change_arrow, "10", "8"))
        assertEquals(8.0, stockOf(id), 0.0)
    }

    @Test
    fun settingACountedQuantityReplacesTheStock() = runBlocking {
        val id = tracked("Espetinho", 8.0)
        setScreenContent()
        openAdjustment(id, "Espetinho")
        composeTestRule.onNodeWithTag(StockTestTags.MODE_SET).performClick()

        typeAmountAndConfirm("7")

        waitForText(string(R.string.stock_change_arrow, "8", "7"))
        assertEquals(7.0, stockOf(id), 0.0)
    }

    @Test
    fun aDecimalCommaQuantityIsAccepted() = runBlocking {
        val id = tracked("Carne", 2.0)
        setScreenContent()
        openAdjustment(id, "Carne")

        typeAmountAndConfirm("1,5")

        waitForText(string(R.string.stock_updated_title))
        assertEquals(3.5, stockOf(id), 0.0)
    }

    @Test
    fun thePreviewShowsTheResultBeforeConfirming() = runBlocking {
        val id = tracked("Espetinho", 8.0)
        setScreenContent()
        openAdjustment(id, "Espetinho")

        composeTestRule.onNodeWithTag(StockTestTags.AMOUNT_FIELD).performTextInput("2")

        waitForText(string(R.string.stock_change_arrow, "8", "10"))
        assertEquals(8.0, stockOf(id), 0.0) // Nothing is written until the operator confirms.
    }

    @Test
    fun removingMoreThanIsInStockShowsAnErrorAndChangesNothing() = runBlocking {
        val id = tracked("Espetinho", 13.0)
        setScreenContent()
        openAdjustment(id, "Espetinho")
        composeTestRule.onNodeWithTag(StockTestTags.MODE_REMOVE).performClick()

        typeAmountAndConfirm("14")

        waitForText(string(R.string.stock_error_negative))
        // No confusing "13 -> -1" preview next to the error.
        composeTestRule.onNodeWithTag(StockTestTags.PREVIEW).assertDoesNotExist()
        assertEquals(13.0, stockOf(id), 0.0)
    }

    @Test
    fun aZeroAmountShowsAnErrorAndChangesNothing() = runBlocking {
        val id = tracked("Espetinho", 10.0)
        setScreenContent()
        openAdjustment(id, "Espetinho")

        typeAmountAndConfirm("0")

        waitForText(string(R.string.stock_error_zero))
        assertEquals(10.0, stockOf(id), 0.0)
    }

    @Test
    fun aBlankOrInvalidAmountShowsAnErrorAndChangesNothing() = runBlocking {
        val id = tracked("Espetinho", 10.0)
        setScreenContent()
        openAdjustment(id, "Espetinho")

        composeTestRule.onNodeWithTag(StockTestTags.CONFIRM_BUTTON).performClick()

        waitForText(string(R.string.product_field_stock_quantity_error))
        assertEquals(10.0, stockOf(id), 0.0)
    }

    @Test
    fun theAdjustmentIsAppliedToTheFreshStockWhenASaleHappensWhileTheScreenIsOpen() = runBlocking {
        val id = tracked("Espetinho", 10.0)
        setScreenContent()
        openAdjustment(id, "Espetinho")

        orderRepository.confirmQuickSale(listOf(CartLine(id, 2.0)), PaymentMethod.CASH, false, null) // 10 -> 8
        waitForText("8") // The adjust screen follows the live stock.

        typeAmountAndConfirm("5")

        waitForText(string(R.string.stock_change_arrow, "8", "13"))
        assertEquals(13.0, stockOf(id), 0.0)
    }

    @Test
    fun aProductThatStopsTrackingStockWhileAdjustingIsReportedNotChanged() = runBlocking {
        val id = tracked("Espetinho", 10.0)
        setScreenContent()
        openAdjustment(id, "Espetinho")

        productRepository.update(productRepository.getById(id)!!, StockConfig(false, 0.0))

        waitForText(string(R.string.stock_error_not_tracked))
        assertEquals(0.0, stockOf(id), 0.0)
    }

    @Test
    fun backFromTheAdjustmentReturnsToTheListWithoutChangingAnything() = runBlocking {
        val id = tracked("Espetinho", 10.0)
        setScreenContent()
        openAdjustment(id, "Espetinho")

        composeTestRule.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        waitForText(string(R.string.stock_current_label, "10"))
        assertEquals(10.0, stockOf(id), 0.0)
    }

    @Test
    fun anInactiveTrackedProductCanBeAdjustedAndStaysInactive() = runBlocking {
        val id = tracked("Antigo", 3.0)
        productRepository.setActive(id, false)
        setScreenContent()
        openAdjustment(id, "Antigo")

        typeAmountAndConfirm("2")

        waitForText(string(R.string.stock_change_arrow, "3", "5"))
        assertEquals(false, productRepository.getById(id)!!.active)
    }
}
