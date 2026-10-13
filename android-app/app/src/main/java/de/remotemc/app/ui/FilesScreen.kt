package de.remotemc.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.remotemc.app.R
import de.remotemc.app.data.FileEntry
import de.remotemc.app.data.FileList
import de.remotemc.app.data.formatSize
import de.remotemc.app.data.parentPath
import de.remotemc.app.ui.components.McCard
import de.remotemc.app.ui.util.errorText
import de.remotemc.app.ui.util.rememberHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Card on the overview that opens the server folder. */
@Composable
internal fun FilesRow(onClick: () -> Unit) {
    McCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.files_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Full-screen file manager for the server folder: browse, edit text files, upload, delete. */
@Composable
internal fun FilesScreen(
    actions: FileActions,
    onDismiss: () -> Unit,
    initialPath: String = "",
    initialFile: String? = null,
) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var path by rememberSaveable { mutableStateOf(initialPath) }
        var file by rememberSaveable { mutableStateOf(initialFile) }
        val open = file
        if (open != null) {
            FileEditor(actions, open) { file = null }
        } else {
            FolderView(actions, path, onOpenFolder = { path = it }, onOpenFile = { file = it }, onClose = onDismiss)
        }
    }
}

private fun iconFor(entry: FileEntry): ImageVector = when {
    entry.dir -> Icons.Outlined.Folder
    entry.name.endsWith(".jar", ignoreCase = true) -> Icons.Outlined.Extension
    entry.name.substringAfterLast('.', "").lowercase() in TextTypes -> Icons.Outlined.Description
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

/** Keeps the end of a long path, where the interesting part is. */
private fun shortPath(path: String, max: Int = 42) = if (path.length <= max) path else "…" + path.takeLast(max - 1)

private val TextTypes = setOf("yml", "yaml", "json", "properties", "txt", "log", "toml", "conf", "cfg", "ini", "md", "sk", "csv", "xml", "html", "js", "sh", "bat")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderView(
    actions: FileActions,
    path: String,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var list by remember(path) { mutableStateOf<FileList?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<FileEntry?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    val dates = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    LaunchedEffect(path, tick) {
        try {
            list = actions.list(path)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = context.errorText(e)
        }
    }

    fun run(progress: String, done: (String) -> String?, call: suspend () -> String) {
        scope.launch {
            busy = progress
            try {
                val result = call()
                haptics.success()
                done(result)?.let { snackbar.showSnackbar(it) }
                tick++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                haptics.error()
                snackbar.showSnackbar(context.errorText(e))
            } finally {
                busy = null
            }
        }
    }

    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val name = runCatching { actions.nameOf(uri) }.getOrDefault("…")
                run(context.getString(R.string.files_uploading, name), { context.getString(R.string.files_uploaded, name) }) {
                    actions.upload(path, uri)
                }
            }
        }
    }

    BackHandler { if (path.isEmpty()) onClose() else onOpenFolder(parentPath(path)) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { if (path.isEmpty()) onClose() else onOpenFolder(parentPath(path)) }) {
                        Icon(
                            if (path.isEmpty()) Icons.Outlined.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(if (path.isEmpty()) R.string.action_close else R.string.action_back),
                        )
                    }
                },
                title = {
                    Column {
                        Text(path.substringAfterLast('/').ifEmpty { stringResource(R.string.files_root) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            shortPath("/" + path), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Clip,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { newFolder = true }, enabled = busy == null) {
                        Icon(Icons.Outlined.CreateNewFolder, stringResource(R.string.files_new_folder))
                    }
                    IconButton(onClick = { upload.launch(arrayOf("*/*")) }, enabled = busy == null) {
                        Icon(Icons.Outlined.UploadFile, stringResource(R.string.files_upload))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            busy?.let { text ->
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            val l = list
            when {
                l == null && error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (l.entries.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(R.string.files_empty), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    }
                    items(l.entries, key = { it.path }) { entry ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (entry.dir) onOpenFolder(entry.path) else onOpenFile(entry.path) }
                                .padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                iconFor(entry), null,
                                tint = if (entry.dir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    (if (entry.dir) "" else formatSize(entry.size) + " · ") + dates.format(Date(entry.modified)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { deleting = entry }, enabled = busy == null) {
                                Icon(Icons.Outlined.Delete, stringResource(R.string.files_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item(key = "hint") {
                        Text(
                            stringResource(R.string.files_restart_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        )
                    }
                }
            }
        }
    }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(stringResource(R.string.files_delete_confirm, entry.name)) },
            text = { Text(stringResource(if (entry.dir) R.string.files_delete_folder_text else R.string.files_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    run("…", { context.getString(R.string.files_deleted, entry.name) }) { actions.delete(entry.path); entry.path }
                }) { Text(stringResource(R.string.files_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (newFolder) {
        var name by remember { mutableStateOf("") }
        val valid = name.isNotBlank() && name.none { it in "\\/:*?\"<>|" } && name != "." && name != ".."
        AlertDialog(
            onDismissRequest = { newFolder = false },
            icon = { Icon(Icons.Outlined.CreateNewFolder, null) },
            title = { Text(stringResource(R.string.files_new_folder)) },
            text = {
                OutlinedTextField(name, { name = it.take(100) }, label = { Text(stringResource(R.string.files_folder_name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    newFolder = false
                    val target = if (path.isEmpty()) name.trim() else "$path/${name.trim()}"
                    run("…", { null }) { actions.createFolder(target); target }
                }) { Text(stringResource(R.string.files_create)) }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileEditor(actions: FileActions, path: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var original by remember(path) { mutableStateOf<String?>(null) }
    var text by rememberSaveable(path) { mutableStateOf<String?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        try {
            val file = actions.read(path)
            original = file.content
            if (text == null) text = file.content
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = context.errorText(e) + "\n\n" + context.getString(R.string.files_open_failed)
        }
    }
    val changed = text != null && text != original

    fun close() = if (changed) confirmClose = true else onClose()

    fun save() {
        val content = text ?: return
        scope.launch {
            saving = true
            try {
                actions.write(path, content)
                original = content
                haptics.success()
                snackbar.showSnackbar(context.getString(R.string.files_saved))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                haptics.error()
                snackbar.showSnackbar(context.errorText(e))
            } finally {
                saving = false
            }
        }
    }

    BackHandler { close() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = ::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                title = {
                    Column {
                        Text((if (changed) "• " else "") + path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            shortPath("/" + parentPath(path)), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Clip,
                        )
                    }
                },
                actions = {
                    if (saving) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = ::save, enabled = changed) { Icon(Icons.Outlined.Save, stringResource(R.string.action_save)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = text
        when {
            error != null -> Text(error!!, modifier = Modifier.padding(padding).padding(16.dp))
            current == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> Box(
                Modifier.fillMaxSize().padding(padding).imePadding()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState()),
            ) {
                // Config files keep their line structure: no wrapping, scroll sideways for long lines.
                BasicTextField(
                    value = current,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(stringResource(R.string.files_unsaved)) },
            confirmButton = {
                TextButton(onClick = { confirmClose = false; onClose() }) { Text(stringResource(R.string.files_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false; save() }) { Text(stringResource(R.string.action_save)) }
            },
        )
    }
}
