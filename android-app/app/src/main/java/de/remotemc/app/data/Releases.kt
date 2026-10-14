package de.remotemc.app.data

import org.json.JSONObject

/** A newer RemoteMC release on GitHub with the file for this app (APK or Windows installer). */
data class Release(
    val version: String,
    val notes: String,
    /** Release page (fallback when the file cannot be installed directly). */
    val page: String,
    /** Download of the file for this app, if the release has one. */
    val fileUrl: String?,
    val fileSize: Long,
    /** SHA-256 GitHub reports for the file (hex), checked after downloading. */
    val sha256: String?,
)

/** GitHub Releases of RemoteMC: which one is newer and where its files may come from. Shared by phone and PC. */
object Releases {
    const val LATEST_API = "https://api.github.com/repos/michig-code/RemoteMC/releases/latest"
    const val LATEST_PAGE = "https://github.com/michig-code/RemoteMC/releases/latest"
    private val TRUSTED_HOSTS = setOf("github.com", "api.github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

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

    /** Only downloads from GitHub over HTTPS. */
    fun isTrusted(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.scheme == "https" && uri.host in TRUSTED_HOSTS
    }.getOrDefault(false)

    /**
     * Reads GitHub's "latest release" answer; null when it is not newer than [current].
     * [isFile] picks this app's file among the release assets by name.
     */
    fun parse(json: String, current: String, isFile: (String) -> Boolean): Release? {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val version = o.optString("tag_name").removePrefix("v")
        if (version.isBlank() || !isNewer(version, current)) return null
        val assets = o.optJSONArray("assets")
        var file: JSONObject? = null
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (isFile(a.optString("name"))) file = a
            }
        }
        return Release(
            version = version,
            notes = o.optString("body").trim().take(4000),
            page = o.optString("html_url").takeIf { isTrusted(it) } ?: LATEST_PAGE,
            fileUrl = file?.optString("browser_download_url")?.takeIf { isTrusted(it) },
            fileSize = file?.optLong("size") ?: 0,
            sha256 = file?.optString("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
        )
    }

    /** The phone app's APK of a release (not the Google Play bundle). */
    fun isPhoneApk(name: String) = name.endsWith(".apk", ignoreCase = true) && !name.contains("Play", ignoreCase = true)

    fun isWindowsInstaller(name: String) = name.endsWith(".msi", ignoreCase = true)
}
