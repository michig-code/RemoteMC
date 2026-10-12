package de.mcremote.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.mcremote.app.data.ApiClient
import de.mcremote.app.data.ServerProfile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    servers: List<ServerProfile>,
    pendingLink: ServerProfile?,
    onLinkConsumed: () -> Unit,
    onOpen: (ServerProfile) -> Unit,
    onSave: (ServerProfile) -> Unit,
    onDelete: (ServerProfile) -> Unit,
) {
    // null = closed, otherwise the profile being edited (a fresh one for "add")
    var editing by remember { mutableStateOf<ServerProfile?>(null) }
    var deleting by remember { mutableStateOf<ServerProfile?>(null) }

    LaunchedEffect(pendingLink) {
        if (pendingLink != null) {
            editing = pendingLink
            onLinkConsumed()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("MC Remote") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = ServerProfile(name = "", host = "", token = "") },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Server") },
            )
        },
    ) { padding ->
        if (servers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Dns, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(16.dp))
                    Text("Noch kein Server", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Installiere das McRemote-Plugin auf deinem Server und gib dort " +
                            "/mcremote link ein. Den Link kannst du hier einfügen.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(servers, key = { it.id }) { server ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(server) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(server.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${server.host}:${server.port}" + if (server.https) " (HTTPS)" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { editing = server }) { Icon(Icons.Default.Edit, "Bearbeiten") }
                            IconButton(onClick = { deleting = server }) { Icon(Icons.Default.Delete, "Löschen") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { profile ->
        ServerEditDialog(
            initial = profile,
            onDismiss = { editing = null },
            onSave = {
                onSave(it)
                editing = null
            },
        )
    }

    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Server löschen?") },
            text = { Text("\"${profile.name}\" wird aus der App entfernt.") },
            confirmButton = {
                TextButton(onClick = { onDelete(profile); deleting = null }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun ServerEditDialog(
    initial: ServerProfile,
    onDismiss: () -> Unit,
    onSave: (ServerProfile) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var token by remember { mutableStateOf(initial.token) }
    var https by remember { mutableStateOf(initial.https) }
    var showToken by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    fun build(): ServerProfile? {
        val p = port.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        if (host.isBlank() || token.isBlank()) return null
        return initial.copy(
            name = name.ifBlank { host.trim() },
            host = host.trim(),
            port = p,
            token = token.trim(),
            https = https,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.host.isEmpty()) "Server hinzufügen" else "Server bearbeiten") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val link = clipboard.getText()?.text.orEmpty()
                        val parsed = ServerProfile.fromLink(link)
                        if (parsed != null) {
                            host = parsed.host
                            port = parsed.port.toString()
                            token = parsed.token
                            https = parsed.https
                            if (name.isBlank()) name = parsed.name
                            testResult = "Link übernommen"
                        } else {
                            testResult = "Kein gültiger mcremote:// Link in der Zwischenablage"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.ContentPaste, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Link aus Zwischenablage")
                }
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("Adresse / IP") }, singleLine = true)
                OutlinedTextField(
                    port, { port = it.filter(Char::isDigit) },
                    label = { Text("API-Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    token, { token = it },
                    label = { Text("Token") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                        }
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("HTTPS (Reverse-Proxy)", Modifier.weight(1f))
                    Switch(https, { https = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = !testing,
                        onClick = {
                            val profile = build()
                            if (profile == null) {
                                testResult = "Bitte Adresse, Port und Token ausfüllen"
                                return@OutlinedButton
                            }
                            testing = true
                            scope.launch {
                                testResult = try {
                                    val api = ApiClient(profile)
                                    val version = api.ping()
                                    val status = api.status()
                                    "✓ Verbunden – Plugin $version, ${status.onlinePlayers}/${status.maxPlayers} Spieler"
                                } catch (e: Exception) {
                                    "✗ ${e.message}"
                                }
                                testing = false
                            }
                        },
                    ) { Text("Verbindung testen") }
                    if (testing) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val profile = build()
                if (profile != null) onSave(profile) else testResult = "Bitte Adresse, Port und Token ausfüllen"
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
