package com.aurorascan.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurorascan.ui.util.findActivity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDocument: (String) -> Unit,
    onOpenSignatures: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val scanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onScanActivityResult(result.resultCode, result.data)
    }

    val idScanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onIdScanActivityResult(result.resultCode, result.data)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    fun startScanner(
        buildSender: suspend () -> android.content.IntentSender,
        launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>,
    ) {
        scope.launch {
            runCatching {
                launcher.launch(IntentSenderRequest.Builder(buildSender()).build())
            }.onFailure {
                snackbarHostState.showSnackbar("Scanner unavailable: ${it.message}")
            }
        }
    }

    val modes = listOf(
        ScanModeUi(
            label = "Scan",
            icon = Icons.Outlined.DocumentScanner,
            onClick = {
                startScanner({ viewModel.buildScanIntentSender(context.findActivity()) }, scanLauncher)
            },
        ),
        ScanModeUi(
            label = "ID Card",
            icon = Icons.Outlined.CreditCard,
            onClick = {
                startScanner({ viewModel.buildIdScanIntentSender(context.findActivity()) }, idScanLauncher)
            },
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documents") },
                actions = {
                    androidx.compose.material3.IconButton(onClick = onOpenSignatures) {
                        Icon(Icons.Outlined.Draw, contentDescription = "Manage signatures")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { ScanModeBar(modes) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search titles and scanned text") },
                singleLine = true,
            )

            if (documents.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(documents, key = { it.id }) { document ->
                        DocumentCard(
                            document = document,
                            onClick = { onOpenDocument(document.id) },
                            onDelete = { viewModel.onDelete(document.id) },
                        )
                    }
                }
            }
        }
    }
}

private data class ScanModeUi(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** CamScanner-style bottom row of capture modes; easy to extend with more. */
@Composable
private fun ScanModeBar(modes: List<ScanModeUi>) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            modes.forEach { ModeItem(it) }
        }
    }
}

@Composable
private fun ModeItem(mode: ScanModeUi) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = mode.onClick)
            .semantics { contentDescription = mode.label }
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = mode.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(mode.label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Your scans will appear here.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Scan a page or import an existing file.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
