package de.mcremote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.GroupOff
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.RemoveModerator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mcremote.app.data.PlayerInfo
import de.mcremote.app.ui.components.AnimatedBar
import de.mcremote.app.ui.components.Badge
import de.mcremote.app.ui.components.EmptyState
import de.mcremote.app.ui.components.McCard
import de.mcremote.app.ui.components.PlayerHead
import de.mcremote.app.ui.components.staggeredAppear
import de.mcremote.app.ui.theme.McColors
import org.json.JSONObject

typealias PlayerAction = (player: String, action: String, body: JSONObject, success: String) -> Unit

@Composable
fun PlayersTab(players: List<PlayerInfo>, onAction: PlayerAction) {
    var selected by remember { mutableStateOf<String?>(null) }

    if (players.isEmpty()) {
        // Scrollable so pull-to-refresh still works on the empty state.
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            EmptyState(Icons.Outlined.GroupOff, "Niemand online", "Sobald jemand den Server betritt, erscheint er hier.")
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(players, key = { _, p -> p.uuid }) { i, p ->
                PlayerCard(p, Modifier.animateItem().staggeredAppear(i)) { selected = p.name }
            }
        }
    }

    // Look the player up again so the sheet shows fresh data and closes if they leave.
    selected?.let { name ->
        val player = players.firstOrNull { it.name == name }
        if (player == null) selected = null else PlayerSheet(player, onAction) { selected = null }
    }
}

@Composable
private fun PlayerCard(p: PlayerInfo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    McCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerHead(p.name, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (p.op) {
                        Spacer(Modifier.width(6.dp))
                        Badge("OP", McColors.Gold)
                    }
                }
                Text(
                    "${p.world} · ${p.x}, ${p.y}, ${p.z}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Favorite, null, Modifier.size(12.dp), tint = McColors.Heart)
                    Spacer(Modifier.width(4.dp))
                    AnimatedBar((p.health / 20.0).toFloat(), McColors.Heart, Modifier.width(70.dp), height = 5.dp)
                    Spacer(Modifier.width(10.dp))
                    Badge(gameModeLabel(p.gameMode), gameModeColor(p.gameMode))
                }
            }
            PingIndicator(p.ping)
        }
    }
}

@Composable
private fun PingIndicator(ping: Int) {
    val (bars, color) = when {
        ping < 80 -> 4 to McColors.Online
        ping < 150 -> 3 to McColors.Online
        ping < 300 -> 2 to McColors.Warning
        else -> 1 to McColors.Offline
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            (1..4).forEach { i ->
                Box(
                    Modifier
                        .width(4.dp)
                        .height((4 + i * 3).dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (i <= bars) color else MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("$ping ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlayerSheet(p: PlayerInfo, onAction: PlayerAction, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerHead(p.name, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.headlineSmall)
                        if (p.op) {
                            Spacer(Modifier.width(8.dp))
                            Badge("OP", McColors.Gold)
                        }
                    }
                    Text(
                        "Level ${p.level} · ${p.world} (${p.x}, ${p.y}, ${p.z})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Leben ${p.health} / 20", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    AnimatedBar((p.health / 20.0).toFloat(), McColors.Heart, height = 8.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text("Hunger ${p.food} / 20", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    AnimatedBar(p.food / 20f, McColors.Food, height = 8.dp)
                }
            }

            OutlinedTextField(
                text, { text = it },
                label = { Text("Nachricht oder Grund") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        enabled = text.isNotBlank(),
                        onClick = {
                            onAction(p.name, "message", JSONObject().put("message", text), "Nachricht an ${p.name} gesendet")
                            text = ""
                        },
                    ) { Icon(Icons.AutoMirrored.Outlined.Send, "Nachricht senden") }
                },
            )

            Text("Spielmodus", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR").forEach { mode ->
                    FilterChip(
                        selected = p.gameMode == mode,
                        onClick = {
                            onAction(p.name, "gamemode", JSONObject().put("mode", mode), "Spielmodus: ${gameModeLabel(mode)}")
                        },
                        label = { Text(gameModeLabel(mode)) },
                    )
                }
            }

            Text("Aktionen", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(Icons.Outlined.Favorite, "Heilen", McColors.Heart) {
                    onAction(p.name, "heal", JSONObject(), "${p.name} geheilt")
                }
                ActionButton(
                    if (p.op) Icons.Outlined.RemoveModerator else Icons.Outlined.Shield,
                    if (p.op) "OP entziehen" else "OP geben",
                    McColors.Gold,
                ) {
                    onAction(p.name, if (p.op) "deop" else "op", JSONObject(), if (p.op) "OP entzogen" else "OP vergeben")
                }
                ActionButton(Icons.Outlined.Logout, "Kicken", McColors.Warning) { confirm = "kick" }
                ActionButton(Icons.Outlined.Block, "Bannen", McColors.Offline) { confirm = "ban" }
            }
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(if (action == "kick") Icons.Outlined.Logout else Icons.Outlined.Block, null) },
            title = { Text(if (action == "kick") "${p.name} kicken?" else "${p.name} bannen?") },
            text = { Text(if (text.isBlank()) "Ohne Angabe eines Grundes." else "Grund: $text") },
            confirmButton = {
                TextButton(onClick = {
                    onAction(
                        p.name, action, JSONObject().put("reason", text),
                        if (action == "kick") "${p.name} gekickt" else "${p.name} gebannt",
                    )
                    confirm = null
                    onDismiss()
                }) { Text("Bestätigen") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, null, Modifier.size(18.dp), tint = color)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

private fun gameModeLabel(mode: String) = when (mode) {
    "SURVIVAL" -> "Überleben"
    "CREATIVE" -> "Kreativ"
    "ADVENTURE" -> "Abenteuer"
    "SPECTATOR" -> "Zuschauer"
    else -> mode.lowercase()
}

private fun gameModeColor(mode: String) = when (mode) {
    "CREATIVE" -> McColors.End
    "ADVENTURE" -> McColors.Food
    "SPECTATOR" -> Color(0xFF64B5F6)
    else -> McColors.Grass
}
