package de.remotemc.desktop

import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowMotionTest {
    // A 1920×1080 screen with a 40 px taskbar at the bottom.
    private val work = Rectangle(0, 0, 1920, 1040)

    @Test
    fun snapsAtTheEdgesLikeWindows() {
        assertEquals(Snap.MAXIMIZE, snapFor(Point(900, 0), work))
        assertEquals(Snap.LEFT, snapFor(Point(0, 500), work))
        assertEquals(Snap.RIGHT, snapFor(Point(1919, 500), work))
        assertNull(snapFor(Point(900, 500), work))
        assertEquals(Rectangle(0, 0, 960, 1040), snapBounds(Snap.LEFT, work))
        assertEquals(Rectangle(960, 0, 960, 1040), snapBounds(Snap.RIGHT, work))
        assertEquals(work, snapBounds(Snap.MAXIMIZE, work)) // the taskbar stays visible
    }

    @Test
    fun glidesWithEaseOut() {
        val from = Rectangle(100, 100, 1000, 700)
        assertEquals(from, lerpBounds(from, work, 0f))
        assertEquals(work, lerpBounds(from, work, 1f))
        val half = lerpBounds(from, work, 0.5f)
        // Ease out: more than half of the way is done at half the time.
        assertTrue(half.width > (from.width + work.width) / 2, "eased: ${half.width}")
    }
}
