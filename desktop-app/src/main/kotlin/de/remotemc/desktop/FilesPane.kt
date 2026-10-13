package de.remotemc.desktop

import de.remotemc.desktop.res.files_restored
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.remotemc.app.data.FileEntry
import de.remotemc.app.data.FileList
import de.remotemc.app.data.formatSize
import de.remotemc.app.data.parentPath
import de.remotemc.desktop.res.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.text.DateFormat
import java.util.Date

private val TextTypes = setOf("yml", "yaml", "json", "properties", "txt", "log", "toml", "conf", "cfg", "ini", "md", "sk", "csv", "xml", "html", "js", "sh", "bat")

private fun iconFor(entry: FileEntry): ImageVector = when {
    entry.dir -> Icons.Outlined.Folder
    entry.name.endsWith(".jar", ignoreCase = true) -> Icons.Outlined.Extension
    entry.name.substringAfterLast('.', "").lowercase() in TextTypes -> Icons.Outlined.Description
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

/** Lets the user pick a file on this PC (Windows' own dialog); [filter] e.g. "*.jar". */
fun pickFile(title: String, filter: String? = null): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    if (filter != null) {
        dialog.file = filter
        dialog.setFilenameFilter { _, name -> name.endsWith(filter.removePrefix("*"), ignoreCase = true) }
    }
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

/** The server folder: browse on the left, edit text files on the right (admins). */
@Composable
fun FilesPane(session: ServerSession, initialPath: String = "", initialFile: String? = null) {
    val manage = session.manage
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf(initialPath) }
    var open by remember { mutableStateOf(initialFile) }
    var list by remember(path) { mutableStateOf<FileList?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var deleting by remember { mutableStateOf<FileEntry?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    val dates = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    LaunchedEffect(path, tick) {
        try {
            list = manage.files(path)
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = errorText(e)
        }
    }

    fun run(progress: String, done: suspend () -> String?, call: suspend () -> Unit) {
        scope.launch {
            busy = progress
            try {
                call()
                done()?.let(session::notify)
                tick++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                session.notify(errorText(e))
            } finally {
                busy = null
            }
        }
    }

    val uploadTitle = stringResource(Res.string.files_upload)
    SplitView("files", 0.42f, Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 24.dp), left = {
        McCard(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { path = parentPath(path) }, enabled = path.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.action_back))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(path.substringAfterLast('/').ifEmpty { stringResource(Res.string.files_root) }, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("/$path", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { newFolder = true }, enabled = busy == null) {
                        Icon(Icons.Outlined.CreateNewFolder, stringResource(Res.string.files_new_folder))
                    }
                    IconButton(enabled = busy == null, onClick = {
                        val file = pickFile(uploadTitle) ?: return@IconButton
                        val target = if (path.isEmpty()) file.name else "$path/${file.name}"
                        scope.launch {
                            run(getString(Res.string.files_uploading, file.name), { getString(Res.string.files_uploaded, file.name) }) {
                                manage.uploadFile(target, file)
                            }
                        }
                    }) { Icon(Icons.Outlined.UploadFile, uploadTitle) }
                }
                busy?.let {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                val l = list
                when {
                    l == null && error != null -> Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    l == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                        if (l.entries.isEmpty()) {
                            item { Text(stringResource(Res.string.files_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp)) }
                        }
                        items(l.entries, key = { it.path }) { entry ->
                            val selected = entry.path == open
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { if (entry.dir) path = entry.path else open = entry.path }
                                    .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(iconFor(entry), null, Modifier.size(20.dp),
                                    tint = if (entry.dir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Text(entry.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(
                                    (if (entry.dir) "" else formatSize(entry.size) + "  ·  ") + dates.format(Date(entry.modified)),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                IconButton(onClick = { deleting = entry }, enabled = busy == null) {
                                    Icon(Icons.Outlined.Delete, stringResource(Res.string.files_delete), Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        item {
                            Text(stringResource(Res.string.files_restart_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                        }
                    }
                }
            }
        }
    }, right = {
        McCard(Modifier.fillMaxSize()) {
            val file = open
            if (file == null) {
                EmptyState(stringResource(Res.string.files_title), stringResource(Res.string.files_open_failed))
            } else {
                Editor(session, file, onClosed = { open = null })
            }
        }
    })

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            icon = { Icon(Icons.Outlined.Delete, null) },
            title = { Text(stringResource(Res.string.files_delete_confirm, entry.name)) },
            text = { Text(stringResource(if (entry.dir) Res.string.files_delete_folder_text else Res.string.files_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    if (open?.startsWith(entry.path) == true) open = null
                    scope.launch {
                        busy = "…"
                        try {
                            val trash = manage.deleteFile(entry.path)
                            tick++
                            // Deleted files wait a day in the plugin's trash: offer to take it back.
                            session.notify(
                                getString(Res.string.files_deleted, entry.name),
                                undo = trash?.let { id ->
                                    {
                                        run("…", { getString(Res.string.files_restored, entry.name) }) { manage.restoreFile(id) }
                                    }
                                },
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            session.notify(errorText(e))
                        } finally {
                            busy = null
                        }
                    }
                }) { Text(stringResource(Res.string.files_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }

    if (newFolder) {
        var name by remember { mutableStateOf("") }
        val valid = name.isNotBlank() && name.none { it in "\\/:*?\"<>|" } && name != "." && name != ".."
        AlertDialog(
            onDismissRequest = { newFolder = false },
            icon = { Icon(Icons.Outlined.CreateNewFolder, null) },
            title = { Text(stringResource(Res.string.files_new_folder)) },
            text = { OutlinedTextField(name, { name = it.take(100) }, label = { Text(stringResource(Res.string.files_folder_name)) }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    newFolder = false
                    val target = if (path.isEmpty()) name.trim() else "$path/${name.trim()}"
                    run("…", { null }) { manage.createFolder(target) }
                }) { Text(stringResource(Res.string.files_create)) }
            },
            dismissButton = { TextButton(onClick = { newFolder = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Editor(session: ServerSession, path: String, onClosed: () -> Unit) {
    val manage = session.manage
    val scope = rememberCoroutineScope()
    var original by remember(path) { mutableStateOf<String?>(null) }
    var text by remember(path) { mutableStateOf<String?>(null) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    LaunchedEffect(path) {
        try {
            val file = manage.readFile(path)
            original = file.content
            text = file.content
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = errorText(e)
        }
    }
    val changed = text != null && text != original

    fun save() {
        val content = text ?: return
        if (!changed || saving) return
        scope.launch {
            saving = true
            try {
                manage.writeFile(path, content)
                original = content
                session.notify(getString(Res.string.files_saved))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                session.notify(errorText(e))
            } finally {
                saving = false
            }
        }
    }

    Column(Modifier.fillMaxSize().onPreviewKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.S) { save(); true } else false
    }) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text((if (changed) "• " else "") + path.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("/" + parentPath(path), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (saving) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp).size(22.dp), strokeWidth = 2.dp)
            } else {
                Button(onClick = ::save, enabled = changed) {
                    Icon(Icons.Outlined.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.action_save))
                }
            }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = { if (changed) confirmClose = true else onClosed() }) { Text(stringResource(Res.string.action_close)) }
        }
        val current = text
        when {
            error != null -> Text(error!! + "\n\n" + stringResource(Res.string.files_open_failed), modifier = Modifier.padding(16.dp))
            current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> Box(
                Modifier.fillMaxSize().padding(8.dp).clip(RoundedCornerShape(12.dp)).background(McColors.Terminal)
                    .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
            ) {
                BasicTextField(
                    value = current,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFDDE6E0)),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(stringResource(Res.string.files_unsaved)) },
            confirmButton = { TextButton(onClick = { confirmClose = false; onClosed() }) { Text(stringResource(Res.string.files_discard)) } },
            dismissButton = { TextButton(onClick = { confirmClose = false; save() }) { Text(stringResource(Res.string.action_save)) } },
        )
    }
}
