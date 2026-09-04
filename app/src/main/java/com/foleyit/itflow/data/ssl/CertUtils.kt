package com.foleyit.itflow.data.ssl

import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/** SHA-256 fingerprint of a certificate as XX:XX:XX... hex string. */
fun X509Certificate.sha256Fingerprint(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded)
        .joinToString(":") { "%02X".format(it) }

/**
 * Probes [url] with a trust-all TrustManager to retrieve the server's leaf certificate
 * without establishing a trusted connection. Used to show the cert to the user before
 * they decide to accept it.
 */
fun probeCertificate(url: String): X509Certificate? {
    var leafCert: X509Certificate? = null
    val probeTm = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            leafCert = chain.firstOrNull()
            // Always throw — we only want the cert, not an actual connection
            throw java.security.cert.CertificateException("probe")
        }
    }
    try {
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(null, arrayOf<TrustManager>(probeTm), null)
        val client = okhttp3.OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, probeTm)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        client.newCall(okhttp3.Request.Builder().url(url).build()).execute().close()
    } catch (_: Exception) {}
    return leafCert
}

/**
 * TrustManager that:
 * 1. Accepts certs trusted by the system (CA-signed)
 * 2. If system rejects, accepts a cert whose SHA-256 fingerprint matches [trustedSha]
 */
class FingerprintTrustManager(private val trustedSha: String?) : X509TrustManager {

    private val systemTm: X509TrustManager by lazy {
        javax.net.ssl.TrustManagerFactory
            .getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
            .also { it.init(null as java.security.KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = systemTm.acceptedIssuers

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        systemTm.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            systemTm.checkServerTrusted(chain, authType)
            return
        } catch (_: java.security.cert.CertificateException) {}

        val leaf = chain.firstOrNull()
            ?: throw java.security.cert.CertificateException("Empty certificate chain")

        if (trustedSha != null && leaf.sha256Fingerprint() == trustedSha) {
            leaf.checkValidity() // throws if cert is expired, even if fingerprint matches
            return
        }

        throw java.security.cert.CertificateException(
            "Certificate not trusted by system and no matching fingerprint stored"
        )
    }
}
