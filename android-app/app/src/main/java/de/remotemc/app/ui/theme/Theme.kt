package de.remotemc.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fixed accent colors used for status indicators in both themes. */
object McColors {
    val Online = Color(0xFF4ADE80)
    val Warning = Color(0xFFFBBF24)
    val Offline = Color(0xFFF87171)
    // Darker variants with enough contrast for text on light backgrounds (WCAG AA).
    val OnlineText = Color(0xFF15803D)
    val WarningText = Color(0xFFB45309)
    val OfflineText = Color(0xFFDC2626)
    val Gold = Color(0xFFFFD54F)
    val Grass = Color(0xFF66BB6A)
    val Dirt = Color(0xFF7A4E2D)
    val Nether = Color(0xFFEF5350)
    val End = Color(0xFFB388FF)
    val Heart = Color(0xFFFF5370)
    val Food = Color(0xFFE0A15A)
    val Terminal = Color(0xFF0B0F0D)
}

private val Dark = darkColorScheme(
    primary = Color(0xFF6EE7A0),
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF1F4D33),
    onPrimaryContainer = Color(0xFFB9F6CF),
    secondary = Color(0xFFFFD54F),
    onSecondary = Color(0xFF3B2F00),
    secondaryContainer = Color(0xFF4A3D10),
    onSecondaryContainer = Color(0xFFFFE9A8),
    tertiary = Color(0xFFB388FF),
    background = Color(0xFF0E1411),
    onBackground = Color(0xFFE3EAE5),
    surface = Color(0xFF0E1411),
    onSurface = Color(0xFFE3EAE5),
    surfaceVariant = Color(0xFF1C2620),
    onSurfaceVariant = Color(0xFFA9B8AE),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF131B16),
    surfaceContainer = Color(0xFF17211B),
    surfaceContainerHigh = Color(0xFF1D2922),
    surfaceContainerHighest = Color(0xFF243229),
    outline = Color(0xFF3A4A40),
    outlineVariant = Color(0xFF2A362F),
    error = Color(0xFFF87171),
)

private val Light = lightColorScheme(
    primary = Color(0xFF16803C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F2D6),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF8A6A00),
    secondaryContainer = Color(0xFFFFE9A8),
    onSecondaryContainer = Color(0xFF2B2000),
    tertiary = Color(0xFF6B4FBB),
    background = Color(0xFFF3F7F4),
    surface = Color(0xFFF3F7F4),
    surfaceVariant = Color(0xFFE0E8E2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FBF9),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDF3EF),
    surfaceContainerHighest = Color(0xFFE5ECE7),
    outline = Color(0xFFBFCBC3),
    outlineVariant = Color(0xFFDCE4DE),
)

/** Dark scheme in any hue, tuned like the classic green one (tinted near-black surfaces). */
private fun darkScheme(h: Float) = darkColorScheme(
    primary = Color.hsl(h, 0.72f, 0.67f),
    onPrimary = Color.hsl(h, 0.9f, 0.12f),
    primaryContainer = Color.hsl(h, 0.42f, 0.21f),
    onPrimaryContainer = Color.hsl(h, 0.8f, 0.85f),
    secondary = Dark.secondary,
    onSecondary = Dark.onSecondary,
    secondaryContainer = Dark.secondaryContainer,
    onSecondaryContainer = Dark.onSecondaryContainer,
    tertiary = Dark.tertiary,
    background = Color.hsl(h, 0.18f, 0.067f),
    onBackground = Color.hsl(h, 0.12f, 0.90f),
    surface = Color.hsl(h, 0.18f, 0.067f),
    onSurface = Color.hsl(h, 0.12f, 0.90f),
    surfaceVariant = Color.hsl(h, 0.15f, 0.13f),
    onSurfaceVariant = Color.hsl(h, 0.11f, 0.69f),
    surfaceContainerLowest = Color.hsl(h, 0.2f, 0.05f),
    surfaceContainerLow = Color.hsl(h, 0.17f, 0.09f),
    surfaceContainer = Color.hsl(h, 0.17f, 0.11f),
    surfaceContainerHigh = Color.hsl(h, 0.16f, 0.14f),
    surfaceContainerHighest = Color.hsl(h, 0.16f, 0.17f),
    outline = Color.hsl(h, 0.12f, 0.26f),
    outlineVariant = Color.hsl(h, 0.13f, 0.19f),
    error = Dark.error,
)

private fun lightScheme(h: Float) = lightColorScheme(
    primary = Color.hsl(h, 0.72f, 0.30f),
    onPrimary = Color.White,
    primaryContainer = Color.hsl(h, 0.62f, 0.87f),
    onPrimaryContainer = Color.hsl(h, 0.9f, 0.08f),
    secondary = Light.secondary,
    secondaryContainer = Light.secondaryContainer,
    onSecondaryContainer = Light.onSecondaryContainer,
    tertiary = Light.tertiary,
    background = Color.hsl(h, 0.22f, 0.96f),
    surface = Color.hsl(h, 0.22f, 0.96f),
    surfaceVariant = Color.hsl(h, 0.17f, 0.90f),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.hsl(h, 0.3f, 0.98f),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.hsl(h, 0.2f, 0.94f),
    surfaceContainerHighest = Color.hsl(h, 0.16f, 0.91f),
    outline = Color.hsl(h, 0.14f, 0.77f),
    outlineVariant = Color.hsl(h, 0.16f, 0.88f),
)

/** Colour for a theme hue as shown on the swatches (null = classic green). */
fun accentSwatch(hue: Float?): Color = if (hue == null) Color(0xFF4CAF50) else Color.hsl(hue, 0.65f, 0.52f)

/** Preset hues offered in the settings; null is the classic green. */
val AccentPresets: List<Float?> = listOf(null, 172f, 212f, 240f, 275f, 330f, 2f, 24f, 45f)

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(letterSpacing = 0.8.sp),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)

@Composable
fun RemoteMCTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    accentHue: Float? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        accentHue != null -> if (darkTheme) darkScheme(accentHue) else lightScheme(accentHue)
        darkTheme -> Dark
        else -> Light
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Status colour readable as text on the current background. */
@Composable
fun statusTextColor(base: Color): Color {
    if (!isLightTheme()) return base
    return when (base) {
        McColors.Online -> McColors.OnlineText
        McColors.Warning -> McColors.WarningText
        McColors.Offline -> McColors.OfflineText
        else -> base
    }
}

@Composable
fun isLightTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() > 0.5f
