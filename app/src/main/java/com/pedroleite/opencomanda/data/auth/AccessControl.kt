package com.pedroleite.opencomanda.data.auth

import com.pedroleite.opencomanda.data.local.dao.AuditLogDao
import com.pedroleite.opencomanda.data.local.entity.AuditLogEntity
import com.pedroleite.opencomanda.domain.auth.AuditAction
import com.pedroleite.opencomanda.domain.auth.Permission

/** Thrown when the signed-in user (or nobody) attempts something [permission] is needed for. */
class PermissionDeniedException(val permission: Permission) :
    SecurityException("Permission ${permission.key} is required")

/**
 * The repositories' view of who is acting: every write that needs a permission calls [require]
 * before touching the database, so a hidden button is never the only protection — and sensitive
 * actions are recorded with [audit].
 */
interface AccessControl {
    /** @throws PermissionDeniedException when the current session lacks [permission]. */
    suspend fun require(permission: Permission)

    /** Records a sensitive action, attributed to the signed-in user. */
    suspend fun audit(action: AuditAction, details: String? = null)

    companion object {
        /**
         * Allows everything and records nothing. Repository constructors default to it so unit and
         * Room tests can build a repository directly; the app itself always wires
         * [SessionAccessControl] (see [com.pedroleite.opencomanda.data.AppContainer]).
         */
        val Unrestricted: AccessControl = object : AccessControl {
            override suspend fun require(permission: Permission) = Unit
            override suspend fun audit(action: AuditAction, details: String?) = Unit
        }
    }
}

/** [AccessControl] backed by the live [SessionManager]: only an active (not locked) session counts. */
class SessionAccessControl(
    private val session: SessionManager,
    private val auditLogDao: AuditLogDao,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : AccessControl {

    override suspend fun require(permission: Permission) {
        val user = session.activeUser()
        if (user == null || permission !in user.permissions) {
            audit(AuditAction.PERMISSION_DENIED, permission.key)
            throw PermissionDeniedException(permission)
        }
    }

    override suspend fun audit(action: AuditAction, details: String?) {
        val user = session.activeUser()
        auditLogDao.insert(
            AuditLogEntity(
                userId = user?.id,
                userName = user?.displayName,
                action = action,
                details = details,
                createdAt = nowMillis(),
            ),
        )
    }
}
