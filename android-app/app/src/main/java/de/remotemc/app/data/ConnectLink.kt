package de.remotemc.app.data

import java.net.URI
import java.net.URLDecoder

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
            // java.net.URI instead of android.net.Uri, so the desktop app can use this too.
            val uri = runCatching { URI(text.trim()) }.getOrNull() ?: return null
            if (uri.scheme?.lowercase() !in SCHEMES) return null
            val host = uri.host?.removePrefix("[")?.removeSuffix("]")?.takeIf { it.isNotBlank() } ?: return null
            val query = (uri.rawQuery ?: "").split('&').mapNotNull {
                val kv = it.split('=', limit = 2)
                if (kv[0].isEmpty()) null else kv[0] to URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
            }.toMap()
            val code = query["code"]?.filter(Char::isDigit)?.takeIf { it.length == 6 }
            val token = query["token"]?.takeIf { it.isNotBlank() }
            if (code == null && token == null) return null
            val fingerprint = query["fp"]?.takeIf { it.isNotBlank() }
            val tls = when {
                query["tls"] == "0" -> false
                fingerprint != null -> true
                else -> query["https"] == "1"
            }
            return ConnectLink(host, if (uri.port in 1..65535) uri.port else 8765, code, token, fingerprint, tls, text.trim())
        }
    }
}
