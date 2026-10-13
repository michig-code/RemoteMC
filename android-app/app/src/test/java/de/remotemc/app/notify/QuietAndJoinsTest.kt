package de.remotemc.app.notify

import de.remotemc.app.data.AlertSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietAndJoinsTest {
    @Test
    fun quietHoursOverMidnight() {
        val s = AlertSettings(quiet = true, quietFrom = 22 * 60, quietTo = 7 * 60)
        assertTrue(s.isQuiet(23 * 60))
        assertTrue(s.isQuiet(0))
        assertTrue(s.isQuiet(6 * 60 + 59))
        assertFalse(s.isQuiet(7 * 60))
        assertFalse(s.isQuiet(12 * 60))
        assertTrue(s.isQuiet(22 * 60))
    }

    @Test
    fun quietHoursSameDayAndOff() {
        val s = AlertSettings(quiet = true, quietFrom = 13 * 60, quietTo = 15 * 60)
        assertTrue(s.isQuiet(14 * 60))
        assertFalse(s.isQuiet(15 * 60))
        assertFalse(s.copy(quiet = false).isQuiet(14 * 60))
        assertFalse(s.copy(quietTo = 13 * 60).isQuiet(13 * 60)) // empty window
    }

    @Test
    fun joinsAreBundledPerServerForAWhile() {
        val bundle = JoinBundle(windowMs = 1000)
        assertEquals(listOf("Steve"), bundle.add("a", "Steve", 0))
        assertEquals(listOf("Steve", "Alex"), bundle.add("a", "Alex", 100))
        assertEquals(listOf("Sam"), bundle.add("b", "Sam", 150))
        // Rejoining moves the player to the end instead of listing them twice.
        assertEquals(listOf("Alex", "steve"), bundle.add("a", "steve", 200))
        // Old joins drop out.
        assertEquals(listOf("steve", "Notch"), bundle.add("a", "Notch", 1150))
        bundle.clear("a")
        assertEquals(listOf("Zoe"), bundle.add("a", "Zoe", 1200))
    }
}
