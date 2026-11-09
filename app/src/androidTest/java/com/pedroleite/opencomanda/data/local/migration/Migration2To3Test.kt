package com.pedroleite.opencomanda.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Verifies [MIGRATION_2_3] against a real version-2 database: every existing business row survives
 * untouched and the new sign-in tables arrive empty (the first launch after the update then asks for
 * the owner to be created, exactly like a fresh install).
 */
class Migration2To3Test {

    private val testDbName = "migration-2-3-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    private fun seedVersion2() {
        helper.createDatabase(testDbName, 2).apply {
            execSQL("INSERT INTO categories (id, name, active, createdAt, updatedAt) VALUES (1, 'Espetinhos', 1, 1000, 1000)")
            execSQL(
                "INSERT INTO products (id, name, description, priceCents, costCents, stockQuantity, trackStock, active, createdAt, updatedAt, categoryId) " +
                    "VALUES (1, 'Espetinho', 'Carne', 1250, 500, 18.0, 1, 1, 1000, 2000, 1)",
            )
            execSQL("INSERT INTO customers (id, name, phone, notes, active, createdAt, updatedAt) VALUES (1, 'Carla', '11999990000', NULL, 1, 1000, 1000)")
            execSQL("INSERT INTO cash_sessions (id, openedAt, closedAt, openingBalanceCents, closingBalanceCents, status, notes) VALUES (1, 1000, NULL, 10000, NULL, 'OPEN', NULL)")
            execSQL("INSERT INTO orders (id, customerId, displayName, orderType, status, openedAt, closedAt) VALUES (1, 1, 'Mesa 4', 'COMANDA', 'CLOSED', 1000, 3000)")
            execSQL(
                "INSERT INTO order_items (id, orderId, productId, productNameSnapshot, unitPriceCentsSnapshot, quantity, subtotalCents) " +
                    "VALUES (1, 1, 1, 'Espetinho', 1250, 2.0, 2500)",
            )
            execSQL("INSERT INTO payments (id, orderId, cashSessionId, method, amountCents, createdAt) VALUES (1, 1, 1, 'PIX', 2500, 3000)")
            execSQL("INSERT INTO debts (id, customerId, orderId, originalAmountCents, notes, status, createdAt) VALUES (1, 1, NULL, 3000, NULL, 'OPEN', 4000)")
            execSQL("INSERT INTO debt_payments (id, debtId, cashSessionId, amountCents, method, paidAt) VALUES (1, 1, 1, 1000, 'CASH', 5000)")
            close()
        }
    }

    @Test
    fun migrate2To3KeepsEveryBusinessRow() {
        seedVersion2()

        val db = helper.runMigrationsAndValidate(testDbName, 3, true, MIGRATION_2_3)

        listOf("categories", "products", "customers", "cash_sessions", "orders", "order_items", "payments", "debts", "debt_payments")
            .forEach { assertEquals(it, 1, count(db, it)) }
        db.query("SELECT name, priceCents, stockQuantity, categoryId FROM products WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("Espetinho", it.getString(0))
            assertEquals(1250L, it.getLong(1))
            assertEquals(18.0, it.getDouble(2), 0.0)
            assertEquals(1L, it.getLong(3))
        }
        db.query("SELECT productId, subtotalCents FROM order_items WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(1L, it.getLong(0))
            assertEquals(2500L, it.getLong(1))
        }
        db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
    }

    @Test
    fun migrate2To3CreatesEmptySignInTables() {
        seedVersion2()

        val db = helper.runMigrationsAndValidate(testDbName, 3, true, MIGRATION_2_3)

        listOf("roles", "role_permissions", "users", "security_settings", "audit_log").forEach { assertEquals(it, 0, count(db, it)) }
    }

    @Test
    fun migrateAllTheWayFrom1To3() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                "INSERT INTO products (id, name, description, priceCents, costCents, stockQuantity, trackStock, active, createdAt, updatedAt) " +
                    "VALUES (1, 'Espetinho', NULL, 1250, NULL, 0.0, 0, 1, 1000, 1000)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        assertEquals(1, count(db, "products"))
        assertEquals(0, count(db, "users"))
    }
}
