package com.aurorascan.engine.ocr

import com.aurorascan.core.model.NormalizedRect
import kotlinx.serialization.Serializable
import java.io.File

/**
 * OCR abstraction (blueprint section 8 & 17). First release uses ML Kit Text
 * Recognition; PaddleOCR/ONNX can be added behind the same interface later.
 */
interface OcrEngine {
    val engineId: String
    val engineVersion: String?

    suspend fun recognize(
        imageFile: File,
        rotationDegrees: Int,
        languageHints: Set<String>,
    ): OcrResult
}

@Serializable
data class OcrResult(
    val fullText: String,
    val blocks: List<OcrBlock>,
    val languages: Set<String>,
    val averageConfidence: Float?,
    val engineId: String,
    val engineVersion: String?,
)

@Serializable
data class OcrBlock(
    val text: String,
    val bounds: NormalizedRect,
    val lines: List<OcrLine>,
)

@Serializable
data class OcrLine(
    val text: String,
    val bounds: NormalizedRect,
    val words: List<OcrWord>,
)

@Serializable
data class OcrWord(
    val text: String,
    val bounds: NormalizedRect,
    val confidence: Float? = null,
    val rotationDegrees: Float? = null,
)
