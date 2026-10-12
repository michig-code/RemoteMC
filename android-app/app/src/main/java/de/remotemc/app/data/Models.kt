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
    /** Skin texture URL from the player's profile (plugin 2.1+), used to draw the real head. */
    val skin: String? = null,
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
                    skin = p.optString("skin").takeIf { it.startsWith("https://") },
                )
            }
        }
    }
}

/** [formatted] keeps Minecraft colour codes (§a…) when the line has colours. */
data class ConsoleLine(val id: Long, val time: Long, val level: String, val message: String, val formatted: String? = null) {
    companion object {
        fun fromJson(o: JSONObject) = ConsoleLine(
            o.optLong("id"), o.optLong("time"), o.optString("level"), o.optString("message"),
            o.optString("formatted").takeIf { it.isNotEmpty() },
        )
    }
}

/** A console command known to the server, for suggestions. */
data class CommandInfo(val name: String, val description: String?, val usage: String?, val aliases: List<String>)

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

/** [formatted] holds the same lines with colour codes (plugin 2.1+); empty for older plugins. */
data class CommandResult(val success: Boolean, val captured: Boolean, val output: List<String>, val formatted: List<String> = emptyList())

data class PairResult(val token: String, val name: String, val role: Role)

/** Time span of the performance history. */
enum class HistoryRange(val param: String) { HOUR("1h"), DAY("24h"), WEEK("7d") }

/** One averaged point of the performance history; tps/mspt are null if the server does not report them. */
data class HistoryPoint(val time: Long, val tps: Double?, val mspt: Double?, val memoryMb: Long, val players: Int)

data class PerformanceHistory(val from: Long, val to: Long, val step: Long, val points: List<HistoryPoint>) {
    companion object {
        fun fromJson(o: JSONObject): PerformanceHistory {
            val arr = o.optJSONArray("points") ?: JSONArray()
            val points = List(arr.length()) {
                val p = arr.getJSONArray(it)
                HistoryPoint(
                    time = p.getLong(0),
                    tps = p.getDouble(1).takeIf { v -> v >= 0 },
                    mspt = p.getDouble(2).takeIf { v -> v >= 0 },
                    memoryMb = p.getLong(3),
                    players = p.getInt(4),
                )
            }
            return PerformanceHistory(o.optLong("from"), o.optLong("to"), o.optLong("step", 60_000), points)
        }
    }
}

data class Whitelist(val enabled: Boolean, val players: List<String>) {
    companion object {
        fun fromJson(o: JSONObject): Whitelist {
            val arr = o.optJSONArray("players") ?: JSONArray()
            return Whitelist(o.optBoolean("enabled"), List(arr.length()) { arr.getString(it) })
        }
    }
}

/** A player ban; times in ms, expires null = permanent. */
data class BanInfo(val name: String, val reason: String?, val source: String?, val created: Long?, val expires: Long?) {
    companion object {
        fun listFromJson(o: JSONObject): List<BanInfo> {
            val arr = o.optJSONArray("bans") ?: JSONArray()
            return List(arr.length()) {
                val b = arr.getJSONObject(it)
                BanInfo(
                    name = b.getString("name"),
                    reason = b.optString("reason").takeIf { r -> r.isNotBlank() },
                    source = b.optString("source").takeIf { r -> r.isNotBlank() },
                    created = if (b.has("created")) b.getLong("created") else null,
                    expires = if (b.has("expires")) b.getLong("expires") else null,
                )
            }
        }
    }
}

data class GameRuleInfo(val name: String, val isBoolean: Boolean, val value: String)

/** One world's settings for the world controls (admin). */
data class WorldDetails(
    val name: String,
    val environment: String,
    val time: Long,
    val storm: Boolean,
    val thunder: Boolean,
    val difficulty: String,
    val gameRules: List<GameRuleInfo>,
) {
    companion object {
        fun fromJson(o: JSONObject): WorldDetails {
            val rules = o.optJSONArray("gamerules") ?: JSONArray()
            return WorldDetails(
                name = o.getString("name"),
                environment = o.optString("environment"),
                time = o.optLong("time"),
                storm = o.optBoolean("storm"),
                thunder = o.optBoolean("thunder"),
                difficulty = o.optString("difficulty"),
                gameRules = List(rules.length()) {
                    val r = rules.getJSONObject(it)
                    GameRuleInfo(r.getString("name"), r.optString("type") == "boolean", r.get("value").toString())
                },
            )
        }
    }
}

data class PluginInfo(
    val name: String,
    val version: String,
    val enabled: Boolean,
    val description: String?,
    val authors: List<String>,
) {
    companion object {
        fun listFromJson(o: JSONObject): List<PluginInfo> {
            val arr = o.optJSONArray("plugins") ?: JSONArray()
            return List(arr.length()) {
                val p = arr.getJSONObject(it)
                val authors = p.optJSONArray("authors") ?: JSONArray()
                PluginInfo(
                    p.getString("name"), p.optString("version"), p.optBoolean("enabled", true),
                    p.optString("description").takeIf { d -> d.isNotBlank() },
                    List(authors.length()) { i -> authors.getString(i) },
                )
            }
        }
    }
}

/** A scheduled console command: every [minutes], or daily at [time] ("HH:mm", server time zone). */
data class ScheduleInfo(
    val id: String?,
    val name: String,
    val command: String,
    val daily: Boolean,
    val minutes: Int,
    val time: String,
    val warn: Int,
    val enabled: Boolean,
    val nextRun: Long = 0,
    val lastRun: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id ?: "")
        .put("name", name)
        .put("command", command)
        .put("type", if (daily) "daily" else "interval")
        .put("minutes", minutes)
        .put("time", time)
        .put("warn", warn)
        .put("enabled", enabled)
}

data class ScheduleList(val schedules: List<ScheduleInfo>, val timezone: String, val serverNow: Long) {
    companion object {
        fun fromJson(o: JSONObject): ScheduleList {
            val arr = o.optJSONArray("schedules") ?: JSONArray()
            return ScheduleList(
                List(arr.length()) {
                    val t = arr.getJSONObject(it)
                    ScheduleInfo(
                        id = t.getString("id"),
                        name = t.optString("name"),
                        command = t.optString("command"),
                        daily = t.optString("type") == "daily",
                        minutes = t.optInt("minutes"),
                        time = t.optString("time", "04:00"),
                        warn = t.optInt("warn"),
                        enabled = t.optBoolean("enabled", true),
                        nextRun = t.optLong("nextRun"),
                        lastRun = t.optLong("lastRun"),
                    )
                },
                o.optString("timezone"),
                o.optLong("now"),
            )
        }
    }
}
