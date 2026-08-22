package com.localai.chat.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory())
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.editSystemPrompt.collectAsStateWithLifecycle()
    val temperature by viewModel.editTemperature.collectAsStateWithLifecycle()
    val maxTokens by viewModel.editMaxTokens.collectAsStateWithLifecycle()
    val topP by viewModel.editTopP.collectAsStateWithLifecycle()
    val useGpu by viewModel.editUseGpu.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            snackbarHostState.showSnackbar("Settings saved")
            viewModel.dismissSaved()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::resetToDefaults) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset to defaults"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::saveSettings,
                icon = { Icon(Icons.Default.Save, contentDescription = null) },
                text = { Text("Save") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // System Prompt section
            SectionHeader(title = "System Prompt")
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = viewModel::onSystemPromptChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("System Prompt") },
                minLines = 4,
                maxLines = 8,
                placeholder = { Text("Enter instructions for the AI assistant...") }
            )

            HorizontalDivider()

            // Generation parameters section
            SectionHeader(title = "Generation Parameters")

            // Temperature
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Temperature",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Controls randomness. Lower = more deterministic.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = "%.2f".format(temperature),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Slider(
                    value = temperature,
                    onValueChange = viewModel::onTemperatureChange,
                    valueRange = 0.1f..2.0f,
                    steps = 18
                )
            }

            // Max Tokens
            var maxTokensText by remember(maxTokens) { mutableStateOf(maxTokens.toString()) }
            Column {
                Text(
                    text = "Max Tokens",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Maximum number of tokens to generate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = maxTokensText,
                    onValueChange = { text ->
                        maxTokensText = text
                        text.toIntOrNull()?.let { v ->
                            if (v in 64..4096) viewModel.onMaxTokensChange(v)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Tokens (64–4096)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Top P
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Top P",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Nucleus sampling threshold.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = "%.2f".format(topP),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Slider(
                    value = topP,
                    onValueChange = viewModel::onTopPChange,
                    valueRange = 0.1f..1.0f,
                    steps = 17
                )
            }

            HorizontalDivider()

            // Hardware section
            SectionHeader(title = "Hardware")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GPU Acceleration",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Use GPU delegate for faster inference. Requires model restart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = useGpu,
                    onCheckedChange = viewModel::onUseGpuChange
                )
            }

            HorizontalDivider()

            // Privacy note
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("\uD83D\uDD12")
                    Text(
                        text = "All inference runs locally on your device. No prompts or responses are sent to the internet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Bottom padding to avoid FAB overlap
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}
