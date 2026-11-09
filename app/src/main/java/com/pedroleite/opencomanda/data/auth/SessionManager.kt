package com.pedroleite.opencomanda.data.auth

import com.pedroleite.opencomanda.domain.auth.Permission
import com.pedroleite.opencomanda.domain.auth.SystemRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The signed-in person, with their profile's permissions resolved once at sign-in. */
data class AuthenticatedUser(
    val id: Long,
    val displayName: String,
    val username: String,
    val roleId: Long,
    val roleName: String,
    val systemRole: SystemRole?,
    val isOwner: Boolean,
    val permissions: Set<Permission>,
) {
    fun can(permission: Permission): Boolean = permission in permissions
}

/** Where the app is, as far as access goes. Drives which top-level screen shows. */
sealed interface SessionState {
    /** Still checking the database at launch. */
    data object Loading : SessionState

    /** No user exists yet: the owner must create the first Administrator. */
    data object NeedsSetup : SessionState

    /** Users exist, nobody is signed in. */
    data object SignedOut : SessionState

    /** [user] was signed in but the session locked after inactivity; their PIN or password resumes it. */
    data class Locked(val user: AuthenticatedUser) : SessionState

    data class Active(val user: AuthenticatedUser) : SessionState
}

/**
 * Holds the current session in memory — it is never persisted, so restarting the app always asks
 * for sign-in again. Inactivity is tracked through [touch]; whoever observes the clock calls
 * [lockIfIdle].
 */
class SessionManager(private val nowMillis: () -> Long = System::currentTimeMillis) {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile
    private var lastActivityMillis: Long = nowMillis()

    /** The user of an active session; null while signed out or locked. */
    fun activeUser(): AuthenticatedUser? = (_state.value as? SessionState.Active)?.user

    fun setNeedsSetup() {
        _state.value = SessionState.NeedsSetup
    }

    fun setSignedOut() {
        _state.value = SessionState.SignedOut
    }

    fun start(user: AuthenticatedUser) {
        lastActivityMillis = nowMillis()
        _state.value = SessionState.Active(user)
    }

    fun lock() {
        val user = activeUser() ?: return
        _state.value = SessionState.Locked(user)
    }

    /** Records user interaction, postponing the inactivity lock. */
    fun touch() {
        lastActivityMillis = nowMillis()
    }

    /** Locks an active session that has been idle for at least [timeoutMillis]. Returns true if it locked. */
    fun lockIfIdle(timeoutMillis: Long): Boolean {
        if (activeUser() == null || nowMillis() - lastActivityMillis < timeoutMillis) return false
        lock()
        return true
    }
}
