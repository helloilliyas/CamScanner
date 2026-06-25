package com.aurorascan.data.repository

import android.graphics.Bitmap
import com.aurorascan.core.model.AssetKind
import com.aurorascan.core.model.SignatureAsset
import kotlinx.coroutines.flow.Flow

/** Library of reusable signatures and stamps (stored as transparent PNGs). */
interface SignatureRepository {
    fun observe(kind: AssetKind): Flow<List<SignatureAsset>>

    /** Stores a drawn signature (already transparent); only auto-cropped. */
    suspend fun saveDrawn(bitmap: Bitmap, kind: AssetKind): SignatureAsset

    /** Stores an imported image with its light background removed. */
    suspend fun saveImported(bitmap: Bitmap, kind: AssetKind): SignatureAsset

    suspend fun delete(assetId: String)
}
