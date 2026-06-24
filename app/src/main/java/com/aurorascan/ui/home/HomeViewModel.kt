package com.aurorascan.ui.home

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurorascan.core.model.Document
import com.aurorascan.domain.usecase.DeleteDocumentUseCase
import com.aurorascan.domain.usecase.ImportScanUseCase
import com.aurorascan.domain.usecase.SearchDocumentsUseCase
import com.aurorascan.engine.scan.DocumentScanEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchDocuments: SearchDocumentsUseCase,
    private val importScan: ImportScanUseCase,
    private val deleteDocument: DeleteDocumentUseCase,
    private val scanEngine: DocumentScanEngine,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: MutableSharedFlow<String> get() = _messages

    @OptIn(ExperimentalCoroutinesApi::class)
    val documents: StateFlow<List<Document>> =
        _query
            .flatMapLatest { q -> searchDocuments(q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    suspend fun buildScanIntentSender(activity: Activity): IntentSender =
        scanEngine.createScanIntentSender(activity, pageLimit = 50, allowGalleryImport = true)

    fun onScanActivityResult(resultCode: Int, data: Intent?) {
        val result = scanEngine.parseResult(resultCode, data)
        if (result.isEmpty) return
        viewModelScope.launch {
            runCatching { importScan(result) }
                .onSuccess { _messages.tryEmit("Saved ${result.pages.size}-page scan. Running OCR…") }
                .onFailure { _messages.tryEmit("Could not save scan: ${it.message}") }
        }
    }

    fun onDelete(documentId: String) {
        viewModelScope.launch { deleteDocument(documentId) }
    }
}
