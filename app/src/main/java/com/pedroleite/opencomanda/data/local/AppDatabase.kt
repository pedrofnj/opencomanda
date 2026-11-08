package com.pedroleite.opencomanda.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pedroleite.opencomanda.data.local.dao.AuditLogDao
import com.pedroleite.opencomanda.data.local.dao.CashSessionDao
import com.pedroleite.opencomanda.data.local.dao.CategoryDao
import com.pedroleite.opencomanda.data.local.dao.CustomerDao
import com.pedroleite.opencomanda.data.local.dao.DebtDao
import com.pedroleite.opencomanda.data.local.dao.DebtPaymentDao
import com.pedroleite.opencomanda.data.local.dao.OrderDao
import com.pedroleite.opencomanda.data.local.dao.OrderItemDao
import com.pedroleite.opencomanda.data.local.dao.PaymentDao
import com.pedroleite.opencomanda.data.local.dao.ProductDao
import com.pedroleite.opencomanda.data.local.dao.RoleDao
import com.pedroleite.opencomanda.data.local.dao.SecuritySettingsDao
import com.pedroleite.opencomanda.data.local.dao.UserDao
import com.pedroleite.opencomanda.data.local.entity.AuditLogEntity
import com.pedroleite.opencomanda.data.local.entity.CashSessionEntity
import com.pedroleite.opencomanda.data.local.entity.CategoryEntity
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.data.local.entity.DebtEntity
import com.pedroleite.opencomanda.data.local.entity.DebtPaymentEntity
import com.pedroleite.opencomanda.data.local.entity.OrderEntity
import com.pedroleite.opencomanda.data.local.entity.OrderItemEntity
import com.pedroleite.opencomanda.data.local.entity.PaymentEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.data.local.entity.RoleEntity
import com.pedroleite.opencomanda.data.local.entity.RolePermissionEntity
import com.pedroleite.opencomanda.data.local.entity.SecuritySettingsEntity
import com.pedroleite.opencomanda.data.local.entity.UserEntity
import com.pedroleite.opencomanda.data.local.migration.MIGRATION_1_2
import com.pedroleite.opencomanda.data.local.migration.MIGRATION_2_3

/**
 * OpenComanda's single local (offline-first) database. Schema is exported to app/schemas so
 * each version is snapshotted and real migrations (see [MIGRATION_1_2], [MIGRATION_2_3]) can be tested against
 * the actual historical schema, not just a freshly created database.
 */
@Database(
    entities = [
        ProductEntity::class,
        CategoryEntity::class,
        CustomerEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        PaymentEntity::class,
        DebtEntity::class,
        DebtPaymentEntity::class,
        CashSessionEntity::class,
        RoleEntity::class,
        RolePermissionEntity::class,
        UserEntity::class,
        SecuritySettingsEntity::class,
        AuditLogEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun customerDao(): CustomerDao
    abstract fun orderDao(): OrderDao
    abstract fun orderItemDao(): OrderItemDao
    abstract fun paymentDao(): PaymentDao
    abstract fun debtDao(): DebtDao
    abstract fun debtPaymentDao(): DebtPaymentDao
    abstract fun cashSessionDao(): CashSessionDao
    abstract fun roleDao(): RoleDao
    abstract fun userDao(): UserDao
    abstract fun securitySettingsDao(): SecuritySettingsDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        private const val DATABASE_NAME = "opencomanda.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
