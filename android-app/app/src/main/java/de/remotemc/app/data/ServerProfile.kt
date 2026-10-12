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
    /** Background notifications for this server (when they are on in the settings). */
    val notify: Boolean = true,
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
        .put("notify", notify)

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
            notify = o.optBoolean("notify", true),
        )
    }
}
