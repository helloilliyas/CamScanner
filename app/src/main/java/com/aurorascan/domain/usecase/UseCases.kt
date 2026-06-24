package com.aurorascan.domain.usecase

import com.aurorascan.core.model.Document
import com.aurorascan.core.model.DocumentWithPages
import com.aurorascan.data.repository.DocumentRepository
import com.aurorascan.engine.scan.ScanResult
import com.aurorascan.work.OcrScheduler
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Thin domain use cases (blueprint section 6). Feature ViewModels depend on
 * these rather than on the repository directly, keeping orchestration testable.
 */

class ObserveDocumentsUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    operator fun invoke(): Flow<List<Document>> = repository.observeDocuments()
}

class SearchDocumentsUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    operator fun invoke(query: String): Flow<List<Document>> = repository.search(query)
}

class ObserveDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    operator fun invoke(documentId: String): Flow<DocumentWithPages?> =
        repository.observeDocument(documentId)
}

/**
 * Persists a finished scan and immediately schedules background OCR
 * (blueprint 13.1 → 17.4 pipeline). Returns the new document id.
 */
class ImportScanUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val ocrScheduler: OcrScheduler,
) {
    suspend operator fun invoke(
        scan: ScanResult,
        title: String? = null,
        languageHints: Set<String> = setOf("en"),
    ): String {
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: defaultTitle()
        val documentId = repository.importScan(scan, resolvedTitle)
        ocrScheduler.schedule(documentId, languageHints)
        return documentId
    }

    private fun defaultTitle(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "Scan ${formatter.format(Date())}"
    }
}

/** Appends scanned pages to an existing document and re-runs OCR for it. */
class AddPagesUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val ocrScheduler: OcrScheduler,
) {
    suspend operator fun invoke(
        documentId: String,
        scan: ScanResult,
        languageHints: Set<String> = setOf("en"),
    ) {
        repository.addPages(documentId, scan)
        ocrScheduler.schedule(documentId, languageHints)
    }
}

/** Composites an ID card (front/back) onto one page and schedules OCR. */
class ImportIdCardUseCase @Inject constructor(
    private val repository: DocumentRepository,
    private val ocrScheduler: OcrScheduler,
) {
    suspend operator fun invoke(
        scan: ScanResult,
        title: String? = null,
        languageHints: Set<String> = setOf("en"),
    ): String {
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: defaultTitle()
        val documentId = repository.importIdCard(scan, resolvedTitle)
        ocrScheduler.schedule(documentId, languageHints)
        return documentId
    }

    private fun defaultTitle(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "ID Card ${formatter.format(Date())}"
    }
}

class RenameDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(documentId: String, title: String) {
        val trimmed = title.trim()
        require(trimmed.isNotEmpty()) { "Title cannot be empty" }
        repository.rename(documentId, trimmed)
    }
}

class ExportSearchablePdfUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(documentId: String, includeOcrLayer: Boolean = true): File =
        repository.exportSearchablePdf(documentId, includeOcrLayer)
}

class SetFavoriteUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(documentId: String, favorite: Boolean) =
        repository.setFavorite(documentId, favorite)
}

class DeleteDocumentUseCase @Inject constructor(
    private val repository: DocumentRepository,
) {
    suspend operator fun invoke(documentId: String) = repository.softDelete(documentId)
}
