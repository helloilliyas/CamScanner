package com.aurorascan.engine.scan

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri

/**
 * Capture engine abstraction (blueprint section 8). The first release is backed
 * by the ML Kit Document Scanner; a CameraX implementation can replace it later
 * without touching feature code.
 */
interface DocumentScanEngine {
    /** Builds the platform scan intent to launch via the Activity Result API. */
    suspend fun createScanIntentSender(
        activity: Activity,
        pageLimit: Int?,
        allowGalleryImport: Boolean,
        mode: ScanMode = ScanMode.FULL,
    ): IntentSender

    /** Parses the activity result returned by the launched scanner. */
    fun parseResult(resultCode: Int, data: Intent?): ScanResult
}

/**
 * Capture/editing mode.
 *
 * - [FULL] offers the enhancement filters (auto, grayscale, B&W) — good for
 *   documents.
 * - [BASE] only detects and crops the page, keeping the original colors — used
 *   for ID cards so the card is captured true-to-original on a white page.
 */
enum class ScanMode { FULL, BASE }

data class ScannedPageSource(val imageUri: Uri)

data class ScanResult(
    val pages: List<ScannedPageSource>,
    val generatedPdfUri: String?,
) {
    val isEmpty: Boolean get() = pages.isEmpty()

    companion object {
        val EMPTY = ScanResult(emptyList(), null)
    }
}
