package com.cw2.cw_1kito.data.config

import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.LanguageConfig
import com.cw2.cw_1kito.model.OcrLanguage
import com.cw2.cw_1kito.model.VlmModel
import com.cw2.cw_1kito.model.PerformanceMode
import com.cw2.cw_1kito.model.TranslationMode
import com.cw2.cw_1kito.model.MergingConfig
import com.cw2.cw_1kito.ui.theme.ThemeConfig
import kotlinx.coroutines.flow.Flow

/**
 * 配置管理器接口
 */
interface ConfigManager {
    /**
     * 获取当前语言配置
     */
    suspend fun getLanguageConfig(): LanguageConfig

    /**
     * 保存语言配置
     */
    suspend fun saveLanguageConfig(config: LanguageConfig)

    /**
     * 获取 API Key
     */
    suspend fun getApiKey(): String?

    /**
     * 保存 API Key（加密存储）
     */
    suspend fun saveApiKey(apiKey: String)

    /**
     * 验证 API Key 有效性
     */
    suspend fun validateApiKey(apiKey: String): ValidationResult

    /**
     * 获取选中的 VLM 模型
     */
    suspend fun getSelectedModel(): VlmModel

    /**
     * 保存选中的 VLM 模型
     */
    suspend fun saveSelectedModel(model: VlmModel)

    /**
     * 获取自定义提示词（null 表示使用默认）
     */
    suspend fun getCustomPrompt(): String?

    /**
     * 保存自定义提示词
     */
    suspend fun saveCustomPrompt(prompt: String?)

    /**
     * 配置变更流
     */
    val configChanges: Flow<ConfigChange>

    /**
     * 获取流式翻译开关状态
     */
    suspend fun getStreamingEnabled(): Boolean

    /**
     * 保存流式翻译开关状态
     */
    suspend fun saveStreamingEnabled(enabled: Boolean)

    /**
     * 获取主题配置
     */
    suspend fun getThemeConfig(): ThemeConfig

    /**
     * 保存主题配置
     */
    suspend fun saveThemeConfig(config: ThemeConfig)

    /**
     * 获取文本合并提示词（null 表示使用默认）
     */
    suspend fun getTextMergingPrompt(): String?

    /**
     * 保存文本合并提示词
     */
    suspend fun saveTextMergingPrompt(prompt: String?)

    /**
     * 获取文本合并功能开关状态
     */
    suspend fun getTextMergingEnabled(): Boolean

    /**
     * 保存文本合并功能开关状态
     */
    suspend fun saveTextMergingEnabled(enabled: Boolean)

    /**
     * 获取 OCR 识别语言（null 表示自动推断）
     */
    suspend fun getOcrLanguage(): OcrLanguage?

    /**
     * 保存 OCR 识别语言
     */
    suspend fun saveOcrLanguage(language: OcrLanguage?)

    /**
     * 获取性能模式
     */
    suspend fun getPerformanceMode(): PerformanceMode

    /**
     * 保存性能模式
     */
    suspend fun savePerformanceMode(mode: PerformanceMode)

    /**
     * 获取翻译模式
     */
    suspend fun getTranslationMode(): TranslationMode

    /**
     * 保存翻译模式
     */
    suspend fun saveTranslationMode(mode: TranslationMode)

    /**
     * 获取合并配置
     */
    suspend fun getMergingConfig(): MergingConfig

    /**
     * 保存合并配置
     */
    suspend fun saveMergingConfig(config: MergingConfig)

    /**
     * 获取本地OCR功能开关状态（实验室设置）
     */
    suspend fun getEnableLocalOcr(): Boolean

    /**
     * 保存本地OCR功能开关状态
     */
    suspend fun saveEnableLocalOcr(enabled: Boolean)

    /**
     * 获取是否使用本地OCR方案
     */
    suspend fun getUseLocalOcrScheme(): Boolean

    /**
     * 保存是否使用本地OCR方案
     */
    suspend fun saveUseLocalOcrScheme(use: Boolean)

    /**
     * 获取本地OCR翻译模式
     */
    suspend fun getLocalOcrTranslationMode(): TranslationMode

    /**
     * 保存本地OCR翻译模式
     */
    suspend fun saveLocalOcrTranslationMode(mode: TranslationMode)

    /**
     * 获取云端LLM模型ID
     */
    suspend fun getCloudLlmModel(): String

    /**
     * 保存云端LLM模型ID
     */
    suspend fun saveCloudLlmModel(modelId: String)

    /**
     * 获取自定义翻译提示词
     */
    suspend fun getCustomTranslationPrompt(): String?

    /**
     * 保存自定义翻译提示词
     */
    suspend fun saveCustomTranslationPrompt(prompt: String?)

    /**
     * 清除所有配置
     */
    suspend fun clearAll()
}

/**
 * API Key 验证结果
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()
    data object Invalid : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

/**
 * 配置变更事件
 */
sealed class ConfigChange {
    data class LanguageChanged(val config: LanguageConfig) : ConfigChange()
    data class ApiKeyChanged(val isValid: Boolean) : ConfigChange()
    data class ModelChanged(val model: VlmModel) : ConfigChange()
    data class TextMergingChanged(val enabled: Boolean) : ConfigChange()
    data class PerformanceModeChanged(val mode: PerformanceMode) : ConfigChange()
    data class TranslationModeChanged(val mode: TranslationMode) : ConfigChange()
    data class MergingConfigChanged(val config: MergingConfig) : ConfigChange()
    data class LocalOcrEnabledChanged(val enabled: Boolean) : ConfigChange()
    data class LocalOcrSchemeChanged(val useLocalOcr: Boolean) : ConfigChange()
    data class LocalOcrTranslationModeChanged(val mode: TranslationMode) : ConfigChange()
    data class OcrLanguageChanged(val language: OcrLanguage?) : ConfigChange()
    data class CloudLlmModelChanged(val modelId: String) : ConfigChange()
}
