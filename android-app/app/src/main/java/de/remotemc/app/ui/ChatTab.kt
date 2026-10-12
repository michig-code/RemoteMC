package de.remotemc.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.remotemc.app.ui.components.EmptyState
import de.remotemc.app.ui.components.PlayerHead
import de.remotemc.app.ui.components.staggeredAppear
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ChatMessage(val key: String, val sender: String?, val text: String, val time: Long)

// "<Steve> hi", "[Not Secure] <Steve> hi", "[Server] hi"
private val playerChat = Regex("^(?:\\[Not Secure] )?<([^>]+)> (.*)$")
private val serverChat = Regex("^\\[Server] (.*)$")
private val chatTime = SimpleDateFormat("HH:mm", Locale.GERMANY)

private fun ConsoleEntry.toChat(): ChatMessage? {
    if (level == "COMMAND" || level == "OUTPUT") return null
    playerChat.find(text)?.let { return ChatMessage(key, it.groupValues[1], it.groupValues[2], time) }
    serverChat.find(text)?.let { return ChatMessage(key, null, it.groupValues[1], time) }
    return null
}

@Composable
fun ChatTab(entries: List<ConsoleEntry>, onBroadcast: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val chat = remember(entries.size) { entries.mapNotNull { it.toChat() } }
    val listState = rememberLazyListState()
    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        if (chat.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.AutoMirrored.Outlined.Chat, "Noch ruhig hier", "Chatnachrichten der Spieler erscheinen hier live.")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(chat, key = { it.key }) { msg -> ChatBubble(msg, Modifier.staggeredAppear(0)) }
            }
        }
        CommandInput(
            value = input,
            onValueChange = { input = it },
            placeholder = "Nachricht an alle…",
            onSend = {
                if (input.isNotBlank()) {
                    onBroadcast(input)
                    input = ""
                }
            },
        )
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, modifier: Modifier = Modifier) {
    val fromServer = msg.sender == null
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (fromServer) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!fromServer) {
            PlayerHead(msg.sender!!, 32.dp)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (fromServer) 18.dp else 4.dp,
                        bottomEnd = if (fromServer) 4.dp else 18.dp,
                    ),
                )
                .background(
                    if (fromServer) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                (msg.sender ?: "Server") + " · " + chatTime.format(Date(msg.time)),
                style = MaterialTheme.typography.labelSmall,
                color = if (fromServer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
            )
            Text(
                msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (fromServer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
