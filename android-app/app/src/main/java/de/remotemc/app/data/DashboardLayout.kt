package de.remotemc.app.data

/** Width of a card on the overview grid. */
enum class CardSize {
    FULL, HALF, THIRD;

    /** Columns taken in a grid of [columns] (6 on wide screens, 2 on phones). */
    fun span(columns: Int): Int = when (columns) {
        6 -> when (this) { FULL -> 6; HALF -> 3; THIRD -> 2 }
        else -> if (this == FULL) columns else (columns / 2).coerceAtLeast(1)
    }

    fun next(allowThird: Boolean): CardSize = when (this) {
        FULL -> HALF
        HALF -> if (allowThird) THIRD else FULL
        THIRD -> FULL
    }
}

/** A card of the overview; [width] in sixths of the row (1–6) when the user dragged its handle, else 0. */
data class DashCard(val id: String, val size: CardSize, val hidden: Boolean = false, val width: Int = 0) {
    /** Columns taken in a grid of [columns]. */
    fun span(columns: Int): Int =
        if (width <= 0) size.span(columns) else ((width * columns + 5) / 6).coerceIn(1, columns)
}

/**
 * Which overview cards are shown, in which order and how wide – chosen by the user by holding a
 * card. Saved as text like "players:half,tps:half,-memory:third" ("-" = hidden).
 */
data class DashboardLayout(val cards: List<DashCard>) {

    fun move(from: String, to: String): DashboardLayout {
        val i = cards.indexOfFirst { it.id == from }
        val j = cards.indexOfFirst { it.id == to }
        if (i < 0 || j < 0 || i == j) return this
        val list = cards.toMutableList()
        list.add(j, list.removeAt(i))
        return DashboardLayout(list)
    }

    fun toggleHidden(id: String) = DashboardLayout(cards.map { if (it.id == id) it.copy(hidden = !it.hidden) else it })

    fun resize(id: String, allowThird: Boolean) =
        DashboardLayout(cards.map { if (it.id == id) it.copy(size = it.size.next(allowThird)) else it })

    /** Card [id] made [span] columns wide in a grid of [columns] (dragging the frame's handle). */
    fun setSpan(id: String, span: Int, columns: Int) = DashboardLayout(
        cards.map { if (it.id == id) it.copy(width = (span.coerceIn(1, columns) * 6 / columns).coerceIn(1, 6)) else it },
    )

    fun encode(): String = cards.joinToString(",") {
        (if (it.hidden) "-" else "") + it.id + ":" + if (it.width > 0) it.width.toString() else it.size.name.lowercase()
    }

    companion object {
        /**
         * Restores a saved layout: known cards keep the saved order, size and visibility, cards that
         * are new in this version are added where the defaults have them, unknown ones are dropped.
         */
        fun decode(text: String?, defaults: List<DashCard>): DashboardLayout {
            if (text.isNullOrBlank()) return DashboardLayout(defaults)
            val known = defaults.associateBy { it.id }
            val saved = text.split(',').mapNotNull { part ->
                val hidden = part.startsWith("-")
                val (id, size) = part.removePrefix("-").split(':', limit = 2).let { it[0].trim() to it.getOrNull(1) }
                val base = known[id] ?: return@mapNotNull null
                val width = size?.toIntOrNull()?.takeIf { it in 1..6 } ?: 0
                val parsed = size?.let { s -> CardSize.entries.firstOrNull { it.name.equals(s, ignoreCase = true) } } ?: base.size
                DashCard(id, parsed, hidden, width)
            }.distinctBy { it.id }
            val result = saved.toMutableList()
            // A card new in this version goes right after the card it follows by default.
            defaults.forEachIndexed { index, card ->
                if (result.none { it.id == card.id }) {
                    val before = defaults.subList(0, index).lastOrNull { d -> result.any { it.id == d.id } }
                    result.add(before?.let { b -> result.indexOfFirst { it.id == b.id } + 1 } ?: 0, card)
                }
            }
            return DashboardLayout(result)
        }
    }
}
