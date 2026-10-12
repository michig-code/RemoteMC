package de.remotemc.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.remotemc.app.R
import de.remotemc.app.data.HistoryPoint
import de.remotemc.app.data.HistoryRange
import de.remotemc.app.data.PerformanceHistory
import de.remotemc.app.net.ApiError
import de.remotemc.app.ui.components.McCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** What the chart shows. */
internal enum class Metric(val label: Int) {
    TPS(R.string.history_metric_tps),
    PLAYERS(R.string.history_metric_players),
    MEMORY(R.string.history_metric_memory),
    MSPT(R.string.history_metric_mspt);

    fun value(p: HistoryPoint): Double? = when (this) {
        TPS -> p.tps
        PLAYERS -> p.players.toDouble()
        MEMORY -> p.memoryMb.toDouble()
        MSPT -> p.mspt
    }

    fun format(v: Double): String = when (this) {
        TPS -> "%.1f".format(v)
        PLAYERS -> v.roundToInt().toString()
        MEMORY -> if (v >= 1024) "%.1f GB".format(v / 1024) else "${v.roundToInt()} MB"
        MSPT -> "%.1f ms".format(v)
    }
}

private sealed interface Load {
    data object Loading : Load
    data class Loaded(val history: PerformanceHistory) : Load
    data object Failed : Load
    /** History turned off in the plugin config, or an older plugin: the card is hidden. */
    data object Unavailable : Load
}

/** Chart of TPS, players, memory or MSPT over the last hour, day or week. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryCard(load: suspend (HistoryRange) -> PerformanceHistory, modifier: Modifier = Modifier) {
    var range by rememberSaveable { mutableStateOf(HistoryRange.DAY) }
    var metric by rememberSaveable { mutableStateOf(Metric.TPS) }
    var state by remember { mutableStateOf<Load>(Load.Loading) }

    LaunchedEffect(range) {
        state = Load.Loading
        while (true) {
            state = try {
                Load.Loaded(load(range))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError.Http) {
                if (e.status == 404) Load.Unavailable else Load.Failed
            } catch (e: Exception) {
                if (state is Load.Loaded) state else Load.Failed
            }
            delay(60_000) // the plugin records one value per minute
        }
    }
    if (state == Load.Unavailable) return

    McCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Timeline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                HistoryRange.entries.forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = range == r,
                        onClick = { range = r },
                        shape = SegmentedButtonDefaults.itemShape(i, HistoryRange.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        icon = {},
                    ) {
                        Text(
                            stringResource(
                                when (r) {
                                    HistoryRange.HOUR -> R.string.history_1h
                                    HistoryRange.DAY -> R.string.history_24h
                                    HistoryRange.WEEK -> R.string.history_7d
                                },
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric.entries.forEach { m ->
                    FilterChip(
                        selected = metric == m,
                        onClick = { metric = m },
                        label = { Text(stringResource(m.label)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(state, label = "history", transitionSpec = { fadeIn() togetherWith fadeOut() }) { s ->
                when (s) {
                    is Load.Loaded -> ChartWithSummary(s.history, metric)
                    Load.Loading -> Box(Modifier.fillMaxWidth().height(CHART_HEIGHT), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                    else -> Box(Modifier.fillMaxWidth().height(CHART_HEIGHT), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.history_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private val CHART_HEIGHT = 170.dp

@Composable
private fun ChartWithSummary(history: PerformanceHistory, metric: Metric) {
    val values = history.points.mapNotNull { metric.value(it) }
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(CHART_HEIGHT), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    var selected by remember(history, metric) { mutableStateOf<HistoryPoint?>(null) }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    val dateTime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    val fmt = if (history.to - history.from >= 86_400_000L) dateTime else time

    Column {
        val shown = selected
        Text(
            if (shown != null) {
                "${fmt.format(Date(shown.time))} · ${metric.value(shown)?.let(metric::format) ?: "–"}"
            } else {
                stringResource(
                    R.string.history_summary,
                    metric.format(values.average()), metric.format(values.min()), metric.format(values.max()),
                )
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (shown != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LineChart(history, metric, selected) { selected = it }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(fmt.format(Date(history.from)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(fmt.format(Date(history.to)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Y range of the chart: TPS always 0–20, the others from 0 to a bit above the maximum. */
internal fun chartMax(metric: Metric, values: List<Double>): Double = when (metric) {
    Metric.TPS -> 20.0
    else -> (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0) * 1.15
}

/**
 * Lines between neighbouring points; a gap of more than two steps (server off) breaks the line.
 * Returns runs of point indices.
 */
internal fun segments(points: List<HistoryPoint>, metric: Metric, step: Long): List<List<Int>> {
    val runs = mutableListOf<MutableList<Int>>()
    var last: HistoryPoint? = null
    points.forEachIndexed { i, p ->
        if (metric.value(p) == null) {
            last = null
            return@forEachIndexed
        }
        val prev = last
        if (prev == null || p.time - prev.time > step * 2) runs += mutableListOf<Int>()
        runs.last() += i
        last = p
    }
    return runs
}

@Composable
private fun LineChart(history: PerformanceHistory, metric: Metric, selected: HistoryPoint?, onSelect: (HistoryPoint?) -> Unit) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val points = history.points
    val values = points.mapNotNull { metric.value(it) }
    val max = chartMax(metric, values)
    val span = (history.to - history.from).coerceAtLeast(1)
    val runs = remember(history, metric) { segments(points, metric, history.step) }
    val summary = stringResource(
        R.string.history_summary,
        metric.format(values.average()), metric.format(values.min()), metric.format(values.max()),
    )

    fun nearest(x: Float, width: Float): HistoryPoint? {
        val t = history.from + (x / width * span).toLong()
        return points.filter { metric.value(it) != null }.minByOrNull { kotlin.math.abs(it.time - t) }
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .semantics { contentDescription = summary }
            .pointerInput(history, metric) {
                detectTapGestures(onPress = { onSelect(nearest(it.x, size.width.toFloat())); tryAwaitRelease(); onSelect(null) })
            }
            .pointerInput(history, metric) {
                detectDragGestures(
                    onDragStart = { onSelect(nearest(it.x, size.width.toFloat())) },
                    onDragEnd = { onSelect(null) },
                    onDragCancel = { onSelect(null) },
                ) { change, _ -> onSelect(nearest(change.position.x, size.width.toFloat())) }
            },
    ) {
        fun x(p: HistoryPoint) = ((p.time - history.from).toFloat() / span) * size.width
        fun y(v: Double) = size.height - (v / max * size.height).toFloat()

        // Grid: quarter lines, dashed.
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        for (i in 1..3) {
            val gy = size.height * i / 4f
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        if (metric == Metric.TPS) {
            // 18 TPS: below this, players notice lag.
            val ly = y(18.0)
            drawLine(Color(0x55FBBF24), Offset(0f, ly), Offset(size.width, ly), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }

        runs.forEach { run ->
            val path = Path()
            val fill = Path()
            run.forEachIndexed { k, i ->
                val p = points[i]
                val px = x(p)
                val py = y(metric.value(p)!!)
                if (k == 0) {
                    path.moveTo(px, py)
                    fill.moveTo(px, size.height)
                    fill.lineTo(px, py)
                } else {
                    path.lineTo(px, py)
                    fill.lineTo(px, py)
                }
            }
            val lastX = x(points[run.last()])
            if (run.size == 1) {
                drawCircle(line, 2.5.dp.toPx(), Offset(lastX, y(metric.value(points[run.first()])!!)))
            } else {
                fill.lineTo(lastX, size.height)
                fill.close()
                drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.28f), line.copy(alpha = 0.02f))))
                drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        selected?.let { p ->
            val v = metric.value(p) ?: return@let
            val sx = x(p)
            drawLine(line.copy(alpha = 0.5f), Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(line, 5.dp.toPx(), Offset(sx, y(v)))
            drawCircle(Color.White, 2.dp.toPx(), Offset(sx, y(v)))
        }
    }
}
