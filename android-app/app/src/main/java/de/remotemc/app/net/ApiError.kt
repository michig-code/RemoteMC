package de.remotemc.app.net

import java.io.IOException

/** Everything that can go wrong talking to a server, in a form the UI can explain. */
sealed class ApiError(message: String) : IOException(message) {
    /** Host not reachable: wrong address, port closed, firewall, server offline. */
    class Unreachable(detail: String) : ApiError(detail)
    class Timeout : ApiError("timeout")
    /** Certificate differs from the paired one: re-pair or possible attack. */
    class CertificateChanged(val fingerprint: String) : ApiError("certificate changed")
    /** TLS failed, e.g. HTTPS enabled in the app but not on the server (or the other way round). */
    class Tls(detail: String) : ApiError(detail)
    /** HTTP error with the plugin's error code, e.g. invalid_token, forbidden, locked_out. */
    class Http(val status: Int, val code: String, detail: String) : ApiError(detail)
    class InvalidResponse : ApiError("invalid response")
    /** Plugin is too old (no /api/v1) or too new for this app. */
    class Incompatible(val pluginTooOld: Boolean) : ApiError("incompatible")
}
