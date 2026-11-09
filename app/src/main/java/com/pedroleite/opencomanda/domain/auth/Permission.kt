package com.pedroleite.opencomanda.domain.auth

/**
 * One thing a user may be allowed to do. [key] is the stable identifier stored in the database
 * (`role_permissions.permission`) — never rename a key once shipped; add a new one instead.
 */
enum class Permission(val key: String) {
    SALES_CREATE("sales.create"),
    COMANDA_MANAGE("comanda.manage"),
    SALES_CANCEL("sales.cancel"),
    STOCK_VIEW("stock.view"),
    STOCK_ADJUST("stock.adjust"),
    PRODUCTS_MANAGE("products.manage"),
    FIADO_VIEW("fiado.view"),
    FIADO_CHARGE("fiado.charge"),
    FIADO_RECEIVE("fiado.receive"),
    CASH_OPEN("cash.open"),
    CASH_CLOSE("cash.close"),
    REPORTS_VIEW("reports.view"),
    USERS_MANAGE("users.manage"),
    SETTINGS_MANAGE("settings.manage"),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        /** The permission stored under [key], or null for a key this version doesn't know. */
        fun fromKey(key: String): Permission? = byKey[key]
    }
}

/** The three built-in profiles. They start with [PermissionPolicy.defaultPermissions]; Gerente and Vendedor can then be edited. */
enum class SystemRole { ADMIN, MANAGER, SELLER }

/**
 * OpenComanda's authorization rules, in one place so the UI, the repositories and the tests all
 * agree:
 * - the built-in profiles' starting permissions ([defaultPermissions]) — the initial matrix. The
 *   Gerente and Vendedor profiles can then be edited by an Administrator; the Administrador profile
 *   always holds everything, so the owner can never be locked out;
 * - which permissions a profile may hold at all ([isGrantable]). Managing users, profiles and
 *   settings stays with Administrators, and opening/closing the cash register stays with
 *   Administrators and Managers — no other profile (built-in or custom) can be given these, so no one
 *   can edit their way into more access than the business owner intended.
 */
object PermissionPolicy {

    private val sellerPermissions = setOf(
        Permission.SALES_CREATE,
        Permission.COMANDA_MANAGE,
        Permission.STOCK_VIEW,
    )

    private val managerPermissions = sellerPermissions + setOf(
        Permission.SALES_CANCEL,
        Permission.STOCK_ADJUST,
        Permission.PRODUCTS_MANAGE,
        Permission.FIADO_VIEW,
        Permission.FIADO_CHARGE,
        Permission.FIADO_RECEIVE,
        Permission.CASH_OPEN,
        Permission.CASH_CLOSE,
        Permission.REPORTS_VIEW,
    )

    private val adminPermissions = Permission.entries.toSet()

    /** Only the Administrador profile may hold these. */
    val adminOnlyPermissions: Set<Permission> = setOf(Permission.USERS_MANAGE, Permission.SETTINGS_MANAGE)

    /** Only the Administrador and Gerente profiles may hold these. */
    val adminAndManagerOnlyPermissions: Set<Permission> = setOf(Permission.CASH_OPEN, Permission.CASH_CLOSE)

    fun defaultPermissions(role: SystemRole): Set<Permission> = when (role) {
        SystemRole.ADMIN -> adminPermissions
        SystemRole.MANAGER -> managerPermissions
        SystemRole.SELLER -> sellerPermissions
    }

    /** Whether an Administrator may edit the permissions of a profile with [role] (null = custom). */
    fun isEditable(role: SystemRole?): Boolean = role != SystemRole.ADMIN

    /** Whether a profile with [role] (null = a custom profile) may hold [permission]. */
    fun isGrantable(role: SystemRole?, permission: Permission): Boolean = when (role) {
        SystemRole.ADMIN -> true
        SystemRole.MANAGER -> permission !in adminOnlyPermissions
        SystemRole.SELLER, null -> permission !in adminOnlyPermissions && permission !in adminAndManagerOnlyPermissions
    }
}
