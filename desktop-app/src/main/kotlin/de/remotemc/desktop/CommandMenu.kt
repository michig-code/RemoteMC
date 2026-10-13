// Ported from the Android app (android-app/.../ui/CommandMenu.kt); keep both in step.
package de.remotemc.desktop

import de.remotemc.desktop.res.Res
import de.remotemc.desktop.res.macro_commands
import de.remotemc.desktop.res.action_cancel
import de.remotemc.desktop.res.action_edit
import de.remotemc.desktop.res.action_remove
import de.remotemc.desktop.res.action_run
import de.remotemc.desktop.res.action_save
import de.remotemc.desktop.res.confirm_before
import de.remotemc.desktop.res.fav_add
import de.remotemc.desktop.res.fav_command
import de.remotemc.desktop.res.fav_empty
import de.remotemc.desktop.res.fav_title
import de.remotemc.desktop.res.fav_widget
import de.remotemc.desktop.res.fav_widget_hint
import de.remotemc.desktop.res.macro_add
import de.remotemc.desktop.res.macro_add_step
import de.remotemc.desktop.res.macro_empty
import de.remotemc.desktop.res.macro_name
import de.remotemc.desktop.res.macro_running
import de.remotemc.desktop.res.macro_stop
import de.remotemc.desktop.res.macro_title
import de.remotemc.desktop.res.macro_wait
import de.remotemc.desktop.res.macro_waiting
import de.remotemc.desktop.res.menu_hint
import de.remotemc.desktop.res.menu_loading
import de.remotemc.desktop.res.menu_none
import de.remotemc.desktop.res.menu_search
import de.remotemc.desktop.res.menu_tab_commands
import de.remotemc.desktop.res.menu_tab_favorites
import de.remotemc.desktop.res.menu_tab_macros
import de.remotemc.desktop.res.placeholder_help
import de.remotemc.desktop.res.placeholder_player
import de.remotemc.desktop.res.placeholder_text
import de.remotemc.desktop.res.placeholder_title
import de.remotemc.desktop.res.run_confirm
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.CommandCatalog
import de.remotemc.app.data.CommandInfo
import de.remotemc.app.data.Favorite
import de.remotemc.app.data.Macro
import de.remotemc.app.data.MacroStep
import de.remotemc.app.data.Placeholders
import de.remotemc.app.data.Shortcuts
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** What the command menu (book next to "send" in the console) shows and does. */
class CommandMenuActions(
    val commands: List<CommandInfo>,
    val shortcuts: Shortcuts,
    val players: List<String>,
    /** Name of the running macro, if any. */
    val runningMacro: String?,
    val onShortcuts: ((Shortcuts) -> Shortcuts) -> Unit,
    val onRunMacro: (Macro, Map<String, String>) -> Unit,
    val onStopMacro: () -> Unit,
)

private enum class MenuTab(val label: StringResource) { Commands(Res.string.menu_tab_commands), Favorites(Res.string.menu_tab_favorites), Macros(Res.string.menu_tab_macros) }

/** Something waiting for placeholders and/or a confirmation before it runs. */
private sealed interface Pending {
    val label: String
    val placeholders: List<String>
    val confirm: Boolean

    data class Command(val favorite: Favorite) : Pending {
        override val label get() = "/" + favorite.command
        override val placeholders get() = Placeholders.find(favorite.command)
        override val confirm get() = favorite.confirm
    }

    data class Steps(val macro: Macro) : Pending {
        override val label get() = macro.name
        override val placeholders get() = macro.placeholders
        override val confirm get() = macro.confirm
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommandMenuSheet(
    menu: CommandMenuActions,
    /** What is typed in the console now; a new favorite starts with it. */
    currentInput: String,
    onInsert: (String) -> Unit,
    onRun: (String) -> Unit,
    onDismiss: () -> Unit,
    /** false shows the content without the bottom sheet (screenshot tests). */
    inSheet: Boolean = true,
    initialTab: Int = 0,
) {
    val haptics = rememberHaptics()
    var tab by rememberSaveable { mutableStateOf(MenuTab.entries[initialTab]) }
    var editFavorite by remember { mutableStateOf<Favorite?>(null) }
    var editMacro by remember { mutableStateOf<Macro?>(null) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var values by remember { mutableStateOf<Map<String, String>?>(null) }

    fun start(p: Pending) {
        haptics.tap()
        values = null
        pending = p
    }

    fun finish(p: Pending, filled: Map<String, String>) {
        pending = null
        haptics.success()
        when (p) {
            is Pending.Command -> onRun(Placeholders.fill(p.favorite.command, filled))
            is Pending.Steps -> menu.onRunMacro(p.macro, filled)
        }
        onDismiss()
    }

    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                MenuTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t }, text = { Text(stringResource(t.label)) })
                }
            }
            menu.runningMacro?.let { name ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.macro_running, name), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { haptics.heavy(); menu.onStopMacro() }) {
                            Icon(Icons.Outlined.Stop, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(Res.string.macro_stop))
                        }
                    }
                }
            }
            when (tab) {
                MenuTab.Commands -> CommandList(
                    commands = menu.commands,
                    onPick = { haptics.tap(); onInsert("${it.name} "); onDismiss() },
                    onSave = { haptics.heavy(); editFavorite = Favorite(command = it.name + " ") },
                )
                MenuTab.Favorites -> ShortcutList(
                    items = menu.shortcuts.favorites,
                    key = { it.id },
                    addLabel = Res.string.fav_add,
                    empty = Res.string.fav_empty,
                    onAdd = { editFavorite = Favorite(command = currentInput.trim().removePrefix("/")) },
                    onMove = { from, to -> menu.onShortcuts { it.moveFavorite(from, to) } },
                    title = { "/" + it.command },
                    subtitle = { null },
                    confirm = { it.confirm },
                    onRun = { start(Pending.Command(it)) },
                    onEdit = { editFavorite = it },
                )
                MenuTab.Macros -> ShortcutList(
                    items = menu.shortcuts.macros,
                    key = { it.id },
                    addLabel = Res.string.macro_add,
                    empty = Res.string.macro_empty,
                    onAdd = { editMacro = Macro(name = "", steps = listOf(MacroStep(""))) },
                    onMove = { from, to -> menu.onShortcuts { it.moveMacro(from, to) } },
                    title = { it.name.ifBlank { "/" + it.steps.first().command } },
                    subtitle = { m ->
                        val count = pluralStringResource(Res.plurals.macro_commands, m.steps.size, m.steps.size)
                        if (m.duration > 0) count + " · " + stringResource(Res.string.macro_waiting, m.duration) else count
                    },
                    monospace = false,
                    confirm = { it.confirm },
                    runEnabled = menu.runningMacro == null,
                    onRun = { start(Pending.Steps(it)) },
                    onEdit = { editMacro = it },
                )
            }
        }
    }

    if (inSheet) {
        // On the PC a dialog in the middle of the window instead of the phone's bottom sheet.
        Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.width(620.dp).height(560.dp)) { content() }
        }
    } else {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { content() }
    }

    editFavorite?.let { f ->
        FavoriteDialog(
            initial = f,
            isNew = menu.shortcuts.favorites.none { it.id == f.id },
            onSave = { saved -> menu.onShortcuts { it.upsert(saved) }; editFavorite = null; tab = MenuTab.Favorites },
            onDelete = { menu.onShortcuts { it.removeFavorite(f.id) }; editFavorite = null },
            onDismiss = { editFavorite = null },
        )
    }
    editMacro?.let { m ->
        MacroDialog(
            initial = m,
            isNew = menu.shortcuts.macros.none { it.id == m.id },
            onSave = { saved -> menu.onShortcuts { it.upsert(saved) }; editMacro = null },
            onDelete = { menu.onShortcuts { it.removeMacro(m.id) }; editMacro = null },
            onDismiss = { editMacro = null },
        )
    }
    pending?.let { p ->
        val filled = values
        when {
            p.placeholders.isNotEmpty() && filled == null ->
                PlaceholderDialog(p.label, p.placeholders, menu.players, onDone = { v ->
                    if (p.confirm) values = v else finish(p, v)
                }, onDismiss = { pending = null })
            p.confirm -> AlertDialog(
                onDismissRequest = { pending = null },
                title = { Text(stringResource(Res.string.run_confirm, p.label)) },
                confirmButton = { TextButton(onClick = { finish(p, filled.orEmpty()) }) { Text(stringResource(Res.string.action_run)) } },
                dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(Res.string.action_cancel)) } },
            )
            // Nothing to ask: run it right away (outside of composition).
            else -> LaunchedEffect(p) { finish(p, filled.orEmpty()) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommandList(commands: List<CommandInfo>, onPick: (CommandInfo) -> Unit, onSave: (CommandInfo) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val groups = remember(commands, query) { CommandCatalog.grouped(commands, query) }
    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "search") {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(Res.string.menu_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
            Text(
                stringResource(if (commands.isEmpty()) Res.string.menu_loading else Res.string.menu_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (commands.isNotEmpty() && groups.isEmpty()) {
            item(key = "none") { Text(stringResource(Res.string.menu_none), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        groups.forEach { (plugin, list) ->
            item(key = "g:$plugin") {
                Text(
                    "$plugin · ${list.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(list, key = { "c:$plugin:${it.name}" }) { c ->
                Column(
                    Modifier.fillMaxWidth()
                        .combinedClickable(onClick = { onPick(c) }, onLongClick = { onSave(c) })
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("/" + c.name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
                    val detail = c.usage ?: c.description
                    if (detail != null) {
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ShortcutList(
    items: List<T>,
    key: (T) -> String,
    addLabel: StringResource,
    empty: StringResource,
    onAdd: () -> Unit,
    onMove: (Int, Int) -> Unit,
    title: (T) -> String,
    subtitle: @Composable (T) -> String?,
    confirm: (T) -> Boolean,
    onRun: (T) -> Unit,
    onEdit: (T) -> Unit,
    runEnabled: Boolean = true,
    monospace: Boolean = true,
) {
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    // The list is shown from a local copy while dragging, saved when an item moves.
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val offset = 1 // the "add" row
        onMove(from.index - offset, to.index - offset)
        haptics.tick()
    }
    LazyColumn(Modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "add") {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
                FilledTonalButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(addLabel))
                }
                if (items.isEmpty()) {
                    Text(stringResource(empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
        shortcutItems(items, key, reorder, title, subtitle, confirm, onRun, onEdit, runEnabled, monospace)
    }
}

private fun <T> LazyListScope.shortcutItems(
    items: List<T>,
    key: (T) -> String,
    reorder: sh.calvin.reorderable.ReorderableLazyListState,
    title: (T) -> String,
    subtitle: @Composable (T) -> String?,
    confirm: (T) -> Boolean,
    onRun: (T) -> Unit,
    onEdit: (T) -> Unit,
    runEnabled: Boolean,
    monospace: Boolean,
) {
    items(items, key = key) { item ->
        ReorderableItem(reorder, key = key(item)) { dragging ->
            Surface(
                color = if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.DragIndicator, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.draggableHandle().padding(12.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title(item), fontFamily = if (monospace) FontFamily.Monospace else null, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false))
                            if (confirm(item)) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Outlined.Shield, stringResource(Res.string.confirm_before), Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        subtitle(item)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { onEdit(item) }) { Icon(Icons.Outlined.Edit, stringResource(Res.string.action_edit)) }
                    IconButton(onClick = { onRun(item) }, enabled = runEnabled) {
                        Icon(Icons.Default.PlayArrow, stringResource(Res.string.action_run), tint = if (runEnabled) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteDialog(initial: Favorite, isNew: Boolean, onSave: (Favorite) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var command by remember { mutableStateOf(initial.command) }
    var confirm by remember { mutableStateOf(initial.confirm) }
    var widget by remember { mutableStateOf(initial.widget) }
    val fits = Favorite(command = command, confirm = confirm).fitsWidget
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.fav_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it.removePrefix("/") },
                    label = { Text(stringResource(Res.string.fav_command)) },
                    prefix = { Text("/") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(Res.string.placeholder_help), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                ConfirmSwitch(confirm) { confirm = it }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.fav_widget))
                        if (!fits) {
                            Text(stringResource(Res.string.fav_widget_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = widget && fits, onCheckedChange = { widget = it }, enabled = fits)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(initial.copy(command = command.trim(), confirm = confirm, widget = widget && fits)) }, enabled = command.isNotBlank()) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text(stringResource(Res.string.action_remove), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}

@Composable
internal fun MacroDialog(initial: Macro, isNew: Boolean, onSave: (Macro) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var confirm by remember { mutableStateOf(initial.confirm) }
    val commands = remember { mutableStateListOf(*initial.steps.map { it.command }.toTypedArray()) }
    val waits = remember { mutableStateListOf(*initial.steps.map { if (it.waitSeconds > 0) it.waitSeconds.toString() else "" }.toTypedArray()) }
    val valid = name.isNotBlank() && commands.any { it.isNotBlank() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.macro_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(Res.string.macro_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                commands.indices.forEach { i ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = if (i == 0) 8.dp else 0.dp)) {
                        if (i > 0) {
                            OutlinedTextField(
                                value = waits[i],
                                onValueChange = { v -> waits[i] = v.filter(Char::isDigit).take(4) },
                                label = { Text(stringResource(Res.string.macro_wait)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(96.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        OutlinedTextField(
                            value = commands[i],
                            onValueChange = { v -> commands[i] = v.removePrefix("/") },
                            label = { Text(stringResource(Res.string.fav_command)) },
                            prefix = { Text("/") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f),
                        )
                        if (commands.size > 1) {
                            IconButton(onClick = { commands.removeAt(i); waits.removeAt(i) }) {
                                Icon(Icons.Outlined.Close, stringResource(Res.string.action_remove))
                            }
                        }
                    }
                }
                TextButton(onClick = { commands.add(""); waits.add("") }, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.macro_add_step))
                }
                Text(stringResource(Res.string.placeholder_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ConfirmSwitch(confirm) { confirm = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val steps = commands.indices
                        .map { MacroStep(commands[it].trim(), (waits[it].toIntOrNull() ?: 0).coerceIn(0, Shortcuts.MAX_WAIT)) }
                        .filter { it.command.isNotEmpty() }
                    onSave(initial.copy(name = name.trim(), steps = steps, confirm = confirm))
                },
            ) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text(stringResource(Res.string.action_remove), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun ConfirmSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.confirm_before), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceholderDialog(
    label: String,
    placeholders: List<String>,
    players: List<String>,
    onDone: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = remember { mutableStateListOf(*Array(placeholders.size) { "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                placeholders.forEachIndexed { i, p ->
                    val player = Placeholders.isPlayer(p)
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { values[i] = if (player) it.trim() else it },
                        label = { Text(stringResource(if (player) Res.string.placeholder_player else Res.string.placeholder_text) + "  $p") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = if (i > 0) 12.dp else 0.dp),
                    )
                    if (player && players.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            players.forEach { name ->
                                FilterChip(selected = values[i] == name, onClick = { values[i] = name }, label = { Text(name) })
                            }
                        }
                    }
                }
                Box(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = values.all { it.isNotBlank() },
                onClick = { onDone(placeholders.indices.associate { placeholders[it] to values[it].trim() }) },
            ) { Text(stringResource(Res.string.placeholder_title)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
