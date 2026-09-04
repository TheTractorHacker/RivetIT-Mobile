package com.foleyit.itflow.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun `generated password has requested length`() {
        for (length in listOf(8, 16, 32)) {
            assertEquals(length, generatePassword(length).length)
        }
    }

    @Test
    fun `default charset excludes ambiguous look-alike characters`() {
        // il1Lo0O and similar are excluded from the charset by design (readability on-screen).
        val ambiguous = setOf('l', 'I', 'O', '0', '1')
        val password = generatePassword(length = 500)
        assertTrue(
            "password should not contain ambiguous characters, was: $password",
            password.none { it in ambiguous }
        )
    }

    @Test
    fun `disabling upper, digits, symbols yields only lowercase letters`() {
        val password = generatePassword(length = 200, upper = false, digits = false, symbols = false)
        assertTrue(password.all { it.isLowerCase() && it.isLetter() })
    }

    @Test
    fun `passwords are not deterministic across calls`() {
        val a = generatePassword(32)
        val b = generatePassword(32)
        assertTrue("two 32-char passwords should not be identical", a != b)
    }
}
