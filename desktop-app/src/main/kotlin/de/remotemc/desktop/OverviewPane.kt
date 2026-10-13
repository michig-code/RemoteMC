package de.remotemc.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.HistoryPoint
import de.remotemc.app.data.HistoryRange
import de.remotemc.app.data.PerformanceHistory
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.data.WorldInfo
import de.remotemc.app.net.ApiError
import de.remotemc.desktop.res.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewPane(session: ServerSession, loadHistory: (suspend (HistoryRange) -> PerformanceHistory)? = { session.client.history(it) }) {
    val status = session.status
    if (status == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val error = session.error
            if (error != null) {
                var text by remember(error) { mutableStateOf("") }
                LaunchedEffect(error) { text = errorText(error) }
                EmptyState(stringResource(Res.string.dashboard_unreachable_title), text)
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (status.features.isEmpty()) {
            val uri = LocalUriHandler.current
            McCard(Modifier.fillMaxWidth(), onClick = { uri.openUri("https://github.com/michig-code/RemoteMC/releases/latest") }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.SystemUpdate, null, tint = McColors.Gold)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(Res.string.plugin_update_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(Res.string.plugin_update_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Hero(status, Modifier.weight(1.4f))
            TpsCard(status, Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), maxItemsInEachRow = 3) {
            val mspt = status.mspt
            Stat(Icons.Outlined.Speed, stringResource(Res.string.dashboard_mspt),
                if (mspt != null) stringResource(Res.string.unit_ms, "%.1f".format(mspt)) else "–", Modifier.weight(1f))
            Stat(Icons.Outlined.Schedule, stringResource(Res.string.dashboard_uptime), formatUptime(status.uptimeSeconds), Modifier.weight(1f))
            MemoryCard(status, Modifier.weight(1f))
        }
        if (loadHistory != null && status.supports("history")) HistoryCard(loadHistory)
        SectionTitle(stringResource(Res.string.dashboard_worlds))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp), maxItemsInEachRow = 3) {
            status.worlds.forEach { WorldCard(it, Modifier.weight(1f).widthIn(min = 220.dp)) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Hero(status: ServerStatus, modifier: Modifier) {
    McCard(modifier) {
        Column(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer))).padding(22.dp)) {
            Text(status.motd.ifBlank { stringResource(Res.string.dashboard_default_motd) }, style = MaterialTheme.typography.titleLarge)
            Text("${status.platform} · ${status.minecraftVersion}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${status.onlinePlayers}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(" / ${status.maxPlayers} " + pluralStringResource(Res.plurals.players_label, status.maxPlayers), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            Bar(if (status.maxPlayers > 0) status.onlinePlayers.toFloat() / status.maxPlayers else 0f, MaterialTheme.colorScheme.primary)
        }
    }
}

private fun Modifier.background(brush: Brush) = this.then(Modifier.drawBehind { drawRect(brush) })

@Composable
private fun TpsCard(status: ServerStatus, modifier: Modifier) {
    McCard(modifier) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val tps = status.tps.firstOrNull()
            if (tps == null) {
                Text(stringResource(Res.string.dashboard_tps_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            val color = tpsColor(tps)
            val track = MaterialTheme.colorScheme.surfaceContainerHighest
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 14.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 135f, 270f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(color, 135f, 270f * (tps / 20.0).toFloat().coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(tps), style = MaterialTheme.typography.headlineLarge, color = color)
                    Text("TPS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOf(Res.string.dashboard_tps_1m, Res.string.dashboard_tps_5m, Res.string.dashboard_tps_15m).forEachIndexed { i, label ->
                    status.tps.getOrNull(i)?.let { v ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("%.1f".format(v), style = MaterialTheme.typography.titleMedium, color = tpsColor(v))
                            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    McCard(modifier) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun MemoryCard(status: ServerStatus, modifier: Modifier) {
    McCard(modifier) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.dashboard_memory), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                val pct = if (status.memoryMaxMb > 0) status.memoryUsedMb * 100 / status.memoryMaxMb else 0
                Text("$pct %", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            Bar(if (status.memoryMaxMb > 0) status.memoryUsedMb.toFloat() / status.memoryMaxMb else 0f, MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.dashboard_memory_detail, formatMb(status.memoryUsedMb), formatMb(status.memoryMaxMb)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorldCard(world: WorldInfo, modifier: Modifier) {
    val (color, label) = when (world.environment) {
        "NETHER" -> McColors.Nether to stringResource(Res.string.world_nether)
        "THE_END" -> McColors.End to stringResource(Res.string.world_end)
        else -> McColors.Grass to stringResource(Res.string.world_overworld)
    }
    McCard(modifier) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).padding(0.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) { drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())) }
                Icon(Icons.Outlined.Public, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(world.name, style = MaterialTheme.typography.titleMedium)
                val details = buildList {
                    add(label)
                    if (world.loadedChunks >= 0) add(pluralStringResource(Res.plurals.world_chunks, world.loadedChunks, world.loadedChunks))
                }.joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(world.players.toString(), style = MaterialTheme.typography.titleLarge, color = color)
        }
    }
}

private enum class Metric(val label: StringResource) {
    TPS(Res.string.history_metric_tps), PLAYERS(Res.string.history_metric_players),
    MEMORY(Res.string.history_metric_memory), MSPT(Res.string.history_metric_mspt);

    fun value(p: HistoryPoint): Double? = when (this) {
        TPS -> p.tps
        PLAYERS -> p.players.toDouble()
        MEMORY -> p.memoryMb.toDouble()
        MSPT -> p.mspt
    }

    fun format(v: Double) = when (this) {
        TPS -> "%.1f".format(v)
        PLAYERS -> v.toInt().toString()
        MEMORY -> formatMb(v.toLong())
        MSPT -> "%.1f ms".format(v)
    }
}


@Composable
private fun HistoryCard(load: suspend (HistoryRange) -> PerformanceHistory) {
    var range by remember { mutableStateOf(HistoryRange.DAY) }
    var metric by remember { mutableStateOf(Metric.TPS) }
    var data by remember { mutableStateOf<PerformanceHistory?>(null) }
    var hidden by remember { mutableStateOf(false) }
    LaunchedEffect(range) {
        data = null
        while (true) {
            try {
                data = load(range)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError.Http) {
                if (e.status == 404) hidden = true
            } catch (_: Exception) {
            }
            delay(60_000)
        }
    }
    if (hidden) return
    McCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Timeline, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.history_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                listOf(HistoryRange.HOUR to Res.string.history_1h, HistoryRange.DAY to Res.string.history_24h, HistoryRange.WEEK to Res.string.history_7d).forEach { (r, label) ->
                    Spacer(Modifier.width(6.dp))
                    Chip(range == r, stringResource(label)) { range = r }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Metric.entries.forEach { m -> Chip(metric == m, stringResource(m.label)) { metric = m } }
            }
            Spacer(Modifier.height(12.dp))
            val d = data
            val points = d?.points.orEmpty()
            val values = points.mapNotNull { metric.value(it) }
            when {
                d == null -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                values.isEmpty() -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    Text(stringResource(Res.string.history_summary, metric.format(values.average()), metric.format(values.min()), metric.format(values.max())),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Chart(d, metric)
                    val fmt = if (d.to - d.from >= 86_400_000L) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) else DateFormat.getTimeInstance(DateFormat.SHORT)
                    Row(Modifier.fillMaxWidth()) {
                        Text(fmt.format(Date(d.from)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(fmt.format(Date(d.to)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(selected: Boolean, label: String, onClick: () -> Unit) = FilterChip(
    selected, onClick, label = { Text(label) },
    colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
)

@Composable
private fun Chart(history: PerformanceHistory, metric: Metric) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val values = history.points.mapNotNull { metric.value(it) }
    val max = if (metric == Metric.TPS) 20.0 else (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0) * 1.15
    val span = (history.to - history.from).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        fun x(p: HistoryPoint) = ((p.time - history.from).toFloat() / span) * size.width
        fun y(v: Double) = size.height - (v / max * size.height).toFloat()
        for (i in 1..3) drawLine(grid, Offset(0f, size.height * i / 4f), Offset(size.width, size.height * i / 4f), 1.dp.toPx())
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        // Lines between neighbouring points; a gap (server off) breaks the line.
        var path: Path? = null
        var fill: Path? = null
        var last: HistoryPoint? = null
        var firstX = 0f
        fun close() {
            val p = path ?: return
            val f = fill!!
            f.lineTo(x(last!!), size.height); f.lineTo(firstX, size.height); f.close()
            drawPath(f, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0.02f))))
            drawPath(p, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            path = null; fill = null
        }
        for (pt in history.points) {
            val v = metric.value(pt)
            val prev = last
            if (v == null || (prev != null && pt.time - prev.time > history.step * 2)) close()
            if (v == null) { last = null; continue }
            if (path == null) {
                path = Path().apply { moveTo(x(pt), y(v)) }
                fill = Path().apply { moveTo(x(pt), y(v)) }
                firstX = x(pt)
            } else {
                path!!.lineTo(x(pt), y(v)); fill!!.lineTo(x(pt), y(v))
            }
            last = pt
        }
        close()
    }
}

fun formatMb(mb: Long) = if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "$mb MB"

fun formatUptime(seconds: Long): String {
    val d = seconds / 86400
    val h = seconds % 86400 / 3600
    val m = seconds % 3600 / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}
