package com.aurorascan.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.aurorascan.core.model.DocumentWithPages
import com.aurorascan.data.files.FileStorage
import com.aurorascan.domain.usecase.AddPagesUseCase
import com.aurorascan.domain.usecase.ExportSearchablePdfUseCase
import com.aurorascan.domain.usecase.ObserveDocumentUseCase
import com.aurorascan.domain.usecase.RenameDocumentUseCase
import com.aurorascan.domain.usecase.SetFavoriteUseCase
import com.aurorascan.engine.scan.DocumentScanEngine
import com.aurorascan.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDocument: ObserveDocumentUseCase,
    private val exportSearchablePdf: ExportSearchablePdfUseCase,
    private val setFavorite: SetFavoriteUseCase,
    private val renameDocument: RenameDocumentUseCase,
    private val addPages: AddPagesUseCase,
    private val scanEngine: DocumentScanEngine,
    private val fileStorage: FileStorage,
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle[Routes.ARG_DOCUMENT_ID])

    val state: StateFlow<DocumentWithPages?> =
        observeDocument(documentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    val exportedFiles = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val errors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    fun resolve(relativePath: String): File = fileStorage.resolve(relativePath)

    fun toggleFavorite() {
        val current = state.value?.document ?: return
        viewModelScope.launch { setFavorite(current.id, !current.favorite) }
    }

    fun rename(title: String) {
        viewModelScope.launch {
            runCatching { renameDocument(documentId, title) }
                .onFailure { errors.tryEmit("Rename failed: ${it.message}") }
        }
    }

    suspend fun buildAddPagesIntentSender(activity: Activity): IntentSender =
        scanEngine.createScanIntentSender(activity, pageLimit = 50, allowGalleryImport = true)

    fun onPagesScanned(resultCode: Int, data: Intent?) {
        val result = scanEngine.parseResult(resultCode, data)
        if (result.isEmpty) return
        viewModelScope.launch {
            runCatching { addPages(documentId, result) }
                .onFailure { errors.tryEmit("Could not add pages: ${it.message}") }
        }
    }

    fun exportPdf() {
        if (_exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            runCatching { exportSearchablePdf(documentId) }
                .onSuccess { exportedFiles.tryEmit(it) }
                .onFailure { errors.tryEmit("Export failed: ${it.message}") }
            _exporting.value = false
        }
    }
}
