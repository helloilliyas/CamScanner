package com.aurorascan.engine.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Composites captured ID-card sides (front, optionally back) onto a single
 * A4-portrait page — the CamScanner-style "ID copy" layout. Each card is scaled
 * to fit its slot while preserving aspect ratio and centered, on a white page.
 */
class IdCardComposer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun compose(sources: List<Uri>): Bitmap = withContext(Dispatchers.Default) {
        val cards = sources.take(MAX_SIDES).mapNotNull { decode(it) }
        require(cards.isNotEmpty()) { "No ID images to compose" }

        val page = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page).apply { drawColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val slots = slotsFor(cards.size)
        cards.forEachIndexed { index, card ->
            drawCentered(canvas, paint, card, slots[index])
            card.recycle()
        }
        page
    }

    private fun slotsFor(count: Int): List<RectF> {
        val left = MARGIN
        val right = PAGE_WIDTH - MARGIN
        return if (count == 1) {
            // Single card: centered band in the upper third, like a copied card.
            listOf(RectF(left, MARGIN, right, PAGE_HEIGHT * 0.5f))
        } else {
            val gap = MARGIN
            val mid = PAGE_HEIGHT / 2f
            listOf(
                RectF(left, MARGIN, right, mid - gap / 2f),
                RectF(left, mid + gap / 2f, right, PAGE_HEIGHT - MARGIN),
            )
        }
    }

    private fun drawCentered(canvas: Canvas, paint: Paint, card: Bitmap, slot: RectF) {
        val scale = minOf(slot.width() / card.width, slot.height() / card.height)
        val drawW = card.width * scale
        val drawH = card.height * scale
        val cx = slot.centerX()
        val cy = slot.centerY()
        val dst = RectF(cx - drawW / 2f, cy - drawH / 2f, cx + drawW / 2f, cy + drawH / 2f)
        canvas.drawBitmap(card, Rect(0, 0, card.width, card.height), dst, paint)
    }

    private fun decode(uri: Uri): Bitmap? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

    private companion object {
        // A4 portrait at ~150 dpi.
        const val PAGE_WIDTH = 1240
        const val PAGE_HEIGHT = 1754
        const val MARGIN = 90f
        const val MAX_SIDES = 2
    }
}
