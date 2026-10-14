package de.remotemc.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardLayoutTest {
    private val defaults = listOf(
        DashCard("players", CardSize.HALF), DashCard("tps", CardSize.HALF),
        DashCard("memory", CardSize.THIRD), DashCard("history", CardSize.FULL),
    )

    @Test
    fun emptyTextGivesDefaults() {
        assertEquals(defaults, DashboardLayout.decode("", defaults).cards)
        assertEquals(defaults, DashboardLayout.decode(null, defaults).cards)
    }

    @Test
    fun roundTrip() {
        val changed = DashboardLayout(defaults).move("history", "players").toggleHidden("tps").resize("memory", allowThird = true)
        assertEquals("history:full,players:half,-tps:half,memory:full", changed.encode())
        assertEquals(changed, DashboardLayout.decode(changed.encode(), defaults))
    }

    @Test
    fun newCardsAppearAndUnknownOnesAreDropped() {
        // Saved by an older version without "memory", with a card that no longer exists.
        val layout = DashboardLayout.decode("history:full,old:half,players:third", defaults)
        assertEquals(listOf("history", "players", "tps", "memory"), layout.cards.map { it.id })
        assertEquals(CardSize.THIRD, layout.cards[1].size)
        // garbage sizes fall back to the default size
        assertEquals(CardSize.HALF, DashboardLayout.decode("players:huge", defaults).cards.first { it.id == "players" }.size)
    }

    @Test
    fun spans() {
        assertEquals(listOf(6, 3, 2), CardSize.entries.map { it.span(6) })
        assertEquals(listOf(2, 1, 1), CardSize.entries.map { it.span(2) })
        assertEquals(CardSize.FULL, CardSize.HALF.next(allowThird = false))
        assertEquals(CardSize.THIRD, CardSize.HALF.next(allowThird = true))
    }

    @Test
    fun handleSetsAFreeWidth() {
        val layout = DashboardLayout(defaults).setSpan("players", 5, 6).setSpan("tps", 3, 4)
        assertEquals(5, layout.cards[0].span(6))
        assertEquals(3, layout.cards[1].span(4))
        assertEquals("players:5,tps:4,memory:third,history:full", layout.encode())
        assertEquals(layout, DashboardLayout.decode(layout.encode(), defaults))
        assertEquals(4, DashCard("x", CardSize.HALF, width = 4).span(6))
    }
}
