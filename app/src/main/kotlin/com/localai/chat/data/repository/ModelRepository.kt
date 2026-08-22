package com.localai.chat.data.repository

import android.content.Context
import com.localai.chat.data.download.DownloadState
import com.localai.chat.data.download.ModelDownloader
import com.localai.chat.data.engine.InferenceEngine
import com.localai.chat.data.engine.ModelLoadException
import com.localai.chat.data.model.ModelCatalog
import com.localai.chat.data.model.ModelInfo
import com.localai.chat.util.FileUtils
import com.localai.chat.util.MemoryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed class ModelStatus {
    object NotInstalled : ModelStatus()
    data class Installed(val file: File, val sizeBytes: Long) : ModelStatus()
    object Loading : ModelStatus()
    object Loaded : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}

class ModelRepository(
    private val context: Context,
    private val engine: InferenceEngine,
    private val downloader: ModelDownloader
) {
    private val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.NotInstalled)
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private var currentModelId: String? = null

    fun refreshStatus(modelId: String) {
        val info = ModelCatalog.findById(modelId) ?: return
        val file = info.localFile(context.filesDir)
        _modelStatus.value = if (FileUtils.isValidFile(file)) {
            if (engine.isModelLoaded()) ModelStatus.Loaded
            else ModelStatus.Installed(file, file.length())
        } else {
            ModelStatus.NotInstalled
        }
    }

    fun downloadModel(modelInfo: ModelInfo, kaggleUsername: String = "", kaggleKey: String = ""): Flow<DownloadState> {
        return downloader.download(
            url = modelInfo.downloadUrl,
            fileName = modelInfo.fileName,
            expectedSizeBytes = modelInfo.sizeBytes,
            kaggleUsername = kaggleUsername,
            kaggleKey = kaggleKey
        )
    }

    suspend fun loadModel(modelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val info = ModelCatalog.findById(modelId)
            ?: return@withContext Result.failure(IllegalArgumentException("Unknown model: $modelId"))

        val file = info.localFile(context.filesDir)
        if (!FileUtils.isValidFile(file)) {
            return@withContext Result.failure(IllegalStateException("Model file not found: ${file.absolutePath}"))
        }

        // Check RAM
        // Rough estimate: model size in memory is ~2x the file size for INT4 quantized
        val requiredRam = file.length() * 2
        if (!MemoryUtils.hasEnoughRam(context, requiredRam)) {
            val available = MemoryUtils.formatBytes(MemoryUtils.availableRamBytes(context))
            val needed = MemoryUtils.formatBytes(requiredRam)
            return@withContext Result.failure(
                OutOfMemoryError("Not enough RAM to load model. Need ~$needed, available: $available")
            )
        }

        _modelStatus.value = ModelStatus.Loading
        return@withContext try {
            // Release previous model if different
            if (currentModelId != null && currentModelId != modelId) {
                engine.release()
            }
            engine.loadModel(file.absolutePath)
            currentModelId = modelId
            _modelStatus.value = ModelStatus.Loaded
            Result.success(Unit)
        } catch (e: ModelLoadException) {
            _modelStatus.value = ModelStatus.Error(e.message ?: "Failed to load model")
            Result.failure(e)
        } catch (e: Exception) {
            _modelStatus.value = ModelStatus.Error(e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun deleteModel(modelId: String): Boolean {
        val info = ModelCatalog.findById(modelId) ?: return false
        val deleted = downloader.deleteModel(info.fileName)
        if (deleted) {
            engine.release()
            currentModelId = null
            _modelStatus.value = ModelStatus.NotInstalled
        }
        return deleted
    }

    fun releaseEngine() {
        engine.release()
        currentModelId = null
    }

    /** Selects a model from a custom local path (user-picked from storage). */
    suspend fun loadModelFromPath(absolutePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val file = File(absolutePath)
        if (!FileUtils.isValidFile(file)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid file: $absolutePath"))
        }
        _modelStatus.value = ModelStatus.Loading
        return@withContext try {
            engine.release()
            engine.loadModel(absolutePath)
            currentModelId = "custom"
            _modelStatus.value = ModelStatus.Loaded
            Result.success(Unit)
        } catch (e: Exception) {
            _modelStatus.value = ModelStatus.Error(e.message ?: "Failed to load model")
            Result.failure(e)
        }
    }
}
