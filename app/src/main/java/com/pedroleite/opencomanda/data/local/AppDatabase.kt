package com.pedroleite.opencomanda.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pedroleite.opencomanda.data.local.dao.CashSessionDao
import com.pedroleite.opencomanda.data.local.dao.CustomerDao
import com.pedroleite.opencomanda.data.local.dao.DebtDao
import com.pedroleite.opencomanda.data.local.dao.DebtPaymentDao
import com.pedroleite.opencomanda.data.local.dao.OrderDao
import com.pedroleite.opencomanda.data.local.dao.OrderItemDao
import com.pedroleite.opencomanda.data.local.dao.PaymentDao
import com.pedroleite.opencomanda.data.local.dao.ProductDao
import com.pedroleite.opencomanda.data.local.entity.CashSessionEntity
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.data.local.entity.DebtEntity
import com.pedroleite.opencomanda.data.local.entity.DebtPaymentEntity
import com.pedroleite.opencomanda.data.local.entity.OrderEntity
import com.pedroleite.opencomanda.data.local.entity.OrderItemEntity
import com.pedroleite.opencomanda.data.local.entity.PaymentEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity

/**
 * OpenComanda's single local (offline-first) database. Schema export is intentionally off for
 * this first version ([exportSchema] = false) since there is no prior version to migrate from
 * yet; turn it on (with a schema directory configured) once a migration is first needed.
 */
@Database(
    entities = [
        ProductEntity::class,
        CustomerEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        PaymentEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class,
        CashSessionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun customerDao(): CustomerDao
    abstract fun orderDao(): OrderDao
    abstract fun orderItemDao(): OrderItemDao
    abstract fun paymentDao(): PaymentDao
    abstract fun debtDao(): DebtDao
    abstract fun debtPaymentDao(): DebtPaymentDao
    abstract fun cashSessionDao(): CashSessionDao

    companion object {
        private const val DATABASE_NAME = "opencomanda.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                .build()
    }
}
