package com.pedroleite.opencomanda.ui.navigation

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.ui.comandas.NewComandaTestTags
import com.pedroleite.opencomanda.ui.comandas.OpenComandasTestTags
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the navigation graph end to end: Home is the start destination, each action leads
 * to its (placeholder) screen with the correct localized title, and back navigation returns
 * to Home. Reads expected labels from string resources so the test passes regardless of the
 * device's locale.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(resId: Int): String = context.getString(resId)

    private fun setNavHostContent() {
        composeTestRule.setContent {
            OpenComandaTheme {
                OpenComandaNavHost(navController = rememberNavController())
            }
        }
    }

    @Test
    fun startDestinationIsHome() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_quick_sale), substring = true).assertExists()
        composeTestRule.onNodeWithText(string(R.string.action_new_comanda), substring = true).assertExists()
    }

    @Test
    fun navigatingToFiadoOpensTheRealScreenNotAPlaceholder() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_fiado)).performScrollTo().performClick()

        // The real Fiado screen shows its empty-state copy; it must never show the placeholder.
        composeTestRule.onNodeWithText(string(R.string.fiado_empty_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.placeholder_message)).assertDoesNotExist()
    }

    @Test
    fun navigatingToQuickSaleOpensTheRealScreenNotAPlaceholder() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_quick_sale), substring = true)
            .performScrollTo()
            .performClick()

        // The real Quick Sale screen shows its empty-products copy; it must never show the
        // placeholder text.
        composeTestRule.onNodeWithText(string(R.string.quicksale_empty_products_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.placeholder_message)).assertDoesNotExist()
    }

    @Test
    fun navigatingToNewComandaOpensTheRealScreenNotAPlaceholder() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_new_comanda), substring = true)
            .performScrollTo()
            .performClick()

        // The real New Comanda screen shows its name field; it must never show the placeholder.
        composeTestRule.onNodeWithText(string(R.string.comanda_field_name)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.placeholder_message)).assertDoesNotExist()
    }

    @Test
    fun navigatingToOpenComandasOpensTheRealScreenNotAPlaceholder() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_open_comandas)).performScrollTo().performClick()

        // The real Open Comandas screen always shows its "new comanda" FAB, empty or not; it
        // must never show the placeholder. (Not asserting the empty state itself here: this
        // real, app-container-backed database is shared with other tests in this class, such as
        // the one that creates a Comanda, so it isn't guaranteed to be empty at this point.)
        composeTestRule.onNodeWithTag(OpenComandasTestTags.CREATE_FAB).assertExists()
        composeTestRule.onNodeWithText(string(R.string.placeholder_message)).assertDoesNotExist()
    }

    @Test
    fun creatingAComandaFromHomeNavigatesStraightToItsDetailScreen() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_new_comanda), substring = true)
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(NewComandaTestTags.NAME_FIELD).performTextInput("Mesa 4")
        composeTestRule.onNodeWithTag(NewComandaTestTags.CREATE_BUTTON).performClick()

        // Landed on the detail screen for the comanda just created — its name is now the
        // top-bar title, and the creation form's fields are gone.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Mesa 4").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(string(R.string.comanda_field_name)).assertDoesNotExist()
    }

    @Test
    fun navigatingToProductsOpensTheRealProductsScreenNotAPlaceholder() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_products)).performScrollTo().performClick()

        // The real Products screen shows its FAB label; it must never show the placeholder copy.
        composeTestRule.onNodeWithText(string(R.string.product_add_fab)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.placeholder_message)).assertDoesNotExist()
    }

    @Test
    fun manageCategoriesActionFromProductsOpensCategoryManagement() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_products)).performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.category_manage_action)).performClick()

        composeTestRule.onNodeWithText(string(R.string.category_list_title)).assertExists()
    }

    @Test
    fun navigatingToCustomersOpensTheRealCustomersScreenNotAPlaceholder() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_customers)).performScrollTo().performClick()

        // The real Customers screen shows its FAB label; it must never show the placeholder copy.
        composeTestRule.onNodeWithText(string(R.string.customer_add_fab)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.placeholder_message)).assertDoesNotExist()
    }

    @Test
    fun customersToNewCustomerOpensTheCreateCustomerForm() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_customers)).performScrollTo().performClick()
        composeTestRule.onNodeWithText(string(R.string.customer_add_fab)).performClick()

        composeTestRule.onNodeWithText(string(R.string.customer_create_title)).assertExists()
    }

    @Test
    fun backNavigationFromFiadoReturnsToHome() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_fiado)).performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        composeTestRule.onNodeWithText(string(R.string.action_quick_sale), substring = true).assertExists()
    }

    @Test
    fun navigatingToCashRegisterOpensTheRealScreenNotAPlaceholder() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_cash_register)).performScrollTo().performClick()

        // The real Cash Register screen shows its closed/empty-state copy; it must never show
        // the placeholder text.
        composeTestRule.onNodeWithText(string(R.string.cash_register_empty_title)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.placeholder_message)).assertDoesNotExist()
    }
}
