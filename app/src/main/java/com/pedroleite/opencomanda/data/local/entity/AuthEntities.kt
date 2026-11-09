package com.pedroleite.opencomanda.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pedroleite.opencomanda.domain.auth.AuditAction
import com.pedroleite.opencomanda.domain.auth.SystemRole

/**
 * An access profile. [systemRole] is set for the three built-in profiles (Administrador, Gerente,
 * Vendedor), which start from [com.pedroleite.opencomanda.domain.auth.PermissionPolicy.defaultPermissions]
 * and can't be deleted; it is null for profiles an Administrator creates.
 */
@Entity(tableName = "roles")
data class RoleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val systemRole: SystemRole? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** One permission held by a role, stored by its stable key ([com.pedroleite.opencomanda.domain.auth.Permission.key]). */
@Entity(
    tableName = "role_permissions",
    primaryKeys = ["roleId", "permission"],
    foreignKeys = [
        ForeignKey(entity = RoleEntity::class, parentColumns = ["id"], childColumns = ["roleId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("roleId")],
)
data class RolePermissionEntity(
    val roleId: Long,
    val permission: String,
)

/**
 * Someone who can sign in on this device. Only hashes of the password and PIN are stored (see
 * [com.pedroleite.opencomanda.domain.auth.CredentialHasher]). [isOwner] marks the Administrator created
 * at first launch: that account can't be deactivated or moved to another profile, so the business
 * can never lock itself out. Users are deactivated, never deleted, so the audit log keeps its names.
 */
@Entity(
    tableName = "users",
    foreignKeys = [
        ForeignKey(entity = RoleEntity::class, parentColumns = ["id"], childColumns = ["roleId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index(value = ["username"], unique = true), Index("roleId")],
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    /** Lower-cased; see [com.pedroleite.opencomanda.domain.auth.CredentialRules.normalizeUsername]. */
    val username: String,
    val passwordHash: String,
    val pinHash: String,
    val roleId: Long,
    @ColumnInfo(defaultValue = "0")
    val isOwner: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val active: Boolean = true,
    /** Consecutive failed sign-ins; reset on success. Drives [lockedUntil]. */
    @ColumnInfo(defaultValue = "0")
    val failedAttempts: Int = 0,
    val lockedUntil: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Device-wide settings — a single row with [id] = [SINGLETON_ID]. */
@Entity(tableName = "security_settings")
data class SecuritySettingsEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val establishmentName: String,
    /** Minutes without interaction before the session locks. */
    val lockTimeoutMinutes: Int,
    /** Hash of the owner's one-time recovery key; replaced each time the key is used or regenerated. */
    val recoveryKeyHash: String,
    @ColumnInfo(defaultValue = "0")
    val recoveryFailedAttempts: Int = 0,
    val recoveryLockedUntil: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

/** A sensitive action, kept for the owner to review. [userName] is a snapshot, so the entry stays
 *  readable even if the account is later renamed. */
@Entity(
    tableName = "audit_log",
    foreignKeys = [
        ForeignKey(entity = UserEntity::class, parentColumns = ["id"], childColumns = ["userId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("userId"), Index("createdAt")],
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long?,
    val userName: String?,
    val action: AuditAction,
    val details: String? = null,
    val createdAt: Long,
)
