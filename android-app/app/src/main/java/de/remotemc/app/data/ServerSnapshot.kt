package de.remotemc.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * What a server screen showed last: opening the server again shows this right away while the
 * connection is still being made, instead of an empty screen with a spinner.
 */
data class ServerSnapshot(
    /** When it was saved (ms). */
    val savedAt: Long,
    val session: Session?,
    val status: ServerStatus?,
    val players: List<PlayerInfo>,
    val console: List<ConsoleLine>,
    val chat: List<ChatEntry>,
    val commands: List<CommandInfo>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("version", VERSION)
        .put("savedAt", savedAt)
        .apply { session?.let { put("session", it.toJson()) } }
        .apply { status?.let { put("status", it.toJson()) } }
        .put("players", JSONArray(players.map { it.toJson() }))
        .put("console", JSONArray(console.takeLast(MAX_CONSOLE).map { it.toJson() }))
        .put("chat", JSONArray(chat.takeLast(MAX_CHAT).map { it.toJson() }))
        .put("commands", JSONArray(commands.map { it.toJson() }))

    companion object {
        const val VERSION = 1
        const val MAX_CONSOLE = 300
        const val MAX_CHAT = 100

        /** Null for anything unreadable (other version, damaged file). */
        fun fromJson(o: JSONObject): ServerSnapshot? = runCatching {
            if (o.optInt("version") != VERSION) return null
            fun <T> list(key: String, read: (JSONObject) -> T): List<T> {
                val arr = o.optJSONArray(key) ?: return emptyList()
                return List(arr.length()) { read(arr.getJSONObject(it)) }
            }
            ServerSnapshot(
                savedAt = o.getLong("savedAt"),
                session = o.optJSONObject("session")?.let(Session::fromJson),
                status = o.optJSONObject("status")?.let(ServerStatus::fromJson),
                players = PlayerInfo.listFromJson(o.optJSONArray("players")),
                console = list("console", ConsoleLine::fromJson),
                chat = list("chat", ChatEntry::fromJson),
                commands = list("commands", CommandInfo::fromJson),
            )
        }.getOrNull()
    }
}

/**
 * One encrypted file per server in the app's private folder (console and chat can hold private
 * things, so they are encrypted like the tokens).
 */
class SnapshotStore(private val dir: File, private val box: SecureBox) {

    private fun file(serverId: String) = File(dir, serverId.filter { it.isLetterOrDigit() || it == '-' } + ".snap")

    fun load(serverId: String): ServerSnapshot? = runCatching {
        val f = file(serverId)
        if (!f.isFile) return null
        ServerSnapshot.fromJson(JSONObject(box.decrypt(f.readText())))
    }.getOrNull()

    fun save(serverId: String, snapshot: ServerSnapshot) {
        runCatching {
            dir.mkdirs()
            val target = file(serverId)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(box.encrypt(snapshot.toJson().toString()))
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    fun delete(serverId: String) {
        file(serverId).delete()
    }
}
