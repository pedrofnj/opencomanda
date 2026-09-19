package com.pedroleite.opencomanda.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Verifies [MIGRATION_1_2] against a real historical (version 1) database — not just a freshly
 * created one — proving existing product rows survive the migration to introduce categories.
 */
class Migration1To2Test {

    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2PreservesAnExistingProductAndAllItsFields() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """
                INSERT INTO products
                    (id, name, description, priceCents, costCents, stockQuantity, trackStock, active, createdAt, updatedAt)
                VALUES
                    (1, 'Espetinho Especial', 'Corte especial', 1250, 500, 24.0, 1, 1, 1000, 2000)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        val cursor = db.query("SELECT * FROM products WHERE id = 1")
        assertEquals(1, cursor.count)
        cursor.moveToFirst()
        assertEquals("Espetinho Especial", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        assertEquals("Corte especial", cursor.getString(cursor.getColumnIndexOrThrow("description")))
        assertEquals(1250L, cursor.getLong(cursor.getColumnIndexOrThrow("priceCents")))
        assertEquals(500L, cursor.getLong(cursor.getColumnIndexOrThrow("costCents")))
        assertEquals(24.0, cursor.getDouble(cursor.getColumnIndexOrThrow("stockQuantity")), 0.0)
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("trackStock")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("active")))
        assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
        assertEquals(2000L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
        assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("categoryId")))
        cursor.close()
    }

    @Test
    fun migrate1To2PreservesMultipleExistingProductsAndCreatesAnEmptyCategoriesTable() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """
                INSERT INTO products
                    (id, name, description, priceCents, costCents, stockQuantity, trackStock, active, createdAt, updatedAt)
                VALUES
                    (1, 'Espetinho', NULL, 1050, NULL, 0.0, 0, 1, 1000, 1000),
                    (2, 'Refrigerante', NULL, 500, NULL, 0.0, 0, 0, 1000, 1000)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        val productCount = db.query("SELECT COUNT(*) FROM products")
        productCount.moveToFirst()
        assertEquals(2, productCount.getInt(0))
        productCount.close()

        val categoryCount = db.query("SELECT COUNT(*) FROM categories")
        categoryCount.moveToFirst()
        assertEquals(0, categoryCount.getInt(0))
        categoryCount.close()
    }

    /** Seeds a v1 database with an order whose lines point at products, the case the migration's
     *  `DROP TABLE products` could otherwise damage through `order_items`' ON DELETE SET NULL. */
    private fun createV1DatabaseWithOrderItems() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """
                INSERT INTO products
                    (id, name, description, priceCents, costCents, stockQuantity, trackStock, active, createdAt, updatedAt)
                VALUES
                    (1, 'Espetinho', NULL, 1250, NULL, 24.0, 1, 1, 1000, 2000),
                    (2, 'Refrigerante', NULL, 500, NULL, 0.0, 0, 1, 1000, 1000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO orders (id, customerId, displayName, orderType, status, openedAt, closedAt)
                VALUES (1, NULL, 'Mesa 1', 'COMANDA', 'CLOSED', 1000, 3000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO order_items
                    (id, orderId, productId, productNameSnapshot, unitPriceCentsSnapshot, quantity, subtotalCents)
                VALUES
                    (1, 1, 1, 'Espetinho', 1250, 2.0, 2500),
                    (2, 1, 2, 'Refrigerante', 500, 1.0, 500),
                    (3, 1, NULL, 'Produto removido', 300, 1.0, 300)
                """.trimIndent(),
            )
            close()
        }
    }

    @Test
    fun migrate1To2KeepsEveryOrderItemsProductReference() {
        createV1DatabaseWithOrderItems()

        val db = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        val cursor = db.query("SELECT id, productId, productNameSnapshot, subtotalCents FROM order_items ORDER BY id")
        assertEquals(3, cursor.count)
        cursor.moveToFirst()
        assertEquals(1L, cursor.getLong(1))
        assertEquals("Espetinho", cursor.getString(2))
        assertEquals(2500L, cursor.getLong(3))
        cursor.moveToNext()
        assertEquals(2L, cursor.getLong(1))
        cursor.moveToNext()
        assertTrue("A line that had no product must stay without one", cursor.isNull(1))
        cursor.close()

        val violations = db.query("PRAGMA foreign_key_check")
        assertEquals("No dangling foreign key after migrating", 0, violations.count)
        violations.close()
    }

    @Test
    fun migrate1To2KeepsOrderItemsProductForeignKeyActive() {
        createV1DatabaseWithOrderItems()

        val db = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM products WHERE id = 1")

        val cursor = db.query("SELECT productId FROM order_items WHERE id = 1")
        cursor.moveToFirst()
        assertTrue("ON DELETE SET NULL must still apply to the recreated products table", cursor.isNull(0))
        cursor.close()
    }
}
