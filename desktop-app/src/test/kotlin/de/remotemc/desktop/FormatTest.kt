package de.remotemc.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatTest {
    @Test fun clock() {
        assertEquals("06:00", clockTime(0))
        assertEquals("18:30", clockTime(12_500))
        assertEquals("00:00", clockTime(18_000))
        assertEquals("05:00", clockTime(-1000))
    }

    @Test fun time() {
        assertEquals("04:05", parseTime("4:05"))
        assertEquals("23:59", parseTime(" 23:59 "))
        assertNull(parseTime("24:00"))
        assertNull(parseTime("12:60"))
        assertNull(parseTime("1200"))
    }

    @Test fun relativeTimes() {
        assertEquals("5 min", relative(5 * 60_000L, 0))
        assertEquals("2 h 5 min", relative(125 * 60_000L, 0))
        assertEquals("3 d", relative(3 * 86_400_000L, 0))
    }
}
