package de.remotemc.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdaterTest {
    @Test
    fun versions() {
        assertTrue(Updater.isNewer("1.0.1", "1.0.0"))
        assertTrue(Updater.isNewer("v1.2", "1.1.9"))
        assertTrue(Updater.isNewer("1.10.0", "1.9.3"))
        assertFalse(Updater.isNewer("1.0.0", "1.0.0"))
        assertFalse(Updater.isNewer("0.9.9", "1.0.0"))
        assertTrue(Updater.isNewer("1.0.0", "1.0.0-beta"))
        assertFalse(Updater.isNewer("1.0.0-beta", "1.0.0"))
    }

    private fun release(tag: String, msiHost: String = "github.com", prerelease: Boolean = false) = """
        {"tag_name":"$tag","draft":false,"prerelease":$prerelease,"body":"- New: things\n- Fixed: stuff",
         "html_url":"https://github.com/michig-code/RemoteMC/releases/tag/$tag",
         "assets":[
           {"name":"RemoteMC-1.1.0.jar","browser_download_url":"https://github.com/x/RemoteMC-1.1.0.jar","size":200000},
           {"name":"RemoteMC-Windows-1.1.0.msi","browser_download_url":"https://$msiHost/michig-code/RemoteMC/releases/download/$tag/RemoteMC-Windows-1.1.0.msi",
            "size":123456789,"digest":"sha256:ABCDEF0123"}
         ]}
    """.trimIndent()

    @Test
    fun readsTheInstallerOfANewerRelease() {
        val info = Updater.parseRelease(release("v1.1.0"), "1.0.0")!!
        assertEquals("1.1.0", info.version)
        assertTrue(info.msiUrl!!.endsWith("RemoteMC-Windows-1.1.0.msi"))
        assertEquals(123456789, info.msiSize)
        assertEquals("abcdef0123", info.sha256)
        assertTrue(info.notes.startsWith("- New"))
    }

    @Test
    fun ignoresOldPreAndForeignReleases() {
        assertNull(Updater.parseRelease(release("v1.0.0"), "1.0.0"))
        assertNull(Updater.parseRelease(release("v1.2.0", prerelease = true), "1.0.0"))
        // An installer that is not on GitHub is never downloaded.
        assertNull(Updater.parseRelease(release("v1.1.0", msiHost = "evil.example.com"), "1.0.0")!!.msiUrl)
        assertFalse(Updater.isTrusted("http://github.com/a.msi"))
        assertTrue(Updater.isTrusted("https://objects.githubusercontent.com/a.msi"))
    }
}
