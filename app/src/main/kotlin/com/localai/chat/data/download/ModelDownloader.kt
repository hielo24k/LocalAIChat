package com.localai.chat.data.download

import com.localai.chat.util.FileUtils
import com.localai.chat.util.MemoryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long, val progressFraction: Float) : DownloadState()
    data class Completed(val file: File) : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

class ModelDownloader(
    private val modelsDir: File
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Downloads a model from [url] into [modelsDir]/[fileName].
     * [kaggleUsername] and [kaggleKey] are used for Kaggle Basic Auth when provided.
     */
    fun download(
        url: String,
        fileName: String,
        expectedSizeBytes: Long,
        kaggleUsername: String = "",
        kaggleKey: String = "",
        authToken: String = ""   // legacy HF Bearer token, kept for compatibility
    ): Flow<DownloadState> = flow {
        emit(DownloadState.Idle)

        // Ensure the models directory exists
        if (!FileUtils.ensureDir(modelsDir)) {
            emit(DownloadState.Failed("Cannot create models directory: ${modelsDir.absolutePath}"))
            return@flow
        }

        // Check available disk space
        if (!MemoryUtils.hasEnoughDisk(modelsDir, expectedSizeBytes)) {
            val free = MemoryUtils.freeDiskBytes(modelsDir)
            val needed = MemoryUtils.formatBytes(expectedSizeBytes)
            val available = MemoryUtils.formatBytes(free)
            emit(DownloadState.Failed("Not enough disk space. Need $needed, have $available free."))
            return@flow
        }

        val destFile = File(modelsDir, fileName)
        val tempFile = File(modelsDir, "$fileName.download")

        try {
            val requestBuilder = Request.Builder().url(url)
            when {
                kaggleUsername.isNotBlank() && kaggleKey.isNotBlank() -> {
                    // Kaggle API uses HTTP Basic Auth: Base64(username:key)
                    val credentials = Base64.getEncoder()
                        .encodeToString("$kaggleUsername:$kaggleKey".toByteArray())
                    requestBuilder.addHeader("Authorization", "Basic $credentials")
                }
                authToken.isNotBlank() -> {
                    requestBuilder.addHeader("Authorization", "Bearer $authToken")
                }
            }
            val response = client.newCall(requestBuilder.build()).execute()

            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    403 -> "Access denied (403): Gemma requires accepting the license on Kaggle.\n\n" +
                           "Use 'Select from Storage' to load a model you already downloaded manually.\n" +
                           "Download at: kaggle.com/models/google/gemma"
                    404 -> "Model not found at the download URL (404). The URL may have changed."
                    else -> "Server error ${response.code}: ${response.message}"
                }
                emit(DownloadState.Failed(errorMsg))
                return@flow
            }

            val body = response.body
                ?: run {
                    emit(DownloadState.Failed("Empty response body from server"))
                    return@flow
                }

            val contentLength = body.contentLength().takeIf { it > 0 } ?: expectedSizeBytes
            var bytesDownloaded = 0L

            tempFile.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (!coroutineContext.isActive) {
                            // Coroutine was cancelled — clean up
                            tempFile.delete()
                            return@flow
                        }
                        out.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        val fraction = if (contentLength > 0) {
                            (bytesDownloaded.toFloat() / contentLength).coerceIn(0f, 1f)
                        } else 0f
                        emit(DownloadState.Downloading(bytesDownloaded, contentLength, fraction))
                    }
                }
            }

            // Rename temp file to final destination
            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                tempFile.delete()
                emit(DownloadState.Failed("Failed to finalize downloaded file"))
                return@flow
            }

            // Validate the file is non-empty
            if (!FileUtils.isValidFile(destFile)) {
                destFile.delete()
                emit(DownloadState.Failed("Downloaded file is empty or corrupt"))
                return@flow
            }

            emit(DownloadState.Completed(destFile))

        } catch (e: Exception) {
            tempFile.delete()
            emit(DownloadState.Failed("Download failed: ${e.message ?: "Unknown error"}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Returns the local file for a model if it exists and appears valid.
     */
    fun getLocalFile(fileName: String): File? {
        val file = File(modelsDir, fileName)
        return if (FileUtils.isValidFile(file)) file else null
    }

    /**
     * Deletes the local model file.
     */
    fun deleteModel(fileName: String): Boolean {
        val file = File(modelsDir, fileName)
        return FileUtils.deleteIfExists(file)
    }
}
