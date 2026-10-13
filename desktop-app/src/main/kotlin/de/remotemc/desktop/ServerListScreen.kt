package de.remotemc.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.ConnectLink
import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.net.ApiClient
import de.remotemc.app.net.Http
import de.remotemc.desktop.res.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.util.concurrent.ConcurrentHashMap

private sealed interface Probe {
    data object Loading : Probe
    data class Online(val status: ServerStatus) : Probe
    data object Offline : Probe
}

private val ProbeCache = ConcurrentHashMap<String, Probe>()

@Composable
fun ServerListScreen(
    model: AppModel,
    onOpen: (String) -> Unit,
    onAdd: (String?) -> Unit,
    onSettings: () -> Unit,
    probe: suspend (ServerProfile) -> ServerStatus = { ApiClient(it, Http.quick).status() },
) {
    val servers by model.servers.servers.collectAsState()
    val alertsOn = model.settings.settings.collectAsState().value.alerts.enabled
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ServerProfile?>(null) }
    var deleting by remember { mutableStateOf<ServerProfile?>(null) }
    val clipboard = LocalClipboardManager.current
    val shown = servers.filter { it.matches(query) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAdd(null) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(Res.string.servers_add)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                GridCells.Adaptive(340.dp),
                Modifier.fillMaxSize().widthIn(max = 1200.dp),
                contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(56.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge)
                            Text(
                                if (servers.isEmpty()) stringResource(Res.string.servers_tagline)
                                else pluralStringResource(Res.plurals.servers_count, servers.size, servers.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = {
                            val text = clipboard.getText()?.text.orEmpty()
                            onAdd(text.takeIf { ConnectLink.parse(it) != null })
                        }) {
                            Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.scan_paste_link))
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(Res.string.settings_title)) }
                    }
                }
                if (servers.size > 1) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TextField(
                            query, { query = it },
                            placeholder = { Text(stringResource(Res.string.servers_search)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            singleLine = true,
                            shape = CircleShape,
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (servers.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().height(320.dp)) {
                            EmptyState(stringResource(Res.string.servers_empty_title), stringResource(Res.string.servers_empty_text))
                        }
                    }
                }
                itemsIndexed(shown, key = { _, it -> it.id }) { index, server ->
                    Box(Modifier.staggeredAppear(index)) {
                        ServerCard(
                            server, probe, { onOpen(server.id) }, { renaming = server }, { deleting = server },
                            alertsOn = alertsOn, onToggleNotify = { model.servers.upsert(server.copy(notify = !server.notify)) },
                        )
                    }
                }
            }
        }
    }

    renaming?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(Res.string.servers_rename)) },
            text = { OutlinedTextField(name, { if (it.length <= 40) name = it }, singleLine = true, label = { Text(stringResource(Res.string.pair_server_name)) }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { model.servers.upsert(p.copy(name = name.trim())); renaming = null }) {
                    Text(stringResource(Res.string.action_save))
                }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(Res.string.servers_delete_title)) },
            text = { Text(stringResource(Res.string.servers_delete_text, p.name)) },
            confirmButton = { TextButton(onClick = { model.servers.delete(p.id); deleting = null }) { Text(stringResource(Res.string.action_remove)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ServerCard(
    server: ServerProfile,
    probe: suspend (ServerProfile) -> ServerStatus,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    alertsOn: Boolean = false,
    onToggleNotify: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val state by produceState(ProbeCache[server.id] ?: Probe.Loading, server.id, server.baseUrl, server.token, refresh) {
        if (refresh > 0) value = Probe.Loading
        while (true) {
            value = try {
                Probe.Online(probe(server))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Probe.Offline
            }
            ProbeCache[server.id] = value
            delay(if (value is Probe.Online) 10_000 else 3_000)
        }
    }
    McCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) { Text(server.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (alertsOn && !server.notify) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Outlined.NotificationsOff, stringResource(Res.string.servers_notify_muted), Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (server.role != Role.ADMIN) {
                            Spacer(Modifier.width(6.dp))
                            Badge(roleLabel(server.role), MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Text(
                        server.address + if (!server.tls) " · " + stringResource(Res.string.servers_unencrypted) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!server.tls) McColors.Warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(Res.string.action_more)) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem({ Text(stringResource(Res.string.servers_rename)) }, { menu = false; onRename() }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
                        if (alertsOn) {
                            DropdownMenuItem(
                                { Text(stringResource(if (server.notify) Res.string.servers_notify_off else Res.string.servers_notify_on)) },
                                { menu = false; onToggleNotify() },
                                leadingIcon = { Icon(if (server.notify) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications, null) },
                            )
                        }
                        DropdownMenuItem({ Text(stringResource(Res.string.action_remove)) }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = { refresh++ }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(
                            when (state) {
                                is Probe.Online -> Conn.LIVE
                                Probe.Offline -> Conn.OFFLINE
                                Probe.Loading -> Conn.CONNECTING
                            },
                            8.dp,
                        )
                        Text(
                            stringResource(
                                when (state) {
                                    is Probe.Online -> Res.string.status_online_short
                                    Probe.Offline -> Res.string.status_offline_short
                                    Probe.Loading -> Res.string.status_checking
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                (state as? Probe.Online)?.status?.let { s ->
                    Icon(Icons.Outlined.Group, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("${s.onlinePlayers}/${s.maxPlayers}", style = MaterialTheme.typography.labelLarge)
                    s.tps.firstOrNull()?.let { tps ->
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Outlined.Speed, null, Modifier.size(16.dp), tint = tpsColor(tps))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.unit_tps, "%.1f".format(tps)), style = MaterialTheme.typography.labelLarge, color = tpsColor(tps))
                    }
                }
            }
        }
    }
}
