package com.aurorascan.data.repository

import com.aurorascan.core.model.Annotation
import com.aurorascan.data.local.AuroraDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Non-destructive page overlays (signatures, stamps, dates). */
interface AnnotationRepository {
    fun observeForPage(pageId: String): Flow<List<Annotation>>
    suspend fun getForPages(pageIds: List<String>): List<Annotation>
    suspend fun upsert(annotation: Annotation)
    suspend fun delete(annotationId: String)
}

@Singleton
class AnnotationRepositoryImpl @Inject constructor(
    database: AuroraDatabase,
) : AnnotationRepository {

    private val dao = database.annotationDao()

    override fun observeForPage(pageId: String): Flow<List<Annotation>> =
        dao.observeForPage(pageId).map { list -> list.map { it.toDomain() } }

    override suspend fun getForPages(pageIds: List<String>): List<Annotation> =
        dao.getForPages(pageIds).map { it.toDomain() }

    override suspend fun upsert(annotation: Annotation) =
        dao.upsert(annotation.toEntity(System.currentTimeMillis()))

    override suspend fun delete(annotationId: String) = dao.delete(annotationId)
}
