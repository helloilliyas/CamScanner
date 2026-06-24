package com.aurorascan.data.repository

import com.aurorascan.core.model.CropGeometry
import com.aurorascan.core.model.Document
import com.aurorascan.core.model.EnhancementRecipe
import com.aurorascan.core.model.Page
import com.aurorascan.data.local.entity.DocumentEntity
import com.aurorascan.data.local.entity.PageEntity
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal val AuroraJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun DocumentEntity.toDomain(): Document = Document(
    id = id,
    title = title,
    folderId = folderId,
    createdAtEpochMs = createdAt,
    updatedAtEpochMs = updatedAt,
    pageCount = pageCount,
    favorite = favorite,
    coverPageId = coverPageId,
    syncState = syncState,
)

internal fun PageEntity.toDomain(): Page = Page(
    id = id,
    documentId = documentId,
    position = position,
    originalPath = originalPath,
    previewPath = previewPath,
    renderedPath = renderedPath,
    width = width,
    height = height,
    rotationDegrees = rotationDegrees,
    crop = runCatching { AuroraJson.decodeFromString<CropGeometry>(cropJson) }
        .getOrDefault(CropGeometry.FULL),
    enhancement = runCatching { AuroraJson.decodeFromString<EnhancementRecipe>(enhancementRecipeJson) }
        .getOrDefault(EnhancementRecipe.DEFAULT),
    ocrStatus = ocrStatus,
)
