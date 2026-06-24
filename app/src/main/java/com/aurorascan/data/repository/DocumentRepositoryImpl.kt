package com.aurorascan.data.repository

import androidx.room.withTransaction
import com.aurorascan.core.model.CropGeometry
import com.aurorascan.core.model.Document
import com.aurorascan.core.model.DocumentWithPages
import com.aurorascan.core.model.EnhancementRecipe
import com.aurorascan.core.model.OcrStatus
import com.aurorascan.core.model.SyncState
import com.aurorascan.data.files.FileStorage
import com.aurorascan.data.local.AuroraDatabase
import com.aurorascan.data.local.entity.DocumentEntity
import com.aurorascan.data.local.entity.OcrPageEntity
import com.aurorascan.data.local.entity.PageEntity
import com.aurorascan.engine.ocr.OcrBlock
import com.aurorascan.engine.ocr.OcrEngine
import com.aurorascan.engine.pdf.PdfBuildSpec
import com.aurorascan.engine.pdf.PdfEngine
import com.aurorascan.engine.pdf.PdfPageSpec
import com.aurorascan.engine.scan.ScanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val database: AuroraDatabase,
    private val fileStorage: FileStorage,
    private val ocrEngine: OcrEngine,
    private val pdfEngine: PdfEngine,
) : DocumentRepository {

    private val documentDao = database.documentDao()
    private val pageDao = database.pageDao()
    private val ocrDao = database.ocrDao()

    override fun observeDocuments(): Flow<List<Document>> =
        documentDao.observeDocuments().map { list -> list.map { it.toDomain() } }

    override fun search(query: String): Flow<List<Document>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return observeDocuments()
        return documentDao.search(trimmed).map { list -> list.map { it.toDomain() } }
    }

    override fun observeDocument(documentId: String): Flow<DocumentWithPages?> =
        combine(
            documentDao.observeById(documentId),
            pageDao.observeForDocument(documentId),
        ) { doc, pages ->
            doc?.let {
                DocumentWithPages(
                    document = it.toDomain(),
                    pages = pages.map { page -> page.toDomain() },
                )
            }
        }

    override suspend fun importScan(scan: ScanResult, title: String): String {
        require(!scan.isEmpty) { "Cannot import an empty scan" }
        val now = System.currentTimeMillis()
        val documentId = UUID.randomUUID().toString()

        val cropJson = AuroraJson.encodeToString(CropGeometry.FULL)
        val recipeJson = AuroraJson.encodeToString(EnhancementRecipe.DEFAULT)

        val pages = scan.pages.mapIndexed { index, source ->
            val pageId = UUID.randomUUID().toString()
            val imported = fileStorage.importOriginal(source.imageUri, documentId, pageId)
            PageEntity(
                id = pageId,
                documentId = documentId,
                position = index,
                originalPath = imported.relativePath,
                previewPath = null,
                renderedPath = null,
                width = imported.width,
                height = imported.height,
                rotationDegrees = 0,
                cropJson = cropJson,
                enhancementRecipeJson = recipeJson,
                ocrStatus = OcrStatus.PENDING,
                checksumSha256 = imported.checksumSha256,
                createdAt = now,
                updatedAt = now,
            )
        }

        val document = DocumentEntity(
            id = documentId,
            title = title,
            folderId = null,
            createdAt = now,
            updatedAt = now,
            pageCount = pages.size,
            favorite = false,
            deletedAt = null,
            localRevision = 1,
            remoteRevision = null,
            syncState = SyncState.LOCAL_ONLY,
            coverPageId = pages.firstOrNull()?.id,
            searchableText = "",
        )

        database.withTransaction {
            documentDao.upsert(document)
            pageDao.insertAll(pages)
        }
        return documentId
    }

    override suspend fun runOcrForDocument(documentId: String, languageHints: Set<String>) {
        val pending = pageDao.getForDocumentWithStatus(
            documentId,
            listOf(OcrStatus.PENDING, OcrStatus.NONE, OcrStatus.FAILED),
        )
        for (page in pending) {
            val now = System.currentTimeMillis()
            pageDao.updateOcrStatus(page.id, OcrStatus.RUNNING, now)
            try {
                val file = fileStorage.resolve(page.renderedPath ?: page.originalPath)
                val result = ocrEngine.recognize(file, page.rotationDegrees, languageHints)
                ocrDao.upsert(
                    OcrPageEntity(
                        pageId = page.id,
                        engineId = result.engineId,
                        engineVersion = result.engineVersion,
                        languageCodes = result.languages.joinToString(","),
                        rawText = result.fullText,
                        correctedText = null,
                        blocksJson = AuroraJson.encodeToString(result.blocks),
                        averageConfidence = result.averageConfidence,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
                pageDao.updateOcrStatus(page.id, OcrStatus.DONE, System.currentTimeMillis())
            } catch (e: Exception) {
                pageDao.updateOcrStatus(page.id, OcrStatus.FAILED, System.currentTimeMillis())
            }
        }
        refreshSearchableText(documentId)
    }

    private suspend fun refreshSearchableText(documentId: String) {
        val pages = pageDao.getForDocument(documentId)
        val ocr = ocrDao.getForPages(pages.map { it.id }).associateBy { it.pageId }
        val combinedText = pages
            .sortedBy { it.position }
            .mapNotNull { ocr[it.id] }
            .joinToString("\n") { it.correctedText ?: it.rawText }
        documentDao.updateSearchableText(documentId, combinedText, System.currentTimeMillis())
    }

    override suspend fun setFavorite(documentId: String, favorite: Boolean) =
        documentDao.setFavorite(documentId, favorite, System.currentTimeMillis())

    override suspend fun rename(documentId: String, title: String) =
        documentDao.rename(documentId, title, System.currentTimeMillis())

    override suspend fun softDelete(documentId: String) =
        documentDao.softDelete(documentId, System.currentTimeMillis())

    override suspend fun exportSearchablePdf(documentId: String, includeOcrLayer: Boolean): File {
        val document = documentDao.getById(documentId)
            ?: error("Document not found: $documentId")
        val pages = pageDao.getForDocument(documentId)
        require(pages.isNotEmpty()) { "Document has no pages to export" }
        val ocr = ocrDao.getForPages(pages.map { it.id }).associateBy { it.pageId }

        val pageSpecs = pages.map { page ->
            val blocks: List<OcrBlock> = ocr[page.id]?.let {
                runCatching { AuroraJson.decodeFromString<List<OcrBlock>>(it.blocksJson) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
            PdfPageSpec(
                imageFile = fileStorage.resolve(page.renderedPath ?: page.originalPath),
                rotationDegrees = page.rotationDegrees,
                ocrBlocks = blocks,
            )
        }

        val output = File(fileStorage.exportsDir(documentId), "${sanitize(document.title)}-searchable.pdf")
        return pdfEngine.createSearchablePdf(
            PdfBuildSpec(
                documentTitle = document.title,
                pages = pageSpecs,
                outputFile = output,
                includeOcrTextLayer = includeOcrLayer,
            ),
        )
    }

    private fun sanitize(name: String): String =
        name.trim().ifEmpty { "document" }.replace(Regex("[^A-Za-z0-9-_ ]"), "_").take(64)
}
