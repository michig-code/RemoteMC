package de.remotemc.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    @Test
    fun durations() {
        assertEquals("1 min", relative(30_000, 0))
        assertEquals("59 min", relative(59 * 60_000L, 0))
        assertEquals("2 h 5 min", relative((125 * 60_000L), 0))
        assertEquals("3 d", relative(3 * 24 * 3_600_000L, 0))
        assertEquals("0 min", relative(0, 10))
    }

    @Test
    fun playtime() {
        assertEquals("0 min", formatPlaytime(30))
        assertEquals("45 min", formatPlaytime(45 * 60))
        assertEquals("12 h 5 min", formatPlaytime((12 * 60 + 5) * 60L))
        assertEquals("3 d 4 h", formatPlaytime((3 * 24 + 4) * 3600L))
    }
}
