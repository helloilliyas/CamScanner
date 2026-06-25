package com.aurorascan.engine.image

import android.graphics.Bitmap
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * Extracts the ink/marks of a signature or stamp by keying out a light
 * (paper/white) background to transparency, then tight-cropping to the
 * remaining content. The result is a transparent PNG-ready bitmap that overlays
 * cleanly on documents (blueprint 21 — annotations as overlays).
 *
 * A drawn signature is already transparent, so [autoCrop] alone is used there.
 */
class BackgroundRemover @Inject constructor() {

    /**
     * @param luminanceThreshold pixels brighter than this (0..255) become fully
     *   transparent; a soft band just below it fades for clean edges.
     */
    fun removeLightBackground(source: Bitmap, luminanceThreshold: Int = 205): Bitmap {
        val scaled = downscaleIfNeeded(source, MAX_EDGE)
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        val softFloor = (luminanceThreshold - SOFT_BAND).coerceAtLeast(0)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            val alpha = when {
                luminance >= luminanceThreshold -> 0
                luminance <= softFloor -> 255
                else -> {
                    val t = (luminanceThreshold - luminance).toFloat() / SOFT_BAND
                    (t * 255f).toInt().coerceIn(0, 255)
                }
            }
            pixels[i] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        }

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        if (scaled !== source) scaled.recycle()
        return autoCrop(out) ?: out
    }

    /** Crops to the bounding box of non-transparent pixels (with small padding). */
    fun autoCrop(source: Bitmap, padding: Int = 12): Bitmap? {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width; var minY = height; var maxX = -1; var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((pixels[row + x] ushr 24) != 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return null // fully transparent

        minX = max(0, minX - padding)
        minY = max(0, minY - padding)
        maxX = min(width - 1, maxX + padding)
        maxY = min(height - 1, maxY + padding)
        val cropped = Bitmap.createBitmap(source, minX, minY, maxX - minX + 1, maxY - minY + 1)
        if (cropped !== source) source.recycle()
        return cropped
    }

    private fun downscaleIfNeeded(source: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxEdge) return source
        val scale = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private companion object {
        const val MAX_EDGE = 1600
        const val SOFT_BAND = 30
    }
}
