package com.localai.chat.ui.models

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localai.chat.data.download.DownloadState
import com.localai.chat.data.model.ModelCatalog
import com.localai.chat.data.model.ModelInfo
import com.localai.chat.data.repository.ModelRepository
import com.localai.chat.data.repository.ModelStatus
import com.localai.chat.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ModelUiState(
    val modelStatus: ModelStatus = ModelStatus.NotInstalled,
    val downloadState: DownloadState = DownloadState.Idle,
    val isLoadingModel: Boolean = false,
    val selectedModelInfo: ModelInfo = ModelCatalog.entries.first(),
    val availableModels: List<ModelInfo> = ModelCatalog.entries,
    val kaggleUsername: String = "",
    val kaggleKey: String = "",
    val errorMessage: String? = null
)

class ModelViewModel(
    private val modelRepository: ModelRepository,
    private val appPreferences: com.localai.chat.data.preferences.AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelUiState())
    val uiState: StateFlow<ModelUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            modelRepository.modelStatus.collect { status ->
                _uiState.update { it.copy(modelStatus = status, isLoadingModel = status is ModelStatus.Loading) }
            }
        }
        viewModelScope.launch {
            appPreferences.settings.collect { settings ->
                val model = ModelCatalog.findById(settings.selectedModelId)
                    ?: ModelCatalog.entries.first()
                _uiState.update {
                    it.copy(
                        selectedModelInfo = model,
                        kaggleUsername = settings.kaggleUsername,
                        kaggleKey = settings.kaggleKey
                    )
                }
                modelRepository.refreshStatus(settings.selectedModelId)
            }
        }
    }

    fun selectModel(modelInfo: ModelInfo) {
        _uiState.update { it.copy(selectedModelInfo = modelInfo) }
        viewModelScope.launch {
            appPreferences.updateSelectedModel(modelInfo.id)
            modelRepository.refreshStatus(modelInfo.id)
        }
    }

    fun downloadModel() {
        val modelInfo = _uiState.value.selectedModelInfo
        if (modelInfo.downloadUrl.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "No download URL configured for ${modelInfo.displayName}. See README for manual download instructions.")
            }
            return
        }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            modelRepository.downloadModel(
                modelInfo,
                kaggleUsername = _uiState.value.kaggleUsername,
                kaggleKey = _uiState.value.kaggleKey
            )
                .collect { state ->
                    _uiState.update { it.copy(downloadState = state) }
                    when (state) {
                        is DownloadState.Completed -> {
                            modelRepository.refreshStatus(modelInfo.id)
                        }
                        is DownloadState.Failed -> {
                            _uiState.update { it.copy(errorMessage = state.error) }
                        }
                        else -> { /* no-op */ }
                    }
                }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _uiState.update { it.copy(downloadState = DownloadState.Idle) }
    }

    fun loadModel() {
        val modelInfo = _uiState.value.selectedModelInfo
        viewModelScope.launch {
            val result = modelRepository.loadModel(modelInfo.id)
            result.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Failed to load model")
                }
            }
        }
    }

    fun deleteModel() {
        val modelInfo = _uiState.value.selectedModelInfo
        val deleted = modelRepository.deleteModel(modelInfo.id)
        if (!deleted) {
            _uiState.update { it.copy(errorMessage = "Failed to delete model") }
        } else {
            _uiState.update { it.copy(downloadState = DownloadState.Idle) }
        }
    }

    /**
     * Loads a model from a content:// URI obtained via the file picker (SAF).
     * Copies the file into app-private storage first, then loads it.
     * This is required because MediaPipe cannot read content:// URIs directly.
     */
    fun loadModelFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModel = true, errorMessage = null) }
            val result = withContext(Dispatchers.IO) {
                try {
                    val modelsDir = File(context.filesDir, "models").also { it.mkdirs() }
                    val fileName = resolveFileName(context, uri)
                    val destFile = File(modelsDir, fileName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@withContext Result.failure(Exception("Cannot open file from URI"))
                    Result.success(destFile.absolutePath)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            result
                .onSuccess { path -> modelRepository.loadModelFromPath(path) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingModel = false,
                            errorMessage = error.message ?: "Failed to import model file"
                        )
                    }
                }
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        // Try to get the display name from the content provider
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name?.takeIf { it.isNotBlank() } ?: "custom_model_${System.currentTimeMillis()}.bin"
    }

    fun updateKaggleCredentials(username: String, key: String) {
        _uiState.update { it.copy(kaggleUsername = username, kaggleKey = key) }
        viewModelScope.launch { appPreferences.updateKaggleCredentials(username, key) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ModelViewModel(
                modelRepository = ServiceLocator.modelRepository,
                appPreferences = ServiceLocator.appPreferences
            ) as T
        }
    }
}
