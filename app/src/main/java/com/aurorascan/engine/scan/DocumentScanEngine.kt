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
    ): IntentSender

    /** Parses the activity result returned by the launched scanner. */
    fun parseResult(resultCode: Int, data: Intent?): ScanResult
}

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
