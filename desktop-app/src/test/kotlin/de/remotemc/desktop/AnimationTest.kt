package de.remotemc.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import kotlin.test.Test

/** Screens with animations on render frame after frame without getting stuck. */
class AnimationTest {
    @Test
    fun animatedCardsSettle() {
        val scene = ImageComposeScene(900, 600, Density(1f)) {
            RemoteMCTheme(DesktopSettings(animations = true)) {
                Column {
                    (0 until 6).forEach { i -> McCard(Modifier.staggeredAppear(i)) { Bar(i / 6f, McColors.Online) } }
                    StatusDot(Conn.LIVE)
                }
            }
        }
        var t = 0L
        repeat(120) { t += 16_000_000L; scene.render(t) } // two seconds of frames
        scene.close()
    }
}
