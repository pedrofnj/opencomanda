package com.pedroleite.opencomanda.data.auth

import com.pedroleite.opencomanda.domain.auth.PermissionPolicy
import com.pedroleite.opencomanda.domain.auth.SystemRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {

    private var now = 1_000_000L
    private val session = SessionManager(nowMillis = { now })
    private val seller = AuthenticatedUser(
        id = 7, displayName = "Ana", username = "ana", roleId = 3, roleName = "Vendedor",
        systemRole = SystemRole.SELLER, isOwner = false, permissions = PermissionPolicy.defaultPermissions(SystemRole.SELLER),
    )

    @Test
    fun anActiveSessionLocksOnlyAfterTheTimeout() {
        session.start(seller)
        now += 4 * 60_000L
        assertFalse(session.lockIfIdle(5 * 60_000L))
        now += 60_000L
        assertTrue(session.lockIfIdle(5 * 60_000L))
        assertEquals(SessionState.Locked(seller), session.state.value)
    }

    @Test
    fun interactionPostponesTheLock() {
        session.start(seller)
        now += 4 * 60_000L
        session.touch()
        now += 4 * 60_000L
        assertFalse(session.lockIfIdle(5 * 60_000L))
    }

    @Test
    fun aLockedSessionHasNoActiveUser() {
        session.start(seller)
        session.lock()
        assertNull(session.activeUser())
    }

    @Test
    fun signingOutClearsTheUser() {
        session.start(seller)
        session.setSignedOut()
        assertNull(session.activeUser())
        assertFalse(session.lockIfIdle(0))
    }
}
