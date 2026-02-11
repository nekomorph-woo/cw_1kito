package com.cw2.cw_1kito.data.config

import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.LanguageConfig
import com.cw2.cw_1kito.model.VlmModel
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
}
