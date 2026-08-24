package com.localai.chat.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

data class AppSettings(
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 1024,
    val topP: Float = 0.95f,
    val selectedModelId: String = "gemma_2b_it_int4",
    val useGpuAcceleration: Boolean = true,
    val kaggleUsername: String = "",
    val kaggleKey: String = ""
)

const val DEFAULT_SYSTEM_PROMPT =
    "You are TIO, a concise and helpful AI assistant running fully on-device.\n" +
    "Rules you must always follow:\n" +
    "- Answer ONLY what was asked. Do not add greetings, follow-up questions, or filler phrases.\n" +
    "- Respond in the same language the user writes in.\n" +
    "- Be direct and precise. Avoid unnecessary padding.\n" +
    "- Never repeat yourself or ask 'What would you like to do today?'.\n" +
    "- If you do not know something, say so briefly."

/**
 * Bump this whenever defaults change and you want existing installs to reset.
 * User's Kaggle credentials are preserved across resets.
 */
const val SETTINGS_VERSION = 2

class AppPreferences(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        val KEY_SETTINGS_VERSION = intPreferencesKey("settings_version")
        val KEY_SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val KEY_TEMPERATURE = floatPreferencesKey("temperature")
        val KEY_MAX_TOKENS = intPreferencesKey("max_tokens")
        val KEY_TOP_P = floatPreferencesKey("top_p")
        val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model_id")
        val KEY_USE_GPU = booleanPreferencesKey("use_gpu")
        val KEY_KAGGLE_USERNAME = stringPreferencesKey("kaggle_username")
        val KEY_KAGGLE_KEY = stringPreferencesKey("kaggle_key")
    }

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        val storedVersion = prefs[KEY_SETTINGS_VERSION] ?: 0
        if (storedVersion < SETTINGS_VERSION) {
            // Preserve credentials, reset everything else
            val savedUsername = prefs[KEY_KAGGLE_USERNAME] ?: ""
            val savedKey = prefs[KEY_KAGGLE_KEY] ?: ""
            dataStore.edit { mutable ->
                mutable.clear()
                mutable[KEY_SETTINGS_VERSION] = SETTINGS_VERSION
                mutable[KEY_KAGGLE_USERNAME] = savedUsername
                mutable[KEY_KAGGLE_KEY] = savedKey
            }
        }
        AppSettings(
            systemPrompt = prefs[KEY_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT,
            temperature = prefs[KEY_TEMPERATURE] ?: 0.7f,
            maxTokens = prefs[KEY_MAX_TOKENS] ?: 1024,
            topP = prefs[KEY_TOP_P] ?: 0.95f,
            selectedModelId = prefs[KEY_SELECTED_MODEL] ?: "gemma_2b_it_int4",
            useGpuAcceleration = prefs[KEY_USE_GPU] ?: true,
            kaggleUsername = prefs[KEY_KAGGLE_USERNAME] ?: "",
            kaggleKey = prefs[KEY_KAGGLE_KEY] ?: ""
        )
    }

    suspend fun updateSystemPrompt(value: String) {
        dataStore.edit { it[KEY_SYSTEM_PROMPT] = value }
    }

    suspend fun updateTemperature(value: Float) {
        dataStore.edit { it[KEY_TEMPERATURE] = value }
    }

    suspend fun updateMaxTokens(value: Int) {
        dataStore.edit { it[KEY_MAX_TOKENS] = value }
    }

    suspend fun updateTopP(value: Float) {
        dataStore.edit { it[KEY_TOP_P] = value }
    }

    suspend fun updateSelectedModel(modelId: String) {
        dataStore.edit { it[KEY_SELECTED_MODEL] = modelId }
    }

    suspend fun updateUseGpu(value: Boolean) {
        dataStore.edit { it[KEY_USE_GPU] = value }
    }

    suspend fun updateKaggleCredentials(username: String, key: String) {
        dataStore.edit {
            it[KEY_KAGGLE_USERNAME] = username.trim()
            it[KEY_KAGGLE_KEY] = key.trim()
        }
    }
}
