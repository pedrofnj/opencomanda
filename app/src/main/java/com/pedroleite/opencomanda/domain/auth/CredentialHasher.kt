package com.pedroleite.opencomanda.domain.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hashes passwords, PINs and recovery keys with PBKDF2-HMAC-SHA256 and a random 16-byte salt per
 * credential. PBKDF2 ships with every Android version the app supports, so no extra dependency is
 * needed.
 *
 * The stored form is self-describing — `pbkdf2-sha256$<iterations>$<salt>$<hash>` (Base64) — so the
 * iteration count can be raised later without invalidating existing credentials: [verify] always
 * uses the count recorded in the hash. Nothing reversible is ever stored.
 */
class CredentialHasher(private val iterations: Int = DEFAULT_ITERATIONS) {

    init {
        require(iterations > 0) { "Iterations must be positive" }
    }

    fun hash(secret: String): String {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val derived = derive(secret, salt, iterations)
        return listOf(ALGORITHM, iterations.toString(), encoder.encodeToString(salt), encoder.encodeToString(derived))
            .joinToString(SEPARATOR)
    }

    /** True when [secret] matches [stored]. A malformed [stored] value never matches. */
    fun verify(secret: String, stored: String): Boolean {
        val parts = stored.split(SEPARATOR)
        if (parts.size != 4 || parts[0] != ALGORITHM) return false
        val rounds = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        val actual = derive(secret, salt, rounds)
        // Constant-time comparison: how long a wrong guess takes says nothing about how close it was.
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(secret: String, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, rounds, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        /** Tuned for a few hundred milliseconds on an entry-level phone. */
        const val DEFAULT_ITERATIONS = 120_000
        private const val ALGORITHM = "pbkdf2-sha256"
        private const val SEPARATOR = "$"
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
        private val random = SecureRandom()
        private val encoder = Base64.getEncoder()
        private val decoder = Base64.getDecoder()
    }
}
