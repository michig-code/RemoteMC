package de.remotemc.app.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.net.ApiClient
import de.remotemc.app.ui.components.AppLogo
import de.remotemc.app.ui.components.Badge
import de.remotemc.app.ui.components.ConnectionState
import de.remotemc.app.ui.components.EmptyState
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.components.SectionTitle
import de.remotemc.app.ui.components.StatusPill
import de.remotemc.app.ui.components.staggeredAppear
import de.remotemc.app.ui.components.tpsColor
import de.remotemc.app.ui.theme.McColors
import de.remotemc.app.ui.theme.statusTextColor
import de.remotemc.app.ui.util.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Live status shown on a server card. */
sealed interface ServerProbe {
    data object Loading : ServerProbe
    data class Online(val status: ServerStatus) : ServerProbe
    data class Offline(val reason: Throwable) : ServerProbe
}

@Composable
fun ServerListScreen(
    servers: List<ServerProfile>,
    onOpen: (ServerProfile) -> Unit,
    onAdd: () -> Unit,
    onRename: (ServerProfile, String) -> Unit,
    onRepair: (ServerProfile) -> Unit,
    onDelete: (ServerProfile) -> Unit,
    onSettings: () -> Unit,
    probe: suspend (ServerProfile) -> ServerStatus = { ApiClient(it).status() },
) {
    var renaming by remember { mutableStateOf<ServerProfile?>(null) }
    var deleting by remember { mutableStateOf<ServerProfile?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.servers_add)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.fillMaxSize().widthIn(max = 720.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 24.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") { Header(servers.size, onSettings) }
                if (servers.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(Icons.Outlined.Dns, stringResource(R.string.servers_empty_title), stringResource(R.string.servers_empty_text))
                    }
                } else {
                    item(key = "title") { SectionTitle(stringResource(R.string.servers_title)) }
                    itemsIndexed(servers, key = { _, s -> s.id }) { i, server ->
                        ServerCard(
                            server = server,
                            probe = probe,
                            onOpen = { onOpen(server) },
                            onRename = { renaming = server },
                            onRepair = { onRepair(server) },
                            onDelete = { deleting = server },
                            modifier = Modifier.animateItem().staggeredAppear(i + 1),
                        )
                    }
                }
            }
        }
    }

    renaming?.let { profile ->
        var name by remember(profile.id) { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.servers_rename)) },
            text = {
                OutlinedTextField(name, { if (it.length <= 40) name = it }, singleLine = true, label = { Text(stringResource(R.string.pair_server_name)) })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { onRename(profile, name.trim()); renaming = null }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    deleting?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(stringResource(R.string.servers_delete_title)) },
            text = { Text(stringResource(R.string.servers_delete_text, profile.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(profile); deleting = null }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Header(count: Int, onSettings: () -> Unit) {
    // Logo pops in with a little bounce and wobble on first show.
    val scale = remember { Animatable(0.4f) }
    val rotation = remember { Animatable(-18f) }
    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }
        rotation.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessLow))
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AppLogo(
            64.dp,
            Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = rotation.value
            },
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(
                if (count == 0) stringResource(R.string.servers_tagline) else pluralStringResource(R.plurals.servers_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title)) }
    }
}

@Composable
private fun ServerCard(
    server: ServerProfile,
    probe: suspend (ServerProfile) -> ServerStatus,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onRepair: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val haptics = rememberHaptics()
    // Lint misses the assignments inside the loop (false positive).
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val state by produceState<ServerProbe>(ServerProbe.Loading, server, refreshKey) {
        value = ServerProbe.Loading
        while (true) {
            val result = try {
                ServerProbe.Online(probe(server))
            } catch (e: Exception) {
                ServerProbe.Offline(e)
            }
            value = result
            delay(15_000)
        }
    }
    val connection = when (state) {
        is ServerProbe.Online -> ConnectionState.Online
        is ServerProbe.Offline -> ConnectionState.Offline
        ServerProbe.Loading -> ConnectionState.Connecting
    }

    McCard(modifier.fillMaxWidth(), onClick = { haptics.tap(); onOpen() }) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (server.role != Role.ADMIN) {
                            Spacer(Modifier.width(6.dp))
                            Badge(roleLabel(server.role), MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Text(
                        server.address + if (!server.tls) " · " + stringResource(R.string.servers_unencrypted) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!server.tls) statusTextColor(McColors.Warning) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.action_more)) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.servers_rename)) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menu = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_pair_again)) },
                            leadingIcon = { Icon(Icons.Outlined.Link, null) },
                            onClick = { menu = false; onRepair() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_remove)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
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
                        is ServerProbe.Online -> stringResource(R.string.status_online_short)
                        is ServerProbe.Offline -> stringResource(R.string.status_offline_short)
                        ServerProbe.Loading -> stringResource(R.string.status_checking)
                    },
                    refreshing = state == ServerProbe.Loading,
                    onClick = { haptics.tap(); refreshKey++ },
                )
                Spacer(Modifier.weight(1f))
                AnimatedContent(state, label = "probe", transitionSpec = { fadeIn() togetherWith fadeOut() }) { s ->
                    if (s is ServerProbe.Online) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Group, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text("${s.status.onlinePlayers}/${s.status.maxPlayers}", style = MaterialTheme.typography.labelLarge)
                            s.status.tps.firstOrNull()?.let { tps ->
                                Spacer(Modifier.width(12.dp))
                                Icon(Icons.Outlined.Speed, null, Modifier.size(16.dp), tint = tpsColor(tps))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.unit_tps, tps),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = statusTextColor(tpsColor(tps)),
                                )
                            }
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
fun roleLabel(role: Role) = stringResource(
    when (role) {
        Role.ADMIN -> R.string.role_admin
        Role.MODERATOR -> R.string.role_moderator
        Role.VIEWER -> R.string.role_viewer
    },
)
