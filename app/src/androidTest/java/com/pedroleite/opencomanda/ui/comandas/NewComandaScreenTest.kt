package com.pedroleite.opencomanda.ui.comandas

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.pedroleite.opencomanda.data.repository.OrderRepository
import com.pedroleite.opencomanda.domain.OrderStatus
import com.pedroleite.opencomanda.domain.OrderType
import com.pedroleite.opencomanda.ui.theme.OpenComandaTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the New Comanda creation form: required name, optional customer, and that creating
 *  one actually persists an OPEN Comanda order (unlike Quick Sale, there's no in-memory draft). */
@RunWith(AndroidJUnit4::class)
class NewComandaScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var orderRepository: OrderRepository
    private lateinit var customerRepository: CustomerRepository

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
        orderRepository = OrderRepository(
            database = database,
            orderDao = database.orderDao(),
            orderItemDao = database.orderItemDao(),
            paymentDao = database.paymentDao(),
            debtDao = database.debtDao(),
            productDao = database.productDao(),
            cashSessionDao = database.cashSessionDao(),
        )
        customerRepository = CustomerRepository(database.customerDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Suppress("ViewModelConstructorInComposable")
    private fun setScreenContent(
        onBack: () -> Unit = {},
        onCreated: (Long) -> Unit = {},
        viewModel: NewComandaViewModel = NewComandaViewModel(orderRepository, customerRepository),
    ): NewComandaViewModel {
        composeTestRule.setContent {
            OpenComandaTheme {
                NewComandaScreen(onBack = onBack, onCreated = onCreated, viewModel = viewModel)
            }
        }
        return viewModel
    }

    @Test
    fun creatingWithABlankNameShowsAValidationErrorAndDoesNotCreate() {
        var created: Long? = null
        setScreenContent(onCreated = { created = it })

        composeTestRule.onNodeWithTag(NewComandaTestTags.CREATE_BUTTON).performClick()

        waitForText(string(R.string.comanda_field_name_error))
        assertNull(created)
    }

    @Test
    fun creatingWithANameCreatesAnOpenComandaAndInvokesTheCallback() = runBlocking {
        var created: Long? = null
        setScreenContent(onCreated = { created = it })

        composeTestRule.onNodeWithTag(NewComandaTestTags.NAME_FIELD).performTextInput("Mesa 4")
        composeTestRule.onNodeWithTag(NewComandaTestTags.CREATE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { created != null }
        val order = database.orderDao().getById(created!!)!!
        assertEquals(OrderType.COMANDA, order.orderType)
        assertEquals(OrderStatus.OPEN, order.status)
        assertEquals("Mesa 4", order.displayName)
        assertNull(order.customerId)
    }

    @Test
    fun selectingACustomerAssociatesItWithTheCreatedComanda() = runBlocking {
        val customerId = customerRepository.create(name = "Joao Silva", phone = null, notes = null)
        var created: Long? = null
        val viewModel = NewComandaViewModel(orderRepository, customerRepository)
        setScreenContent(onCreated = { created = it }, viewModel = viewModel)
        // The dropdown's items aren't composed until it's expanded, so "Joao Silva" isn't a
        // visible node to wait on — poll the ViewModel's own loaded state instead.
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.uiState.value.activeCustomers.isNotEmpty() }

        composeTestRule.onNodeWithTag(NewComandaTestTags.NAME_FIELD).performTextInput("Mesa 9")
        composeTestRule.onNodeWithTag(NewComandaTestTags.CUSTOMER_FIELD).performClick()
        composeTestRule.onNodeWithText("Joao Silva").performClick()
        composeTestRule.onNodeWithTag(NewComandaTestTags.CREATE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { created != null }
        assertEquals(customerId, database.orderDao().getById(created!!)!!.customerId)
    }

    @Test
    fun creatingWithoutSelectingACustomerDoesNotRequireOne() = runBlocking {
        var created: Long? = null
        setScreenContent(onCreated = { created = it })

        composeTestRule.onNodeWithTag(NewComandaTestTags.NAME_FIELD).performTextInput("Balcao")
        composeTestRule.onNodeWithTag(NewComandaTestTags.CREATE_BUTTON).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) { created != null }
        assertNull(database.orderDao().getById(created!!)!!.customerId)
    }
}
