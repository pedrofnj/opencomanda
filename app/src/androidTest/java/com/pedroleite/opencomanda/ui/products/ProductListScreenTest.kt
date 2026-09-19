package com.pedroleite.opencomanda.ui.products

import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import com.pedroleite.opencomanda.data.repository.ProductRepository
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Products list against a real (in-memory) Room database: the empty state, and
 * that a persisted product actually shows up with its formatted price and stock.
 */
@RunWith(AndroidJUnit4::class)
class ProductListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: ProductRepository
    private lateinit var categoryRepository: CategoryRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(resId: Int): String = context.getString(resId)

    /** Waits for [text] to appear — Room's Flow emission can trail slightly behind
     *  setContent()'s own idle wait, so a plain assertion right after it can be flaky. */
    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = ProductRepository(database, database.productDao())
        categoryRepository = CategoryRepository(database.categoryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Constructing the ViewModel directly (bypassing the app-container-backed factory) is
    // deliberate here: it's what lets this test inject an isolated in-memory-database repository.
    @Suppress("ViewModelConstructorInComposable")
    private fun setListContent() {
        composeTestRule.setContent {
            OpenComandaTheme {
                ProductListScreen(
                    onBack = {},
                    onCreateProduct = {},
                    onEditProduct = {},
                    onManageCategories = {},
                    viewModel = ProductListViewModel(repository, categoryRepository),
                )
            }
        }
    }

    @Test
    fun showsEmptyStateWhenThereAreNoProducts() {
        setListContent()

        val emptyTitle = string(R.string.product_list_empty_title)
        waitForText(emptyTitle)
        composeTestRule.onNodeWithText(emptyTitle).assertExists()
    }

    @Test
    fun persistedProductAppearsInTheListWithFormattedPriceAndStock() = runBlocking {
        repository.create(
            name = "Espetinho",
            description = null,
            priceCents = 1050,
            costCents = null,
            trackStock = true,
            initialStockQuantity = 12.0,
        )

        setListContent()

        waitForText("Espetinho")
        composeTestRule.onNodeWithText("Espetinho").assertExists()
        composeTestRule.onNodeWithText(string(R.string.product_list_empty_title)).assertDoesNotExist()
    }

    @Test
    fun tappingTheStatusSwitchDeactivatesAProduct() = runBlocking {
        val id = repository.create(
            name = "Refrigerante",
            description = null,
            priceCents = 500,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
        )

        setListContent()
        waitForText("Refrigerante")

        val activeDescription = "Refrigerante: " + string(R.string.product_status_active)
        composeTestRule.onNodeWithContentDescription(activeDescription).performClick()

        // setActive() runs asynchronously in the ViewModel's coroutine scope, off Compose's own
        // idle/animation clock, so poll the repository rather than relying on waitForIdle().
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { repository.getById(id)?.active == false }
        }

        assertFalse(repository.getById(id)!!.active)
    }

    @Test
    fun productListShowsItsCategoryNameWhenAssigned() = runBlocking {
        val categoryId = categoryRepository.create("Espetinhos")
        repository.create(
            name = "Espetinho",
            description = null,
            priceCents = 1050,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
            categoryId = categoryId,
        )

        setListContent()

        waitForText("Espetinho")
        // The category name appears twice (filter chip + product row) — a plain onNodeWithText
        // requires a single match, so just confirm it shows up at all.
        waitForText("Espetinhos")
    }

    @Test
    fun filteringByACategoryShowsOnlyItsProducts() = runBlocking {
        val espetinhosId = categoryRepository.create("Espetinhos")
        categoryRepository.create("Bebidas")
        repository.create(
            name = "Espetinho",
            description = null,
            priceCents = 1050,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
            categoryId = espetinhosId,
        )
        repository.create(
            name = "Refrigerante",
            description = null,
            priceCents = 500,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
        )

        setListContent()
        waitForText("Espetinho")

        composeTestRule.onNodeWithTag(ProductListTestTags.categoryFilterChip(espetinhosId)).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Refrigerante").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("Espetinho").assertExists()
        composeTestRule.onNodeWithText("Refrigerante").assertDoesNotExist()
    }

    @Test
    fun filteringByNoCategoryShowsOnlyUncategorizedProducts() = runBlocking {
        val espetinhosId = categoryRepository.create("Espetinhos")
        repository.create(
            name = "Espetinho",
            description = null,
            priceCents = 1050,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
            categoryId = espetinhosId,
        )
        repository.create(
            name = "Refrigerante",
            description = null,
            priceCents = 500,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
        )

        setListContent()
        waitForText("Espetinho")

        composeTestRule.onNodeWithTag(ProductListTestTags.CATEGORY_FILTER_UNCATEGORIZED).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Espetinho").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("Refrigerante").assertExists()
        composeTestRule.onNodeWithText("Espetinho").assertDoesNotExist()
    }
}
