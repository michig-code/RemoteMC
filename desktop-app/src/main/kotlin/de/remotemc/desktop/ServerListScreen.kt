package de.remotemc.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import de.remotemc.app.data.ListRow
import de.remotemc.app.data.ListTitle
import de.remotemc.app.data.buildRows
import de.remotemc.app.data.drop
import de.remotemc.app.data.folderOf
import de.remotemc.app.data.mergeInto
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import kotlin.math.abs
import kotlin.math.sign
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
import de.remotemc.app.net.DemoServer
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
    var moving by remember { mutableStateOf<ServerProfile?>(null) }
    var renamingFolder by remember { mutableStateOf<String?>(null) }
    var removingFolder by remember { mutableStateOf<String?>(null) }
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    val clipboard = LocalClipboardManager.current
    val demoName = stringResource(Res.string.demo_name)
    val rows = buildRows(servers, query, collapsed)
    val folders = remember(servers) { servers.map { it.folder }.filter { it.isNotBlank() }.distinct() }

    // Drag a server onto the middle of another one to put both into a folder; past the middle they swap.
    val slots = remember { mutableStateMapOf<String, Rect>() } // where each card sits (without the drag)
    var held by remember { mutableStateOf<Pair<String, Rect>?>(null) } // the dragged card and where it is now
    var hoverTarget by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableStateOf<List<ServerProfile>?>(null) }
    var merging by remember { mutableStateOf<Pair<String, String>?>(null) }
    val currentServers by rememberUpdatedState(servers)
    val shownIds = rows.mapNotNullTo(HashSet()) { (it as? ListRow.Server)?.server?.id }
    fun targetUnder(r: Rect, self: String): String? = slots.entries.firstOrNull { (id, b) ->
        id != self && id in shownIds &&
            abs(r.center.x - b.center.x) < b.width * 0.3f && abs(r.center.y - b.center.y) < b.height * 0.3f
    }?.key
    /** Another card only swaps with the dragged one once that is past its middle (seen from where it came). */
    fun canSwap(id: String): Boolean {
        val (heldId, r) = held ?: return true
        if (id == heldId) return true
        val b = slots[id] ?: return true
        val from = slots[heldId] ?: return true
        val c = r.center
        return if (abs(b.center.y - from.center.y) > b.height / 2) {
            (c.y - b.center.y) * sign(b.center.y - from.center.y) > b.height * 0.2f
        } else {
            (c.x - b.center.x) * sign(b.center.x - from.center.x) > b.width * 0.2f
        }
    }

    val gridState = rememberLazyGridState()
    val reorder = rememberReorderableLazyGridState(gridState) { from, to ->
        val id = from.key as? String ?: return@rememberReorderableLazyGridState
        val result = drop(currentServers, rows, id, to.key, collapsed) ?: return@rememberReorderableLazyGridState
        result.expand?.let { collapsed = collapsed - it }
        model.servers.setOrder(result.servers)
    }

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
                state = gridState,
                contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
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
                    item(span = { GridItemSpan(maxLineSpan) }, key = "search") {
                        TextField(
                            query, { query = it },
                            placeholder = { Text(stringResource(Res.string.servers_search)) },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, stringResource(Res.string.action_clear)) }
                                }
                            },
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
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().height(180.dp)) {
                                EmptyState(stringResource(Res.string.servers_empty_title), stringResource(Res.string.servers_empty_text))
                            }
                            // A made-up server inside the app, to look around without a server of one's own.
                            OutlinedButton(onClick = {
                                model.servers.upsert(DemoServer.profile(demoName))
                                onOpen(DemoServer.ID)
                            }) { Text(stringResource(Res.string.demo_try)) }
                        }
                    }
                } else if (rows.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "no-match") {
                        Text(
                            stringResource(Res.string.servers_no_match, query.trim()),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }
                }
                rows.forEachIndexed { index, row ->
                    when (row) {
                        is ListRow.Title -> item(key = row.key, span = { GridItemSpan(maxLineSpan) }) {
                            // Nothing can go above the first title.
                            ReorderableItem(reorder, key = row.key, enabled = index > 0) {
                                Text(
                                    stringResource(if (row.title == ListTitle.PINNED) Res.string.servers_pinned else Res.string.servers_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                                )
                            }
                        }
                        is ListRow.Folder -> item(key = row.key, span = { GridItemSpan(maxLineSpan) }) {
                            ReorderableItem(reorder, key = row.key) {
                                FolderHeader(
                                    row,
                                    onToggle = { collapsed = if (row.expanded) collapsed + row.name else collapsed - row.name },
                                    onPin = { model.servers.setFolderPinned(row.name, !row.pinned) },
                                    onRename = { renamingFolder = row.name },
                                    onRemove = { removingFolder = row.name },
                                )
                            }
                        }
                        is ListRow.Server -> item(key = row.key) {
                            val server = row.server
                            ReorderableItem(
                                reorder, key = row.key, enabled = canSwap(row.key),
                                modifier = Modifier.onGloballyPositioned { c ->
                                    val r = c.boundsInRoot()
                                    if (slots[server.id] != r) slots[server.id] = r
                                },
                            ) { dragging ->
                                Box(Modifier.staggeredAppear(index)) {
                                    ServerCard(
                                        server, probe, { onOpen(server.id) }, { renaming = server }, { deleting = server },
                                        alertsOn = alertsOn, onToggleNotify = { model.servers.upsert(server.copy(notify = !server.notify)) },
                                        onPin = { model.servers.setPinned(server.id, !server.pinned) },
                                        onMoveToFolder = { moving = server },
                                        dragging = dragging,
                                        dropTarget = hoverTarget == server.id,
                                        modifier = Modifier
                                            .draggableHandle(
                                                enabled = query.isBlank(),
                                                onDragStarted = { dragStart = currentServers },
                                                onDragStopped = {
                                                    val target = hoverTarget
                                                    hoverTarget = null
                                                    held = null
                                                    if (target != null) {
                                                        val folder = folderOf(currentServers, target)
                                                        if (folder != null) {
                                                            // Onto a server in a folder: it simply joins that folder.
                                                            model.servers.setOrder(mergeInto(currentServers, server.id, target, folder))
                                                        } else {
                                                            merging = server.id to target
                                                        }
                                                    }
                                                },
                                            )
                                            .onGloballyPositioned { c ->
                                                if (dragging) {
                                                    val r = c.boundsInRoot()
                                                    held = server.id to r
                                                    hoverTarget = targetUnder(r, server.id)
                                                }
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    merging?.let { (id, target) ->
        // A new folder from two servers: name it, or cancel and the server goes back where it was.
        NameDialog(
            title = stringResource(Res.string.servers_new_folder),
            text = stringResource(Res.string.servers_new_folder_text),
            label = stringResource(Res.string.servers_folder_name),
            initial = "",
            confirm = stringResource(Res.string.servers_folder_create),
            onDismiss = {
                dragStart?.let { model.servers.setOrder(it) }
                merging = null
            },
            onSave = { name ->
                model.servers.setOrder(mergeInto(currentServers, id, target, name))
                collapsed = collapsed - name
                merging = null
            },
        )
    }
    renaming?.let { p ->
        NameDialog(
            title = stringResource(Res.string.servers_rename),
            label = stringResource(Res.string.pair_server_name),
            initial = p.name,
            onDismiss = { renaming = null },
            onSave = { model.servers.upsert(p.copy(name = it)); renaming = null },
        )
    }
    renamingFolder?.let { folder ->
        NameDialog(
            title = stringResource(Res.string.servers_rename_folder),
            label = stringResource(Res.string.servers_folder_name),
            initial = folder,
            onDismiss = { renamingFolder = null },
            onSave = { model.servers.renameFolder(folder, it); renamingFolder = null },
        )
    }
    moving?.let { p ->
        MoveToFolderDialog(
            current = p.folder,
            folders = folders,
            onDismiss = { moving = null },
            onMove = { folder ->
                model.servers.setFolder(p.id, folder)
                // Pinned servers are listed on top; moving one into a folder means "show it there".
                if (p.pinned && folder.isNotBlank()) model.servers.setPinned(p.id, false)
                collapsed = collapsed - folder
                moving = null
            },
        )
    }
    removingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { removingFolder = null },
            icon = { Icon(Icons.Outlined.FolderOff, null) },
            title = { Text(stringResource(Res.string.servers_remove_folder)) },
            text = { Text(stringResource(Res.string.servers_remove_folder_text, folder)) },
            confirmButton = {
                TextButton(onClick = { model.servers.removeFolder(folder); removingFolder = null }) { Text(stringResource(Res.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { removingFolder = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(Res.string.servers_delete_title)) },
            text = { Text(stringResource(Res.string.servers_delete_text, p.name)) },
            confirmButton = { TextButton(onClick = { model.servers.delete(p.id); model.extras.forget(p.id); deleting = null }) { Text(stringResource(Res.string.action_remove)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun FolderHeader(row: ListRow.Folder, onToggle: () -> Unit, onPin: () -> Unit, onRename: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (row.expanded) 0f else -90f, motion(), label = "chevron")
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onToggle)
            .pointerHoverIcon(PointerIcon.Hand).padding(start = 4.dp, top = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.ExpandMore,
            stringResource(if (row.expanded) Res.string.servers_folder_collapse else Res.string.servers_folder_expand),
            Modifier.size(20.dp).rotate(rotation),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(row.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Badge(row.count.toString(), MaterialTheme.colorScheme.onSurfaceVariant)
        if (row.pinned) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.PushPin, stringResource(Res.string.servers_pinned), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.weight(1f))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(Res.string.action_more)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    { Text(stringResource(if (row.pinned) Res.string.servers_unpin else Res.string.servers_pin)) },
                    { menu = false; onPin() }, leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                )
                DropdownMenuItem({ Text(stringResource(Res.string.servers_rename_folder)) }, { menu = false; onRename() }, leadingIcon = { Icon(Icons.Outlined.Edit, null) })
                DropdownMenuItem({ Text(stringResource(Res.string.servers_remove_folder)) }, { menu = false; onRemove() }, leadingIcon = { Icon(Icons.Outlined.FolderOff, null) })
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    text: String? = null,
    confirm: String = stringResource(Res.string.action_save),
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (text != null) Text(text, Modifier.padding(bottom = 12.dp))
                OutlinedTextField(name, { if (it.length <= 40) name = it }, singleLine = true, label = { Text(label) })
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}

@Composable
private fun MoveToFolderDialog(current: String, folders: List<String>, onDismiss: () -> Unit, onMove: (String) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    var newFolder by remember { mutableStateOf("") }
    val target = newFolder.trim().ifEmpty { selected }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.servers_move_to_folder)) },
        text = {
            Column {
                (listOf("") + folders).forEach { folder ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                            .clickable { selected = folder; newFolder = "" }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = newFolder.isBlank() && selected == folder, onClick = { selected = folder; newFolder = "" })
                        Text(folder.ifBlank { stringResource(Res.string.servers_no_folder) })
                    }
                }
                OutlinedTextField(
                    newFolder, { if (it.length <= 30) newFolder = it },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.servers_new_folder)) },
                    leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(enabled = target != current, onClick = { onMove(target) }) { Text(stringResource(Res.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
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
    onPin: () -> Unit = {},
    onMoveToFolder: () -> Unit = {},
    dragging: Boolean = false,
    /** Another server is held over this one: dropping it makes a folder. */
    dropTarget: Boolean = false,
    modifier: Modifier = Modifier,
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
    val lift by animateFloatAsState(if (dragging) 1.03f else if (dropTarget) 0.96f else 1f, label = "lift")
    McCard(
        modifier.fillMaxWidth().zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
                shadowElevation = if (dragging) 16.dp.toPx() else 0f
                shape = RoundedCornerShape(20.dp)
                clip = false
            }
            .then(if (dropTarget) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)) else Modifier),
        onClick = onOpen,
    ) {
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
                        if (server.pinned) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.PushPin, stringResource(Res.string.servers_pinned), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        }
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
                    val demo = DemoServer.isDemo(server)
                    Text(
                        when {
                            demo -> stringResource(Res.string.demo_name)
                            !server.tls -> server.address + " · " + stringResource(Res.string.servers_unencrypted)
                            else -> server.address
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!server.tls && !demo) McColors.Warning else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(Res.string.action_more)) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            { Text(stringResource(if (server.pinned) Res.string.servers_unpin else Res.string.servers_pin)) },
                            { menu = false; onPin() }, leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                        )
                        DropdownMenuItem(
                            { Text(stringResource(Res.string.servers_move_to_folder)) },
                            { menu = false; onMoveToFolder() }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) },
                        )
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
