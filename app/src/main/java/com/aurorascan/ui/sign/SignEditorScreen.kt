package com.aurorascan.ui.sign

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aurorascan.core.model.Annotation
import com.aurorascan.core.model.AnnotationType
import com.aurorascan.core.model.Page
import com.aurorascan.core.model.SignatureAsset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignEditorScreen(
    onBack: () -> Unit,
    viewModel: SignEditorViewModel = hiltViewModel(),
) {
    val document by viewModel.document.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val signatures by viewModel.signatures.collectAsStateWithLifecycle()
    val stamps by viewModel.stamps.collectAsStateWithLifecycle()
    val pageIndex by viewModel.currentPage.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()

    var showLibrary by remember { mutableStateOf(false) }
    var editingDate by remember { mutableStateOf<Annotation?>(null) }

    val pages = document?.pages.orEmpty()
    val page = pages.getOrNull(pageIndex)
    val selected = annotations.firstOrNull { it.id == selectedId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign & Stamp") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selected?.type == AnnotationType.DATE) {
                        IconButton(onClick = { editingDate = selected }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit date")
                        }
                    }
                    if (selected != null) {
                        IconButton(onClick = viewModel::removeSelected) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete overlay")
                        }
                    }
                    IconButton(onClick = { viewModel.save(); onBack() }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save")
                    }
                },
            )
        },
        bottomBar = {
            SignToolbar(
                onSignStamp = { showLibrary = true },
                onDate = viewModel::addDate,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (page != null) {
                    PageSigningCanvas(
                        page = page,
                        imageModel = viewModel.resolve(page.renderedPath ?: page.originalPath),
                        annotations = annotations.filter { it.pageId == page.id },
                        selectedId = selectedId,
                        resolve = viewModel::resolve,
                        onSelect = viewModel::select,
                        onTransform = viewModel::applyTransform,
                    )
                }
            }
            if (pages.size > 1) {
                PageNavRow(
                    index = pageIndex,
                    total = pages.size,
                    onPrev = { viewModel.setPage((pageIndex - 1).coerceAtLeast(0)) },
                    onNext = { viewModel.setPage((pageIndex + 1).coerceAtMost(pages.size - 1)) },
                )
            }
        }
    }

    if (showLibrary) {
        LibraryPickerSheet(
            signatures = signatures,
            stamps = stamps,
            resolve = viewModel::resolve,
            onPick = { asset -> showLibrary = false; viewModel.addAsset(asset) },
            onDismiss = { showLibrary = false },
        )
    }

    editingDate?.let { dateAnnotation ->
        DateEditDialog(
            initial = dateAnnotation.text.orEmpty(),
            onConfirm = { text ->
                viewModel.updateDateText(dateAnnotation.id, text)
                editingDate = null
            },
            onDismiss = { editingDate = null },
        )
    }
}

@Composable
private fun PageSigningCanvas(
    page: Page,
    imageModel: Any?,
    annotations: List<Annotation>,
    selectedId: String?,
    resolve: (String) -> Any,
    onSelect: (String?) -> Unit,
    onTransform: (id: String, panFractionX: Float, panFractionY: Float, zoom: Float, rotation: Float) -> Unit,
) {
    val density = LocalDensity.current
    val aspect = if (page.height > 0) page.width.toFloat() / page.height else 0.707f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onSelect(null) } },
        contentAlignment = Alignment.Center,
    ) {
        val availW = constraints.maxWidth.toFloat()
        val availH = constraints.maxHeight.toFloat()
        val dispW: Float
        val dispH: Float
        if (availW / availH > aspect) {
            dispH = availH; dispW = availH * aspect
        } else {
            dispW = availW; dispH = availW / aspect
        }

        Box(
            modifier = Modifier.size(
                with(density) { dispW.toDp() },
                with(density) { dispH.toDp() },
            ),
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = "Page ${page.position + 1}",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().background(Color.White),
            )
            annotations.forEach { ann ->
                OverlayItem(
                    annotation = ann,
                    boxWidthPx = dispW,
                    boxHeightPx = dispH,
                    selected = ann.id == selectedId,
                    resolve = resolve,
                    onSelect = { onSelect(ann.id) },
                    onTransform = onTransform,
                )
            }
        }
    }
}

@Composable
private fun OverlayItem(
    annotation: Annotation,
    boxWidthPx: Float,
    boxHeightPx: Float,
    selected: Boolean,
    resolve: (String) -> Any,
    onSelect: () -> Unit,
    onTransform: (id: String, panFractionX: Float, panFractionY: Float, zoom: Float, rotation: Float) -> Unit,
) {
    val density = LocalDensity.current
    val wPx = annotation.widthFraction * boxWidthPx
    val hPx = if (annotation.aspectRatio > 0f) wPx / annotation.aspectRatio else wPx
    val leftPx = annotation.centerX * boxWidthPx - wPx / 2f
    val topPx = annotation.centerY * boxHeightPx - hPx / 2f

    var modifier = Modifier
        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
        .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
        .graphicsLayer { rotationZ = annotation.rotationDegrees }
        .pointerInput(annotation.id) { detectTapGestures { onSelect() } }
        .pointerInput(annotation.id) {
            detectTransformGestures { _, pan, zoom, rotation ->
                // Pass per-frame deltas; the ViewModel accumulates onto current state.
                onTransform(annotation.id, pan.x / boxWidthPx, pan.y / boxHeightPx, zoom, rotation)
            }
        }

    if (selected) {
        modifier = modifier.border(2.dp, MaterialTheme.colorScheme.primary)
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (annotation.type == AnnotationType.DATE) {
            Text(
                text = annotation.text.orEmpty(),
                color = Color.Black,
                maxLines = 1,
                fontSize = with(density) { (hPx * 0.7f).toSp() },
            )
        } else if (annotation.assetPath != null) {
            AsyncImage(
                model = resolve(annotation.assetPath),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PageNavRow(index: Int, total: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, enabled = index > 0) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous page")
        }
        Text("Page ${index + 1} of $total", style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onNext, enabled = index < total - 1) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next page")
        }
    }
}

@Composable
private fun SignToolbar(onSignStamp: () -> Unit, onDate: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            ToolbarAction(label = "Sign & Stamp", onClick = onSignStamp) {
                Icon(Icons.Outlined.Draw, contentDescription = null)
            }
            ToolbarAction(label = "Date", onClick = onDate) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ToolbarAction(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 6.dp),
    ) {
        icon()
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryPickerSheet(
    signatures: List<SignatureAsset>,
    stamps: List<SignatureAsset>,
    resolve: (String) -> Any,
    onPick: (SignatureAsset) -> Unit,
    onDismiss: () -> Unit,
) {
    val all = signatures + stamps
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Choose a signature or stamp",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        if (all.isEmpty()) {
            Text(
                text = "No saved signatures yet. Add one from Manage Signatures.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(all, key = { it.id }) { asset ->
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .clickable { onPick(asset) },
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = resolve(asset.path),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateEditDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit date") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Date") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
