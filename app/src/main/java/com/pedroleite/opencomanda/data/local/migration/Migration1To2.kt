package com.pedroleite.opencomanda.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Introduces product categories: a new `categories` table, and an optional `categoryId` on
 * `products` referencing it (`ON DELETE SET NULL`, never cascading). The `products` table is
 * recreated rather than altered in place — the standard, safest way to add a foreign key column
 * to an existing SQLite table — and every existing row is copied over unchanged, with
 * `categoryId` defaulting to `NULL`. No data is lost.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `active` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `products_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `priceCents` INTEGER NOT NULL,
                `costCents` INTEGER,
                `stockQuantity` REAL NOT NULL DEFAULT 0,
                `trackStock` INTEGER NOT NULL DEFAULT 0,
                `active` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `categoryId` INTEGER,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT INTO `products_new`
                (`id`, `name`, `description`, `priceCents`, `costCents`, `stockQuantity`, `trackStock`, `active`, `createdAt`, `updatedAt`, `categoryId`)
            SELECT `id`, `name`, `description`, `priceCents`, `costCents`, `stockQuantity`, `trackStock`, `active`, `createdAt`, `updatedAt`, NULL
            FROM `products`
            """.trimIndent(),
        )

        db.execSQL("DROP TABLE `products`")
        db.execSQL("ALTER TABLE `products_new` RENAME TO `products`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_categoryId` ON `products` (`categoryId`)")
    }
}
