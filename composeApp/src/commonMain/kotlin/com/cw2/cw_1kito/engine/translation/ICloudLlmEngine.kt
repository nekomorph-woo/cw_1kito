package com.cw2.cw_1kito.engine.translation

import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.LlmModel

/**
 * 云端 LLM 翻译引擎接口
 *
 * 定义基于普通 LLM（非视觉模型）的文本翻译服务标准接口。
 * 与 IRemoteTranslationEngine 的区别：
 * - ICloudLlmEngine: 用于纯文本翻译，使用普通 LLM 模型
 * - IRemoteTranslationEngine: 用于图像 OCR + 翻译，使用视觉模型（VLM）
 */
interface ICloudLlmEngine {
    /**
     * 翻译文本
     *
     * 使用云端 LLM 进行纯文本翻译。
     *
     * @param text 要翻译的文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param customPrompt 自定义提示词（可选，为 null 时使用默认提示词）
     * @param model 模型（可选，为 null 时使用配置中的默认模型，非 null 时使用指定的模型）
     * @return 翻译后的文本
     * @throws com.cw2.cw_1kito.error.ApiKeyInvalidException API Key 无效
     * @throws com.cw2.cw_1kito.error.RateLimitException API 配额超限
     * @throws com.cw2.cw_1kito.error.NetworkException 网络错误
     * @throws com.cw2.cw_1kito.error.TranslationTimeoutException 翻译超时
     */
    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language,
        customPrompt: String? = null,
        model: LlmModel? = null
    ): String

    /**
     * 批量翻译文本
     *
     * 使用协程并发翻译多个文本，提高翻译效率。
     *
     * @param texts 要翻译的文本列表
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param customPrompt 自定义提示词（可选）
     * @param model 模型（可选，为 null 时使用配置中的默认模型，非 null 时使用指定的模型）
     * @param concurrency 并发数，默认 5
     * @return 翻译后的文本列表（顺序与输入一致）
     * @throws com.cw2.cw_1kito.error.ApiKeyInvalidException API Key 无效
     * @throws com.cw2.cw_1kito.error.RateLimitException API 配额超限
     * @throws com.cw2.cw_1kito.error.NetworkException 网络错误
     * @throws com.cw2.cw_1kito.error.TranslationTimeoutException 翻译超时
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLang: Language,
        targetLang: Language,
        customPrompt: String? = null,
        model: LlmModel? = null,
        concurrency: Int = 5
    ): List<String>

    /**
     * 验证 API Key 有效性
     *
     * 发送测试请求到服务器验证 API Key 是否有效。
     *
     * @param apiKey 要验证的 API Key
     * @return 是否有效
     */
    suspend fun validateApiKey(apiKey: String): Boolean

    /**
     * 设置 API Key
     *
     * @param apiKey API 密钥
     */
    suspend fun setApiKey(apiKey: String)

    /**
     * 获取当前 API Key
     *
     * @return API Key，如果未设置则返回 null
     */
    suspend fun getApiKey(): String?

    /**
     * 检查是否已设置 API Key
     *
     * @return 是否已设置
     */
    suspend fun hasApiKey(): Boolean

    /**
     * 释放资源
     *
     * 清理客户端资源，如关闭 HTTP 连接等。
     */
    fun release()
}
