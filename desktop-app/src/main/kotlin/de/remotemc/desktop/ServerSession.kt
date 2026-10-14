package de.remotemc.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.remotemc.app.data.ChatEntry
import de.remotemc.desktop.res.bans_unbanned
import de.remotemc.desktop.res.players_batch_failed
import de.remotemc.desktop.res.players_batch_done
import de.remotemc.desktop.res.macro_stopped
import de.remotemc.desktop.res.macro_done
import de.remotemc.desktop.res.macro_failed
import de.remotemc.desktop.res.macro_started
import org.jetbrains.compose.resources.getPluralString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import de.remotemc.app.data.Shortcuts
import de.remotemc.app.data.ServerSnapshot
import de.remotemc.app.data.Placeholders
import de.remotemc.app.data.Macro
import de.remotemc.app.data.CompletionCache
import de.remotemc.app.data.CommandCompletion
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
import de.remotemc.app.ui.ConsoleEntry
import de.remotemc.app.ui.Suggestion
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


/** Live state of one server while its window part is open. */
/** A message for the snackbar, optionally with "Undo". */
data class Notice(val text: String, val undo: (() -> Unit)? = null)

class ServerSession(
    initial: ServerProfile,
    private val scope: CoroutineScope,
    private val store: ServerStore,
    private val historyStore: CommandHistory,
    private val extras: ServerExtras = ServerExtras.inMemory(),
) {
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
    /** True when the server could not be asked: only saved and local suggestions are shown. */
    var suggestionsOffline by mutableStateOf(false)
        private set
    /**
     * How old the shown data is while it is not live: saved at the last visit (instant start) or
     * last received before the connection was lost; null while the server answers.
     */
    var cachedAt by mutableStateOf<Long?>(null)
        private set
    /** Favorites and macros of this server. */
    var shortcuts by mutableStateOf(extras.shortcuts.load(initial.id))
        private set
    /** Name of the macro that is running, if any. */
    var runningMacro by mutableStateOf<String?>(null)
        private set
    /** Short feedback for the snackbar. */
    val messages = Channel<Notice>(Channel.BUFFERED)

    val role get() = session?.role ?: profile.role
    val client get() = api
    /** Whitelist, bans, worlds, plugins and scheduled tasks (replaced in screenshot tests). */
    var manage: Management = ApiManagement { api }
        internal set

    fun supports(feature: String) = status?.supports(feature) == true

    /** Shows [text] in the snackbar. */
    fun notify(text: String, undo: (() -> Unit)? = null) {
        messages.trySend(Notice(text, undo))
    }

    /** Private note about a player, only on this PC. */
    fun note(uuid: String) = extras.notes.get(profile.id, uuid)
    fun setNote(uuid: String, text: String) = extras.notes.set(profile.id, uuid, text)

    private var lastConsoleId = 0L
    private var lastChatId = 0L
    private var localCounter = 0L
    private val loadMutex = Mutex()
    private val reconnect = Channel<Unit>(Channel.CONFLATED)
    private var suggestJob: Job? = null
    private var job: Job? = null
    private var macroJob: Job? = null
    private var commandsLoaded = false
    private var commandsJob: Job? = null
    private val completionCache = CompletionCache()
    /** What is typed now (before the cursor), to place late server answers. */
    private var typed = ""
    private var lastLiveAt = 0L
    private var restoredOnly = false
    private var lastSave = 0L
    private var restoredConsole: Set<Pair<Long, Long>> = emptySet()
    private var restoredChat: Set<Pair<Long, Long>> = emptySet()
    private var restoredConsoleUntil = 0L
    private var restoredChatUntil = 0L

    fun start() {
        if (job != null) return
        restore()
        job = scope.launch { run() }
    }

    fun stop() {
        job?.cancel()
        job = null
        saveSnapshot(now = true) // the tab was closed: keep what it showed for next time
    }

    /** Shows the last visit's data until the server answers. */
    private fun restore() {
        if (status != null) return
        val snapshot = runCatching { extras.snapshots?.load(profile.id) }.getOrNull() ?: return
        cachedAt = snapshot.savedAt
        restoredOnly = true
        snapshot.session?.let { session = it }
        status = snapshot.status
        players = snapshot.players
        if (commands.isEmpty()) commands = snapshot.commands
        snapshot.console.forEach { line -> console.add(ConsoleEntry("s${line.id}-${line.time}", line.message, line.level, line.time, line.formatted)) }
        restoredConsole = snapshot.console.mapTo(HashSet()) { it.id to it.time }
        restoredConsoleUntil = snapshot.console.maxOfOrNull { it.time } ?: 0L
        chat.addAll(snapshot.chat)
        restoredChat = snapshot.chat.mapTo(HashSet()) { it.id to it.time }
        restoredChatUntil = snapshot.chat.maxOfOrNull { it.time } ?: 0L
    }

    /** Remembers what is shown now for the next start. */
    fun saveSnapshot(now: Boolean = false) {
        val shown = status ?: return
        val snapshots = extras.snapshots ?: return
        if (restoredOnly) return
        val snapshot = ServerSnapshot(
            savedAt = System.currentTimeMillis(),
            session = session,
            status = shown,
            players = players,
            console = console.filter { it.key.startsWith("s") }.takeLast(ServerSnapshot.MAX_CONSOLE).mapNotNull { e ->
                e.key.removePrefix("s").substringBefore('-').toLongOrNull()?.let { ConsoleLine(it, e.time, e.level, e.text, e.formatted) }
            },
            chat = chat.takeLast(ServerSnapshot.MAX_CHAT),
            commands = commands,
        )
        // When closing, right away (the app may be quitting); otherwise in the background.
        if (now) runCatching { snapshots.save(profile.id, snapshot) }
        else scope.launch(Dispatchers.IO) { runCatching { snapshots.save(profile.id, snapshot) } }
    }

    private fun saveSometimes() {
        val now = System.currentTimeMillis()
        if (now - lastSave < 60_000) return
        lastSave = now
        saveSnapshot()
    }

    private fun live() {
        cachedAt = null
        restoredOnly = false
        lastLiveAt = System.currentTimeMillis()
    }

    private fun stale() {
        if (cachedAt == null && status != null && lastLiveAt > 0) cachedAt = lastLiveAt
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
                live()
                error = null
                connection = Conn.LIVE
                loadBacklog()
                loadCommands()
            }
            is StreamEvent.Status -> {
                status = event.status
                players = event.players
                lastLiveAt = System.currentTimeMillis()
                saveSometimes()
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
            live()
            error = null
            if (connection != Conn.LIVE) connection = Conn.POLLING
            loadBacklog()
        } catch (e: Exception) {
            error = e
            connection = Conn.OFFLINE
            stale()
        }
    }

    private fun addConsole(lines: List<ConsoleLine>) {
        for (line in lines) {
            // Lines already shown from the last visit (or older than those) are skipped.
            if (line.time < restoredConsoleUntil || line.id to line.time in restoredConsole) {
                lastConsoleId = maxOf(lastConsoleId, line.id)
                continue
            }
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
        history = historyStore.add(profile.id, cmd)
        clearSuggestions()
        scope.launch { execute(cmd) }
    }

    /** Runs one command and shows it with its output in the console; false if it failed. */
    private suspend fun execute(cmd: String): Boolean {
        local("> /$cmd", "COMMAND")
        return try {
            val result = api.command(cmd)
            result.output.forEachIndexed { i, line -> local(line, "OUTPUT", result.formatted.getOrNull(i)) }
            if (!result.success) local(getString(Res.string.console_command_failed), "ERROR")
            else if (!result.captured && result.output.isEmpty()) local(getString(Res.string.console_output_in_log), "OUTPUT")
            result.success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            local(errorText(e), "ERROR")
            false
        }
    }

    fun updateShortcuts(change: (Shortcuts) -> Shortcuts) {
        shortcuts = change(shortcuts)
        extras.shortcuts.save(profile.id, shortcuts)
    }

    /** Runs the steps of [macro] one after another with their waits; stops at the first failing step. */
    fun runMacro(macro: Macro, values: Map<String, String>) {
        if (macroJob?.isActive == true) return
        clearSuggestions()
        macroJob = scope.launch {
            runningMacro = macro.name
            local(getString(Res.string.macro_started, macro.name), "INFO")
            try {
                for (step in macro.steps) {
                    if (step.waitSeconds > 0) delay(step.waitSeconds * 1000L)
                    if (!execute(Placeholders.fill(step.command, values).trim().removePrefix("/"))) {
                        local(getString(Res.string.macro_failed, macro.name), "ERROR")
                        return@launch
                    }
                }
                local(getString(Res.string.macro_done, macro.name), "INFO")
            } catch (e: CancellationException) {
                local(getString(Res.string.macro_stopped, macro.name), "WARN")
                throw e
            } finally {
                runningMacro = null
            }
        }
    }

    fun stopMacro() {
        macroJob?.cancel()
    }

    /**
     * Loads the server's command list (admins only); restored commands are shown at once but
     * still refreshed once, and a failed load is tried again.
     */
    private fun loadCommands() {
        if (commandsLoaded || commandsJob?.isActive == true || !role.atLeast(Role.ADMIN)) return
        commandsJob = scope.launch {
            for (wait in listOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L)) {
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
     * while typing the first word; for arguments, local ones (players, game modes, …) and
     * remembered server answers at once, then the server's own tab completion.
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
            val local = commands.filter { it.name.startsWith(label) && it.name != label }
                .sortedBy { it.name.length }.take(8).map { Suggestion(it.name, it.description) }
            suggestions = local
            suggestionsOffline = false
            if (local.isNotEmpty() || commands.isNotEmpty()) return
        }
        showArguments(text, completionCache.lookup(text))
        suggestJob?.cancel() // only the debounce: requests already sent are not cancelled
        suggestJob = scope.launch {
            delay(120)
            if (typed != text) return@launch
            launch { requestCompletion(text) }
        }
    }

    private fun showArguments(text: String, server: List<String>?) {
        val local = CommandCompletion.suggest(text, players.map { it.name }, status?.worlds?.map { it.name }.orEmpty())
        suggestions = CommandCompletion.merge(server.orEmpty(), local).take(30).map { Suggestion(it) }
    }

    private suspend fun requestCompletion(text: String) {
        val result = runCatching { api.complete(text) }
        result.onSuccess { completionCache.put(text, it) }
        val now = typed
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

    fun broadcast(message: String) {
        scope.launch {
            try {
                api.broadcast(message)
            } catch (e: Exception) {
                messages.send(Notice(errorText(e)))
            }
        }
    }

    fun playerAction(player: String, action: String, body: JSONObject, success: String) {
        scope.launch {
            try {
                api.playerAction(player, action, body)
                // A ban can be taken back right away.
                messages.send(Notice(success, if (action == "ban") ({ unban(listOf(player)) }) else null))
            } catch (e: Exception) {
                messages.send(Notice(errorText(e)))
            }
        }
    }

    /** The same action for several players ("whitelist" adds them); one message at the end. */
    fun playerBatch(names: List<String>, action: String, body: JSONObject = JSONObject()) {
        if (names.isEmpty()) return
        scope.launch {
            val failed = mutableListOf<String>()
            for (name in names) {
                try {
                    if (action == "whitelist") api.whitelistAdd(name) else api.playerAction(name, action, body)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed += name
                }
            }
            val done = names - failed.toSet()
            val text = if (failed.isEmpty()) getPluralString(Res.plurals.players_batch_done, names.size, names.size)
            else getString(Res.string.players_batch_failed, failed.joinToString(", "))
            messages.send(Notice(text, if (action == "ban" && done.isNotEmpty()) ({ unban(done) }) else null))
        }
    }

    private fun unban(names: List<String>) {
        scope.launch {
            val failed = names.filter { runCatching { api.pardon(it) }.isFailure }
            messages.send(
                Notice(
                    if (failed.isEmpty()) getString(Res.string.bans_unbanned, names.joinToString(", "))
                    else getString(Res.string.players_batch_failed, failed.joinToString(", ")),
                ),
            )
        }
    }

    /** Fixed data for screenshot tests (no connection). */
    /** Removes a command from the recent commands. */
    fun forgetCommand(command: String) {
        history = historyStore.remove(profile.id, command)
    }

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
