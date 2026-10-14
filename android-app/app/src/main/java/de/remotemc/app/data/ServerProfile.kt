package de.remotemc.app.data

import org.json.JSONObject
import java.util.UUID

/** What the paired device may do on a server (mirrors the plugin's roles). */
enum class Role {
    VIEWER, MODERATOR, ADMIN;

    fun atLeast(other: Role) = ordinal >= other.ordinal

    companion object {
        fun parse(value: String?) = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: VIEWER
    }
}

data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 8765,
    val token: String,
    /** HTTPS (built-in certificate or a reverse proxy). */
    val tls: Boolean = true,
    /** Pinned SHA-256 fingerprint (base64url) of the plugin's certificate; null = normal CA validation. */
    val fingerprint: String? = null,
    val role: Role = Role.ADMIN,
    val deviceName: String = "",
    /** Pinned servers are listed first. */
    val pinned: Boolean = false,
    /** Folder in the server list; blank = no folder. */
    val folder: String = "",
    /** Its folder is pinned (listed on top); the same for every server of the folder. */
    val folderPinned: Boolean = false,
    /** Background notifications for this server (when they are on in the settings). */
    val notify: Boolean = true,
    /** Size of its card in the server list, in grid cells; 0 = the default size (see [CardGrid]). */
    val cardW: Int = 0,
    val cardH: Int = 0,
) {
    val baseUrl: String
        get() {
            val h = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
            return "${if (tls) "https" else "http"}://$h:$port"
        }

    val address: String get() = "$host:$port"

    /** Search by name, address or folder, ignoring case. */
    fun matches(query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || listOf(name, address, folder).any { it.contains(q, ignoreCase = true) }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("host", host)
        .put("port", port)
        .put("token", token)
        .put("tls", tls)
        .put("fingerprint", fingerprint)
        .put("role", role.name)
        .put("deviceName", deviceName)
        .put("pinned", pinned)
        .put("folder", folder)
        .put("folderPinned", folderPinned)
        .put("notify", notify)
        .put("cardW", cardW)
        .put("cardH", cardH)

    companion object {
        fun fromJson(o: JSONObject) = ServerProfile(
            id = o.getString("id"),
            name = o.getString("name"),
            host = o.getString("host"),
            port = o.optInt("port", 8765),
            token = o.getString("token"),
            // Profiles from version 1 used "https" and had no fingerprint.
            tls = if (o.has("tls")) o.getBoolean("tls") else o.optBoolean("https", false),
            fingerprint = o.optString("fingerprint").takeIf { it.isNotBlank() && it != "null" },
            role = Role.parse(o.optString("role", "ADMIN")),
            deviceName = o.optString("deviceName"),
            pinned = o.optBoolean("pinned", false),
            folder = o.optString("folder"),
            folderPinned = o.optBoolean("folderPinned", false),
            notify = o.optBoolean("notify", true),
            cardW = o.optInt("cardW", 0),
            cardH = o.optInt("cardH", 0),
        )
    }
}
