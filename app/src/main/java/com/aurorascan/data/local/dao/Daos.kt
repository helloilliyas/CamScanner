package com.aurorascan.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.aurorascan.core.model.OcrStatus
import com.aurorascan.data.local.entity.AnnotationEntity
import com.aurorascan.data.local.entity.AssetEntity
import com.aurorascan.data.local.entity.DocumentEntity
import com.aurorascan.data.local.entity.OcrPageEntity
import com.aurorascan.data.local.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query(
        """
        SELECT * FROM documents
        WHERE deletedAt IS NULL
          AND (title LIKE '%' || :query || '%' OR searchableText LIKE '%' || :query || '%')
        ORDER BY
          CASE WHEN title LIKE '%' || :query || '%' THEN 0 ELSE 1 END,
          updatedAt DESC
        """,
    )
    fun search(query: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeById(id: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: String): DocumentEntity?

    @Upsert
    suspend fun upsert(document: DocumentEntity)

    @Query("UPDATE documents SET favorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, updatedAt: Long)

    @Query("UPDATE documents SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAt: Long)

    @Query("UPDATE documents SET pageCount = :count, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePageCount(id: String, count: Int, updatedAt: Long)

    @Query("UPDATE documents SET searchableText = :text, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSearchableText(id: String, text: String, updatedAt: Long)

    @Query("UPDATE documents SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)
}

@Dao
interface PageDao {

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position ASC")
    fun observeForDocument(documentId: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position ASC")
    suspend fun getForDocument(documentId: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE documentId = :documentId AND ocrStatus IN (:statuses) ORDER BY position ASC")
    suspend fun getForDocumentWithStatus(documentId: String, statuses: List<OcrStatus>): List<PageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<PageEntity>)

    @Query("UPDATE pages SET ocrStatus = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOcrStatus(id: String, status: OcrStatus, updatedAt: Long)
}

@Dao
interface OcrDao {

    @Upsert
    suspend fun upsert(ocr: OcrPageEntity)

    @Query("SELECT * FROM ocr_pages WHERE pageId = :pageId")
    suspend fun getForPage(pageId: String): OcrPageEntity?

    @Query("SELECT * FROM ocr_pages WHERE pageId IN (:pageIds)")
    suspend fun getForPages(pageIds: List<String>): List<OcrPageEntity>
}

@Dao
interface AssetDao {

    @Query("SELECT * FROM assets WHERE kind = :kind ORDER BY createdAt DESC")
    fun observeByKind(kind: String): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :id")
    suspend fun getById(id: String): AssetEntity?

    @Upsert
    suspend fun upsert(asset: AssetEntity)

    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AnnotationDao {

    @Query("SELECT * FROM annotations WHERE pageId = :pageId ORDER BY zIndex ASC")
    fun observeForPage(pageId: String): Flow<List<AnnotationEntity>>

    @Query("SELECT * FROM annotations WHERE pageId IN (:pageIds) ORDER BY zIndex ASC")
    suspend fun getForPages(pageIds: List<String>): List<AnnotationEntity>

    @Upsert
    suspend fun upsert(annotation: AnnotationEntity)

    @Query("DELETE FROM annotations WHERE id = :id")
    suspend fun delete(id: String)
}
