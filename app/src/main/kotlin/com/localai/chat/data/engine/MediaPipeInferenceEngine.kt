package com.localai.chat.data.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production inference engine backed by MediaPipe LLM Inference API.
 *
 * Session-based API (tasks-genai >= 0.10.20):
 *  1. LlmInference.createFromOptions()      → global engine (model path, max tokens)
 *  2. LlmInferenceSession.createFromOptions() → per-request session (temperature, topK)
 *  3. session.addQueryChunk(prompt)         → set the prompt
 *  4. session.generateResponse()            → blocking response
 *     OR session.generateResponseAsync(ProgressListener) → streaming
 *
 * REPLACE_ENGINE: implement [InferenceEngine] and update ServiceLocator.
 */
class MediaPipeInferenceEngine(
    private val context: Context,
    private val maxTokens: Int = 1024,
    private val temperature: Float = 0.8f,
    private val topK: Int = 40,
    private val randomSeed: Int = 42
) : InferenceEngine {

    @Volatile private var llmInference: LlmInference? = null
    @Volatile private var shouldStop = false

    override fun isModelLoaded(): Boolean = llmInference != null

    override suspend fun loadModel(modelPath: String) {
        release()

        val options = LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(maxTokens)
            .build()

        return suspendCancellableCoroutine { cont ->
            try {
                llmInference = LlmInference.createFromOptions(context, options)
                cont.resume(Unit)
            } catch (e: Exception) {
                // Unroll the full cause chain so the UI shows exactly what MediaPipe reported
                val fullMessage = generateSequence(e as Throwable) { it.cause }
                    .mapNotNull { it.message }
                    .distinct()
                    .joinToString(" → ")
                cont.resumeWithException(
                    ModelLoadException(fullMessage.ifBlank { "Unknown error loading model" }, e)
                )
            }
        }
    }

    private fun buildSessionOptions(): LlmInferenceSessionOptions =
        LlmInferenceSessionOptions.builder()
            .setTemperature(temperature)
            .setTopK(topK)
            .setRandomSeed(randomSeed)
            .build()

    override suspend fun generate(prompt: String): String {
        val engine = llmInference
            ?: throw InferenceException("Model not loaded. Call loadModel() first.")

        return suspendCancellableCoroutine { cont ->
            try {
                val session = LlmInferenceSession.createFromOptions(engine, buildSessionOptions())
                session.use {
                    // Prompt is fed via addQueryChunk, then generateResponse() returns the full response
                    it.addQueryChunk(prompt)
                    val result = it.generateResponse()
                    cont.resume(result)
                }
            } catch (e: Exception) {
                cont.resumeWithException(InferenceException("Inference failed: ${e.message}", e))
            }
        }
    }

    override fun generateStream(prompt: String): Flow<String> = callbackFlow {
        val engine = llmInference
            ?: throw InferenceException("Model not loaded. Call loadModel() first.")

        shouldStop = false
        val session = LlmInferenceSession.createFromOptions(engine, buildSessionOptions())
        session.addQueryChunk(prompt)

        // ProgressListener<String> is the streaming callback:
        // run(result: String?, done: Boolean) — called for each token chunk
        val listener = ProgressListener<String> { partialResult, done ->
            if (!shouldStop && partialResult != null) {
                trySend(partialResult)
            }
            if (done || shouldStop) {
                try { session.close() } catch (_: Exception) {}
                close()
            }
        }

        session.generateResponseAsync(listener)

        awaitClose {
            shouldStop = true
            try { session.close() } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    override fun stop() {
        shouldStop = true
    }

    override fun release() {
        try {
            llmInference?.close()
        } catch (_: Exception) {
        } finally {
            llmInference = null
        }
    }
}
