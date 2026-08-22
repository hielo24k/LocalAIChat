package com.localai.chat.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localai.chat.data.repository.ModelStatus
import com.localai.chat.ui.components.ChatBubble
import com.localai.chat.ui.components.MessageInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateToModels: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-scroll to latest message
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    // Show snackbar on error
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Long
            )
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TIO",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = when (val status = uiState.modelStatus) {
                                is ModelStatus.Loaded -> "Model ready"
                                is ModelStatus.Loading -> "Loading model..."
                                is ModelStatus.Installed -> "Model installed, not loaded"
                                is ModelStatus.NotInstalled -> "No model installed"
                                is ModelStatus.Error -> "Error: ${status.message}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (uiState.modelStatus) {
                                is ModelStatus.Loaded -> MaterialTheme.colorScheme.primary
                                is ModelStatus.Error -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearConversation() }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear conversation"
                        )
                    }
                    IconButton(onClick = onNavigateToModels) {
                        Icon(
                            imageVector = Icons.Default.DownloadForOffline,
                            contentDescription = "Models"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MessageInput(
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputChange,
                    onSend = viewModel::sendMessage,
                    onStop = viewModel::stopGeneration,
                    isGenerating = uiState.isGenerating,
                    enabled = uiState.modelStatus is ModelStatus.Loaded
                )
            }
        }
    ) { innerPadding ->
        if (uiState.messages.isEmpty()) {
            EmptyState(
                modelLoaded = uiState.modelStatus is ModelStatus.Loaded,
                onNavigateToModels = onNavigateToModels,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.messages,
                    key = { it.id }
                ) { message ->
                    ChatBubble(message = message)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    modelLoaded: Boolean,
    onNavigateToModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "\uD83E\uDD16",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "LocalAI Chat",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (modelLoaded) {
                "Your AI is ready. All conversations stay on your device."
            } else {
                "No model loaded yet. Download a model to start chatting — 100% on-device, no internet needed."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!modelLoaded) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onNavigateToModels) {
                Text("Download a Model")
            }
        }
    }
}
