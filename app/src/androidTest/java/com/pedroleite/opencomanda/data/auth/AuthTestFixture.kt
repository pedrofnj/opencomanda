package com.pedroleite.opencomanda.data.auth

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.repository.AuthRepository
import com.pedroleite.opencomanda.data.repository.SignInResult
import com.pedroleite.opencomanda.data.repository.UserManagementRepository
import com.pedroleite.opencomanda.domain.auth.CredentialHasher
import com.pedroleite.opencomanda.domain.auth.SystemRole

/**
 * An in-memory database with the real sign-in stack around it: a controllable clock, a fast hasher
 * (the production iteration count would only slow the tests down) and the session the repositories
 * consult. Owner credentials are fixed so tests can sign back in as the owner.
 */
class AuthTestFixture {
    var now: Long = 1_700_000_000_000L
    val database: AppDatabase = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext,
        AppDatabase::class.java,
    ).build()
    val session = SessionManager(nowMillis = { now })
    val hasher = CredentialHasher(iterations = 1_000)
    val accessControl = SessionAccessControl(session, database.auditLogDao(), nowMillis = { now })

    val auth = AuthRepository(
        database = database,
        userDao = database.userDao(),
        roleDao = database.roleDao(),
        settingsDao = database.securitySettingsDao(),
        auditLogDao = database.auditLogDao(),
        session = session,
        hasher = hasher,
        nowMillis = { now },
    )

    val users = UserManagementRepository(
        database = database,
        userDao = database.userDao(),
        roleDao = database.roleDao(),
        auditLogDao = database.auditLogDao(),
        session = session,
        access = accessControl,
        hasher = hasher,
        nowMillis = { now },
    )

    /** Completes first-run setup; the owner is left signed in. Returns the recovery key. */
    suspend fun setUpOwner(): String =
        auth.completeSetup("Espetaria Teste", "Dona Maria", OWNER_USERNAME, OWNER_PASSWORD, OWNER_PIN)

    suspend fun roleId(role: SystemRole): Long = requireNotNull(database.roleDao().getBySystemRole(role)).id

    /** As the signed-in admin, creates a user with [role] and returns their id. */
    suspend fun createUser(username: String, role: SystemRole, pin: String = "222222"): Long =
        users.createUser("Pessoa $username", username, "senha-$username", pin, roleId(role))

    /** Signs in as [userId] with [pin], failing the test if that doesn't work. */
    suspend fun signInAs(userId: Long, pin: String = "222222") {
        val result = auth.signInWithPin(userId, pin)
        check(result is SignInResult.Success) { "Sign-in failed: $result" }
    }

    suspend fun signInAsOwner() {
        val owner = requireNotNull(database.userDao().getOwner())
        signInAs(owner.id, OWNER_PIN)
    }

    fun close() = database.close()

    companion object {
        const val OWNER_USERNAME = "maria"
        const val OWNER_PASSWORD = "senha-forte-1"
        const val OWNER_PIN = "111111"
    }
}
