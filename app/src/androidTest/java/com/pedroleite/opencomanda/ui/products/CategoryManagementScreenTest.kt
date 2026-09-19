package com.pedroleite.opencomanda.ui.products

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.pedroleite.opencomanda.data.repository.CategoryRepository
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the lightweight "Manage categories" screen against a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class CategoryManagementScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: CategoryRepository

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(resId: Int): String = context.getString(resId)

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = CategoryRepository(database.categoryDao())
    }

    private fun waitForContentDescription(description: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Constructing the ViewModel directly (bypassing the app-container-backed factory) is
    // deliberate here: it's what lets this test inject an isolated in-memory-database repository.
    @Suppress("ViewModelConstructorInComposable")
    private fun setContent() {
        composeTestRule.setContent {
            OpenComandaTheme {
                CategoryManagementScreen(
                    onBack = {},
                    viewModel = CategoryManagementViewModel(repository),
                )
            }
        }
    }

    @Test
    fun showsEmptyStateWhenThereAreNoCategories() {
        setContent()

        val emptyTitle = string(R.string.category_list_empty_title)
        waitForText(emptyTitle)
        composeTestRule.onNodeWithText(emptyTitle).assertExists()
    }

    @Test
    fun creatingACategoryPersistsItAndShowsItInTheList() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.category_add_fab)).performClick()
        composeTestRule.onNodeWithTag(CategoryManagementTestTags.NAME_FIELD).performTextInput("Espetinhos")
        composeTestRule.onNodeWithTag(CategoryManagementTestTags.SAVE_BUTTON).performClick()

        waitForText("Espetinhos")
        val categories = runBlocking { repository.getAll().first() }
        assertFalse(categories.isEmpty())
    }

    @Test
    fun savingWithABlankNameShowsAValidationErrorAndDoesNotPersist() {
        setContent()

        composeTestRule.onNodeWithText(string(R.string.category_add_fab)).performClick()
        composeTestRule.onNodeWithTag(CategoryManagementTestTags.SAVE_BUTTON).performClick()

        composeTestRule.onNodeWithText(string(R.string.category_field_name_error)).assertExists()
        assertFalse(runBlocking { repository.getAll().first() }.isNotEmpty())
    }

    @Test
    fun editingACategoryRenamesIt() = runBlocking {
        val id = repository.create("Bebida")
        setContent()
        waitForText("Bebida")

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.category_edit_action, "Bebida"))
            .performClick()
        composeTestRule.onNodeWithTag(CategoryManagementTestTags.NAME_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(CategoryManagementTestTags.NAME_FIELD).performTextInput("Bebidas")
        composeTestRule.onNodeWithTag(CategoryManagementTestTags.SAVE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { repository.getById(id)?.name == "Bebidas" }
        }
    }

    @Test
    fun deactivatingAndReactivatingACategoryTogglesItsActiveState() = runBlocking {
        val id = repository.create("Doses")
        setContent()
        waitForText("Doses")

        val activeDescription = "Doses: " + string(R.string.category_status_active)
        composeTestRule.onNodeWithContentDescription(activeDescription).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { repository.getById(id)?.active == false }
        }

        // Wait for the UI (not just the database) to show each state: a write triggers a Room
        // flow re-query in the ViewModel, and finishing — then closing the database in tearDown —
        // while that query is still in flight makes the test fail intermittently.
        val inactiveDescription = "Doses: " + string(R.string.category_status_inactive)
        waitForContentDescription(inactiveDescription)
        composeTestRule.onNodeWithContentDescription(inactiveDescription).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { repository.getById(id)?.active == true }
        }
        waitForContentDescription(activeDescription)
    }
}
