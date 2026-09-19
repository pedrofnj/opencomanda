package com.pedroleite.opencomanda.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneInputTest {

    @Test
    fun stripsPunctuationAndSpacingFromAFormattedNumber() {
        assertEquals("62999991234", normalizePhoneDigits("(62) 99999-1234"))
    }

    @Test
    fun leavesAnAlreadyDigitOnlyNumberUnchanged() {
        assertEquals("62999991234", normalizePhoneDigits("62999991234"))
    }

    @Test
    fun blankInputProducesEmptyDigits() {
        assertEquals("", normalizePhoneDigits("   "))
    }

    @Test
    fun nonDigitInputProducesEmptyDigits() {
        assertEquals("", normalizePhoneDigits("abc-def"))
    }

    @Test
    fun mixedLettersAndDigitsKeepsOnlyDigits() {
        assertEquals("1234", normalizePhoneDigits("ext.1234"))
    }
}
