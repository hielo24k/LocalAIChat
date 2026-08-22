package com.localai.chat.data.engine

import kotlinx.coroutines.flow.Flow

/**
 * Core abstraction for on-device language model inference.
 *
 * Implementations:
 *  - [MediaPipeInferenceEngine]: uses com.google.mediapipe:tasks-genai
 *  - [MockInferenceEngine]: simulates responses for UI testing without a model
 *
 * All suspend functions should be called on Dispatchers.IO.
 * [generateStream] emits partial text tokens as they are produced.
 */
interface InferenceEngine {

    /** Returns true if a model has been successfully loaded and is ready. */
    fun isModelLoaded(): Boolean

    /**
     * Loads the model from [modelPath].
     * Throws [ModelLoadException] if the model cannot be loaded.
     * Should be called once per model file; call [release] before loading a different model.
     */
    suspend fun loadModel(modelPath: String)

    /**
     * Generates a response for [prompt] and returns the full result.
     * Throws [InferenceException] if generation fails.
     * Prefer [generateStream] for a better user experience.
     */
    suspend fun generate(prompt: String): String

    /**
     * Generates a response for [prompt] and emits partial text chunks.
     * Completes when generation is done or [stop] is called.
     */
    fun generateStream(prompt: String): Flow<String>

    /** Signals the engine to stop the current generation. */
    fun stop()

    /** Releases all resources held by the engine. Must be called when done. */
    fun release()
}

class ModelLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
class InferenceException(message: String, cause: Throwable? = null) : Exception(message, cause)
