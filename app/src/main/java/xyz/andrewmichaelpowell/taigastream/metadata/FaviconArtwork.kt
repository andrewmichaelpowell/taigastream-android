//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import xyz.andrewmichaelpowell.taigastream.R
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.scale

object FaviconArtwork {

    const val TRANSPARENT_ICON_INSET = 0.80f

    private const val ARTWORK_SIDE = 600
    private const val TRANSPARENCY_SAMPLE_SIDE = 256

    @Volatile
    private var cachedAppIcon: Bitmap? = null

    fun render(source: Bitmap): Bitmap? {
        if (source.width <= 0 || source.height <= 0) return null
        val side = ARTWORK_SIDE.toFloat()
        val fillScale = max(side / source.width, side / source.height)
        val fitScale = min(side / source.width, side / source.height)
        val scale = if (hasTransparency(source)) fitScale * TRANSPARENT_ICON_INSET else fillScale
        val width = source.width * scale
        val height = source.height * scale

        val result = createBitmap(ARTWORK_SIDE, ARTWORK_SIDE)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(
            source,
            null,
            RectF((side - width) / 2, (side - height) / 2, (side + width) / 2, (side + height) / 2),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return result
    }

    fun hasTransparency(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val software = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false
        } else {
            bitmap
        }
        val scale = min(1f, TRANSPARENCY_SAMPLE_SIDE.toFloat() / max(software.width, software.height))
        val sample = if (scale < 1f) {
            software.scale(
                max(1, (software.width * scale).toInt()),
                max(1, (software.height * scale).toInt()),
            )
        } else {
            software
        }
        val pixels = IntArray(sample.width * sample.height)
        sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
        return pixels.any { (it ushr 24) < 255 }
    }

    fun appIcon(context: Context): Bitmap? {
        cachedAppIcon?.let { return it }
        val icon = runCatching {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)!!
            val size = 512
            createBitmap(size, size).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
            }
        }.getOrNull() ?: runCatching {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }.getOrNull()
        cachedAppIcon = icon
        return icon
    }
}
