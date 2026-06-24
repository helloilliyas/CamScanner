package com.aurorascan.engine.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Searchable PDF builder using Android's [PdfDocument] (blueprint 19).
 *
 * The OCR text is drawn with a fully transparent paint: the glyphs become real,
 * selectable/searchable text in the PDF content stream but are invisible on top
 * of the page image. [PdfDocument]'s canvas uses a top-left origin and performs
 * the PDF coordinate transform internally, so normalized OCR coordinates map
 * directly without a manual vertical flip (blueprint 19.2).
 */
class AndroidPdfEngine @Inject constructor() : PdfEngine {

    override suspend fun createSearchablePdf(spec: PdfBuildSpec): File =
        withContext(Dispatchers.Default) {
            val pdf = PdfDocument()
            try {
                spec.pages.forEachIndexed { index, page ->
                    renderPage(pdf, index + 1, page, spec.includeOcrTextLayer)
                }
                writeAtomically(pdf, spec.outputFile)
            } finally {
                pdf.close()
            }
            spec.outputFile
        }

    private fun renderPage(
        pdf: PdfDocument,
        pageNumber: Int,
        page: PdfPageSpec,
        includeOcr: Boolean,
    ) {
        val raw = BitmapFactory.decodeFile(page.imageFile.absolutePath)
            ?: error("Unable to decode page image: ${page.imageFile}")
        val bitmap = applyRotation(raw, page.rotationDegrees)

        val pageWidth = bitmap.width
        val pageHeight = bitmap.height
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val pdfPage = pdf.startPage(pageInfo)
        val canvas = pdfPage.canvas

        canvas.drawBitmap(bitmap, 0f, 0f, null)

        if (includeOcr) {
            val textPaint = Paint().apply {
                color = Color.TRANSPARENT
                isAntiAlias = true
            }
            page.ocrBlocks.forEach { block ->
                block.lines.forEach { line ->
                    val rect = line.bounds
                    val text = line.text
                    if (text.isBlank() || rect.width <= 0f || rect.height <= 0f) return@forEach
                    val pixelHeight = rect.height * pageHeight
                    val pixelWidth = rect.width * pageWidth
                    textPaint.textSize = pixelHeight.coerceAtLeast(1f)
                    // Stretch the glyphs to fill the detected line box so selection
                    // hit-boxes line up with the visible characters.
                    val measured = textPaint.measureText(text).coerceAtLeast(1f)
                    textPaint.textScaleX = (pixelWidth / measured).coerceIn(0.1f, 10f)
                    // drawText y is the text baseline; approximate from the box bottom.
                    val baseline = rect.bottom * pageHeight - pixelHeight * 0.18f
                    canvas.drawText(text, rect.left * pageWidth, baseline, textPaint)
                }
            }
        }

        pdf.finishPage(pdfPage)
        if (bitmap !== raw) bitmap.recycle()
        raw.recycle()
    }

    private fun applyRotation(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun writeAtomically(pdf: PdfDocument, output: File) {
        output.parentFile?.mkdirs()
        val tmp = File(output.parentFile, output.name + ".tmp")
        tmp.outputStream().use { out ->
            pdf.writeTo(out)
            out.flush()
        }
        check(tmp.length() > 0) { "Generated PDF is empty" }
        if (output.exists()) output.delete()
        check(tmp.renameTo(output)) { "Atomic rename failed for $output" }
    }
}
