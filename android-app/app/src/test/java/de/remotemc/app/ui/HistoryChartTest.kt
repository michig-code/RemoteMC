package de.remotemc.app.ui

import de.remotemc.app.data.HistoryPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryChartTest {

    private fun p(min: Long, tps: Double?, players: Int = 1) = HistoryPoint(min * 60_000, tps, null, 1000, players)

    @Test
    fun gapsBreakTheLine() {
        val points = listOf(p(0, 20.0), p(1, 19.0), p(2, 18.0), p(10, 20.0), p(11, 20.0))
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4)), segments(points, Metric.TPS, 60_000))
    }

    @Test
    fun missingValuesBreakTheLine() {
        val points = listOf(p(0, 20.0), p(1, null), p(2, 18.0), p(3, 19.0))
        assertEquals(listOf(listOf(0), listOf(2, 3)), segments(points, Metric.TPS, 60_000))
        // Players are always known.
        assertEquals(listOf(listOf(0, 1, 2, 3)), segments(points, Metric.PLAYERS, 60_000))
    }

    @Test
    fun yAxis() {
        assertEquals(20.0, chartMax(Metric.TPS, listOf(12.0)), 0.0)
        assertEquals(11.5, chartMax(Metric.PLAYERS, listOf(3.0, 10.0)), 0.001)
        assertEquals(1.15, chartMax(Metric.PLAYERS, listOf(0.0)), 0.001)
    }
}
