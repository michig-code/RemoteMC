package de.mcremote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import de.mcremote.app.data.PlayerInfo
import de.mcremote.app.data.ServerStatus
import org.json.JSONObject

private enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Übersicht", Icons.Default.Dashboard),
    Players("Spieler", Icons.Default.Group),
    Console("Konsole", Icons.Default.Terminal),
    Chat("Chat", Icons.AutoMirrored.Filled.Chat),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(vm: ServerViewModel, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Dashboard) }
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Poll only while the app is in the foreground.
    LaunchedEffect(vm) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.pollForever() }
    }
    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(vm.profile.name)
                        val err = vm.connectionError
                        Text(
                            when {
                                err != null -> "Offline – $err"
                                vm.status == null -> "Verbinde…"
                                else -> "Online"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (err != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { tab = it },
                        icon = { Icon(it.icon, null) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (tab) {
                Tab.Dashboard -> DashboardTab(vm.status)
                Tab.Players -> PlayersTab(vm)
                Tab.Console -> ConsoleTab(vm)
                Tab.Chat -> ChatTab(vm)
            }
        }
    }
}

// ── Übersicht ───────────────────────────────────────────────────────────

@Composable
private fun DashboardTab(status: ServerStatus?) {
    if (status == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Lade Serverdaten…") }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Spieler", "${status.onlinePlayers} / ${status.maxPlayers}", Modifier.weight(1f))
            val tps = status.tps.firstOrNull() ?: 0.0
            StatCard("TPS (1 min)", "%.1f".format(tps), Modifier.weight(1f), color = tpsColor(tps))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("MSPT", "%.1f ms".format(status.mspt), Modifier.weight(1f))
            StatCard("Laufzeit", formatUptime(status.uptimeSeconds), Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Arbeitsspeicher", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                val fraction = if (status.memoryMaxMb > 0) status.memoryUsedMb.toFloat() / status.memoryMaxMb else 0f
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text("${status.memoryUsedMb} MB / ${status.memoryMaxMb} MB", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("TPS 1 / 5 / 15 min", style = MaterialTheme.typography.labelLarge)
                Text(status.tps.joinToString(" / ") { "%.1f".format(it) })
                Spacer(Modifier.height(8.dp))
                Text("Version", style = MaterialTheme.typography.labelLarge)
                Text(status.version, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("MOTD", style = MaterialTheme.typography.labelLarge)
                Text(status.motd, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("Welten", style = MaterialTheme.typography.titleMedium)
        status.worlds.forEach { w ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(w.name, fontWeight = FontWeight.Bold)
                    Text(
                        "${w.environment} · ${w.players} Spieler · ${w.loadedChunks} Chunks · ${w.entities} Entities",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = color)
        }
    }
}

private fun tpsColor(tps: Double) = when {
    tps >= 18 -> Color(0xFF43A047)
    tps >= 15 -> Color(0xFFFFA000)
    else -> Color(0xFFE53935)
}

private fun formatUptime(seconds: Long): String {
    val d = seconds / 86400
    val h = seconds % 86400 / 3600
    val m = seconds % 3600 / 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

// ── Spieler ─────────────────────────────────────────────────────────────

@Composable
private fun PlayersTab(vm: ServerViewModel) {
    var selected by remember { mutableStateOf<String?>(null) }
    if (vm.players.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Keine Spieler online") }
    } else {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.players, key = { it.uuid }) { p ->
                Card(Modifier.fillMaxWidth().clickable { selected = p.name }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name + if (p.op) " (OP)" else "", fontWeight = FontWeight.Bold)
                            Text(
                                "${p.world} · ${p.x}, ${p.y}, ${p.z} · ${p.gameMode.lowercase()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("❤ ${p.health}  🍗 ${p.food}", style = MaterialTheme.typography.bodySmall)
                            Text("${p.ping} ms", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    // Look the player up again so the dialog reflects fresh data and closes if they leave.
    selected?.let { name ->
        val player = vm.players.firstOrNull { it.name == name }
        if (player == null) selected = null else PlayerDialog(vm, player) { selected = null }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerDialog(vm: ServerViewModel, p: PlayerInfo, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Level ${p.level} · ${p.world} (${p.x}, ${p.y}, ${p.z})", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    text, { text = it },
                    label = { Text("Nachricht / Grund") },
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            if (text.isNotBlank()) {
                                vm.playerAction(p.name, "message", JSONObject().put("message", text), "Nachricht gesendet")
                                text = ""
                            }
                        },
                        label = { Text("Nachricht") },
                    )
                    AssistChip(onClick = { vm.playerAction(p.name, "heal", success = "${p.name} geheilt") }, label = { Text("Heilen") })
                    AssistChip(
                        onClick = {
                            vm.playerAction(p.name, if (p.op) "deop" else "op", success = if (p.op) "OP entzogen" else "OP vergeben")
                        },
                        label = { Text(if (p.op) "DeOP" else "OP") },
                    )
                    AssistChip(onClick = { confirm = "kick" }, label = { Text("Kicken") })
                    AssistChip(onClick = { confirm = "ban" }, label = { Text("Bannen") })
                }
                Text("Spielmodus", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR").forEach { mode ->
                        FilterChip(
                            selected = p.gameMode == mode,
                            onClick = {
                                vm.playerAction(p.name, "gamemode", JSONObject().put("mode", mode), "Spielmodus: ${mode.lowercase()}")
                            },
                            label = { Text(mode.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (action == "kick") "${p.name} kicken?" else "${p.name} bannen?") },
            text = { Text(if (text.isBlank()) "Ohne Grund" else "Grund: $text") },
            confirmButton = {
                TextButton(onClick = {
                    vm.playerAction(
                        p.name, action, JSONObject().put("reason", text),
                        if (action == "kick") "${p.name} gekickt" else "${p.name} gebannt",
                    )
                    confirm = null
                    onDismiss()
                }) { Text("Bestätigen") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Abbrechen") } },
        )
    }
}

// ── Konsole ─────────────────────────────────────────────────────────────

private val quickCommands = listOf("list", "time set day", "weather clear", "save-all", "difficulty", "tps")

@Composable
private fun ConsoleTab(vm: ServerViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    var lastSize by remember { mutableIntStateOf(0) }

    // Auto-scroll when new lines arrive and the user is at (or near) the bottom.
    LaunchedEffect(vm.console.size) {
        val size = vm.console.size
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (size > 0 && (lastSize == 0 || lastVisible >= lastSize - 3)) {
            listState.scrollToItem(size - 1)
        }
        lastSize = size
    }

    fun send() {
        vm.runCommand(input)
        input = ""
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF1E1E1E)),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(vm.console, key = { it.key }) { line ->
                Text(
                    line.text,
                    color = consoleColor(line.level),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                )
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickCommands.forEach { cmd ->
                AssistChip(onClick = { vm.runCommand(cmd) }, label = { Text("/$cmd") })
            }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Befehl, z.B. gamerule keepInventory true") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            IconButton(onClick = { send() }) { Icon(Icons.AutoMirrored.Filled.Send, "Senden") }
        }
    }
}

private fun consoleColor(level: String) = when (level) {
    "WARN" -> Color(0xFFFFC107)
    "ERROR", "FATAL" -> Color(0xFFFF5252)
    "COMMAND" -> Color(0xFF64B5F6)
    "OUTPUT" -> Color(0xFF81C784)
    else -> Color(0xFFE0E0E0)
}

// ── Chat ────────────────────────────────────────────────────────────────

private val chatPattern = Regex("^(<[^>]+> |\\[Server] |\\[Not Secure] <).*")

@Composable
private fun ChatTab(vm: ServerViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    val chat = vm.console.filter { it.level != "COMMAND" && it.level != "OUTPUT" && chatPattern.matches(it.text) }
    val listState = rememberLazyListState()
    LaunchedEffect(chat.size) {
        if (chat.isNotEmpty()) listState.scrollToItem(chat.size - 1)
    }

    fun send() {
        vm.broadcast(input)
        input = ""
    }

    Column(Modifier.fillMaxSize()) {
        if (chat.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Noch keine Chatnachrichten", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(chat, key = { it.key }) { line ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.padding(top = 6.dp).size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(line.text.removePrefix("[Not Secure] "))
                    }
                }
            }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nachricht an alle Spieler") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
            )
            IconButton(onClick = { send() }) { Icon(Icons.AutoMirrored.Filled.Send, "Senden") }
        }
    }
}
