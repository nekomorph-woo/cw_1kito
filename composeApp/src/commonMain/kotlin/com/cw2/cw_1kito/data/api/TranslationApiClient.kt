package com.cw2.cw_1kito.data.api

import com.cw2.cw_1kito.model.VlmModel

/**
 * 翻译 API 客户端接口
 */
interface TranslationApiClient {

    /**
     * 发送翻译请求
     * @param request 翻译请求参数
     * @return 翻译响应结果
     * @throws ApiException 当 API 返回错误时抛出
     */
    suspend fun translate(request: TranslationApiRequest): SiliconFlowResponse

    /**
     * 验证 API Key 有效性
     * @param apiKey API Key
     * @return 是否有效
     */
    suspend fun validateApiKey(apiKey: String): Boolean

    /**
     * 设置 API Key
     */
    fun setApiKey(apiKey: String)

    /**
     * 获取当前 API Key
     */
    fun getApiKey(): String?
}

/**
 * API 异常
 */
sealed class ApiException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable?) : super(message, cause)

    data class NetworkError(val internalCause: Throwable) : ApiException("网络错误: ${internalCause.message}", internalCause)

    data class AuthError(val errorMsg: String) : ApiException("认证错误: $errorMsg")

    data class RateLimitError(val retryAfter: Long? = null) : ApiException("请求限流${retryAfter?.let { "，${it}秒后重试"} ?: ""}")

    data class ServerError(val code: Int, val errorMsg: String) : ApiException("服务器错误 ($code): $errorMsg")

    data class ParseError(val errorMsg: String) : ApiException("解析错误: $errorMsg")

    data class UnknownError(val errorMsg: String) : ApiException("未知错误: $errorMsg")
}

/**
 * API 常量
 */
object ApiConstants {
    const val BASE_URL = "https://api.siliconflow.cn/v1/chat/completions"
    const val TIMEOUT_MS = 300_000L // 5 分钟
    const val MAX_RETRIES = 3
    const val DEFAULT_TEMPERATURE = 0.7
    const val DEFAULT_MAX_TOKENS = 2000
}
