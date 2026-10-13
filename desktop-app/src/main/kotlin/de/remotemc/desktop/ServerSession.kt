package de.remotemc.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.remotemc.app.data.ChatEntry
import de.remotemc.app.data.CommandInfo
import de.remotemc.app.data.ConsoleLine
import de.remotemc.app.data.PlayerInfo
import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.data.Session
import de.remotemc.app.net.ApiClient
import de.remotemc.app.net.ApiError
import de.remotemc.app.net.EventStream
import de.remotemc.app.net.StreamEvent
import de.remotemc.desktop.res.Res
import de.remotemc.desktop.res.console_command_failed
import de.remotemc.desktop.res.console_output_in_log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import org.json.JSONObject

data class ConsoleEntry(val key: String, val text: String, val level: String, val time: Long = 0, val formatted: String? = null)
data class Suggestion(val text: String, val description: String? = null)

/** Live state of one server while its window part is open. */
class ServerSession(initial: ServerProfile, private val scope: CoroutineScope, private val store: ServerStore, private val historyStore: CommandHistory) {
    var profile by mutableStateOf(initial)
        private set
    private var api = ApiClient(initial)

    var session by mutableStateOf<Session?>(null)
        private set
    var status by mutableStateOf<ServerStatus?>(null)
        private set
    var players by mutableStateOf<List<PlayerInfo>>(emptyList())
        private set
    var connection by mutableStateOf(Conn.CONNECTING)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    val console = mutableStateListOf<ConsoleEntry>()
    val chat = mutableStateListOf<ChatEntry>()
    var history by mutableStateOf(historyStore.load(initial.id))
        private set
    var commands by mutableStateOf<List<CommandInfo>>(emptyList())
        private set
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
        private set
    var usageHint by mutableStateOf<String?>(null)
        private set
    /** Short feedback for the snackbar. */
    val messages = Channel<String>(Channel.BUFFERED)

    val role get() = session?.role ?: profile.role
    val client get() = api
    /** Whitelist, bans, worlds, plugins and scheduled tasks (replaced in screenshot tests). */
    var manage: Management = ApiManagement { api }
        internal set

    fun supports(feature: String) = status?.supports(feature) == true

    /** Shows [text] in the snackbar. */
    fun notify(text: String) {
        messages.trySend(text)
    }

    private var lastConsoleId = 0L
    private var lastChatId = 0L
    private var localCounter = 0L
    private val loadMutex = Mutex()
    private val reconnect = Channel<Unit>(Channel.CONFLATED)
    private var suggestJob: Job? = null
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch { run() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun retry() {
        reconnect.trySend(Unit)
    }

    private suspend fun run() {
        var failures = 0
        while (true) {
            try {
                EventStream(profile).events().collect { event ->
                    failures = 0
                    handle(event)
                }
            } catch (e: ApiError) {
                error = e
            }
            failures++
            pollOnce()
            if (connection != Conn.OFFLINE) connection = Conn.POLLING
            val wait = when (error) {
                is ApiError.CertificateChanged -> 30_000L
                is ApiError.Http -> if ((error as ApiError.Http).code == "invalid_token") 30_000L else 5_000L
                else -> minOf(30_000L, 1_000L shl minOf(failures, 5))
            }
            withTimeoutOrNull(wait) { reconnect.receive() }
        }
    }

    private suspend fun handle(event: StreamEvent) {
        when (event) {
            is StreamEvent.Hello -> {
                onSession(event.session)
                status = event.status
                players = event.players
                error = null
                connection = Conn.LIVE
                loadBacklog()
                loadCommands()
            }
            is StreamEvent.Status -> {
                status = event.status
                players = event.players
            }
            is StreamEvent.Console -> addConsole(listOf(event.line))
            is StreamEvent.Chat -> addChat(listOf(event.entry))
        }
    }

    private fun onSession(s: Session) {
        session = s
        if (s.role != profile.role) {
            profile = profile.copy(role = s.role)
            store.upsert(profile)
        }
    }

    private suspend fun loadBacklog() = loadMutex.withLock {
        runCatching { addChat(api.chat(lastChatId)) }
        if (role.atLeast(Role.MODERATOR)) runCatching { addConsole(api.console(lastConsoleId)) }
    }

    private suspend fun pollOnce() {
        try {
            status = api.status()
            players = api.players()
            if (session == null) runCatching { onSession(api.me()) }
            error = null
            if (connection != Conn.LIVE) connection = Conn.POLLING
            loadBacklog()
        } catch (e: Exception) {
            error = e
            connection = Conn.OFFLINE
        }
    }

    private fun addConsole(lines: List<ConsoleLine>) {
        for (line in lines) {
            if (line.id <= lastConsoleId) {
                if (line.id == 1L && lastConsoleId > 1) lastConsoleId = 0 else continue // server restarted
            }
            console.add(ConsoleEntry("s${line.id}-${line.time}", line.message, line.level, line.time, line.formatted))
            lastConsoleId = line.id
        }
        trim(console, 3000)
    }

    private fun addChat(entries: List<ChatEntry>) {
        for (entry in entries) {
            if (entry.id <= lastChatId) {
                if (entry.id == 1L && lastChatId > 1) lastChatId = 0 else continue
            }
            chat.add(entry)
            lastChatId = entry.id
        }
        trim(chat, 500)
    }

    private fun <T> trim(list: MutableList<T>, max: Int) {
        val overflow = list.size - max
        repeat(overflow.coerceAtLeast(0)) { list.removeAt(0) }
    }

    private fun local(text: String, level: String, formatted: String? = null) {
        console.add(ConsoleEntry("l${localCounter++}", text, level, System.currentTimeMillis(), formatted))
    }

    fun runCommand(command: String) {
        val cmd = command.trim().removePrefix("/")
        if (cmd.isEmpty()) return
        local("> /$cmd", "COMMAND")
        history = historyStore.add(profile.id, cmd)
        suggestions = emptyList()
        usageHint = null
        scope.launch {
            try {
                val result = api.command(cmd)
                result.output.forEachIndexed { i, line -> local(line, "OUTPUT", result.formatted.getOrNull(i)) }
                if (!result.success) local(getString(Res.string.console_command_failed), "ERROR")
                else if (!result.captured && result.output.isEmpty()) local(getString(Res.string.console_output_in_log), "OUTPUT")
            } catch (e: Exception) {
                local(errorText(e), "ERROR")
            }
        }
    }

    private fun loadCommands() {
        if (commands.isNotEmpty() || !role.atLeast(Role.ADMIN)) return
        scope.launch { runCatching { commands = api.commands() } }
    }

    /** Like Minecraft's chat: command names first, then the server's tab completion. */
    fun onCommandInput(input: String) {
        suggestJob?.cancel()
        val text = input.removePrefix("/")
        if (text.isBlank()) {
            suggestions = emptyList()
            usageHint = null
            return
        }
        val label = text.substringBefore(' ').lowercase()
        val info = commands.firstOrNull { it.name == label || label in it.aliases }
        usageHint = info?.usage ?: info?.let { "/${it.name}" }
        if (' ' !in text) {
            val local = commands.filter { it.name.startsWith(label) && it.name != label }
                .sortedBy { it.name.length }.take(8).map { Suggestion(it.name, it.description) }
            suggestions = local
            if (local.isNotEmpty() || commands.isNotEmpty()) return
        }
        suggestJob = scope.launch {
            delay(150)
            val current = text.substringAfterLast(' ')
            suggestions = runCatching { api.complete(text) }.getOrDefault(emptyList())
                .filter { it != current }.take(30).map { Suggestion(it) }
        }
    }

    fun broadcast(message: String) {
        scope.launch {
            try {
                api.broadcast(message)
            } catch (e: Exception) {
                messages.send(errorText(e))
            }
        }
    }

    fun playerAction(player: String, action: String, body: JSONObject, success: String) {
        scope.launch {
            try {
                api.playerAction(player, action, body)
                messages.send(success)
            } catch (e: Exception) {
                messages.send(errorText(e))
            }
        }
    }

    /** Fixed data for screenshot tests (no connection). */
    internal fun showPreview(status: ServerStatus, players: List<PlayerInfo>, console: List<ConsoleEntry>, chat: List<ChatEntry>, session: Session, history: List<String>) {
        this.status = status
        this.players = players
        this.console.addAll(console)
        this.chat.addAll(chat)
        this.session = session
        this.history = history
        connection = Conn.LIVE
    }
}
