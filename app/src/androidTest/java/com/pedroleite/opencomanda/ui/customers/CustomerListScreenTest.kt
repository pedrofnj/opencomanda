package com.pedroleite.opencomanda.ui.customers

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
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Customers list against a real (in-memory) Room database: the empty state, search
 * by name/phone, and active/inactive filtering.
 */
@RunWith(AndroidJUnit4::class)
class CustomerListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: CustomerRepository

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
        repository = CustomerRepository(database.customerDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Constructing the ViewModel directly (bypassing the app-container-backed factory) is
    // deliberate here: it's what lets this test inject an isolated in-memory-database repository.
    @Suppress("ViewModelConstructorInComposable")
    private fun setListContent(onEditCustomer: (Long) -> Unit = {}) {
        composeTestRule.setContent {
            OpenComandaTheme {
                CustomerListScreen(
                    onBack = {},
                    onCreateCustomer = {},
                    onEditCustomer = onEditCustomer,
                    viewModel = CustomerListViewModel(repository),
                )
            }
        }
    }

    @Test
    fun showsEmptyStateWhenThereAreNoCustomers() {
        setListContent()

        val emptyTitle = string(R.string.customer_list_empty_title)
        waitForText(emptyTitle)
        composeTestRule.onNodeWithText(emptyTitle).assertExists()
    }

    @Test
    fun persistedCustomerAppearsInTheListWithItsPhone() = runBlocking {
        repository.create(name = "João Silva", phone = "(62) 99999-1234", notes = null)

        setListContent()

        waitForText("João Silva")
        composeTestRule.onNodeWithText("(62) 99999-1234").assertExists()
        Unit
    }

    @Test
    fun tappingACustomerRowOpensEdit() = runBlocking {
        val id = repository.create(name = "João Silva", phone = null, notes = null)
        var openedId: Long? = null

        setListContent(onEditCustomer = { openedId = it })
        waitForText("João Silva")
        composeTestRule.onNodeWithText("João Silva").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { openedId == id }
    }

    @Test
    fun searchingByFullNameFindsTheCustomer() = runBlocking {
        repository.create(name = "João Silva", phone = null, notes = null)
        repository.create(name = "Maria Souza", phone = null, notes = null)

        setListContent()
        waitForText("João Silva")

        composeTestRule.onNodeWithTag(CustomerListTestTags.SEARCH_FIELD).performTextInput("João Silva")

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Maria Souza").fetchSemanticsNodes().isEmpty()
        }
        // The search field's own typed text also matches "João Silva", so this only checks
        // for at least one match (the customer row) rather than requiring a single one.
        waitForText("João Silva")
    }

    @Test
    fun searchingByPartialCaseInsensitiveNameFindsTheCustomer() = runBlocking {
        repository.create(name = "João Silva", phone = null, notes = null)
        repository.create(name = "Maria Souza", phone = null, notes = null)

        setListContent()
        waitForText("João Silva")

        composeTestRule.onNodeWithTag(CustomerListTestTags.SEARCH_FIELD).performTextInput("silva")

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Maria Souza").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("João Silva").assertExists()
        Unit
    }

    @Test
    fun searchingByPhoneDigitsFindsAFormattedPhone() = runBlocking {
        repository.create(name = "João Silva", phone = "(62) 99999-1234", notes = null)
        repository.create(name = "Maria Souza", phone = "(11) 98888-0000", notes = null)

        setListContent()
        waitForText("João Silva")

        composeTestRule.onNodeWithTag(CustomerListTestTags.SEARCH_FIELD).performTextInput("999991234")

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Maria Souza").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("João Silva").assertExists()
        Unit
    }

    @Test
    fun searchWithNoMatchesShowsTheNoResultsState() = runBlocking {
        repository.create(name = "João Silva", phone = null, notes = null)

        setListContent()
        waitForText("João Silva")

        composeTestRule.onNodeWithTag(CustomerListTestTags.SEARCH_FIELD).performTextInput("Zzzz")

        waitForText(string(R.string.customer_list_no_results))
        composeTestRule.onNodeWithText("João Silva").assertDoesNotExist()
    }

    @Test
    fun filteringToInactiveShowsOnlyDeactivatedCustomers() = runBlocking {
        val activeId = repository.create(name = "Ativo", phone = null, notes = null)
        repository.create(name = "Inativo", phone = null, notes = null).let { repository.setActive(it, false) }

        setListContent()
        waitForText("Ativo")

        composeTestRule.onNodeWithTag(CustomerListTestTags.FILTER_INACTIVE).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Ativo").fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.onNodeWithText("Inativo").assertExists()
        assertTrue(repository.getById(activeId)!!.active)
    }

    @Test
    fun tappingTheStatusSwitchDeactivatesACustomer() = runBlocking {
        val id = repository.create(name = "Refrigerante", phone = null, notes = null)

        setListContent()
        waitForText("Refrigerante")

        val activeDescription = "Refrigerante: " + string(R.string.customer_status_active)
        composeTestRule.onNodeWithContentDescription(activeDescription).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { repository.getById(id)?.active == false }
        }
    }
}
