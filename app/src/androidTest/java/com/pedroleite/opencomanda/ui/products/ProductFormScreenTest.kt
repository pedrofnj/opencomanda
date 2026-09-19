package com.pedroleite.opencomanda.ui.products

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.data.repository.ProductRepository
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the shared Create/Edit product form against a real (in-memory) Room database: field
 * validation, that a new product is actually persisted, and that editing updates it in place.
 */
@RunWith(AndroidJUnit4::class)
class ProductFormScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: ProductRepository
    private lateinit var categoryRepository: CategoryRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(resId: Int): String = context.getString(resId)

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

    /** The edit form loads the product asynchronously, so the first frame shows empty fields:
     *  always wait for the loaded value before asserting on it or interacting. */
    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // Constructing the ViewModel directly (bypassing the app-container-backed factory) is
    // deliberate here: it's what lets this test inject an isolated in-memory-database repository.
    @Suppress("ViewModelConstructorInComposable")
    private fun setFormContent(productId: Long? = null, onSaved: () -> Unit = {}) {
        composeTestRule.setContent {
            OpenComandaTheme {
                ProductFormScreen(
                    productId = productId,
                    onBack = {},
                    onSaved = onSaved,
                    viewModel = ProductFormViewModel(repository, categoryRepository),
                )
            }
        }
    }

    @Test
    fun savingWithABlankNameShowsAValidationErrorAndDoesNotPersist() {
        setFormContent()

        composeTestRule.onNodeWithTag(ProductFormTestTags.SAVE_BUTTON).performClick()

        composeTestRule.onNodeWithText(string(R.string.product_field_name_error)).assertExists()
        assertTrue(runBlocking { repository.getAll().first() }.isEmpty())
    }

    @Test
    fun creatingAProductPersistsItAndCallsOnSaved() {
        var saved = false
        setFormContent(onSaved = { saved = true })

        composeTestRule.onNodeWithTag(ProductFormTestTags.NAME_FIELD).performTextInput("Espetinho")
        composeTestRule.onNodeWithTag(ProductFormTestTags.PRICE_FIELD).performTextInput("1250")
        composeTestRule.onNodeWithTag(ProductFormTestTags.SAVE_BUTTON).performClick()

        // Wait for onSaved itself (a Compose-side effect) rather than polling the repository
        // directly, which can observe the DB write slightly before the UI effect has run.
        composeTestRule.waitUntil(timeoutMillis = 5_000) { saved }

        val products = runBlocking { repository.getAll().first() }
        assertEquals(1, products.size)
        assertEquals("Espetinho", products.single().name)
        assertEquals(1250L, products.single().priceCents)
    }

    @Test
    fun enablingStockTrackingWithoutAQuantityShowsAValidationError() {
        setFormContent()

        composeTestRule.onNodeWithTag(ProductFormTestTags.NAME_FIELD).performTextInput("Cerveja")
        composeTestRule.onNodeWithTag(ProductFormTestTags.TRACK_STOCK_TOGGLE).performClick()
        composeTestRule.onNodeWithTag(ProductFormTestTags.SAVE_BUTTON).performClick()

        composeTestRule.onNodeWithText(string(R.string.product_field_stock_quantity_error)).assertExists()
        assertTrue(runBlocking { repository.getAll().first() }.isEmpty())
    }

    @Test
    fun trackedStockQuantityIsPersisted() {
        setFormContent()

        composeTestRule.onNodeWithTag(ProductFormTestTags.NAME_FIELD).performTextInput("Cerveja")
        composeTestRule.onNodeWithTag(ProductFormTestTags.TRACK_STOCK_TOGGLE).performClick()
        composeTestRule.onNodeWithTag(ProductFormTestTags.STOCK_QUANTITY_FIELD).performTextInput("24")
        composeTestRule.onNodeWithTag(ProductFormTestTags.SAVE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { repository.getAll().first() }.isNotEmpty()
        }

        val product = runBlocking { repository.getAll().first() }.single()
        assertTrue(product.trackStock)
        assertEquals(24.0, product.stockQuantity, 0.0)
    }

    @Test
    fun editingAnExistingProductKeepsUntouchedFieldsAndSaves() {
        val id = runBlocking {
            repository.create(
                name = "Costela",
                description = null,
                priceCents = 2000,
                costCents = null,
                trackStock = false,
                initialStockQuantity = 0.0,
            )
        }
        var saved = false
        setFormContent(productId = id, onSaved = { saved = true })

        waitForText("Costela")
        composeTestRule.onNodeWithTag(ProductFormTestTags.SAVE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { saved }
        val product = runBlocking { repository.getById(id) }
        assertEquals("Costela", product?.name)
        assertEquals(2000L, product?.priceCents)
    }

    private fun createTrackedProduct(stock: Double): Long = runBlocking {
        repository.create(
            name = "Cerveja",
            description = null,
            priceCents = 800,
            costCents = null,
            trackStock = true,
            initialStockQuantity = stock,
        )
    }

    @Test
    fun editingTheNameOfATrackedProductKeepsItsStock() {
        val id = createTrackedProduct(stock = 8.0)
        var saved = false
        setFormContent(productId = id, onSaved = { saved = true })
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Cerveja").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag(ProductFormTestTags.NAME_FIELD).performTextReplacement("Cerveja Lata")
        composeTestRule.onNodeWithTag(ProductFormTestTags.SAVE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { saved }
        val product = runBlocking { repository.getById(id)!! }
        assertEquals("Cerveja Lata", product.name)
        assertEquals(8.0, product.stockQuantity, 0.0)
    }

    @Test
    fun savingAnEditDoesNotUndoASaleMadeWhileTheFormWasOpen() {
        val id = createTrackedProduct(stock = 8.0)
        var saved = false
        setFormContent(productId = id, onSaved = { saved = true })
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Cerveja").fetchSemanticsNodes().isNotEmpty()
        }

        // While the form is open showing 8, stock drops to 5 elsewhere (a sale, a Comanda item, a count).
        runBlocking { repository.adjustStock(id, -3.0) }
        composeTestRule.onNodeWithTag(ProductFormTestTags.NAME_FIELD).performTextReplacement("Cerveja Lata")
        composeTestRule.onNodeWithTag(ProductFormTestTags.SAVE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { saved }
        val product = runBlocking { repository.getById(id)!! }
        assertEquals("Cerveja Lata", product.name)
        assertEquals(5.0, product.stockQuantity, 0.0)
    }

    @Test
    fun anIntentionalStockEditInTheFormIsStillSaved() {
        val id = createTrackedProduct(stock = 8.0)
        var saved = false
        setFormContent(productId = id, onSaved = { saved = true })
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Cerveja").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag(ProductFormTestTags.STOCK_QUANTITY_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(ProductFormTestTags.STOCK_QUANTITY_FIELD).performTextInput("20")
        composeTestRule.onNodeWithTag(ProductFormTestTags.SAVE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { saved }
        assertEquals(20.0, runBlocking { repository.getById(id)!! }.stockQuantity, 0.0)
    }

    @Test
    fun newProductDefaultsToNoCategory() {
        var saved = false
        setFormContent(onSaved = { saved = true })

        composeTestRule.onNodeWithTag(ProductFormTestTags.NAME_FIELD).performTextInput("Água")
        composeTestRule.onNodeWithTag(ProductFormTestTags.SAVE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { saved }
        val product = runBlocking { repository.getAll().first() }.single()
        assertEquals(null, product.categoryId)
    }

    @Test
    fun selectingACategoryInTheFormPersistsIt() = runBlocking {
        val categoryId = categoryRepository.create("Bebidas")
        var saved = false
        setFormContent(onSaved = { saved = true })

        composeTestRule.onNodeWithTag(ProductFormTestTags.NAME_FIELD).performTextInput("Suco")
        composeTestRule.onNodeWithTag(ProductFormTestTags.CATEGORY_FIELD).performClick()
        composeTestRule.onNodeWithText("Bebidas").performClick()
        composeTestRule.onNodeWithTag(ProductFormTestTags.SAVE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { saved }
        val product = repository.getAll().first().single()
        assertEquals(categoryId, product.categoryId)
    }

    @Test
    fun editingAProductKeepsItsCategoryWhenThatCategoryHasBeenDeactivated() = runBlocking {
        val categoryId = categoryRepository.create("Doses")
        categoryRepository.setActive(categoryId, false)
        val id = repository.create(
            name = "Whisky",
            description = null,
            priceCents = 1500,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
            categoryId = categoryId,
        )
        var saved = false
        setFormContent(productId = id, onSaved = { saved = true })

        // The now-inactive category is still shown, not silently dropped.
        val inactiveCategoryLabel = context.getString(R.string.product_field_category_inactive_suffix, "Doses")
        waitForText(inactiveCategoryLabel)

        composeTestRule.onNodeWithTag(ProductFormTestTags.SAVE_BUTTON).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { saved }

        assertEquals(categoryId, repository.getById(id)?.categoryId)
    }

    @Test
    fun enablingStockControlOnAProductOnAnOpenComandaShowsAnErrorAndChangesNothing() {
        val id = runBlocking {
            repository.create(
                name = "Espetinho",
                description = null,
                priceCents = 1000,
                costCents = null,
                trackStock = false,
                initialStockQuantity = 0.0,
            )
        }
        runBlocking {
            val orders = OrderRepository(
                database = database,
                orderDao = database.orderDao(),
                orderItemDao = database.orderItemDao(),
                paymentDao = database.paymentDao(),
                debtDao = database.debtDao(),
                productDao = database.productDao(),
                cashSessionDao = database.cashSessionDao(),
            )
            val comanda = orders.createComanda(customerId = null, displayName = "Mesa 1")
            orders.addComandaItem(comanda, id, 3.0)
        }
        var saved = false
        setFormContent(productId = id, onSaved = { saved = true })
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Espetinho").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag(ProductFormTestTags.TRACK_STOCK_TOGGLE).performClick()
        composeTestRule.onNodeWithTag(ProductFormTestTags.STOCK_QUANTITY_FIELD).performTextInput("10")
        composeTestRule.onNodeWithTag(ProductFormTestTags.SAVE_BUTTON).performClick()

        val errorText = string(R.string.product_stock_tracking_locked_error)
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(errorText).fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(saved)
        val product = runBlocking { repository.getById(id)!! }
        assertFalse(product.trackStock)
        assertEquals(0.0, product.stockQuantity, 0.0)
    }
}
