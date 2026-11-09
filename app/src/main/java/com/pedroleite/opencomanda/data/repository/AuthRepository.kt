package com.pedroleite.opencomanda.data.repository

import androidx.room.withTransaction
import com.pedroleite.opencomanda.data.auth.AuthenticatedUser
import com.pedroleite.opencomanda.data.auth.PermissionDeniedException
import com.pedroleite.opencomanda.data.auth.SessionAccessControl
import com.pedroleite.opencomanda.data.auth.SessionManager
import com.pedroleite.opencomanda.data.auth.SessionState
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.dao.AuditLogDao
import com.pedroleite.opencomanda.data.local.dao.RoleDao
import com.pedroleite.opencomanda.data.local.dao.SecuritySettingsDao
import com.pedroleite.opencomanda.data.local.dao.UserDao
import com.pedroleite.opencomanda.data.local.entity.AuditLogEntity
import com.pedroleite.opencomanda.data.local.entity.RoleEntity
import com.pedroleite.opencomanda.data.local.entity.RolePermissionEntity
import com.pedroleite.opencomanda.data.local.entity.SecuritySettingsEntity
import com.pedroleite.opencomanda.data.local.entity.UserEntity
import com.pedroleite.opencomanda.domain.auth.AuditAction
import com.pedroleite.opencomanda.domain.auth.CredentialHasher
import com.pedroleite.opencomanda.domain.auth.CredentialRules
import com.pedroleite.opencomanda.domain.auth.LockoutPolicy
import com.pedroleite.opencomanda.domain.auth.Permission
import com.pedroleite.opencomanda.domain.auth.PermissionPolicy
import com.pedroleite.opencomanda.domain.auth.RecoveryKeys
import com.pedroleite.opencomanda.domain.auth.SystemRole
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Why an account or profile operation was refused — mapped to a localized message by the UI. */
enum class AuthError {
    BLANK_NAME,
    INVALID_USERNAME,
    DUPLICATE_USERNAME,
    INVALID_PASSWORD,
    INVALID_PIN,
    WRONG_PASSWORD,
    SETUP_ALREADY_DONE,
    OWNER_PROTECTED,
    SELF_PROTECTED,
    USER_NOT_FOUND,
    ROLE_NOT_FOUND,
    SYSTEM_ROLE_READ_ONLY,
    PERMISSION_NOT_GRANTABLE,
    DUPLICATE_ROLE_NAME,
    ROLE_IN_USE,
}

class AuthException(val error: AuthError) : IllegalArgumentException(error.name)

/** An active user as the sign-in screen lists them — never with credential hashes. */
data class SignInCandidate(val id: Long, val displayName: String, val username: String)

/** The device-wide settings an Administrator can see and change. */
data class SecuritySettings(val establishmentName: String, val lockTimeoutMinutes: Int)

sealed interface SignInResult {
    data class Success(val user: AuthenticatedUser) : SignInResult

    /** Deliberately the same whether the username, the password or the account state was wrong. */
    data object InvalidCredentials : SignInResult

    data class LockedOut(val untilMillis: Long) : SignInResult
}

sealed interface RecoveryResult {
    /** The owner is signed in with the new credentials; [newRecoveryKey] replaces the one just used. */
    data class Success(val newRecoveryKey: String) : RecoveryResult

    data object InvalidKey : RecoveryResult

    data class LockedOut(val untilMillis: Long) : RecoveryResult
}

/**
 * Local sign-in: first-run setup of the owner, sign-in by password or PIN, the inactivity lock, the
 * owner's one-time recovery key, a user's own password/PIN changes and the device settings.
 *
 * Every secret is hashed off the main thread ([hashDispatcher]) — PBKDF2 is deliberately slow.
 * Failed attempts are counted per account and lock it for a growing period ([LockoutPolicy]);
 * recovery-key attempts are counted on the settings row the same way.
 */
class AuthRepository(
    private val database: AppDatabase,
    private val userDao: UserDao,
    private val roleDao: RoleDao,
    private val settingsDao: SecuritySettingsDao,
    private val auditLogDao: AuditLogDao,
    private val session: SessionManager,
    private val hasher: CredentialHasher = CredentialHasher(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val hashDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val access = SessionAccessControl(session, auditLogDao, nowMillis)

    /** Run at launch: brings the built-in profiles up to date and decides between setup and sign-in. */
    suspend fun initialize() {
        ensureSystemRoles()
        if (userDao.count() == 0) {
            session.setNeedsSetup()
        } else if (session.state.value == SessionState.Loading) {
            session.setSignedOut()
        }
    }

    /**
     * Creates any missing built-in profile with the initial matrix ([PermissionPolicy.defaultPermissions]).
     * Existing profiles keep the permissions an Administrator gave them; only the rules that can never
     * be broken are enforced here: the Administrador profile holds every permission (so the owner can
     * never be locked out), and no profile keeps a permission it isn't allowed to hold.
     */
    suspend fun ensureSystemRoles() {
        database.withTransaction {
            val now = nowMillis()
            for (systemRole in SystemRole.entries) {
                val existing = roleDao.getBySystemRole(systemRole)
                if (existing == null) {
                    val id = roleDao.insert(RoleEntity(name = defaultRoleName(systemRole), systemRole = systemRole, createdAt = now, updatedAt = now))
                    roleDao.insertPermissions(PermissionPolicy.defaultPermissions(systemRole).map { RolePermissionEntity(id, it.key) })
                    continue
                }
                val held = roleDao.getPermissionKeys(existing.id).mapNotNull { Permission.fromKey(it) }.toSet()
                val allowed = if (systemRole == SystemRole.ADMIN) {
                    Permission.entries.toSet()
                } else {
                    held.filter { PermissionPolicy.isGrantable(systemRole, it) }.toSet()
                }
                if (allowed != held) {
                    roleDao.clearPermissions(existing.id)
                    roleDao.insertPermissions(allowed.map { RolePermissionEntity(existing.id, it.key) })
                }
            }
        }
    }

    fun observeSignInCandidates(): Flow<List<SignInCandidate>> =
        userDao.observeActive().map { users -> users.map { SignInCandidate(it.id, it.displayName, it.username) } }

    fun observeSettings(): Flow<SecuritySettings?> =
        settingsDao.observe().map { it?.let { s -> SecuritySettings(s.establishmentName, s.lockTimeoutMinutes) } }

    /**
     * First launch: creates the owner (an Administrator who can't be deactivated) and the settings
     * row, and signs the owner in. Returns the one-time recovery key — shown once, stored only hashed.
     */
    suspend fun completeSetup(
        establishmentName: String,
        displayName: String,
        username: String,
        password: String,
        pin: String,
    ): String {
        validateName(establishmentName)
        validateName(displayName)
        validateUsername(username)
        validatePassword(password)
        validatePin(pin)
        ensureSystemRoles()

        val recoveryKey = RecoveryKeys.generate()
        val (passwordHash, pinHash, keyHash) = withContext(hashDispatcher) {
            Triple(hasher.hash(password), hasher.hash(pin), hasher.hash(recoveryKey))
        }
        val owner = database.withTransaction {
            if (userDao.count() > 0) throw AuthException(AuthError.SETUP_ALREADY_DONE)
            val now = nowMillis()
            val adminRole = requireNotNull(roleDao.getBySystemRole(SystemRole.ADMIN))
            val id = userDao.insert(
                UserEntity(
                    displayName = displayName.trim(),
                    username = CredentialRules.normalizeUsername(username),
                    passwordHash = passwordHash,
                    pinHash = pinHash,
                    roleId = adminRole.id,
                    isOwner = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            settingsDao.insert(
                SecuritySettingsEntity(
                    establishmentName = establishmentName.trim(),
                    lockTimeoutMinutes = DEFAULT_LOCK_TIMEOUT_MINUTES,
                    recoveryKeyHash = keyHash,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val owner = requireNotNull(userDao.getById(id))
            log(owner, AuditAction.SETUP_COMPLETED)
            owner
        }
        session.start(load(owner))
        return recoveryKey
    }

    suspend fun signInWithPassword(username: String, password: String): SignInResult {
        val user = userDao.getByUsername(CredentialRules.normalizeUsername(username))
        if (user == null || !user.active) {
            // Same work and the same answer as a wrong password, so the screen can't be used to find usernames.
            withContext(hashDispatcher) { hasher.verify(password, DUMMY_HASH) }
            logAnonymous(AuditAction.LOGIN_FAILED, CredentialRules.normalizeUsername(username))
            return SignInResult.InvalidCredentials
        }
        return attempt(user, password) { it.passwordHash }
    }

    suspend fun signInWithPin(userId: Long, pin: String): SignInResult {
        val user = userDao.getById(userId)?.takeIf { it.active } ?: return SignInResult.InvalidCredentials
        return attempt(user, pin) { it.pinHash }
    }

    /** Resumes a locked session for the same person, with their PIN ([usePin]) or password. */
    suspend fun unlock(secret: String, usePin: Boolean): SignInResult {
        val locked = session.state.value as? SessionState.Locked ?: return SignInResult.InvalidCredentials
        val user = userDao.getById(locked.user.id)?.takeIf { it.active }
        if (user == null) {
            session.setSignedOut()
            return SignInResult.InvalidCredentials
        }
        return attempt(user, secret) { if (usePin) it.pinHash else it.passwordHash }
    }

    fun lock() = session.lock()

    suspend fun signOut() {
        if (session.activeUser() != null) access.audit(AuditAction.LOGOUT)
        session.setSignedOut()
    }

    /** Resets the owner's password and PIN with the one-time recovery key, then signs the owner in. */
    suspend fun recoverOwnerAccess(recoveryKey: String, newPassword: String, newPin: String): RecoveryResult {
        validatePassword(newPassword)
        validatePin(newPin)
        val settings = settingsDao.get() ?: return RecoveryResult.InvalidKey
        val now = nowMillis()
        settings.recoveryLockedUntil?.let { if (it > now) return RecoveryResult.LockedOut(it) }

        val matches = withContext(hashDispatcher) { hasher.verify(RecoveryKeys.normalize(recoveryKey), settings.recoveryKeyHash) }
        if (!matches) {
            val attempts = settings.recoveryFailedAttempts + 1
            val lockMillis = LockoutPolicy.lockDurationMillis(attempts)
            val lockedUntil = if (lockMillis > 0) now + lockMillis else null
            settingsDao.update(settings.copy(recoveryFailedAttempts = attempts, recoveryLockedUntil = lockedUntil, updatedAt = now))
            logAnonymous(AuditAction.RECOVERY_FAILED, null)
            return if (lockedUntil != null) RecoveryResult.LockedOut(lockedUntil) else RecoveryResult.InvalidKey
        }

        val newKey = RecoveryKeys.generate()
        val (passwordHash, pinHash, keyHash) = withContext(hashDispatcher) {
            Triple(hasher.hash(newPassword), hasher.hash(newPin), hasher.hash(newKey))
        }
        val owner = database.withTransaction {
            val owner = userDao.getOwner() ?: throw AuthException(AuthError.USER_NOT_FOUND)
            val updated = owner.copy(
                passwordHash = passwordHash,
                pinHash = pinHash,
                active = true,
                failedAttempts = 0,
                lockedUntil = null,
                updatedAt = now,
            )
            userDao.update(updated)
            settingsDao.update(
                settings.copy(recoveryKeyHash = keyHash, recoveryFailedAttempts = 0, recoveryLockedUntil = null, updatedAt = now),
            )
            log(updated, AuditAction.RECOVERY_USED)
            updated
        }
        session.start(load(owner))
        return RecoveryResult.Success(newKey)
    }

    /** Changes the signed-in user's own password, confirming the current one first. */
    suspend fun changeOwnPassword(currentPassword: String, newPassword: String) {
        validatePassword(newPassword)
        val user = currentUserEntity()
        verifyOrThrow(currentPassword, user.passwordHash)
        val hash = withContext(hashDispatcher) { hasher.hash(newPassword) }
        database.withTransaction {
            userDao.update(user.copy(passwordHash = hash, updatedAt = nowMillis()))
            access.audit(AuditAction.PASSWORD_CHANGED)
        }
    }

    /** Changes the signed-in user's own PIN, confirming their password first. */
    suspend fun changeOwnPin(currentPassword: String, newPin: String) {
        validatePin(newPin)
        val user = currentUserEntity()
        verifyOrThrow(currentPassword, user.passwordHash)
        val hash = withContext(hashDispatcher) { hasher.hash(newPin) }
        database.withTransaction {
            userDao.update(user.copy(pinHash = hash, updatedAt = nowMillis()))
            access.audit(AuditAction.PIN_CHANGED)
        }
    }

    /** Issues a new recovery key (the old one stops working). Only the owner can, with their password. */
    suspend fun regenerateRecoveryKey(currentPassword: String): String {
        val user = currentUserEntity()
        if (!user.isOwner) {
            access.audit(AuditAction.PERMISSION_DENIED, "recovery.regenerate")
            throw PermissionDeniedException(Permission.SETTINGS_MANAGE)
        }
        verifyOrThrow(currentPassword, user.passwordHash)
        val newKey = RecoveryKeys.generate()
        val hash = withContext(hashDispatcher) { hasher.hash(newKey) }
        database.withTransaction {
            val settings = requireNotNull(settingsDao.get())
            settingsDao.update(settings.copy(recoveryKeyHash = hash, recoveryFailedAttempts = 0, recoveryLockedUntil = null, updatedAt = nowMillis()))
            access.audit(AuditAction.RECOVERY_KEY_REGENERATED)
        }
        return newKey
    }

    suspend fun updateSettings(establishmentName: String, lockTimeoutMinutes: Int) {
        access.require(Permission.SETTINGS_MANAGE)
        validateName(establishmentName)
        require(lockTimeoutMinutes in LOCK_TIMEOUT_OPTIONS) { "Unsupported lock timeout" }
        database.withTransaction {
            val settings = requireNotNull(settingsDao.get())
            settingsDao.update(
                settings.copy(establishmentName = establishmentName.trim(), lockTimeoutMinutes = lockTimeoutMinutes, updatedAt = nowMillis()),
            )
            access.audit(AuditAction.SETTINGS_CHANGED, "${establishmentName.trim()} · $lockTimeoutMinutes min")
        }
    }

    private suspend fun attempt(user: UserEntity, secret: String, storedHash: (UserEntity) -> String): SignInResult {
        val now = nowMillis()
        user.lockedUntil?.let { if (it > now) return SignInResult.LockedOut(it) }
        val matches = withContext(hashDispatcher) { hasher.verify(secret, storedHash(user)) }
        return database.withTransaction {
            // Re-read inside the transaction so two quick failures can't both count from the same total.
            val current = userDao.getById(user.id) ?: return@withTransaction SignInResult.InvalidCredentials
            if (!matches) {
                val attempts = current.failedAttempts + 1
                val lockMillis = LockoutPolicy.lockDurationMillis(attempts)
                val lockedUntil = if (lockMillis > 0) now + lockMillis else null
                userDao.update(current.copy(failedAttempts = attempts, lockedUntil = lockedUntil, updatedAt = now))
                log(current, AuditAction.LOGIN_FAILED)
                if (lockedUntil != null) {
                    log(current, AuditAction.ACCOUNT_LOCKED)
                    SignInResult.LockedOut(lockedUntil)
                } else {
                    SignInResult.InvalidCredentials
                }
            } else {
                val updated = current.copy(failedAttempts = 0, lockedUntil = null, updatedAt = now)
                userDao.update(updated)
                log(updated, AuditAction.LOGIN_SUCCEEDED)
                SignInResult.Success(load(updated))
            }
        }.also { if (it is SignInResult.Success) session.start(it.user) }
    }

    private suspend fun currentUserEntity(): UserEntity {
        val active = session.activeUser() ?: throw PermissionDeniedException(Permission.USERS_MANAGE)
        return userDao.getById(active.id) ?: throw AuthException(AuthError.USER_NOT_FOUND)
    }

    private suspend fun verifyOrThrow(secret: String, stored: String) {
        val ok = withContext(hashDispatcher) { hasher.verify(secret, stored) }
        if (!ok) throw AuthException(AuthError.WRONG_PASSWORD)
    }

    private suspend fun load(user: UserEntity): AuthenticatedUser = loadAuthenticatedUser(user, roleDao)

    private suspend fun log(user: UserEntity, action: AuditAction, details: String? = null) {
        auditLogDao.insert(AuditLogEntity(userId = user.id, userName = user.displayName, action = action, details = details, createdAt = nowMillis()))
    }

    private suspend fun logAnonymous(action: AuditAction, details: String?) {
        auditLogDao.insert(AuditLogEntity(userId = null, userName = null, action = action, details = details, createdAt = nowMillis()))
    }

    companion object {
        const val DEFAULT_LOCK_TIMEOUT_MINUTES = 5
        val LOCK_TIMEOUT_OPTIONS = listOf(1, 2, 5, 10, 15, 30)

        /** A well-formed hash nobody's secret matches, verified when the username doesn't exist. */
        private const val DUMMY_HASH = "pbkdf2-sha256$120000\$AAAAAAAAAAAAAAAAAAAAAA==\$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="

        fun defaultRoleName(role: SystemRole): String = when (role) {
            SystemRole.ADMIN -> "Administrador"
            SystemRole.MANAGER -> "Gerente"
            SystemRole.SELLER -> "Vendedor"
        }
    }
}

internal suspend fun loadAuthenticatedUser(user: UserEntity, roleDao: RoleDao): AuthenticatedUser {
    val role = roleDao.getById(user.roleId) ?: throw AuthException(AuthError.ROLE_NOT_FOUND)
    val permissions = roleDao.getPermissionKeys(role.id).mapNotNull { Permission.fromKey(it) }.toSet()
    return AuthenticatedUser(
        id = user.id,
        displayName = user.displayName,
        username = user.username,
        roleId = role.id,
        roleName = role.name,
        systemRole = role.systemRole,
        isOwner = user.isOwner,
        permissions = permissions,
    )
}

internal fun validateName(name: String) {
    if (name.isBlank()) throw AuthException(AuthError.BLANK_NAME)
}

internal fun validateUsername(username: String) {
    if (!CredentialRules.isValidUsername(username)) throw AuthException(AuthError.INVALID_USERNAME)
}

internal fun validatePassword(password: String) {
    if (!CredentialRules.isValidPassword(password)) throw AuthException(AuthError.INVALID_PASSWORD)
}

internal fun validatePin(pin: String) {
    if (!CredentialRules.isValidPin(pin)) throw AuthException(AuthError.INVALID_PIN)
}
