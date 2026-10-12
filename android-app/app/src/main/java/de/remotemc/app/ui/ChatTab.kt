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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import de.remotemc.app.data.ChatEntry
import de.remotemc.app.ui.components.EmptyState
import de.remotemc.app.ui.components.PlayerHead
import de.remotemc.app.ui.theme.McColors
import java.text.DateFormat
import java.util.Date

@Composable
fun ChatTab(entries: List<ChatEntry>, canSend: Boolean, onBroadcast: (String) -> Unit, skins: Map<String, String> = emptyMap()) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        if (entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.AutoMirrored.Outlined.Chat, stringResource(R.string.chat_empty_title), stringResource(R.string.chat_empty_text))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { "${it.id}-${it.time}" }) { entry ->
                    when (entry.type) {
                        ChatEntry.Type.JOIN, ChatEntry.Type.QUIT -> SystemLine(entry, Modifier.animateItem())
                        else -> ChatBubble(entry, skins[entry.player], Modifier.animateItem())
                    }
                }
            }
        }
        if (canSend) {
            CommandInput(
                value = input,
                onValueChange = { if (it.length <= 256) input = it },
                placeholder = stringResource(R.string.chat_input_hint),
                onSend = {
                    if (input.isNotBlank()) {
                        onBroadcast(input.trim())
                        input = ""
                    }
                },
            )
        }
    }
}

private val timeFormat: DateFormat get() = DateFormat.getTimeInstance(DateFormat.SHORT)

@Composable
private fun SystemLine(entry: ChatEntry, modifier: Modifier = Modifier) {
    val join = entry.type == ChatEntry.Type.JOIN
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (join) Icons.AutoMirrored.Outlined.Login else Icons.AutoMirrored.Outlined.Logout,
            null,
            Modifier.size(14.dp),
            tint = if (join) McColors.Online else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(if (join) R.string.chat_joined else R.string.chat_left, entry.player) + " · " + timeFormat.format(Date(entry.time)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatBubble(entry: ChatEntry, skin: String?, modifier: Modifier = Modifier) {
    val fromServer = entry.type == ChatEntry.Type.BROADCAST
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (fromServer) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!fromServer) {
            PlayerHead(entry.player, 32.dp, skinUrl = skin)
            Spacer(Modifier.width(8.dp))
        }
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (fromServer) 18.dp else 4.dp,
                        bottomEnd = if (fromServer) 4.dp else 18.dp,
                    ),
                )
                .background(if (fromServer) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                (if (fromServer) stringResource(R.string.chat_from_server, entry.player) else entry.player) +
                    " · " + timeFormat.format(Date(entry.time)),
                style = MaterialTheme.typography.labelSmall,
                color = if (fromServer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
            )
            Text(
                entry.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (fromServer) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
