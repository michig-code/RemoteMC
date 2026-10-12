package de.mcremote.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import de.mcremote.app.R
import de.mcremote.app.ui.theme.McColors

enum class ConnectionState { Connecting, Online, Offline }

/** The app logo on its gradient tile. */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Color(0xFF1E3A2B), Color(0xFF0B1410)))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().scale(1.45f),
        )
    }
}

/** Small dot that pulses while online / connecting. */
@Composable
fun PulsingDot(state: ConnectionState, size: Dp = 10.dp) {
    val color by animateColorAsState(
        when (state) {
            ConnectionState.Online -> McColors.Online
            ConnectionState.Offline -> McColors.Offline
            ConnectionState.Connecting -> McColors.Warning
        },
        label = "dotColor",
    )
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "pulseProgress",
    )
    Box(Modifier.size(size * 2.2f), contentAlignment = Alignment.Center) {
        if (state != ConnectionState.Offline) {
            Box(
                Modifier
                    .size(size * (1f + pulse * 1.2f))
                    .background(color.copy(alpha = (1f - pulse) * 0.45f), CircleShape),
            )
        }
        Box(Modifier.size(size).background(color, CircleShape))
    }
}

/**
 * Online/offline badge. Tapping it triggers [onClick] (refresh); the icon spins while [refreshing].
 */
@Composable
fun StatusPill(
    state: ConnectionState,
    text: String,
    refreshing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle",
    )
    val container by animateColorAsState(
        when (state) {
            ConnectionState.Online -> McColors.Online.copy(alpha = 0.14f)
            ConnectionState.Offline -> McColors.Offline.copy(alpha = 0.16f)
            ConnectionState.Connecting -> McColors.Warning.copy(alpha = 0.16f)
        },
        label = "pillColor",
    )
    Row(
        modifier
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(start = 2.dp, end = 10.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(state, 8.dp)
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.Default.Refresh,
            contentDescription = "Aktualisieren",
            modifier = Modifier.size(14.dp).rotate(if (refreshing) angle else 0f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Card surface used across the app. */
@Composable
fun McCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
            content = content,
        )
    }
}

/** Integer that counts up/down smoothly when it changes. */
@Composable
fun AnimatedCounter(value: Int, style: androidx.compose.ui.text.TextStyle, color: Color = Color.Unspecified) {
    val animated by animateIntAsState(value, tween(600, easing = FastOutSlowInEasing), label = "counter")
    Text(animated.toString(), style = style, color = color)
}

/** Circular TPS gauge (0–20) with animated sweep and colour. */
@Composable
fun TpsGauge(tps: Double, modifier: Modifier = Modifier, stroke: Dp = 10.dp) {
    val fraction by animateFloatAsState(
        (tps / 20.0).toFloat().coerceIn(0f, 1f),
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "tps",
    )
    val color by animateColorAsState(tpsColor(tps), label = "tpsColor")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arcSize = Size(size.width - s, size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(track, 135f, 270f, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * fraction, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%.1f".format(tps), style = MaterialTheme.typography.headlineMedium, color = color)
            Text("TPS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun tpsColor(tps: Double) = when {
    tps >= 18 -> McColors.Online
    tps >= 15 -> McColors.Warning
    else -> McColors.Offline
}

/** Horizontal animated bar, e.g. for RAM usage. */
@Composable
fun AnimatedBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "bar")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .drawBehind {
                drawRect(track)
                drawRoundRect(
                    Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)),
                    size = Size(size.width * animated, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
                )
            },
    )
}

/** Player head from mc-heads.net with a letter fallback while loading / offline. */
@Composable
fun PlayerHead(name: String, size: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.22f)
    SubcomposeAsyncImage(
        model = "https://mc-heads.net/avatar/$name/${(size.value * 3).toInt()}",
        contentDescription = name,
        contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.None,
        modifier = modifier.size(size).clip(shape),
        loading = { HeadFallback(name, size) },
        error = { HeadFallback(name, size) },
    )
}

@Composable
private fun HeadFallback(name: String, size: Dp) {
    val hue = (name.hashCode() and 0xFFFFFF) % 360
    Box(
        Modifier.fillMaxSize().background(Color.hsv(hue.toFloat(), 0.45f, 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp,
        )
    }
}

/** Rounded small label, e.g. "OP" or the game mode. */
@Composable
fun Badge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
    )
}

/** Shimmering placeholder block while data loads. */
@Composable
fun Shimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "x",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier
            .clip(MaterialTheme.shapes.large)
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        listOf(base, highlight, base),
                        start = Offset(size.width * x - size.width, 0f),
                        end = Offset(size.width * x, size.height),
                    ),
                )
            },
    )
}

/** Centered illustration + text for empty lists. */
@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    val scale = remember { androidx.compose.animation.core.Animatable(0.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .scale(scale.value)
                .size(88.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Small uppercase section heading. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    )
}

/** Fades and slides content in, delayed by [index] for a staggered list entrance. */
fun Modifier.staggeredAppear(index: Int): Modifier = composed {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index.coerceAtMost(10) * 55L)
        progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 36.dp.toPx()
    }
}
