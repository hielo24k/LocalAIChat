package com.localai.chat.data.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Mock engine for UI development and testing.
 * Simulates streaming responses with artificial delays.
 * Replace with [MediaPipeInferenceEngine] for production.
 */
class MockInferenceEngine : InferenceEngine {

    @Volatile private var loaded = false
    @Volatile private var shouldStop = false

    override fun isModelLoaded(): Boolean = loaded

    override suspend fun loadModel(modelPath: String) {
        delay(1500) // Simulate loading time
        loaded = true
    }

    override suspend fun generate(prompt: String): String {
        delay(2000)
        return buildMockResponse(prompt)
    }

    override fun generateStream(prompt: String): Flow<String> = flow {
        shouldStop = false
        val response = buildMockResponse(prompt)
        val words = response.split(" ")
        for (word in words) {
            if (!coroutineContext.isActive || shouldStop) break
            emit("$word ")
            delay(60)
        }
    }

    override fun stop() {
        shouldStop = true
    }

    override fun release() {
        loaded = false
    }

    private fun buildMockResponse(prompt: String): String {
        return when {
            prompt.contains("hello", ignoreCase = true) ||
            prompt.contains("hi", ignoreCase = true) ->
                "Hello! I'm a local AI running entirely on your device. How can I help you today?"

            prompt.contains("who are you", ignoreCase = true) ||
            prompt.contains("what are you", ignoreCase = true) ->
                "I'm a local language model running 100% on your Android device using MediaPipe LLM Inference. " +
                "No data is sent to external servers. Your conversations are completely private."

            prompt.contains("?") ->
                "That's an interesting question. As a local AI model, I process everything on-device " +
                "without sending your data anywhere. This is a mock response since no real model " +
                "is loaded yet. Download a model from the Models screen to get real answers."

            else ->
                "I received your message: \"${prompt.take(50)}${if (prompt.length > 50) "..." else "\""} " +
                "This is a mock response. To use a real AI model, go to the Models screen " +
                "and download Gemma 2B-IT. Once downloaded, real inference will happen locally on your device."
        }
    }
}
