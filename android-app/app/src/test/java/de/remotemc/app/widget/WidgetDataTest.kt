package de.remotemc.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetDataTest {

    @Test
    fun roundTrip() {
        val data = WidgetData(
            listOf(
                WidgetServer("a", "Survival", online = true, players = 3, maxPlayers = 20, tps = 19.8),
                WidgetServer("b", "Folia", online = true, players = 1, maxPlayers = 50, tps = null),
                WidgetServer("c", "Test \"server\"", online = false),
            ),
            updatedAt = 123,
        )
        assertEquals(data, WidgetData.fromJson(data.toJson()))
        assertEquals(2, data.onlineCount)
    }
}
