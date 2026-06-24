package com.aurorascan.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Documents") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            runCatching {
                                val sender = viewModel.buildIdScanIntentSender(context.findActivity())
                                idScanLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            }.onFailure {
                                snackbarHostState.showSnackbar("Scanner unavailable: ${it.message}")
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                ) {
                    Icon(Icons.Outlined.CreditCard, contentDescription = "Scan ID card")
                }
                ExtendedFloatingActionButton(
                    text = { Text("Scan") },
                    icon = { Icon(Icons.Outlined.DocumentScanner, contentDescription = null) },
                    onClick = {
                        scope.launch {
                            runCatching {
                                val sender = viewModel.buildScanIntentSender(context.findActivity())
                                scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            }.onFailure {
                                snackbarHostState.showSnackbar("Scanner unavailable: ${it.message}")
                            }
                        }
                    },
                )
            }
        },
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
