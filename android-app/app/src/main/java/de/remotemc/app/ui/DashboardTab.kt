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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@Composable
fun DashboardTab(status: ServerStatus?, error: String?) {
    if (status == null) {
        if (error != null) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                EmptyState(Icons.Outlined.CloudOff, "Server nicht erreichbar", "$error\n\nTippe oben auf den Status oder ziehe nach unten, um es erneut zu versuchen.")
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
        HeroCard(status, Modifier.staggeredAppear(0))

        Row(Modifier.height(IntrinsicSizeRow), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            McCard(Modifier.weight(1f).fillMaxHeight().staggeredAppear(1)) {
                Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    TpsGauge(status.tps.firstOrNull() ?: 0.0, Modifier.fillMaxWidth().aspectRatio(1f))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniStat(Icons.Outlined.Speed, "MSPT", "%.1f ms".format(status.mspt), msptColor(status.mspt), Modifier.weight(1f).staggeredAppear(2))
                MiniStat(Icons.Outlined.Schedule, "Laufzeit", formatUptime(status.uptimeSeconds), MaterialTheme.colorScheme.primary, Modifier.weight(1f).staggeredAppear(3))
            }
        }

        MemoryCard(status, Modifier.staggeredAppear(4))
        TpsHistoryCard(status.tps, Modifier.staggeredAppear(5))

        SectionTitle("Welten")
        status.worlds.forEachIndexed { i, w -> WorldRow(w, Modifier.staggeredAppear(6 + i)) }
        Spacer(Modifier.height(8.dp))
    }
}

private val IntrinsicSizeRow = 184.dp

@Composable
private fun HeroCard(status: ServerStatus, modifier: Modifier = Modifier) {
    val fraction = if (status.maxPlayers > 0) status.onlinePlayers.toFloat() / status.maxPlayers else 0f
    Box(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer),
                ),
            )
            .padding(20.dp),
    ) {
        Column {
            Text(
                status.motd.lines().firstOrNull().orEmpty().ifBlank { "Minecraft-Server" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                status.version,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedCounter(status.onlinePlayers, MaterialTheme.typography.headlineLarge, MaterialTheme.colorScheme.onSurface)
                Text(
                    " / ${status.maxPlayers} Spieler online",
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
    McCard(modifier.fillMaxWidth()) {
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
            Text(value, style = MaterialTheme.typography.titleLarge)
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
    McCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text("Arbeitsspeicher", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${(fraction * 100).toInt()} %", style = MaterialTheme.typography.titleMedium, color = color)
            }
            Spacer(Modifier.height(12.dp))
            AnimatedBar(fraction, color)
            Spacer(Modifier.height(8.dp))
            Text(
                "${formatMb(status.memoryUsedMb)} von ${formatMb(status.memoryMaxMb)} belegt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TpsHistoryCard(tps: List<Double>, modifier: Modifier = Modifier) {
    McCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("1 min", "5 min", "15 min").forEachIndexed { i, label ->
                val value = tps.getOrNull(i) ?: 0.0
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.1f".format(value), style = MaterialTheme.typography.titleLarge, color = tpsColor(value))
                    Spacer(Modifier.height(6.dp))
                    AnimatedBar((value / 20.0).toFloat(), tpsColor(value), height = 6.dp)
                }
            }
        }
    }
}

@Composable
private fun WorldRow(world: WorldInfo, modifier: Modifier = Modifier) {
    val (color, label) = when (world.environment) {
        "NETHER" -> McColors.Nether to "Nether"
        "THE_END" -> McColors.End to "End"
        else -> McColors.Grass to "Oberwelt"
    }
    McCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(MaterialTheme.shapes.small)
                    .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.5f)))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Public, null, tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(world.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "$label · ${world.loadedChunks} Chunks · ${world.entities} Entities",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(world.players.toString(), style = MaterialTheme.typography.titleLarge, color = color)
                Text("Spieler", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DashboardSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Shimmer(Modifier.fillMaxWidth().height(150.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Shimmer(Modifier.weight(1f).height(IntrinsicSizeRow))
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
