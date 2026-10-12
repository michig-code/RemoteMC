package de.remotemc.app.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.remotemc.app.R
import de.remotemc.app.app
import de.remotemc.app.data.ChatEntry
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
import de.remotemc.app.ui.util.UiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** One entry in the console tab: a server log line or a local command echo/result. */
data class ConsoleEntry(
    val key: String,
    val text: String,
    val level: String,
    val time: Long = System.currentTimeMillis(),
)

enum class Connection { CONNECTING, LIVE, POLLING, OFFLINE }

class ServerViewModel(application: Application, profile: ServerProfile) : AndroidViewModel(application) {

    var profile by mutableStateOf(profile)
        private set
    private var api = ApiClient(profile)

    var session by mutableStateOf<Session?>(null)
        private set
    var status by mutableStateOf<ServerStatus?>(null)
        private set
    var players by mutableStateOf<List<PlayerInfo>>(emptyList())
        private set
    var connection by mutableStateOf(Connection.CONNECTING)
        private set
    var error by mutableStateOf<Throwable?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set

    val console = mutableStateListOf<ConsoleEntry>()
    val chat = mutableStateListOf<ChatEntry>()

    val role: Role get() = session?.role ?: profile.role

    private var lastConsoleId = 0L
    private var lastChatId = 0L
    private var localCounter = 0L
    private val historyMutex = Mutex()
    private val reconnect = Channel<Unit>(Channel.CONFLATED)
    private var pendingBroadcast: Job? = null

    private val messageChannel = Channel<UiText>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            reconnect.trySend(Unit)
        }
    }

    init {
        runCatching {
            application.getSystemService<ConnectivityManager>()?.registerDefaultNetworkCallback(networkCallback)
        }
    }

    override fun onCleared() {
        runCatching {
            getApplication<Application>().getSystemService<ConnectivityManager>()?.unregisterNetworkCallback(networkCallback)
        }
    }

    /**
     * Keeps a live connection while the screen is visible: Server-Sent Events first, plain
     * polling while the stream is unavailable, with growing back-off between attempts.
     */
    suspend fun runConnection() {
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
            if (connection != Connection.OFFLINE) connection = Connection.POLLING
            val wait = when {
                error is ApiError.Http && (error as ApiError.Http).code == "invalid_token" -> 30_000L
                error is ApiError.CertificateChanged -> 30_000L
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
                connection = Connection.LIVE
                loadHistory()
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
            getApplication<Application>().app.servers.upsert(profile)
        }
    }

    /** Fills gaps after (re)connecting. */
    private suspend fun loadHistory() = historyMutex.withLock {
        runCatching { addChat(api.chat(lastChatId)) }
        if (role.atLeast(Role.MODERATOR)) {
            runCatching { addConsole(api.console(lastConsoleId)) }
        }
    }

    private suspend fun pollOnce() {
        try {
            val s = api.status()
            val p = api.players()
            if (session == null) runCatching { onSession(api.me()) }
            status = s
            players = p
            error = null
            if (connection != Connection.LIVE) connection = Connection.POLLING
            loadHistory()
        } catch (e: Exception) {
            error = e
            connection = Connection.OFFLINE
        }
    }

    /** Manual refresh (status pill, pull-to-refresh). */
    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            val started = System.currentTimeMillis()
            pollOnce()
            if (connection != Connection.LIVE) reconnect.trySend(Unit)
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < 600) delay(600 - elapsed) // keep the spinner visible briefly
            refreshing = false
        }
    }

    private fun addConsole(lines: List<ConsoleLine>) {
        for (line in lines) {
            if (line.id <= lastConsoleId) {
                // Server restarted: ids start again at 1.
                if (line.id == 1L && lastConsoleId > 1) lastConsoleId = 0 else continue
            }
            console.add(ConsoleEntry("s${line.id}-${line.time}", line.message, line.level, line.time))
            lastConsoleId = line.id
        }
        trim(console, 1500)
    }

    private fun addChat(entries: List<ChatEntry>) {
        for (entry in entries) {
            if (entry.id <= lastChatId) {
                if (entry.id == 1L && lastChatId > 1) lastChatId = 0 else continue
            }
            chat.add(entry)
            lastChatId = entry.id
        }
        trim(chat, 300)
    }

    private fun <T> trim(list: MutableList<T>, max: Int) {
        val overflow = list.size - max
        if (overflow > 0) list.subList(0, overflow).clear()
    }

    private fun local(text: String, level: String) {
        console.add(ConsoleEntry("l${localCounter++}", text, level))
    }

    fun runCommand(command: String) {
        val cmd = command.trim().removePrefix("/")
        if (cmd.isEmpty()) return
        local("> /$cmd", "COMMAND")
        viewModelScope.launch {
            try {
                val result = api.command(cmd)
                result.output.forEach { local(it, "OUTPUT") }
                if (!result.success) {
                    local(getApplication<Application>().getString(R.string.console_command_failed), "ERROR")
                } else if (!result.captured && result.output.isEmpty()) {
                    local(getApplication<Application>().getString(R.string.console_output_in_log), "OUTPUT")
                }
            } catch (e: Exception) {
                local(UiText.Error(e).resolve(getApplication()), "ERROR")
            }
        }
    }

    /** Sends after a short delay so the message can still be undone from the snackbar. */
    fun broadcast(message: String) {
        if (message.isBlank()) return
        pendingBroadcast?.cancel()
        pendingBroadcast = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            try {
                api.broadcast(message)
            } catch (e: Exception) {
                messageChannel.send(UiText.Error(e))
            }
        }
    }

    fun cancelBroadcast() {
        pendingBroadcast?.cancel()
        pendingBroadcast = null
    }

    fun playerAction(player: String, action: String, body: JSONObject = JSONObject(), success: UiText) {
        viewModelScope.launch {
            try {
                api.playerAction(player, action, body)
                messageChannel.send(success)
                pollOnce()
            } catch (e: Exception) {
                messageChannel.send(UiText.Error(e))
            }
        }
    }

    companion object {
        const val UNDO_WINDOW_MS = 4_000L
    }
}
