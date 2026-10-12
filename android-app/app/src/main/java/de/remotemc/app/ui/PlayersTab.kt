package de.remotemc.app.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.GroupOff
import androidx.compose.material.icons.outlined.RemoveModerator
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import de.remotemc.app.data.PlayerInfo
import de.remotemc.app.data.Role
import de.remotemc.app.ui.components.AnimatedBar
import de.remotemc.app.ui.components.Badge
import de.remotemc.app.ui.components.EmptyState
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.components.PlayerHead
import de.remotemc.app.ui.components.staggeredAppear
import de.remotemc.app.ui.theme.McColors
import de.remotemc.app.ui.theme.statusTextColor
import de.remotemc.app.ui.util.UiText
import de.remotemc.app.ui.util.rememberHaptics
import org.json.JSONObject

typealias PlayerAction = (player: String, action: String, body: JSONObject, success: UiText) -> Unit

private enum class SortBy(val label: Int) { NAME(R.string.players_sort_name), PING(R.string.players_sort_ping), WORLD(R.string.players_sort_world) }

@Composable
fun PlayersTab(players: List<PlayerInfo>, role: Role, onAction: PlayerAction) {
    var selected by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortBy.NAME) }

    if (players.isEmpty()) {
        // Scrollable so pull-to-refresh still works on the empty state.
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            EmptyState(Icons.Outlined.GroupOff, stringResource(R.string.players_empty_title), stringResource(R.string.players_empty_text))
        }
    } else {
        val shown = players
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .sortedWith(
                when (sort) {
                    SortBy.NAME -> compareBy { it.name.lowercase() }
                    SortBy.PING -> compareBy { it.ping }
                    SortBy.WORLD -> compareBy<PlayerInfo> { it.world }.thenBy { it.name.lowercase() }
                },
            )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (players.size > 3) {
                item(key = "search") { SearchBar(query, { query = it }, sort, { sort = it }) }
            }
            itemsIndexed(shown, key = { _, p -> p.uuid }) { i, p ->
                PlayerCard(p, Modifier.animateItem().staggeredAppear(i)) { selected = p.name }
            }
            if (shown.isEmpty()) {
                item(key = "nomatch") {
                    Text(
                        stringResource(R.string.players_no_match, query),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    // Look the player up again so the sheet shows fresh data and closes if they leave.
    selected?.let { name ->
        val player = players.firstOrNull { it.name == name }
        if (player == null) selected = null else PlayerSheet(player, role, onAction) { selected = null }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, sort: SortBy, onSort: (SortBy) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.players_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.action_clear)) }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        )
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.players_sort))
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                SortBy.entries.forEach {
                    DropdownMenuItem(
                        text = { Text(stringResource(it.label)) },
                        onClick = { onSort(it); menu = false },
                        trailingIcon = { if (it == sort) Text("✓") },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(p: PlayerInfo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val description = stringResource(
        R.string.players_card_description, p.name, gameModeLabel(p.gameMode), p.world, p.health.toInt(), p.ping,
    )
    McCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.padding(14.dp).clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerHead(p.name, 48.dp, skinUrl = p.skin)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (p.op) {
                        Spacer(Modifier.width(6.dp))
                        Badge(stringResource(R.string.players_op), McColors.Gold)
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
                    AnimatedBar((p.health / p.maxHealth).toFloat(), McColors.Heart, Modifier.width(70.dp), height = 5.dp)
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
        Text(stringResource(R.string.unit_ping, ping), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlayerSheet(p: PlayerInfo, role: Role, onAction: PlayerAction, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberHaptics()
    val canModerate = role.atLeast(Role.MODERATOR)

    fun act(action: String, body: JSONObject, success: UiText) {
        haptics.success()
        onAction(p.name, action, body, success)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerHead(p.name, 64.dp, skinUrl = p.skin)
                Spacer(Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, style = MaterialTheme.typography.headlineSmall)
                        if (p.op) {
                            Spacer(Modifier.width(8.dp))
                            Badge(stringResource(R.string.players_op), McColors.Gold)
                        }
                    }
                    Text(
                        stringResource(R.string.players_level_location, p.level, p.world, p.x, p.y, p.z),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.players_health, p.health, p.maxHealth), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    AnimatedBar((p.health / p.maxHealth).toFloat(), McColors.Heart, height = 8.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.players_food, p.food), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    AnimatedBar(p.food / 20f, McColors.Food, height = 8.dp)
                }
            }

            if (!canModerate) {
                Text(
                    stringResource(R.string.players_viewer_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            OutlinedTextField(
                text, { if (it.length <= 200) text = it },
                label = { Text(stringResource(R.string.players_message_or_reason)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        enabled = text.isNotBlank(),
                        onClick = {
                            act("message", JSONObject().put("message", text), UiText.Res(R.string.players_message_sent, listOf(p.name)))
                            text = ""
                        },
                    ) { Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.players_send_message)) }
                },
            )

            Text(stringResource(R.string.players_game_mode), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR").forEach { mode ->
                    val label = gameModeLabel(mode)
                    FilterChip(
                        selected = p.gameMode == mode,
                        onClick = {
                            act("gamemode", JSONObject().put("mode", mode), UiText.Res(R.string.players_game_mode_set, listOf(label)))
                        },
                        label = { Text(label) },
                    )
                }
            }

            Text(stringResource(R.string.players_actions), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(Icons.Outlined.Favorite, stringResource(R.string.players_heal), McColors.Heart) {
                    act("heal", JSONObject(), UiText.Res(R.string.players_healed, listOf(p.name)))
                }
                if (role.atLeast(Role.ADMIN)) {
                    ActionButton(
                        if (p.op) Icons.Outlined.RemoveModerator else Icons.Outlined.Shield,
                        stringResource(if (p.op) R.string.players_deop else R.string.players_make_op),
                        McColors.Gold,
                    ) {
                        act(if (p.op) "deop" else "op", JSONObject(),
                            UiText.Res(if (p.op) R.string.players_deopped else R.string.players_opped, listOf(p.name)))
                    }
                }
                ActionButton(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.players_kick), McColors.Warning) {
                    haptics.heavy(); confirm = "kick"
                }
                ActionButton(Icons.Outlined.Block, stringResource(R.string.players_ban), McColors.Offline) {
                    haptics.heavy(); confirm = "ban"
                }
            }
        }
    }

    confirm?.let { action ->
        val kick = action == "kick"
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(if (kick) Icons.AutoMirrored.Outlined.Logout else Icons.Outlined.Block, null) },
            title = { Text(stringResource(if (kick) R.string.players_kick_confirm else R.string.players_ban_confirm, p.name)) },
            text = {
                Text(if (text.isBlank()) stringResource(R.string.players_no_reason) else stringResource(R.string.players_reason, text))
            },
            confirmButton = {
                TextButton(onClick = {
                    act(action, JSONObject().put("reason", text),
                        UiText.Res(if (kick) R.string.players_kicked else R.string.players_banned, listOf(p.name)))
                    confirm = null
                    onDismiss()
                }) { Text(stringResource(if (kick) R.string.players_kick else R.string.players_ban)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, null, Modifier.size(18.dp), tint = statusTextColor(color))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun gameModeLabel(mode: String) = when (mode) {
    "SURVIVAL" -> stringResource(R.string.gamemode_survival)
    "CREATIVE" -> stringResource(R.string.gamemode_creative)
    "ADVENTURE" -> stringResource(R.string.gamemode_adventure)
    "SPECTATOR" -> stringResource(R.string.gamemode_spectator)
    else -> mode.lowercase()
}

private fun gameModeColor(mode: String) = when (mode) {
    "CREATIVE" -> McColors.End
    "ADVENTURE" -> McColors.Food
    "SPECTATOR" -> Color(0xFF64B5F6)
    else -> McColors.Grass
}
