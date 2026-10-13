package de.remotemc.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.PlayerInfo
import de.remotemc.app.data.Role
import de.remotemc.desktop.res.*
import org.jetbrains.compose.resources.stringResource
import org.json.JSONObject

@Composable
fun PlayersPane(session: ServerSession) {
    val players = session.players.sortedBy { it.name.lowercase() }
    var selected by remember { mutableStateOf<String?>(null) }
    if (players.isEmpty()) {
        EmptyState(stringResource(Res.string.players_empty_title), stringResource(Res.string.players_empty_text))
    } else {
        LazyVerticalGrid(
            GridCells.Adaptive(280.dp), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(players, key = { it.uuid.ifBlank { it.name } }) { p -> PlayerCard(p) { selected = p.name } }
        }
    }
    selected?.let { name ->
        val p = session.players.firstOrNull { it.name == name }
        if (p == null) selected = null else PlayerDialog(session, p, players.map { it.name } - name) { selected = null }
    }
}

@Composable
private fun PlayerCard(p: PlayerInfo, onClick: () -> Unit) {
    McCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerHead(p.name, 48.dp, p.skin)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    if (p.op) {
                        Spacer(Modifier.width(6.dp))
                        Badge(stringResource(Res.string.players_op), McColors.Gold)
                    }
                }
                Text("${p.world} · ${p.x}, ${p.y}, ${p.z}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Bar((p.health / p.maxHealth).toFloat(), McColors.Heart, height = 6.dp)
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(Res.string.unit_ping, p.ping), style = MaterialTheme.typography.labelLarge,
                color = when { p.ping < 100 -> McColors.Online; p.ping < 250 -> McColors.Warning; else -> McColors.Offline })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerDialog(session: ServerSession, p: PlayerInfo, others: List<String>, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    var teleport by remember { mutableStateOf(false) }
    val canModerate = session.role.atLeast(Role.MODERATOR)
    fun act(action: String, body: JSONObject, success: String) = session.playerAction(p.name, action, body, success)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { PlayerHead(p.name, 64.dp, p.skin) },
        title = { Text(p.name) },
        text = {
            Column(Modifier.widthIn(min = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.players_level_location, p.level, p.world, p.x, p.y, p.z), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.players_health, "%.1f".format(p.health), "%.0f".format(p.maxHealth)), style = MaterialTheme.typography.labelMedium)
                        Bar((p.health / p.maxHealth).toFloat(), McColors.Heart)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.players_food, p.food), style = MaterialTheme.typography.labelMedium)
                        Bar(p.food / 20f, McColors.Food)
                    }
                }
                if (!canModerate) {
                    Text(stringResource(Res.string.players_viewer_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    return@Column
                }
                val sent = stringResource(Res.string.players_message_sent, p.name)
                OutlinedTextField(
                    text, { if (it.length <= 200) text = it },
                    label = { Text(stringResource(Res.string.players_message_or_reason)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(enabled = text.isNotBlank(), onClick = { act("message", JSONObject().put("message", text), sent); text = "" }) {
                            Icon(Icons.AutoMirrored.Outlined.Send, stringResource(Res.string.players_send_message))
                        }
                    },
                )
                Text(stringResource(Res.string.players_game_mode), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SURVIVAL" to Res.string.gamemode_survival, "CREATIVE" to Res.string.gamemode_creative,
                        "ADVENTURE" to Res.string.gamemode_adventure, "SPECTATOR" to Res.string.gamemode_spectator).forEach { (mode, label) ->
                        val name = stringResource(label)
                        val done = stringResource(Res.string.players_game_mode_set, name)
                        FilterChip(p.gameMode == mode, { act("gamemode", JSONObject().put("mode", mode), done) }, label = { Text(name) })
                    }
                }
                Text(stringResource(Res.string.players_actions), style = MaterialTheme.typography.labelLarge)
                val healed = stringResource(Res.string.players_healed, p.name)
                val opText = stringResource(if (p.op) Res.string.players_deopped else Res.string.players_opped, p.name)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action(Icons.Outlined.Favorite, stringResource(Res.string.players_heal), McColors.Heart) { act("heal", JSONObject(), healed) }
                    Action(Icons.Outlined.MyLocation, stringResource(Res.string.players_teleport), McColors.End) { teleport = true }
                    if (session.role.atLeast(Role.ADMIN)) {
                        Action(Icons.Outlined.Shield, stringResource(if (p.op) Res.string.players_deop else Res.string.players_make_op), McColors.Gold) {
                            act(if (p.op) "deop" else "op", JSONObject(), opText)
                        }
                    }
                    Action(Icons.AutoMirrored.Outlined.Logout, stringResource(Res.string.players_kick), McColors.Warning) { confirm = "kick" }
                    Action(Icons.Outlined.Block, stringResource(Res.string.players_ban), McColors.Offline) { confirm = "ban" }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_close)) } },
    )

    if (teleport) {
        val done = stringResource(Res.string.players_teleported, p.name)
        AlertDialog(
            onDismissRequest = { teleport = false },
            title = { Text(stringResource(Res.string.players_teleport_to, p.name)) },
            text = {
                Column {
                    (listOf("spawn") + others).forEach { target ->
                        TextButton(onClick = { teleport = false; act("teleport", JSONObject().put("to", target), done) }, modifier = Modifier.fillMaxWidth()) {
                            if (target != "spawn") { PlayerHead(target, 24.dp); Spacer(Modifier.width(10.dp)) }
                            Text(if (target == "spawn") stringResource(Res.string.players_teleport_spawn) else target, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { teleport = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }

    confirm?.let { action ->
        val kick = action == "kick"
        val done = stringResource(if (kick) Res.string.players_kicked else Res.string.players_banned, p.name)
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(if (kick) Res.string.players_kick_confirm else Res.string.players_ban_confirm, p.name)) },
            text = { Text(if (text.isBlank()) stringResource(Res.string.players_no_reason) else stringResource(Res.string.players_reason, text)) },
            confirmButton = {
                TextButton(onClick = { act(action, JSONObject().put("reason", text), done); confirm = null; onDismiss() }) {
                    Text(stringResource(if (kick) Res.string.players_kick else Res.string.players_ban))
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) =
    AssistChip(onClick, label = { Text(label) }, leadingIcon = { Icon(icon, null, tint = color) })

