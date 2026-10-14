package de.remotemc.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class HelpSearchTest {
    @Test
    fun ignoresCaseAndAccentsAndNeedsEveryWord() {
        assertTrue(HelpSearch.matches("Übersicht anpassen", "ubersicht"))
        assertTrue(HelpSearch.matches("Whitelist and bans", "BANS white"))
        assertFalse(HelpSearch.matches("Whitelist and bans", "bans macro"))
        assertTrue(HelpSearch.matches("anything", "  "))
    }

    private fun search(query: String): List<String> {
        val context = RuntimeEnvironment.getApplication()
        return HelpTopics.filter { t ->
            HelpSearch.matches(listOf(t.title, t.where, t.text, t.keys).joinToString(" ") { context.getString(it) }, query)
        }.map { it.id }
    }

    @Test
    fun findsWhereThingsAre() {
        assertEquals(listOf("command_menu"), search("macro"))
        assertTrue("whitelist" in search("unban"))
        assertTrue("files" in search("server.properties"))
        assertTrue("notifications" in search("quiet hours"))
    }

    @Test
    @Config(qualifiers = "de")
    fun searchesInTheAppLanguage() {
        assertEquals(listOf("command_menu"), search("makro"))
        assertTrue("whitelist" in search("entbannen"))
        assertTrue("customize" in search("übersicht anpassen"))
    }

    @Test
    fun everyTopicHasAValidSpot() {
        val ids = HelpTopics.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        HelpTopics.forEach { t ->
            t.spot?.let {
                assertTrue(t.id, it.left >= 0f && it.top >= 0f && it.left + it.width <= 1.001f && it.top + it.height <= 1.001f)
                assertTrue(t.id, t.image != null)
            }
        }
    }
}
