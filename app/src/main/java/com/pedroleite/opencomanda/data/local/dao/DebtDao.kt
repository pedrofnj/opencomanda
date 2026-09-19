package com.pedroleite.opencomanda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pedroleite.opencomanda.data.local.entity.DebtEntity
import com.pedroleite.opencomanda.domain.DebtStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {
    @Insert
    suspend fun insert(debt: DebtEntity): Long

    @Update
    suspend fun update(debt: DebtEntity)

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getById(id: Long): DebtEntity?

    /** Reactive single-debt lookup — used by the Debt detail screen so it picks up this same
     *  debt's own later payments (status transitions) without a manual refetch. */
    @Query("SELECT * FROM debts WHERE id = :id")
    fun observeById(id: Long): Flow<DebtEntity?>

    @Query("SELECT * FROM debts WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun getForCustomer(customerId: Long): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE status != :paidStatus ORDER BY createdAt ASC")
    fun getOpen(paidStatus: DebtStatus): Flow<List<DebtEntity>>

    @Query("UPDATE debts SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DebtStatus)
}
