package com.pedroleite.opencomanda.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies Product CRUD, soft-disable, and stock-tracking behavior against a real Room database. */
@RunWith(AndroidJUnit4::class)
class ProductRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ProductRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = ProductRepository(database, database.productDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun newDatabaseHasNoProducts() = runBlocking {
        assertEquals(0, repository.getAll().first().size)
    }

    @Test
    fun createdProductAppearsInTheListWithPricePersistedAsLongCents() = runBlocking {
        repository.create(
            name = "Espetinho",
            description = null,
            priceCents = 1050,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
        )

        val products = repository.getAll().first()
        assertEquals(1, products.size)
        val product = products.single()
        assertEquals("Espetinho", product.name)
        assertEquals(1050L, product.priceCents)
        assertEquals(true, product.active)
    }

    @Test
    fun stockTrackingOffKeepsStockAtZeroRegardlessOfInitialQuantity() = runBlocking {
        val id = repository.create(
            name = "Refrigerante",
            description = null,
            priceCents = 500,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 999.0,
        )

        val product = repository.getById(id)!!
        assertEquals(false, product.trackStock)
        assertEquals(0.0, product.stockQuantity, 0.0)
    }

    @Test
    fun stockTrackingOnPersistsTheInitialQuantity() = runBlocking {
        val id = repository.create(
            name = "Cerveja",
            description = null,
            priceCents = 800,
            costCents = null,
            trackStock = true,
            initialStockQuantity = 24.0,
        )

        val product = repository.getById(id)!!
        assertEquals(true, product.trackStock)
        assertEquals(24.0, product.stockQuantity, 0.0)
    }

    @Test
    fun editingAProductPersistsAllUpdatedFields() = runBlocking {
        val id = repository.create(
            name = "Costela",
            description = "Corte especial",
            priceCents = 2500,
            costCents = 1500,
            trackStock = false,
            initialStockQuantity = 0.0,
        )

        val original = repository.getById(id)!!
        // Stock is changed only by passing an explicit StockConfig — see the stale-snapshot tests below.
        repository.update(
            original.copy(name = "Costela Premium", priceCents = 3000),
            stockConfig = StockConfig(trackStock = true, quantity = 5.0),
        )

        val updated = repository.getById(id)!!
        assertEquals("Costela Premium", updated.name)
        assertEquals(3000L, updated.priceCents)
        assertEquals(true, updated.trackStock)
        assertEquals(5.0, updated.stockQuantity, 0.0)
        // Untouched fields survive the edit.
        assertEquals("Corte especial", updated.description)
        assertEquals(1500L, updated.costCents)
    }

    @Test
    fun deactivatingAProductKeepsItStoredButMarksItInactive() = runBlocking {
        val id = repository.create(
            name = "Linguica",
            description = null,
            priceCents = 1200,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
        )

        repository.setActive(id, false)

        val product = repository.getById(id)!!
        assertEquals(false, product.active)
        assertEquals("Linguica", product.name) // still fully stored, not deleted
    }

    @Test
    fun reactivatingAProductRestoresActiveState() = runBlocking {
        val id = repository.create(
            name = "Suco",
            description = null,
            priceCents = 700,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
        )
        repository.setActive(id, false)

        repository.setActive(id, true)

        assertEquals(true, repository.getById(id)!!.active)
    }

    @Test
    fun createRejectsABlankName() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.create(
                    name = "   ",
                    description = null,
                    priceCents = 100,
                    costCents = null,
                    trackStock = false,
                    initialStockQuantity = 0.0,
                )
            }
        }
        Unit
    }

    @Test
    fun createRejectsANegativePrice() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.create(
                    name = "Produto",
                    description = null,
                    priceCents = -100,
                    costCents = null,
                    trackStock = false,
                    initialStockQuantity = 0.0,
                )
            }
        }
        Unit
    }

    @Test
    fun createAllowsAZeroPrice() = runBlocking {
        val id = repository.create(
            name = "Cortesia",
            description = null,
            priceCents = 0,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
        )

        assertEquals(0L, repository.getById(id)!!.priceCents)
    }

    @Test
    fun getByIdReturnsNullForAnUnknownProduct() = runBlocking {
        assertNull(repository.getById(999_999))
    }

    @Test
    fun createWithACategoryPersistsTheCategoryId() = runBlocking {
        val categoryRepository = CategoryRepository(database.categoryDao())
        val categoryId = categoryRepository.create("Espetinhos")

        val id = repository.create(
            name = "Espetinho",
            description = null,
            priceCents = 1050,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
            categoryId = categoryId,
        )

        assertEquals(categoryId, repository.getById(id)!!.categoryId)
    }

    @Test
    fun createWithoutACategoryLeavesCategoryIdNull() = runBlocking {
        val id = repository.create(
            name = "Espetinho",
            description = null,
            priceCents = 1050,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
        )

        assertNull(repository.getById(id)!!.categoryId)
    }

    @Test
    fun deactivatingACategoryDoesNotAlterItsProducts() = runBlocking {
        val categoryRepository = CategoryRepository(database.categoryDao())
        val categoryId = categoryRepository.create("Espetinhos")
        val id = repository.create(
            name = "Espetinho",
            description = null,
            priceCents = 1050,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
            categoryId = categoryId,
        )

        categoryRepository.setActive(categoryId, false)

        val product = repository.getById(id)!!
        assertEquals(categoryId, product.categoryId)
        assertEquals(true, product.active)
    }

    @Test
    fun updatingAProductPreservesItsCategoryRelationshipWhenUntouched() = runBlocking {
        val categoryRepository = CategoryRepository(database.categoryDao())
        val categoryId = categoryRepository.create("Espetinhos")
        val id = repository.create(
            name = "Espetinho",
            description = null,
            priceCents = 1050,
            costCents = null,
            trackStock = false,
            initialStockQuantity = 0.0,
            categoryId = categoryId,
        )

        val original = repository.getById(id)!!
        repository.update(original.copy(priceCents = 1200))

        assertEquals(categoryId, repository.getById(id)!!.categoryId)
    }

    // ---------------------------------------------------------------------------------------
    // Editing a product must never silently overwrite stock / active with a stale snapshot
    // ---------------------------------------------------------------------------------------

    private suspend fun createTracked(stock: Double): Long = repository.create(
        name = "Cerveja",
        description = null,
        priceCents = 800,
        costCents = null,
        trackStock = true,
        initialStockQuantity = stock,
    )

    @Test
    fun editingNameAndPriceKeepsTheCurrentStock() = runBlocking {
        val id = createTracked(stock = 8.0)

        repository.update(repository.getById(id)!!.copy(name = "Cerveja Lata", priceCents = 900))

        val updated = repository.getById(id)!!
        assertEquals("Cerveja Lata", updated.name)
        assertEquals(900L, updated.priceCents)
        assertEquals(8.0, updated.stockQuantity, 0.0)
        assertEquals(true, updated.trackStock)
    }

    @Test
    fun aStaleSnapshotCannotOverwriteStockChangedInTheMeantime() = runBlocking {
        val id = createTracked(stock = 8.0)
        val staleSnapshot = repository.getById(id)!! // What an edit form loaded when it opened.

        repository.adjustStock(id, -3.0) // A sale happens while the form is open: 8 -> 5.
        repository.update(staleSnapshot.copy(priceCents = 950))

        assertEquals(5.0, repository.getById(id)!!.stockQuantity, 0.0)
    }

    @Test
    fun aStaleSnapshotCannotOverwriteTheActiveFlag() = runBlocking {
        val id = createTracked(stock = 8.0)
        val staleSnapshot = repository.getById(id)!! // active = true

        repository.setActive(id, false)
        repository.update(staleSnapshot.copy(name = "Cerveja Long Neck"))

        val updated = repository.getById(id)!!
        assertEquals("Cerveja Long Neck", updated.name)
        assertEquals(false, updated.active)
    }

    @Test
    fun anIntentionalStockEditIsAppliedTogetherWithTheRestOfTheEdit() = runBlocking {
        val id = createTracked(stock = 8.0)

        repository.update(
            repository.getById(id)!!.copy(priceCents = 1000),
            stockConfig = StockConfig(trackStock = true, quantity = 20.0),
        )

        val updated = repository.getById(id)!!
        assertEquals(1000L, updated.priceCents)
        assertEquals(20.0, updated.stockQuantity, 0.0)
    }

    @Test
    fun turningStockControlOffViaTheEditZeroesTheStockFigure() = runBlocking {
        val id = createTracked(stock = 8.0)

        repository.update(repository.getById(id)!!, stockConfig = StockConfig(trackStock = false, quantity = 8.0))

        val updated = repository.getById(id)!!
        assertEquals(false, updated.trackStock)
        assertEquals(0.0, updated.stockQuantity, 0.0)
    }

    @Test
    fun aNegativeStockEditIsRejected() = runBlocking {
        val id = createTracked(stock = 8.0)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.update(repository.getById(id)!!, StockConfig(true, -1.0)) }
        }
        assertEquals(8.0, repository.getById(id)!!.stockQuantity, 0.0)
    }
}
