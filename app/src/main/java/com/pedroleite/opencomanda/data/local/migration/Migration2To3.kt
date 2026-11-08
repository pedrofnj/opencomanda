package com.pedroleite.opencomanda.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Introduces local sign-in and access control: profiles (`roles`) and their permissions, `users`,
 * the device-wide `security_settings` row and the `audit_log`. Only new tables are created — no
 * existing table or row is touched, so every product, order, payment, debt and cash session survives
 * as-is.
 *
 * The tables start empty on purpose: the three built-in profiles are created, with the initial
 * matrix, by [com.pedroleite.opencomanda.data.repository.AuthRepository.ensureSystemRoles] at the first
 * launch after the update, and the first Administrator is created by the first-run setup — the same path a fresh
 * install takes.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `roles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`systemRole` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `role_permissions` (`roleId` INTEGER NOT NULL, `permission` TEXT NOT NULL, " +
                "PRIMARY KEY(`roleId`, `permission`), " +
                "FOREIGN KEY(`roleId`) REFERENCES `roles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_role_permissions_roleId` ON `role_permissions` (`roleId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `users` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `displayName` TEXT NOT NULL, " +
                "`username` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, `pinHash` TEXT NOT NULL, `roleId` INTEGER NOT NULL, " +
                "`isOwner` INTEGER NOT NULL DEFAULT 0, `active` INTEGER NOT NULL DEFAULT 1, " +
                "`failedAttempts` INTEGER NOT NULL DEFAULT 0, `lockedUntil` INTEGER, `createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`roleId`) REFERENCES `roles`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_username` ON `users` (`username`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_users_roleId` ON `users` (`roleId`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `security_settings` (`id` INTEGER NOT NULL, `establishmentName` TEXT NOT NULL, " +
                "`lockTimeoutMinutes` INTEGER NOT NULL, `recoveryKeyHash` TEXT NOT NULL, " +
                "`recoveryFailedAttempts` INTEGER NOT NULL DEFAULT 0, `recoveryLockedUntil` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `audit_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `userId` INTEGER, " +
                "`userName` TEXT, `action` TEXT NOT NULL, `details` TEXT, `createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_userId` ON `audit_log` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_createdAt` ON `audit_log` (`createdAt`)")
    }
}
