package com.aurorascan.ui.detail

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PostAdd
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.aurorascan.core.model.OcrStatus
import com.aurorascan.core.model.Page
import com.aurorascan.ui.util.findActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onSign: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf(false) }

    val addPagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onPagesScanned(result.resultCode, result.data)
    }

    LaunchedEffect(Unit) {
        viewModel.exportedFiles.collect { file ->
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, "Share PDF"))
        }
    }
    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbarHostState.showSnackbar(it) }
    }

    val document = state?.document
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.title ?: "Document") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    document?.let { doc ->
                        IconButton(onClick = { onSign(doc.id) }) {
                            Icon(Icons.Outlined.Draw, contentDescription = "Sign & stamp")
                        }
                    }
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "Rename")
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val sender = viewModel.buildAddPagesIntentSender(context.findActivity())
                                    addPagesLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                }.onFailure {
                                    snackbarHostState.showSnackbar("Scanner unavailable: ${it.message}")
                                }
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.PostAdd, contentDescription = "Add pages")
                    }
                    IconButton(onClick = viewModel::toggleFavorite) {
                        val favorite = document?.favorite == true
                        Icon(
                            imageVector = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Toggle favorite",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(if (exporting) "Exporting…" else "Export PDF") },
                icon = {
                    if (exporting) {
                        CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null)
                    }
                },
                onClick = viewModel::exportPdf,
            )
        },
    ) { padding ->
        val pages = state?.pages.orEmpty()
        if (pages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No pages yet")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(pages, key = { it.id }) { page ->
                    PageItem(
                        page = page,
                        imageModel = viewModel.resolve(page.renderedPath ?: page.originalPath),
                    )
                }
            }
        }
    }

    if (renaming && document != null) {
        RenameDialog(
            initial = document.title,
            onDismiss = { renaming = false },
            onConfirm = { newTitle ->
                renaming = false
                viewModel.rename(newTitle)
            },
        )
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename document") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Title") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun PageItem(page: Page, imageModel: Any?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = imageModel,
            contentDescription = "Page ${page.position + 1}",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
        Text(
            text = "Page ${page.position + 1} · ${ocrLabel(page.ocrStatus)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun ocrLabel(status: OcrStatus): String = when (status) {
    OcrStatus.NONE -> "OCR not started"
    OcrStatus.PENDING -> "OCR queued"
    OcrStatus.RUNNING -> "Recognizing text…"
    OcrStatus.DONE -> "Text recognized"
    OcrStatus.FAILED -> "OCR failed"
}
