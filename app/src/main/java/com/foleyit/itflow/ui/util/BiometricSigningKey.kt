package com.foleyit.itflow.ui.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.Base64

/**
 * A Keystore-backed EC P-256 signing key that requires a fresh biometric
 * unlock to use (setUserAuthenticationRequired). Signing a server-issued,
 * single-use challenge with this key is the server-verifiable proof that a
 * real biometric event occurred for this specific request - replacing a
 * previous design where the app just sent a hardcoded "X-Biometric: 1"
 * header the server had no way to verify.
 *
 * setInvalidatedByBiometricEnrollment(true) means the key is silently
 * invalidated if the user enrolls a new fingerprint/face - getOrCreatePublicKey()
 * then transparently generates a fresh key, which the caller must re-register
 * with the server (PUT /api/v1/me) before it can be used to sign anything.
 */
object BiometricSigningKey {
    private const val KEY_ALIAS = "itflow_credential_biometric_signing_key"

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun existingPublicKey(): PublicKey? = keyStore().getCertificate(KEY_ALIAS)?.publicKey

    private fun generateKey(): PublicKey {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        kpg.initialize(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return kpg.generateKeyPair().public
    }

    /**
     * Returns this device's current public key, generating a new Keystore
     * keypair on first use (or after invalidation). The caller is
     * responsible for registering the returned key with the server whenever
     * it might be new - registration is idempotent (PUT overwrites), so
     * callers can simply always register-then-sign rather than tracking
     * "is this key already registered" state themselves.
     */
    fun getOrCreatePublicKey(): PublicKey? = try {
        existingPublicKey() ?: generateKey()
    } catch (_: Exception) {
        null
    }

    fun publicKeyPem(publicKey: PublicKey): String {
        val b64 = Base64.getEncoder().encodeToString(publicKey.encoded)
        val wrapped = b64.chunked(64).joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$wrapped\n-----END PUBLIC KEY-----\n"
    }

    /**
     * A CryptoObject wrapping a Signature initialized with this device's
     * Keystore private key. Pass to BiometricPrompt.authenticate() - the
     * wrapped Signature only becomes usable inside onAuthenticationSucceeded,
     * which is what makes the resulting signature real proof of a fresh
     * biometric unlock rather than just a claim the app makes.
     */
    fun cryptoObject(): BiometricPrompt.CryptoObject? = try {
        getOrCreatePublicKey()
        val privateKey = keyStore().getKey(KEY_ALIAS, null) as PrivateKey
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        BiometricPrompt.CryptoObject(signature)
    } catch (_: Exception) {
        null
    }

    /**
     * Call inside onAuthenticationSucceeded with the raw challenge bytes
     * (base64url-decoded server challenge). Returns the base64-encoded DER
     * ECDSA signature to send back as the X-Biometric-Signature header, or
     * null if signing failed.
     */
    fun sign(result: BiometricPrompt.AuthenticationResult, challenge: ByteArray): String? =
        try {
            val signature = result.cryptoObject?.signature ?: return null
            signature.update(challenge)
            Base64.getEncoder().encodeToString(signature.sign())
        } catch (_: Exception) {
            null
        }

    /** Decodes a base64url string (no padding required) to raw bytes. */
    fun base64UrlDecode(s: String): ByteArray {
        val padded = s + "=".repeat((4 - s.length % 4) % 4)
        return Base64.getUrlDecoder().decode(padded)
    }
}
