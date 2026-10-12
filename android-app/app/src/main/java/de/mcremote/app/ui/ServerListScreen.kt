package de.mcremote.app.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import de.mcremote.app.data.ApiClient
import de.mcremote.app.data.ServerProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import de.mcremote.app.data.ServerStatus
import de.mcremote.app.ui.components.AppLogo
import de.mcremote.app.ui.components.ConnectionState
import de.mcremote.app.ui.components.EmptyState
import de.mcremote.app.ui.components.McCard
import de.mcremote.app.ui.components.SectionTitle
import de.mcremote.app.ui.components.StatusPill
import de.mcremote.app.ui.components.staggeredAppear
import de.mcremote.app.ui.components.tpsColor

/** Live status shown on a server card. */
sealed interface ServerProbe {
    data object Loading : ServerProbe
    data class Online(val status: ServerStatus) : ServerProbe
    data class Offline(val reason: String) : ServerProbe
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    servers: List<ServerProfile>,
    pendingLink: ServerProfile?,
    onLinkConsumed: () -> Unit,
    onOpen: (ServerProfile) -> Unit,
    onSave: (ServerProfile) -> Unit,
    onDelete: (ServerProfile) -> Unit,
    probe: suspend (ServerProfile) -> ServerStatus = { ApiClient(it).status() },
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
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = ServerProfile(name = "", host = "", token = "") },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Server hinzufügen") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 24.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { Header(servers.size) }
            if (servers.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Outlined.Dns,
                        "Noch kein Server",
                        "Installiere das McRemote-Plugin auf deinem Server, gib dort /mcremote link ein " +
                            "und füge den Link über „Server hinzufügen“ ein.",
                    )
                }
            } else {
                item(key = "title") { SectionTitle("Meine Server") }
                itemsIndexed(servers, key = { _, s -> s.id }) { i, server ->
                    ServerCard(
                        server = server,
                        probe = probe,
                        onOpen = { onOpen(server) },
                        onEdit = { editing = server },
                        onDelete = { deleting = server },
                        modifier = Modifier.animateItem().staggeredAppear(i + 1),
                    )
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
            icon = { Icon(Icons.Default.Delete, null) },
            title = { Text("Server löschen?") },
            text = { Text("\"${profile.name}\" wird aus der App entfernt. Am Server selbst ändert sich nichts.") },
            confirmButton = {
                TextButton(onClick = { onDelete(profile); deleting = null }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun Header(count: Int) {
    // Logo pops in with a little bounce and wobble on first show.
    val scale = remember { Animatable(0.4f) }
    val rotation = remember { Animatable(-18f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        rotation.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessLow))
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppLogo(
            72.dp,
            Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = rotation.value
            },
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text("MC Remote", style = MaterialTheme.typography.headlineLarge)
            Text(
                when (count) {
                    0 -> "Dein Server, in deiner Hosentasche"
                    1 -> "1 Server verbunden"
                    else -> "$count Server verbunden"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServerCard(
    server: ServerProfile,
    probe: suspend (ServerProfile) -> ServerStatus,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val state by produceState<ServerProbe>(ServerProbe.Loading, server, refreshKey) {
        while (true) {
            value = try {
                ServerProbe.Online(probe(server))
            } catch (e: Exception) {
                ServerProbe.Offline(e.message ?: "Offline")
            }
            delay(10_000)
        }
    }
    val connection = when (state) {
        is ServerProbe.Online -> ConnectionState.Online
        is ServerProbe.Offline -> ConnectionState.Offline
        ServerProbe.Loading -> ConnectionState.Connecting
    }

    McCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        server.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${server.host}:${server.port}" + if (server.https) " · HTTPS" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Mehr") }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Bearbeiten") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { menu = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Löschen") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    state = connection,
                    text = when (state) {
                        is ServerProbe.Online -> "Online"
                        is ServerProbe.Offline -> "Offline"
                        ServerProbe.Loading -> "Prüfe…"
                    },
                    refreshing = state == ServerProbe.Loading,
                    onClick = { refreshKey++ },
                )
                Spacer(Modifier.weight(1f))
                AnimatedContent(state, label = "probe", transitionSpec = { fadeIn() togetherWith fadeOut() }) { s ->
                    if (s is ServerProbe.Online) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Group, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text("${s.status.onlinePlayers}/${s.status.maxPlayers}", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(12.dp))
                            val tps = s.status.tps.firstOrNull() ?: 0.0
                            Icon(Icons.Outlined.Speed, null, Modifier.size(16.dp), tint = tpsColor(tps))
                            Spacer(Modifier.width(4.dp))
                            Text("%.1f TPS".format(tps), style = MaterialTheme.typography.labelLarge, color = tpsColor(tps))
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                }
            }
        }
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
        icon = { AppLogo(48.dp) },
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
