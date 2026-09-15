package com.pedroleite.opencomanda.ui.home

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.ui.navigation.Destination
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Home screen shows its main actions and reports the right [Destination] when
 * each one is tapped. Uses text/semantics assertions rather than pixel positions, and reads
 * expected labels from string resources so the test passes regardless of the device's locale.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(resId: Int): String = context.getString(resId)

    private fun setHomeContent(onNavigate: (Destination) -> Unit) {
        composeTestRule.setContent {
            OpenComandaTheme {
                HomeScreen(onNavigate = onNavigate)
            }
        }
    }

    @Test
    fun allSevenActionsAreDisplayed() {
        setHomeContent(onNavigate = {})

        listOf(
            R.string.action_quick_sale,
            R.string.action_new_comanda,
            R.string.action_open_comandas,
            R.string.action_cash_register,
            R.string.action_products,
            R.string.action_customers,
            R.string.action_fiado,
        ).forEach { resId ->
            // substring: Primary cards now also show a short supporting line, whose text is
            // merged into the same semantics node as the label.
            composeTestRule.onNodeWithText(string(resId), substring = true).assertHasClickAction()
        }
    }

    @Test
    fun tappingQuickSaleReportsTheQuickSaleDestination() {
        var navigated: Destination? = null
        setHomeContent(onNavigate = { navigated = it })

        composeTestRule.onNodeWithText(string(R.string.action_quick_sale), substring = true)
            .performScrollTo()
            .performClick()

        assertEquals(Destination.QuickSale, navigated)
    }

    @Test
    fun tappingNewComandaReportsTheNewComandaDestination() {
        var navigated: Destination? = null
        setHomeContent(onNavigate = { navigated = it })

        composeTestRule.onNodeWithText(string(R.string.action_new_comanda), substring = true)
            .performScrollTo()
            .performClick()

        assertEquals(Destination.NewComanda, navigated)
    }

    @Test
    fun tappingFiadoReportsTheFiadoDestination() {
        var navigated: Destination? = null
        setHomeContent(onNavigate = { navigated = it })

        composeTestRule.onNodeWithText(string(R.string.action_fiado)).performScrollTo().performClick()

        assertEquals(Destination.Fiado, navigated)
    }
}
