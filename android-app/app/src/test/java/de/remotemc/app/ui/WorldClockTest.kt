package de.remotemc.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldClockTest {
    @Test
    fun minecraftTicksAsClock() {
        assertEquals("06:00", clockTime(0))
        assertEquals("12:00", clockTime(6000))
        assertEquals("18:30", clockTime(12500))
        assertEquals("00:00", clockTime(18000))
        assertEquals("06:00", clockTime(24000 * 5)) // full time keeps counting days
        assertEquals("05:00", clockTime(-1000))
    }
}
