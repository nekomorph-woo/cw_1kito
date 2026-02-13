package com.cw2.cw_1kito.data.config

import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.LanguageConfig
import com.cw2.cw_1kito.model.VlmModel
import com.cw2.cw_1kito.model.OcrLanguage
import com.cw2.cw_1kito.model.PerformanceMode
import com.cw2.cw_1kito.model.TranslationMode
import com.cw2.cw_1kito.model.MergingConfig
import com.cw2.cw_1kito.ui.theme.ThemeConfig
import com.cw2.cw_1kito.ui.theme.ThemeHue
import com.cw2.cw_1kito.ui.theme.DarkModeOption
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
        remove(STREAMING_ENABLED_KEY)
        remove(THEME_HUE_KEY)
        remove(DARK_MODE_KEY)
        remove(TEXT_MERGING_ENABLED_KEY)
        remove(TEXT_MERGING_PROMPT_KEY)
        remove(PERFORMANCE_MODE_KEY)
        remove(TRANSLATION_MODE_KEY)
        remove(MERGING_CONFIG_KEY)
        remove(ENABLE_LOCAL_OCR_KEY)
        remove(USE_LOCAL_OCR_SCHEME_KEY)
        remove(LOCAL_OCR_TRANSLATION_MODE_KEY)
        remove(OCR_LANGUAGE_KEY)
        remove(CLOUD_LLM_MODEL_KEY)
        remove(CUSTOM_TRANSLATION_PROMPT_KEY)
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

    override suspend fun getStreamingEnabled(): Boolean {
        return getString(STREAMING_ENABLED_KEY)?.toBoolean() ?: false
    }

    override suspend fun saveStreamingEnabled(enabled: Boolean) {
        saveString(STREAMING_ENABLED_KEY, enabled.toString())
    }

    override suspend fun getThemeConfig(): ThemeConfig {
        val hueName = getString(THEME_HUE_KEY)
        val darkModeName = getString(DARK_MODE_KEY)

        val hue = ThemeHue.fromName(hueName)
        val darkMode = DarkModeOption.fromName(darkModeName)

        return ThemeConfig(hue, darkMode)
    }

    override suspend fun saveThemeConfig(config: ThemeConfig) {
        saveString(THEME_HUE_KEY, config.hue.name)
        saveString(DARK_MODE_KEY, config.darkMode.name)
    }

    override suspend fun getTextMergingEnabled(): Boolean {
        return getString(TEXT_MERGING_ENABLED_KEY)?.toBoolean() ?: false
    }

    override suspend fun saveTextMergingEnabled(enabled: Boolean) {
        saveString(TEXT_MERGING_ENABLED_KEY, enabled.toString())
    }

    override suspend fun getTextMergingPrompt(): String? {
        return getString(TEXT_MERGING_PROMPT_KEY)
    }

    override suspend fun saveTextMergingPrompt(prompt: String?) {
        if (prompt == null) {
            remove(TEXT_MERGING_PROMPT_KEY)
        } else {
            saveString(TEXT_MERGING_PROMPT_KEY, prompt)
        }
    }

    override suspend fun getPerformanceMode(): PerformanceMode {
        val modeName = getString(PERFORMANCE_MODE_KEY)
        return modeName?.let { PerformanceMode.valueOf(it) } ?: PerformanceMode.DEFAULT
    }

    override suspend fun savePerformanceMode(mode: PerformanceMode) {
        saveString(PERFORMANCE_MODE_KEY, mode.name)
        _configChanges.trySend(ConfigChange.PerformanceModeChanged(mode))
    }

    override suspend fun getTranslationMode(): TranslationMode {
        val modeName = getString(TRANSLATION_MODE_KEY)
        return modeName?.let { TranslationMode.valueOf(it) } ?: TranslationMode.DEFAULT
    }

    override suspend fun saveTranslationMode(mode: TranslationMode) {
        saveString(TRANSLATION_MODE_KEY, mode.name)
        _configChanges.trySend(ConfigChange.TranslationModeChanged(mode))
    }

    override suspend fun getMergingConfig(): MergingConfig {
        val configJson = getString(MERGING_CONFIG_KEY)
        return if (configJson != null) {
            try {
                json.decodeFromString<MergingConfig>(configJson)
            } catch (e: Exception) {
                MergingConfig.DEFAULT
            }
        } else {
            MergingConfig.DEFAULT
        }
    }

    override suspend fun saveMergingConfig(config: MergingConfig) {
        val configJson = json.encodeToString(MergingConfig.serializer(), config)
        saveString(MERGING_CONFIG_KEY, configJson)
        _configChanges.trySend(ConfigChange.MergingConfigChanged(config))
    }

    override suspend fun getEnableLocalOcr(): Boolean {
        return getString(ENABLE_LOCAL_OCR_KEY)?.toBoolean() ?: false
    }

    override suspend fun saveEnableLocalOcr(enabled: Boolean) {
        saveString(ENABLE_LOCAL_OCR_KEY, enabled.toString())
        _configChanges.trySend(ConfigChange.LocalOcrEnabledChanged(enabled))
    }

    override suspend fun getUseLocalOcrScheme(): Boolean {
        return getString(USE_LOCAL_OCR_SCHEME_KEY)?.toBoolean() ?: true
    }

    override suspend fun saveUseLocalOcrScheme(use: Boolean) {
        saveString(USE_LOCAL_OCR_SCHEME_KEY, use.toString())
        _configChanges.trySend(ConfigChange.LocalOcrSchemeChanged(use))
    }

    override suspend fun getLocalOcrTranslationMode(): TranslationMode {
        val modeName = getString(LOCAL_OCR_TRANSLATION_MODE_KEY)
        return modeName?.let { TranslationMode.valueOf(it) } ?: TranslationMode.LOCAL
    }

    override suspend fun saveLocalOcrTranslationMode(mode: TranslationMode) {
        saveString(LOCAL_OCR_TRANSLATION_MODE_KEY, mode.name)
        _configChanges.trySend(ConfigChange.LocalOcrTranslationModeChanged(mode))
    }

    override suspend fun getCloudLlmModel(): String {
        return getString(CLOUD_LLM_MODEL_KEY) ?: DEFAULT_CLOUD_LLM_MODEL
    }

    override suspend fun saveCloudLlmModel(modelId: String) {
        saveString(CLOUD_LLM_MODEL_KEY, modelId)
        _configChanges.trySend(ConfigChange.CloudLlmModelChanged(modelId))
    }

    override suspend fun getCustomTranslationPrompt(): String? {
        return getString(CUSTOM_TRANSLATION_PROMPT_KEY)
    }

    override suspend fun saveCustomTranslationPrompt(prompt: String?) {
        if (prompt == null) {
            remove(CUSTOM_TRANSLATION_PROMPT_KEY)
        } else {
            saveString(CUSTOM_TRANSLATION_PROMPT_KEY, prompt)
        }
    }

    override suspend fun getOcrLanguage(): OcrLanguage? {
        val languageCode = getString(OCR_LANGUAGE_KEY)
        return languageCode?.let { OcrLanguage.fromCode(it) }
    }

    override suspend fun saveOcrLanguage(language: OcrLanguage?) {
        if (language == null) {
            remove(OCR_LANGUAGE_KEY)
        } else {
            saveString(OCR_LANGUAGE_KEY, language.code)
        }
        _configChanges.trySend(ConfigChange.OcrLanguageChanged(language))
    }

    companion object {
        private const val CUSTOM_PROMPT_KEY = "custom_prompt"
        private const val STREAMING_ENABLED_KEY = "lab_streaming_enabled"
        private const val THEME_HUE_KEY = "theme_hue"
        private const val DARK_MODE_KEY = "dark_mode"
        private const val TEXT_MERGING_ENABLED_KEY = "text_merging_enabled"
        private const val TEXT_MERGING_PROMPT_KEY = "text_merging_prompt"
        private const val PERFORMANCE_MODE_KEY = "performance_mode"
        private const val TRANSLATION_MODE_KEY = "translation_mode"
        private const val MERGING_CONFIG_KEY = "merging_config"

        // 本地OCR相关配置键
        private const val ENABLE_LOCAL_OCR_KEY = "enable_local_ocr"
        private const val USE_LOCAL_OCR_SCHEME_KEY = "use_local_ocr_scheme"
        private const val LOCAL_OCR_TRANSLATION_MODE_KEY = "local_ocr_translation_mode"
        private const val OCR_LANGUAGE_KEY = "ocr_language"
        private const val CLOUD_LLM_MODEL_KEY = "cloud_llm_model"
        private const val CUSTOM_TRANSLATION_PROMPT_KEY = "custom_translation_prompt"

        // 默认值
        private const val DEFAULT_CLOUD_LLM_MODEL = "Qwen/Qwen2.5-7B-Instruct"
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
