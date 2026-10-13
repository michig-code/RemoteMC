package de.remotemc.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.remotemc.app.net.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** A newer RemoteMC release on GitHub. */
data class UpdateInfo(
    val version: String,
    val notes: String,
    /** Release page, for the portable version or when installing fails. */
    val page: String,
    /** Windows installer of the release, if there is one. */
    val msiUrl: String?,
    val msiSize: Long,
    /** SHA-256 GitHub reports for the installer (hex), checked after downloading. */
    val sha256: String?,
)

/**
 * Looks for new versions on GitHub Releases and installs them: downloads the MSI, checks its
 * checksum, starts the Windows installer and restarts RemoteMC afterwards.
 */
object Updater {
    const val RELEASES_API = "https://api.github.com/repos/michig-code/RemoteMC/releases/latest"
    private val TRUSTED_HOSTS = setOf("github.com", "api.github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    /** True when RemoteMC was installed with the MSI/EXE (it can replace itself then). */
    val canInstall: Boolean get() = AppDirs.isWindows && System.getProperty("jpackage.app-path") != null

    /** "1.2.0" > "1.1.9"; a version with a suffix ("1.2.0-beta") counts as older than without. */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String): Pair<List<Int>, Boolean> {
            val clean = v.trim().removePrefix("v").removePrefix("V")
            val main = clean.substringBefore('-').substringBefore('+')
            return main.split('.').map { it.toIntOrNull() ?: 0 } to clean.contains('-')
        }
        val (a, aPre) = parts(latest)
        val (b, bPre) = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return bPre && !aPre
    }

    /** Reads GitHub's "latest release" answer; null when it is not newer than [current]. */
    fun parseRelease(json: String, current: String): UpdateInfo? {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val version = o.optString("tag_name").removePrefix("v")
        if (version.isBlank() || !isNewer(version, current)) return null
        val assets = o.optJSONArray("assets")
        var msi: JSONObject? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".msi", ignoreCase = true)) msi = a
            }
        }
        val url = msi?.optString("browser_download_url")?.takeIf { isTrusted(it) }
        return UpdateInfo(
            version = version,
            notes = o.optString("body").trim().take(4000),
            page = o.optString("html_url").takeIf { isTrusted(it) } ?: "https://github.com/michig-code/RemoteMC/releases/latest",
            msiUrl = url,
            msiSize = msi?.optLong("size") ?: 0,
            sha256 = msi?.optString("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
        )
    }

    /** Only downloads from GitHub over HTTPS. */
    fun isTrusted(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme == "https" && uri.host in TRUSTED_HOSTS
    }.getOrDefault(false)

    suspend fun check(current: String = AppVersion, client: OkHttpClient = Http.base, api: String = RELEASES_API): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(api)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "RemoteMC-Desktop/$current")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext null // no release yet
                if (!response.isSuccessful) throw IOException("GitHub answered ${response.code}")
                parseRelease(response.body?.string().orEmpty(), current)
            }
        }

    /** Downloads the installer into the temp folder and checks size and checksum. */
    suspend fun download(info: UpdateInfo, client: OkHttpClient = Http.base, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val url = info.msiUrl ?: throw IOException("This release has no Windows installer")
            if (!isTrusted(url)) throw IOException("Untrusted download address")
            val target = File(System.getProperty("java.io.tmpdir"), "RemoteMC-Windows-${info.version.filter { it.isLetterOrDigit() || it == '.' }}.msi")
            val digest = MessageDigest.getInstance("SHA-256")
            val slow = client.newBuilder().readTimeout(2, java.util.concurrent.TimeUnit.MINUTES).build()
            slow.newCall(Request.Builder().url(url).header("User-Agent", "RemoteMC-Desktop").build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed (${response.code})")
                // GitHub redirects to its file servers; the final address must still be GitHub's.
                if (!isTrusted(response.request.url.toString())) throw IOException("Untrusted download address")
                val body = response.body ?: throw IOException("Empty download")
                val total = body.contentLength().takeIf { it > 0 } ?: info.msiSize
                var done = 0L
                body.byteStream().use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (info.msiSize > 0 && target.length() != info.msiSize) {
                target.delete()
                throw IOException("The download is incomplete")
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            if (info.sha256 != null && hash != info.sha256) {
                target.delete()
                throw IOException("The download does not match the checksum")
            }
            target
        }

    /**
     * Starts the installer (without questions, with a progress window) and RemoteMC again once it
     * has finished; the caller then quits the app so its files can be replaced.
     */
    fun install(msi: File) {
        val app = System.getProperty("jpackage.app-path") ?: throw IOException("Not installed with the installer")
        val script = File(System.getProperty("java.io.tmpdir"), "remotemc-update.cmd")
        script.writeText(
            "@echo off\r\n" +
                "timeout /t 2 /nobreak >nul\r\n" +
                "msiexec /i \"${msi.absolutePath}\" /passive /norestart\r\n" +
                "start \"\" \"$app\"\r\n" +
                "del \"%~f0\"\r\n",
        )
        ProcessBuilder("cmd", "/c", "start", "\"\"", "/min", script.absolutePath).start()
    }
}

/** What the app shows about updates; checks at start and every 6 hours while turned on. */
class UpdateState(private val scope: CoroutineScope, private val enabled: () -> Boolean) {
    var available by mutableStateOf<UpdateInfo?>(null)
        private set
    /** 0..1 while downloading, null otherwise. */
    var progress by mutableStateOf<Float?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var checking by mutableStateOf(false)
        private set
    /** Result of the last manual check, for "RemoteMC is up to date". */
    var upToDate by mutableStateOf(false)
        private set

    fun start() = scope.launch {
        delay(5_000) // not while the window is still opening
        while (isActive) {
            if (enabled()) checkNow()
            delay(6 * 60 * 60 * 1000L)
        }
    }

    fun checkNow() {
        if (checking) return
        scope.launch {
            checking = true
            try {
                available = Updater.check()
                upToDate = available == null
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                checking = false
            }
        }
    }

    /** Downloads and installs; [quit] closes RemoteMC so the installer can replace it. */
    fun install(quit: () -> Unit) {
        val info = available ?: return
        if (progress != null) return
        scope.launch {
            progress = 0f
            error = null
            try {
                val file = Updater.download(info) { p -> progress = p }
                Updater.install(file)
                quit()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                progress = null
            }
        }
    }
}
