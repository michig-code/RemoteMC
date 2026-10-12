package de.remotemc.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.remotemc.app.ui.theme.McColors
import de.remotemc.app.ui.theme.MonoStyle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val quickCommands = listOf("list", "time set day", "weather clear", "save-all", "difficulty", "whitelist list")
private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)

@Composable
fun ConsoleTab(entries: List<ConsoleEntry>, onCommand: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    // Follow new output while the user is at the bottom.
    var initialScrollDone by remember { mutableStateOf(false) }
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty() && (atBottom || !initialScrollDone)) {
            listState.scrollToItem(entries.size - 1)
            initialScrollDone = true
        }
    }

    fun send() {
        if (input.isBlank()) return
        onCommand(input)
        input = ""
    }

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp)
                .clip(MaterialTheme.shapes.large)
                .background(McColors.Terminal),
        ) {
            if (entries.isEmpty()) {
                Text(
                    "Warte auf Konsolenausgabe…",
                    style = MonoStyle,
                    color = Color(0xFF6B7A70),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(entries, key = { it.key }) { line -> ConsoleLineView(line) }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = !atBottom && entries.isNotEmpty(),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(entries.size - 1) } },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) { Icon(Icons.Default.KeyboardArrowDown, "Nach unten") }
            }
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickCommands.forEach { cmd ->
                AssistChip(
                    onClick = { onCommand(cmd) },
                    label = { Text("/$cmd", fontFamily = FontFamily.Monospace) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = null,
                )
            }
        }

        CommandInput(
            value = input,
            onValueChange = { input = it },
            placeholder = "Befehl eingeben…",
            prefix = "/",
            onSend = ::send,
        )
    }
}

@Composable
private fun ConsoleLineView(line: ConsoleEntry) {
    val color = when (line.level) {
        "WARN" -> Color(0xFFFFC857)
        "ERROR", "FATAL" -> Color(0xFFFF6B6B)
        "COMMAND" -> Color(0xFF7CC4FF)
        "OUTPUT" -> Color(0xFF8BE9A8)
        else -> Color(0xFFD5DDD8)
    }
    // Timestamp in its own column so wrapped lines stay indented under the message.
    Row(Modifier.padding(vertical = 1.dp)) {
        Text(timeFormat.format(Date(line.time)), style = MonoStyle, color = Color(0xFF56635A))
        Spacer(Modifier.width(10.dp))
        Text(line.text, style = MonoStyle, color = color, modifier = Modifier.weight(1f))
    }
}

/** Rounded input bar with a send button, shared by console and chat. */
@Composable
fun CommandInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSend: () -> Unit,
    prefix: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
            prefix = prefix?.let { { Text(it, color = MaterialTheme.colorScheme.primary) } },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        FilledIconButton(onClick = onSend, enabled = value.isNotBlank(), modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.AutoMirrored.Filled.Send, "Senden")
        }
    }
}
