package com.foleyit.itflow.ui.util

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt

/** Pure mapping from AndroidX biometric result codes to user-facing text, so it can be unit tested. */
object BiometricGate {
    /** Null when strong biometrics can be used; otherwise a sentence explaining what is missing. */
    fun unavailableMessage(canAuthenticateResult: Int): String? = when (canAuthenticateResult) {
        BiometricManager.BIOMETRIC_SUCCESS -> null
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            "No fingerprint or face unlock is set up on this device. Set one up in system settings to view credentials."
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            "This device has no strong biometric sensor, so credentials can't be shown here."
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            "The biometric sensor is temporarily unavailable. Try again in a moment."
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            "A security update is required before biometric verification can be used."
        else -> "Biometric verification isn't available on this device."
    }

    fun canEnroll(canAuthenticateResult: Int): Boolean =
        canAuthenticateResult == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

    /** Errors the user caused on purpose (closing the prompt) should not show an error message. */
    fun isUserDismissal(errorCode: Int): Boolean = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_CANCELED
}
