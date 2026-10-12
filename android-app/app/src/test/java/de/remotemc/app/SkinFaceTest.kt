package de.remotemc.app

import android.graphics.Bitmap
import android.graphics.Color
import coil.size.Size
import de.remotemc.app.ui.util.SkinFaceTransformation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SkinFaceTest {

    @Test
    fun cutsFaceAndHatFromSkin() = runBlocking {
        val skin = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        // Face red, hat layer: left half blue, right half transparent.
        for (x in 8 until 16) for (y in 8 until 16) skin.setPixel(x, y, Color.RED)
        for (x in 40 until 44) for (y in 8 until 16) skin.setPixel(x, y, Color.BLUE)

        val face = SkinFaceTransformation(16).transform(skin, Size.ORIGINAL)

        assertEquals(16, face.width)
        assertEquals(Color.BLUE, face.getPixel(2, 8))   // hat drawn over the face
        assertEquals(Color.RED, face.getPixel(12, 8))   // transparent hat shows the face
    }
}
