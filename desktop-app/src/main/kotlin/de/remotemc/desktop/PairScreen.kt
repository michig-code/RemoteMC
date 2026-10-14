package de.remotemc.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.ConnectLink
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.net.ApiClient
import de.remotemc.app.net.PingInfo
import de.remotemc.app.net.Tls
import de.remotemc.desktop.res.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private data class Checked(
    val host: String, val port: Int, val tls: Boolean, val ping: PingInfo,
    val fingerprint: String?, val verified: Boolean, val mismatch: Boolean,
)

/** Adds a server with the 6-digit code from /remotemc pair, a pasted link, or an access token. */
@Composable
fun PairScreen(model: AppModel, link: String?, onDone: (String?) -> Unit, repair: ServerProfile? = null) {
    val parsed = remember(link) { link?.let(ConnectLink::parse) }
    val settings by model.settings.settings.collectAsState()
    var host by remember { mutableStateOf(parsed?.host ?: repair?.host.orEmpty()) }
    var port by remember { mutableStateOf((parsed?.port ?: repair?.port ?: 8765).toString()) }
    var code by remember { mutableStateOf(parsed?.code.orEmpty()) }
    var token by remember { mutableStateOf(parsed?.token.orEmpty()) }
    var useToken by remember { mutableStateOf(parsed?.token != null) }
    var tls by remember { mutableStateOf(parsed?.tls ?: repair?.tls ?: true) }
    var name by remember { mutableStateOf(repair?.name.orEmpty()) }
    var device by remember { mutableStateOf(repair?.deviceName?.takeIf { it.isNotBlank() } ?: settings.deviceName) }
    var checked by remember { mutableStateOf<Checked?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val expected = parsed?.fingerprint

    fun check() {
        val p = port.toIntOrNull() ?: return
        busy = true
        error = null
        scope.launch {
            try {
                val r = ApiClient.probe(host.trim(), p, tls)
                checked = Checked(host.trim(), p, tls, r.ping, r.fingerprint,
                    verified = expected != null && expected == r.fingerprint,
                    mismatch = expected != null && expected != r.fingerprint)
                if (name.isBlank()) name = host.trim()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = errorText(e)
            } finally {
                busy = false
            }
        }
    }

    fun pair(c: Checked) {
        busy = true
        error = null
        scope.launch {
            try {
                // Pairing again keeps the server's id (open tab), folder, pins and notification choice.
                val base = repair ?: ServerProfile(name = "", host = c.host, token = "")
                val profile = if (!useToken) {
                    val r = ApiClient.pair(c.host, c.port, c.tls, c.fingerprint, code.trim(), device.trim())
                    base.copy(token = r.token, role = r.role)
                } else {
                    val candidate = base.copy(token = token.trim(), port = c.port, tls = c.tls, fingerprint = c.fingerprint)
                    base.copy(token = token.trim(), role = ApiClient(candidate).me().role)
                }.copy(
                    name = name.trim().ifBlank { c.host }, host = c.host, port = c.port, tls = c.tls,
                    fingerprint = c.fingerprint, deviceName = device.trim(),
                )
                model.servers.upsert(profile)
                onDone(profile.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = errorText(e)
            } finally {
                busy = false
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (checked != null) checked = null else onDone(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.pair_title), style = MaterialTheme.typography.headlineMedium)
                }
                val c = checked
                if (c == null) {
                    Text(stringResource(Res.string.pair_intro_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                        Text("/remotemc pair", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(host, { host = it.trim() }, label = { Text(stringResource(Res.string.pair_address)) }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(port, { v -> port = v.filter(Char::isDigit).take(5) }, label = { Text(stringResource(Res.string.pair_port)) },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(150.dp))
                    }
                    if (!useToken) {
                        OutlinedTextField(code, { v -> code = v.filter(Char::isDigit).take(6) }, label = { Text(stringResource(Res.string.pair_code)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                    } else {
                        OutlinedTextField(token, { token = it.trim() }, label = { Text(stringResource(Res.string.pair_token)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { useToken = !useToken }) {
                        Text(stringResource(if (useToken) Res.string.pair_enter_code else Res.string.pair_use_token))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.pair_https))
                            Text(stringResource(Res.string.pair_https_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(tls, { tls = it })
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = ::check,
                        enabled = !busy && host.isNotBlank() && port.toIntOrNull() != null && (if (useToken) token.isNotBlank() else code.length == 6),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(Res.string.action_continue))
                    }
                } else {
                    Text(stringResource(Res.string.pair_confirm_title), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${c.host}:${c.port}", style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(Res.string.pair_server_info, c.ping.platform, c.ping.version), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when {
                        c.mismatch -> Notice(Icons.Outlined.GppBad, McColors.Offline, stringResource(Res.string.pair_mismatch_title), stringResource(Res.string.pair_mismatch_text))
                        !c.tls -> Notice(Icons.Outlined.GppMaybe, McColors.Warning, stringResource(Res.string.pair_unencrypted_title), stringResource(Res.string.pair_unencrypted_text))
                        c.verified -> Notice(Icons.Outlined.VerifiedUser, McColors.Online, stringResource(Res.string.pair_verified_title), stringResource(Res.string.pair_verified_text))
                        c.fingerprint != null -> Notice(
                            Icons.Outlined.GppMaybe, MaterialTheme.colorScheme.primary,
                            stringResource(Res.string.pair_compare_title),
                            stringResource(Res.string.pair_compare_text) + "\n" + Tls.shortFingerprint(c.fingerprint),
                        )
                        else -> Notice(Icons.Outlined.VerifiedUser, McColors.Online, stringResource(Res.string.pair_ca_title), stringResource(Res.string.pair_ca_text))
                    }
                    OutlinedTextField(name, { if (it.length <= 40) name = it }, label = { Text(stringResource(Res.string.pair_server_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (!useToken) {
                        OutlinedTextField(device, { if (it.length <= 40) device = it }, label = { Text(stringResource(Res.string.pair_device_name)) },
                            supportingText = { Text(stringResource(Res.string.pair_device_name_text)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { pair(c) }, enabled = !busy && !c.mismatch && device.isNotBlank(), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(if (useToken) Res.string.pair_action_token else Res.string.pair_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(icon: ImageVector, color: Color, title: String, text: String) {
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = color)
                Spacer(Modifier.height(4.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, fontFamily = if ('\n' in text) FontFamily.Default else null)
            }
        }
    }
}
