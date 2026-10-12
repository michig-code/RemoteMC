package de.mcremote.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Fixed accent colors used for status indicators in both themes. */
object McColors {
    val Online = Color(0xFF4ADE80)
    val Warning = Color(0xFFFBBF24)
    val Offline = Color(0xFFF87171)
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
fun McRemoteTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
