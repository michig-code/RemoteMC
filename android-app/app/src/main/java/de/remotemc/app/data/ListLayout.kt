package de.remotemc.app.data

import kotlin.math.roundToInt

/*
 * How the server list looks: the size of each card in grid cells, what a card shows in which order
 * and the order of the screen's building blocks. Shared by the phone and the Windows app.
 */

/** The grid of the server list. */
object CardGrid {
    /** Tallest card, in rows. */
    const val MAX_ROWS = 4
    /** Default height of a card, in rows (as tall as the cards were before the grid). */
    const val DEFAULT_ROWS = 2

    /** Width and height of [server]'s card in a grid of [columns] columns. */
    fun size(server: ServerProfile, columns: Int): Pair<Int, Int> {
        val w = if (server.cardW <= 0) columns else server.cardW.coerceIn(1, columns)
        val h = if (server.cardH <= 0) DEFAULT_ROWS else server.cardH.coerceIn(1, MAX_ROWS)
        return w to h
    }

    /**
     * New size while a handle is dragged by ([dx], [dy]) pixels from a card of [w]×[h] cells; one
     * cell is [cellW]×[cellH] pixels (with the gap). Snaps to whole cells, at least 1×1.
     */
    fun resize(w: Int, h: Int, dx: Float, dy: Float, cellW: Float, cellH: Float, columns: Int): Pair<Int, Int> {
        val nw = if (cellW > 0) (w + (dx / cellW).roundToInt()).coerceIn(1, columns) else w
        val nh = if (cellH > 0) (h + (dy / cellH).roundToInt()).coerceIn(1, MAX_ROWS) else h
        return nw to nh
    }
}

/** Parts of a server card; which of them fit depends on the card's size. */
enum class CardPart {
    NAME, ADDRESS, STATUS, PLAYERS, TPS, MEMORY, VERSION, MOTD;

    /** Small parts share one line. */
    val inline get() = this == STATUS || this == PLAYERS || this == TPS
}

/** Building blocks of the server list screen, from top to bottom. */
enum class ScreenBlock { HEADER, SEARCH, SERVERS }

/** What the user arranged: card parts and screen blocks in their order. Saved as text. */
data class ListLayout(
    val parts: List<CardPart> = DEFAULT_PARTS,
    val blocks: List<ScreenBlock> = ScreenBlock.entries,
) {
    fun moveBlock(from: Int, to: Int) = copy(blocks = blocks.moved(from, to))
    fun movePart(from: Int, to: Int) = copy(parts = parts.moved(from, to))

    /** "parts=NAME,ADDRESS,…;blocks=HEADER,…" */
    fun encode() = "parts=" + parts.joinToString(",") + ";blocks=" + blocks.joinToString(",")

    /**
     * The lines a card of [w]×[h] cells shows: parts in the user's order, small ones sharing a
     * line, as many lines as fit. A 1×1 card shows only its status (see the card itself).
     */
    fun lines(w: Int, h: Int): List<List<CardPart>> {
        val wanted = parts.filter { w >= 2 || (it != CardPart.ADDRESS && it != CardPart.MOTD && it != CardPart.VERSION) }
        val lines = mutableListOf<MutableList<CardPart>>()
        wanted.forEach { part ->
            val last = lines.lastOrNull()
            if (part.inline && last != null && last.all { it.inline } && last.size < maxOf(1, w)) last += part
            else lines += mutableListOf(part)
        }
        val fit = when (h) { 1 -> 1; 2 -> 3; 3 -> 5; else -> 7 }
        val shown = lines.take(fit)
        // The name line holds the menu, so it is always there.
        return if (shown.any { CardPart.NAME in it }) shown else shown.dropLast(1) + listOf(listOf(CardPart.NAME))
    }

    companion object {
        val DEFAULT_PARTS = listOf(
            CardPart.NAME, CardPart.ADDRESS, CardPart.STATUS, CardPart.PLAYERS, CardPart.TPS,
            CardPart.MEMORY, CardPart.VERSION, CardPart.MOTD,
        )

        fun decode(text: String?): ListLayout {
            if (text.isNullOrBlank()) return ListLayout()
            val fields = text.split(';').associate { it.substringBefore('=') to it.substringAfter('=', "") }
            fun <T : Enum<T>> list(key: String, all: List<T>): List<T> {
                val saved = fields[key].orEmpty().split(',').mapNotNull { n -> all.firstOrNull { it.name == n.trim() } }.distinct()
                return saved + all.filter { it !in saved } // new parts or blocks are added at the end
            }
            return ListLayout(list("parts", DEFAULT_PARTS), list("blocks", ScreenBlock.entries))
        }
    }
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
