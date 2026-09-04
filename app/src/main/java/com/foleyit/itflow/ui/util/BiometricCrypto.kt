package com.foleyit.itflow.ui.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

object BiometricCrypto {
    private const val KEY_ALIAS = "itflow_biometric_gate"
    private val TRANSFORMATION =
        "${KeyProperties.KEY_ALGORITHM_AES}/" +
        "${KeyProperties.BLOCK_MODE_CBC}/" +
        KeyProperties.ENCRYPTION_PADDING_PKCS7

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    /**
     * Returns a CryptoObject whose Cipher is initialized with a Keystore key that
     * requires biometric authentication. Pass this to BiometricPrompt.authenticate().
     * Throws if no biometric is enrolled or the key is permanently invalidated.
     */
    fun cryptoObject(): BiometricPrompt.CryptoObject {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return BiometricPrompt.CryptoObject(cipher)
    }

    /**
     * Call inside onAuthenticationSucceeded. Performs a cipher operation using the
     * hardware-unlocked key — this is the cryptographic proof that biometric auth
     * succeeded. Returns true if the operation completes successfully.
     */
    fun confirm(result: BiometricPrompt.AuthenticationResult): Boolean =
        try {
            result.cryptoObject?.cipher?.doFinal("ok".toByteArray()) != null
        } catch (_: Exception) {
            false
        }
}
