package com.aurorascan.data.repository

import android.graphics.Bitmap
import com.aurorascan.core.model.AssetKind
import com.aurorascan.core.model.SignatureAsset
import com.aurorascan.data.files.FileStorage
import com.aurorascan.data.local.AuroraDatabase
import com.aurorascan.data.local.entity.AssetEntity
import com.aurorascan.engine.image.BackgroundRemover
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignatureRepositoryImpl @Inject constructor(
    database: AuroraDatabase,
    private val fileStorage: FileStorage,
    private val backgroundRemover: BackgroundRemover,
) : SignatureRepository {

    private val assetDao = database.assetDao()

    override fun observe(kind: AssetKind): Flow<List<SignatureAsset>> =
        assetDao.observeByKind(kind.name).map { list ->
            list.map {
                SignatureAsset(
                    id = it.id,
                    kind = runCatching { AssetKind.valueOf(it.kind) }.getOrDefault(AssetKind.SIGNATURE),
                    path = it.path,
                    createdAtEpochMs = it.createdAt,
                )
            }
        }

    override suspend fun saveDrawn(bitmap: Bitmap, kind: AssetKind): SignatureAsset {
        val cropped = backgroundRemover.autoCrop(bitmap) ?: bitmap
        return persist(cropped, kind)
    }

    override suspend fun saveImported(bitmap: Bitmap, kind: AssetKind): SignatureAsset {
        val extracted = backgroundRemover.removeLightBackground(bitmap)
        return persist(extracted, kind)
    }

    private suspend fun persist(bitmap: Bitmap, kind: AssetKind): SignatureAsset {
        val id = UUID.randomUUID().toString()
        val path = fileStorage.writeAssetPng(id, bitmap)
        val now = System.currentTimeMillis()
        assetDao.upsert(AssetEntity(id = id, kind = kind.name, path = path, createdAt = now))
        return SignatureAsset(id = id, kind = kind, path = path, createdAtEpochMs = now)
    }

    override suspend fun delete(assetId: String) {
        val asset = assetDao.getById(assetId) ?: return
        fileStorage.deleteRelative(asset.path)
        assetDao.delete(assetId)
    }
}
