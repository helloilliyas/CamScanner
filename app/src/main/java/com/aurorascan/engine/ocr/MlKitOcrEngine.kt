package com.aurorascan.engine.ocr

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import com.aurorascan.core.model.NormalizedRect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * First OCR engine (blueprint 17.1): ML Kit Text Recognition v2 with the bundled
 * Latin model. Pixel bounding boxes are converted to normalized coordinates so
 * downstream PDF/search code is resolution independent.
 */
class MlKitOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : OcrEngine {

    override val engineId: String = "mlkit-text-recognition"
    override val engineVersion: String? = "16.0.1"

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(
        imageFile: File,
        rotationDegrees: Int,
        languageHints: Set<String>,
    ): OcrResult = withContext(Dispatchers.Default) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.absolutePath, bounds)
        // For 90/270 degree rotations ML Kit reports coordinates on the rotated
        // canvas, so width and height are swapped.
        val swap = rotationDegrees == 90 || rotationDegrees == 270
        val canvasWidth = (if (swap) bounds.outHeight else bounds.outWidth).coerceAtLeast(1)
        val canvasHeight = (if (swap) bounds.outWidth else bounds.outHeight).coerceAtLeast(1)

        val image = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
        val text: Text = recognizer.process(image).await()

        val confidences = mutableListOf<Float>()
        val blocks = text.textBlocks.map { block ->
            OcrBlock(
                text = block.text,
                bounds = block.boundingBox.normalize(canvasWidth, canvasHeight),
                lines = block.lines.map { line ->
                    OcrLine(
                        text = line.text,
                        bounds = line.boundingBox.normalize(canvasWidth, canvasHeight),
                        words = line.elements.map { element ->
                            val confidence = element.confidence.takeUnless { it.isNaN() }
                            confidence?.let(confidences::add)
                            OcrWord(
                                text = element.text,
                                bounds = element.boundingBox.normalize(canvasWidth, canvasHeight),
                                confidence = confidence,
                                rotationDegrees = element.angle.takeUnless { it.isNaN() },
                            )
                        },
                    )
                },
            )
        }

        OcrResult(
            fullText = text.text,
            blocks = blocks,
            languages = languageHints,
            averageConfidence = confidences.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            engineId = engineId,
            engineVersion = engineVersion,
        )
    }

    private fun Rect?.normalize(width: Int, height: Int): NormalizedRect {
        if (this == null) return NormalizedRect(0f, 0f, 0f, 0f)
        return NormalizedRect(
            left = (left.toFloat() / width).coerceIn(0f, 1f),
            top = (top.toFloat() / height).coerceIn(0f, 1f),
            right = (right.toFloat() / width).coerceIn(0f, 1f),
            bottom = (bottom.toFloat() / height).coerceIn(0f, 1f),
        )
    }
}
