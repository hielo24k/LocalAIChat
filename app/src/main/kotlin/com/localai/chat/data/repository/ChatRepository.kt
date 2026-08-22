package com.localai.chat.data.repository

import com.localai.chat.data.engine.InferenceEngine
import com.localai.chat.data.engine.InferenceException
import com.localai.chat.data.model.ChatMessage
import com.localai.chat.data.model.MessageRole
import com.localai.chat.data.preferences.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion

class ChatRepository(private val engine: InferenceEngine) {

    /**
     * Formats a conversation history + system prompt into a single prompt string.
     * Uses a simple instruction-following format compatible with Gemma-IT models.
     */
    fun buildPrompt(messages: List<ChatMessage>, settings: AppSettings): String {
        val sb = StringBuilder()

        // System prompt as context prefix (Gemma-IT style)
        if (settings.systemPrompt.isNotBlank()) {
            sb.append("<start_of_turn>user\n")
            sb.append(settings.systemPrompt)
            sb.append("<end_of_turn>\n")
            sb.append("<start_of_turn>model\n")
            sb.append("Understood. I will follow these instructions.")
            sb.append("<end_of_turn>\n")
        }

        for (msg in messages) {
            when (msg.role) {
                MessageRole.USER -> {
                    sb.append("<start_of_turn>user\n")
                    sb.append(msg.content)
                    sb.append("<end_of_turn>\n")
                }
                MessageRole.ASSISTANT -> {
                    sb.append("<start_of_turn>model\n")
                    sb.append(msg.content)
                    sb.append("<end_of_turn>\n")
                }
            }
        }

        // End with the model's turn prefix to prompt generation
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    /**
     * Streams a response for the given [prompt].
     * Emits partial text tokens as they are generated.
     */
    fun streamResponse(prompt: String): Flow<String> {
        return engine.generateStream(prompt)
            .catch { e ->
                emit("[Error: ${e.message ?: "Generation failed"}]")
            }
    }

    /**
     * Generates a full response (non-streaming) for [prompt].
     */
    suspend fun generateResponse(prompt: String): String {
        return try {
            engine.generate(prompt)
        } catch (e: InferenceException) {
            "[Error: ${e.message}]"
        } catch (e: Exception) {
            "[Error: ${e.message ?: "Unknown error during generation"}]"
        }
    }

    fun stopGeneration() {
        engine.stop()
    }
}
