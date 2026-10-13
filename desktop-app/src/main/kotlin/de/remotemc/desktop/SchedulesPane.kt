package de.remotemc.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.ScheduleInfo
import de.remotemc.desktop.res.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** "in 2 h 5 min" for a time [ms] after [now]. */
fun relative(ms: Long, now: Long): String {
    val minutes = ((ms - now).coerceAtLeast(0) + 59_999) / 60_000
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 48 * 60 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "${minutes / (24 * 60)} d"
    }
}

/** Commands the server runs on a timer or daily (admins). */
@Composable
fun SchedulesPane(session: ServerSession) {
    val manage = session.manage
    val (loader, reload) = rememberLoader { manage.schedules() }
    var editing by remember { mutableStateOf<ScheduleInfo?>(null) }
    var deleting by remember { mutableStateOf<ScheduleInfo?>(null) }
    val list = loader.data

    ListColumn {
        if (list == null) {
            item { LoadingOrError(loader.error, reload) }
            return@ListColumn
        }
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.schedules_title), style = MaterialTheme.typography.titleLarge)
                    list.timezone.takeIf { it.isNotBlank() }?.let {
                        Text(stringResource(Res.string.schedules_timezone, it), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (loader.busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Button(onClick = { editing = ScheduleInfo(null, "", "", daily = false, minutes = 30, time = "04:00", warn = 0, enabled = true) },
                    enabled = !loader.busy) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.schedules_add))
                }
            }
        }
        loader.error?.let { e -> item(key = "error") { ErrorLine(e) } }
        if (list.schedules.isEmpty()) item(key = "empty") { EmptyLine(stringResource(Res.string.schedules_empty)) }
        items(list.schedules, key = { it.id ?: it.name }) { task ->
            McCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.name, style = MaterialTheme.typography.titleSmall)
                        Text("/" + task.command, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val timing = if (task.daily) {
                            stringResource(Res.string.schedules_summary_daily, task.time)
                        } else {
                            stringResource(Res.string.schedules_summary_interval, task.minutes)
                        }
                        val next = if (task.enabled && task.nextRun > 0) " · " + stringResource(Res.string.schedules_next, relative(task.nextRun, list.serverNow)) else ""
                        Text(timing + next, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(enabled = !loader.busy, onClick = {
                        loader.run({ session.notify(getString(Res.string.schedules_ran, task.name)) }) { manage.scheduleAction(task.id!!, "run") }
                    }) { Icon(Icons.Outlined.PlayArrow, stringResource(Res.string.schedules_run_now)) }
                    IconButton(onClick = { editing = task }) { Icon(Icons.Outlined.Edit, stringResource(Res.string.schedules_edit)) }
                    IconButton(onClick = { deleting = task }) { Icon(Icons.Outlined.Delete, stringResource(Res.string.action_remove)) }
                    Spacer(Modifier.width(4.dp))
                    Switch(task.enabled, onCheckedChange = { on -> loader.run { manage.saveSchedule(task.copy(enabled = on)) } }, enabled = !loader.busy)
                }
            }
        }
    }

    editing?.let { task ->
        ScheduleEditor(task, onDismiss = { editing = null }) { saved ->
            editing = null
            loader.run { manage.saveSchedule(saved) }
        }
    }
    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(stringResource(Res.string.schedules_delete_confirm, task.name)) },
            confirmButton = {
                TextButton(onClick = { deleting = null; loader.run { manage.scheduleAction(task.id!!, "delete") } }) {
                    Text(stringResource(Res.string.action_remove))
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** "HH:mm" if [text] is a valid 24-hour time, otherwise null. */
fun parseTime(text: String): String? {
    val m = Regex("""(\d{1,2}):(\d{2})""").matchEntire(text.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].toInt()
    return if (h in 0..23 && min in 0..59) "%02d:%02d".format(h, min) else null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleEditor(task: ScheduleInfo, onDismiss: () -> Unit, onSave: (ScheduleInfo) -> Unit) {
    var name by remember { mutableStateOf(task.name) }
    var command by remember { mutableStateOf(task.command) }
    var daily by remember { mutableStateOf(task.daily) }
    var minutes by remember { mutableStateOf(task.minutes.toString()) }
    var time by remember { mutableStateOf(task.time) }
    var warn by remember { mutableStateOf(task.warn) }
    val presets = listOf(Res.string.schedules_preset_announce to "say ", Res.string.schedules_preset_save to "save-all")
    val presetNames = presets.map { stringResource(it.first) }
    val valid = name.isNotBlank() && command.isNotBlank() &&
        (if (daily) parseTime(time) != null else (minutes.toIntOrNull() ?: 0) in 1..10080)
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (task.id == null) Res.string.schedules_add else Res.string.schedules_edit)) },
        text = {
            Column(Modifier.widthIn(min = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (task.id == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEachIndexed { i, (_, cmd) ->
                            FilterChip(
                                selected = command == cmd && name == presetNames[i],
                                onClick = { name = presetNames[i]; command = cmd },
                                label = { Text(presetNames[i]) },
                                colors = chipColors,
                            )
                        }
                    }
                }
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(Res.string.schedules_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    command, { command = it.removePrefix("/").take(256) },
                    label = { Text(stringResource(Res.string.schedules_command)) },
                    prefix = { Text("/") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to Res.string.schedules_interval, true to Res.string.schedules_daily).forEachIndexed { i, (d, label) ->
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
                    OutlinedTextField(
                        time, { v -> time = v.filter { it.isDigit() || it == ':' }.take(5) },
                        label = { Text(stringResource(Res.string.world_time)) },
                        placeholder = { Text("04:00") },
                        isError = parseTime(time) == null,
                        singleLine = true,
                    )
                } else {
                    OutlinedTextField(
                        minutes, { v -> minutes = v.filter { it.isDigit() }.take(5) },
                        label = { Text(stringResource(Res.string.schedules_minutes)) },
                        singleLine = true,
                    )
                }
                Text(stringResource(Res.string.schedules_warn), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 1, 5, 10).forEach { w ->
                        FilterChip(
                            selected = warn == w, onClick = { warn = w },
                            label = { Text(if (w == 0) stringResource(Res.string.schedules_warn_none) else "$w min") },
                            colors = chipColors,
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
                        time = parseTime(time) ?: task.time, warn = warn,
                    ),
                )
            }) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    )
}
