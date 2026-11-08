package com.pedroleite.opencomanda.domain.auth

import java.security.SecureRandom

/** Input rules for usernames, passwords and PINs, shared by the forms and the repository. */
object CredentialRules {
    const val PIN_LENGTH = 6
    const val MIN_PASSWORD_LENGTH = 8
    const val MIN_USERNAME_LENGTH = 3
    const val MAX_USERNAME_LENGTH = 32

    private val usernamePattern = Regex("^[a-z0-9._-]+$")

    /** Usernames are compared lower-cased and without surrounding spaces. */
    fun normalizeUsername(raw: String): String = raw.trim().lowercase()

    fun isValidUsername(raw: String): Boolean {
        val username = normalizeUsername(raw)
        return username.length in MIN_USERNAME_LENGTH..MAX_USERNAME_LENGTH && usernamePattern.matches(username)
    }

    fun isValidPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH && password.isNotBlank()

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }
}

/**
 * The owner's one-time recovery key: 20 characters from an alphabet without look-alikes (no 0/O,
 * 1/I/L), shown once in groups of five. Only its hash is stored; using it consumes it and a new one
 * is issued.
 */
object RecoveryKeys {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val LENGTH = 20
    private const val GROUP = 5
    private val random = SecureRandom()

    fun generate(): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }
            .chunked(GROUP)
            .joinToString("-") { it.joinToString("") }

    /** Accepts what the owner types back: any case, with or without dashes and spaces. */
    fun normalize(raw: String): String =
        raw.uppercase().filter { it.isLetterOrDigit() }.chunked(GROUP).joinToString("-")
}

/**
 * Lockout after repeated failed sign-ins: the first [FREE_ATTEMPTS] mistakes cost nothing, then
 * each further failure locks the account for twice as long as the previous lock — 30 s, 1 min,
 * 2 min… capped at 15 min. A successful sign-in resets the counter.
 */
object LockoutPolicy {
    const val FREE_ATTEMPTS = 5
    private const val BASE_LOCK_MILLIS = 30_000L
    private const val MAX_LOCK_MILLIS = 15 * 60_000L

    /** How long to lock after the [failedAttempts]-th consecutive failure; 0 means no lock. */
    fun lockDurationMillis(failedAttempts: Int): Long {
        if (failedAttempts < FREE_ATTEMPTS) return 0L
        val doublings = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(10)
        return (BASE_LOCK_MILLIS shl doublings).coerceAtMost(MAX_LOCK_MILLIS)
    }
}
