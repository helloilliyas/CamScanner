package com.aurorascan.ui.sign

import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurorascan.core.model.Annotation
import com.aurorascan.core.model.AnnotationType
import com.aurorascan.core.model.AssetKind
import com.aurorascan.core.model.DocumentWithPages
import com.aurorascan.core.model.SignatureAsset
import com.aurorascan.data.files.FileStorage
import com.aurorascan.data.repository.AnnotationRepository
import com.aurorascan.data.repository.SignatureRepository
import com.aurorascan.domain.usecase.ObserveDocumentUseCase
import com.aurorascan.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SignEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDocument: ObserveDocumentUseCase,
    signatureRepository: SignatureRepository,
    private val annotationRepository: AnnotationRepository,
    private val fileStorage: FileStorage,
) : ViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle[Routes.ARG_DOCUMENT_ID])

    val document: StateFlow<DocumentWithPages?> =
        observeDocument(documentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val signatures: StateFlow<List<SignatureAsset>> =
        signatureRepository.observe(AssetKind.SIGNATURE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stamps: StateFlow<List<SignatureAsset>> =
        signatureRepository.observe(AssetKind.STAMP)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _annotations = MutableStateFlow<List<Annotation>>(emptyList())
    val annotations: StateFlow<List<Annotation>> = _annotations.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _selectedId = MutableStateFlow<String?>(null)
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    private val removedIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val dwp = document.filterNotNull().first()
            val pageIds = dwp.pages.map { it.id }
            _annotations.value = annotationRepository.getForPages(pageIds)
        }
    }

    fun setPage(index: Int) {
        _currentPage.value = index
        _selectedId.value = null
    }

    fun select(id: String?) {
        _selectedId.value = id
    }

    private fun currentPageId(): String? =
        document.value?.pages?.getOrNull(_currentPage.value)?.id

    fun addAsset(asset: SignatureAsset) {
        val pageId = currentPageId() ?: return
        viewModelScope.launch {
            val aspect = withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(fileStorage.resolve(asset.path).absolutePath, bounds)
                if (bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else 2f
            }
            val annotation = Annotation(
                id = UUID.randomUUID().toString(),
                pageId = pageId,
                type = if (asset.kind == AssetKind.STAMP) AnnotationType.STAMP else AnnotationType.SIGNATURE,
                assetPath = asset.path,
                text = null,
                centerX = 0.3f,
                centerY = 0.6f,
                widthFraction = 0.35f,
                aspectRatio = aspect.coerceAtLeast(0.1f),
                rotationDegrees = 0f,
                zIndex = _annotations.value.size,
            )
            _annotations.value = _annotations.value + annotation
            _selectedId.value = annotation.id
        }
    }

    fun addDate() {
        val pageId = currentPageId() ?: return
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val annotation = Annotation(
            id = UUID.randomUUID().toString(),
            pageId = pageId,
            type = AnnotationType.DATE,
            assetPath = null,
            text = today,
            centerX = 0.3f,
            centerY = 0.74f,
            widthFraction = 0.3f,
            aspectRatio = 3.5f,
            rotationDegrees = 0f,
            zIndex = _annotations.value.size,
        )
        _annotations.value = _annotations.value + annotation
        _selectedId.value = annotation.id
    }

    fun update(annotation: Annotation) {
        _annotations.value = _annotations.value.map { if (it.id == annotation.id) annotation else it }
    }

    /**
     * Applies incremental gesture deltas to the CURRENT annotation (not a stale
     * captured copy), so dragging/pinching accumulate correctly across frames.
     * Pan is expressed as a fraction of the page (dx/pageWidth, dy/pageHeight).
     */
    fun applyTransform(
        id: String,
        panFractionX: Float,
        panFractionY: Float,
        zoom: Float,
        rotation: Float,
    ) {
        _annotations.value = _annotations.value.map {
            if (it.id != id) {
                it
            } else {
                it.copy(
                    centerX = (it.centerX + panFractionX).coerceIn(0f, 1f),
                    centerY = (it.centerY + panFractionY).coerceIn(0f, 1f),
                    widthFraction = (it.widthFraction * zoom).coerceIn(0.05f, 2f),
                    rotationDegrees = it.rotationDegrees + rotation,
                )
            }
        }
        _selectedId.value = id
    }

    fun updateDateText(id: String, text: String) {
        _annotations.value = _annotations.value.map { if (it.id == id) it.copy(text = text) else it }
    }

    fun removeSelected() {
        val id = _selectedId.value ?: return
        removedIds.add(id)
        _annotations.value = _annotations.value.filterNot { it.id == id }
        _selectedId.value = null
    }

    fun save() {
        val toSave = _annotations.value
        val toRemove = removedIds.toList()
        removedIds.clear()
        // NonCancellable so navigating back (which clears this ViewModel) cannot
        // cancel the write mid-flight.
        viewModelScope.launch {
            withContext(NonCancellable) {
                toSave.forEach { annotationRepository.upsert(it) }
                toRemove.forEach { annotationRepository.delete(it) }
            }
        }
    }

    fun resolve(path: String): File = fileStorage.resolve(path)
}
