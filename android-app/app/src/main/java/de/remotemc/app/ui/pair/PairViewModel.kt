package de.remotemc.app.ui.pair

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.remotemc.app.app
import de.remotemc.app.data.ConnectLink
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.suggestedDeviceName
import de.remotemc.app.net.ApiClient
import de.remotemc.app.net.PingInfo
import kotlinx.coroutines.launch

enum class PairMethod { CODE, TOKEN }

/** Pairing flow: choose → form → (probe) → confirm → pair. */
sealed interface PairStep {
    data object Choose : PairStep
    data class Form(val method: PairMethod) : PairStep
    data object Probing : PairStep

    data class Confirm(
        val host: String,
        val port: Int,
        val tls: Boolean,
        val ping: PingInfo,
        /** Fingerprint actually presented by the server. */
        val fingerprint: String?,
        /** True if the fingerprint came from a scanned QR code / link and matches. */
        val verified: Boolean,
        /** QR code fingerprint differs from the server's: refuse. */
        val mismatch: Boolean,
        val code: String?,
        val token: String?,
    ) : PairStep

    data object Pairing : PairStep
    data class Done(val profile: ServerProfile) : PairStep
}

class PairViewModel(application: Application) : AndroidViewModel(application) {

    var step by mutableStateOf<PairStep>(PairStep.Choose)
        internal set // screenshot tests jump to a step
    var error by mutableStateOf<Throwable?>(null)
        private set

    // Form fields
    var host by mutableStateOf("")
    var port by mutableStateOf("8765")
    var code by mutableStateOf("")
    var token by mutableStateOf("")
    var tls by mutableStateOf(true)
    var serverName by mutableStateOf("")
    var deviceName by mutableStateOf(
        application.app.settings.settings.value.deviceName.ifBlank { suggestedDeviceName() },
    )

    /** Server being re-paired (keeps its id and name). */
    private var replacing: ServerProfile? = null
    private var expectedFingerprint: String? = null

    fun startRepair(profile: ServerProfile) {
        replacing = profile
        host = profile.host
        port = profile.port.toString()
        tls = profile.tls
        serverName = profile.name
        if (profile.deviceName.isNotBlank()) deviceName = profile.deviceName
    }

    fun choose(method: PairMethod) {
        error = null
        step = PairStep.Form(method)
    }

    fun back(): Boolean {
        error = null
        step = when (step) {
            is PairStep.Form -> PairStep.Choose
            is PairStep.Confirm -> PairStep.Form(if (token.isNotBlank() && code.isBlank()) PairMethod.TOKEN else PairMethod.CODE)
            else -> return false
        }
        return true
    }

    /** From a scanned QR code or an opened remotemc:// link. */
    fun useLink(link: ConnectLink) {
        host = link.host
        port = link.port.toString()
        tls = link.tls
        code = link.code.orEmpty()
        token = link.token.orEmpty()
        expectedFingerprint = link.fingerprint
        probe()
    }

    fun submitForm(method: PairMethod) {
        expectedFingerprint = null
        if (method == PairMethod.CODE) token = "" else code = ""
        probe()
    }

    private fun probe() {
        val p = port.toIntOrNull()
        if (host.isBlank() || p == null || p !in 1..65535) return
        error = null
        step = PairStep.Probing
        viewModelScope.launch {
            try {
                val result = ApiClient.probe(host.trim(), p, tls)
                val expected = expectedFingerprint
                if (serverName.isBlank()) serverName = host.trim()
                step = PairStep.Confirm(
                    host = host.trim(),
                    port = p,
                    tls = tls,
                    ping = result.ping,
                    fingerprint = result.fingerprint,
                    verified = expected != null && expected == result.fingerprint,
                    mismatch = expected != null && expected != result.fingerprint,
                    code = code.takeIf { it.isNotBlank() },
                    token = token.takeIf { it.isNotBlank() },
                )
            } catch (e: Exception) {
                error = e
                step = PairStep.Form(if (token.isNotBlank() && code.isBlank()) PairMethod.TOKEN else PairMethod.CODE)
            }
        }
    }

    fun pair() {
        val confirm = step as? PairStep.Confirm ?: return
        if (confirm.mismatch) return
        error = null
        step = PairStep.Pairing
        viewModelScope.launch {
            try {
                val base = replacing ?: ServerProfile(name = "", host = confirm.host, token = "")
                val profile = if (confirm.code != null) {
                    val result = ApiClient.pair(confirm.host, confirm.port, confirm.tls, confirm.fingerprint, confirm.code, deviceName.trim())
                    base.copy(token = result.token, role = result.role)
                } else {
                    val candidate = base.copy(token = confirm.token.orEmpty())
                        .copy(host = confirm.host, port = confirm.port, tls = confirm.tls, fingerprint = confirm.fingerprint)
                    base.copy(token = confirm.token.orEmpty(), role = ApiClient(candidate).me().role)
                }.copy(
                    name = serverName.trim().ifBlank { confirm.host },
                    host = confirm.host,
                    port = confirm.port,
                    tls = confirm.tls,
                    fingerprint = confirm.fingerprint,
                    deviceName = deviceName.trim(),
                )
                getApplication<Application>().app.servers.upsert(profile)
                step = PairStep.Done(profile)
            } catch (e: Exception) {
                error = e
                step = confirm
            }
        }
    }
}
