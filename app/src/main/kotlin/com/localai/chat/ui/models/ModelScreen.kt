package com.localai.chat.ui.models

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localai.chat.data.download.DownloadState
import com.localai.chat.data.model.ModelInfo
import com.localai.chat.data.repository.ModelStatus
import com.localai.chat.util.MemoryUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModelViewModel = viewModel(factory = ModelViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error, duration = SnackbarDuration.Long)
            viewModel.dismissError()
        }
    }

    // File picker for a locally-stored model file.
    // On Android, ACTION_OPEN_DOCUMENT returns a content:// URI, not a filesystem path.
    // We pass the URI to the ViewModel which copies the file into app-private storage
    // and then loads it from there.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri: Uri ->
                viewModel.loadModelFromUri(context, uri)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Model Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Model selector
            ModelSelectorCard(
                models = uiState.availableModels,
                selectedModel = uiState.selectedModelInfo,
                onSelectModel = viewModel::selectModel
            )

            // Kaggle credentials — required to download Gemma directly
            KaggleCredentialsCard(
                username = uiState.kaggleUsername,
                apiKey = uiState.kaggleKey,
                onCredentialsChange = viewModel::updateKaggleCredentials
            )

            // Status card
            ModelStatusCard(
                modelInfo = uiState.selectedModelInfo,
                modelStatus = uiState.modelStatus,
                downloadState = uiState.downloadState,
                onDownload = viewModel::downloadModel,
                onCancelDownload = viewModel::cancelDownload,
                onLoad = viewModel::loadModel,
                onDelete = viewModel::deleteModel,
                onPickLocalFile = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    filePickerLauncher.launch(intent)
                }
            )

            // Info card
            ModelInfoCard(modelInfo = uiState.selectedModelInfo)
        }
    }
}

@Composable
private fun KaggleCredentialsCard(
    username: String,
    apiKey: String,
    onCredentialsChange: (String, String) -> Unit
) {
    var keyVisible by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Kaggle Credentials",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "Required to download Gemma. Get your credentials at kaggle.com/settings → API → Create New Token.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = username,
                onValueChange = { onCredentialsChange(it, apiKey) },
                label = { Text("Kaggle Username") },
                placeholder = { Text("your_kaggle_username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { onCredentialsChange(username, it) },
                label = { Text("Kaggle API Key") },
                placeholder = { Text("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (keyVisible) "Hide key" else "Show key"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectorCard(
    models: List<ModelInfo>,
    selectedModel: ModelInfo,
    onSelectModel: (ModelInfo) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Select Model",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedModel.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                    label = { Text("Model") }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(model.displayName, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = "~%.1f GB".format(model.sizeGb),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            },
                            onClick = {
                                onSelectModel(model)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelStatusCard(
    modelInfo: ModelInfo,
    modelStatus: ModelStatus,
    downloadState: DownloadState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onPickLocalFile: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Model?") },
            text = { Text("This will delete ${modelInfo.displayName} from your device. You will need to download it again to use it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (modelStatus) {
                        is ModelStatus.Loaded -> Icons.Default.CheckCircle
                        is ModelStatus.Installed -> Icons.Default.CheckCircle
                        is ModelStatus.Loading -> Icons.Default.HourglassTop
                        is ModelStatus.NotInstalled -> Icons.Default.CloudDownload
                        is ModelStatus.Error -> Icons.Default.Error
                    },
                    contentDescription = null,
                    tint = when (modelStatus) {
                        is ModelStatus.Loaded -> MaterialTheme.colorScheme.primary
                        is ModelStatus.Installed -> MaterialTheme.colorScheme.secondary
                        is ModelStatus.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (modelStatus) {
                        is ModelStatus.Loaded -> "Model loaded and ready"
                        is ModelStatus.Installed -> "Installed (${MemoryUtils.formatBytes(modelStatus.sizeBytes)})"
                        is ModelStatus.Loading -> "Loading into memory..."
                        is ModelStatus.NotInstalled -> "Not installed"
                        is ModelStatus.Error -> "Error: ${modelStatus.message}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            // Download progress
            AnimatedVisibility(
                visible = downloadState is DownloadState.Downloading,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                val dlState = downloadState as? DownloadState.Downloading
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    LinearProgressIndicator(
                        progress = { dlState?.progressFraction ?: 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (dlState != null && dlState.totalBytes > 0) {
                            "${MemoryUtils.formatBytes(dlState.bytesDownloaded)} / ${MemoryUtils.formatBytes(dlState.totalBytes)}"
                        } else {
                            "Downloading..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            when {
                downloadState is DownloadState.Downloading -> {
                    OutlinedButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel Download")
                    }
                }
                modelStatus is ModelStatus.NotInstalled -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download Model")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onPickLocalFile,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select from Storage")
                    }
                }
                modelStatus is ModelStatus.Installed -> {
                    Button(
                        onClick = onLoad,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Load Model")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Model")
                    }
                }
                modelStatus is ModelStatus.Loaded -> {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Model")
                    }
                }
                modelStatus is ModelStatus.Loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                modelStatus is ModelStatus.Error -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry Download")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelInfoCard(modelInfo: ModelInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Model Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow(label = "Name", value = modelInfo.displayName)
            InfoRow(label = "Size", value = "~%.1f GB".format(modelInfo.sizeGb))
            InfoRow(label = "File", value = modelInfo.fileName)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = modelInfo.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⚠️ Privacy: Once downloaded, the model runs entirely on-device. No internet connection needed for chat.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
