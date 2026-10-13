package de.remotemc.desktop

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.remotemc.app.data.PluginInfo
import de.remotemc.app.data.Role
import de.remotemc.desktop.res.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Installed plugins with version, authors and whether they are enabled. */
@Composable
fun PluginsPane(session: ServerSession) {
    val manage = session.manage
    val (loader, reload) = rememberLoader { manage.plugins() }
    var query by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<PluginInfo?>(null) }
    // Removed JARs: the plugin keeps running (and stays listed) until the server restarts.
    var removed by remember { mutableStateOf(setOf<String>()) }
    val canManage = session.role.atLeast(Role.ADMIN) && session.supports("files")
    val scope = rememberCoroutineScope()
    val list = loader.data
    val pickTitle = stringResource(Res.string.plugins_add)

    fun add() {
        val file = pickFile(pickTitle, "*.jar") ?: return
        scope.launch {
            if (!file.name.endsWith(".jar", ignoreCase = true)) {
                session.notify(getString(Res.string.plugins_not_jar))
                return@launch
            }
            session.notify(getString(Res.string.files_uploading, file.name))
            try {
                manage.uploadFile("plugins/${file.name}", file)
                session.notify(getString(Res.string.plugins_added, file.name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                session.notify(errorText(e))
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (list == null) {
            LoadingOrError(loader.error, reload)
            return@Column
        }
        Row(Modifier.padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.dashboard_plugins) + " · ${list.size}",
                style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
            )
            SearchField(query, { query = it }, stringResource(Res.string.plugins_search), Modifier.widthIn(max = 340.dp).fillMaxWidth())
            if (canManage) {
                Spacer(Modifier.width(12.dp))
                Button(onClick = ::add) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(pickTitle)
                }
            }
        }
        LazyVerticalGrid(
            GridCells.Adaptive(320.dp), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(list.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }, key = { it.name }) { p ->
                McCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(
                            Icons.Outlined.Extension, null,
                            tint = if (p.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false))
                                Spacer(Modifier.width(8.dp))
                                Text(p.version, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!p.enabled) {
                                    Spacer(Modifier.width(8.dp))
                                    Badge(stringResource(Res.string.plugins_disabled), McColors.Offline)
                                }
                            }
                            p.description?.let {
                                Spacer(Modifier.height(2.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (p.authors.isNotEmpty()) {
                                Text(
                                    stringResource(Res.string.plugins_by, p.authors.joinToString(", ")),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (p.file != null && p.file in removed) {
                                Spacer(Modifier.height(4.dp))
                                Badge(stringResource(Res.string.plugins_removed), McColors.Warning)
                            }
                        }
                        if (canManage && p.file != null && p.file !in removed && p.name != "RemoteMC") {
                            IconButton(onClick = { removing = p }) {
                                Icon(Icons.Outlined.Delete, stringResource(Res.string.plugins_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    removing?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = null },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(stringResource(Res.string.plugins_remove_confirm, p.name)) },
            text = { Text(stringResource(Res.string.plugins_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    val file = p.file ?: return@TextButton
                    scope.launch {
                        try {
                            manage.deleteFile(file)
                            removed = removed + file
                            session.notify(getString(Res.string.files_deleted, file.substringAfterLast('/')))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            session.notify(errorText(e))
                        }
                    }
                }) { Text(stringResource(Res.string.plugins_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}
