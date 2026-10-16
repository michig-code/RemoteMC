package de.remotemc.app.net

import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Pipe
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import kotlin.concurrent.thread
import kotlin.math.sin

/**
 * A made-up Minecraft server inside the app, to try RemoteMC without a server of one's own (and for
 * store reviewers). It answers the plugin's API for [HOST] with sample data that reacts to what is
 * done: kicks, bans, the whitelist, world settings, files and commands change it, and a live stream
 * sends status, console and chat like a real server. Nothing leaves the phone.
 */
object DemoServer {
    const val HOST = "demo.remotemc.invalid"
    const val ID = "demo"

    fun profile(name: String) = ServerProfile(
        id = ID, name = name, host = HOST, token = "demo", tls = false, role = Role.ADMIN, notify = false,
    )

    fun isDemo(profile: ServerProfile) = profile.host == HOST

    /** Answers requests to [HOST]; everything else goes to the network as usual. */
    val interceptor = Interceptor { chain ->
        val request = chain.request()
        if (request.url.host == HOST) state.handle(request) else chain.proceed(request)
    }

    private val state = State()
    private const val OWN_JAR = "plugins/RemoteMC-1.0.0.jar"

    private class Fail(val code: Int, val error: String, message: String) : Exception(message)

    private class State {
        private val start = System.currentTimeMillis()
        private var lastTick = start
        private var nextId = 1L
        private val console = ArrayDeque<JSONObject>()
        private val chat = ArrayDeque<JSONObject>()
        private val players = mutableListOf<JSONObject>()
        private val away = mutableListOf("Sam_Redstone")
        private val whitelist = mutableListOf("Lena_Builds", "Tobi_PvP", "Mira", "Sam_Redstone")
        private var whitelistOn = true
        private val bans = mutableListOf(ban("Griefer123", "Griefing at spawn"))
        private val worlds = linkedMapOf(
            "world" to world("world", "NORMAL", 1000, "NORMAL"),
            "world_nether" to world("world_nether", "NETHER", 18000, "NORMAL"),
            "world_the_end" to world("world_the_end", "THE_END", 6000, "NORMAL"),
        )
        private val files = sortedMapOf<String, String?>()
        private val trash = mutableMapOf<String, Pair<String, String?>>()
        private val schedules = mutableListOf(
            schedule("restart", "Nightly restart", "restart", daily = true, minutes = 0, time = "04:00", warn = 5),
            schedule("save", "Save worlds", "save-all", daily = false, minutes = 30, time = "04:00", warn = 0),
        )

        init {
            listOf("Lena_Builds" to "CREATIVE", "Tobi_PvP" to "SURVIVAL", "Mira" to "SURVIVAL").forEachIndexed { i, (name, mode) ->
                players += player(name, mode, op = i == 0, onlineMinutes = 35L - i * 12)
            }
            log("INFO", "Starting minecraft server version 1.21.4")
            log("INFO", "Preparing level \"world\"")
            log("INFO", "[RemoteMC] API listening on port 8765 (HTTPS)")
            log("INFO", "Done (6.214s)! For help, type \"help\"")
            players.forEach { log("INFO", "${it.getString("name")} joined the game") }
            say("Lena_Builds", "the new spawn is almost done :)")
            say("Tobi_PvP", "nice! can I help with the roof?")
            log("WARN", "Can't keep up! Is the server overloaded? Running 2104ms or 42 ticks behind")
            files["server.properties"] = SERVER_PROPERTIES
            files["eula.txt"] = "eula=true\n"
            files["whitelist.json"] = ""
            files["plugins"] = null
            files[OWN_JAR] = ""
            files["plugins/RemoteMC"] = null
            files["plugins/RemoteMC/config.yml"] = "port: 8765\nlanguage: en\nupdate-check: true\n"
            files["logs"] = null
            files["world"] = null
        }

        @Synchronized
        fun handle(request: Request): Response {
            advance()
            val path = request.url.encodedPath
            val body = request.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            return try {
                if (path == "/api/v1/events") return stream(request)
                respond(request, 200, route(request.method, path, request, json))
            } catch (f: Fail) {
                respond(request, f.code, JSONObject().put("error", f.error).put("message", f.message))
            }
        }

        private fun route(method: String, path: String, request: Request, body: JSONObject): JSONObject {
            val parts = path.removePrefix("/api/v1/").split('/').map { URLDecoder.decode(it, "UTF-8") }
            val q = { name: String -> request.url.queryParameter(name).orEmpty() }
            return when ("$method ${parts[0]}") {
                "GET ping" -> JSONObject().put("app", "RemoteMC").put("version", "1.0.0").put("apiVersion", 1).put("platform", "Paper")
                "GET me" -> me()
                "GET status" -> status()
                "GET players" -> if (parts.size == 3 && parts[2] == "inventory") inventory(online(parts[1])) else JSONObject().put("players", JSONArray(players))
                "POST players" -> playerAction(parts[1], parts[2], body)
                "GET console" -> JSONObject().put("lines", JSONArray(console.filter { it.getLong("id") > (q("since").toLongOrNull() ?: 0) }))
                "GET chat" -> JSONObject().put("messages", JSONArray(chat.filter { it.getLong("id") > (q("since").toLongOrNull() ?: 0) })).put("last", nextId - 1)
                "POST chat" -> ok().also { broadcast(body.optString("message")) }
                "POST command" -> command(body.optString("command"))
                "GET commands" -> JSONObject().put("commands", JSONArray(COMMANDS.map { (name, plugin) -> JSONObject().put("name", name).put("plugin", plugin).put("description", "").put("usage", name).put("aliases", JSONArray()) }))
                "GET complete" -> JSONObject().put("suggestions", JSONArray(complete(q("buffer"))))
                "GET whitelist" -> whitelist()
                "POST whitelist" -> when (parts.getOrNull(1)) {
                    "add" -> body.optString("name").trim().let { n -> if (n.isNotEmpty() && whitelist.none { it.equals(n, true) }) whitelist += n; whitelist() }
                    "remove" -> whitelist.removeAll { it.equals(body.optString("name"), true) }.let { whitelist() }
                    else -> { whitelistOn = body.optBoolean("enabled"); whitelist() }
                }
                "GET bans" -> JSONObject().put("bans", JSONArray(bans))
                "POST bans" -> { bans.removeAll { it.getString("name").equals(parts[1], true) }; log("INFO", "Unbanned ${parts[1]}"); JSONObject().put("bans", JSONArray(bans)) }
                "GET worlds" -> worldDetails(parts[1])
                "POST worlds" -> worldAction(parts[1], parts[2], body)
                "GET history" -> history(q("range"))
                "GET plugins" -> JSONObject().put("plugins", JSONArray(PLUGINS.map { (name, version, description) ->
                    JSONObject().put("name", name).put("version", version).put("enabled", true).put("description", description).put("authors", JSONArray())
                }))
                "GET schedules" -> schedulesJson()
                "POST schedules" -> scheduleAction(parts.getOrNull(1), parts.getOrNull(2), body)
                "GET files" -> if (parts.getOrNull(1) == "content") readFile(q("path")) else listFiles(q("path"))
                "POST files" -> fileAction(parts[1], body, q("path"), request)
                else -> throw Fail(404, "not_found", "Unknown endpoint")
            }
        }

        // ── Live stream ────────────────────────────────────────────────

        private fun stream(request: Request): Response {
            val pipe = Pipe(64 * 1024)
            val sink = pipe.sink.buffer()
            var consoleSent = console.lastOrNull()?.getLong("id") ?: 0
            var chatSent = chat.lastOrNull()?.getLong("id") ?: 0
            val hello = me().put("snapshot", JSONObject().put("status", status()).put("players", JSONArray(players)))
                .put("consoleLast", consoleSent).put("chatLast", chatSent)
            thread(isDaemon = true, name = "demo-stream") {
                try {
                    sink.writeUtf8("retry: 3000\n\nevent: hello\ndata: $hello\n\n").flush()
                    while (true) {
                        Thread.sleep(2000)
                        val events = synchronized(this) {
                            advance()
                            buildList {
                                console.filter { it.getLong("id") > consoleSent }.forEach { add("console" to it) }
                                chat.filter { it.getLong("id") > chatSent }.forEach { add("chat" to it) }
                                add("status" to JSONObject().put("status", status()).put("players", JSONArray(players)))
                                consoleSent = console.lastOrNull()?.getLong("id") ?: consoleSent
                                chatSent = chat.lastOrNull()?.getLong("id") ?: chatSent
                            }
                        }
                        events.forEach { (type, data) -> sink.writeUtf8("event: $type\ndata: $data\n\n") }
                        sink.flush()
                    }
                } catch (_: IOException) {
                    // The app closed the stream.
                } catch (_: InterruptedException) {
                }
                runCatching { sink.close() }
            }
            return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(pipe.source.buffer().asResponseBody("text/event-stream".toMediaType())).build()
        }

        /** Something happens every few seconds, like on a real server. */
        private fun advance() {
            val now = System.currentTimeMillis()
            val steps = ((now - lastTick) / 7000).toInt().coerceAtMost(5)
            repeat(steps) { i ->
                lastTick += 7000
                when ((lastTick / 7000 % 9).toInt()) {
                    0 -> players.randomOrNull()?.let { say(it.getString("name"), CHATTER.random()) }
                    3 -> log("INFO", "Saved the game")
                    5 -> if (away.isNotEmpty() && players.size < 4) join(away.removeAt(0))
                    7 -> players.randomOrNull()?.let { say(it.getString("name"), CHATTER.random()) }
                    else -> if (i == steps - 1) players.forEach { p -> p.put("ping", (20..140).random()) }
                }
            }
            if (steps > 0) lastTick = now - (now - lastTick) % 7000
        }

        // ── Answers ────────────────────────────────────────────────────

        private fun me() = JSONObject().put("name", "Demo").put("role", "admin").put("commandsEnabled", true)
            .put("capturesCommandOutput", true).put("apiVersion", 1)

        private fun status(): JSONObject {
            val t = (System.currentTimeMillis() - start) / 1000.0
            val tps = (19.6 + 0.4 * sin(t / 40)).coerceAtMost(20.0)
            worlds.values.forEach { it.put("time", (it.getLong("time") + 40) % 24000) }
            return JSONObject()
                .put("motd", "RemoteMC demo server").put("version", "1.21.4 (demo)").put("minecraftVersion", "1.21.4")
                .put("platform", "Paper").put("pluginVersion", "1.0.0")
                .put("features", JSONArray(listOf("history", "moderation", "worlds", "plugins", "schedules", "teleport", "files", "inventory")))
                .put("onlinePlayers", players.size).put("maxPlayers", 20)
                .put("tps", JSONArray(listOf(round1(tps), 19.8, 19.9))).put("mspt", round1(18 + 6 * sin(t / 15)))
                .put("memoryUsedMb", (2300 + 400 * sin(t / 60)).toInt()).put("memoryMaxMb", 4096)
                .put("uptimeSeconds", 3 * 3600 + (System.currentTimeMillis() - start) / 1000)
                .put("worlds", JSONArray(worlds.values.map { w ->
                    JSONObject().put("name", w.getString("name")).put("environment", w.getString("environment"))
                        .put("players", players.count { it.getString("world") == w.getString("name") })
                        .put("loadedChunks", 420).put("entities", 180).put("time", w.getLong("time")).put("storm", w.getBoolean("storm"))
                }))
        }

        private fun whitelist() = JSONObject().put("enabled", whitelistOn).put("players", JSONArray(whitelist))

        private fun online(name: String) = players.firstOrNull { it.getString("name").equals(name, true) }
            ?: throw Fail(404, "player_offline", "Player $name is not online")

        private fun playerAction(name: String, action: String, body: JSONObject): JSONObject {
            if (action == "ban") {
                bans.removeAll { it.getString("name").equals(name, true) }
                bans += ban(name, body.optString("reason"))
                players.firstOrNull { it.getString("name").equals(name, true) }?.let { leave(it, "Banned") }
                return ok()
            }
            val p = online(name)
            when (action) {
                "kick" -> leave(p, body.optString("reason").ifBlank { "Kicked from the server" })
                "op", "deop" -> p.put("op", action == "op")
                "heal" -> p.put("health", 20.0).put("food", 20)
                "gamemode" -> p.put("gameMode", body.optString("mode").uppercase())
                "message" -> log("INFO", "[Server -> $name] ${body.optString("message")}")
                "teleport" -> log("INFO", "Teleported $name")
                else -> throw Fail(404, "not_found", "Unknown action: $action")
            }
            return ok()
        }

        private fun inventory(p: JSONObject) = JSONObject(INVENTORY).also { it.put("selectedSlot", p.getString("name").length % 9) }

        private fun command(raw: String): JSONObject {
            val cmd = raw.trim().removePrefix("/")
            val args = cmd.split(' ').filter { it.isNotEmpty() }
            log("INFO", "Demo issued server command: /$cmd")
            val output: List<String> = when (args.firstOrNull()?.lowercase()) {
                null -> emptyList()
                "say" -> listOf("[Server] ${args.drop(1).joinToString(" ")}").also { broadcast(args.drop(1).joinToString(" ")) }
                "list" -> listOf("There are ${players.size} of a max of 20 players online: " + players.joinToString(", ") { it.getString("name") })
                "time" -> args.getOrNull(2)?.let { worldAction("world", "time", JSONObject().put("time", it)); listOf("Set the time to ${worlds.getValue("world").getLong("time")}") }
                    ?: listOf("The time is ${worlds.getValue("world").getLong("time")}")
                "weather" -> worldAction("world", "weather", JSONObject().put("weather", args.getOrElse(1) { "clear" })).let { listOf("Set the weather to ${args.getOrElse(1) { "clear" }}") }
                "save-all" -> listOf("Saving the game (this may take a moment!)", "Saved the game")
                "tps" -> listOf("TPS from last 1m, 5m, 15m: 19.9, 19.8, 19.9")
                "seed" -> listOf("Seed: [4815162342]")
                "stop", "restart", "reload" -> listOf("This is the demo server – it keeps running.")
                "kick", "ban", "op", "deop" -> args.getOrNull(1)?.let { n ->
                    runCatching { playerAction(n, args[0].lowercase(), JSONObject().put("reason", args.drop(2).joinToString(" "))) }
                        .fold({ listOf("Done: ${args[0]} $n") }, { listOf("No player was found") })
                } ?: listOf("Unknown or incomplete command")
                else -> if (COMMANDS.any { it.first == args[0].lowercase() }) listOf("Executed /$cmd") else listOf("Unknown or incomplete command, see below for error")
            }
            output.forEach { log("INFO", it) }
            return JSONObject().put("success", true).put("captured", true).put("output", JSONArray(output)).put("formatted", JSONArray(output))
        }

        private fun complete(buffer: String): List<String> {
            val words = buffer.removePrefix("/").split(' ')
            val last = words.last()
            val options = if (words.size == 1) COMMANDS.map { it.first } else when (words[0].lowercase()) {
                "time" -> if (words.size == 2) listOf("add", "query", "set") else listOf("day", "night", "noon", "midnight")
                "weather" -> listOf("clear", "rain", "thunder")
                "gamemode", "defaultgamemode" -> if (words.size == 2) listOf("adventure", "creative", "spectator", "survival") else players.map { it.getString("name") }
                "difficulty" -> listOf("easy", "hard", "normal", "peaceful")
                else -> players.map { it.getString("name") }
            }
            return options.filter { it.startsWith(last, ignoreCase = true) }.take(40)
        }

        private fun worldDetails(name: String): JSONObject {
            val w = worlds[name] ?: throw Fail(404, "world_unknown", "No world $name")
            return JSONObject(w.toString()).put("gamerules", JSONArray(w.getJSONObject("rules").keys().asSequence().sorted().map { rule ->
                val v = w.getJSONObject("rules").get(rule)
                JSONObject().put("name", rule).put("type", if (v is Boolean) "boolean" else "int").put("value", v)
            }.toList())).apply { remove("rules") }
        }

        private fun worldAction(name: String, action: String, body: JSONObject): JSONObject {
            val w = worlds[name] ?: throw Fail(404, "world_unknown", "No world $name")
            if ((action == "time" || action == "weather") && w.getString("environment") in setOf("NETHER", "THE_END")) {
                throw Fail(400, "no_sky", "The Nether and the End have no time of day and no weather")
            }
            when (action) {
                "time" -> w.put("time", when (val t = body.optString("time")) {
                    "morning" -> 0; "day" -> 1000; "noon" -> 6000; "evening" -> 12000; "night" -> 13000; "midnight" -> 18000
                    else -> t.toIntOrNull() ?: 1000
                })
                "weather" -> body.optString("weather").let { w.put("storm", it != "clear").put("thunder", it == "thunder") }
                "difficulty" -> w.put("difficulty", body.optString("difficulty").uppercase())
                "gamerule" -> w.getJSONObject("rules").put(body.optString("rule"), body.get("value"))
                else -> throw Fail(404, "not_found", "Unknown world action: $action")
            }
            log("INFO", "[RemoteMC] Demo: $name $action")
            return worldDetails(name)
        }

        private fun history(range: String): JSONObject {
            val span = when (range) { "1h" -> 3_600_000L; "7d" -> 7 * 86_400_000L; else -> 86_400_000L }
            val now = System.currentTimeMillis()
            val step = span / 180
            val points = (0 until 180).map { i ->
                val t = now - span + i * step
                val hour = (t / 3_600_000 % 24).toDouble()
                val busy = (sin((hour - 14) / 24 * 2 * Math.PI) + 1) / 2
                val count = (busy * 6).toInt()
                JSONArray(listOf(t, round1(20 - busy * 1.2 - (if (i % 37 == 0) 3.0 else 0.0)), round1(8 + busy * 25), (1800 + busy * 1200 + (i * 53 % 300)).toInt(), count))
            }
            return JSONObject().put("from", now - span).put("to", now).put("step", step).put("points", JSONArray(points))
        }

        private fun schedulesJson() = JSONObject().put("schedules", JSONArray(schedules)).put("timezone", "Europe/Berlin").put("now", System.currentTimeMillis())

        private fun scheduleAction(id: String?, action: String?, body: JSONObject): JSONObject {
            if (id == null) {
                val newId = body.optString("id").ifBlank { "task${nextId++}" }
                schedules.removeAll { it.getString("id") == newId }
                schedules += schedule(newId, body.optString("name"), body.optString("command"), body.optString("type") == "daily",
                    body.optInt("minutes"), body.optString("time", "04:00"), body.optInt("warn")).put("enabled", body.optBoolean("enabled", true))
                return schedulesJson()
            }
            val task = schedules.firstOrNull { it.getString("id") == id } ?: throw Fail(404, "schedule_unknown", "No scheduled task $id")
            when (action) {
                "delete" -> schedules.remove(task)
                "run" -> { command(task.getString("command")); task.put("lastRun", System.currentTimeMillis()) }
                else -> throw Fail(404, "not_found", "Unknown action: $action")
            }
            return schedulesJson()
        }

        private fun listFiles(path: String): JSONObject {
            val dir = path.trim('/')
            if (dir.isNotEmpty() && files[dir] != null) throw Fail(400, "not_a_folder", "Not a folder")
            val prefix = if (dir.isEmpty()) "" else "$dir/"
            val entries = files.filterKeys { it.startsWith(prefix) && it.length > prefix.length && '/' !in it.substring(prefix.length) }
                .map { (p, content) -> entry(p, content) }
                .sortedWith(compareBy({ !it.getBoolean("dir") }, { it.getString("name").lowercase() }))
            val logs = if (dir == "logs") listOf(entry("logs/latest.log", logText())) else emptyList()
            return JSONObject().put("path", dir).put("entries", JSONArray(entries + logs))
        }

        private fun readFile(path: String): JSONObject {
            val p = path.trim('/')
            val content = if (p == "logs/latest.log") logText() else files[p] ?: throw Fail(404, "not_found", "No such file")
            if (p.endsWith(".jar")) throw Fail(415, "binary_file", "This is not a text file")
            return JSONObject().put("path", p).put("content", content).put("modified", start)
        }

        private fun fileAction(action: String, body: JSONObject, query: String, request: Request): JSONObject {
            val p = body.optString("path", query).trim('/')
            return when (action) {
                "content" -> { requireUnlocked(p); files[p] = body.optString("content"); entry(p, files[p]) }
                "upload" -> { requireUnlocked(p); files[p] = ""; entry(p, "x".repeat((request.body?.contentLength() ?: 0).toInt().coerceIn(0, 1_000_000))) }
                "mkdir" -> { files[p] = null; entry(p, null) }
                "delete" -> {
                    if (p !in files) throw Fail(404, "not_found", "No such file")
                    requireUnlocked(p)
                    val id = "t${nextId++}"
                    trash[id] = p to files.remove(p)
                    files.keys.filter { it.startsWith("$p/") }.forEach { files.remove(it) }
                    JSONObject().put("trash", id)
                }
                "restore" -> {
                    val (path, content) = trash.remove(body.optString("trash")) ?: throw Fail(404, "not_found", "Not in the trash")
                    files[path] = content
                    entry(path, content)
                }
                else -> throw Fail(404, "not_found", "Unknown endpoint")
            }
        }

        // ── Helpers ────────────────────────────────────────────────────

        private fun entry(path: String, content: String?) = JSONObject().put("path", path).put("name", path.substringAfterLast('/'))
            .put("dir", content == null).put("size", if (path.endsWith(".jar")) 225_707 else content?.length ?: 0).put("modified", start)
            .apply { if (isLocked(path)) put("locked", true) }

        /** Like the plugin: RemoteMC's own JAR (and the folder it is in) cannot be deleted or replaced. */
        private fun isLocked(path: String) = OWN_JAR == path || OWN_JAR.startsWith("$path/")

        private fun requireUnlocked(path: String) {
            if (isLocked(path)) throw Fail(403, "forbidden", "RemoteMC itself cannot be deleted or replaced from the app")
        }

        private fun logText() = console.joinToString("\n") { "[${it.getString("level")}] ${it.getString("message")}" }

        private fun log(level: String, message: String) {
            console += JSONObject().put("id", nextId++).put("time", System.currentTimeMillis()).put("level", level).put("message", message)
            while (console.size > 500) console.removeFirst()
        }

        private fun addChat(type: String, player: String, message: String) {
            chat += JSONObject().put("id", nextId++).put("time", System.currentTimeMillis()).put("type", type).put("player", player).put("message", message)
            while (chat.size > 200) chat.removeFirst()
        }

        private fun say(player: String, message: String) {
            log("INFO", "<$player> $message")
            addChat("chat", player, message)
        }

        private fun broadcast(message: String) {
            log("INFO", "[Server] $message")
            addChat("broadcast", "", message)
        }

        private fun join(name: String) {
            players += player(name, "SURVIVAL", op = false, onlineMinutes = 0)
            log("INFO", "$name joined the game")
            addChat("join", name, "")
        }

        private fun leave(p: JSONObject, reason: String) {
            val name = p.getString("name")
            players.remove(p)
            if (bans.none { it.getString("name").equals(name, true) }) away += name
            log("INFO", "$name lost connection: $reason")
            addChat("quit", name, "")
        }

        private fun player(name: String, mode: String, op: Boolean, onlineMinutes: Long): JSONObject {
            val now = System.currentTimeMillis()
            val seed = name.hashCode()
            return JSONObject().put("name", name).put("uuid", "00000000-0000-4000-8000-%012x".format(seed.toLong() and 0xffffffffL))
                .put("world", if (seed % 5 == 0) "world_nether" else "world").put("x", seed % 900).put("y", 64 + seed % 20).put("z", (seed / 7) % 900)
                .put("health", 14.0 + seed.mod(7)).put("maxHealth", 20.0).put("food", 14 + seed.mod(7)).put("level", 5 + seed.mod(30))
                .put("ping", 20 + seed.mod(90)).put("gameMode", mode).put("op", op).put("skin", "")
                .put("playtime", 3600L * (4 + seed.mod(80))).put("firstPlayed", now - 86_400_000L * (3 + seed.mod(60)))
                .put("onlineSince", now - onlineMinutes * 60_000)
        }

        private fun ok() = JSONObject().put("ok", true)

        private fun round1(v: Double) = Math.round(v * 10) / 10.0

        private fun respond(request: Request, code: Int, json: JSONObject): Response =
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message(if (code == 200) "OK" else "Error")
                .body(json.toString().toResponseBody("application/json".toMediaType())).build()
    }

    private fun ban(name: String, reason: String) = JSONObject().put("name", name).put("reason", reason.ifBlank { "Banned by an operator." })
        .put("source", "Demo").put("created", System.currentTimeMillis() - 3_600_000)

    private fun world(name: String, environment: String, time: Int, difficulty: String) = JSONObject()
        .put("name", name).put("environment", environment).put("time", time).put("storm", false).put("thunder", false)
        .put("difficulty", difficulty).put("spawn", JSONObject().put("x", 0).put("y", 64).put("z", 0))
        .put("rules", JSONObject().put("doDaylightCycle", true).put("doWeatherCycle", true).put("keepInventory", false)
            .put("mobGriefing", true).put("doFireTick", true).put("doMobSpawning", true).put("pvp", true)
            .put("naturalRegeneration", true).put("showDeathMessages", true).put("announceAdvancements", true)
            .put("playersSleepingPercentage", 100).put("randomTickSpeed", 3).put("spawnRadius", 10))

    private fun schedule(id: String, name: String, command: String, daily: Boolean, minutes: Int, time: String, warn: Int) = JSONObject()
        .put("id", id).put("name", name).put("command", command).put("type", if (daily) "daily" else "interval")
        .put("minutes", minutes).put("time", time).put("warn", warn).put("enabled", true)
        .put("nextRun", System.currentTimeMillis() + if (daily) 6 * 3_600_000L else minutes * 60_000L).put("lastRun", 0)

    private val CHATTER = listOf(
        "anyone up for the nether?", "brb", "who took my diamonds?? :D", "gg", "the farm is working now",
        "can someone tp me to spawn?", "lag?", "night is coming, beds!", "found a village at 300 -120",
    )

    private val COMMANDS = listOf(
        "advancement", "ban", "banlist", "clear", "difficulty", "effect", "enchant", "gamemode", "gamerule", "give",
        "kick", "kill", "list", "locate", "op", "deop", "pardon", "particle", "playsound", "save-all", "say", "seed",
        "setworldspawn", "spawnpoint", "summon", "tell", "time", "title", "tp", "weather", "whitelist", "worldborder", "xp",
    ).map { it to "Minecraft" } + listOf("tps" to "Paper", "mspt" to "Paper", "plugins" to "Bukkit", "version" to "Bukkit", "remotemc" to "RemoteMC")

    private val PLUGINS = listOf(
        Triple("RemoteMC", "1.0.0", "Manage your server from your phone with the RemoteMC app"),
        Triple("DemoPermissions", "2.3.1", "Example plugin on the demo server"),
        Triple("DemoHomes", "1.4.0", "Example plugin on the demo server"),
    )

    private const val SERVER_PROPERTIES = """#Minecraft server properties (demo)
difficulty=normal
gamemode=survival
max-players=20
motd=RemoteMC demo server
online-mode=true
pvp=true
simulation-distance=10
spawn-protection=16
view-distance=10
white-list=true
"""

    private const val INVENTORY = """{"selectedSlot":1,
 "items":[{"id":"minecraft:diamond_sword","amount":1,"enchants":["sharpness 3","unbreaking 2"],"durability":900,"maxDurability":1561,"slot":0},
          {"id":"minecraft:diamond_pickaxe","amount":1,"durability":200,"maxDurability":1561,"slot":1},
          {"id":"minecraft:cooked_beef","amount":24,"slot":2},{"id":"minecraft:torch","amount":64,"slot":3},
          {"id":"minecraft:oak_log","amount":32,"slot":9},{"id":"minecraft:cobblestone","amount":64,"slot":10},
          {"id":"minecraft:iron_ingot","amount":17,"slot":11},{"id":"minecraft:redstone","amount":40,"slot":20}],
 "armor":[{"id":"minecraft:iron_boots","amount":1,"slot":0},{"id":"minecraft:diamond_chestplate","amount":1,"enchants":["protection 4"],"slot":2},
          {"id":"minecraft:iron_helmet","amount":1,"slot":3}],
 "offhand":[{"id":"minecraft:shield","amount":1,"durability":300,"maxDurability":336,"slot":0}],
 "enderChest":[{"id":"minecraft:emerald","amount":5,"slot":0}]}"""
}
