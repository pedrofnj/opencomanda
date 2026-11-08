package com.pedroleite.opencomanda.data.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pedroleite.opencomanda.data.repository.AuthError
import com.pedroleite.opencomanda.data.repository.AuthException
import com.pedroleite.opencomanda.data.repository.SignInResult
import com.pedroleite.opencomanda.domain.auth.AuditAction
import com.pedroleite.opencomanda.domain.auth.Permission
import com.pedroleite.opencomanda.domain.auth.SystemRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** User and profile administration, and the rules that prevent privilege escalation. */
@RunWith(AndroidJUnit4::class)
class UserManagementRepositoryTest {

    private val fixture = AuthTestFixture()
    private val users get() = fixture.users

    @Before
    fun setUp() = runBlocking<Unit> { fixture.setUpOwner() }

    @After
    fun tearDown() = fixture.close()

    private fun assertAuthError(expected: AuthError, block: suspend () -> Unit) {
        val error = assertThrows(AuthException::class.java) { runBlocking { block() } }
        assertEquals(expected, error.error)
    }

    private fun assertDenied(block: suspend () -> Unit) {
        assertThrows(PermissionDeniedException::class.java) { runBlocking { block() } }
    }

    @Test
    fun theAdminCreatesAUserWhoCanSignInWithTheirProfile() = runBlocking {
        val id = fixture.createUser("ana", SystemRole.SELLER)

        val created = users.getUser(id)!!
        assertEquals("ana", created.username)
        assertEquals(SystemRole.SELLER, created.systemRole)
        fixture.signInAs(id)
        assertEquals(setOf(Permission.SALES_CREATE, Permission.COMANDA_MANAGE, Permission.STOCK_VIEW), fixture.session.activeUser()!!.permissions)
    }

    @Test
    fun usernamesAreUniqueIgnoringCase() {
        runBlocking { fixture.createUser("ana", SystemRole.SELLER) }
        assertAuthError(AuthError.DUPLICATE_USERNAME) {
            users.createUser("Outra Ana", "ANA", "senha-outra", "333333", fixture.roleId(SystemRole.SELLER))
        }
    }

    @Test
    fun aSellerOrManagerCannotManageUsersOrProfiles() = runBlocking {
        val sellerId = fixture.createUser("ana", SystemRole.SELLER)
        val managerId = fixture.createUser("beto", SystemRole.MANAGER)

        for (actor in listOf(sellerId, managerId)) {
            fixture.signInAs(actor)
            assertDenied { users.createUser("Novo", "novo", "senha-novo-1", "444444", fixture.roleId(SystemRole.ADMIN)) }
            assertDenied { users.updateUser(actor, "Eu mesmo", fixture.roleId(SystemRole.ADMIN)) }
            assertDenied { users.resetPassword(actor, "senha-nova-1") }
            assertDenied { users.createRole("Supervisor", setOf(Permission.SALES_CANCEL)) }
        }
        assertTrue(fixture.database.auditLogDao().getAll().any { it.action == AuditAction.PERMISSION_DENIED && it.details == Permission.USERS_MANAGE.key })
        assertEquals(3, fixture.database.userDao().count())
    }

    @Test
    fun nobodyCanActWithoutASession() {
        runBlocking { fixture.auth.signOut() }
        assertDenied { users.createUser("Novo", "novo", "senha-novo-1", "444444", fixture.roleId(SystemRole.SELLER)) }
    }

    @Test
    fun theOwnerCannotBeDeactivatedOrMovedToAnotherProfile() = runBlocking {
        val owner = fixture.database.userDao().getOwner()!!
        val otherAdmin = fixture.createUser("joao", SystemRole.ADMIN)
        fixture.signInAs(otherAdmin)

        assertAuthError(AuthError.OWNER_PROTECTED) { users.setUserActive(owner.id, false) }
        assertAuthError(AuthError.OWNER_PROTECTED) { users.updateUser(owner.id, owner.displayName, fixture.roleId(SystemRole.SELLER)) }
        assertTrue(fixture.database.userDao().getById(owner.id)!!.active)
    }

    @Test
    fun anotherAdminCannotResetTheOwnersCredentials() = runBlocking {
        val owner = fixture.database.userDao().getOwner()!!
        val otherAdmin = fixture.createUser("joao", SystemRole.ADMIN)
        fixture.signInAs(otherAdmin)

        assertAuthError(AuthError.OWNER_PROTECTED) { users.resetPassword(owner.id, "tomada-123") }
        assertAuthError(AuthError.OWNER_PROTECTED) { users.resetPin(owner.id, "999999") }
        fixture.auth.signOut()
        assertTrue(fixture.auth.signInWithPassword(AuthTestFixture.OWNER_USERNAME, AuthTestFixture.OWNER_PASSWORD) is SignInResult.Success)
    }

    @Test
    fun anAdminCannotDeactivateThemselvesOrChangeTheirOwnProfile() = runBlocking {
        val otherAdmin = fixture.createUser("joao", SystemRole.ADMIN)
        fixture.signInAs(otherAdmin)
        assertAuthError(AuthError.SELF_PROTECTED) { users.setUserActive(otherAdmin, false) }
        assertAuthError(AuthError.SELF_PROTECTED) { users.updateUser(otherAdmin, "João", fixture.roleId(SystemRole.SELLER)) }
    }

    @Test
    fun resettingCredentialsLiftsALockout() = runBlocking {
        val sellerId = fixture.createUser("ana", SystemRole.SELLER)
        repeat(5) { fixture.auth.signInWithPin(sellerId, "000000") }
        fixture.signInAsOwner()
        users.resetPin(sellerId, "777777")

        val user = fixture.database.userDao().getById(sellerId)!!
        assertEquals(0, user.failedAttempts)
        assertEquals(null, user.lockedUntil)
        assertTrue(fixture.auth.signInWithPin(sellerId, "777777") is SignInResult.Success)
    }

    @Test
    fun customProfilesCannotHoldUserSettingsOrCashPermissions() {
        for (reserved in listOf(Permission.USERS_MANAGE, Permission.SETTINGS_MANAGE, Permission.CASH_OPEN, Permission.CASH_CLOSE)) {
            assertAuthError(AuthError.PERMISSION_NOT_GRANTABLE) { users.createRole("Tentativa", setOf(Permission.SALES_CREATE, reserved)) }
        }
    }

    @Test
    fun aCustomProfileGrantsExactlyItsPermissions() = runBlocking {
        val roleId = users.createRole("Caixa do balcão", setOf(Permission.SALES_CREATE, Permission.REPORTS_VIEW))
        val id = users.createUser("Rita", "rita", "senha-rita-1", "888888", roleId)
        fixture.signInAs(id, "888888")
        assertEquals(setOf(Permission.SALES_CREATE, Permission.REPORTS_VIEW), fixture.session.activeUser()!!.permissions)
    }

    @Test
    fun theSellerProfileCanBeEditedAndTheChangeAppliesAtTheNextSignIn() = runBlocking {
        val sellerRole = fixture.roleId(SystemRole.SELLER)
        val sellerId = fixture.createUser("ana", SystemRole.SELLER)

        users.updateRole(sellerRole, "ignored for built-in profiles", setOf(Permission.SALES_CREATE, Permission.SALES_CANCEL, Permission.FIADO_VIEW))

        val role = users.observeRoles().first().single { it.role.id == sellerRole }
        assertEquals(setOf(Permission.SALES_CREATE, Permission.SALES_CANCEL, Permission.FIADO_VIEW), role.permissions)
        assertEquals("Vendedor", role.role.name)
        fixture.signInAs(sellerId)
        assertEquals(role.permissions, fixture.session.activeUser()!!.permissions)
        assertTrue(fixture.database.auditLogDao().getAll().any { it.action == AuditAction.ROLE_UPDATED })
    }

    @Test
    fun theManagerProfileCanGainOrLoseTheCashRegisterButNeverUsers() = runBlocking {
        val managerRole = fixture.roleId(SystemRole.MANAGER)
        users.updateRole(managerRole, "Gerente", setOf(Permission.SALES_CREATE, Permission.CASH_OPEN))
        assertEquals(setOf(Permission.SALES_CREATE, Permission.CASH_OPEN), users.observeRoles().first().single { it.role.id == managerRole }.permissions)

        assertAuthError(AuthError.PERMISSION_NOT_GRANTABLE) { users.updateRole(managerRole, "Gerente", setOf(Permission.USERS_MANAGE)) }
        assertAuthError(AuthError.PERMISSION_NOT_GRANTABLE) { users.updateRole(managerRole, "Gerente", setOf(Permission.SETTINGS_MANAGE)) }
    }

    @Test
    fun theSellerProfileCanNeverGetTheCashRegisterOrUsers() {
        val sellerRole = runBlocking { fixture.roleId(SystemRole.SELLER) }
        for (forbidden in listOf(Permission.CASH_OPEN, Permission.CASH_CLOSE, Permission.USERS_MANAGE, Permission.SETTINGS_MANAGE)) {
            assertAuthError(AuthError.PERMISSION_NOT_GRANTABLE) { users.updateRole(sellerRole, "Vendedor", setOf(forbidden)) }
        }
    }

    @Test
    fun theAdministratorProfileStaysFixedAndBuiltInProfilesCannotBeDeleted() {
        assertAuthError(AuthError.SYSTEM_ROLE_READ_ONLY) {
            users.updateRole(fixture.roleId(SystemRole.ADMIN), "Administrador", setOf(Permission.SALES_CREATE))
        }
        assertAuthError(AuthError.SYSTEM_ROLE_READ_ONLY) { users.deleteRole(fixture.roleId(SystemRole.MANAGER)) }
        assertAuthError(AuthError.SYSTEM_ROLE_READ_ONLY) { users.deleteRole(fixture.roleId(SystemRole.SELLER)) }
    }

    @Test
    fun aManagerCannotEditProfiles() = runBlocking {
        val managerId = fixture.createUser("beto", SystemRole.MANAGER)
        fixture.signInAs(managerId)
        assertDenied { users.updateRole(fixture.roleId(SystemRole.SELLER), "Vendedor", setOf(Permission.SALES_CANCEL)) }
    }

    @Test
    fun aProfileInUseCannotBeDeleted() = runBlocking {
        val roleId = users.createRole("Temporário", setOf(Permission.SALES_CREATE))
        users.createUser("Temp", "temp", "senha-temp-1", "121212", roleId)
        assertAuthError(AuthError.ROLE_IN_USE) { users.deleteRole(roleId) }
    }

    @Test
    fun profileNamesAreUnique() {
        runBlocking { users.createRole("Supervisor", setOf(Permission.SALES_CANCEL)) }
        assertAuthError(AuthError.DUPLICATE_ROLE_NAME) { users.createRole(" supervisor ", emptySet()) }
    }

    @Test
    fun anUnusedCustomProfileCanBeEditedAndDeleted() = runBlocking {
        val roleId = users.createRole("Supervisor", setOf(Permission.SALES_CANCEL))
        users.updateRole(roleId, "Supervisor de turno", setOf(Permission.SALES_CANCEL, Permission.STOCK_ADJUST))
        val role = users.observeRoles().first().single { it.role.id == roleId }
        assertEquals("Supervisor de turno", role.role.name)
        assertEquals(setOf(Permission.SALES_CANCEL, Permission.STOCK_ADJUST), role.permissions)

        users.deleteRole(roleId)
        assertFalse(users.observeRoles().first().any { it.role.id == roleId })
    }

    @Test
    fun sensitiveAdministrationIsAudited() = runBlocking {
        val id = fixture.createUser("ana", SystemRole.SELLER)
        users.resetPassword(id, "senha-nova-1")
        users.setUserActive(id, false)
        users.createRole("Supervisor", setOf(Permission.SALES_CANCEL))

        val actions = fixture.database.auditLogDao().getAll().map { it.action }
        listOf(AuditAction.USER_CREATED, AuditAction.PASSWORD_RESET, AuditAction.USER_DEACTIVATED, AuditAction.ROLE_CREATED).forEach {
            assertTrue(it.name, it in actions)
        }
        val owner = fixture.database.userDao().getOwner()!!
        assertTrue(fixture.database.auditLogDao().getAll().filter { it.action == AuditAction.USER_CREATED }.all { it.userId == owner.id })
    }
}
