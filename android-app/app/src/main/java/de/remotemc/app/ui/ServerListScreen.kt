package de.remotemc.app.ui

import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.outlined.DashboardCustomize
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.remotemc.app.data.CardGrid
import de.remotemc.app.data.CardPart
import de.remotemc.app.data.ListLayout
import de.remotemc.app.data.ScreenBlock
import sh.calvin.reorderable.rememberReorderableLazyGridState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import de.remotemc.app.R
import de.remotemc.app.data.ListRow
import de.remotemc.app.data.ListTitle
import de.remotemc.app.data.Role
import de.remotemc.app.data.buildRows
import de.remotemc.app.data.drop
import de.remotemc.app.data.folderOf
import de.remotemc.app.data.mergeInto
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.net.ApiClient
import de.remotemc.app.net.DemoServer
import de.remotemc.app.net.Http
import de.remotemc.app.ui.components.AppLogo
import de.remotemc.app.ui.components.Badge
import de.remotemc.app.ui.components.ConnectionState
import de.remotemc.app.ui.components.EmptyState
import de.remotemc.app.ui.components.LocalAnimations
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.components.SectionTitle
import de.remotemc.app.ui.components.StatusPill
import de.remotemc.app.ui.components.motion
import de.remotemc.app.ui.components.staggeredAppear
import de.remotemc.app.ui.components.tpsColor
import de.remotemc.app.ui.theme.McColors
import de.remotemc.app.ui.theme.statusTextColor
import de.remotemc.app.ui.util.rememberHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    onPinFolder: (String, Boolean) -> Unit = { _, _ -> },
    onReorder: (List<ServerProfile>) -> Unit = {},
    onToggleNotify: (ServerProfile) -> Unit = {},
    alertsOn: Boolean = false,
    probe: suspend (ServerProfile) -> ServerStatus = { ApiClient(it, Http.quick).status() },
    initialQuery: String = "",
    /** Adds and opens the built-in demo server (shown while the list is empty). */
    onDemo: (() -> Unit)? = null,
    /** Card parts and screen blocks as arranged by the user. */
    layout: ListLayout = ListLayout(),
    onLayout: (ListLayout) -> Unit = {},
    /** New card size in grid cells. */
    onResize: (ServerProfile, Int, Int) -> Unit = { _, _, _ -> },
    /** All cards back to their default size ("Reset" in edit mode). */
    onResetSizes: () -> Unit = {},
    startEditing: Boolean = false, // screenshot tests
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

    // Drop a server onto another one to put both into a folder: the card under the middle of the
    // dragged one is the target while it is over its middle part (above or below that, they swap).
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var hoverTarget by remember { mutableStateOf<String?>(null) }
    var dragStart by remember { mutableStateOf<List<ServerProfile>?>(null) }
    var merging by remember { mutableStateOf<Pair<String, String>?>(null) } // dragged to target
    val currentServers by rememberUpdatedState(servers)
    var held by remember { mutableStateOf<Pair<String, Rect>?>(null) } // the held server and where it is now
    val shownIds = rows.mapNotNullTo(HashSet()) { (it as? ListRow.Server)?.server?.id }
    fun targetUnder(dragged: Rect, self: String): String? {
        val c = dragged.center
        return bounds.entries.firstOrNull { (id, b) -> id != self && id in shownIds && b.middle(c) }?.key
    }
    LaunchedEffect(hoverTarget) { if (hoverTarget != null) haptics.tick() }
    /** Another server only swaps with the held one once that is past its middle part. */
    fun canSwap(id: String, rowIndex: Int): Boolean {
        val (heldId, rect) = held ?: return true
        val b = bounds[id] ?: return true
        if (id == heldId) return true
        val c = rect.center
        val inBand = c.y in (b.top + b.height * 0.3f)..(b.bottom - b.height * 0.3f)
        return if (rowIndex < rows.indexOfFirst { it.key == heldId }) {
            c.y < b.top + b.height * 0.3f || (inBand && c.x < b.left + b.width * 0.25f)
        } else {
            c.y > b.bottom - b.height * 0.3f || (inBand && c.x > b.right - b.width * 0.25f)
        }
    }

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

    val gridState = rememberLazyGridState()
    val reorder = rememberReorderableLazyGridState(gridState) { from, to ->
        val id = from.key as? String ?: return@rememberReorderableLazyGridState
        val result = drop(servers, rows, id, to.key, collapsed) ?: return@rememberReorderableLazyGridState
        result.expand?.let { collapsed = collapsed - it }
        onReorder(result.servers)
        haptics.tick()
    }

    // Arranging: in edit mode every card has the blue frame; otherwise holding a card without
    // moving it selects just that one. The frame's handles change its size in grid cells.
    var editing by rememberSaveable { mutableStateOf(startEditing) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<Triple<String, Int, Int>?>(null) } // size while a handle is dragged
    var dragOrigin by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var gridWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val cellW = with(density) { (gridWidth - (32 - 12).dp.toPx()) / GridColumns }
    val cellH = with(density) { (RowUnit + 12.dp).toPx() }
    fun sizeOf(server: ServerProfile): Pair<Int, Int> =
        preview?.takeIf { it.first == server.id }?.let { it.second to it.third } ?: CardGrid.size(server, GridColumns)
    if (editing || selected != null) BackHandler { editing = false; selected = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!editing) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text(stringResource(R.string.servers_add)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            refreshing, refresh,
            // A tap next to the cards ends the selection.
            Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { selected = null } },
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyVerticalGrid(
                GridCells.Fixed(GridColumns),
                Modifier.fillMaxSize().widthIn(max = 720.dp).onSizeChanged { gridWidth = it.width },
                state = gridState,
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 24.dp,
                    bottom = padding.calculateBottomPadding() + if (editing) 300.dp else 96.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val full: (androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> GridItemSpan) = { GridItemSpan(maxLineSpan) }
                layout.blocks.forEach { block ->
                    when (block) {
                        ScreenBlock.HEADER -> item(key = "header", span = full) {
                            Header(servers.size, onSettings, onEdit = if (servers.isEmpty()) null else { { haptics.heavy(); selected = null; editing = true } })
                        }
                        ScreenBlock.SEARCH -> if (servers.size > 1) {
                            item(key = "search", span = full) { SearchField(query) { query = it } }
                        }
                        ScreenBlock.SERVERS -> if (servers.isEmpty()) {
                            item(key = "empty", span = full) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    EmptyState(Icons.Outlined.Dns, stringResource(R.string.servers_empty_title), stringResource(R.string.servers_empty_text))
                                    if (onDemo != null) {
                                        OutlinedButton(onClick = onDemo) { Text(stringResource(R.string.demo_try)) }
                                    }
                                }
                            }
                        } else {
                            if (rows.isEmpty()) {
                                item(key = "no-match", span = full) {
                                    Text(
                                        stringResource(R.string.servers_no_match, query.trim()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            itemsIndexed(
                                rows,
                                key = { _, row -> row.key },
                                span = { _, row -> if (row is ListRow.Server) GridItemSpan(sizeOf(row.server).first.coerceAtMost(maxLineSpan)) else GridItemSpan(maxLineSpan) },
                            ) { i, row ->
                                when (row) {
                                    // Nothing can go above the first title (it would only stall the drag).
                                    is ListRow.Title -> ReorderableItem(reorder, key = row.key, enabled = i > 0) {
                                        SectionTitle(stringResource(if (row.title == ListTitle.PINNED) R.string.servers_pinned else R.string.servers_title))
                                    }
                                    is ListRow.Folder -> ReorderableItem(reorder, key = row.key) { FolderHeader(
                                        row,
                                        onToggle = {
                                            haptics.tap()
                                            collapsed = if (row.expanded) collapsed + row.name else collapsed - row.name
                                        },
                                        onRename = { renamingFolder = row.name },
                                        onRemove = { removingFolder = row.name },
                                        onPin = { haptics.tap(); onPinFolder(row.name, !row.pinned) },
                                    ) }
                                    is ListRow.Server -> ReorderableItem(reorder, key = row.key, enabled = canSwap(row.key, i)) { dragging ->
                                        val server = row.server
                                        val (w, h) = sizeOf(server)
                                        val framed = editing || selected == server.id
                                        Box(Modifier.height(RowUnit * h + 12.dp * (h - 1))) {
                                            ServerCard(
                                                server = server,
                                                w = w,
                                                h = h,
                                                parts = layout,
                                                probe = probe,
                                                refreshTick = refreshTick,
                                                dragging = dragging,
                                                dropTarget = hoverTarget == server.id,
                                                onOpen = {
                                                    when {
                                                        editing -> Unit
                                                        selected != null -> selected = null
                                                        else -> onOpen(server)
                                                    }
                                                },
                                                onRename = { renaming = server },
                                                onPin = { onPin(server, !server.pinned) },
                                                onMoveToFolder = { moving = server },
                                                alertsOn = alertsOn,
                                                onToggleNotify = { onToggleNotify(server) },
                                                onRepair = { onRepair(server) },
                                                onDelete = { deleting = server },
                                                // Hold a server to drag it; not while searching, the list is filtered then.
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .longPressDraggableHandle(
                                                        enabled = query.isBlank(),
                                                        onDragStarted = {
                                                            haptics.heavy()
                                                            dragStart = currentServers
                                                            dragOrigin = bounds[server.id]?.center
                                                        },
                                                        onDragStopped = {
                                                            val target = hoverTarget
                                                            val moved = held?.second?.center?.let { c -> dragOrigin?.let { (c - it).getDistance() } } ?: 0f
                                                            hoverTarget = null
                                                            held = null
                                                            dragOrigin = null
                                                            if (target != null) {
                                                                val folder = folderOf(currentServers, target)
                                                                if (folder != null) {
                                                                    // Onto a server in a folder: it simply joins that folder.
                                                                    onReorder(mergeInto(currentServers, server.id, target, folder))
                                                                    haptics.success()
                                                                } else {
                                                                    merging = server.id to target
                                                                }
                                                            } else if (!editing && moved < with(density) { 24.dp.toPx() }) {
                                                                // Held without moving: show the frame to change its size.
                                                                selected = server.id
                                                            }
                                                        },
                                                    )
                                                    .onGloballyPositioned { c ->
                                                        val r = c.boundsInRoot()
                                                        if (dragging) {
                                                            held = server.id to r
                                                            hoverTarget = targetUnder(r, server.id)
                                                        } else if (bounds[server.id] != r) {
                                                            bounds[server.id] = r
                                                        }
                                                    }
                                                    .staggeredAppear(i + 1),
                                            )
                                            if (framed && !dragging) {
                                                ResizeFrame(
                                                    w = w, h = h, cellW = cellW, cellH = cellH, columns = GridColumns,
                                                    onPreview = { nw, nh ->
                                                        if (preview?.let { it.second to it.third } != nw to nh) haptics.tick()
                                                        preview = Triple(server.id, nw, nh)
                                                    },
                                                    onDone = {
                                                        preview?.let { (_, nw, nh) -> onResize(server, nw, nh) }
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
            AnimatedVisibility(
                visible = editing,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                ListEditPanel(
                    layout = layout,
                    onLayout = onLayout,
                    onReset = { haptics.tap(); onLayout(ListLayout()); onResetSizes() },
                    onDone = { haptics.success(); editing = false },
                )
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

    merging?.let { (id, target) ->
        // A new folder from two servers: name it, or cancel and the server goes back where it was.
        NameDialog(
            title = stringResource(R.string.servers_new_folder),
            text = stringResource(R.string.servers_new_folder_text),
            label = stringResource(R.string.servers_folder_name),
            initial = "",
            confirm = stringResource(R.string.servers_folder_create),
            onDismiss = {
                dragStart?.let(onReorder)
                merging = null
            },
            onSave = { name ->
                onReorder(mergeInto(servers, id, target, name))
                collapsed = collapsed - name
                haptics.success()
                merging = null
            },
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

/** Columns of the server list grid; a card is 1 to 4 columns wide. */
private const val GridColumns = 4

/** Height of one grid row. */
private val RowUnit = 64.dp

/** The middle part of a card: a server dropped there makes a folder with it. */
private fun Rect.middle(p: androidx.compose.ui.geometry.Offset) =
    p.x in (left + width * 0.25f)..(right - width * 0.25f) && p.y in (top + height * 0.3f)..(bottom - height * 0.3f)

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
private fun FolderHeader(
    row: ListRow.Folder,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (row.expanded) 0f else -90f, motion(), label = "chevron")
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
            if (row.pinned) {
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Filled.PushPin, stringResource(R.string.servers_pinned), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.action_more)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (row.pinned) R.string.servers_unpin else R.string.servers_pin)) },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                    onClick = { menu = false; onPin() },
                )
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
private fun NameDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    text: String? = null,
    confirm: String = stringResource(R.string.action_save),
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
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim()) }) { Text(confirm) }
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
private fun Header(count: Int, onSettings: () -> Unit, onEdit: (() -> Unit)? = null) {
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
            // Always one line: with large fonts the name gets smaller instead of breaking ("RemoteM / C").
            OneLineTitle(stringResource(R.string.app_name), MaterialTheme.typography.headlineLarge)
            Text(
                if (count == 0) stringResource(R.string.servers_tagline) else pluralStringResource(R.plurals.servers_count, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.DashboardCustomize, stringResource(R.string.servers_arrange)) }
        }
        IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title)) }
    }
}

@Composable
private fun ServerCard(
    server: ServerProfile,
    /** Size in grid cells. */
    w: Int,
    h: Int,
    parts: ListLayout,
    probe: suspend (ServerProfile) -> ServerStatus,
    dragging: Boolean,
    refreshTick: Int,
    /** Another server is held over this one: dropping it makes a folder. */
    dropTarget: Boolean,
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

    val lift by animateFloatAsState(if (dragging) 1.03f else if (dropTarget) 0.96f else 1f, label = "lift")
    McCard(
        modifier.zIndex(if (dragging) 1f else 0f).graphicsLayer {
            scaleX = lift
            scaleY = lift
            shadowElevation = if (dragging) 16.dp.toPx() else 0f
            shape = RoundedCornerShape(24.dp)
            clip = false
        }.then(if (dropTarget) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp)) else Modifier),
        onClick = { haptics.tap(); onOpen() },
    ) {
        val pill: @Composable () -> Unit = {
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
        }
        val online = (state as? ServerProbe.Online)?.status
        if (w == 1 && h == 1) {
            // The smallest card: first letter and a dot for the status.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ServerIcon(server, 40.dp, connection)
            }
            return@McCard
        }
        val lines = parts.lines(w, h)
        val showsStatus = lines.any { CardPart.STATUS in it }
        Column(
            Modifier.fillMaxSize().clipToBounds().padding(horizontal = 14.dp, vertical = if (h == 1) 6.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, if (h == 1) Alignment.CenterVertically else Alignment.Top),
        ) {
            lines.forEach { line ->
                when (line.first()) {
                    CardPart.NAME -> Row(verticalAlignment = Alignment.CenterVertically) {
                        ServerIcon(server, if (h == 1 || w == 1) 36.dp else 44.dp, connection.takeUnless { showsStatus })
                        if (w > 1) {
                            Spacer(Modifier.width(12.dp))
                            NameLine(server, alertsOn, Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Box {
                            IconButton(onClick = { menu = true }, Modifier.size(36.dp)) { Icon(Icons.Default.MoreVert, stringResource(R.string.action_more)) }
                            ServerMenu(
                                menu, { menu = false }, server, alertsOn,
                                onPin, onToggleNotify, onMoveToFolder, onRename, onRepair, onDelete,
                            )
                        }
                    }
                    CardPart.ADDRESS -> Text(
                        when {
                            DemoServer.isDemo(server) -> stringResource(R.string.demo_name)
                            !server.tls -> server.address + " · " + stringResource(R.string.servers_unencrypted)
                            else -> server.address
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!server.tls && !DemoServer.isDemo(server)) statusTextColor(McColors.Warning) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CardPart.MEMORY -> if (online != null && online.memoryMaxMb > 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                stringResource(R.string.dashboard_memory) + " · ${online.memoryUsedMb}/${online.memoryMaxMb} MB",
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
                        line.forEach { inline ->
                            when (inline) {
                                CardPart.STATUS -> pill()
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
                                        Text(
                                            stringResource(R.string.unit_tps, tps),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = statusTextColor(tpsColor(tps)),
                                            maxLines = 1,
                                        )
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

/** First letter of the server; with a status dot when the card has no status line. */
@Composable
private fun ServerIcon(server: ServerProfile, size: androidx.compose.ui.unit.Dp, status: ConnectionState?) {
    Box {
        Box(
            Modifier.size(size).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                server.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (status != null) {
            val color = when (status) {
                ConnectionState.Online -> McColors.Online
                ConnectionState.Offline -> McColors.Offline
                else -> MaterialTheme.colorScheme.outline
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface).padding(2.dp).clip(CircleShape).background(color),
            )
        }
    }
}

@Composable
private fun NameLine(server: ServerProfile, alertsOn: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            server.name, style = MaterialTheme.typography.titleMedium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
        )
        if (server.pinned) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.PushPin, stringResource(R.string.servers_pinned), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
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
}

@Composable
private fun ServerMenu(
    open: Boolean,
    onClose: () -> Unit,
    server: ServerProfile,
    alertsOn: Boolean,
    onPin: () -> Unit,
    onToggleNotify: () -> Unit,
    onMoveToFolder: () -> Unit,
    onRename: () -> Unit,
    onRepair: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(open, onDismissRequest = onClose) {
        DropdownMenuItem(
            text = { Text(stringResource(if (server.pinned) R.string.servers_unpin else R.string.servers_pin)) },
            leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
            onClick = { onClose(); onPin() },
        )
        if (alertsOn) {
            DropdownMenuItem(
                text = { Text(stringResource(if (server.notify) R.string.servers_notify_off else R.string.servers_notify_on)) },
                leadingIcon = { Icon(if (server.notify) Icons.Outlined.NotificationsOff else Icons.Outlined.Notifications, null) },
                onClick = { onClose(); onToggleNotify() },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.servers_move_to_folder)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
            onClick = { onClose(); onMoveToFolder() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.servers_rename)) },
            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
            onClick = { onClose(); onRename() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_pair_again)) },
            leadingIcon = { Icon(Icons.Outlined.Link, null) },
            onClick = { onClose(); onRepair() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_remove)) },
            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
            onClick = { onClose(); onDelete() },
        )
    }
}

/**
 * The blue frame around a card with handles on the right, at the bottom and in the corner, like
 * resizing a widget on the home screen. Dragging a handle changes the size in whole grid cells.
 */
@Composable
private fun BoxScope.ResizeFrame(
    w: Int,
    h: Int,
    cellW: Float,
    cellH: Float,
    columns: Int,
    onPreview: (Int, Int) -> Unit,
    onDone: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.matchParentSize().border(2.dp, primary, RoundedCornerShape(24.dp)))
    val size by rememberUpdatedState(w to h)
    val label = stringResource(R.string.servers_resize)
    @Composable
    fun Handle(align: Alignment, horizontal: Boolean, vertical: Boolean) {
        Box(
            Modifier.align(align).size(40.dp)
                .semantics { contentDescription = label }
                .pointerInput(horizontal, vertical, cellW, cellH) {
                    var start = size
                    var drag = androidx.compose.ui.geometry.Offset.Zero
                    detectDragGestures(
                        onDragStart = { start = size; drag = androidx.compose.ui.geometry.Offset.Zero },
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
            Box(
                Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, primary, CircleShape),
            )
        }
    }
    Handle(Alignment.CenterEnd, horizontal = true, vertical = false)
    Handle(Alignment.BottomCenter, horizontal = false, vertical = true)
    Handle(Alignment.BottomEnd, horizontal = true, vertical = true)
}

/** Bottom panel in edit mode: what a card shows and the screen's blocks, both in a draggable order. */
@Composable
private fun ListEditPanel(layout: ListLayout, onLayout: (ListLayout) -> Unit, onReset: () -> Unit, onDone: () -> Unit) {
    val haptics = rememberHaptics()
    Surface(
        Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.servers_arrange), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp))
            Text(
                stringResource(R.string.servers_arrange_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            ChipOrder(
                stringResource(R.string.servers_arrange_card),
                layout.parts.map { partLabel(it) },
            ) { from, to -> haptics.tick(); onLayout(layout.movePart(from, to)) }
            ChipOrder(
                stringResource(R.string.servers_arrange_screen),
                layout.blocks.map { blockLabel(it) },
            ) { from, to -> haptics.tick(); onLayout(layout.moveBlock(from, to)) }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dashboard_edit_reset)) }
                Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.dashboard_edit_done)) }
            }
        }
    }
}

/** A row of chips that can be put in another order by holding and dragging them. */
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
        itemsIndexed(labels, key = { _, label -> label }) { _, label ->
            ReorderableItem(reorder, key = label) { dragging ->
                Surface(
                    Modifier.longPressDraggableHandle(),
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
        CardPart.NAME -> R.string.part_name
        CardPart.ADDRESS -> R.string.part_address
        CardPart.STATUS -> R.string.part_status
        CardPart.PLAYERS -> R.string.part_players
        CardPart.TPS -> R.string.part_tps
        CardPart.MEMORY -> R.string.dashboard_memory
        CardPart.VERSION -> R.string.part_version
        CardPart.MOTD -> R.string.part_motd
    },
)

@Composable
private fun blockLabel(block: ScreenBlock) = stringResource(
    when (block) {
        ScreenBlock.HEADER -> R.string.block_header
        ScreenBlock.SEARCH -> R.string.block_search
        ScreenBlock.SERVERS -> R.string.block_servers
    },
)

@Composable
fun roleLabel(role: Role) = stringResource(
    when (role) {
        Role.ADMIN -> R.string.role_admin
        Role.MODERATOR -> R.string.role_moderator
        Role.VIEWER -> R.string.role_viewer
    },
)

/** Text that stays on one line and shrinks (down to 60 %) when there is not enough room. */
@Composable
private fun OneLineTitle(text: String, style: androidx.compose.ui.text.TextStyle) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    Text(
        text,
        style = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale),
        maxLines = 1,
        softWrap = false,
        onTextLayout = { layout ->
            if (layout.didOverflowWidth && scale > 0.6f) scale = (scale - 0.08f).coerceAtLeast(0.6f)
        },
    )
}

