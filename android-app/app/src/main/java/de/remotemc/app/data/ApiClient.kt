package de.remotemc.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiException(message: String) : Exception(message)

data class WorldInfo(
    val name: String,
    val environment: String,
    val players: Int,
    val loadedChunks: Int,
    val entities: Int,
)

data class ServerStatus(
    val motd: String,
    val version: String,
    val onlinePlayers: Int,
    val maxPlayers: Int,
    val tps: List<Double>,
    val mspt: Double,
    val memoryUsedMb: Long,
    val memoryMaxMb: Long,
    val uptimeSeconds: Long,
    val worlds: List<WorldInfo>,
)

data class PlayerInfo(
    val name: String,
    val uuid: String,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val health: Double,
    val food: Int,
    val level: Int,
    val ping: Int,
    val gameMode: String,
    val op: Boolean,
)

data class ConsoleLine(val id: Long, val time: Long, val level: String, val message: String)

data class CommandResult(val success: Boolean, val output: List<String>)

/** Talks to the RemoteMC plugin's REST API. */
class ApiClient(private val profile: ServerProfile) {

    suspend fun ping(): String = request("GET", "/api/ping", auth = false).getString("version")

    suspend fun status(): ServerStatus {
        val o = request("GET", "/api/status")
        val tps = o.getJSONArray("tps")
        val worlds = o.getJSONArray("worlds")
        return ServerStatus(
            motd = o.optString("motd"),
            version = o.optString("version"),
            onlinePlayers = o.getInt("onlinePlayers"),
            maxPlayers = o.getInt("maxPlayers"),
            tps = List(tps.length()) { tps.getDouble(it) },
            mspt = o.optDouble("mspt", 0.0),
            memoryUsedMb = o.getLong("memoryUsedMb"),
            memoryMaxMb = o.getLong("memoryMaxMb"),
            uptimeSeconds = o.getLong("uptimeSeconds"),
            worlds = List(worlds.length()) {
                val w = worlds.getJSONObject(it)
                WorldInfo(
                    name = w.getString("name"),
                    environment = w.getString("environment"),
                    players = w.getInt("players"),
                    loadedChunks = w.getInt("loadedChunks"),
                    entities = w.getInt("entities"),
                )
            },
        )
    }

    suspend fun players(): List<PlayerInfo> {
        val arr = request("GET", "/api/players").getJSONArray("players")
        return List(arr.length()) {
            val p = arr.getJSONObject(it)
            PlayerInfo(
                name = p.getString("name"),
                uuid = p.getString("uuid"),
                world = p.getString("world"),
                x = p.getInt("x"),
                y = p.getInt("y"),
                z = p.getInt("z"),
                health = p.getDouble("health"),
                food = p.getInt("food"),
                level = p.getInt("level"),
                ping = p.getInt("ping"),
                gameMode = p.getString("gameMode"),
                op = p.getBoolean("op"),
            )
        }
    }

    suspend fun console(since: Long): Pair<List<ConsoleLine>, Long> {
        val o = request("GET", "/api/console?since=$since")
        val arr = o.getJSONArray("lines")
        val lines = List(arr.length()) {
            val l = arr.getJSONObject(it)
            ConsoleLine(l.getLong("id"), l.getLong("time"), l.getString("level"), l.getString("message"))
        }
        return lines to o.getLong("last")
    }

    suspend fun command(command: String): CommandResult {
        val o = request("POST", "/api/command", JSONObject().put("command", command))
        val out = o.getJSONArray("output")
        return CommandResult(o.getBoolean("success"), List(out.length()) { out.getString(it) })
    }

    suspend fun chat(message: String) {
        request("POST", "/api/chat", JSONObject().put("message", message))
    }

    suspend fun playerAction(player: String, action: String, body: JSONObject = JSONObject()) {
        request("POST", "/api/players/${android.net.Uri.encode(player)}/$action", body)
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        auth: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val conn = try {
            URL(profile.baseUrl + path).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw ApiException("Ungültige Adresse: ${e.message}")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 5000
            conn.readTimeout = 15000
            conn.setRequestProperty("Accept", "application/json")
            if (auth) conn.setRequestProperty("Authorization", "Bearer ${profile.token}")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (code !in 200..299) {
                throw ApiException(json?.optString("error")?.takeIf { it.isNotBlank() } ?: "HTTP $code")
            }
            json ?: throw ApiException("Ungültige Antwort vom Server")
        } catch (e: IOException) {
            throw ApiException("Server nicht erreichbar (${e.javaClass.simpleName}: ${e.message})")
        } finally {
            conn.disconnect()
        }
    }
}
