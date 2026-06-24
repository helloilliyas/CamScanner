package com.aurorascan.data.repository

import com.aurorascan.core.model.Document
import com.aurorascan.core.model.DocumentWithPages
import com.aurorascan.engine.scan.ScanResult
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Single source of truth for documents/pages/OCR. Repositories depend on
 * engine interfaces, not concrete implementations (blueprint section 7).
 */
interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    fun search(query: String): Flow<List<Document>>
    fun observeDocument(documentId: String): Flow<DocumentWithPages?>

    /**
     * Persists scanner output: copies originals into internal storage and writes
     * the document + page rows in one transaction. Returns the new document id.
     */
    suspend fun importScan(scan: ScanResult, title: String): String

    /** Runs OCR for every page that still needs it and refreshes the search text. */
    suspend fun runOcrForDocument(documentId: String, languageHints: Set<String>)

    suspend fun setFavorite(documentId: String, favorite: Boolean)
    suspend fun rename(documentId: String, title: String)
    suspend fun softDelete(documentId: String)

    /** Builds a searchable PDF and returns the exported file. */
    suspend fun exportSearchablePdf(documentId: String, includeOcrLayer: Boolean = true): File
}
