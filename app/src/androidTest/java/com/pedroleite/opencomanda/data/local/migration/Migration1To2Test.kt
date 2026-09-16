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
}
