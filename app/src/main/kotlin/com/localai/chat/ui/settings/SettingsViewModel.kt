package com.localai.chat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.localai.chat.data.preferences.AppPreferences
import com.localai.chat.data.preferences.AppSettings
import com.localai.chat.data.preferences.DEFAULT_SYSTEM_PROMPT
import com.localai.chat.di.ServiceLocator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isSaved: Boolean = false
)

class SettingsViewModel(
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Local editable copies
    private val _editSystemPrompt = MutableStateFlow(DEFAULT_SYSTEM_PROMPT)
    val editSystemPrompt: StateFlow<String> = _editSystemPrompt.asStateFlow()

    private val _editTemperature = MutableStateFlow(0.7f)
    val editTemperature: StateFlow<Float> = _editTemperature.asStateFlow()

    private val _editMaxTokens = MutableStateFlow(1024)
    val editMaxTokens: StateFlow<Int> = _editMaxTokens.asStateFlow()

    private val _editTopP = MutableStateFlow(0.95f)
    val editTopP: StateFlow<Float> = _editTopP.asStateFlow()

    private val _editUseGpu = MutableStateFlow(true)
    val editUseGpu: StateFlow<Boolean> = _editUseGpu.asStateFlow()

    init {
        viewModelScope.launch {
            appPreferences.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                _editSystemPrompt.value = settings.systemPrompt
                _editTemperature.value = settings.temperature
                _editMaxTokens.value = settings.maxTokens
                _editTopP.value = settings.topP
                _editUseGpu.value = settings.useGpuAcceleration
            }
        }
    }

    fun onSystemPromptChange(value: String) { _editSystemPrompt.value = value }
    fun onTemperatureChange(value: Float) { _editTemperature.value = value }
    fun onMaxTokensChange(value: Int) { _editMaxTokens.value = value }
    fun onTopPChange(value: Float) { _editTopP.value = value }
    fun onUseGpuChange(value: Boolean) { _editUseGpu.value = value }

    fun saveSettings() {
        viewModelScope.launch {
            appPreferences.updateSystemPrompt(_editSystemPrompt.value)
            appPreferences.updateTemperature(_editTemperature.value)
            appPreferences.updateMaxTokens(_editMaxTokens.value)
            appPreferences.updateTopP(_editTopP.value)
            appPreferences.updateUseGpu(_editUseGpu.value)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun resetToDefaults() {
        _editSystemPrompt.value = DEFAULT_SYSTEM_PROMPT
        _editTemperature.value = 0.7f
        _editMaxTokens.value = 1024
        _editTopP.value = 0.95f
        _editUseGpu.value = true
    }

    fun dismissSaved() {
        _uiState.update { it.copy(isSaved = false) }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                appPreferences = ServiceLocator.appPreferences
            ) as T
        }
    }
}
