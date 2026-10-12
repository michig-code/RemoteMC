package de.remotemc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import de.remotemc.app.data.ScheduleInfo
import de.remotemc.app.data.ScheduleList
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.util.errorText
import de.remotemc.app.ui.util.rememberHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Card on the overview that opens the scheduled tasks. */
@Composable
internal fun SchedulesRow(onClick: () -> Unit) {
    McCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.schedules_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "in 2 h 5 min" for a time [ms] after [now]. */
internal fun relative(ms: Long, now: Long): String {
    val minutes = ((ms - now).coerceAtLeast(0) + 59_999) / 60_000
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 48 * 60 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "${minutes / (24 * 60)} d"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SchedulesSheet(actions: ScheduleActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<ScheduleList?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScheduleInfo?>(null) }
    var deleting by remember { mutableStateOf<ScheduleInfo?>(null) }

    fun run(done: String? = null, call: suspend () -> ScheduleList) {
        scope.launch {
            busy = true
            try {
                list = call()
                error = null
                info = done
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = context.errorText(e)
                haptics.error()
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(Unit) { run { actions.load() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "title") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.schedules_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                list?.timezone?.takeIf { it.isNotBlank() }?.let {
                    Text(stringResource(R.string.schedules_timezone, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error?.let { item(key = "error") { Text(it, color = MaterialTheme.colorScheme.error) } }
            info?.let { item(key = "info") { Text(it, color = MaterialTheme.colorScheme.primary) } }
            val l = list
            if (l == null) {
                if (error == null) {
                    item(key = "loading") { Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
                return@LazyColumn
            }
            if (l.schedules.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.schedules_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
            items(l.schedules, key = { it.id ?: it.name }) { task ->
                ScheduleCard(
                    task, l.serverNow, enabled = !busy,
                    onToggle = { on -> haptics.tap(); run { actions.save(task.copy(enabled = on)) } },
                    onRun = { haptics.success(); run(context.getString(R.string.schedules_ran, task.name)) { actions.run(task.id!!) } },
                    onEdit = { editing = task },
                    onDelete = { deleting = task },
                )
            }
            item(key = "add") {
                Button(onClick = { editing = ScheduleInfo(null, "", "", daily = false, minutes = 30, time = "04:00", warn = 0, enabled = true) }, enabled = !busy) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.schedules_add))
                }
            }
        }
    }

    editing?.let { task ->
        ScheduleEditor(task, onDismiss = { editing = null }) { saved ->
            editing = null
            run { actions.save(saved) }
        }
    }
    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(stringResource(R.string.schedules_delete_confirm, task.name)) },
            confirmButton = {
                TextButton(onClick = { deleting = null; run { actions.delete(task.id!!) } }) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ScheduleCard(
    task: ScheduleInfo,
    serverNow: Long,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    McCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "/" + task.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val timing = if (task.daily) stringResource(R.string.schedules_summary_daily, task.time) else stringResource(R.string.schedules_summary_interval, task.minutes)
                val next = if (task.enabled && task.nextRun > 0) " · " + stringResource(R.string.schedules_next, relative(task.nextRun, serverNow)) else ""
                Text(timing + next, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(task.enabled, onCheckedChange = onToggle, enabled = enabled)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.action_more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.schedules_run_now)) },
                        leadingIcon = { Icon(Icons.Outlined.PlayArrow, null) },
                        onClick = { menu = false; onRun() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.schedules_edit)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_remove)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditor(task: ScheduleInfo, onDismiss: () -> Unit, onSave: (ScheduleInfo) -> Unit) {
    var name by remember { mutableStateOf(task.name) }
    var command by remember { mutableStateOf(task.command) }
    var daily by remember { mutableStateOf(task.daily) }
    var minutes by remember { mutableStateOf(task.minutes.toString()) }
    var warn by remember { mutableStateOf(task.warn) }
    val (h, m) = task.time.split(":").map { it.toIntOrNull() ?: 0 }.let { (it.getOrElse(0) { 4 }) to (it.getOrElse(1) { 0 }) }
    val time = rememberTimePickerState(h, m, is24Hour = true)
    val presets = listOf(
        Triple(R.string.schedules_preset_announce, "say ", false),
        Triple(R.string.schedules_preset_save, "save-all", false),
    )
    val presetNames = presets.map { stringResource(it.first) }
    val valid = name.isNotBlank() && command.isNotBlank() && (daily || (minutes.toIntOrNull() ?: 0) in 1..10080)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (task.id == null) R.string.schedules_add else R.string.schedules_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (task.id == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEachIndexed { i, (_, cmd, _) ->
                            FilterChip(
                                selected = command == cmd && name == presetNames[i],
                                onClick = { name = presetNames[i]; command = cmd },
                                label = { Text(presetNames[i]) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                            )
                        }
                    }
                }
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.schedules_name)) }, singleLine = true)
                OutlinedTextField(
                    command, { command = it.removePrefix("/").take(256) },
                    label = { Text(stringResource(R.string.schedules_command)) },
                    prefix = { Text("/") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to R.string.schedules_interval, true to R.string.schedules_daily).forEachIndexed { i, (d, label) ->
                        SegmentedButton(
                            selected = daily == d, onClick = { daily = d },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            icon = {},
                        ) { Text(stringResource(label)) }
                    }
                }
                if (daily) {
                    TimeInput(time)
                } else {
                    OutlinedTextField(
                        minutes, { v -> minutes = v.filter { it.isDigit() }.take(5) },
                        label = { Text(stringResource(R.string.schedules_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Text(stringResource(R.string.schedules_warn), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 5, 10).forEach { w ->
                        FilterChip(
                            selected = warn == w, onClick = { warn = w },
                            label = { Text(if (w == 0) stringResource(R.string.schedules_warn_none) else "$w min") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    task.copy(
                        name = name.trim(), command = command.trim(), daily = daily,
                        minutes = minutes.toIntOrNull() ?: task.minutes,
                        time = "%02d:%02d".format(time.hour, time.minute), warn = warn,
                    ),
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
