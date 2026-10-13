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
    /** Features of the plugin (empty for plugins older than this list): history, moderation, … */
    val features: Set<String> = emptySet(),
) {
    fun supports(feature: String) = feature in features

    /** Same fields as the plugin sends (for the saved snapshot, see [ServerSnapshot]). */
    fun toJson(): JSONObject = JSONObject()
        .put("motd", motd)
        .put("version", version)
        .put("minecraftVersion", minecraftVersion)
        .put("platform", platform)
        .put("pluginVersion", pluginVersion)
        .put("onlinePlayers", onlinePlayers)
        .put("maxPlayers", maxPlayers)
        .put("tps", JSONArray(tps))
        .put("mspt", mspt ?: -1.0)
        .put("memoryUsedMb", memoryUsedMb)
        .put("memoryMaxMb", memoryMaxMb)
        .put("uptimeSeconds", uptimeSeconds)
        .put("worlds", JSONArray(worlds.map {
            JSONObject().put("name", it.name).put("environment", it.environment).put("players", it.players)
                .put("loadedChunks", it.loadedChunks).put("entities", it.entities)
        }))
        .put("features", JSONArray(features.sorted()))

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
                features = o.optJSONArray("features")?.let { f -> List(f.length()) { f.getString(it) }.toSet() }.orEmpty(),
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
    /** Total time played on this server in seconds; -1 if unknown. */
    val playtime: Long = -1,
    /** First join on this server (ms); 0 if unknown. */
    val firstPlayed: Long = 0,
    /** Start of the current session (ms); 0 if unknown (Spigot). */
    val onlineSince: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("uuid", uuid).put("world", world)
        .put("x", x).put("y", y).put("z", z)
        .put("health", health).put("maxHealth", maxHealth).put("food", food).put("level", level)
        .put("ping", ping).put("gameMode", gameMode).put("op", op)
        .put("skin", skin.orEmpty()).put("playtime", playtime)
        .put("firstPlayed", firstPlayed).put("onlineSince", onlineSince)

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
                    playtime = p.optLong("playtime", -1),
                    firstPlayed = p.optLong("firstPlayed", 0),
                    onlineSince = p.optLong("onlineSince", 0),
                )
            }
        }
    }
}

/** [formatted] keeps Minecraft colour codes (§a…) when the line has colours. */
data class ConsoleLine(val id: Long, val time: Long, val level: String, val message: String, val formatted: String? = null) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("time", time).put("level", level).put("message", message).put("formatted", formatted.orEmpty())

    companion object {
        fun fromJson(o: JSONObject) = ConsoleLine(
            o.optLong("id"), o.optLong("time"), o.optString("level"), o.optString("message"),
            o.optString("formatted").takeIf { it.isNotEmpty() },
        )
    }
}

/** A console command known to the server, for suggestions. */
data class CommandInfo(
    val name: String,
    val description: String?,
    val usage: String?,
    val aliases: List<String>,
    /** Plugin that brings the command, or Minecraft / Bukkit / Paper / Spigot / Server (plugin 1.0+). */
    val plugin: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("description", description.orEmpty()).put("usage", usage.orEmpty())
        .put("aliases", JSONArray(aliases)).put("plugin", plugin.orEmpty())

    companion object {
        fun fromJson(c: JSONObject): CommandInfo {
            val aliases = c.optJSONArray("aliases")
            return CommandInfo(
                name = c.getString("name"),
                description = c.optString("description").takeIf { it.isNotBlank() },
                usage = c.optString("usage").takeIf { it.isNotBlank() },
                aliases = if (aliases == null) emptyList() else List(aliases.length()) { aliases.getString(it) },
                plugin = c.optString("plugin").takeIf { it.isNotBlank() },
            )
        }
    }
}

data class ChatEntry(val id: Long, val time: Long, val type: Type, val player: String, val message: String) {
    enum class Type { CHAT, JOIN, QUIT, BROADCAST }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("time", time).put("type", type.name.lowercase()).put("player", player).put("message", message)

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
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("role", role.name.lowercase())
        .put("commandsEnabled", commandsEnabled).put("capturesCommandOutput", capturesCommandOutput)

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
    /** Its JAR relative to the server folder (plugin 1.0 with file access), for removing it. */
    val file: String? = null,
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
                    p.optString("file").takeIf { f -> f.isNotBlank() },
                )
            }
        }
    }
}

/** True while the day/night cycle runs (the game rule is on; newer versions call it advance_time). */
val WorldDetails.daylightCycle: Boolean
    get() = gameRules.firstOrNull { it.name == "doDaylightCycle" || it.name == "advance_time" }?.value != "false"

/** World time [elapsedMs] after it was fetched: 20 ticks per second while the cycle runs. */
fun WorldDetails.timeAfter(elapsedMs: Long): Long = if (daylightCycle) time + elapsedMs.coerceAtLeast(0) / 50 else time

/** A file or folder in the server folder; [path] is relative to it with "/" separators. */
data class FileEntry(val path: String, val name: String, val dir: Boolean, val size: Long, val modified: Long) {
    companion object {
        fun fromJson(o: JSONObject, parent: String? = null): FileEntry {
            val name = o.optString("name")
            val path = parent?.let { if (it.isEmpty()) name else "$it/$name" } ?: o.optString("path")
            return FileEntry(path, name, o.optBoolean("dir"), o.optLong("size"), o.optLong("modified"))
        }
    }
}

/** Contents of one folder (folders first). */
data class FileList(val path: String, val entries: List<FileEntry>) {
    companion object {
        fun fromJson(o: JSONObject): FileList {
            val path = o.optString("path")
            val arr = o.optJSONArray("entries") ?: JSONArray()
            return FileList(path, List(arr.length()) { FileEntry.fromJson(arr.getJSONObject(it), path) })
        }
    }
}

/** A text file opened for editing. */
data class TextFile(val path: String, val content: String, val modified: Long) {
    companion object {
        fun fromJson(o: JSONObject) = TextFile(o.optString("path"), o.optString("content"), o.optLong("modified"))
    }
}

/** Parent of a server-relative path ("" for the server folder). */
fun parentPath(path: String): String = path.substringBeforeLast('/', "")

/** "1.4 MB" etc. */
fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
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

/** One stack in a player's inventory ([id] like "minecraft:diamond_sword"). */
data class InventoryItem(
    val slot: Int,
    val id: String,
    val amount: Int,
    /** Custom name given in an anvil, if any. */
    val name: String? = null,
    /** "sharpness 3", … */
    val enchants: List<String> = emptyList(),
    val durability: Int? = null,
    val maxDurability: Int? = null,
) {
    /** "Diamond Sword" from "minecraft:diamond_sword". */
    val displayName: String get() = name ?: prettyId(id)

    companion object {
        fun prettyId(id: String) = id.substringAfter(':').split('_')
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

        fun listFromJson(arr: JSONArray?): List<InventoryItem> {
            if (arr == null) return emptyList()
            return List(arr.length()) {
                val o = arr.getJSONObject(it)
                val enchants = o.optJSONArray("enchants")
                InventoryItem(
                    slot = o.optInt("slot"),
                    id = o.optString("id"),
                    amount = o.optInt("amount", 1),
                    name = o.optString("name").takeIf { n -> n.isNotBlank() },
                    enchants = if (enchants == null) emptyList() else List(enchants.length()) { i -> enchants.getString(i) },
                    durability = if (o.has("durability")) o.optInt("durability") else null,
                    maxDurability = if (o.has("maxDurability")) o.optInt("maxDurability") else null,
                )
            }
        }
    }
}

/** Read-only view of a player's inventory (GET /players/{name}/inventory). */
data class PlayerInventory(
    /** Slots 0–8 hotbar, 9–35 main inventory. */
    val items: List<InventoryItem>,
    /** Slots 0 boots, 1 leggings, 2 chestplate, 3 helmet. */
    val armor: List<InventoryItem>,
    val offhand: InventoryItem?,
    val enderChest: List<InventoryItem>,
    val selectedSlot: Int,
) {
    companion object {
        fun fromJson(o: JSONObject) = PlayerInventory(
            items = InventoryItem.listFromJson(o.optJSONArray("items")),
            armor = InventoryItem.listFromJson(o.optJSONArray("armor")),
            offhand = InventoryItem.listFromJson(o.optJSONArray("offhand")).firstOrNull(),
            enderChest = InventoryItem.listFromJson(o.optJSONArray("enderChest")),
            selectedSlot = o.optInt("selectedSlot", -1),
        )
    }
}
