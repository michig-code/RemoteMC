package de.remotemc.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import de.remotemc.app.data.PluginInfo
import de.remotemc.app.ui.components.Badge
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.theme.McColors
import de.remotemc.app.ui.util.errorText
import de.remotemc.app.ui.util.rememberHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Card on the overview that opens the plugin list. */
@Composable
internal fun PluginsRow(onClick: () -> Unit) {
    McCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Extension, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.dashboard_plugins), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Installed plugins with version, authors and whether they are enabled; admins can add and remove them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginsSheet(load: suspend () -> List<PluginInfo>, files: FileActions? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var plugins by remember { mutableStateOf<List<PluginInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<PluginInfo?>(null) }
    // Removed JARs: the plugin keeps running (and stays in the list) until the server restarts.
    var removed by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) {
        try {
            plugins = load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = context.errorText(e)
        }
    }

    fun run(call: suspend () -> String) {
        scope.launch {
            busy = true
            try {
                info = call()
                error = null
                haptics.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = context.errorText(e)
                haptics.error()
            } finally {
                busy = false
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && files != null) {
            run {
                val name = files.nameOf(uri)
                if (!name.endsWith(".jar", ignoreCase = true)) throw IllegalArgumentException(context.getString(R.string.plugins_not_jar))
                files.upload("plugins", uri)
                context.getString(R.string.plugins_added, name)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "title") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.dashboard_plugins) + (plugins?.let { " · ${it.size}" } ?: ""),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (files != null) {
                        FilledTonalButton(onClick = { pick.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*")) }) {
                            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.plugins_add))
                        }
                    }
                }
            }
            error?.let { item(key = "error") { Text(it, color = MaterialTheme.colorScheme.error) } }
            info?.let { item(key = "info") { Text(it, color = MaterialTheme.colorScheme.primary) } }
            val list = plugins
            if (list == null) {
                if (error == null) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
                return@LazyColumn
            }
            if (list.size > 6) {
                item(key = "search") {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.plugins_search)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(list.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }, key = { it.name }) { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(8.dp))
                            Text(p.version, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            if (!p.enabled) {
                                Spacer(Modifier.width(8.dp))
                                Badge(stringResource(R.string.plugins_disabled), McColors.Offline)
                            }
                        }
                        if (p.file != null && p.file in removed) {
                            Badge(stringResource(R.string.plugins_removed), McColors.Warning)
                        }
                        p.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (p.authors.isNotEmpty()) {
                            Text(
                                stringResource(R.string.plugins_by, p.authors.joinToString(", ")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (files != null && p.file != null && p.file !in removed && p.name != "RemoteMC") {
                        IconButton(onClick = { removing = p }, enabled = !busy) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.plugins_remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            title = { Text(stringResource(R.string.plugins_remove_confirm, p.name)) },
            text = { Text(stringResource(R.string.plugins_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    val file = p.file ?: return@TextButton
                    run {
                        files?.delete?.invoke(file)
                        removed = removed + file
                        context.getString(R.string.files_deleted, file.substringAfterLast('/'))
                    }
                }) { Text(stringResource(R.string.plugins_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
