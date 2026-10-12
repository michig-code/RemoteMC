package de.remotemc.app.data

import org.json.JSONArray
import org.json.JSONObject

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
    val minecraftVersion: String,
    val platform: String,
    val pluginVersion: String,
    val onlinePlayers: Int,
    val maxPlayers: Int,
    /** 1/5/15-minute TPS; empty if the server does not report it (Folia, Spigot). */
    val tps: List<Double>,
    /** Average tick time in ms, or null if unknown. */
    val mspt: Double?,
    val memoryUsedMb: Long,
    val memoryMaxMb: Long,
    val uptimeSeconds: Long,
    val worlds: List<WorldInfo>,
) {
    companion object {
        fun fromJson(o: JSONObject): ServerStatus {
            val tps = o.optJSONArray("tps") ?: JSONArray()
            val worlds = o.optJSONArray("worlds") ?: JSONArray()
            val mspt = o.optDouble("mspt", -1.0)
            return ServerStatus(
                motd = o.optString("motd"),
                version = o.optString("version"),
                minecraftVersion = o.optString("minecraftVersion"),
                platform = o.optString("platform"),
                pluginVersion = o.optString("pluginVersion"),
                onlinePlayers = o.optInt("onlinePlayers"),
                maxPlayers = o.optInt("maxPlayers"),
                tps = List(tps.length()) { tps.getDouble(it) },
                mspt = mspt.takeIf { it >= 0 },
                memoryUsedMb = o.optLong("memoryUsedMb"),
                memoryMaxMb = o.optLong("memoryMaxMb"),
                uptimeSeconds = o.optLong("uptimeSeconds"),
                worlds = List(worlds.length()) {
                    val w = worlds.getJSONObject(it)
                    WorldInfo(
                        name = w.optString("name"),
                        environment = w.optString("environment"),
                        players = w.optInt("players"),
                        loadedChunks = w.optInt("loadedChunks", -1),
                        entities = w.optInt("entities", -1),
                    )
                },
            )
        }
    }
}

data class PlayerInfo(
    val name: String,
    val uuid: String,
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val health: Double,
    val maxHealth: Double,
    val food: Int,
    val level: Int,
    val ping: Int,
    val gameMode: String,
    val op: Boolean,
) {
    companion object {
        fun listFromJson(arr: JSONArray?): List<PlayerInfo> {
            if (arr == null) return emptyList()
            return List(arr.length()) {
                val p = arr.getJSONObject(it)
                PlayerInfo(
                    name = p.optString("name"),
                    uuid = p.optString("uuid"),
                    world = p.optString("world"),
                    x = p.optInt("x"),
                    y = p.optInt("y"),
                    z = p.optInt("z"),
                    health = p.optDouble("health"),
                    maxHealth = p.optDouble("maxHealth", 20.0),
                    food = p.optInt("food"),
                    level = p.optInt("level"),
                    ping = p.optInt("ping"),
                    gameMode = p.optString("gameMode"),
                    op = p.optBoolean("op"),
                )
            }
        }
    }
}

data class ConsoleLine(val id: Long, val time: Long, val level: String, val message: String) {
    companion object {
        fun fromJson(o: JSONObject) = ConsoleLine(o.optLong("id"), o.optLong("time"), o.optString("level"), o.optString("message"))
    }
}

data class ChatEntry(val id: Long, val time: Long, val type: Type, val player: String, val message: String) {
    enum class Type { CHAT, JOIN, QUIT, BROADCAST }

    companion object {
        fun fromJson(o: JSONObject) = ChatEntry(
            id = o.optLong("id"),
            time = o.optLong("time"),
            type = Type.entries.firstOrNull { it.name.equals(o.optString("type"), true) } ?: Type.CHAT,
            player = o.optString("player"),
            message = o.optString("message"),
        )
    }
}

/** Who this device is on the server (GET /me and the stream's hello event). */
data class Session(
    val name: String,
    val role: Role,
    val commandsEnabled: Boolean,
    val capturesCommandOutput: Boolean,
) {
    companion object {
        fun fromJson(o: JSONObject) = Session(
            name = o.optString("name"),
            role = Role.parse(o.optString("role")),
            commandsEnabled = o.optBoolean("commandsEnabled", true),
            capturesCommandOutput = o.optBoolean("capturesCommandOutput", true),
        )
    }
}

data class CommandResult(val success: Boolean, val captured: Boolean, val output: List<String>)

data class PairResult(val token: String, val name: String, val role: Role)
