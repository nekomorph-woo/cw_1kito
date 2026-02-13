package com.cw2.cw_1kito.engine.translation.remote

import com.cw2.cw_1kito.model.Language

/**
 * 远程翻译引擎接口
 *
 * 定义云端翻译服务的标准接口,支持硅基流动(SiliconFlow)等云服务提供商。
 */
interface IRemoteTranslationEngine {
    /**
     * 设置 API Key
     *
     * @param apiKey API 密钥
     */
    suspend fun setApiKey(apiKey: String)

    /**
     * 获取当前 API Key
     *
     * @return API Key,如果未设置则返回 null
     */
    suspend fun getApiKey(): String?

    /**
     * 检查是否已设置 API Key
     *
     * @return 是否已设置
     */
    suspend fun hasApiKey(): Boolean

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
     * 翻译文本
     *
     * 使用云服务翻译文本。
     *
     * @param text 要翻译的文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 翻译后的文本
     * @throws com.cw2.cw_1kito.error.ApiKeyInvalidException API Key 无效
     * @throws com.cw2.cw_1kito.error.RateLimitException API 配额超限
     * @throws com.cw2.cw_1kito.error.NetworkException 网络错误
     * @throws com.cw2.cw_1kito.error.TranslationTimeoutException 翻译超时
     */
    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String

    /**
     * 释放资源
     *
     * 清理客户端资源,如关闭 HTTP 连接等。
     */
    fun release()
}
