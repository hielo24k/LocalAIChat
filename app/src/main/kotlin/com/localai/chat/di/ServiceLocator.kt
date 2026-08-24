package com.localai.chat.di

import android.content.Context
import com.localai.chat.data.download.ModelDownloader
import com.localai.chat.data.engine.InferenceEngine
import com.localai.chat.data.engine.MediaPipeInferenceEngine
import com.localai.chat.data.engine.MockInferenceEngine
import com.localai.chat.data.preferences.AppPreferences
import com.localai.chat.data.repository.ChatRepository
import com.localai.chat.data.repository.ModelRepository
import com.localai.chat.data.database.AppDatabase
import java.io.File

/**
 * Simple service locator that provides singleton dependencies.
 *
 * REPLACE_ENGINE: To use a different inference backend, change [buildEngine].
 * Set [USE_MOCK_ENGINE] to true to force the mock engine during development
 * (useful if tasks-genai dependency is not yet resolved).
 */
object ServiceLocator {

    /**
     * Set to true to always use the mock engine regardless of MediaPipe availability.
     * Useful during development or if the tasks-genai artifact fails to resolve.
     */
    private const val USE_MOCK_ENGINE = false

    private lateinit var appContext: Context

    // Lazily-initialized singletons
    val appPreferences: AppPreferences by lazy { AppPreferences(appContext) }

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }
    val chatDao by lazy { database.chatDao() }

    val inferenceEngine: InferenceEngine by lazy { buildEngine() }

    val modelsDir: File by lazy {
        File(appContext.filesDir, "models").also { it.mkdirs() }
    }

    val modelDownloader: ModelDownloader by lazy {
        ModelDownloader(modelsDir)
    }

    val modelRepository: ModelRepository by lazy {
        ModelRepository(
            context = appContext,
            engine = inferenceEngine,
            downloader = modelDownloader
        )
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(engine = inferenceEngine)
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun release() {
        if (::appContext.isInitialized) {
            try {
                inferenceEngine.release()
            } catch (_: Exception) { }
        }
    }

    private fun buildEngine(): InferenceEngine {
        if (USE_MOCK_ENGINE) {
            return MockInferenceEngine()
        }
        return try {
            // Try to instantiate MediaPipe engine.
            // If the dependency is missing, this will throw and we fall back to mock.
            MediaPipeInferenceEngine(context = appContext)
        } catch (e: Throwable) {
            // REPLACE_ENGINE: Handle linkage errors if tasks-genai is not available
            android.util.Log.w(
                "ServiceLocator",
                "MediaPipe unavailable (${e.message}), falling back to MockInferenceEngine"
            )
            MockInferenceEngine()
        }
    }
}
