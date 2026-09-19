package com.pedroleite.opencomanda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pedroleite.opencomanda.data.local.entity.CashSessionEntity
import com.pedroleite.opencomanda.domain.CashSessionStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CashSessionDao {
    @Insert
    suspend fun insert(session: CashSessionEntity): Long

    @Update
    suspend fun update(session: CashSessionEntity)

    @Query("SELECT * FROM cash_sessions WHERE id = :id")
    suspend fun getById(id: Long): CashSessionEntity?

    @Query("SELECT * FROM cash_sessions WHERE status = :status LIMIT 1")
    suspend fun getByStatus(status: CashSessionStatus): CashSessionEntity?

    @Query("SELECT * FROM cash_sessions WHERE status = :status LIMIT 1")
    fun observeByStatus(status: CashSessionStatus): Flow<CashSessionEntity?>

    @Query("SELECT * FROM cash_sessions ORDER BY openedAt DESC")
    fun getAll(): Flow<List<CashSessionEntity>>
}
