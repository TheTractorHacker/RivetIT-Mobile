package com.foleyit.itflow.ui.util

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricGateTest {

    @Test
    fun `available biometrics produce no message`() {
        assertNull(BiometricGate.unavailableMessage(BiometricManager.BIOMETRIC_SUCCESS))
    }

    @Test
    fun `every failure code explains itself`() {
        listOf(
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
        ).forEach { assertNotNull("code $it", BiometricGate.unavailableMessage(it)) }
    }

    @Test
    fun `only the not-enrolled case offers enrollment`() {
        assertTrue(BiometricGate.canEnroll(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
        assertFalse(BiometricGate.canEnroll(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE))
        assertFalse(BiometricGate.canEnroll(BiometricManager.BIOMETRIC_SUCCESS))
    }

    @Test
    fun `closing the prompt is not an error`() {
        assertTrue(BiometricGate.isUserDismissal(BiometricPrompt.ERROR_USER_CANCELED))
        assertTrue(BiometricGate.isUserDismissal(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
        assertFalse(BiometricGate.isUserDismissal(BiometricPrompt.ERROR_LOCKOUT))
    }
}
