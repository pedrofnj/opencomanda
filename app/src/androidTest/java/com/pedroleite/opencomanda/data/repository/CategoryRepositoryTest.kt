package com.pedroleite.opencomanda.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies Category CRUD, soft-disable, and duplicate-name handling against a real Room database. */
@RunWith(AndroidJUnit4::class)
class CategoryRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: CategoryRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = CategoryRepository(database.categoryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun newDatabaseHasNoCategories() = runBlocking {
        assertEquals(0, repository.getAll().first().size)
    }

    @Test
    fun createdCategoryAppearsInTheList() = runBlocking {
        repository.create("Espetinhos")

        val categories = repository.getAll().first()
        assertEquals(1, categories.size)
        assertEquals("Espetinhos", categories.single().name)
        assertEquals(true, categories.single().active)
    }

    @Test
    fun renameUpdatesTheCategoryName() = runBlocking {
        val id = repository.create("Bebida")
        repository.rename(id, "Bebidas")

        assertEquals("Bebidas", repository.getById(id)!!.name)
    }

    @Test
    fun deactivatingACategoryMarksItInactiveButKeepsItStored() = runBlocking {
        val id = repository.create("Doses")

        repository.setActive(id, false)

        val category = repository.getById(id)!!
        assertFalse(category.active)
        assertEquals("Doses", category.name)
    }

    @Test
    fun reactivatingACategoryRestoresActiveState() = runBlocking {
        val id = repository.create("Doses")
        repository.setActive(id, false)

        repository.setActive(id, true)

        assertTrue(repository.getById(id)!!.active)
    }

    @Test
    fun createRejectsABlankName() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create("   ") }
        }
        Unit
    }

    @Test
    fun createRejectsADuplicateActiveName() = runBlocking {
        repository.create("Bebidas")

        assertThrows(DuplicateCategoryNameException::class.java) {
            runBlocking { repository.create("  bebidas  ") }
        }
        Unit
    }

    @Test
    fun createAllowsANameThatOnlyCollidesWithAnInactiveCategory() = runBlocking {
        val firstId = repository.create("Bebidas")
        repository.setActive(firstId, false)

        val secondId = repository.create("Bebidas")

        assertEquals(2, repository.getAll().first().size)
        assertTrue(repository.getById(secondId)!!.active)
    }

    @Test
    fun renameRejectsADuplicateOfAnotherActiveCategory() = runBlocking {
        repository.create("Bebidas")
        val doses = repository.create("Doses")

        assertThrows(DuplicateCategoryNameException::class.java) {
            runBlocking { repository.rename(doses, "Bebidas") }
        }
        Unit
    }

    @Test
    fun renamingACategoryToItsOwnCurrentNameIsAllowed() = runBlocking {
        val id = repository.create("Bebidas")

        repository.rename(id, "Bebidas")

        assertEquals("Bebidas", repository.getById(id)!!.name)
    }

    @Test
    fun getByIdReturnsNullForAnUnknownCategory() = runBlocking {
        assertEquals(null, repository.getById(999_999))
    }
}
