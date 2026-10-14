package de.remotemc.app.update

import de.remotemc.app.data.Release
import de.remotemc.app.data.Releases
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class AppUpdaterTest {
    private val client = OkHttpClient()

    private fun release(version: String, vararg assets: Pair<String, String>, prerelease: Boolean = false) = """
        {"tag_name":"v$version","prerelease":$prerelease,"html_url":"https://github.com/michig-code/RemoteMC/releases/tag/v$version","body":"Notes",
         "assets":[${assets.joinToString(",") { (name, url) -> """{"name":"$name","browser_download_url":"$url","size":4,"digest":"sha256:abc"}""" }}]}
    """.trimIndent()

    @Test
    fun picksThePhoneApkOfANewerRelease() {
        val json = release(
            "1.1.0",
            "RemoteMC-App-1.1.0.apk" to "https://github.com/michig-code/RemoteMC/releases/download/v1.1.0/RemoteMC-App-1.1.0.apk",
            "RemoteMC-App-Play-1.1.0.aab" to "https://github.com/x.aab",
            "RemoteMC-1.1.0.msi" to "https://github.com/x.msi",
        )
        val r = Releases.parse(json, "1.0.0", Releases::isPhoneApk)!!
        assertEquals("1.1.0", r.version)
        assertTrue(r.fileUrl!!.endsWith(".apk"))
        assertEquals("abc", r.sha256)
        assertNull(Releases.parse(json, "1.1.0", Releases::isPhoneApk))
        assertNull(Releases.parse(release("1.2.0", prerelease = true), "1.0.0", Releases::isPhoneApk))
        // A file from somewhere else than GitHub is never used.
        assertNull(Releases.parse(release("1.1.0", "RemoteMC.apk" to "https://evil.example/RemoteMC.apk"), "1.0.0", Releases::isPhoneApk)!!.fileUrl)
    }

    @Test
    fun checksGitHub() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release("2.0.0", "RemoteMC-App-2.0.0.apk" to "https://github.com/a.apk")))
            server.enqueue(MockResponse().setResponseCode(404))
            val found = AppUpdater.check("1.0.0", client, server.url("/latest").toString())
            assertEquals("2.0.0", found?.version)
            assertEquals("RemoteMC-Android/1.0.0", server.takeRequest().getHeader("User-Agent"))
            // No release yet: nothing to do.
            assertNull(AppUpdater.check("1.0.0", client, server.url("/latest").toString()))
        }
    }

    @Test
    fun downloadChecksTheChecksum() = runBlocking {
        val apk = "fake apk".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
        val context = RuntimeEnvironment.getApplication()
        MockWebServer().use { server ->
            repeat(3) { server.enqueue(MockResponse().setBody(Buffer().write(apk))) }
            val url = server.url("/app.apk").toString()
            val good = Release("1.1.0", "", "", url, apk.size.toLong(), sha)
            val file = AppUpdater.download(context, good, client, trusted = { true }) {}
            assertTrue(file.readBytes().contentEquals(apk))
            assertEquals(File(context.cacheDir, "updates"), file.parentFile)

            val tampered = good.copy(sha256 = "0".repeat(64))
            val error = runCatching { AppUpdater.download(context, tampered, client, trusted = { true }) {} }.exceptionOrNull()
            assertTrue(error is IOException)
            assertFalse(File(context.cacheDir, "updates/RemoteMC-1.1.0.apk").exists())

            // Only GitHub over HTTPS by default.
            assertNotNull(runCatching { AppUpdater.download(context, good, client) {} }.exceptionOrNull())
        }
    }
}
