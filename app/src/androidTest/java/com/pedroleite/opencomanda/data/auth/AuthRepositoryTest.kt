package com.pedroleite.opencomanda.data.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pedroleite.opencomanda.data.auth.AuthTestFixture.Companion.OWNER_PASSWORD
import com.pedroleite.opencomanda.data.auth.AuthTestFixture.Companion.OWNER_PIN
import com.pedroleite.opencomanda.data.auth.AuthTestFixture.Companion.OWNER_USERNAME
import com.pedroleite.opencomanda.data.local.entity.RolePermissionEntity
import com.pedroleite.opencomanda.data.repository.AuthError
import com.pedroleite.opencomanda.data.repository.AuthException
import com.pedroleite.opencomanda.data.repository.RecoveryResult
import com.pedroleite.opencomanda.data.repository.SignInResult
import com.pedroleite.opencomanda.domain.auth.AuditAction
import com.pedroleite.opencomanda.domain.auth.Permission
import com.pedroleite.opencomanda.domain.auth.PermissionPolicy
import com.pedroleite.opencomanda.domain.auth.SystemRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** First-run setup, sign-in, lockout, the inactivity lock and owner recovery against a real Room database. */
@RunWith(AndroidJUnit4::class)
class AuthRepositoryTest {

    private val fixture = AuthTestFixture()
    private val auth get() = fixture.auth

    @After
    fun tearDown() = fixture.close()

    private suspend fun audit(): List<AuditAction> = fixture.database.auditLogDao().getAll().map { it.action }

    @Test
    fun aFreshDatabaseNeedsSetup() = runBlocking {
        auth.initialize()
        assertEquals(SessionState.NeedsSetup, fixture.session.state.value)
    }

    @Test
    fun initializeCreatesTheThreeBuiltInProfilesWithTheMatrix() = runBlocking {
        auth.initialize()
        SystemRole.entries.forEach { role ->
            val id = fixture.roleId(role)
            val keys = fixture.database.roleDao().getPermissionKeys(id).toSet()
            assertEquals(PermissionPolicy.defaultPermissions(role).map { it.key }.toSet(), keys)
        }
    }

    @Test
    fun editedProfilesKeepTheirPermissionsAcrossLaunches() = runBlocking {
        fixture.setUpOwner()
        val sellerId = fixture.roleId(SystemRole.SELLER)
        fixture.users.updateRole(sellerId, "Vendedor", setOf(Permission.SALES_CREATE, Permission.SALES_CANCEL))

        auth.initialize()

        assertEquals(setOf(Permission.SALES_CREATE.key, Permission.SALES_CANCEL.key), fixture.database.roleDao().getPermissionKeys(sellerId).toSet())
        assertEquals(3, fixture.database.roleDao().getAll().size)
    }

    @Test
    fun launchRepairsWhatNoProfileMayHold() = runBlocking {
        auth.initialize()
        val sellerId = fixture.roleId(SystemRole.SELLER)
        val adminId = fixture.roleId(SystemRole.ADMIN)
        // Written straight to the database, bypassing the repository's checks.
        fixture.database.roleDao().insertPermissions(listOf(RolePermissionEntity(sellerId, Permission.CASH_OPEN.key)))
        fixture.database.roleDao().clearPermissions(adminId)

        auth.initialize()

        assertFalse(Permission.CASH_OPEN.key in fixture.database.roleDao().getPermissionKeys(sellerId))
        assertEquals(Permission.entries.map { it.key }.toSet(), fixture.database.roleDao().getPermissionKeys(adminId).toSet())
    }

    @Test
    fun setupCreatesTheOwnerAsAnActiveAdministratorAndSignsThemIn() = runBlocking {
        val key = fixture.setUpOwner()

        val owner = requireNotNull(fixture.database.userDao().getOwner())
        assertTrue(owner.isOwner)
        assertTrue(owner.active)
        assertEquals(fixture.roleId(SystemRole.ADMIN), owner.roleId)
        assertTrue(Regex("^[A-Z2-9]{5}(-[A-Z2-9]{5}){3}$").matches(key))
        val active = fixture.session.activeUser()
        assertEquals(owner.id, active?.id)
        assertEquals(Permission.entries.toSet(), active?.permissions)
        assertTrue(AuditAction.SETUP_COMPLETED in audit())
    }

    @Test
    fun credentialsAreStoredOnlyAsHashes() = runBlocking {
        val key = fixture.setUpOwner()
        val owner = requireNotNull(fixture.database.userDao().getOwner())
        val settings = requireNotNull(fixture.database.securitySettingsDao().get())
        assertFalse(owner.passwordHash.contains(OWNER_PASSWORD))
        assertFalse(owner.pinHash.contains(OWNER_PIN))
        assertFalse(settings.recoveryKeyHash.contains(key))
        assertTrue(owner.passwordHash.startsWith("pbkdf2-sha256$"))
    }

    @Test
    fun setupCanOnlyHappenOnce() = runBlocking<Unit> {
        fixture.setUpOwner()
        val error = assertThrows(AuthException::class.java) {
            runBlocking { auth.completeSetup("Outro", "Intruso", "intruso", "senha-intruso", "999999") }
        }
        assertEquals(AuthError.SETUP_ALREADY_DONE, error.error)
        assertEquals(1, fixture.database.userDao().count())
    }

    @Test
    fun setupRejectsInvalidInput() = runBlocking<Unit> {
        listOf(
            Triple("x", "senha-forte-1", "111111") to AuthError.INVALID_USERNAME,
            Triple("maria", "curta", "111111") to AuthError.INVALID_PASSWORD,
            Triple("maria", "senha-forte-1", "1234") to AuthError.INVALID_PIN,
        ).forEach { (input, expected) ->
            val error = assertThrows(AuthException::class.java) {
                runBlocking { auth.completeSetup("Loja", "Maria", input.first, input.second, input.third) }
            }
            assertEquals(expected, error.error)
        }
        assertEquals(0, fixture.database.userDao().count())
    }

    @Test
    fun signInWithPasswordWorksAndIgnoresUsernameCase() = runBlocking {
        fixture.setUpOwner()
        auth.signOut()

        val result = auth.signInWithPassword("  MARIA ", OWNER_PASSWORD)

        assertTrue(result is SignInResult.Success)
        assertTrue(fixture.session.state.value is SessionState.Active)
    }

    @Test
    fun aWrongPasswordOrUnknownUserGivesTheSameAnswer() = runBlocking {
        fixture.setUpOwner()
        auth.signOut()
        assertEquals(SignInResult.InvalidCredentials, auth.signInWithPassword(OWNER_USERNAME, "errada-123"))
        assertEquals(SignInResult.InvalidCredentials, auth.signInWithPassword("ninguem", "errada-123"))
        assertEquals(SessionState.SignedOut, fixture.session.state.value)
        assertTrue(AuditAction.LOGIN_FAILED in audit())
    }

    @Test
    fun fiveFailuresLockTheAccountUntilTheLockExpires() = runBlocking {
        fixture.setUpOwner()
        auth.signOut()
        val owner = requireNotNull(fixture.database.userDao().getOwner())

        repeat(4) { assertEquals(SignInResult.InvalidCredentials, auth.signInWithPin(owner.id, "000000")) }
        val fifth = auth.signInWithPin(owner.id, "000000")
        assertTrue(fifth is SignInResult.LockedOut)

        // Even the right PIN is refused while locked.
        assertTrue(auth.signInWithPin(owner.id, OWNER_PIN) is SignInResult.LockedOut)
        assertTrue(AuditAction.ACCOUNT_LOCKED in audit())

        fixture.now = (fifth as SignInResult.LockedOut).untilMillis + 1
        assertTrue(auth.signInWithPin(owner.id, OWNER_PIN) is SignInResult.Success)
        assertEquals(0, fixture.database.userDao().getById(owner.id)!!.failedAttempts)
    }

    @Test
    fun aDeactivatedUserCannotSignIn() = runBlocking {
        fixture.setUpOwner()
        val sellerId = fixture.createUser("ana", SystemRole.SELLER)
        fixture.users.setUserActive(sellerId, false)
        auth.signOut()

        assertEquals(SignInResult.InvalidCredentials, auth.signInWithPin(sellerId, "222222"))
        assertEquals(SignInResult.InvalidCredentials, auth.signInWithPassword("ana", "senha-ana"))
        assertTrue(auth.observeSignInCandidates().first().none { it.id == sellerId })
    }

    @Test
    fun aLockedSessionResumesOnlyForTheSamePersonsPin() = runBlocking {
        fixture.setUpOwner()
        auth.lock()
        assertTrue(fixture.session.state.value is SessionState.Locked)
        assertNull(fixture.session.activeUser())

        assertEquals(SignInResult.InvalidCredentials, auth.unlock("000000", usePin = true))
        assertTrue(auth.unlock(OWNER_PIN, usePin = true) is SignInResult.Success)
        assertTrue(fixture.session.state.value is SessionState.Active)
    }

    @Test
    fun theRecoveryKeyResetsTheOwnerAndWorksOnlyOnce() = runBlocking {
        val key = fixture.setUpOwner()
        auth.signOut()

        val result = auth.recoverOwnerAccess(key.lowercase().replace("-", " "), "nova-senha-1", "333333")

        assertTrue(result is RecoveryResult.Success)
        val newKey = (result as RecoveryResult.Success).newRecoveryKey
        assertNotEquals(key, newKey)
        assertTrue(fixture.session.activeUser()!!.isOwner)
        assertTrue(AuditAction.RECOVERY_USED in audit())

        auth.signOut()
        assertTrue(auth.signInWithPassword(OWNER_USERNAME, "nova-senha-1") is SignInResult.Success)
        auth.signOut()
        assertEquals(SignInResult.InvalidCredentials, auth.signInWithPassword(OWNER_USERNAME, OWNER_PASSWORD))

        // The used key is gone; the new one works.
        assertEquals(RecoveryResult.InvalidKey, auth.recoverOwnerAccess(key, "outra-senha-1", "444444"))
        assertTrue(auth.recoverOwnerAccess(newKey, "outra-senha-1", "444444") is RecoveryResult.Success)
    }

    @Test
    fun recoveryAlsoLiftsALockoutOnTheOwner() = runBlocking {
        val key = fixture.setUpOwner()
        auth.signOut()
        val owner = requireNotNull(fixture.database.userDao().getOwner())
        repeat(5) { auth.signInWithPin(owner.id, "000000") }
        assertNotNull(fixture.database.userDao().getById(owner.id)!!.lockedUntil)

        assertTrue(auth.recoverOwnerAccess(key, "nova-senha-1", "333333") is RecoveryResult.Success)
        assertNull(fixture.database.userDao().getById(owner.id)!!.lockedUntil)
    }

    @Test
    fun wrongRecoveryKeysAreRateLimited() = runBlocking {
        fixture.setUpOwner()
        auth.signOut()
        repeat(4) { assertEquals(RecoveryResult.InvalidKey, auth.recoverOwnerAccess("AAAAA-AAAAA-AAAAA-AAAAA", "nova-senha-1", "333333")) }
        assertTrue(auth.recoverOwnerAccess("AAAAA-AAAAA-AAAAA-AAAAA", "nova-senha-1", "333333") is RecoveryResult.LockedOut)
        assertTrue(AuditAction.RECOVERY_FAILED in audit())
    }

    @Test
    fun changingTheOwnPasswordRequiresTheCurrentOne() = runBlocking<Unit> {
        fixture.setUpOwner()
        val error = assertThrows(AuthException::class.java) {
            runBlocking { auth.changeOwnPassword("errada-123", "nova-senha-1") }
        }
        assertEquals(AuthError.WRONG_PASSWORD, error.error)

        auth.changeOwnPassword(OWNER_PASSWORD, "nova-senha-1")
        auth.signOut()
        assertTrue(auth.signInWithPassword(OWNER_USERNAME, "nova-senha-1") is SignInResult.Success)
        assertTrue(AuditAction.PASSWORD_CHANGED in audit())
    }

    @Test
    fun changingTheOwnPinRequiresThePassword() = runBlocking {
        fixture.setUpOwner()
        auth.changeOwnPin(OWNER_PASSWORD, "555555")
        auth.signOut()
        val owner = requireNotNull(fixture.database.userDao().getOwner())
        assertTrue(auth.signInWithPin(owner.id, "555555") is SignInResult.Success)
    }

    @Test
    fun onlyTheOwnerCanRegenerateTheRecoveryKey() = runBlocking<Unit> {
        val oldKey = fixture.setUpOwner()
        val adminId = fixture.createUser("joao", SystemRole.ADMIN)
        fixture.signInAs(adminId)

        assertThrows(PermissionDeniedException::class.java) {
            runBlocking { auth.regenerateRecoveryKey("senha-joao") }
        }

        fixture.signInAsOwner()
        val newKey = auth.regenerateRecoveryKey(OWNER_PASSWORD)
        auth.signOut()
        assertEquals(RecoveryResult.InvalidKey, auth.recoverOwnerAccess(oldKey, "nova-senha-1", "333333"))
        assertTrue(auth.recoverOwnerAccess(newKey, "nova-senha-1", "333333") is RecoveryResult.Success)
    }

    @Test
    fun settingsNeedTheSettingsPermission() = runBlocking<Unit> {
        fixture.setUpOwner()
        val managerId = fixture.createUser("gerente", SystemRole.MANAGER)
        fixture.signInAs(managerId)
        assertThrows(PermissionDeniedException::class.java) {
            runBlocking { auth.updateSettings("Novo nome", 10) }
        }

        fixture.signInAsOwner()
        auth.updateSettings("Novo nome", 10)
        val settings = auth.observeSettings().first()!!
        assertEquals("Novo nome", settings.establishmentName)
        assertEquals(10, settings.lockTimeoutMinutes)
    }

    private fun assertNotNull(value: Any?) = assertTrue(value != null)
}
