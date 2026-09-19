package com.pedroleite.opencomanda.ui.customers

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.repository.CustomerRepository
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the shared Create/Edit customer form against a real (in-memory) Room database: field
 * validation, that a new customer is actually persisted, and that editing updates it in place.
 */
@RunWith(AndroidJUnit4::class)
class CustomerFormScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: CustomerRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(resId: Int): String = context.getString(resId)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = CustomerRepository(database.customerDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** The edit form loads the customer asynchronously, so the first frame shows empty fields:
     *  always wait for the loaded value before asserting on it or interacting. */
    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // Constructing the ViewModel directly (bypassing the app-container-backed factory) is
    // deliberate here: it's what lets this test inject an isolated in-memory-database repository.
    @Suppress("ViewModelConstructorInComposable")
    private fun setFormContent(customerId: Long? = null, onSaved: () -> Unit = {}) {
        composeTestRule.setContent {
            OpenComandaTheme {
                CustomerFormScreen(
                    customerId = customerId,
                    onBack = {},
                    onSaved = onSaved,
                    viewModel = CustomerFormViewModel(repository),
                )
            }
        }
    }

    @Test
    fun savingWithABlankNameShowsAValidationErrorAndDoesNotPersist() {
        setFormContent()

        composeTestRule.onNodeWithTag(CustomerFormTestTags.SAVE_BUTTON).performClick()

        composeTestRule.onNodeWithText(string(R.string.customer_field_name_error)).assertExists()
        assertTrue(runBlocking { repository.getAll().first() }.isEmpty())
    }

    @Test
    fun creatingACustomerPersistsItAndCallsOnSaved() {
        var saved = false
        setFormContent(onSaved = { saved = true })

        composeTestRule.onNodeWithTag(CustomerFormTestTags.NAME_FIELD).performTextInput("João Silva")
        composeTestRule.onNodeWithTag(CustomerFormTestTags.PHONE_FIELD).performTextInput("(62) 99999-1234")
        composeTestRule.onNodeWithTag(CustomerFormTestTags.NOTES_FIELD).performTextInput("Cliente frequente")
        composeTestRule.onNodeWithTag(CustomerFormTestTags.SAVE_BUTTON).performClick()

        // Wait for onSaved itself (a Compose-side effect) rather than polling the repository
        // directly, which can observe the DB write slightly before the UI effect has run.
        composeTestRule.waitUntil(timeoutMillis = 5_000) { saved }

        val customers = runBlocking { repository.getAll().first() }
        assertEquals(1, customers.size)
        val customer = customers.single()
        assertEquals("João Silva", customer.name)
        assertEquals("(62) 99999-1234", customer.phone)
        assertEquals("Cliente frequente", customer.notes)
    }

    @Test
    fun creatingACustomerWithoutPhoneOrNotesLeavesThemNull() {
        var saved = false
        setFormContent(onSaved = { saved = true })

        composeTestRule.onNodeWithTag(CustomerFormTestTags.NAME_FIELD).performTextInput("Maria")
        composeTestRule.onNodeWithTag(CustomerFormTestTags.SAVE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { saved }

        val customer = runBlocking { repository.getAll().first() }.single()
        assertNull(customer.phone)
        assertNull(customer.notes)
    }

    @Test
    fun editingAnExistingCustomerLoadsItsFieldsAndSaves() {
        val id = runBlocking {
            repository.create(name = "Costela", phone = "11999990000", notes = "Nota antiga")
        }
        var saved = false
        setFormContent(customerId = id, onSaved = { saved = true })

        waitForText("Costela")
        waitForText("11999990000")
        waitForText("Nota antiga")

        composeTestRule.onNodeWithTag(CustomerFormTestTags.NAME_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(CustomerFormTestTags.NAME_FIELD).performTextInput("Costela Premium")
        composeTestRule.onNodeWithTag(CustomerFormTestTags.SAVE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { saved }
        val customer = runBlocking { repository.getById(id) }
        assertEquals("Costela Premium", customer?.name)
        assertEquals("11999990000", customer?.phone)
    }

    @Test
    fun editingACustomerCanRemoveItsPhoneAndNotes() {
        val id = runBlocking {
            repository.create(name = "Ana", phone = "11999990000", notes = "Prefere carne bem passada")
        }
        var saved = false
        setFormContent(customerId = id, onSaved = { saved = true })

        waitForText("11999990000")

        composeTestRule.onNodeWithTag(CustomerFormTestTags.PHONE_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(CustomerFormTestTags.NOTES_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(CustomerFormTestTags.SAVE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { saved }
        val customer = runBlocking { repository.getById(id) }
        assertNull(customer?.phone)
        assertNull(customer?.notes)
    }
}
