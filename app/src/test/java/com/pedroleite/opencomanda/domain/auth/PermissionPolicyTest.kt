package com.pedroleite.opencomanda.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The initial permission matrix, row by row, plus the rules that stop custom profiles from escalating. */
class PermissionPolicyTest {

    private val admin = PermissionPolicy.defaultPermissions(SystemRole.ADMIN)
    private val manager = PermissionPolicy.defaultPermissions(SystemRole.MANAGER)
    private val seller = PermissionPolicy.defaultPermissions(SystemRole.SELLER)

    private fun assertRow(permission: Permission, admin: Boolean, manager: Boolean, seller: Boolean) {
        assertEquals("Admin · ${permission.key}", admin, permission in this.admin)
        assertEquals("Gerente · ${permission.key}", manager, permission in this.manager)
        assertEquals("Vendedor · ${permission.key}", seller, permission in this.seller)
    }

    @Test
    fun matrixMatchesTheSpecification() {
        assertRow(Permission.SALES_CREATE, admin = true, manager = true, seller = true)
        assertRow(Permission.COMANDA_MANAGE, admin = true, manager = true, seller = true)
        assertRow(Permission.SALES_CANCEL, admin = true, manager = true, seller = false)
        assertRow(Permission.STOCK_VIEW, admin = true, manager = true, seller = true)
        assertRow(Permission.STOCK_ADJUST, admin = true, manager = true, seller = false)
        assertRow(Permission.PRODUCTS_MANAGE, admin = true, manager = true, seller = false)
        assertRow(Permission.FIADO_VIEW, admin = true, manager = true, seller = false)
        assertRow(Permission.FIADO_CHARGE, admin = true, manager = true, seller = false)
        assertRow(Permission.FIADO_RECEIVE, admin = true, manager = true, seller = false)
        assertRow(Permission.CASH_OPEN, admin = true, manager = true, seller = false)
        assertRow(Permission.CASH_CLOSE, admin = true, manager = true, seller = false)
        assertRow(Permission.REPORTS_VIEW, admin = true, manager = true, seller = false)
        assertRow(Permission.USERS_MANAGE, admin = true, manager = false, seller = false)
        assertRow(Permission.SETTINGS_MANAGE, admin = true, manager = false, seller = false)
    }

    @Test
    fun adminHoldsEveryPermission() {
        assertEquals(Permission.entries.toSet(), admin)
    }

    @Test
    fun onlyTheAdministratorProfileIsFixed() {
        assertFalse(PermissionPolicy.isEditable(SystemRole.ADMIN))
        assertTrue(PermissionPolicy.isEditable(SystemRole.MANAGER))
        assertTrue(PermissionPolicy.isEditable(SystemRole.SELLER))
        assertTrue(PermissionPolicy.isEditable(null))
    }

    @Test
    fun usersAndSettingsStayWithTheAdministrator() {
        listOf(Permission.USERS_MANAGE, Permission.SETTINGS_MANAGE).forEach {
            assertTrue(it.key, PermissionPolicy.isGrantable(SystemRole.ADMIN, it))
            assertFalse(it.key, PermissionPolicy.isGrantable(SystemRole.MANAGER, it))
            assertFalse(it.key, PermissionPolicy.isGrantable(SystemRole.SELLER, it))
            assertFalse(it.key, PermissionPolicy.isGrantable(null, it))
        }
    }

    @Test
    fun theCashRegisterStaysWithAdministratorAndManager() {
        listOf(Permission.CASH_OPEN, Permission.CASH_CLOSE).forEach {
            assertTrue(it.key, PermissionPolicy.isGrantable(SystemRole.ADMIN, it))
            assertTrue(it.key, PermissionPolicy.isGrantable(SystemRole.MANAGER, it))
            assertFalse(it.key, PermissionPolicy.isGrantable(SystemRole.SELLER, it))
            assertFalse(it.key, PermissionPolicy.isGrantable(null, it))
        }
    }

    @Test
    fun everyOtherPermissionCanBeGivenToAnyEditableProfile() {
        val restricted = PermissionPolicy.adminOnlyPermissions + PermissionPolicy.adminAndManagerOnlyPermissions
        (Permission.entries - restricted).forEach { permission ->
            listOf(SystemRole.MANAGER, SystemRole.SELLER, null).forEach { role ->
                assertTrue("$role · ${permission.key}", PermissionPolicy.isGrantable(role, permission))
            }
        }
    }

    @Test
    fun theDefaultMatrixRespectsTheRules() {
        SystemRole.entries.forEach { role ->
            PermissionPolicy.defaultPermissions(role).forEach { assertTrue("$role · ${it.key}", PermissionPolicy.isGrantable(role, it)) }
        }
    }

    @Test
    fun keysAreUniqueAndRoundTrip() {
        assertEquals(Permission.entries.size, Permission.entries.map { it.key }.toSet().size)
        Permission.entries.forEach { assertEquals(it, Permission.fromKey(it.key)) }
        assertNull(Permission.fromKey("unknown.permission"))
    }
}
