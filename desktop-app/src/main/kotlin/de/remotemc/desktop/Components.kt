package de.remotemc.desktop

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.remotemc.app.net.ApiError
import de.remotemc.desktop.res.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/** False when the user turned animations off in the settings. */
val LocalAnimations = androidx.compose.runtime.staticCompositionLocalOf { true }

/** [spec], or an instant change when animations are off. */
@Composable
fun <T> motion(spec: androidx.compose.animation.core.AnimationSpec<T> = androidx.compose.animation.core.spring()): androidx.compose.animation.core.AnimationSpec<T> =
    if (LocalAnimations.current) spec else androidx.compose.animation.core.snap()

/** Fades and slides content in, a little later for each [index] (lists appearing one by one). */
fun Modifier.staggeredAppear(index: Int): Modifier = composed {
    if (!LocalAnimations.current) return@composed this
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // The delay is part of the animation (frame driven), no separate timer.
        progress.animateTo(
            1f,
            androidx.compose.animation.core.tween(380, delayMillis = index.coerceAtMost(10) * 45, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        )
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 28.dp.toPx()
    }
}

@Composable
fun McCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        // Clickable cards light up a little under the mouse.
        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        val color by androidx.compose.animation.animateColorAsState(
            if (hovered) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer, motion(), label = "cardHover",
        )
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, interactionSource = interaction) { content() }
    } else {
        Surface(modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
)

@Composable
fun Badge(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

enum class Conn { LIVE, POLLING, CONNECTING, OFFLINE }

/** Dot that pulses while connected. */
@Composable
fun StatusDot(state: Conn, size: Dp = 10.dp) {
    val color = when (state) {
        Conn.LIVE, Conn.POLLING -> McColors.Online
        Conn.CONNECTING -> McColors.Warning
        Conn.OFFLINE -> McColors.Offline
    }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "p",
    )
    val animate = LocalAnimations.current
    Box(Modifier.size(size * 2.2f), contentAlignment = Alignment.Center) {
        if (state != Conn.OFFLINE && animate) {
            Box(Modifier.size(size * (1f + pulse * 1.2f)).background(color.copy(alpha = (1f - pulse) * 0.45f), CircleShape))
        }
        Box(Modifier.size(size).background(color, CircleShape))
    }
}

@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val shown by androidx.compose.animation.core.animateFloatAsState(
        fraction.coerceIn(0f, 1f), motion(androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing)), label = "bar",
    )
    val tint by androidx.compose.animation.animateColorAsState(color, motion(), label = "barColor")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(Modifier.fillMaxWidth(shown).fillMaxHeight().clip(CircleShape).background(tint))
    }
}

/** The RemoteMC logo (also the window icon). */
@Composable
fun AppLogo(size: Dp) {
    Image(
        org.jetbrains.compose.resources.painterResource(Res.drawable.logo), null,
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)),
    )
}

/** Same texts as the phone app for network and API errors. */
suspend fun errorText(e: Throwable): String = when (e) {
    is ApiError.Unreachable -> getString(Res.string.error_unreachable)
    is ApiError.Timeout -> getString(Res.string.error_timeout)
    is ApiError.CertificateChanged -> getString(Res.string.error_certificate_changed)
    is ApiError.Tls -> getString(Res.string.error_tls)
    is ApiError.InvalidResponse -> getString(Res.string.error_invalid_response)
    is ApiError.Incompatible -> getString(if (e.pluginTooOld) Res.string.error_plugin_too_old else Res.string.error_app_too_old)
    is ApiError.Http -> when (e.code) {
        "invalid_token" -> getString(Res.string.error_invalid_token)
        "invalid_code" -> getString(Res.string.error_invalid_code)
        "locked_out" -> getString(Res.string.error_locked_out)
        "forbidden" -> getString(Res.string.error_forbidden)
        "command_blocked" -> getString(Res.string.error_command_blocked)
        "player_offline" -> getString(Res.string.error_player_offline)
        "player_unknown" -> getString(Res.string.error_player_unknown)
        "world_unknown" -> getString(Res.string.error_world_unknown)
        "too_large" -> getString(Res.string.error_too_large)
        "too_many_streams" -> getString(Res.string.error_too_many_streams)
        "timeout" -> getString(Res.string.error_server_timeout)
        "not_found" -> getString(Res.string.error_needs_plugin_update)
        "file_not_found" -> getString(Res.string.error_file_not_found)
        "binary_file" -> getString(Res.string.error_binary_file)
        "file_too_large" -> getString(Res.string.error_file_too_large)
        "file_locked" -> getString(Res.string.error_file_locked)
        "file_exists" -> getString(Res.string.error_file_exists)
        else -> e.message?.takeIf { it.isNotBlank() } ?: getString(Res.string.error_http, e.status)
    }
    else -> e.message ?: getString(Res.string.error_unknown)
}

/** Player heads cut from the real skin (8×8 face plus the hat layer), cached per name. */
object Heads {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val client = OkHttpClient()

    suspend fun load(name: String, skinUrl: String?): ImageBitmap? = cache[name] ?: withContext(Dispatchers.IO) {
        val url = skinUrl ?: "https://minotar.net/skin/$name"
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val skin = ImageIO.read(r.body!!.byteStream()) ?: return@use null
                face(skin)
            }
        }.getOrNull()?.also { cache[name] = it }
    }

    fun face(skin: BufferedImage): ImageBitmap {
        val scale = skin.width / 64
        val out = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.drawImage(skin, 0, 0, 8, 8, 8 * scale, 8 * scale, 16 * scale, 16 * scale, null)
        g.drawImage(skin, 0, 0, 8, 8, 40 * scale, 8 * scale, 48 * scale, 16 * scale, null) // hat layer
        g.dispose()
        return out.toComposeImageBitmap()
    }
}

/** Player head; shows the first letter until the skin is loaded (or if it cannot be). */
@Composable
fun PlayerHead(name: String, size: Dp, skinUrl: String? = null) {
    var image by remember(name) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(name, skinUrl) { if (LoadHeads) image = Heads.load(name, skinUrl) }
    val shape = RoundedCornerShape(size * 0.22f)
    val img = image
    if (img != null) {
        Image(img, name, Modifier.size(size).clip(shape), filterQuality = FilterQuality.None)
    } else {
        val hue = (name.hashCode() and 0x7fffffff) % 360
        Box(Modifier.size(size).clip(shape).background(Color.hsl(hue.toFloat(), 0.35f, 0.42f)), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Off in screenshot tests, which have no network. */
var LoadHeads = true

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun roleLabel(role: de.remotemc.app.data.Role) = stringResource(
    when (role) {
        de.remotemc.app.data.Role.ADMIN -> Res.string.role_admin
        de.remotemc.app.data.Role.MODERATOR -> Res.string.role_moderator
        de.remotemc.app.data.Role.VIEWER -> Res.string.role_viewer
    },
)

