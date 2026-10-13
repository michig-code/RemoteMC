package de.remotemc.desktop

import androidx.compose.ui.input.pointer.isCtrlPressed
import de.remotemc.desktop.res.action_send
import de.remotemc.desktop.res.players_inventory
import de.remotemc.desktop.res.players_note
import de.remotemc.desktop.res.players_online_since
import de.remotemc.desktop.res.players_first_played
import de.remotemc.desktop.res.players_playtime
import de.remotemc.desktop.res.players_ban_many
import de.remotemc.desktop.res.players_kick_many
import de.remotemc.desktop.res.players_message_many
import de.remotemc.desktop.res.players_whitelist_add
import de.remotemc.desktop.res.players_message
import de.remotemc.desktop.res.players_select_hint_pc
import de.remotemc.desktop.res.players_select_all
import de.remotemc.desktop.res.players_selected
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.material3.Surface
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.json.JSONObject

private enum class PlayersView(val label: StringResource) {
    ONLINE(Res.string.players_tab_online),
    WHITELIST(Res.string.players_tab_whitelist),
    BANS(Res.string.players_tab_bans),
}

/** Online players, and for moderators also the whitelist and the ban list. */
@Composable
fun PlayersPane(session: ServerSession, initialView: Int = 0, initialSelection: Set<String> = emptySet()) {
    val moderation = session.role.atLeast(Role.MODERATOR) && session.supports("moderation")
    var view by remember { mutableStateOf(PlayersView.entries[initialView]) }
    Column(Modifier.fillMaxSize()) {
        if (moderation) {
            SingleChoiceSegmentedButtonRow(Modifier.padding(start = 24.dp, top = 8.dp).widthIn(max = 520.dp).fillMaxWidth()) {
                PlayersView.entries.forEachIndexed { i, v ->
                    SegmentedButton(
                        selected = view == v,
                        onClick = { view = v },
                        shape = SegmentedButtonDefaults.itemShape(i, PlayersView.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        icon = {},
                    ) {
                        Text(if (v == PlayersView.ONLINE) "${stringResource(v.label)} · ${session.players.size}" else stringResource(v.label), maxLines = 1)
                    }
                }
            }
        }
        when (if (moderation) view else PlayersView.ONLINE) {
            PlayersView.ONLINE -> OnlinePlayers(session, initialSelection)
            PlayersView.WHITELIST -> WhitelistView(session)
            PlayersView.BANS -> BansView(session)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun OnlinePlayers(session: ServerSession, initialSelection: Set<String>) {
    val players = session.players.sortedBy { it.name.lowercase() }
    var selected by remember { mutableStateOf<String?>(null) }
    // Several players: Ctrl+click or a long press selects; the bar below acts on all of them.
    val canSelect = session.role.atLeast(Role.MODERATOR)
    var picked by remember { mutableStateOf(initialSelection) }
    val names = players.map { it.name }.toSet()
    if (!names.containsAll(picked)) picked = picked intersect names
    val selecting = canSelect && picked.isNotEmpty()
    val window = LocalWindowInfo.current
    fun toggle(name: String) {
        picked = if (name in picked) picked - name else picked + name
    }
    if (players.isEmpty()) {
        EmptyState(stringResource(Res.string.players_empty_title), stringResource(Res.string.players_empty_text))
    } else {
        Column(Modifier.fillMaxSize()) {
            if (selecting) {
                Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { picked = emptySet() }) { Icon(Icons.Outlined.Close, stringResource(Res.string.action_close)) }
                    Text(pluralStringResource(Res.plurals.players_selected, picked.size, picked.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { picked = names }) { Text(stringResource(Res.string.players_select_all)) }
                }
            } else if (canSelect && players.size > 1) {
                Text(stringResource(Res.string.players_select_hint_pc), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 28.dp, top = 10.dp))
            }
            LazyVerticalGrid(
                GridCells.Adaptive(280.dp), Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(players, key = { _, it -> it.uuid.ifBlank { it.name } }) { i, p ->
                    Box(Modifier.staggeredAppear(i)) {
                        PlayerCard(
                            p, picked = selecting && p.name in picked,
                            onLongClick = if (canSelect) ({ toggle(p.name) }) else null,
                        ) {
                            if (canSelect && (selecting || window.keyboardModifiers.isCtrlPressed)) toggle(p.name) else selected = p.name
                        }
                    }
                }
            }
            if (selecting) {
                BatchBar(session, picked.sortedBy { it.lowercase() }) { picked = emptySet() }
            }
        }
    }
    selected?.let { name ->
        val p = session.players.firstOrNull { it.name == name }
        if (p == null) selected = null else PlayerDialog(session, p, players.map { it.name } - name) { selected = null }
    }
}

/** Actions for all selected players. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchBar(session: ServerSession, names: List<String>, onDone: () -> Unit) {
    var dialog by remember { mutableStateOf<String?>(null) }
    fun run(action: String, body: JSONObject = JSONObject()) {
        session.playerBatch(names, action, body)
        onDone()
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Action(Icons.AutoMirrored.Outlined.Send, stringResource(Res.string.players_message), MaterialTheme.colorScheme.primary) { dialog = "message" }
            Action(Icons.Outlined.Favorite, stringResource(Res.string.players_heal), McColors.Heart) { run("heal") }
            Action(Icons.Outlined.SportsEsports, stringResource(Res.string.players_game_mode), McColors.Online) { dialog = "gamemode" }
            if (session.supports("moderation")) {
                Action(Icons.Outlined.VerifiedUser, stringResource(Res.string.players_whitelist_add), McColors.Gold) { run("whitelist") }
            }
            Action(Icons.AutoMirrored.Outlined.Logout, stringResource(Res.string.players_kick), McColors.Warning) { dialog = "kick" }
            Action(Icons.Outlined.Block, stringResource(Res.string.players_ban), McColors.Offline) { dialog = "ban" }
        }
    }
    when (val d = dialog) {
        "message", "kick", "ban" -> {
            var text by remember(d) { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = {
                    Text(when (d) {
                        "message" -> pluralStringResource(Res.plurals.players_message_many, names.size, names.size)
                        "kick" -> pluralStringResource(Res.plurals.players_kick_many, names.size, names.size)
                        else -> pluralStringResource(Res.plurals.players_ban_many, names.size, names.size)
                    })
                },
                text = {
                    Column(Modifier.widthIn(min = 380.dp)) {
                        Text(names.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            text, { if (it.length <= 200) text = it },
                            label = { Text(stringResource(if (d == "message") Res.string.players_message else Res.string.players_message_or_reason)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(enabled = d != "message" || text.isNotBlank(), onClick = {
                        dialog = null
                        if (d == "message") run("message", JSONObject().put("message", text)) else run(d, JSONObject().put("reason", text))
                    }) { Text(stringResource(when (d) { "message" -> Res.string.action_send; "kick" -> Res.string.players_kick; else -> Res.string.players_ban })) }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(Res.string.action_cancel)) } },
            )
        }
        "gamemode" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(Res.string.players_game_mode)) },
            text = {
                Column {
                    listOf("SURVIVAL" to Res.string.gamemode_survival, "CREATIVE" to Res.string.gamemode_creative,
                        "ADVENTURE" to Res.string.gamemode_adventure, "SPECTATOR" to Res.string.gamemode_spectator).forEach { (mode, label) ->
                        TextButton(onClick = { dialog = null; run("gamemode", JSONObject().put("mode", mode)) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(label), modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerCard(p: PlayerInfo, picked: Boolean = false, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    McCard(
        Modifier.fillMaxWidth().clip(shape)
            .then(if (picked) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                PlayerHead(p.name, 48.dp, p.skin)
                if (picked) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp).size(22.dp).background(MaterialTheme.colorScheme.surface, CircleShape))
                }
            }
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
    var inventory by remember { mutableStateOf(false) }
    var note by remember(p.uuid) { mutableStateOf(session.note(p.uuid)) }
    val canModerate = session.role.atLeast(Role.MODERATOR)
    fun act(action: String, body: JSONObject, success: String) = session.playerAction(p.name, action, body, success)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { PlayerHead(p.name, 64.dp, p.skin) },
        title = { Text(p.name) },
        text = {
            Column(Modifier.widthIn(min = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.players_level_location, p.level, p.world, p.x, p.y, p.z), color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Time played, first join and the current session.
                val facts = buildList {
                    if (p.playtime >= 0) add(stringResource(Res.string.players_playtime, formatDuration(p.playtime)))
                    if (p.firstPlayed > 0) add(stringResource(Res.string.players_first_played, java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(p.firstPlayed))))
                    if (p.onlineSince > 0) add(stringResource(Res.string.players_online_since,
                        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(p.onlineSince)),
                        formatDuration(((System.currentTimeMillis() - p.onlineSince) / 1000).coerceAtLeast(0))))
                }
                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                OutlinedTextField(
                    note, { v -> note = v.take(1000); session.setNote(p.uuid, note) },
                    label = { Text(stringResource(Res.string.players_note)) },
                    leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                    maxLines = 3, modifier = Modifier.fillMaxWidth(),
                )
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
                    if (session.status?.supports("teleport") == true) {
                        Action(Icons.Outlined.MyLocation, stringResource(Res.string.players_teleport), McColors.End) { teleport = true }
                    }
                    if (session.supports("inventory")) {
                        Action(Icons.Outlined.Inventory2, stringResource(Res.string.players_inventory), McColors.Online) { inventory = true }
                    }
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

    if (inventory) {
        InventorySheet(p.name, { session.manage.inventory(it) }) { inventory = false }
    }

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

