package com.pedroleite.opencomanda.ui.comandas

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.R
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.data.repository.CartLine
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.domain.PaymentMethod
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the Open Comandas list: empty state, only-OPEN-Comandas visibility (Quick Sales and
 *  closed/cancelled Comandas excluded), and that tapping a row/FAB navigates correctly. */
@RunWith(AndroidJUnit4::class)
class OpenComandasScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var orderRepository: OrderRepository
    private var productId: Long = 0

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun string(resId: Int): String = context.getString(resId)

    private fun waitForText(text: String) {
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        orderRepository = OrderRepository(
            database = database,
            orderDao = database.orderDao(),
            orderItemDao = database.orderItemDao(),
            paymentDao = database.paymentDao(),
            debtDao = database.debtDao(),
            productDao = database.productDao(),
        )
        val now = System.currentTimeMillis()
        productId = database.productDao().insert(
            ProductEntity(name = "Espetinho", priceCents = 1250, createdAt = now, updatedAt = now),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Suppress("ViewModelConstructorInComposable")
    private fun setScreenContent(onCreateComanda: () -> Unit = {}, onOpenComanda: (Long) -> Unit = {}) {
        composeTestRule.setContent {
            OpenComandaTheme {
                OpenComandasScreen(
                    onBack = {},
                    onCreateComanda = onCreateComanda,
                    onOpenComanda = onOpenComanda,
                    viewModel = OpenComandasViewModel(orderRepository),
                )
            }
        }
    }

    @Test
    fun showsEmptyStateWhenThereAreNoOpenComandas() {
        setScreenContent()

        waitForText(string(R.string.comanda_list_empty_title))
    }

    @Test
    fun anOpenComandaAppearsWithItsItemCountAndTotal() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 4")
        orderRepository.addComandaItem(orderId, productId, 2.0)

        setScreenContent()

        waitForText("Mesa 4")
        composeTestRule.onNodeWithText(string(R.string.comanda_list_empty_title)).assertDoesNotExist()
    }

    @Test
    fun quickSalesAndClosedComandasAreNotShown() = runBlocking {
        orderRepository.confirmQuickSale(
            lines = listOf(CartLine(productId, 1.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
            cashSessionId = null,
        )
        val closedId = orderRepository.createComanda(customerId = null, displayName = "Mesa Fechada")
        orderRepository.addComandaItem(closedId, productId, 1.0)
        orderRepository.closeOrderWithPayment(closedId, PaymentMethod.CASH, cashSessionId = null)
        orderRepository.createComanda(customerId = null, displayName = "Mesa Aberta")

        setScreenContent()

        waitForText("Mesa Aberta")
        composeTestRule.onNodeWithText("Mesa Fechada").assertDoesNotExist()
    }

    @Test
    fun tappingTheFabInvokesTheCreateCallback() {
        var invoked = false
        setScreenContent(onCreateComanda = { invoked = true })
        waitForText(string(R.string.comanda_list_empty_title))

        composeTestRule.onNodeWithTag(OpenComandasTestTags.CREATE_FAB).performClick()

        assertEquals(true, invoked)
    }

    @Test
    fun tappingAComandaRowInvokesTheOpenCallbackWithItsId() = runBlocking {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 7")
        var openedId: Long? = null
        setScreenContent(onOpenComanda = { openedId = it })
        waitForText("Mesa 7")

        composeTestRule.onNodeWithText("Mesa 7").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { openedId != null }
        assertEquals(orderId, openedId)
    }
}
