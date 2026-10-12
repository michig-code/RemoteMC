package de.remotemc.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.remotemc.app.data.ApiClient
import de.remotemc.app.data.ConsoleLine
import de.remotemc.app.data.PlayerInfo
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** One entry in the console tab: either a server log line or a local command echo/result. */
data class ConsoleEntry(
    val key: String,
    val text: String,
    val level: String,
    val time: Long = System.currentTimeMillis(),
)

class ServerViewModel(val profile: ServerProfile) : ViewModel() {

    private val api = ApiClient(profile)

    var status by mutableStateOf<ServerStatus?>(null)
        private set
    var players by mutableStateOf<List<PlayerInfo>>(emptyList())
        private set
    var connectionError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var lastUpdated by mutableStateOf(0L)
        private set
    val console = mutableStateListOf<ConsoleEntry>()

    private var lastConsoleId = 0L
    // Polling and manual refresh may overlap; avoid appending the same lines twice.
    private val consoleMutex = Mutex()
    private var localCounter = 0L

    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    /** Polls status/players and the console until the calling coroutine is cancelled. */
    suspend fun pollForever() {
        var tick = 0
        while (true) {
            if (tick % 3 == 0) refreshStatus()
            refreshConsole()
            tick++
            delay(2000)
        }
    }

    suspend fun refreshStatus() {
        try {
            status = api.status()
            players = api.players()
            connectionError = null
            lastUpdated = System.currentTimeMillis()
        } catch (e: Exception) {
            connectionError = e.message
        }
    }

    /** Manual refresh (status pill, pull-to-refresh). */
    fun refresh() {
        if (refreshing) return
        viewModelScope.launch {
            refreshing = true
            val started = System.currentTimeMillis()
            refreshStatus()
            refreshConsole()
            // Keep the indicator visible briefly so the tap gives feedback.
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < 600) delay(600 - elapsed)
            refreshing = false
        }
    }

    private suspend fun refreshConsole() = consoleMutex.withLock {
        try {
            val (lines, last) = api.console(lastConsoleId)
            // Server restarted -> ids start again at 1
            if (last < lastConsoleId) {
                lastConsoleId = 0
                return@withLock
            }
            lines.forEach { console.add(it.toEntry()) }
            lastConsoleId = last
            trimConsole()
        } catch (_: Exception) {
            // shown via connectionError from refreshStatus
        }
    }

    private fun ConsoleLine.toEntry() = ConsoleEntry("s$id", message, level, time)

    private fun trimConsole() {
        val overflow = console.size - 1000
        if (overflow > 0) console.removeRange(0, overflow)
    }

    fun runCommand(command: String) {
        val cmd = command.trim().removePrefix("/")
        if (cmd.isEmpty()) return
        console.add(ConsoleEntry("l${localCounter++}", "> /$cmd", "COMMAND"))
        viewModelScope.launch {
            try {
                val result = api.command(cmd)
                result.output.forEach { console.add(ConsoleEntry("l${localCounter++}", it, "OUTPUT")) }
                if (!result.success) {
                    console.add(ConsoleEntry("l${localCounter++}", "Unbekannter Befehl oder fehlgeschlagen", "ERROR"))
                }
            } catch (e: Exception) {
                console.add(ConsoleEntry("l${localCounter++}", e.message ?: "Fehler", "ERROR"))
            }
            trimConsole()
        }
    }

    fun broadcast(message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            try {
                api.chat(message)
                messageChannel.send("Nachricht gesendet")
            } catch (e: Exception) {
                messageChannel.send(e.message ?: "Fehler")
            }
        }
    }

    fun playerAction(player: String, action: String, body: JSONObject = JSONObject(), success: String) {
        viewModelScope.launch {
            try {
                api.playerAction(player, action, body)
                messageChannel.send(success)
                refreshStatus()
            } catch (e: Exception) {
                messageChannel.send(e.message ?: "Fehler")
            }
        }
    }
}
