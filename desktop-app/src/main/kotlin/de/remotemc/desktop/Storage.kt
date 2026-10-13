package de.remotemc.desktop

import com.sun.jna.platform.win32.Crypt32Util
import de.remotemc.app.data.AlertSettings
import de.remotemc.app.data.ServerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

/** Where the app keeps its files: %APPDATA%\RemoteMC on Windows, ~/.config/remotemc elsewhere. */
object AppDirs {
    val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    val dir: File by lazy {
        val base = if (isWindows) System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"))
        else File(System.getProperty("user.home"), ".config")
        File(base, if (isWindows) "RemoteMC" else "remotemc").apply { mkdirs() }
    }
}

/**
 * Protects the stored access tokens: Windows DPAPI ties them to the user account (like the
 * Android Keystore on the phone). Elsewhere the file is only readable by the user.
 */
object Protect {
    fun seal(data: ByteArray): ByteArray = if (AppDirs.isWindows) Crypt32Util.cryptProtectData(data) else data
    fun open(data: ByteArray): ByteArray = if (AppDirs.isWindows) Crypt32Util.cryptUnprotectData(data) else data
}

private fun writePrivate(file: File, bytes: ByteArray) {
    val tmp = File(file.path + ".tmp")
    tmp.writeBytes(bytes)
    if (!AppDirs.isWindows) runCatching { Files.setPosixFilePermissions(tmp.toPath(), PosixFilePermissions.fromString("rw-------")) }
    Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
}

/** Saved servers (with their tokens), encrypted as a whole. */
class ServerStore(private val file: File = File(AppDirs.dir, "servers.dat")) {
    private val _servers = MutableStateFlow(load())
    val servers: StateFlow<List<ServerProfile>> = _servers.asStateFlow()

    fun get(id: String) = _servers.value.firstOrNull { it.id == id }

    fun upsert(profile: ServerProfile) {
        val list = _servers.value.toMutableList()
        val i = list.indexOfFirst { it.id == profile.id }
        if (i >= 0) list[i] = profile else list += profile
        save(list)
    }

    fun delete(id: String) = save(_servers.value.filterNot { it.id == id })

    fun setOrder(list: List<ServerProfile>) {
        if (list.map { it.id }.sorted() == _servers.value.map { it.id }.sorted()) save(list)
    }

    private fun load(): List<ServerProfile> = runCatching {
        if (!file.isFile) return emptyList()
        val arr = JSONArray(String(Protect.open(file.readBytes()), Charsets.UTF_8))
        List(arr.length()) { ServerProfile.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun save(list: List<ServerProfile>) {
        _servers.value = list
        val arr = JSONArray().apply { list.forEach { put(it.toJson()) } }
        writePrivate(file, Protect.seal(arr.toString().toByteArray(Charsets.UTF_8)))
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class DesktopSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Hue of the colour theme; null = classic green. */
    val accentHue: Float? = null,
    val deviceName: String = defaultDeviceName(),
    /** Windows notifications; while on, closing the window keeps RemoteMC in the tray. */
    val alerts: AlertSettings = AlertSettings(),
    /** Transitions, moving numbers and the like; off = everything changes instantly. */
    val animations: Boolean = true,
)

fun defaultDeviceName(): String =
    (System.getenv("COMPUTERNAME") ?: runCatching { InetAddress.getLocalHost().hostName }.getOrNull() ?: "PC")
        .lowercase().replaceFirstChar { it.uppercase() }.take(40)

class SettingsStore(private val file: File = File(AppDirs.dir, "settings.json")) {
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<DesktopSettings> = _settings.asStateFlow()

    fun update(change: (DesktopSettings) -> DesktopSettings) {
        val next = change(_settings.value)
        _settings.value = next
        val a = next.alerts
        val alerts = JSONObject().put("enabled", a.enabled).put("offline", a.offline).put("lowTps", a.lowTps)
            .put("tpsThreshold", a.tpsThreshold).put("joins", a.joins).put("players", a.players)
            .put("mentions", a.mentions).put("words", a.words)
        val o = JSONObject().put("theme", next.theme.name).put("accentHue", next.accentHue ?: -1.0).put("deviceName", next.deviceName)
            .put("alerts", alerts).put("animations", next.animations)
        writePrivate(file, o.toString(2).toByteArray(Charsets.UTF_8))
    }

    private fun load(): DesktopSettings = runCatching {
        val o = JSONObject(file.readText())
        DesktopSettings(
            theme = runCatching { ThemeMode.valueOf(o.getString("theme")) }.getOrDefault(ThemeMode.SYSTEM),
            accentHue = o.optDouble("accentHue", -1.0).takeIf { it >= 0 }?.toFloat(),
            deviceName = o.optString("deviceName").ifBlank { defaultDeviceName() },
            animations = o.optBoolean("animations", true),
            alerts = o.optJSONObject("alerts")?.let { a ->
                AlertSettings(
                    enabled = a.optBoolean("enabled", false),
                    offline = a.optBoolean("offline", true),
                    lowTps = a.optBoolean("lowTps", true),
                    tpsThreshold = a.optInt("tpsThreshold", 15).coerceIn(5, 19),
                    joins = a.optBoolean("joins", false),
                    players = a.optString("players"),
                    mentions = a.optBoolean("mentions", true),
                    words = a.optString("words"),
                )
            } ?: AlertSettings(),
        )
    }.getOrDefault(DesktopSettings())
}

/** Recently used console commands per server (newest first). */
class CommandHistory(private val file: File = File(AppDirs.dir, "history.json")) {
    private val data: JSONObject = runCatching { JSONObject(file.readText()) }.getOrDefault(JSONObject())

    fun load(serverId: String): List<String> {
        val arr = data.optJSONArray(serverId) ?: return emptyList()
        return List(arr.length()) { arr.getString(it) }
    }

    fun add(serverId: String, command: String): List<String> {
        val list = (listOf(command) + load(serverId).filter { it != command }).take(12)
        data.put(serverId, JSONArray(list))
        writePrivate(file, data.toString().toByteArray(Charsets.UTF_8))
        return list
    }
}
