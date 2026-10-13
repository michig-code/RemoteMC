package de.remotemc.desktop

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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.BanInfo
import de.remotemc.app.data.Role
import de.remotemc.desktop.res.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.text.DateFormat
import java.util.Date

@Composable
fun WhitelistView(session: ServerSession) {
    val manage = session.manage
    val (loader, reload) = rememberLoader { manage.whitelist() }
    var name by remember { mutableStateOf("") }
    val canToggle = session.role.atLeast(Role.ADMIN)
    val list = loader.data

    fun add() {
        val n = name.trim()
        if (n.isEmpty() || loader.busy) return
        loader.run({ name = ""; session.notify(getString(Res.string.whitelist_added, n)) }) { manage.whitelistAdd(n) }
    }

    ListColumn {
        if (list == null) {
            item { LoadingOrError(loader.error, reload) }
            return@ListColumn
        }
        item(key = "switch") {
            McCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.whitelist_enabled), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (list.enabled) Res.string.whitelist_enabled_text else Res.string.whitelist_disabled_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(list.enabled, onCheckedChange = { on -> loader.run { manage.setWhitelist(on) } }, enabled = canToggle && !loader.busy)
                }
            }
        }
        item(key = "add") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { v -> name = v.filter { it.isLetterOrDigit() || it == '_' || it == '.' }.take(16) },
                    label = { Text(stringResource(Res.string.whitelist_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Button(onClick = ::add, enabled = name.isNotBlank() && !loader.busy) { Text(stringResource(Res.string.whitelist_add)) }
            }
        }
        loader.error?.let { e -> item(key = "error") { ErrorLine(e) } }
        if (list.players.isEmpty()) item(key = "empty") { EmptyLine(stringResource(Res.string.whitelist_empty)) }
        items(list.players, key = { it.lowercase() }) { player ->
            McCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayerHead(player, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(player, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { loader.run({ session.notify(getString(Res.string.whitelist_removed, player)) }) { manage.whitelistRemove(player) } },
                        enabled = !loader.busy,
                    ) { Icon(Icons.Outlined.Close, stringResource(Res.string.whitelist_remove, player)) }
                }
            }
        }
    }
}

@Composable
fun BansView(session: ServerSession) {
    val manage = session.manage
    val (loader, reload) = rememberLoader { manage.bans() }
    var confirm by remember { mutableStateOf<BanInfo?>(null) }
    val list = loader.data
    val dates = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    ListColumn {
        if (list == null) {
            item { LoadingOrError(loader.error, reload) }
            return@ListColumn
        }
        loader.error?.let { e -> item(key = "error") { ErrorLine(e) } }
        if (list.isEmpty()) item(key = "empty") { EmptyLine(stringResource(Res.string.bans_empty)) }
        items(list, key = { it.name.lowercase() }) { ban ->
            McCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayerHead(ban.name, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ban.name, style = MaterialTheme.typography.titleSmall)
                        ban.reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        val details = listOfNotNull(
                            ban.created?.let { stringResource(Res.string.bans_since, dates.format(Date(it))) },
                            ban.expires?.let { stringResource(Res.string.bans_until, dates.format(Date(it))) },
                            ban.source?.takeIf { it != "Server" }?.let { stringResource(Res.string.bans_by, it) },
                        ).joinToString(" · ")
                        if (details.isNotEmpty()) {
                            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { confirm = ban }, enabled = !loader.busy) { Text(stringResource(Res.string.bans_unban)) }
                }
            }
        }
    }

    confirm?.let { ban ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            icon = { Icon(Icons.Outlined.Gavel, null) },
            title = { Text(stringResource(Res.string.bans_unban_confirm, ban.name)) },
            text = { Text(stringResource(Res.string.bans_unban_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    loader.run({ session.notify(getString(Res.string.bans_unbanned, ban.name)) }) { manage.pardon(ban.name) }
                }) { Text(stringResource(Res.string.bans_unban)) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** A centred list of limited width, as used by the management views. */
@Composable
fun ListColumn(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun LoadingOrError(error: String?, retry: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        if (error == null) {
            CircularProgressIndicator(Modifier.size(32.dp))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(Res.string.moderation_load_failed), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(error, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                TextButton(onClick = retry) { Text(stringResource(Res.string.action_refresh)) }
            }
        }
    }
}

@Composable
fun ErrorLine(text: String) = Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)

@Composable
fun EmptyLine(text: String) = Text(
    text,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    style = MaterialTheme.typography.bodyMedium,
    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    textAlign = TextAlign.Center,
)
