package com.aurorascan.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aurorascan.core.model.OcrStatus
import com.aurorascan.core.model.SyncState

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folderId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val favorite: Boolean,
    val deletedAt: Long?,
    val localRevision: Long,
    val remoteRevision: Long?,
    val syncState: SyncState,
    val coverPageId: String?,
    val searchableText: String,
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
data class PageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val position: Int,
    val originalPath: String,
    val previewPath: String?,
    val renderedPath: String?,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val cropJson: String,
    val enhancementRecipeJson: String,
    val ocrStatus: OcrStatus,
    val checksumSha256: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "ocr_pages",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class OcrPageEntity(
    @PrimaryKey val pageId: String,
    val engineId: String,
    val engineVersion: String?,
    val languageCodes: String,
    val rawText: String,
    val correctedText: String?,
    val blocksJson: String,
    val averageConfidence: Float?,
    val createdAt: Long,
)
