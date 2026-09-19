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

/** Verifies Customer CRUD, soft-disable, and duplicate-name handling against a real Room database. */
@RunWith(AndroidJUnit4::class)
class CustomerRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: CustomerRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = CustomerRepository(database.customerDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun newDatabaseHasNoCustomers() = runBlocking {
        assertEquals(0, repository.getAll().first().size)
    }

    @Test
    fun createdCustomerAppearsInTheListWithTrimmedFields() = runBlocking {
        repository.create(name = "  João Silva  ", phone = "  (62) 99999-1234  ", notes = "  Cliente frequente  ")

        val customers = repository.getAll().first()
        assertEquals(1, customers.size)
        val customer = customers.single()
        assertEquals("João Silva", customer.name)
        assertEquals("(62) 99999-1234", customer.phone)
        assertEquals("Cliente frequente", customer.notes)
        assertEquals(true, customer.active)
    }

    @Test
    fun createWithoutPhoneOrNotesLeavesThemNull() = runBlocking {
        val id = repository.create(name = "Maria", phone = null, notes = null)

        val customer = repository.getById(id)!!
        assertNull(customer.phone)
        assertNull(customer.notes)
    }

    @Test
    fun blankPhoneAndNotesAreNormalizedToNull() = runBlocking {
        val id = repository.create(name = "Maria", phone = "   ", notes = "   ")

        val customer = repository.getById(id)!!
        assertNull(customer.phone)
        assertNull(customer.notes)
    }

    @Test
    fun createRejectsABlankName() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.create(name = "   ", phone = null, notes = null) }
        }
        Unit
    }

    @Test
    fun duplicateCustomerNamesAreBothAllowed() = runBlocking {
        repository.create(name = "João", phone = null, notes = null)
        repository.create(name = "João", phone = null, notes = null)

        assertEquals(2, repository.getAll().first().size)
    }

    @Test
    fun updatingACustomerPersistsAllChangedFields() = runBlocking {
        val id = repository.create(name = "Costela", phone = "11999990000", notes = "Nota antiga")

        val original = repository.getById(id)!!
        repository.update(original.copy(name = "Costela Premium", phone = "11988887777", notes = "Nota nova"))

        val updated = repository.getById(id)!!
        assertEquals("Costela Premium", updated.name)
        assertEquals("11988887777", updated.phone)
        assertEquals("Nota nova", updated.notes)
    }

    @Test
    fun updatingACustomerCanRemoveAnOptionalPhone() = runBlocking {
        val id = repository.create(name = "Ana", phone = "11999990000", notes = null)

        val original = repository.getById(id)!!
        repository.update(original.copy(phone = null))

        assertNull(repository.getById(id)!!.phone)
    }

    @Test
    fun updatingACustomerCanRemoveExistingNotes() = runBlocking {
        val id = repository.create(name = "Ana", phone = null, notes = "Prefere carne bem passada")

        val original = repository.getById(id)!!
        repository.update(original.copy(notes = null))

        assertNull(repository.getById(id)!!.notes)
    }

    @Test
    fun updateRejectsABlankName() = runBlocking {
        val id = repository.create(name = "Ana", phone = null, notes = null)
        val original = repository.getById(id)!!

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.update(original.copy(name = "   ")) }
        }
        Unit
    }

    @Test
    fun deactivatingACustomerKeepsItStoredButMarksItInactive() = runBlocking {
        val id = repository.create(name = "Pedro", phone = null, notes = null)

        repository.setActive(id, false)

        val customer = repository.getById(id)!!
        assertEquals(false, customer.active)
        assertEquals("Pedro", customer.name)
    }

    @Test
    fun reactivatingACustomerRestoresActiveState() = runBlocking {
        val id = repository.create(name = "Pedro", phone = null, notes = null)
        repository.setActive(id, false)

        repository.setActive(id, true)

        assertEquals(true, repository.getById(id)!!.active)
    }

    @Test
    fun getActiveExcludesDeactivatedCustomers() = runBlocking {
        val activeId = repository.create(name = "Ativo", phone = null, notes = null)
        val inactiveId = repository.create(name = "Inativo", phone = null, notes = null)
        repository.setActive(inactiveId, false)

        val active = repository.getActive().first()
        assertEquals(1, active.size)
        assertEquals(activeId, active.single().id)
    }

    @Test
    fun getByIdReturnsNullForAnUnknownCustomer() = runBlocking {
        assertNull(repository.getById(999_999))
    }
}
