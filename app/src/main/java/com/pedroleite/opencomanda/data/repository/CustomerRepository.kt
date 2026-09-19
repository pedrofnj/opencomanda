package com.pedroleite.opencomanda.data.repository

import com.pedroleite.opencomanda.data.local.dao.CustomerDao
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

class CustomerRepository(private val customerDao: CustomerDao) {

    fun getAll(): Flow<List<CustomerEntity>> = customerDao.getAll()

    fun getActive(): Flow<List<CustomerEntity>> = customerDao.getActive()

    suspend fun getById(id: Long): CustomerEntity? = customerDao.getById(id)

    suspend fun create(name: String, phone: String?, notes: String?): Long {
        require(name.isNotBlank()) { "Customer name must not be blank" }
        val now = System.currentTimeMillis()
        return customerDao.insert(
            CustomerEntity(
                name = name.trim(),
                phone = phone?.trim()?.ifBlank { null },
                notes = notes?.trim()?.ifBlank { null },
                active = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun update(customer: CustomerEntity) {
        require(customer.name.isNotBlank()) { "Customer name must not be blank" }
        customerDao.update(customer.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Soft-disable: OpenComanda never hard-deletes customers referenced by historical orders/debts. */
    suspend fun setActive(id: Long, active: Boolean) {
        customerDao.setActive(id, active, System.currentTimeMillis())
    }
}
