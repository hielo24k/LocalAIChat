package com.localai.chat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localai.chat.data.database.ChatDao
import com.localai.chat.data.database.ConversationEntity
import com.localai.chat.data.database.MessageEntity
import com.localai.chat.data.model.ChatMessage
import com.localai.chat.data.model.MessageRole
import com.localai.chat.data.preferences.AppSettings
import com.localai.chat.data.repository.ChatRepository
import com.localai.chat.data.repository.ModelRepository
import com.localai.chat.data.repository.ModelStatus
import com.localai.chat.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
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
    val settings: AppSettings = AppSettings(),
    val conversations: List<ConversationEntity> = emptyList(),
    val currentConversationId: String? = null
)

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val appPreferences: com.localai.chat.data.preferences.AppPreferences,
    private val chatDao: ChatDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null
    private var messagesJob: Job? = null

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

        // Observe conversations
        viewModelScope.launch {
            chatDao.getAllConversations().collect { conversations ->
                _uiState.update { it.copy(conversations = conversations) }
            }
        }

        // Refresh model status on init
        viewModelScope.launch {
            val settings = appPreferences.settings.first()
            modelRepository.refreshStatus(settings.selectedModelId)
        }
    }

    fun startNewConversation() {
        stopGeneration()
        messagesJob?.cancel()
        _uiState.update {
            it.copy(messages = emptyList(), currentConversationId = null)
        }
    }

    fun loadConversation(conversationId: String) {
        stopGeneration()
        messagesJob?.cancel()
        _uiState.update { it.copy(currentConversationId = conversationId) }
        messagesJob = viewModelScope.launch {
            chatDao.getMessagesForConversation(conversationId).collect { entities ->
                val messages = entities.map { e ->
                    ChatMessage(
                        id = e.id,
                        role = MessageRole.valueOf(e.role),
                        content = e.content,
                        timestamp = e.timestamp,
                        tokensPerSecond = e.tokensPerSecond,
                        generationTimeMs = e.generationTimeMs
                    )
                }
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            chatDao.deleteConversation(conversationId)
            if (_uiState.value.currentConversationId == conversationId) {
                startNewConversation()
            }
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

        // Create or use existing conversation
        val conversationId = _uiState.value.currentConversationId ?: UUID.randomUUID().toString()
        val isNewConversation = _uiState.value.currentConversationId == null

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
                errorMessage = null,
                currentConversationId = conversationId
            )
        }

        val settings = _uiState.value.settings
        val historyForPrompt = _uiState.value.messages
            .filter { it.id != aiMessageId }

        val prompt = chatRepository.buildPrompt(historyForPrompt, settings)

        generationJob = viewModelScope.launch(Dispatchers.Default) {
            // Persist conversation and user message
            if (isNewConversation) {
                val title = text.take(50) + if (text.length > 50) "..." else ""
                chatDao.insertConversation(ConversationEntity(id = conversationId, title = title))
            } else {
                chatDao.updateConversation(conversationId, title = text.take(50) + if (text.length > 50) "..." else "")
            }
            chatDao.insertMessage(
                MessageEntity(
                    id = userMessage.id,
                    conversationId = conversationId,
                    role = userMessage.role.name,
                    content = userMessage.content,
                    timestamp = userMessage.timestamp
                )
            )
            // Insert placeholder for AI message
            chatDao.insertMessage(
                MessageEntity(
                    id = aiMessageId,
                    conversationId = conversationId,
                    role = MessageRole.ASSISTANT.name,
                    content = "",
                    timestamp = System.currentTimeMillis()
                )
            )

            try {
                val accumulatedText = StringBuilder()
                val startTime = System.currentTimeMillis()
                var tokenCount = 0

                chatRepository.streamResponse(prompt)
                    .collect { token ->
                        accumulatedText.append(token)
                        tokenCount++
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

                // Mark streaming as done with speed stats
                val elapsedMs = System.currentTimeMillis() - startTime
                val tps = if (elapsedMs > 0) tokenCount * 1000f / elapsedMs else 0f
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == aiMessageId) {
                                msg.copy(
                                    isStreaming = false,
                                    tokensPerSecond = tps,
                                    generationTimeMs = elapsedMs
                                )
                            } else msg
                        },
                        isGenerating = false
                    )
                }

                // Persist final AI response
                chatDao.updateMessage(
                    id = aiMessageId,
                    content = accumulatedText.toString(),
                    tps = tps,
                    genTime = elapsedMs
                )
            } catch (e: Exception) {
                val errorContent = "[Error: ${e.message ?: "Generation failed"}]"
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == aiMessageId) {
                                msg.copy(
                                    content = errorContent,
                                    isStreaming = false
                                )
                            } else msg
                        },
                        isGenerating = false,
                        errorMessage = e.message
                    )
                }
                chatDao.updateMessage(id = aiMessageId, content = errorContent)
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
        val convId = _uiState.value.currentConversationId
        stopGeneration()
        _uiState.update { it.copy(messages = emptyList(), currentConversationId = null) }
        if (convId != null) {
            viewModelScope.launch { chatDao.deleteConversation(convId) }
        }
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
                appPreferences = ServiceLocator.appPreferences,
                chatDao = ServiceLocator.chatDao
            ) as T
        }
    }
}
