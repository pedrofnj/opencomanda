package com.pedroleite.opencomanda.data.repository

import androidx.room.withTransaction
import com.pedroleite.opencomanda.data.auth.AccessControl
import com.pedroleite.opencomanda.data.auth.SessionManager
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.dao.AuditLogDao
import com.pedroleite.opencomanda.data.local.dao.RoleDao
import com.pedroleite.opencomanda.data.local.dao.UserDao
import com.pedroleite.opencomanda.data.local.entity.AuditLogEntity
import com.pedroleite.opencomanda.data.local.entity.RoleEntity
import com.pedroleite.opencomanda.data.local.entity.RolePermissionEntity
import com.pedroleite.opencomanda.data.local.entity.UserEntity
import com.pedroleite.opencomanda.domain.auth.AuditAction
import com.pedroleite.opencomanda.domain.auth.CredentialHasher
import com.pedroleite.opencomanda.domain.auth.CredentialRules
import com.pedroleite.opencomanda.domain.auth.Permission
import com.pedroleite.opencomanda.domain.auth.PermissionPolicy
import com.pedroleite.opencomanda.domain.auth.SystemRole
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** A user as the management screens show them — never with credential hashes. */
data class ManagedUser(
    val id: Long,
    val displayName: String,
    val username: String,
    val roleId: Long,
    val roleName: String,
    val systemRole: SystemRole?,
    val isOwner: Boolean,
    val active: Boolean,
    val lockedUntil: Long?,
)

data class RoleWithPermissions(
    val role: RoleEntity,
    val permissions: Set<Permission>,
    val userCount: Int,
) {
    val isSystem: Boolean get() = role.systemRole != null
}

/**
 * Administration of users and profiles. Every operation requires [Permission.USERS_MANAGE], which
 * only the Administrador profile can hold ([PermissionPolicy.isGrantable]). On top of that:
 * - the owner can't be deactivated, moved to another profile, or have their credentials reset by
 *   anyone but themselves — so no other Administrator can take over or lock out the business owner;
 * - nobody can deactivate themselves or change their own profile;
 * - the Administrador profile always holds everything and can't be edited; Gerente, Vendedor and
 *   custom profiles can be edited, but only with permissions their kind of profile may hold;
 * - built-in profiles can't be deleted or renamed (their names are shown in the device language).
 */
class UserManagementRepository(
    private val database: AppDatabase,
    private val userDao: UserDao,
    private val roleDao: RoleDao,
    private val auditLogDao: AuditLogDao,
    private val session: SessionManager,
    private val access: AccessControl,
    private val hasher: CredentialHasher = CredentialHasher(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val hashDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    fun observeUsers(): Flow<List<ManagedUser>> =
        combine(userDao.observeAll(), roleDao.observeAll()) { users, roles ->
            val rolesById = roles.associateBy { it.id }
            users.map { it.toManaged(rolesById[it.roleId]) }
        }

    fun observeRoles(): Flow<List<RoleWithPermissions>> =
        combine(roleDao.observeAll(), roleDao.observeAllPermissions(), userDao.observeAll()) { roles, permissions, users ->
            val byRole = permissions.groupBy { it.roleId }
            val usersByRole = users.groupingBy { it.roleId }.eachCount()
            roles.map { role ->
                RoleWithPermissions(
                    role = role,
                    permissions = byRole[role.id].orEmpty().mapNotNull { Permission.fromKey(it.permission) }.toSet(),
                    userCount = usersByRole[role.id] ?: 0,
                )
            }
        }

    fun observeAuditLog(limit: Int = 300): Flow<List<AuditLogEntity>> = auditLogDao.observeRecent(limit)

    suspend fun getUser(id: Long): ManagedUser? {
        val user = userDao.getById(id) ?: return null
        return user.toManaged(roleDao.getById(user.roleId))
    }

    suspend fun createUser(displayName: String, username: String, password: String, pin: String, roleId: Long): Long {
        access.require(Permission.USERS_MANAGE)
        validateName(displayName)
        validateUsername(username)
        validatePassword(password)
        validatePin(pin)
        val normalized = CredentialRules.normalizeUsername(username)
        val (passwordHash, pinHash) = withContext(hashDispatcher) { hasher.hash(password) to hasher.hash(pin) }
        return database.withTransaction {
            if (userDao.getByUsername(normalized) != null) throw AuthException(AuthError.DUPLICATE_USERNAME)
            val role = roleDao.getById(roleId) ?: throw AuthException(AuthError.ROLE_NOT_FOUND)
            val now = nowMillis()
            val id = userDao.insert(
                UserEntity(
                    displayName = displayName.trim(),
                    username = normalized,
                    passwordHash = passwordHash,
                    pinHash = pinHash,
                    roleId = role.id,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            access.audit(AuditAction.USER_CREATED, "${displayName.trim()} ($normalized) · ${role.name}")
            id
        }
    }

    suspend fun updateUser(id: Long, displayName: String, roleId: Long) {
        access.require(Permission.USERS_MANAGE)
        validateName(displayName)
        database.withTransaction {
            val user = userDao.getById(id) ?: throw AuthException(AuthError.USER_NOT_FOUND)
            val role = roleDao.getById(roleId) ?: throw AuthException(AuthError.ROLE_NOT_FOUND)
            if (role.id != user.roleId) {
                if (user.isOwner) throw AuthException(AuthError.OWNER_PROTECTED)
                if (user.id == session.activeUser()?.id) throw AuthException(AuthError.SELF_PROTECTED)
            }
            userDao.update(user.copy(displayName = displayName.trim(), roleId = role.id, updatedAt = nowMillis()))
            access.audit(AuditAction.USER_UPDATED, "${displayName.trim()} · ${role.name}")
        }
    }

    suspend fun setUserActive(id: Long, active: Boolean) {
        access.require(Permission.USERS_MANAGE)
        database.withTransaction {
            val user = userDao.getById(id) ?: throw AuthException(AuthError.USER_NOT_FOUND)
            if (!active) {
                if (user.isOwner) throw AuthException(AuthError.OWNER_PROTECTED)
                if (user.id == session.activeUser()?.id) throw AuthException(AuthError.SELF_PROTECTED)
            }
            if (user.active == active) return@withTransaction
            userDao.update(user.copy(active = active, updatedAt = nowMillis()))
            access.audit(if (active) AuditAction.USER_REACTIVATED else AuditAction.USER_DEACTIVATED, user.displayName)
        }
    }

    /** Sets a new password for someone who forgot theirs, and lifts any lockout. */
    suspend fun resetPassword(id: Long, newPassword: String) {
        access.require(Permission.USERS_MANAGE)
        validatePassword(newPassword)
        val hash = withContext(hashDispatcher) { hasher.hash(newPassword) }
        resetCredential(id, AuditAction.PASSWORD_RESET) { it.copy(passwordHash = hash) }
    }

    /** Sets a new PIN for someone who forgot theirs, and lifts any lockout. */
    suspend fun resetPin(id: Long, newPin: String) {
        access.require(Permission.USERS_MANAGE)
        validatePin(newPin)
        val hash = withContext(hashDispatcher) { hasher.hash(newPin) }
        resetCredential(id, AuditAction.PIN_RESET) { it.copy(pinHash = hash) }
    }

    private suspend fun resetCredential(id: Long, action: AuditAction, change: (UserEntity) -> UserEntity) {
        database.withTransaction {
            val user = userDao.getById(id) ?: throw AuthException(AuthError.USER_NOT_FOUND)
            // Only the owner may change the owner's credentials (Change password, or the recovery key).
            if (user.isOwner && session.activeUser()?.id != user.id) throw AuthException(AuthError.OWNER_PROTECTED)
            userDao.update(change(user).copy(failedAttempts = 0, lockedUntil = null, updatedAt = nowMillis()))
            access.audit(action, user.displayName)
        }
    }

    suspend fun createRole(name: String, permissions: Set<Permission>): Long {
        access.require(Permission.USERS_MANAGE)
        validateName(name)
        validateGrantable(null, permissions)
        return database.withTransaction {
            ensureUniqueRoleName(name, excludeId = null)
            val now = nowMillis()
            val id = roleDao.insert(RoleEntity(name = name.trim(), systemRole = null, createdAt = now, updatedAt = now))
            roleDao.insertPermissions(permissions.map { RolePermissionEntity(id, it.key) })
            access.audit(AuditAction.ROLE_CREATED, describe(name, permissions))
            id
        }
    }

    /**
     * Changes a profile's permissions — and, for a custom profile, its name (built-in profiles keep
     * theirs). Applies to the next sign-in of each user with this profile.
     */
    suspend fun updateRole(id: Long, name: String, permissions: Set<Permission>) {
        access.require(Permission.USERS_MANAGE)
        database.withTransaction {
            val role = roleDao.getById(id) ?: throw AuthException(AuthError.ROLE_NOT_FOUND)
            if (!PermissionPolicy.isEditable(role.systemRole)) throw AuthException(AuthError.SYSTEM_ROLE_READ_ONLY)
            validateGrantable(role.systemRole, permissions)
            val newName = if (role.systemRole == null) {
                validateName(name)
                ensureUniqueRoleName(name, excludeId = id)
                name.trim()
            } else {
                role.name
            }
            roleDao.update(role.copy(name = newName, updatedAt = nowMillis()))
            roleDao.clearPermissions(id)
            roleDao.insertPermissions(permissions.map { RolePermissionEntity(id, it.key) })
            access.audit(AuditAction.ROLE_UPDATED, describe(newName, permissions))
        }
    }

    suspend fun deleteRole(id: Long) {
        access.require(Permission.USERS_MANAGE)
        database.withTransaction {
            val role = roleDao.getById(id) ?: throw AuthException(AuthError.ROLE_NOT_FOUND)
            if (role.systemRole != null) throw AuthException(AuthError.SYSTEM_ROLE_READ_ONLY)
            if (roleDao.countUsers(id) > 0) throw AuthException(AuthError.ROLE_IN_USE)
            roleDao.delete(id)
            access.audit(AuditAction.ROLE_DELETED, role.name)
        }
    }

    private fun validateGrantable(role: SystemRole?, permissions: Set<Permission>) {
        if (permissions.any { !PermissionPolicy.isGrantable(role, it) }) {
            throw AuthException(AuthError.PERMISSION_NOT_GRANTABLE)
        }
    }

    private suspend fun ensureUniqueRoleName(name: String, excludeId: Long?) {
        val normalized = name.trim().lowercase()
        if (roleDao.getAll().any { it.id != excludeId && it.name.trim().lowercase() == normalized }) {
            throw AuthException(AuthError.DUPLICATE_ROLE_NAME)
        }
    }

    private fun describe(name: String, permissions: Set<Permission>): String =
        "${name.trim()}: " + permissions.sortedBy { it.ordinal }.joinToString(", ") { it.key }

    private fun UserEntity.toManaged(role: RoleEntity?) = ManagedUser(
        id = id,
        displayName = displayName,
        username = username,
        roleId = roleId,
        roleName = role?.name.orEmpty(),
        systemRole = role?.systemRole,
        isOwner = isOwner,
        active = active,
        lockedUntil = lockedUntil,
    )
}
