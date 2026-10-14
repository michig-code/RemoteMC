package de.remotemc.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import de.remotemc.desktop.res.menu_open
import de.remotemc.desktop.res.macro_stop
import de.remotemc.desktop.res.macro_running
import de.remotemc.desktop.res.console_suggestions_offline
import de.remotemc.desktop.res.console_no_lines
import de.remotemc.desktop.res.console_copy
import de.remotemc.desktop.res.console_copied
import de.remotemc.desktop.res.console_filter_errors
import de.remotemc.desktop.res.console_filter_warnings
import de.remotemc.desktop.res.console_filter_all
import de.remotemc.desktop.res.console_match_next
import de.remotemc.desktop.res.console_match_previous
import de.remotemc.desktop.res.console_no_matches
import de.remotemc.desktop.res.console_search
import de.remotemc.app.ui.completeAt
import de.remotemc.app.ui.LevelFilter
import de.remotemc.app.ui.ConsoleSearch
import de.remotemc.app.ui.ConsoleEntry
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.remotemc.app.data.ChatEntry
import de.remotemc.app.data.Role
import de.remotemc.app.ui.util.MinecraftText
import de.remotemc.desktop.res.*
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Date

private val TimeFormat = SimpleDateFormat("HH:mm:ss")

private fun levelColor(level: String) = when (level) {
    "WARN", "WARNING" -> McColors.Warning
    "ERROR", "FATAL", "SEVERE" -> McColors.Offline
    "COMMAND" -> Color(0xFF8BE9FD)
    else -> Color(0xFFD7DED9)
}

@Composable
fun ConsolePane(session: ServerSession, initialQuery: String = "", initialLevel: LevelFilter = LevelFilter.ALL, initialMenu: Int? = null) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val canRun = session.role.atLeast(Role.ADMIN) && session.session?.commandsEnabled != false
    val copiedText = stringResource(Res.string.console_copied)
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var historyIndex by remember { mutableIntStateOf(-1) }
    var menuOpen by remember { mutableStateOf(initialMenu != null) }
    // Search highlights and jumps, the level filter hides lines.
    var query by remember { mutableStateOf(initialQuery) }
    var level by remember { mutableStateOf(initialLevel) }
    val searchFocus = remember { FocusRequester() }
    val shown = remember(session.console.size, session.console.lastOrNull()?.key, level) { ConsoleSearch.filter(session.console, level) }
    val matches = remember(shown, query) { ConsoleSearch.matches(shown, query) }
    var current by remember(query, level) { mutableIntStateOf(-1) }
    val selected = if (matches.isEmpty()) -1 else if (current in matches.indices) current else matches.lastIndex
    val selectedKey = matches.getOrNull(selected)?.let { shown[it].key }

    // New lines scroll into view only while the log is at its end; scrolling up pauses that.
    val atBottom by remember {
        derivedStateOf {
            val info = list.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 1
        }
    }
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        snapshotFlow { atBottom }.collect { if (list.isScrollInProgress) follow = it }
    }
    LaunchedEffect(shown.size) {
        if (query.isBlank() && follow && shown.isNotEmpty()) list.scrollToItem(shown.size - 1)
    }
    LaunchedEffect(selected, matches.size, query) {
        matches.getOrNull(selected)?.let { list.animateScrollToItem((it - 3).coerceAtLeast(0)) }
    }

    fun setInput(text: String) {
        input = TextFieldValue(text, TextRange(text.length))
        session.onCommandInput(text)
    }

    /** Puts the suggestion in place of the word at the cursor (like Tab in Minecraft). */
    fun accept(text: String) {
        input = completeAt(input, text)
        session.onCommandInput(input.text.take(input.selection.end))
    }

    fun send() {
        session.runCommand(input.text)
        input = TextFieldValue("")
        historyIndex = -1
    }

    fun step(by: Int) {
        if (matches.isNotEmpty()) current = (selected + by + matches.size) % matches.size
    }

    Column(
        Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 16.dp).onPreviewKeyEvent { e ->
            // Ctrl+F: search the console.
            if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.F) {
                scope.launch { runCatching { searchFocus.requestFocus() } }
                true
            } else false
        },
    ) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it },
                placeholder = { Text(stringResource(Res.string.console_search)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    when {
                        matches.isNotEmpty() -> Text("${selected + 1}/${matches.size}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 12.dp))
                        query.isNotBlank() -> Text(stringResource(Res.string.console_no_matches), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 12.dp))
                    }
                },
                singleLine = true,
                modifier = Modifier.width(360.dp).focusRequester(searchFocus).onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Enter -> { step(if (e.isShiftPressed) 1 else -1); true }
                        Key.Escape -> { query = ""; true }
                        else -> false
                    }
                },
            )
            IconButton(onClick = { step(-1) }, enabled = matches.isNotEmpty()) { Icon(Icons.Default.KeyboardArrowUp, stringResource(Res.string.console_match_previous)) }
            IconButton(onClick = { step(1) }, enabled = matches.isNotEmpty()) { Icon(Icons.Default.KeyboardArrowDown, stringResource(Res.string.console_match_next)) }
            Spacer(Modifier.width(8.dp))
            LevelFilter.entries.forEach { f ->
                FilterChip(
                    selected = level == f, onClick = { level = f },
                    label = {
                        Text(stringResource(when (f) {
                            LevelFilter.ALL -> Res.string.console_filter_all
                            LevelFilter.WARNINGS -> Res.string.console_filter_warnings
                            LevelFilter.ERRORS -> Res.string.console_filter_errors
                        }))
                    },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            // Copies what is found or shown (the PC's way of "share").
            IconButton(onClick = {
                val lines = if (query.isBlank()) shown else matches.map { shown[it] }
                if (lines.isNotEmpty()) {
                    clipboard.setText(AnnotatedString(ConsoleSearch.shareText(lines)))
                    session.notify(copiedText)
                }
            }) { Icon(Icons.Outlined.ContentCopy, stringResource(Res.string.console_copy)) }
        }
        Surface(Modifier.weight(1f).fillMaxWidth(), color = McColors.Terminal, shape = RoundedCornerShape(16.dp)) {
            Box {
                SelectionContainer {
                    LazyColumn(state = list, contentPadding = PaddingValues(16.dp)) {
                        if (shown.isEmpty()) {
                            item {
                                Text(
                                    stringResource(if (session.console.isEmpty()) Res.string.console_waiting else Res.string.console_no_lines),
                                    color = Color.Gray, fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                        items(shown, key = { it.key }) { e -> ConsoleRow(e, query, e.key == selectedKey) }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = !atBottom && shown.isNotEmpty(),
                    enter = fadeIn(), exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            follow = true
                            scope.launch { list.animateScrollToItem((shown.size - 1).coerceAtLeast(0)) }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) { Icon(Icons.Default.KeyboardArrowDown, stringResource(Res.string.console_scroll_down)) }
                }
            }
        }
        if (!canRun) {
            Text(stringResource(Res.string.console_read_only), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            return@Column
        }
        if (session.suggestions.isNotEmpty() || session.usageHint != null || session.suggestionsOffline) {
            Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.heightIn(max = 220.dp).padding(vertical = 6.dp)) {
                    session.usageHint?.let { Text(it, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) }
                    if (session.suggestionsOffline) {
                        Text(stringResource(Res.string.console_suggestions_offline), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
                    }
                    LazyColumn {
                        items(session.suggestions) { s ->
                            Row(Modifier.fillMaxWidth().clickable { accept(s.text) }.padding(horizontal = 14.dp, vertical = 5.dp)) {
                                Text(s.text, fontFamily = FontFamily.Monospace)
                                s.description?.let { Text("  $it", color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                            }
                        }
                    }
                }
            }
        }
        session.runningMacro?.let { name ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.macro_running, name), color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = session::stopMacro) { Text(stringResource(Res.string.macro_stop)) }
            }
        }
        if (session.history.isNotEmpty()) {
            LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(session.history, key = { it }) { cmd ->
                    AssistChip(
                        onClick = { setInput(cmd) },
                        label = { Text("/$cmd", fontFamily = FontFamily.Monospace) },
                        // The ✕ forgets the command (long press on the phone).
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Close, stringResource(Res.string.console_recent_forget),
                                Modifier.size(16.dp).clip(CircleShape).clickable { session.forgetCommand(cmd) },
                            )
                        },
                    )
                }
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                input,
                {
                    // Moving the cursor to another word changes the suggestions too.
                    val changed = it.text != input.text || it.selection.end != input.selection.end
                    input = it
                    historyIndex = -1
                    if (changed) session.onCommandInput(it.text.take(it.selection.end))
                },
                placeholder = { Text(stringResource(Res.string.console_input_hint)) },
                prefix = { Text("/", color = MaterialTheme.colorScheme.primary) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Enter -> { send(); true }
                        Key.Tab -> { session.suggestions.firstOrNull()?.let { accept(it.text) }; true }
                        Key.DirectionUp -> {
                            val h = session.history
                            if (h.isEmpty()) false else {
                                historyIndex = (historyIndex + 1).coerceAtMost(h.size - 1)
                                val t = h[historyIndex]; input = TextFieldValue(t, TextRange(t.length)); true
                            }
                        }
                        Key.DirectionDown -> {
                            historyIndex = (historyIndex - 1).coerceAtLeast(-1)
                            val t = session.history.getOrNull(historyIndex).orEmpty(); input = TextFieldValue(t, TextRange(t.length)); true
                        }
                        else -> false
                    }
                },
            )
            Spacer(Modifier.width(4.dp))
            // Book: all commands, favorites and macros.
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, stringResource(Res.string.menu_open))
            }
            IconButton(onClick = ::send, enabled = input.text.isNotBlank(), modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Send, stringResource(Res.string.action_send))
            }
        }
    }

    if (menuOpen) {
        CommandMenuSheet(
            menu = CommandMenuActions(
                commands = session.commands,
                shortcuts = session.shortcuts,
                players = session.players.map { it.name },
                runningMacro = session.runningMacro,
                onShortcuts = session::updateShortcuts,
                onRunMacro = session::runMacro,
                onStopMacro = session::stopMacro,
            ),
            currentInput = input.text,
            onInsert = { setInput(it) },
            onRun = { session.runCommand(it) },
            onDismiss = { menuOpen = false },
            initialTab = initialMenu ?: 0,
        )
    }
}

/** Parsed once per line and kept while the line is in the log. */
private val parsedLines = object : LinkedHashMap<String, AnnotatedString>(2048, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AnnotatedString>?) = size > 4000
}

@Composable
private fun ConsoleRow(e: ConsoleEntry, highlight: String, current: Boolean) {
    val base = levelColor(e.level)
    val plain = remember(e.key, e.formatted) {
        synchronized(parsedLines) {
            parsedLines.getOrPut(e.key) { MinecraftText.parse(e.formatted ?: e.text, base) }
        }
    }
    val text = remember(plain, highlight, current) {
        val ranges = ConsoleSearch.ranges(plain.text, highlight)
        if (ranges.isEmpty()) plain else AnnotatedString.Builder(plain).apply {
            val style = SpanStyle(
                background = if (current) Color(0xFFFFC857) else Color(0x66FFC857),
                color = if (current) Color(0xFF1B1B1B) else Color.Unspecified,
            )
            ranges.forEach { addStyle(style, it.first, it.last + 1) }
        }.toAnnotatedString()
    }
    Row {
        if (e.time > 0) Text(TimeFormat.format(Date(e.time)) + "  ", color = Color(0xFF6B7A71), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
fun ChatPane(session: ServerSession) {
    val list = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    val canChat = session.role.atLeast(Role.MODERATOR)
    LaunchedEffect(session.chat.size) { if (session.chat.isNotEmpty()) list.scrollToItem(session.chat.size - 1) }
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
        if (session.chat.isEmpty()) {
            Box(Modifier.weight(1f)) { EmptyState(stringResource(Res.string.chat_empty_title), stringResource(Res.string.chat_empty_text)) }
        } else {
            // The list takes the free height, so the input stays at the bottom with few messages.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        // Real skins for players who are online (like on the phone).
                        val skins = session.players.associate { it.name to it.skin }
                        items(session.chat, key = { "${it.id}-${it.time}" }) { e -> ChatRow(e, skins[e.player]) }
                    }
                }
            }
        }
        if (canChat) {
            Row {
                OutlinedTextField(
                    text, { if (it.length <= 256) text = it },
                    placeholder = { Text(stringResource(Res.string.chat_input_hint)) }, singleLine = true,
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && text.isNotBlank()) { session.broadcast(text); text = ""; true } else false
                    },
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { session.broadcast(text); text = "" }, enabled = text.isNotBlank(), modifier = Modifier.padding(top = 6.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Send, stringResource(Res.string.action_send))
                }
            }
        }
    }
}

@Composable
private fun ChatRow(e: ChatEntry, skin: String? = null) {
    val time = TimeFormat.format(Date(e.time)).dropLast(3)
    when (e.type) {
        ChatEntry.Type.JOIN, ChatEntry.Type.QUIT -> Text(
            "$time  " + stringResource(if (e.type == ChatEntry.Type.JOIN) Res.string.chat_joined else Res.string.chat_left, e.player),
            color = if (e.type == ChatEntry.Type.JOIN) McColors.Online else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        else -> Row {
            PlayerHead(e.player.ifBlank { "Server" }, 32.dp, skin)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    if (e.type == ChatEntry.Type.BROADCAST) stringResource(Res.string.chat_from_server, time) else "${e.player} · $time",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                )
                Text(MinecraftText.parse(e.message, MaterialTheme.colorScheme.onSurface))
            }
        }
    }
}
