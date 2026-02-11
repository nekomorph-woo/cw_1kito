package com.cw2.cw_1kito.data.config

import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.LanguageConfig
import com.cw2.cw_1kito.model.VlmModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DataStore 配置键
 */
@Serializable
data class PreferencesKeys(
    @SerialName("api_key")
    val apiKey: String = "api_key",
    @SerialName("source_language")
    val sourceLanguage: String = "source_language",
    @SerialName("target_language")
    val targetLanguage: String = "target_language",
    @SerialName("selected_model")
    val selectedModel: String = "selected_model"
)

/**
 * 配置管理器实现
 * 使用 expect/actual 模式支持不同平台
 */
abstract class ConfigManagerImpl : ConfigManager {

    protected val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _configChanges = Channel<ConfigChange>(capacity = Channel.UNLIMITED)
    override val configChanges: Flow<ConfigChange> = _configChanges.receiveAsFlow()

    /**
     * 保存 API Key（平台相关实现）
     */
    protected abstract suspend fun saveApiKeyInternal(apiKey: String)

    /**
     * 获取 API Key（平台相关实现）
     */
    protected abstract suspend fun getApiKeyInternal(): String?

    /**
     * 保存字符串值
     */
    protected abstract suspend fun saveString(key: String, value: String)

    /**
     * 获取字符串值
     */
    protected abstract suspend fun getString(key: String): String?

    /**
     * 清除指定键
     */
    protected abstract suspend fun remove(key: String)

    override suspend fun getApiKey(): String? {
        return getApiKeyInternal()
    }

    override suspend fun saveApiKey(apiKey: String) {
        saveApiKeyInternal(apiKey)
        val isValid = validateApiKey(apiKey) is ValidationResult.Valid
        _configChanges.trySend(ConfigChange.ApiKeyChanged(isValid))
    }

    override suspend fun validateApiKey(apiKey: String): ValidationResult {
        return when {
            apiKey.isBlank() -> ValidationResult.Invalid
            apiKey.length < 10 -> ValidationResult.Invalid
            !apiKey.startsWith("sk-") && !apiKey.startsWith("Bearer ") -> {
                // 允许多种格式，但给出警告
                ValidationResult.Valid
            }
            else -> ValidationResult.Valid
        }
    }

    override suspend fun getLanguageConfig(): LanguageConfig {
        val sourceCode = getString(PreferencesKeys().sourceLanguage)
        val targetCode = getString(PreferencesKeys().targetLanguage)

        val sourceLanguage = sourceCode?.let { Language.fromCode(it) } ?: Language.AUTO
        val targetLanguage = targetCode?.let { Language.fromCode(it) } ?: Language.ZH

        return LanguageConfig(
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage
        )
    }

    override suspend fun saveLanguageConfig(config: LanguageConfig) {
        saveString(PreferencesKeys().sourceLanguage, config.sourceLanguage.code)
        saveString(PreferencesKeys().targetLanguage, config.targetLanguage.code)
        _configChanges.trySend(ConfigChange.LanguageChanged(config))
    }

    override suspend fun getSelectedModel(): VlmModel {
        val modelId = getString(PreferencesKeys().selectedModel)
        return modelId?.let { VlmModel.fromId(it) } ?: VlmModel.DEFAULT
    }

    override suspend fun saveSelectedModel(model: VlmModel) {
        saveString(PreferencesKeys().selectedModel, model.id)
        _configChanges.trySend(ConfigChange.ModelChanged(model))
    }

    override suspend fun clearAll() {
        remove(PreferencesKeys().apiKey)
        remove(PreferencesKeys().sourceLanguage)
        remove(PreferencesKeys().targetLanguage)
        remove(PreferencesKeys().selectedModel)
        remove(CUSTOM_PROMPT_KEY)
    }

    override suspend fun getCustomPrompt(): String? {
        return getString(CUSTOM_PROMPT_KEY)
    }

    override suspend fun saveCustomPrompt(prompt: String?) {
        if (prompt == null) {
            remove(CUSTOM_PROMPT_KEY)
        } else {
            saveString(CUSTOM_PROMPT_KEY, prompt)
        }
    }

    companion object {
        private const val CUSTOM_PROMPT_KEY = "custom_prompt"
    }
}

/**
 * API Key 加密工具（平台相关接口）
 */
interface ApiKeyEncryptor {
    /**
     * 加密 API Key
     */
    suspend fun encrypt(apiKey: String): String

    /**
     * 解密 API Key
     */
    suspend fun decrypt(encryptedApiKey: String): String
}
