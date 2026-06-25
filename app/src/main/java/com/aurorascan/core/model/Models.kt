package com.aurorascan.core.model

import kotlinx.serialization.Serializable

/**
 * Domain models for AuroraScan.
 *
 * Geometry is always stored in normalized coordinates (0.0..1.0) so it stays
 * valid when the same page is rendered at different resolutions (preview, OCR,
 * export). See blueprint sections 11, 14.3 and 16.5.
 */

@Serializable
data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

@Serializable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

@Serializable
data class CropGeometry(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint,
) {
    companion object {
        /** Full-frame crop used when no detection/manual crop has been applied. */
        val FULL = CropGeometry(
            topLeft = NormalizedPoint(0f, 0f),
            topRight = NormalizedPoint(1f, 0f),
            bottomRight = NormalizedPoint(1f, 1f),
            bottomLeft = NormalizedPoint(0f, 1f),
        )
    }
}

enum class EnhancementPreset {
    ORIGINAL, AUTO, ENHANCED_COLOR, GRAYSCALE, BLACK_AND_WHITE, RECEIPT, PHOTO,
}

@Serializable
data class EnhancementRecipe(
    val preset: String = EnhancementPreset.AUTO.name,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val sharpness: Float = 0f,
) {
    companion object {
        val DEFAULT = EnhancementRecipe()
    }
}

enum class OcrStatus { NONE, PENDING, RUNNING, DONE, FAILED }

enum class SyncState { LOCAL_ONLY, PENDING_UPLOAD, SYNCED }

/** A page as exposed to the UI / domain layer (decoupled from Room entities). */
data class Page(
    val id: String,
    val documentId: String,
    val position: Int,
    val originalPath: String,
    val previewPath: String?,
    val renderedPath: String?,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val crop: CropGeometry,
    val enhancement: EnhancementRecipe,
    val ocrStatus: OcrStatus,
)

/** A document as exposed to the UI / domain layer. */
data class Document(
    val id: String,
    val title: String,
    val folderId: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val pageCount: Int,
    val favorite: Boolean,
    val coverPageId: String?,
    val syncState: SyncState,
)

/** A document together with its ordered pages. */
data class DocumentWithPages(
    val document: Document,
    val pages: List<Page>,
)

/** Reusable signature or stamp stored as a transparent PNG (background removed). */
enum class AssetKind { SIGNATURE, STAMP }

data class SignatureAsset(
    val id: String,
    val kind: AssetKind,
    val path: String,
    val createdAtEpochMs: Long,
)

/** Non-destructive overlay placed on a page (blueprint 21.1). */
enum class AnnotationType { SIGNATURE, STAMP, DATE }

data class Annotation(
    val id: String,
    val pageId: String,
    val type: AnnotationType,
    /** Relative path to the transparent PNG for SIGNATURE/STAMP; null for DATE. */
    val assetPath: String?,
    /** Text content for DATE; null otherwise. */
    val text: String?,
    /** Normalized center within the page (0..1). */
    val centerX: Float,
    val centerY: Float,
    /** Width as a fraction of the page width (0..1); height derived from aspect. */
    val widthFraction: Float,
    val aspectRatio: Float,
    val rotationDegrees: Float,
    val zIndex: Int,
)
