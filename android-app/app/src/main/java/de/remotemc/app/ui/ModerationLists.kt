package de.remotemc.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import de.remotemc.app.data.BanInfo
import de.remotemc.app.data.Role
import de.remotemc.app.data.Whitelist
import de.remotemc.app.ui.components.EmptyState
import de.remotemc.app.ui.components.LocalAnimations
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.components.PlayerHead
import de.remotemc.app.ui.util.errorText
import de.remotemc.app.ui.util.rememberHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class PlayersView(val label: Int) {
    ONLINE(R.string.players_tab_online),
    WHITELIST(R.string.players_tab_whitelist),
    BANS(R.string.players_tab_bans),
}

/** Players tab: online players, and for moderators also the whitelist and the ban list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayersSection(state: ServerUiState, actions: ServerActions, initialView: Int = 0) {
    // Only plugins that have the whitelist / ban list endpoints (older ones show players only).
    val moderation = actions.moderation?.takeIf { state.role.atLeast(Role.MODERATOR) && state.status?.supports("moderation") == true }
    var view by rememberSaveable { mutableStateOf(PlayersView.entries[initialView]) }
    Column(Modifier.fillMaxSize()) {
        if (moderation != null) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
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
                        Text(
                            if (v == PlayersView.ONLINE) "${stringResource(v.label)} · ${state.players.size}" else stringResource(v.label),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        when (if (moderation == null) PlayersView.ONLINE else view) {
            PlayersView.ONLINE -> PullToRefreshBox(state.refreshing, actions.onRefresh) {
                PlayersTab(
                    state.players, state.role, actions.onPlayerAction,
                    canTeleport = state.status?.supports("teleport") == true,
                    onBatch = actions.onPlayerBatch,
                    canWhitelist = actions.moderation != null,
                )
            }
            PlayersView.WHITELIST -> WhitelistView(moderation!!, canToggle = state.role.atLeast(Role.ADMIN))
            PlayersView.BANS -> BansView(moderation!!)
        }
    }
}

/** Loads a list, reloads on pull, and runs changes that return the new list. */
private class Loader<T>(val scope: kotlinx.coroutines.CoroutineScope) {
    var data by mutableStateOf<T?>(null)
    var error by mutableStateOf<Throwable?>(null)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    fun run(onDone: (T) -> String? = { null }, call: suspend () -> T) {
        scope.launch {
            busy = true
            try {
                val result = call()
                data = result
                error = null
                message = onDone(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e
            } finally {
                busy = false
            }
        }
    }
}

@Composable
private fun <T> rememberLoader(load: suspend () -> T): Pair<Loader<T>, () -> Unit> {
    val scope = rememberCoroutineScope()
    val loader = remember { Loader<T>(scope) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { loader.run { load() } }
    return loader to { tick++ }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhitelistView(moderation: ModerationActions, canToggle: Boolean) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val (loader, reload) = rememberLoader { moderation.whitelist() }
    var name by rememberSaveable { mutableStateOf("") }
    val list = loader.data

    fun add() {
        val n = name.trim()
        if (n.isEmpty()) return
        loader.run({ haptics.success(); name = ""; context.getString(R.string.whitelist_added, n) }) { moderation.addToWhitelist(n) }
    }

    PullToRefreshBox(loader.busy && list != null, reload) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list == null) {
                item { LoadingOrError(loader.error, reload) }
                return@LazyColumn
            }
            item(key = "switch") {
                McCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.whitelist_enabled), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(if (list.enabled) R.string.whitelist_enabled_text else R.string.whitelist_disabled_text),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            list.enabled,
                            onCheckedChange = { on -> haptics.tap(); loader.run { moderation.setWhitelist(on) } },
                            enabled = canToggle && !loader.busy,
                        )
                    }
                }
            }
            item(key = "add") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { v -> name = v.filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(16) },
                        label = { Text(stringResource(R.string.whitelist_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = ::add, enabled = name.isNotBlank() && !loader.busy) { Text(stringResource(R.string.whitelist_add)) }
                }
            }
            loader.error?.let { e -> item(key = "error") { ErrorLine(context.errorText(e)) } }
            loader.message?.let { m -> item(key = "message") { Text(m, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) } }
            if (list.players.isEmpty()) {
                item(key = "empty") { EmptyLine(stringResource(R.string.whitelist_empty)) }
            }
            items(list.players, key = { it.lowercase() }) { player ->
                McCard(Modifier.fillMaxWidth().then(if (LocalAnimations.current) Modifier.animateItem() else Modifier)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayerHead(player, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(player, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                haptics.tap()
                                loader.run({ context.getString(R.string.whitelist_removed, player) }) { moderation.removeFromWhitelist(player) }
                            },
                            enabled = !loader.busy,
                        ) { Icon(Icons.Outlined.Close, stringResource(R.string.whitelist_remove, player)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BansView(moderation: ModerationActions) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val (loader, reload) = rememberLoader { moderation.bans() }
    var confirm by remember { mutableStateOf<BanInfo?>(null) }
    val list = loader.data
    val dates = DateFormat.getDateInstance(DateFormat.MEDIUM)

    PullToRefreshBox(loader.busy && list != null, reload) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (list == null) {
                item { LoadingOrError(loader.error, reload) }
                return@LazyColumn
            }
            loader.error?.let { e -> item(key = "error") { ErrorLine(context.errorText(e)) } }
            loader.message?.let { m -> item(key = "message") { Text(m, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) } }
            if (list.isEmpty()) {
                item(key = "empty") { EmptyLine(stringResource(R.string.bans_empty)) }
            }
            items(list, key = { it.name.lowercase() }) { ban ->
                McCard(Modifier.fillMaxWidth().then(if (LocalAnimations.current) Modifier.animateItem() else Modifier)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        PlayerHead(ban.name, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ban.name, style = MaterialTheme.typography.titleSmall)
                            ban.reason?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            val details = listOfNotNull(
                                ban.created?.let { stringResource(R.string.bans_since, dates.format(Date(it))) },
                                ban.expires?.let { stringResource(R.string.bans_until, dates.format(Date(it))) },
                                ban.source?.takeIf { it != "Server" }?.let { stringResource(R.string.bans_by, it) },
                            ).joinToString(" · ")
                            if (details.isNotEmpty()) {
                                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = { confirm = ban }, enabled = !loader.busy) { Text(stringResource(R.string.bans_unban)) }
                    }
                }
            }
        }
    }

    confirm?.let { ban ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Outlined.Gavel, null) },
            title = { Text(stringResource(R.string.bans_unban_confirm, ban.name)) },
            text = { Text(stringResource(R.string.bans_unban_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    haptics.success()
                    loader.run({ context.getString(R.string.bans_unbanned, ban.name) }) { moderation.pardon(ban.name) }
                }) { Text(stringResource(R.string.bans_unban)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun LoadingOrError(error: Throwable?, retry: () -> Unit) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        if (error == null) {
            CircularProgressIndicator(Modifier.size(32.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(Icons.Outlined.Gavel, stringResource(R.string.moderation_load_failed), context.errorText(error))
                TextButton(onClick = retry) { Text(stringResource(R.string.action_refresh)) }
            }
        }
    }
}

@Composable
private fun ErrorLine(text: String) =
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun EmptyLine(text: String) = Text(
    text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
)
