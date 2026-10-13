package de.remotemc.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import de.remotemc.app.data.HistoryRange
import de.remotemc.app.data.PerformanceHistory
import de.remotemc.app.data.PluginInfo
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.data.WorldInfo
import de.remotemc.app.ui.components.AnimatedBar
import de.remotemc.app.ui.components.AnimatedCounter
import de.remotemc.app.ui.components.EmptyState
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.components.SectionTitle
import de.remotemc.app.ui.components.Shimmer
import de.remotemc.app.ui.components.TpsGauge
import de.remotemc.app.ui.components.staggeredAppear
import de.remotemc.app.ui.components.tpsColor
import de.remotemc.app.ui.theme.McColors
import de.remotemc.app.ui.theme.statusTextColor

@Composable
fun DashboardTab(
    status: ServerStatus?,
    error: String?,
    wide: Boolean = false,
    loadHistory: (suspend (HistoryRange) -> PerformanceHistory)? = null,
    worldActions: WorldActions? = null,
    loadPlugins: (suspend () -> List<PluginInfo>)? = null,
    schedules: ScheduleActions? = null,
) {
    var showSchedules by rememberSaveable { mutableStateOf(false) }
    if (showSchedules && schedules != null) SchedulesSheet(schedules) { showSchedules = false }
    var showPlugins by rememberSaveable { mutableStateOf(false) }
    if (showPlugins && loadPlugins != null) PluginsSheet(loadPlugins) { showPlugins = false }
    var openWorld by rememberSaveable { mutableStateOf<String?>(null) }
    val onWorld: ((String) -> Unit)? = worldActions?.let { { name: String -> openWorld = name } }
    if (worldActions != null) {
        openWorld?.let { WorldSheet(it, worldActions) { openWorld = null } }
    }
    if (status == null) {
        if (error != null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    Icons.Outlined.CloudOff,
                    stringResource(R.string.dashboard_unreachable_title),
                    error + "\n\n" + stringResource(R.string.dashboard_unreachable_hint),
                    tint = McColors.Offline,
                )
            }
        } else {
            DashboardSkeleton()
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (status.features.isEmpty()) PluginUpdateCard()
                    HeroCard(status, Modifier.staggeredAppear(0))
                    MemoryCard(status, Modifier.staggeredAppear(2))
                    TpsHistoryCard(status.tps, Modifier.staggeredAppear(3))
                    loadHistory?.let { HistoryCard(it, Modifier.staggeredAppear(4)) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GaugeRow(status, Modifier.staggeredAppear(1))
                    Worlds(status.worlds, onWorld)
            schedules?.let { SchedulesRow { showSchedules = true } }
            loadPlugins?.let { PluginsRow { showPlugins = true } }
                }
            }
        } else {
            if (status.features.isEmpty()) PluginUpdateCard()
            HeroCard(status, Modifier.staggeredAppear(0))
            GaugeRow(status, Modifier.staggeredAppear(1))
            MemoryCard(status, Modifier.staggeredAppear(4))
            TpsHistoryCard(status.tps, Modifier.staggeredAppear(5))
            loadHistory?.let { HistoryCard(it, Modifier.staggeredAppear(6)) }
            Worlds(status.worlds, onWorld)
            schedules?.let { SchedulesRow { showSchedules = true } }
            loadPlugins?.let { PluginsRow { showPlugins = true } }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Worlds(worlds: List<WorldInfo>, onOpen: ((String) -> Unit)?) {
    SectionTitle(stringResource(R.string.dashboard_worlds))
    worlds.forEachIndexed { i, w -> WorldRow(w, Modifier.staggeredAppear(6 + i), onOpen?.let { { it(w.name) } }) }
}

private val GaugeRowHeight = 184.dp

@Composable
private fun GaugeRow(status: ServerStatus, modifier: Modifier = Modifier) {
    Row(modifier.heightIn(min = GaugeRowHeight).height(GaugeRowHeight), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        McCard(Modifier.weight(1f).fillMaxHeight()) {
            Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                TpsGauge(status.tps.firstOrNull(), Modifier.fillMaxWidth().aspectRatio(1f))
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat(
                Icons.Outlined.Speed, stringResource(R.string.dashboard_mspt),
                status.mspt?.let { stringResource(R.string.unit_ms, it) } ?: "–",
                status.mspt?.let(::msptColor) ?: MaterialTheme.colorScheme.onSurfaceVariant,
                Modifier.weight(1f),
            )
            MiniStat(
                Icons.Outlined.Schedule, stringResource(R.string.dashboard_uptime),
                formatUptime(status.uptimeSeconds), MaterialTheme.colorScheme.primary, Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeroCard(status: ServerStatus, modifier: Modifier = Modifier) {
    val fraction = if (status.maxPlayers > 0) status.onlinePlayers.toFloat() / status.maxPlayers else 0f
    val playersText = pluralStringResource(R.plurals.players_online, status.onlinePlayers, status.onlinePlayers, status.maxPlayers)
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer)),
            )
            .padding(20.dp),
    ) {
        Column {
            Text(
                status.motd.lines().firstOrNull().orEmpty().ifBlank { stringResource(R.string.dashboard_default_motd) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(status.platform, status.minecraftVersion).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { status.version },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.clearAndSetSemantics { contentDescription = playersText }, verticalAlignment = Alignment.Bottom) {
                AnimatedCounter(status.onlinePlayers, MaterialTheme.typography.headlineLarge, MaterialTheme.colorScheme.onSurface)
                Text(
                    " / " + pluralStringResource(R.plurals.dashboard_players_suffix, status.maxPlayers, status.maxPlayers),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            AnimatedBar(fraction, MaterialTheme.colorScheme.primary, height = 8.dp)
        }
    }
}

@Composable
private fun MiniStat(icon: ImageVector, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    McCard(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(28.dp).background(color.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, Modifier.size(16.dp), tint = color) }
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        }
    }
}

@Composable
private fun MemoryCard(status: ServerStatus, modifier: Modifier = Modifier) {
    val fraction = if (status.memoryMaxMb > 0) status.memoryUsedMb.toFloat() / status.memoryMaxMb else 0f
    val color = when {
        fraction < 0.7f -> McColors.Online
        fraction < 0.9f -> McColors.Warning
        else -> McColors.Offline
    }
    McCard(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dashboard_memory), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${(fraction * 100).toInt()} %", style = MaterialTheme.typography.titleMedium, color = statusTextColor(color))
            }
            Spacer(Modifier.height(12.dp))
            AnimatedBar(fraction, color)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.dashboard_memory_detail, formatMb(status.memoryUsedMb), formatMb(status.memoryMaxMb)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TpsHistoryCard(tps: List<Double>, modifier: Modifier = Modifier) {
    McCard(modifier.fillMaxWidth()) {
        if (tps.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_tps_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return@McCard
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(R.string.dashboard_tps_1m, R.string.dashboard_tps_5m, R.string.dashboard_tps_15m).forEachIndexed { i, label ->
                val value = tps.getOrNull(i) ?: 0.0
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.1f".format(value), style = MaterialTheme.typography.titleLarge, color = statusTextColor(tpsColor(value)))
                    Spacer(Modifier.height(6.dp))
                    AnimatedBar((value / 20.0).toFloat(), tpsColor(value), height = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun WorldRow(world: WorldInfo, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val (color, label) = when (world.environment) {
        "NETHER" -> McColors.Nether to stringResource(R.string.world_nether)
        "THE_END" -> McColors.End to stringResource(R.string.world_end)
        else -> McColors.Grass to stringResource(R.string.world_overworld)
    }
    val details = buildList {
        add(label)
        if (world.loadedChunks >= 0) add(pluralStringResource(R.plurals.world_chunks, world.loadedChunks, world.loadedChunks))
        if (world.entities >= 0) add(pluralStringResource(R.plurals.world_entities, world.entities, world.entities))
    }.joinToString(" · ")
    McCard(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
                    .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.5f)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Public, null, tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(world.name, style = MaterialTheme.typography.titleMedium)
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(world.players.toString(), style = MaterialTheme.typography.titleLarge, color = statusTextColor(color))
                Text(
                    pluralStringResource(R.plurals.players_label, world.players),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DashboardSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Shimmer(Modifier.fillMaxWidth().height(150.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Shimmer(Modifier.weight(1f).height(GaugeRowHeight))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Shimmer(Modifier.fillMaxWidth().height(86.dp))
                Shimmer(Modifier.fillMaxWidth().height(86.dp))
            }
        }
        Shimmer(Modifier.fillMaxWidth().height(110.dp))
        Shimmer(Modifier.fillMaxWidth().height(90.dp))
    }
}

private fun msptColor(mspt: Double) = when {
    mspt <= 40 -> McColors.Online
    mspt <= 50 -> McColors.Warning
    else -> McColors.Offline
}

private fun formatMb(mb: Long) = if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "$mb MB"

internal fun formatUptime(seconds: Long): String {
    val d = seconds / 86400
    val h = seconds % 86400 / 3600
    val m = seconds % 3600 / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

/** Shown for plugins older than the feature list: the new features need the newer plugin. */
@Composable
private fun PluginUpdateCard() {
    val uri = LocalUriHandler.current
    McCard(Modifier.fillMaxWidth(), onClick = { uri.openUri(PLUGIN_DOWNLOAD_URL) }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SystemUpdate, null, tint = McColors.Gold)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.plugin_update_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.plugin_update_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
