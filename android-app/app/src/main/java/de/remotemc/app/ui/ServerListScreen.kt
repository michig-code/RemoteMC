package de.remotemc.app.ui

import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
import de.remotemc.app.net.Http
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Last status per server, so the list shows it instantly when you come back to it. */
private val ProbeCache = java.util.concurrent.ConcurrentHashMap<String, Probed>()

private class Probed(val probe: ServerProbe, val at: Long = SystemClock.elapsedRealtime()) {
    val fresh get() = SystemClock.elapsedRealtime() - at < 2_000
}

private suspend fun check(server: ServerProfile, probe: suspend (ServerProfile) -> ServerStatus): ServerProbe {
    val result = try {
        ServerProbe.Online(probe(server))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        ServerProbe.Offline(e)
    }
    ProbeCache[server.id] = Probed(result)
    return result
}

/** Live status shown on a server card. */
sealed interface ServerProbe {
    data object Loading : ServerProbe
    data class Online(val status: ServerStatus) : ServerProbe
    data class Offline(val reason: Throwable) : ServerProbe
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerListScreen(
    servers: List<ServerProfile>,
    onOpen: (ServerProfile) -> Unit,
    onAdd: () -> Unit,
    onRename: (ServerProfile, String) -> Unit,
    onRepair: (ServerProfile) -> Unit,
    onDelete: (ServerProfile) -> Unit,
    onSettings: () -> Unit,
    onPin: (ServerProfile, Boolean) -> Unit = { _, _ -> },
    onMoveToFolder: (ServerProfile, String) -> Unit = { _, _ -> },
    onRenameFolder: (old: String, new: String) -> Unit = { _, _ -> },
    onRemoveFolder: (String) -> Unit = {},
    onReorder: (List<ServerProfile>) -> Unit = {},
    onToggleNotify: (ServerProfile) -> Unit = {},
    alertsOn: Boolean = false,
    probe: suspend (ServerProfile) -> ServerStatus = { ApiClient(it, Http.quick).status() },
    initialQuery: String = "",
) {
    var renaming by remember { mutableStateOf<ServerProfile?>(null) }
    var deleting by remember { mutableStateOf<ServerProfile?>(null) }
    var moving by remember { mutableStateOf<ServerProfile?>(null) }
    var renamingFolder by remember { mutableStateOf<String?>(null) }
    var removingFolder by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var collapsed by rememberSaveable { mutableStateOf(listOf<String>()) }
    val haptics = rememberHaptics()
    val folders = remember(servers) { servers.map { it.folder }.filter { it.isNotBlank() }.distinct() }
    val rows = buildRows(servers, query, collapsed)

    // Pull down to check all servers again (also those in collapsed folders).
    var refreshing by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = {
        if (!refreshing) {
            refreshing = true
            haptics.tap()
            scope.launch {
                try {
                    servers.map { async { check(it, probe) } }.awaitAll()
                } finally {
                    refreshing = false
                    refreshTick++
                }
            }
        }
    }

    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val id = from.key as? String ?: return@rememberReorderableLazyListState
        val result = drop(servers, rows, id, to.key, collapsed) ?: return@rememberReorderableLazyListState
        result.expand?.let { collapsed = collapsed - it }
        onReorder(result.servers)
        haptics.tick()
    }

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
        PullToRefreshBox(refreshing, refresh, Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.fillMaxSize().widthIn(max = 720.dp),
                state = listState,
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
                    if (servers.size > 1) {
                        item(key = "search") { SearchField(query) { query = it } }
                    }
                    if (rows.isEmpty()) {
                        item(key = "no-match") {
                            Text(
                                stringResource(R.string.servers_no_match, query.trim()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    itemsIndexed(rows, key = { _, row -> row.key }) { i, row ->
                        when (row) {
                            is ListRow.Title -> ReorderableItem(reorder, key = row.key) {
                                SectionTitle(stringResource(row.text))
                            }
                            is ListRow.Folder -> ReorderableItem(reorder, key = row.key) { FolderHeader(
                                row,
                                onToggle = {
                                    haptics.tap()
                                    collapsed = if (row.expanded) collapsed + row.name else collapsed - row.name
                                },
                                onRename = { renamingFolder = row.name },
                                onRemove = { removingFolder = row.name },
                            ) }
                            is ListRow.Server -> ReorderableItem(reorder, key = row.key) { dragging ->
                                val server = row.server
                                ServerCard(
                                    server = server,
                                    probe = probe,
                                    refreshTick = refreshTick,
                                    dragging = dragging,
                                    onOpen = { onOpen(server) },
                                    onRename = { renaming = server },
                                    onPin = { onPin(server, !server.pinned) },
                                    onMoveToFolder = { moving = server },
                                    alertsOn = alertsOn,
                                    onToggleNotify = { onToggleNotify(server) },
                                    onRepair = { onRepair(server) },
                                    onDelete = { deleting = server },
                                    // Hold a server to drag it; not while searching, the list is filtered then.
                                    modifier = Modifier
                                        .longPressDraggableHandle(enabled = query.isBlank(), onDragStarted = { haptics.heavy() })
                                        .staggeredAppear(i + 1),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { profile ->
        NameDialog(
            title = stringResource(R.string.servers_rename),
            label = stringResource(R.string.pair_server_name),
            initial = profile.name,
            onDismiss = { renaming = null },
            onSave = { onRename(profile, it); renaming = null },
        )
    }

    renamingFolder?.let { folder ->
        NameDialog(
            title = stringResource(R.string.servers_rename_folder),
            label = stringResource(R.string.servers_folder_name),
            initial = folder,
            onDismiss = { renamingFolder = null },
            onSave = { onRenameFolder(folder, it); renamingFolder = null },
        )
    }

    moving?.let { profile ->
        MoveToFolderDialog(
            current = profile.folder,
            folders = folders,
            onDismiss = { moving = null },
            onMove = { folder ->
                onMoveToFolder(profile, folder)
                // Pinned servers are listed on top; moving one into a folder means "show it there".
                if (profile.pinned && folder.isNotBlank()) onPin(profile, false)
                collapsed = collapsed - folder
                moving = null
            },
        )
    }

    removingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { removingFolder = null },
            icon = { Icon(Icons.Outlined.FolderOff, null) },
            title = { Text(stringResource(R.string.servers_remove_folder)) },
            text = { Text(stringResource(R.string.servers_remove_folder_text, folder)) },
            confirmButton = {
                TextButton(onClick = { onRemoveFolder(folder); removingFolder = null }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { removingFolder = null }) { Text(stringResource(R.string.action_cancel)) } },
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
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.servers_search)) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.action_clear)) }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun FolderHeader(row: ListRow.Folder, onToggle: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (row.expanded) 0f else -90f, label = "chevron")
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onToggle).padding(start = 4.dp, top = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.ExpandMore,
            stringResource(if (row.expanded) R.string.servers_folder_collapse else R.string.servers_folder_expand),
            Modifier.size(20.dp).rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Badge(row.count.toString(), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.action_more)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.servers_rename_folder)) },
                    leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                    onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.servers_remove_folder)) },
                    leadingIcon = { Icon(Icons.Outlined.FolderOff, null) },
                    onClick = { menu = false; onRemove() },
                )
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, label: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { if (it.length <= 40) name = it }, singleLine = true, label = { Text(label) })
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun MoveToFolderDialog(current: String, folders: List<String>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    var newFolder by remember { mutableStateOf("") }
    val target = newFolder.trim().ifEmpty { selected }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
        title = { Text(stringResource(R.string.servers_move_to_folder)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                (listOf("") + folders).forEach { folder ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                            .clickable { selected = folder; newFolder = "" }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = newFolder.isBlank() && selected == folder, onClick = { selected = folder; newFolder = "" })
                        Text(folder.ifEmpty { stringResource(R.string.servers_no_folder) }, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    newFolder,
                    { if (it.length <= 30) newFolder = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.servers_new_folder)) },
                    leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = target != current, onClick = { onMove(target) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
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
    dragging: Boolean,
    refreshTick: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onMoveToFolder: () -> Unit,
    alertsOn: Boolean,
    onToggleNotify: () -> Unit,
    onRepair: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val haptics = rememberHaptics()
    // Keyed on the connection only, so pinning, sorting or renaming does not probe again.
    // Lint misses the assignments inside the loop (false positive).
    @SuppressLint("ProduceStateDoesNotAssignValue")
    val state by produceState(
        ProbeCache[server.id]?.probe ?: ServerProbe.Loading,
        server.id, server.baseUrl, server.token, refreshKey, refreshTick,
    ) {
        // Show the last known state right away and refresh it in the background; a tap on the
        // status shows "Checking…" again. After a pull to refresh the result is already fresh.
        val cached = ProbeCache[server.id]
        if (cached != null) value = cached.probe
        var skipFirst = cached?.fresh == true
        if (!skipFirst && (refreshKey > 0 || cached == null)) value = ServerProbe.Loading
        while (true) {
            if (!skipFirst) value = check(server, probe)
            skipFirst = false
            delay(if (value is ServerProbe.Online) 10_000 else 3_000)
        }
    }
    val connection = when (state) {
        is ServerProbe.Online -> ConnectionState.Online
        is ServerProbe.Offline -> ConnectionState.Offline
        ServerProbe.Loading -> ConnectionState.Connecting
    }

    val lift by animateFloatAsState(if (dragging) 1.03f else 1f, label = "lift")
    McCard(
        modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f).graphicsLayer {
            scaleX = lift
            scaleY = lift
            shadowElevation = if (dragging) 16.dp.toPx() else 0f
            shape = RoundedCornerShape(24.dp)
            clip = false
        },
        onClick = { haptics.tap(); onOpen() },
    ) {
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
                        if (server.pinned) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.PushPin, stringResource(R.string.servers_pinned),
                                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (alertsOn && !server.notify) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Outlined.NotificationsOff, stringResource(R.string.servers_notify_muted),
                                Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                            text = { Text(stringResource(if (server.pinned) R.string.servers_unpin else R.string.servers_pin)) },
                            leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                            onClick = { menu = false; onPin() },
                        )
                        if (alertsOn) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (server.notify) R.string.servers_notify_off else R.string.servers_notify_on)) },
                                leadingIcon = {
                                    Icon(if (server.notify) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications, null)
                                },
                                onClick = { menu = false; onToggleNotify() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.servers_move_to_folder)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                            onClick = { menu = false; onMoveToFolder() },
                        )
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
