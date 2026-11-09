package com.pedroleite.opencomanda.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CredentialRulesTest {

    @Test
    fun usernamesAreNormalizedAndValidated() {
        assertEquals("maria.silva", CredentialRules.normalizeUsername("  Maria.Silva "))
        assertTrue(CredentialRules.isValidUsername("Joao_2"))
        assertFalse(CredentialRules.isValidUsername("jo"))
        assertFalse(CredentialRules.isValidUsername("joão"))
        assertFalse(CredentialRules.isValidUsername("com espaço"))
        assertFalse(CredentialRules.isValidUsername("a".repeat(33)))
    }

    @Test
    fun pinsMustBeExactlySixDigits() {
        assertTrue(CredentialRules.isValidPin("012345"))
        assertFalse(CredentialRules.isValidPin("12345"))
        assertFalse(CredentialRules.isValidPin("1234567"))
        assertFalse(CredentialRules.isValidPin("12a456"))
    }

    @Test
    fun passwordsNeedAtLeastEightCharacters() {
        assertTrue(CredentialRules.isValidPassword("12345678"))
        assertFalse(CredentialRules.isValidPassword("1234567"))
        assertFalse(CredentialRules.isValidPassword("        "))
    }

    @Test
    fun recoveryKeysHaveFourGroupsOfFiveUnambiguousCharacters() {
        repeat(50) {
            val key = RecoveryKeys.generate()
            assertTrue(key, Regex("^[A-HJ-NP-Z2-9]{5}(-[A-HJ-NP-Z2-9]{5}){3}$").matches(key))
            assertFalse(key, key.any { it in "01ILO" })
        }
    }

    @Test
    fun recoveryKeysAreAcceptedInAnyCaseWithOrWithoutDashes() {
        assertEquals("ABCDE-FGHJK-MNPQR-STUVW", RecoveryKeys.normalize("abcde fghjk mnpqrstuvw"))
        assertEquals("ABCDE-FGHJK-MNPQR-STUVW", RecoveryKeys.normalize("ABCDE-FGHJK-MNPQR-STUVW"))
    }

    @Test
    fun lockoutStartsAfterFiveFailuresAndDoublesUpToFifteenMinutes() {
        (0..4).forEach { assertEquals(0L, LockoutPolicy.lockDurationMillis(it)) }
        assertEquals(30_000L, LockoutPolicy.lockDurationMillis(5))
        assertEquals(60_000L, LockoutPolicy.lockDurationMillis(6))
        assertEquals(120_000L, LockoutPolicy.lockDurationMillis(7))
        assertEquals(15 * 60_000L, LockoutPolicy.lockDurationMillis(50))
    }
}
