package com.aurorascan.ui.signatures

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurorascan.core.model.AssetKind
import com.aurorascan.core.model.SignatureAsset
import com.aurorascan.data.repository.SignatureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ManageSignaturesViewModel @Inject constructor(
    private val repository: SignatureRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val signatures: StateFlow<List<SignatureAsset>> =
        repository.observe(AssetKind.SIGNATURE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stamps: StateFlow<List<SignatureAsset>> =
        repository.observe(AssetKind.STAMP)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    fun saveDrawn(bitmap: Bitmap) {
        viewModelScope.launch {
            runCatching { repository.saveDrawn(bitmap, AssetKind.SIGNATURE) }
                .onFailure { messages.tryEmit("Could not save signature: ${it.message}") }
        }
    }

    fun importAsset(uri: Uri, kind: AssetKind) {
        viewModelScope.launch {
            runCatching {
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                } ?: error("Unable to read image")
                repository.saveImported(bitmap, kind)
            }.onFailure { messages.tryEmit("Could not import: ${it.message}") }
        }
    }

    fun delete(assetId: String) {
        viewModelScope.launch { repository.delete(assetId) }
    }

    fun resolve(relativePath: String): java.io.File = java.io.File(context.filesDir, relativePath)
}
