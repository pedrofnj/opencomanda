package com.pedroleite.opencomanda.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.data.local.entity.OrderEntity
import com.pedroleite.opencomanda.data.local.entity.OrderItemEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.domain.OrderStatus
import com.pedroleite.opencomanda.domain.OrderType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the fundamental persistence rules the schema itself must guarantee:
 * foreign key cascade/set-null behavior, and that order item snapshots stay
 * immutable when the source product later changes.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var database: AppDatabase

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun productCrudRoundTrip() = runBlocking {
        val now = System.currentTimeMillis()
        val id = database.productDao().insert(
            ProductEntity(name = "Espetinho", priceCents = 1000, createdAt = now, updatedAt = now),
        )

        val stored = database.productDao().getById(id)
        assertEquals("Espetinho", stored?.name)
        assertEquals(1000L, stored?.priceCents)
        assertEquals(false, stored?.trackStock)
        assertEquals(true, stored?.active)
    }

    @Test
    fun deletingAnOrderCascadesToItsItems() = runBlocking {
        val now = System.currentTimeMillis()
        val productId = database.productDao().insert(
            ProductEntity(name = "Refrigerante", priceCents = 500, createdAt = now, updatedAt = now),
        )
        val orderId = database.orderDao().insert(
            OrderEntity(orderType = OrderType.QUICK_SALE, status = OrderStatus.OPEN, openedAt = now),
        )
        database.orderItemDao().insert(
            OrderItemEntity(
                orderId = orderId,
                productId = productId,
                productNameSnapshot = "Refrigerante",
                unitPriceCentsSnapshot = 500,
                quantity = 1.0,
                subtotalCents = 500,
            ),
        )

        database.openHelper.writableDatabase.execSQL("DELETE FROM orders WHERE id = ?", arrayOf(orderId))

        val remainingItems = database.orderItemDao().getItemsForOrder(orderId).first()
        assertEquals(0, remainingItems.size)
    }

    @Test
    fun deletingAProductSetsOrderItemProductIdToNullButKeepsTheSnapshot() = runBlocking {
        val now = System.currentTimeMillis()
        val productId = database.productDao().insert(
            ProductEntity(name = "Costela", priceCents = 2500, createdAt = now, updatedAt = now),
        )
        val orderId = database.orderDao().insert(
            OrderEntity(orderType = OrderType.QUICK_SALE, status = OrderStatus.OPEN, openedAt = now),
        )
        val itemId = database.orderItemDao().insert(
            OrderItemEntity(
                orderId = orderId,
                productId = productId,
                productNameSnapshot = "Costela",
                unitPriceCentsSnapshot = 2500,
                quantity = 2.0,
                subtotalCents = 5000,
            ),
        )

        database.openHelper.writableDatabase.execSQL("DELETE FROM products WHERE id = ?", arrayOf(productId))

        val items = database.orderItemDao().getItemsForOrder(orderId).first()
        val item = items.single { it.id == itemId }
        assertNull(item.productId)
        assertEquals("Costela", item.productNameSnapshot)
        assertEquals(2500L, item.unitPriceCentsSnapshot)
        assertEquals(5000L, item.subtotalCents)
    }

    @Test
    fun updatingAProductDoesNotAlterExistingOrderItemSnapshots() = runBlocking {
        val now = System.currentTimeMillis()
        val productId = database.productDao().insert(
            ProductEntity(name = "Linguica", priceCents = 1200, createdAt = now, updatedAt = now),
        )
        val orderId = database.orderDao().insert(
            OrderEntity(orderType = OrderType.QUICK_SALE, status = OrderStatus.OPEN, openedAt = now),
        )
        val itemId = database.orderItemDao().insert(
            OrderItemEntity(
                orderId = orderId,
                productId = productId,
                productNameSnapshot = "Linguica",
                unitPriceCentsSnapshot = 1200,
                quantity = 1.0,
                subtotalCents = 1200,
            ),
        )

        // The product's price and name change later...
        val product = database.productDao().getById(productId)!!
        database.productDao().update(product.copy(name = "Linguica Artesanal", priceCents = 1800))

        // ...but the historical order item must still reflect the price/name at time of sale.
        val items = database.orderItemDao().getItemsForOrder(orderId).first()
        val item = items.single { it.id == itemId }
        assertEquals("Linguica", item.productNameSnapshot)
        assertEquals(1200L, item.unitPriceCentsSnapshot)
    }

    @Test
    fun deactivatingACustomerIsSoftAndKeepsItsHistory() = runBlocking {
        val now = System.currentTimeMillis()
        val customerId = database.customerDao().insert(
            CustomerEntity(name = "Maria", createdAt = now, updatedAt = now),
        )

        database.customerDao().setActive(customerId, false, System.currentTimeMillis())

        val customer = database.customerDao().getById(customerId)
        assertEquals(false, customer?.active)
        assertEquals("Maria", customer?.name)
    }
}
