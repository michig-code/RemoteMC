package de.remotemc.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldTimeTest {
    private fun world(time: Long, cycle: String?) = WorldDetails(
        "world", "NORMAL", time, storm = false, thunder = false, difficulty = "NORMAL",
        gameRules = listOfNotNull(cycle?.let { GameRuleInfo("doDaylightCycle", true, it) }),
    )

    @Test
    fun clockRunsWithTheDaylightCycle() {
        assertEquals(1020L, world(1000, "true").timeAfter(1000)) // 20 ticks per second
        assertEquals(1000L + 1200, world(1000, null).timeAfter(60_000)) // unknown rule: assume it runs
        assertEquals(1000L, world(1000, "false").timeAfter(60_000)) // frozen time stays
        assertEquals(1000L, world(1000, "true").timeAfter(-5)) // the clock never runs backwards
    }
}
