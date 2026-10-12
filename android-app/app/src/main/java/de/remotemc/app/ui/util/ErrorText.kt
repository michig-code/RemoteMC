package de.remotemc.app.ui.util

import android.content.Context
import de.remotemc.app.R
import de.remotemc.app.net.ApiError

/** Human-readable explanation for any error from the network layer. */
fun Context.errorText(e: Throwable): String = when (e) {
    is ApiError.Unreachable -> getString(R.string.error_unreachable)
    is ApiError.Timeout -> getString(R.string.error_timeout)
    is ApiError.CertificateChanged -> getString(R.string.error_certificate_changed)
    is ApiError.Tls -> getString(R.string.error_tls)
    is ApiError.InvalidResponse -> getString(R.string.error_invalid_response)
    is ApiError.Incompatible -> getString(if (e.pluginTooOld) R.string.error_plugin_too_old else R.string.error_app_too_old)
    is ApiError.Http -> when (e.code) {
        "invalid_token" -> getString(R.string.error_invalid_token)
        "invalid_code" -> getString(R.string.error_invalid_code)
        "locked_out" -> getString(R.string.error_locked_out)
        "forbidden" -> getString(R.string.error_forbidden)
        "command_blocked" -> getString(R.string.error_command_blocked)
        "player_offline" -> getString(R.string.error_player_offline)
        "player_unknown" -> getString(R.string.error_player_unknown)
        "world_unknown" -> getString(R.string.error_world_unknown)
        "schedule_unknown" -> getString(R.string.error_schedule_unknown)
        "too_large" -> getString(R.string.error_too_large)
        "too_many_streams" -> getString(R.string.error_too_many_streams)
        "timeout" -> getString(R.string.error_server_timeout)
        else -> e.message?.takeIf { it.isNotBlank() } ?: getString(R.string.error_http, e.status)
    }
    else -> e.message ?: getString(R.string.error_unknown)
}
