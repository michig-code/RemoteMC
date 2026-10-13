package de.remotemc.app.net

import de.remotemc.app.data.BanInfo
import de.remotemc.app.data.ChatEntry
import de.remotemc.app.data.CommandInfo
import de.remotemc.app.data.CommandResult
import de.remotemc.app.data.HistoryRange
import de.remotemc.app.data.PerformanceHistory
import de.remotemc.app.data.ConsoleLine
import de.remotemc.app.data.FileEntry
import de.remotemc.app.data.FileList
import de.remotemc.app.data.PairResult
import de.remotemc.app.data.PlayerInfo
import de.remotemc.app.data.PluginInfo
import de.remotemc.app.data.ScheduleInfo
import de.remotemc.app.data.ScheduleList
import de.remotemc.app.data.Role
import de.remotemc.app.data.ServerProfile
import de.remotemc.app.data.ServerStatus
import de.remotemc.app.data.Session
import de.remotemc.app.data.TextFile
import de.remotemc.app.data.Whitelist
import de.remotemc.app.data.WorldDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.source
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

data class PingInfo(val version: String, val apiVersion: Int, val platform: String)

/** Probe result before pairing: what the server is and which certificate it presents. */
data class Probe(val ping: PingInfo, val fingerprint: String?)

/** REST client for one server, with certificate pinning. */
class ApiClient(private val profile: ServerProfile, base: OkHttpClient = Http.base) {

    private val client: OkHttpClient = Http.forProfile(base, profile.tls, profile.fingerprint)

    suspend fun ping(): PingInfo = ping(client, profile.baseUrl)
    suspend fun me(): Session = Session.fromJson(call("GET", "/api/v1/me"))
    suspend fun status(): ServerStatus = ServerStatus.fromJson(call("GET", "/api/v1/status"))
    /** Performance history (plugin 1.0+ with history enabled; otherwise an [ApiError.Http] 404). */
    suspend fun history(range: HistoryRange): PerformanceHistory =
        PerformanceHistory.fromJson(call("GET", "/api/v1/history?range=${range.param}"))
    suspend fun whitelist(): Whitelist = Whitelist.fromJson(call("GET", "/api/v1/whitelist"))
    suspend fun setWhitelist(enabled: Boolean): Whitelist =
        Whitelist.fromJson(call("POST", "/api/v1/whitelist", JSONObject().put("enabled", enabled)))
    suspend fun whitelistAdd(name: String): Whitelist =
        Whitelist.fromJson(call("POST", "/api/v1/whitelist/add", JSONObject().put("name", name)))
    suspend fun whitelistRemove(name: String): Whitelist =
        Whitelist.fromJson(call("POST", "/api/v1/whitelist/remove", JSONObject().put("name", name)))
    suspend fun bans(): List<BanInfo> = BanInfo.listFromJson(call("GET", "/api/v1/bans"))
    suspend fun pardon(name: String): List<BanInfo> =
        BanInfo.listFromJson(call("POST", "/api/v1/bans/${java.net.URLEncoder.encode(name, "UTF-8")}/pardon"))
    suspend fun world(name: String): WorldDetails = WorldDetails.fromJson(call("GET", "/api/v1/worlds/${enc(name)}"))

    /** [action]: time, weather, difficulty or gamerule; returns the world afterwards. */
    suspend fun worldAction(name: String, action: String, body: JSONObject): WorldDetails =
        WorldDetails.fromJson(call("POST", "/api/v1/worlds/${enc(name)}/$action", body))

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    suspend fun plugins(): List<PluginInfo> = PluginInfo.listFromJson(call("GET", "/api/v1/plugins"))
    suspend fun schedules(): ScheduleList = ScheduleList.fromJson(call("GET", "/api/v1/schedules"))
    suspend fun saveSchedule(task: ScheduleInfo): ScheduleList = ScheduleList.fromJson(call("POST", "/api/v1/schedules", task.toJson()))
    /** [action]: run or delete. */
    suspend fun scheduleAction(id: String, action: String): ScheduleList =
        ScheduleList.fromJson(call("POST", "/api/v1/schedules/${enc(id)}/$action"))
    suspend fun files(path: String): FileList = FileList.fromJson(call("GET", "/api/v1/files?path=${enc(path)}"))
    suspend fun readFile(path: String): TextFile = TextFile.fromJson(call("GET", "/api/v1/files/content?path=${enc(path)}"))
    suspend fun writeFile(path: String, content: String): FileEntry =
        FileEntry.fromJson(call("POST", "/api/v1/files/content", JSONObject().put("path", path).put("content", content)))
    suspend fun deleteFile(path: String) {
        call("POST", "/api/v1/files/delete", JSONObject().put("path", path))
    }
    suspend fun createFolder(path: String): FileEntry = FileEntry.fromJson(call("POST", "/api/v1/files/mkdir", JSONObject().put("path", path)))

    /** Uploads [length] bytes from [open] to [path] (replacing a file there), e.g. a plugin JAR. */
    suspend fun uploadFile(path: String, length: Long, open: () -> java.io.InputStream): FileEntry {
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = length
            override fun writeTo(sink: okio.BufferedSink) {
                open().use { sink.writeAll(it.source()) }
            }
        }
        // Large files take longer than the usual timeouts allow.
        val slow = client.newBuilder().writeTimeout(5, TimeUnit.MINUTES).readTimeout(2, TimeUnit.MINUTES).build()
        return FileEntry.fromJson(execute(slow, profile.baseUrl, "POST", "/api/v1/files/upload?path=${enc(path)}", profile.token, null, body))
    }

    suspend fun players(): List<PlayerInfo> = PlayerInfo.listFromJson(call("GET", "/api/v1/players").optJSONArray("players"))

    suspend fun console(since: Long): List<ConsoleLine> {
        val arr = call("GET", "/api/v1/console?since=$since").getJSONArray("lines")
        return List(arr.length()) { ConsoleLine.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun chat(since: Long): List<ChatEntry> {
        val arr = call("GET", "/api/v1/chat?since=$since").getJSONArray("messages")
        return List(arr.length()) { ChatEntry.fromJson(arr.getJSONObject(it)) }
    }

    suspend fun command(command: String): CommandResult {
        val o = call("POST", "/api/v1/command", JSONObject().put("command", command))
        val out = o.optJSONArray("output")
        val formatted = o.optJSONArray("formatted")
        return CommandResult(
            success = o.optBoolean("success"),
            captured = o.optBoolean("captured", true),
            output = if (out == null) emptyList() else List(out.length()) { out.getString(it) },
            formatted = if (formatted == null) emptyList() else List(formatted.length()) { formatted.getString(it) },
        )
    }

    /** Commands the server knows (plugin 2.1+; empty list for older plugins). */
    suspend fun commands(): List<CommandInfo> = try {
        val arr = call("GET", "/api/v1/commands").getJSONArray("commands")
        List(arr.length()) {
            val c = arr.getJSONObject(it)
            val aliases = c.optJSONArray("aliases")
            CommandInfo(
                name = c.getString("name"),
                description = c.optString("description").takeIf { d -> d.isNotBlank() },
                usage = c.optString("usage").takeIf { u -> u.isNotBlank() },
                aliases = if (aliases == null) emptyList() else List(aliases.length()) { i -> aliases.getString(i) },
            )
        }
    } catch (e: ApiError.Http) {
        if (e.status == 404) emptyList() else throw e
    }

    /** Server-side tab completion for the last word of [buffer] (plugin 2.1+). */
    suspend fun complete(buffer: String): List<String> = try {
        val arr = call("GET", "/api/v1/complete?buffer=" + enc(buffer)).getJSONArray("suggestions")
        List(arr.length()) { arr.getString(it) }
    } catch (e: ApiError.Http) {
        if (e.status == 404) emptyList() else throw e
    }

    suspend fun broadcast(message: String) {
        call("POST", "/api/v1/chat", JSONObject().put("message", message))
    }

    suspend fun playerAction(player: String, action: String, body: JSONObject = JSONObject()) {
        call("POST", "/api/v1/players/${enc(player)}/$action", body)
    }

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject =
        execute(client, profile.baseUrl, method, path, profile.token, body)

    companion object {
        const val SUPPORTED_API = 1

        suspend fun ping(client: OkHttpClient, baseUrl: String): PingInfo {
            val o = try {
                execute(client, baseUrl, "GET", "/api/v1/ping", null, null)
            } catch (e: ApiError.Http) {
                if (e.status == 404) throw ApiError.Incompatible(pluginTooOld = true) else throw e
            }
            val info = PingInfo(o.optString("version"), o.optInt("apiVersion", 0), o.optString("platform"))
            if (info.apiVersion < SUPPORTED_API) throw ApiError.Incompatible(pluginTooOld = true)
            if (info.apiVersion > SUPPORTED_API) throw ApiError.Incompatible(pluginTooOld = false)
            return info
        }

        /**
         * Connects without trusting anything yet and reports the certificate fingerprint,
         * so the user can compare it with the console before pairing.
         */
        suspend fun probe(host: String, port: Int, tls: Boolean): Probe {
            val profile = ServerProfile(name = "", host = host, port = port, token = "", tls = tls)
            if (!tls) return Probe(ping(Http.base, profile.baseUrl), null)
            val capture = Tls.CapturingTrustManager()
            val client = Http.base.newBuilder()
                .sslSocketFactory(Tls.sslContext(capture).socketFactory, capture)
                .hostnameVerifier { _, _ -> true }
                .build()
            val ping = ping(client, profile.baseUrl)
            return Probe(ping, capture.captured?.let(Tls::fingerprint))
        }

        /** Exchanges a pairing code for this device's own token. */
        suspend fun pair(host: String, port: Int, tls: Boolean, fingerprint: String?, code: String, device: String): PairResult {
            val profile = ServerProfile(name = "", host = host, port = port, token = "", tls = tls, fingerprint = fingerprint)
            val client = Http.forProfile(Http.base, tls, fingerprint)
            ping(client, profile.baseUrl)
            val o = execute(client, profile.baseUrl, "POST", "/api/v1/pair", null,
                JSONObject().put("code", code).put("device", device))
            return PairResult(o.getString("token"), o.optString("name"), Role.parse(o.optString("role")))
        }

        internal suspend fun execute(
            client: OkHttpClient,
            baseUrl: String,
            method: String,
            path: String,
            token: String?,
            body: JSONObject?,
            raw: RequestBody? = null,
        ): JSONObject = withContext(Dispatchers.IO) {
            val url = (baseUrl + path).toHttpUrlOrNull() ?: throw ApiError.Unreachable("Invalid address")
            // POST needs a body even for actions without parameters (delete, run, unban).
            val payload = body ?: if (method == "POST" || method == "PUT" || method == "PATCH") JSONObject() else null
            // Reads we already have an answer for ask "changed?"; an unchanged list then costs an
            // empty 304 instead of the whole JSON. (gzip is added and unpacked by OkHttp itself.)
            val cacheKey = if (method == "GET") ResponseCache.key(url.toString(), token) else null
            val cached = cacheKey?.let(ResponseCache::get)
            val request = Request.Builder()
                .url(url)
                .method(method, raw ?: payload?.toString()?.toRequestBody(JSON))
                .header("Accept", "application/json")
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .apply { if (cached != null) header("If-None-Match", cached.etag) }
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 304 && cached != null) return@use JSONObject(cached.body)
                    val text = response.body?.string().orEmpty()
                    val json = try {
                        JSONObject(text)
                    } catch (e: JSONException) {
                        null
                    }
                    if (!response.isSuccessful) {
                        throw ApiError.Http(
                            response.code,
                            json?.optString("error")?.takeIf { it.isNotBlank() } ?: "http_${response.code}",
                            json?.optString("message").orEmpty(),
                        )
                    }
                    json ?: throw ApiError.InvalidResponse()
                    val etag = response.header("ETag")
                    if (cacheKey != null && etag != null) ResponseCache.put(cacheKey, etag, text)
                    json
                }
            } catch (e: ApiError) {
                throw e
            } catch (e: IOException) {
                throw mapException(e)
            }
        }

        fun mapException(e: Throwable): ApiError {
            var cause: Throwable? = e
            while (cause != null) {
                if (cause is CertificateChangedException) return ApiError.CertificateChanged(cause.actualFingerprint)
                cause = cause.cause
            }
            return when (e) {
                is ApiError -> e
                is SocketTimeoutException, is InterruptedIOException -> ApiError.Timeout()
                is UnknownHostException -> ApiError.Unreachable("Unknown host")
                is ConnectException, is NoRouteToHostException -> ApiError.Unreachable(e.message ?: "")
                is SSLHandshakeException, is SSLException -> ApiError.Tls(e.message ?: "")
                else -> ApiError.Unreachable(e.message ?: e.javaClass.simpleName)
            }
        }

        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

object Http {
    val base: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // The plugin's server drops connections that are idle for about 30 s. Over mobile data its
            // close can arrive just after the app sent the next request on that connection, which then
            // fails ("unreachable"); so idle connections are let go earlier, before the server does it.
            .connectionPool(ConnectionPool(5, 20, TimeUnit.SECONDS))
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * For the status checks on the server list: gives up quickly, so an offline server shows as
     * offline after about 2 s instead of waiting for the full timeouts (per IP address of the host).
     * A false "offline" on a slow network fixes itself with the next check a few seconds later.
     */
    val quick: OkHttpClient by lazy {
        base.newBuilder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    private val pinned = ConcurrentHashMap<Pair<OkHttpClient, String?>, OkHttpClient>()

    /**
     * Client for one certificate. Reused, so repeated requests (e.g. the status on the server list)
     * keep their connection open instead of doing a new TCP and TLS handshake each time.
     */
    fun forProfile(base: OkHttpClient, tls: Boolean, fingerprint: String?): OkHttpClient {
        if (!tls) return base
        return pinned.getOrPut(base to fingerprint) {
            val tm = Tls.PinningTrustManager(fingerprint)
            base.newBuilder()
                .sslSocketFactory(Tls.sslContext(tm).socketFactory, tm)
                .hostnameVerifier(Tls.hostnameVerifier(fingerprint != null))
                .build()
        }
    }
}
