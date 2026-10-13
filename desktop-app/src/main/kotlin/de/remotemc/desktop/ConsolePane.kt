package de.remotemc.desktop

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
    "WARN" -> McColors.Warning
    "ERROR" -> McColors.Offline
    "COMMAND" -> Color(0xFF8BE9FD)
    else -> Color(0xFFD7DED9)
}

@Composable
fun ConsolePane(session: ServerSession) {
    val list = rememberLazyListState()
    val canRun = session.role.atLeast(Role.ADMIN) && session.session?.commandsEnabled != false
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var historyIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(session.console.size) {
        if (session.console.isNotEmpty()) list.scrollToItem(session.console.size - 1)
    }

    fun accept(text: String) {
        val current = input.text.removePrefix("/")
        val before = current.substringBeforeLast(' ', "")
        val next = (if (before.isEmpty()) text else "$before $text") + " "
        input = TextFieldValue(next, TextRange(next.length))
        session.onCommandInput(next)
    }

    fun send() {
        session.runCommand(input.text)
        input = TextFieldValue("")
        historyIndex = -1
    }

    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
        Surface(Modifier.weight(1f).fillMaxWidth(), color = McColors.Terminal, shape = RoundedCornerShape(16.dp)) {
            SelectionContainer {
                LazyColumn(state = list, contentPadding = PaddingValues(16.dp)) {
                    if (session.console.isEmpty()) item { Text(stringResource(Res.string.console_waiting), color = Color.Gray, fontFamily = FontFamily.Monospace) }
                    items(session.console, key = { it.key }) { e ->
                        val base = levelColor(e.level)
                        Row {
                            if (e.time > 0) Text(TimeFormat.format(Date(e.time)) + "  ", color = Color(0xFF6B7A71), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            Text(
                                if (e.formatted != null) MinecraftText.parse(e.formatted!!, base) else MinecraftText.parse(e.text, base),
                                fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }
        }
        if (!canRun) {
            Text(stringResource(Res.string.console_read_only), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            return@Column
        }
        if (session.suggestions.isNotEmpty() || session.usageHint != null) {
            Surface(Modifier.fillMaxWidth().padding(top = 8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.heightIn(max = 220.dp).padding(vertical = 6.dp)) {
                    session.usageHint?.let { Text(it, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) }
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
        if (session.history.isNotEmpty()) {
            LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(session.history) { cmd ->
                    AssistChip(onClick = { input = TextFieldValue(cmd, TextRange(cmd.length)); session.onCommandInput(cmd) }, label = { Text("/$cmd", fontFamily = FontFamily.Monospace) })
                }
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                input,
                { input = it; historyIndex = -1; session.onCommandInput(it.text) },
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
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = ::send, enabled = input.text.isNotBlank(), modifier = Modifier.padding(top = 6.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Send, stringResource(Res.string.action_send))
            }
        }
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
                        items(session.chat, key = { "${it.id}-${it.time}" }) { e -> ChatRow(e) }
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
private fun ChatRow(e: ChatEntry) {
    val time = TimeFormat.format(Date(e.time)).dropLast(3)
    when (e.type) {
        ChatEntry.Type.JOIN, ChatEntry.Type.QUIT -> Text(
            "$time  " + stringResource(if (e.type == ChatEntry.Type.JOIN) Res.string.chat_joined else Res.string.chat_left, e.player),
            color = if (e.type == ChatEntry.Type.JOIN) McColors.Online else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        else -> Row {
            PlayerHead(e.player.ifBlank { "Server" }, 32.dp)
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
