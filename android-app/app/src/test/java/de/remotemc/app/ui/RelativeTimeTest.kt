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
}
