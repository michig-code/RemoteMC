package de.remotemc.app.ui

import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.GroupOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.RemoveModerator
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VerifiedUser
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
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
import de.remotemc.app.ui.components.LocalAnimations
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.components.PlayerHead
import de.remotemc.app.ui.components.staggeredAppear
import de.remotemc.app.ui.theme.McColors
import de.remotemc.app.ui.theme.statusTextColor
import de.remotemc.app.ui.util.UiText
import de.remotemc.app.ui.util.rememberHaptics
import org.json.JSONObject

typealias PlayerAction = (player: String, action: String, body: JSONObject, success: UiText) -> Unit

/** Extras in the player sheet: inventory (null = not offered) and private notes on this device. */
class PlayerDetails(
    val loadInventory: (suspend (String) -> de.remotemc.app.data.PlayerInventory)? = null,
    val note: (uuid: String) -> String = { "" },
    val setNote: (uuid: String, text: String) -> Unit = { _, _ -> },
)

private enum class SortBy(val label: Int) { NAME(R.string.players_sort_name), PING(R.string.players_sort_ping), WORLD(R.string.players_sort_world) }

@Composable
fun PlayersTab(
    players: List<PlayerInfo>,
    role: Role,
    onAction: PlayerAction,
    canTeleport: Boolean = true,
    /** The same action for several players; null turns multi-select off. */
    onBatch: ((players: List<String>, action: String, body: JSONObject) -> Unit)? = null,
    canWhitelist: Boolean = false,
    initialSelection: Set<String> = emptySet(),
    details: PlayerDetails = PlayerDetails(),
) {
    var selected by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SortBy.NAME) }
    val haptics = rememberHaptics()
    // Multi-select (long press): players that leave drop out of the selection.
    val canSelect = onBatch != null && role.atLeast(Role.MODERATOR)
    var picked by remember { mutableStateOf(initialSelection) }
    val names = players.map { it.name }.toSet()
    if (!names.containsAll(picked)) picked = picked intersect names
    val selecting = canSelect && picked.isNotEmpty()

    fun toggle(name: String) {
        haptics.tick()
        picked = if (name in picked) picked - name else picked + name
    }

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
        Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selecting) {
                item(key = "selection") {
                    SelectionBar(picked.size, onAll = { haptics.tap(); picked = shown.map { it.name }.toSet() }, onClose = { picked = emptySet() })
                }
            } else if (players.size > 3) {
                item(key = "search") { SearchBar(query, { query = it }, sort, { sort = it }) }
            }
            if (canSelect && !selecting && players.size > 1) {
                item(key = "hint") {
                    Text(stringResource(R.string.players_select_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            itemsIndexed(shown, key = { _, p -> p.uuid }) { i, p ->
                PlayerCard(
                    p,
                    Modifier.then(if (LocalAnimations.current) Modifier.animateItem() else Modifier).staggeredAppear(i),
                    picked = selecting && p.name in picked,
                    onLongClick = if (canSelect) { { haptics.heavy(); toggle(p.name) } } else null,
                ) { if (selecting) toggle(p.name) else selected = p.name }
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
        AnimatedVisibility(selecting && onBatch != null) {
            BatchActions(picked.sortedBy { it.lowercase() }, role, canWhitelist) { action, body ->
                onBatch?.invoke(picked.toList(), action, body)
                haptics.success()
                picked = emptySet()
            }
        }
        }
    }

    // Look the player up again so the sheet shows fresh data and closes if they leave.
    selected?.let { name ->
        val player = players.firstOrNull { it.name == name }
        if (player == null) selected = null else PlayerSheet(player, role, players.map { it.name } - name, canTeleport, onAction, details) { selected = null }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerCard(
    p: PlayerInfo,
    modifier: Modifier = Modifier,
    picked: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val description = stringResource(
        R.string.players_card_description, p.name, gameModeLabel(p.gameMode), p.world, p.health.toInt(), p.ping,
    )
    val shape = MaterialTheme.shapes.large
    McCard(
        modifier.fillMaxWidth().clip(shape)
            .then(if (picked) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.padding(14.dp).clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                PlayerHead(p.name, 48.dp, skinUrl = p.skin)
                if (picked) {
                    Icon(
                        Icons.Filled.CheckCircle, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp).size(22.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }
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
private fun PlayerSheet(
    p: PlayerInfo,
    role: Role,
    others: List<String>,
    canTeleport: Boolean,
    onAction: PlayerAction,
    details: PlayerDetails,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var inventory by remember { mutableStateOf(false) }
    var note by remember(p.uuid) { mutableStateOf(details.note(p.uuid)) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var teleport by remember { mutableStateOf(false) }
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
                    if (p.playtime >= 0) {
                        Text(
                            stringResource(R.string.players_playtime, formatPlaytime(p.playtime)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (p.firstPlayed > 0) {
                        Text(
                            stringResource(R.string.players_first_played, dateFormat().format(java.util.Date(p.firstPlayed))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (p.onlineSince > 0) {
                        val minutes = ((System.currentTimeMillis() - p.onlineSince) / 1000).coerceAtLeast(0)
                        Text(
                            stringResource(
                                R.string.players_online_since,
                                java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(p.onlineSince)),
                                formatPlaytime(minutes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

            OutlinedTextField(
                note,
                { v -> note = v.take(de.remotemc.app.data.PlayerNotes.MAX_LENGTH); details.setNote(p.uuid, note) },
                label = { Text(stringResource(R.string.players_note)) },
                leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )

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
                if (canTeleport) {
                    ActionButton(Icons.Outlined.MyLocation, stringResource(R.string.players_teleport), McColors.End) {
                        teleport = true
                    }
                }
                if (details.loadInventory != null) {
                    ActionButton(Icons.Outlined.Inventory2, stringResource(R.string.players_inventory), McColors.Grass) {
                        inventory = true
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

    if (inventory && details.loadInventory != null) {
        InventorySheet(p.name, details.loadInventory) { inventory = false }
    }

    if (teleport) {
        AlertDialog(
            onDismissRequest = { teleport = false },
            icon = { Icon(Icons.Outlined.MyLocation, null) },
            title = { Text(stringResource(R.string.players_teleport_to, p.name)) },
            text = {
                Column {
                    (listOf("spawn") + others).forEach { target ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable {
                                teleport = false
                                act("teleport", JSONObject().put("to", target), UiText.Res(R.string.players_teleported, listOf(p.name)))
                            }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (target == "spawn") {
                                Icon(Icons.Outlined.Home, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            } else {
                                PlayerHead(target, 32.dp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(if (target == "spawn") stringResource(R.string.players_teleport_spawn) else target, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { teleport = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
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

/** "12 h 5 min" / "3 d 4 h" for a play time in seconds. */
internal fun formatPlaytime(seconds: Long): String {
    val minutes = seconds / 60
    return when {
        minutes < 60 -> "$minutes min"
        minutes < 24 * 60 -> "${minutes / 60} h ${minutes % 60} min"
        else -> "${minutes / (24 * 60)} d ${minutes / 60 % 24} h"
    }
}

@Composable
private fun SelectionBar(count: Int, onAll: () -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.action_close)) }
        Text(
            pluralStringResource(R.plurals.players_selected, count, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAll) { Text(stringResource(R.string.players_select_all)) }
    }
}

/** Actions for all selected players, shown at the bottom while selecting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BatchActions(names: List<String>, role: Role, canWhitelist: Boolean, onRun: (action: String, body: JSONObject) -> Unit) {
    val haptics = rememberHaptics()
    var dialog by remember { mutableStateOf<String?>(null) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.players_message), MaterialTheme.colorScheme.primary) { dialog = "message" }
            ActionButton(Icons.Outlined.Favorite, stringResource(R.string.players_heal), McColors.Heart) { onRun("heal", JSONObject()) }
            ActionButton(Icons.Outlined.SportsEsports, stringResource(R.string.players_game_mode), McColors.Grass) { dialog = "gamemode" }
            if (canWhitelist) {
                ActionButton(Icons.Outlined.VerifiedUser, stringResource(R.string.players_whitelist_add), McColors.Gold) { onRun("whitelist", JSONObject()) }
            }
            ActionButton(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.players_kick), McColors.Warning) { haptics.heavy(); dialog = "kick" }
            ActionButton(Icons.Outlined.Block, stringResource(R.string.players_ban), McColors.Offline) { haptics.heavy(); dialog = "ban" }
        }
    }

    when (val d = dialog) {
        "message", "kick", "ban" -> {
            var text by remember(d) { mutableStateOf("") }
            val title = when (d) {
                "message" -> pluralStringResource(R.plurals.players_message_many, names.size, names.size)
                "kick" -> pluralStringResource(R.plurals.players_kick_many, names.size, names.size)
                else -> pluralStringResource(R.plurals.players_ban_many, names.size, names.size)
            }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(title) },
                text = {
                    Column {
                        Text(names.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            text, { if (it.length <= 200) text = it },
                            label = { Text(stringResource(if (d == "message") R.string.players_message else R.string.players_message_or_reason)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = d != "message" || text.isNotBlank(),
                        onClick = {
                            dialog = null
                            if (d == "message") onRun("message", JSONObject().put("message", text)) else onRun(d, JSONObject().put("reason", text))
                        },
                    ) {
                        Text(stringResource(when (d) { "message" -> R.string.action_send; "kick" -> R.string.players_kick; else -> R.string.players_ban }))
                    }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        "gamemode" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.players_game_mode)) },
            text = {
                Column {
                    listOf("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR").forEach { mode ->
                        Text(
                            gameModeLabel(mode),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                .clickable { dialog = null; onRun("gamemode", JSONObject().put("mode", mode)) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** Date in the phone's style, e.g. "12.03.2025". */
private fun dateFormat(): java.text.DateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
