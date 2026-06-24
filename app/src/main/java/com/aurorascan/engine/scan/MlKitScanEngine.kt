package com.aurorascan.engine.scan

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * First-release capture engine backed by the ML Kit Document Scanner
 * (blueprint 13.1). Returns JPEG page URIs plus the SDK-generated PDF which we
 * keep only as a fallback until our own searchable PDF is produced.
 */
class MlKitScanEngine @Inject constructor() : DocumentScanEngine {

    override suspend fun createScanIntentSender(
        activity: Activity,
        pageLimit: Int?,
        allowGalleryImport: Boolean,
        mode: ScanMode,
    ): IntentSender = withContext(Dispatchers.Main) {
        val scannerMode = when (mode) {
            ScanMode.FULL -> GmsDocumentScannerOptions.SCANNER_MODE_FULL
            ScanMode.BASE -> GmsDocumentScannerOptions.SCANNER_MODE_BASE
        }
        val builder = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(allowGalleryImport)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
            )
            .setScannerMode(scannerMode)
        if (pageLimit != null) builder.setPageLimit(pageLimit)

        val scanner = GmsDocumentScanning.getClient(builder.build())
        scanner.getStartScanIntent(activity).await()
    }

    override fun parseResult(resultCode: Int, data: Intent?): ScanResult {
        if (resultCode != Activity.RESULT_OK || data == null) return ScanResult.EMPTY
        val result: GmsDocumentScanningResult =
            GmsDocumentScanningResult.fromActivityResultIntent(data) ?: return ScanResult.EMPTY

        val pages = result.pages.orEmpty().map { ScannedPageSource(it.imageUri) }
        val pdfUri = result.pdf?.uri?.toString()
        return ScanResult(pages = pages, generatedPdfUri = pdfUri)
    }
}
