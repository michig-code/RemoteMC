package de.remotemc.app.ui.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import androidx.core.graphics.createBitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * Cuts the face (8×8 at 8,8) plus the hat layer (8×8 at 40,8) out of a Minecraft skin texture
 * and scales it up without smoothing, so the head looks exactly like in game.
 */
class SkinFaceTransformation(private val pixels: Int = 128) : Transformation {

    override val cacheKey = "skin-face-$pixels"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        // Skins are 64×64 (or legacy 64×32); scale coordinates for HD skins.
        val unit = input.width / 64f
        if (input.width < 64 || input.height < 32) return input
        val out = createBitmap(pixels, pixels)
        val canvas = Canvas(out)
        val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        val target = Rect(0, 0, pixels, pixels)
        canvas.drawBitmap(input, rect(8, 8, unit), target, paint)
        canvas.drawBitmap(input, rect(40, 8, unit), target, paint)
        return out
    }

    private fun rect(x: Int, y: Int, unit: Float) =
        Rect((x * unit).toInt(), (y * unit).toInt(), ((x + 8) * unit).toInt(), ((y + 8) * unit).toInt())

    override fun equals(other: Any?) = other is SkinFaceTransformation && other.pixels == pixels
    override fun hashCode() = pixels
}
