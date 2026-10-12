package de.remotemc.app.data

import androidx.core.net.toUri

/**
 * Contents of a pairing QR code / link from `/remotemc pair`:
 * `remotemc://host:port?code=123456&fp=<sha256>` (or `tls=0` without encryption).
 * Older links may carry a `token` instead of a code.
 */
data class ConnectLink(
    val host: String,
    val port: Int,
    val code: String?,
    val token: String?,
    val fingerprint: String?,
    val tls: Boolean,
    /** The original text, e.g. to pass it between screens. */
    val raw: String,
) {
    companion object {
        private val SCHEMES = setOf("remotemc", "mcremote")

        fun parse(text: String): ConnectLink? {
            val uri = runCatching { text.trim().toUri() }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in SCHEMES) return null
            val host = uri.host?.removePrefix("[")?.removeSuffix("]")?.takeIf { it.isNotBlank() } ?: return null
            val code = uri.getQueryParameter("code")?.filter(Char::isDigit)?.takeIf { it.length == 6 }
            val token = uri.getQueryParameter("token")?.takeIf { it.isNotBlank() }
            if (code == null && token == null) return null
            val fingerprint = uri.getQueryParameter("fp")?.takeIf { it.isNotBlank() }
            val tls = when {
                uri.getQueryParameter("tls") == "0" -> false
                fingerprint != null -> true
                else -> uri.getQueryParameter("https") == "1"
            }
            return ConnectLink(host, if (uri.port in 1..65535) uri.port else 8765, code, token, fingerprint, tls, text.trim())
        }
    }
}
