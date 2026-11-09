package com.pedroleite.opencomanda.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialHasherTest {

    private val hasher = CredentialHasher(iterations = 1_000)

    @Test
    fun theRightSecretVerifies() {
        val stored = hasher.hash("senha-forte-123")
        assertTrue(hasher.verify("senha-forte-123", stored))
    }

    @Test
    fun aWrongSecretDoesNotVerify() {
        val stored = hasher.hash("senha-forte-123")
        assertFalse(hasher.verify("senha-forte-124", stored))
        assertFalse(hasher.verify("", stored))
    }

    @Test
    fun theSecretIsNeverStoredInPlainText() {
        val stored = hasher.hash("123456")
        assertFalse(stored.contains("123456"))
    }

    @Test
    fun theSameSecretHashesDifferentlyEachTimeBecauseOfTheSalt() {
        assertNotEquals(hasher.hash("123456"), hasher.hash("123456"))
    }

    @Test
    fun theIterationCountIsRecordedSoItCanChangeLater() {
        val stored = hasher.hash("123456")
        assertEquals("1000", stored.split("$")[1])
        // A hasher configured differently still verifies, using the stored count.
        assertTrue(CredentialHasher(iterations = 5).verify("123456", stored))
    }

    @Test
    fun malformedStoredValuesNeverMatch() {
        listOf("", "plain", "md5$1\$a\$b", "pbkdf2-sha256\$x\$AAAA\$AAAA", "pbkdf2-sha256$10$!!!$!!!").forEach {
            assertFalse(it, hasher.verify("anything", it))
        }
    }
}
