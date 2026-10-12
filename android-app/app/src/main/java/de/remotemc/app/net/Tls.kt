package de.remotemc.app.net

import android.annotation.SuppressLint
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** The server presented a different certificate than the one stored when pairing. */
class CertificateChangedException(val actualFingerprint: String) :
    CertificateException("Server certificate changed")

object Tls {

    fun fingerprint(cert: X509Certificate): String =
        Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(cert.encoded), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    /** Short form users compare with the server console, e.g. "AB12-CD34". */
    fun shortFingerprint(fingerprint: String): String {
        val bytes = Base64.decode(fingerprint, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val hex = bytes.take(4).joinToString("") { "%02X".format(it) }
        return hex.substring(0, 4) + "-" + hex.substring(4)
    }

    val systemTrustManager: X509TrustManager by lazy {
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as KeyStore?)
        tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
     * Trusts exactly the pinned certificate (the plugin's self-signed one), or uses normal CA
     * validation when no fingerprint is stored (e.g. behind a reverse proxy with a real certificate).
     */
    @SuppressLint("CustomX509TrustManager") // deliberate: certificate pinning, falls back to the system trust manager
    class PinningTrustManager(private val pinned: String?) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            if (pinned == null) {
                systemTrustManager.checkServerTrusted(chain, authType)
                return
            }
            val actual = fingerprint(chain.first())
            if (!MessageDigest.isEqual(actual.toByteArray(), pinned.toByteArray())) {
                throw CertificateChangedException(actual)
            }
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
            throw CertificateException("Client certificates are not used")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /** Accepts any certificate but remembers it; only used to show its fingerprint before pairing. */
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager") // only for reading the fingerprint; no secrets are sent
    class CapturingTrustManager : X509TrustManager {
        @Volatile
        var captured: X509Certificate? = null

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            captured = chain.first()
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
            throw CertificateException("Client certificates are not used")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    fun sslContext(trustManager: X509TrustManager): SSLContext =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }

    /**
     * With a pinned certificate the host name is irrelevant (self-signed certificates do not
     * contain the server's IP or domain); otherwise use standard verification.
     */
    fun hostnameVerifier(pinned: Boolean): HostnameVerifier =
        if (pinned) HostnameVerifier { _, _ -> true } else HttpsURLConnection.getDefaultHostnameVerifier()
}
