package de.remotemc.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import de.remotemc.app.data.CardGrid
import de.remotemc.app.data.CardPart
import de.remotemc.app.data.ListLayout
import de.remotemc.app.data.ScreenBlock
import sh.calvin.reorderable.rememberReorderableLazyListState
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
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
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
    /** Pairs a server again (new token or certificate), keeping its name, folder and place. */
    onRepair: (String) -> Unit = {},
    startEditing: Boolean = false, // screenshot tests
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
    var refreshAll by remember { mutableIntStateOf(0) } // checks every server again (also in closed folders)
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

    // Arranging: in edit mode every card has a blue frame whose handles change its size in grid
    // cells; the panel at the bottom orders the parts of a card and the blocks of this screen.
    val settings by model.settings.settings.collectAsState()
    val layout = remember(settings.serverList) { ListLayout.decode(settings.serverList) }
    var editing by remember { mutableStateOf(startEditing) }
    var preview by remember { mutableStateOf<Triple<String, Int, Int>?>(null) }
    var gridWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val columns = with(density) { ((gridWidth - 56.dp.toPx() + 14.dp.toPx()) / (CellWidth + 14.dp).toPx()).toInt() }.coerceIn(4, 12)
    val cellW = with(density) { (gridWidth - 56.dp.toPx() + 14.dp.toPx()) / columns }
    val cellH = with(density) { (RowUnit + 14.dp).toPx() }
    fun sizeOf(server: ServerProfile): Pair<Int, Int> =
        preview?.takeIf { it.first == server.id }?.let { it.second to it.third } ?: CardGrid.size(server, columns, DefaultWidth)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!editing) {
                ExtendedFloatingActionButton(
                    onClick = { onAdd(null) },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text(stringResource(Res.string.servers_add)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                GridCells.Fixed(columns),
                Modifier.fillMaxSize().widthIn(max = 1200.dp).onSizeChanged { gridWidth = it.width },
                state = gridState,
                contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 28.dp, bottom = if (editing) 300.dp else 96.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                layout.blocks.forEach { block ->
                    when (block) {
                        ScreenBlock.HEADER -> {
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
                                    if (servers.isNotEmpty()) {
                                        IconButton(onClick = { ProbeCache.clear(); refreshAll++ }) { Icon(Icons.Outlined.Refresh, stringResource(Res.string.action_refresh)) }
                                    }
                                    if (servers.isNotEmpty()) {
                                        IconButton(onClick = { editing = !editing }) {
                                            Icon(Icons.Outlined.DashboardCustomize, stringResource(Res.string.servers_arrange))
                                        }
                                    }
                                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(Res.string.settings_title)) }
                                }
                            }
                        }
                        ScreenBlock.SEARCH -> {
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
                        }
                        ScreenBlock.SERVERS -> {
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
                                    is ListRow.Server -> item(key = row.key, span = { GridItemSpan(sizeOf(row.server).first.coerceAtMost(maxLineSpan)) }) {
                                        val server = row.server
                                        val (w, h) = sizeOf(server)
                                        ReorderableItem(
                                            reorder, key = row.key, enabled = canSwap(row.key),
                                            modifier = Modifier.onGloballyPositioned { c ->
                                                val r = c.boundsInRoot()
                                                if (slots[server.id] != r) slots[server.id] = r
                                            },
                                        ) { dragging ->
                                            Box(Modifier.staggeredAppear(index).height(RowUnit * h + 14.dp * (h - 1))) {
                                                ServerCard(
                                                    server, probe, { if (!editing) onOpen(server.id) }, { renaming = server }, { deleting = server },
                                                    w = w, h = h, parts = layout,
                                                    alertsOn = alertsOn, onToggleNotify = { model.servers.upsert(server.copy(notify = !server.notify)) },
                                                    onPin = { model.servers.setPinned(server.id, !server.pinned) },
                                                    onMoveToFolder = { moving = server },
                                                    onRepair = { onRepair(server.id) },
                                                    refreshAll = refreshAll,
                                                    dragging = dragging,
                                                    dropTarget = hoverTarget == server.id,
                                                    modifier = Modifier
                                                        .fillMaxSize()
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
                                                if (editing && !dragging) {
                                                    ResizeFrame(
                                                        w = w, h = h, cellW = cellW, cellH = cellH, columns = columns,
                                                        onPreview = { nw, nh -> preview = Triple(server.id, nw, nh) },
                                                        onDone = {
                                                            preview?.let { (_, nw, nh) -> model.servers.setCardSize(server.id, nw, nh) }
                                                            preview = null
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
                }
            }
            AnimatedVisibility(
                visible = editing,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                ListEditPanel(
                    layout = layout,
                    onLayout = { l -> model.settings.update { it.copy(serverList = l.encode()) } },
                    onReset = {
                        model.settings.update { it.copy(serverList = "") }
                        model.servers.resetCardSizes()
                    },
                    onDone = { editing = false },
                )
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
    /** Size in grid cells and what the card shows. */
    w: Int,
    h: Int,
    parts: ListLayout,
    alertsOn: Boolean = false,
    onToggleNotify: () -> Unit = {},
    onPin: () -> Unit = {},
    onMoveToFolder: () -> Unit = {},
    onRepair: () -> Unit = {},
    refreshAll: Int = 0,
    dragging: Boolean = false,
    /** Another server is held over this one: dropping it makes a folder. */
    dropTarget: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val state by produceState(ProbeCache[server.id] ?: Probe.Loading, server.id, server.baseUrl, server.token, refresh, refreshAll) {
        if (refresh > 0 || refreshAll > 0) value = Probe.Loading
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
        val conn = when (state) {
            is Probe.Online -> Conn.LIVE
            Probe.Offline -> Conn.OFFLINE
            Probe.Loading -> Conn.CONNECTING
        }
        val online = (state as? Probe.Online)?.status
        if (w == 1 && h == 1) {
            // The smallest card: first letter and a dot for the status.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ServerIcon(server, 40.dp, conn) }
            return@McCard
        }
        val lines = parts.lines(w, h)
        val showsStatus = lines.any { CardPart.STATUS in it }
        Column(
            Modifier.fillMaxSize().clipToBounds().padding(horizontal = 16.dp, vertical = if (h == 1) 6.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, if (h == 1) Alignment.CenterVertically else Alignment.Top),
        ) {
            lines.forEach { line ->
                when (line.first()) {
                    CardPart.NAME -> Row(verticalAlignment = Alignment.CenterVertically) {
                        ServerIcon(server, if (h == 1 || w == 1) 36.dp else 44.dp, conn.takeUnless { showsStatus })
                        if (w > 1) {
                            Spacer(Modifier.width(12.dp))
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
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
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Box {
                            IconButton(onClick = { menu = true }, Modifier.size(36.dp)) { Icon(Icons.Default.MoreVert, stringResource(Res.string.action_more)) }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    { Text(stringResource(if (server.pinned) Res.string.servers_unpin else Res.string.servers_pin)) },
                                    { menu = false; onPin() }, leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                                )
                                DropdownMenuItem(
                                    { Text(stringResource(Res.string.servers_move_to_folder)) },
                                    { menu = false; onMoveToFolder() }, leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) },
                                )
                                DropdownMenuItem(
                                    { Text(stringResource(Res.string.action_pair_again)) },
                                    { menu = false; onRepair() }, leadingIcon = { Icon(Icons.Outlined.QrCode2, null) },
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
                    CardPart.ADDRESS -> {
                        val demo = DemoServer.isDemo(server)
                        Text(
                            when {
                                demo -> stringResource(Res.string.demo_name)
                                !server.tls -> server.address + " · " + stringResource(Res.string.servers_unencrypted)
                                else -> server.address
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!server.tls && !demo) McColors.Warning else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    CardPart.MEMORY -> if (online != null && online.memoryMaxMb > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(Res.string.dashboard_memory) + " · ${online.memoryUsedMb}/${online.memoryMaxMb} MB",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            LinearProgressIndicator(
                                progress = { (online.memoryUsedMb.toFloat() / online.memoryMaxMb).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    CardPart.VERSION -> if (online != null) {
                        Text(
                            listOf(online.platform, online.minecraftVersion).filter { it.isNotBlank() }.joinToString(" "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    CardPart.MOTD -> if (online != null && online.motd.isNotBlank()) {
                        Text(
                            online.motd.replace(Regex("§."), "").lines().joinToString(" ") { it.trim() },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        line.forEach { part ->
                            when (part) {
                                CardPart.STATUS -> Surface(onClick = { refresh++ }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                    Row(Modifier.padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        StatusDot(conn, 8.dp)
                                        Text(
                                            stringResource(
                                                when (state) {
                                                    is Probe.Online -> Res.string.status_online_short
                                                    Probe.Offline -> Res.string.status_offline_short
                                                    Probe.Loading -> Res.string.status_checking
                                                },
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                CardPart.PLAYERS -> if (online != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Group, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(4.dp))
                                        Text("${online.onlinePlayers}/${online.maxPlayers}", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                    }
                                }
                                CardPart.TPS -> online?.tps?.firstOrNull()?.let { tps ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Speed, null, Modifier.size(16.dp), tint = tpsColor(tps))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(Res.string.unit_tps, "%.1f".format(tps)), style = MaterialTheme.typography.labelLarge, color = tpsColor(tps), maxLines = 1)
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Columns of the server list grid are at least this wide. */
private val CellWidth = 96.dp

/** Height of one grid row. */
private val RowUnit = 64.dp

/** New cards are this many columns wide. */
private const val DefaultWidth = 3

/** First letter of the server; with a status dot when the card has no status line. */
@Composable
private fun ServerIcon(server: ServerProfile, size: Dp, conn: Conn?) {
    Box {
        Box(
            Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) { Text(server.name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer) }
        if (conn != null) {
            Box(Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surface, CircleShape).padding(2.dp)) { StatusDot(conn, 8.dp) }
        }
    }
}

/**
 * The blue frame around a card with handles on the right, at the bottom and in the corner, like
 * resizing a widget on a phone's home screen. Dragging a handle changes the size in whole grid cells.
 */
@Composable
private fun BoxScope.ResizeFrame(w: Int, h: Int, cellW: Float, cellH: Float, columns: Int, onPreview: (Int, Int) -> Unit, onDone: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.matchParentSize().border(2.dp, primary, RoundedCornerShape(20.dp)))
    val size by rememberUpdatedState(w to h)
    val label = stringResource(Res.string.servers_resize)
    @Composable
    fun Handle(align: Alignment, horizontal: Boolean, vertical: Boolean, cursor: Int) {
        Box(
            Modifier.align(align).size(32.dp)
                .semantics { contentDescription = label }
                .pointerHoverIcon(PointerIcon(java.awt.Cursor(cursor)))
                .pointerInput(horizontal, vertical, cellW, cellH, columns) {
                    var start = size
                    var drag = Offset.Zero
                    detectDragGestures(
                        onDragStart = { start = size; drag = Offset.Zero },
                        onDragEnd = onDone,
                        onDragCancel = onDone,
                    ) { change, amount ->
                        change.consume()
                        drag += amount
                        val (nw, nh) = CardGrid.resize(
                            start.first, start.second,
                            if (horizontal) drag.x else 0f, if (vertical) drag.y else 0f,
                            cellW, cellH, columns,
                        )
                        onPreview(nw, nh)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(14.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(3.dp, primary, CircleShape))
        }
    }
    Handle(Alignment.CenterEnd, horizontal = true, vertical = false, cursor = java.awt.Cursor.E_RESIZE_CURSOR)
    Handle(Alignment.BottomCenter, horizontal = false, vertical = true, cursor = java.awt.Cursor.S_RESIZE_CURSOR)
    Handle(Alignment.BottomEnd, horizontal = true, vertical = true, cursor = java.awt.Cursor.SE_RESIZE_CURSOR)
}

/** Bottom panel in edit mode: what a card shows and the screen's blocks, both in a draggable order. */
@Composable
private fun ListEditPanel(layout: ListLayout, onLayout: (ListLayout) -> Unit, onReset: () -> Unit, onDone: () -> Unit) {
    Surface(
        Modifier.padding(16.dp).widthIn(max = 640.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(Res.string.servers_arrange), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp))
            Text(
                stringResource(Res.string.servers_arrange_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            ChipOrder(stringResource(Res.string.servers_arrange_card), layout.parts.map { partLabel(it) }) { from, to -> onLayout(layout.movePart(from, to)) }
            ChipOrder(stringResource(Res.string.servers_arrange_screen), layout.blocks.map { blockLabel(it) }) { from, to -> onLayout(layout.moveBlock(from, to)) }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.dashboard_edit_reset)) }
                Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.dashboard_edit_done)) }
            }
        }
    }
}

/** A row of chips that can be put in another order by dragging them. */
@Composable
private fun ChipOrder(title: String, labels: List<String>, onMove: (Int, Int) -> Unit) {
    val state = rememberLazyListState()
    val current by rememberUpdatedState(labels)
    val reorder = rememberReorderableLazyListState(state) { from, to ->
        if (from.index in current.indices && to.index in current.indices) onMove(from.index, to.index)
    }
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp),
    )
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
        items(labels, key = { it }) { label ->
            ReorderableItem(reorder, key = label) { dragging ->
                Surface(
                    Modifier.draggableHandle().pointerHoverIcon(PointerIcon.Hand),
                    shape = CircleShape,
                    color = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (dragging) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    shadowElevation = if (dragging) 6.dp else 0.dp,
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.DragIndicator, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun partLabel(part: CardPart) = stringResource(
    when (part) {
        CardPart.NAME -> Res.string.part_name
        CardPart.ADDRESS -> Res.string.part_address
        CardPart.STATUS -> Res.string.part_status
        CardPart.PLAYERS -> Res.string.part_players
        CardPart.TPS -> Res.string.part_tps
        CardPart.MEMORY -> Res.string.dashboard_memory
        CardPart.VERSION -> Res.string.part_version
        CardPart.MOTD -> Res.string.part_motd
    },
)

@Composable
private fun blockLabel(block: ScreenBlock) = stringResource(
    when (block) {
        ScreenBlock.HEADER -> Res.string.block_header
        ScreenBlock.SEARCH -> Res.string.block_search
        ScreenBlock.SERVERS -> Res.string.block_servers
    },
)
