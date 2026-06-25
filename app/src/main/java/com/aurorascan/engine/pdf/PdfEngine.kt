package com.aurorascan.engine.pdf

import com.aurorascan.engine.ocr.OcrBlock
import java.io.File

/**
 * Searchable PDF generation (blueprint section 8 & 19): a visible scanned-image
 * layer plus an invisible OCR text layer positioned at matching coordinates.
 */
interface PdfEngine {
    suspend fun createSearchablePdf(spec: PdfBuildSpec): File
}

data class PdfBuildSpec(
    val documentTitle: String,
    val pages: List<PdfPageSpec>,
    val outputFile: File,
    val includeOcrTextLayer: Boolean = true,
)

data class PdfPageSpec(
    val imageFile: File,
    val rotationDegrees: Int = 0,
    /** Normalized OCR geometry used to place the invisible text layer. */
    val ocrBlocks: List<OcrBlock> = emptyList(),
    /** Overlays (signature/stamp/date) flattened into the page at export. */
    val annotations: List<PdfAnnotation> = emptyList(),
)

/**
 * A flattened overlay. If [assetFile] is set it is drawn as an image
 * (transparent PNG); otherwise [text] is drawn. Geometry is normalized to the
 * page (0..1).
 */
data class PdfAnnotation(
    val assetFile: File?,
    val text: String?,
    val centerX: Float,
    val centerY: Float,
    val widthFraction: Float,
    val aspectRatio: Float,
    val rotationDegrees: Float,
)
