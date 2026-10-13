package de.remotemc.app.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.provider.OpenableColumns
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
import de.remotemc.app.data.CommandCompletion
import de.remotemc.app.data.CommandInfo
import de.remotemc.app.data.CompletionCache
import de.remotemc.app.data.ConsoleLine
import de.remotemc.app.data.HistoryRange
import de.remotemc.app.data.Macro
import de.remotemc.app.data.PerformanceHistory
import de.remotemc.app.data.Placeholders
import de.remotemc.app.data.PlayerInfo
import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerSnapshot
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.data.Session
import de.remotemc.app.data.Shortcuts
import de.remotemc.app.net.ApiClient
import de.remotemc.app.net.ApiError
import de.remotemc.app.net.EventStream
import de.remotemc.app.net.StreamEvent
import de.remotemc.app.ui.util.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** One entry in the console tab: a server log line or a local command echo/result. */
data class ConsoleEntry(
    val key: String,
    val text: String,
    val level: String,
    val time: Long = System.currentTimeMillis(),
    /** Same text with Minecraft colour codes, if the server sent colours. */
    val formatted: String? = null,
)

/** A completion shown above the console input. */
data class Suggestion(val text: String, val description: String? = null)

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
    /**
     * How old the shown data is while it is not live: saved at the last visit (instant start) or
     * last received before the connection was lost; null while the server answers.
     */
    var cachedAt by mutableStateOf<Long?>(null)
        private set
    /** Time of the last data from the server (for [cachedAt] when the connection drops). */
    private var lastLiveAt = 0L
    /** True while only restored data is shown; it is not saved again then. */
    private var restoredOnly = false

    val console = mutableStateListOf<ConsoleEntry>()
    val chat = mutableStateListOf<ChatEntry>()

    /** Recently run commands for this server, newest first. */
    var history by mutableStateOf(application.app.history.load(profile.id))
        private set
    var suggestions by mutableStateOf<List<Suggestion>>(emptyList())
        private set
    /** Usage of the command being typed, e.g. "/gamemode <mode> [player]". */
    var usageHint by mutableStateOf<String?>(null)
        private set
    /** The server's commands (saved from the last visit, refreshed once connected). */
    var commands by mutableStateOf<List<CommandInfo>>(emptyList())
        private set
    /** Favorites and macros of this server. */
    var shortcuts by mutableStateOf(application.app.shortcuts.load(profile.id))
        private set
    /** Name of the macro that is running, if any. */
    var runningMacro by mutableStateOf<String?>(null)
        private set
    private var macroJob: Job? = null
    private var commandsLoaded = false
    private var commandsJob: Job? = null
    private val completionCache = CompletionCache()
    /** What is typed now (before the cursor), to place late server answers. */
    private var typed = ""
    /** True when the server could not be asked: only saved and local suggestions are shown. */
    var suggestionsOffline by mutableStateOf(false)
        private set
    private var suggestJob: Job? = null

    val role: Role get() = session?.role ?: profile.role

    private var lastConsoleId = 0L
    private var lastChatId = 0L
    private var localCounter = 0L
    private val historyMutex = Mutex()
    /** Console/chat lines restored from the snapshot; the first load after it skips them. */
    private var restoredConsole: Set<Pair<Long, Long>> = emptySet()
    private var restoredChat: Set<Pair<Long, Long>> = emptySet()
    /** Time of the newest restored line; older lines from the server are already shown or cut off. */
    private var restoredConsoleUntil = 0L
    private var restoredChatUntil = 0L
    private val restore: Job
    private val reconnect = Channel<Unit>(Channel.CONFLATED)

    /** A message for the snackbar, optionally with "Undo". */
    data class Notice(val text: UiText, val undo: (() -> Unit)? = null)

    private val messageChannel = Channel<Notice>(Channel.BUFFERED)
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
        restore = viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { application.app.snapshots.load(profile.id) } ?: return@launch
            restore(snapshot)
        }
    }

    /** Shows the last visit's data until the server answers (only if nothing live arrived yet). */
    private fun restore(snapshot: ServerSnapshot) {
        if (status != null) return
        cachedAt = snapshot.savedAt
        restoredOnly = true
        snapshot.session?.let { session = it }
        status = snapshot.status
        players = snapshot.players
        if (commands.isEmpty()) commands = snapshot.commands
        if (console.isEmpty()) {
            snapshot.console.forEach { line ->
                console.add(ConsoleEntry("s${line.id}-${line.time}", line.message, line.level, line.time, line.formatted))
            }
            restoredConsole = snapshot.console.mapTo(HashSet()) { it.id to it.time }
            restoredConsoleUntil = snapshot.console.maxOfOrNull { it.time } ?: 0L
        }
        if (chat.isEmpty()) {
            chat.addAll(snapshot.chat)
            restoredChat = snapshot.chat.mapTo(HashSet()) { it.id to it.time }
            restoredChatUntil = snapshot.chat.maxOfOrNull { it.time } ?: 0L
        }
    }

    private var lastSave = System.currentTimeMillis()

    /** Also saves while the screen stays open, in case the app is killed without notice. */
    private suspend fun saveSnapshotSometimes() {
        if (System.currentTimeMillis() - lastSave < SAVE_EVERY_MS) return
        lastSave = System.currentTimeMillis()
        saveSnapshot()
    }

    /** Remembers what is shown now for the next visit. */
    private suspend fun saveSnapshot() {
        val shownStatus = status ?: return
        if (restoredOnly) return // nothing new since the last visit
        val snapshot = ServerSnapshot(
            savedAt = System.currentTimeMillis(),
            session = session,
            status = shownStatus,
            players = players,
            console = console.filter { it.key.startsWith("s") }.takeLast(ServerSnapshot.MAX_CONSOLE).mapNotNull { it.toLine() },
            chat = chat.takeLast(ServerSnapshot.MAX_CHAT),
            commands = commands,
        )
        withContext(Dispatchers.IO) { getApplication<Application>().app.snapshots.save(profile.id, snapshot) }
    }

    private fun ConsoleEntry.toLine(): ConsoleLine? {
        val id = key.removePrefix("s").substringBefore('-').toLongOrNull() ?: return null
        return ConsoleLine(id, time, level, text, formatted)
    }

    /** Called once live data replaces the restored data. */
    private fun live() {
        cachedAt = null
        restoredOnly = false
        lastLiveAt = System.currentTimeMillis()
    }

    /** The server stopped answering: the data on screen keeps its age. */
    private fun stale() {
        if (cachedAt == null && status != null && lastLiveAt > 0) cachedAt = lastLiveAt
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
        restore.join() // first show the last visit, then connect
        try {
            connectLoop()
        } finally {
            // Leaving the screen (or the app): keep what is shown for an instant start next time.
            withContext(NonCancellable) { saveSnapshot() }
        }
    }

    private suspend fun connectLoop() {
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
            saveSnapshotSometimes()
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
                live()
                error = null
                connection = Connection.LIVE
                loadHistory()
                loadCommands()
            }
            is StreamEvent.Status -> {
                status = event.status
                players = event.players
                lastLiveAt = System.currentTimeMillis()
                saveSnapshotSometimes()
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
    val moderation = ModerationActions(
        whitelist = { api.whitelist() },
        setWhitelist = { api.setWhitelist(it) },
        addToWhitelist = { api.whitelistAdd(it) },
        removeFromWhitelist = { api.whitelistRemove(it) },
        bans = { api.bans() },
        pardon = { api.pardon(it) },
        ban = { name, reason -> api.playerAction(name, "ban", JSONObject().put("reason", reason)) },
    )

    val worldActions = WorldActions(
        load = { api.world(it) },
        change = { world, action, body -> api.worldAction(world, action, body) },
    )

    suspend fun plugins() = api.plugins()

    suspend fun inventory(player: String) = api.inventory(player)

    val scheduleActions = ScheduleActions(
        load = { api.schedules() },
        save = { api.saveSchedule(it) },
        run = { api.scheduleAction(it, "run") },
        delete = { api.scheduleAction(it, "delete") },
    )

    val fileActions = FileActions(
        list = { api.files(it) },
        read = { api.readFile(it) },
        write = { path, content -> api.writeFile(path, content) },
        delete = { api.deleteFile(it) },
        restore = { api.restoreFile(it) },
        createFolder = { api.createFolder(it) },
        nameOf = { uri -> documentInfo(uri).first },
        upload = { dir, uri ->
            val (name, size) = documentInfo(uri)
            val path = if (dir.isEmpty()) name else "$dir/$name"
            val resolver = getApplication<Application>().contentResolver
            api.uploadFile(path, size) { resolver.openInputStream(uri) ?: throw java.io.FileNotFoundException(uri.toString()) }
            path
        },
    )

    /** Display name (made safe as a file name) and size (-1 = unknown) of a picked document. */
    private suspend fun documentInfo(uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val info = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                (c.getString(0) ?: "upload") to (if (c.isNull(1)) -1L else c.getLong(1))
            } else {
                null
            }
        } ?: ((uri.lastPathSegment ?: "upload") to -1L)
        info.first.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifEmpty { "upload" } to info.second
    }

    /** Performance history for the dashboard chart. */
    suspend fun performance(range: HistoryRange): PerformanceHistory = api.history(range)

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
            live()
            error = null
            if (connection != Connection.LIVE) connection = Connection.POLLING
            loadHistory()
        } catch (e: Exception) {
            error = e
            connection = Connection.OFFLINE
            stale()
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
            if (line.time < restoredConsoleUntil || line.id to line.time in restoredConsole) {
                lastConsoleId = maxOf(lastConsoleId, line.id)
                continue
            }
            if (line.id <= lastConsoleId) {
                // Server restarted: ids start again at 1.
                if (line.id == 1L && lastConsoleId > 1) lastConsoleId = 0 else continue
            }
            console.add(ConsoleEntry("s${line.id}-${line.time}", line.message, line.level, line.time, line.formatted))
            lastConsoleId = line.id
        }
        trim(console, 1500)
    }

    private fun addChat(entries: List<ChatEntry>) {
        for (entry in entries) {
            if (entry.time < restoredChatUntil || entry.id to entry.time in restoredChat) {
                lastChatId = maxOf(lastChatId, entry.id)
                continue
            }
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

    private fun local(text: String, level: String, formatted: String? = null) {
        console.add(ConsoleEntry("l${localCounter++}", text, level, formatted = formatted))
    }

    fun runCommand(command: String) {
        val cmd = command.trim().removePrefix("/")
        if (cmd.isEmpty()) return
        history = getApplication<Application>().app.history.add(profile.id, cmd)
        clearSuggestions()
        viewModelScope.launch { execute(cmd) }
    }

    /** Runs one command and shows it with its output in the console; false if it failed. */
    private suspend fun execute(cmd: String): Boolean {
        local("> /$cmd", "COMMAND")
        return try {
            val result = api.command(cmd)
            result.output.forEachIndexed { i, line -> local(line, "OUTPUT", result.formatted.getOrNull(i)) }
            if (!result.success) {
                local(getApplication<Application>().getString(R.string.console_command_failed), "ERROR")
            } else if (!result.captured && result.output.isEmpty()) {
                local(getApplication<Application>().getString(R.string.console_output_in_log), "OUTPUT")
            }
            result.success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            local(UiText.Error(e).resolve(getApplication()), "ERROR")
            false
        }
    }

    fun updateShortcuts(change: (Shortcuts) -> Shortcuts) {
        shortcuts = change(shortcuts)
        getApplication<Application>().app.shortcuts.save(profile.id, shortcuts)
        // Favorites can be buttons on the home screen widget.
        viewModelScope.launch { runCatching { de.remotemc.app.widget.StatusRefresher.updateActions(getApplication()) } }
    }

    /**
     * Runs the steps of [macro] one after another with their waits, placeholders filled from
     * [values]. Stops at the first failing step; [stopMacro] cancels it.
     */
    fun runMacro(macro: Macro, values: Map<String, String>) {
        if (macroJob?.isActive == true) return
        clearSuggestions()
        macroJob = viewModelScope.launch {
            runningMacro = macro.name
            val app = getApplication<Application>()
            local(app.getString(R.string.macro_started, macro.name), "INFO")
            try {
                for (step in macro.steps) {
                    if (step.waitSeconds > 0) delay(step.waitSeconds * 1000L)
                    if (!execute(Placeholders.fill(step.command, values).trim().removePrefix("/"))) {
                        local(app.getString(R.string.macro_failed, macro.name), "ERROR")
                        return@launch
                    }
                }
                local(app.getString(R.string.macro_done, macro.name), "INFO")
            } catch (e: CancellationException) {
                local(app.getString(R.string.macro_stopped, macro.name), "WARN")
                throw e
            } finally {
                runningMacro = null
            }
        }
    }

    fun stopMacro() {
        macroJob?.cancel()
    }

    fun forgetCommand(command: String) {
        history = getApplication<Application>().app.history.remove(profile.id, command)
    }

    /**
     * Loads the server's command list (admins only) for instant suggestions. Restored commands
     * are shown at once but still refreshed once (cheap: ETag); a failed load is retried.
     */
    fun loadCommands() {
        if (commandsLoaded || commandsJob?.isActive == true || !role.atLeast(Role.ADMIN)) return
        commandsJob = viewModelScope.launch {
            for (wait in COMMAND_RETRY_MS) {
                if (runCatching { commands = api.commands() }.isSuccess) {
                    commandsLoaded = true
                    return@launch
                }
                delay(wait)
            }
        }
    }

    /**
     * Suggestions like Minecraft's chat for [input] (the text before the cursor): command names
     * from the saved list while typing the first word; for arguments, local ones (players, game
     * modes, …) and remembered server answers at once, then the server's own tab completion.
     */
    fun onCommandInput(input: String) {
        val text = input.removePrefix("/")
        typed = text
        if (text.isBlank()) {
            clearSuggestions()
            return
        }
        val label = text.substringBefore(' ').lowercase()
        val info = commands.firstOrNull { it.name == label || label in it.aliases }
        usageHint = info?.usage ?: info?.let { "/${it.name}" }
        if (' ' !in text) {
            val local = commands
                .filter { it.name.startsWith(label) && it.name != label }
                .sortedBy { it.name.length }
                .take(8)
                .map { Suggestion(it.name, it.description) }
            suggestions = local
            suggestionsOffline = false
            if (local.isNotEmpty() || commands.isNotEmpty()) return
        }
        showArguments(text, completionCache.lookup(text))
        suggestJob?.cancel() // only the debounce: requests already sent are not cancelled
        suggestJob = viewModelScope.launch {
            delay(120) // wait until typing pauses
            if (typed != text) return@launch
            launch { requestCompletion(text) }
        }
    }

    private fun showArguments(text: String, server: List<String>?) {
        val local = CommandCompletion.suggest(text, players.map { it.name }, status?.worlds?.map { it.name }.orEmpty())
        suggestions = CommandCompletion.merge(server.orEmpty(), local).take(30).map { Suggestion(it) }
    }

    /** Asks the server; a late answer is still shown if it fits what is typed now. */
    private suspend fun requestCompletion(text: String) {
        val result = runCatching { api.complete(text) }
        result.onSuccess { completionCache.put(text, it) }
        val now = typed
        // Same argument as now (the user may have typed on): show the answer, filtered.
        if (now.startsWith(text) && ' ' !in now.substring(text.lastIndexOf(' ') + 1)) {
            suggestionsOffline = result.isFailure
            if (result.isSuccess) showArguments(now, completionCache.lookup(now))
        }
    }

    fun clearSuggestions() {
        suggestJob?.cancel()
        suggestions = emptyList()
        usageHint = null
        suggestionsOffline = false
    }

    /** Sends right away; the message shows up in the chat via the live stream. */
    fun broadcast(message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            try {
                api.broadcast(message)
            } catch (e: Exception) {
                messageChannel.send(Notice(UiText.Error(e)))
            }
        }
    }

    fun playerAction(player: String, action: String, body: JSONObject = JSONObject(), success: UiText) {
        viewModelScope.launch {
            try {
                api.playerAction(player, action, body)
                // A ban can be taken back right away.
                messageChannel.send(Notice(success, undo = if (action == "ban") ({ unban(listOf(player)) }) else null))
                pollOnce()
            } catch (e: Exception) {
                messageChannel.send(Notice(UiText.Error(e)))
            }
        }
    }

    /**
     * Runs [action] for several players one after another ("whitelist" adds them to the
     * whitelist); one message at the end says for whom it did not work.
     */
    fun playerBatch(players: List<String>, action: String, body: JSONObject = JSONObject()) {
        if (players.isEmpty()) return
        viewModelScope.launch {
            val failed = mutableListOf<String>()
            for (name in players) {
                try {
                    if (action == "whitelist") api.whitelistAdd(name) else api.playerAction(name, action, body)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed += name
                }
            }
            val done = players - failed.toSet()
            messageChannel.send(
                Notice(
                    if (failed.isEmpty()) UiText.Plural(R.plurals.players_batch_done, players.size)
                    else UiText.Res(R.string.players_batch_failed, listOf(failed.joinToString(", "))),
                    undo = if (action == "ban" && done.isNotEmpty()) ({ unban(done) }) else null,
                ),
            )
            pollOnce()
        }
    }

    /** Takes bans back (the "Undo" of a ban). */
    private fun unban(players: List<String>) {
        viewModelScope.launch {
            val failed = players.filter { runCatching { api.pardon(it) }.isFailure }
            messageChannel.send(
                Notice(
                    if (failed.isEmpty()) UiText.Res(R.string.bans_unbanned, listOf(players.joinToString(", ")))
                    else UiText.Res(R.string.players_batch_failed, listOf(failed.joinToString(", "))),
                ),
            )
        }
    }

    private companion object {
        const val SAVE_EVERY_MS = 60_000L
        val COMMAND_RETRY_MS = listOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L)
    }
}
