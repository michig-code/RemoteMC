package de.remotemc.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListLayoutTest {
    private val server = ServerProfile(id = "a", name = "A", host = "h", token = "t")

    @Test
    fun defaultSizeIsFullWidthAndTwoRows() {
        assertEquals(4 to 2, CardGrid.size(server, 4))
        assertEquals(2 to 3, CardGrid.size(server.copy(cardW = 2, cardH = 3), 4))
        assertEquals(4 to 4, CardGrid.size(server.copy(cardW = 9, cardH = 9), 4))
    }

    @Test
    fun resizeSnapsToWholeCells() {
        assertEquals(3 to 2, CardGrid.resize(4, 2, -60f, 10f, 100f, 80f, 4))
        assertEquals(1 to 1, CardGrid.resize(2, 2, -500f, -500f, 100f, 80f, 4))
        assertEquals(4 to 4, CardGrid.resize(2, 2, 500f, 500f, 100f, 80f, 4))
    }

    @Test
    fun linesFollowTheOrderAndTheSize() {
        val layout = ListLayout()
        assertEquals(listOf(listOf(CardPart.NAME)), layout.lines(4, 1))
        assertEquals(
            listOf(listOf(CardPart.NAME), listOf(CardPart.ADDRESS), listOf(CardPart.STATUS, CardPart.PLAYERS, CardPart.TPS)),
            layout.lines(4, 2),
        )
        // Narrow cards leave out the long texts.
        assertTrue(layout.lines(1, 4).none { CardPart.ADDRESS in it || CardPart.MOTD in it })
        // The name line is always there, even when moved to the end.
        val moved = layout.movePart(0, layout.parts.lastIndex)
        assertTrue(moved.lines(4, 1).single() == listOf(CardPart.NAME))
    }

    @Test
    fun encodeAndDecode() {
        val layout = ListLayout().movePart(0, 2).moveBlock(0, 2)
        assertEquals(layout, ListLayout.decode(layout.encode()))
        assertEquals(ListLayout(), ListLayout.decode(""))
        // Unknown names are dropped, missing ones added at the end.
        assertEquals(ScreenBlock.entries, ListLayout.decode("blocks=FOO").blocks)
    }
}
