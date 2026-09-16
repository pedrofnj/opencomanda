package com.pedroleite.opencomanda.ui.navigation

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.R
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
    fun navigatingToFiadoShowsThePlaceholderWithTheRightTitle() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_fiado)).performScrollTo().performClick()

        composeTestRule.onNodeWithText(string(R.string.action_fiado)).assertExists()
        composeTestRule.onNodeWithText(string(R.string.placeholder_message)).assertExists()
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
    fun backNavigationFromAPlaceholderReturnsToHome() {
        setNavHostContent()

        composeTestRule.onNodeWithText(string(R.string.action_customers)).performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        composeTestRule.onNodeWithText(string(R.string.action_quick_sale), substring = true).assertExists()
    }
}
