package de.mcremote.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 8765,
    val token: String,
    val https: Boolean = false,
) {
    val baseUrl: String
        get() {
            val h = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
            return "${if (https) "https" else "http"}://$h:$port"
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("host", host)
        .put("port", port)
        .put("token", token)
        .put("https", https)

    companion object {
        fun fromJson(o: JSONObject) = ServerProfile(
            id = o.getString("id"),
            name = o.getString("name"),
            host = o.getString("host"),
            port = o.optInt("port", 8765),
            token = o.getString("token"),
            https = o.optBoolean("https", false),
        )

        /**
         * Parses the link printed by `/mcremote link`, e.g.
         * `mcremote://play.example.de:8765?token=abc` (append `&https=1` for TLS).
         */
        fun fromLink(link: String): ServerProfile? {
            val uri = runCatching { Uri.parse(link.trim()) }.getOrNull() ?: return null
            if (uri.scheme != "mcremote") return null
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
            val token = uri.getQueryParameter("token")?.takeIf { it.isNotBlank() } ?: return null
            return ServerProfile(
                name = uri.getQueryParameter("name") ?: host,
                host = host,
                port = if (uri.port > 0) uri.port else 8765,
                token = token,
                https = uri.getQueryParameter("https") == "1",
            )
        }
    }
}

/** Saved servers, kept in private SharedPreferences. */
class ServerStore(context: Context) {
    private val prefs = context.getSharedPreferences("servers", Context.MODE_PRIVATE)

    fun load(): List<ServerProfile> {
        val raw = prefs.getString("list", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { ServerProfile.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(list: List<ServerProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("list", arr.toString()).apply()
    }
}
