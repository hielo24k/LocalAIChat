package com.localai.chat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localai.chat.data.model.ChatMessage
import com.localai.chat.data.model.MessageRole
import com.localai.chat.data.preferences.AppSettings
import com.localai.chat.data.repository.ChatRepository
import com.localai.chat.data.repository.ModelRepository
import com.localai.chat.data.repository.ModelStatus
import com.localai.chat.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isGenerating: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.NotInstalled,
    val errorMessage: String? = null,
    val settings: AppSettings = AppSettings()
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val appPreferences: com.localai.chat.data.preferences.AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    init {
        // Observe model status changes
        viewModelScope.launch {
            modelRepository.modelStatus.collect { status ->
                _uiState.update { it.copy(modelStatus = status) }
            }
        }

        // Observe settings changes
        viewModelScope.launch {
            appPreferences.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }

        // Refresh model status on init
        viewModelScope.launch {
            val settings = appPreferences.settings.first()
            modelRepository.refreshStatus(settings.selectedModelId)
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || _uiState.value.isGenerating) return

        val modelStatus = _uiState.value.modelStatus
        if (modelStatus !is ModelStatus.Loaded) {
            _uiState.update {
                it.copy(errorMessage = "No model loaded. Go to Models screen to download and load a model.")
            }
            return
        }

        // Add user message
        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = text
        )

        // Add placeholder AI message for streaming
        val aiMessageId = UUID.randomUUID().toString()
        val aiPlaceholder = ChatMessage(
            id = aiMessageId,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage + aiPlaceholder,
                inputText = "",
                isGenerating = true,
                errorMessage = null
            )
        }

        val settings = _uiState.value.settings
        val historyForPrompt = _uiState.value.messages
            .filter { it.id != aiMessageId } // exclude the placeholder

        val prompt = chatRepository.buildPrompt(historyForPrompt, settings)

        generationJob = viewModelScope.launch {
            try {
                val accumulatedText = StringBuilder()

                chatRepository.streamResponse(prompt)
                    .collect { token ->
                        accumulatedText.append(token)
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { msg ->
                                    if (msg.id == aiMessageId) {
                                        msg.copy(content = accumulatedText.toString(), isStreaming = true)
                                    } else msg
                                }
                            )
                        }
                    }

                // Mark streaming as done
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == aiMessageId) {
                                msg.copy(isStreaming = false)
                            } else msg
                        },
                        isGenerating = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == aiMessageId) {
                                msg.copy(
                                    content = "[Error: ${e.message ?: "Generation failed"}]",
                                    isStreaming = false
                                )
                            } else msg
                        },
                        isGenerating = false,
                        errorMessage = e.message
                    )
                }
            }
        }
    }

    fun stopGeneration() {
        chatRepository.stopGeneration()
        generationJob?.cancel()
        generationJob = null
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.isStreaming) msg.copy(isStreaming = false) else msg
                },
                isGenerating = false
            )
        }
    }

    fun clearConversation() {
        stopGeneration()
        _uiState.update { it.copy(messages = emptyList()) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(
                chatRepository = ServiceLocator.chatRepository,
                modelRepository = ServiceLocator.modelRepository,
                appPreferences = ServiceLocator.appPreferences
            ) as T
        }
    }
}
