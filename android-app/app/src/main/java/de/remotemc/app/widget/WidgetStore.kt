package de.remotemc.app.widget

import android.content.Context
import androidx.core.content.edit
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerStatus
import org.json.JSONArray
import org.json.JSONObject

/** Last known state of one server, as shown on the widget and the tile. */
data class WidgetServer(
    val id: String,
    val name: String,
    val online: Boolean,
    val players: Int = 0,
    val maxPlayers: Int = 0,
    val tps: Double? = null,
) {
    companion object {
        fun of(profile: ServerProfile, status: ServerStatus?) = WidgetServer(
            profile.id, profile.name, status != null,
            status?.onlinePlayers ?: 0, status?.maxPlayers ?: 0, status?.tps?.firstOrNull(),
        )
    }
}

data class WidgetData(val servers: List<WidgetServer>, val updatedAt: Long) {
    val onlineCount get() = servers.count { it.online }

    fun toJson(): String = JSONObject()
        .put("updatedAt", updatedAt)
        .put(
            "servers",
            JSONArray().apply {
                servers.forEach {
                    put(
                        JSONObject().put("id", it.id).put("name", it.name).put("online", it.online)
                            .put("players", it.players).put("max", it.maxPlayers).put("tps", it.tps ?: -1.0),
                    )
                }
            },
        ).toString()

    companion object {
        val EMPTY = WidgetData(emptyList(), 0)

        fun fromJson(json: String): WidgetData {
            val o = JSONObject(json)
            val arr = o.optJSONArray("servers") ?: JSONArray()
            return WidgetData(
                List(arr.length()) {
                    val s = arr.getJSONObject(it)
                    WidgetServer(
                        s.getString("id"), s.getString("name"), s.getBoolean("online"),
                        s.optInt("players"), s.optInt("max"), s.optDouble("tps", -1.0).takeIf { t -> t >= 0 },
                    )
                },
                o.optLong("updatedAt"),
            )
        }
    }
}

/** Small cache in SharedPreferences, written by [StatusRefresher] and read by the widget and the tile. */
object WidgetStore {
    private const val PREFS = "widget"
    private const val KEY = "data"

    fun load(context: Context): WidgetData =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { WidgetData.fromJson(it) }.getOrNull() } ?: WidgetData.EMPTY

    fun save(context: Context, data: WidgetData) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, data.toJson()) }
}
