package com.cw2.cw_1kito.engine.translation.remote

import com.cw2.cw_1kito.data.config.ConfigManager
import com.cw2.cw_1kito.error.*
import com.cw2.cw_1kito.model.ChatCompletionRequest
import com.cw2.cw_1kito.model.ChatCompletionResponse
import com.cw2.cw_1kito.model.ChatMessage
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.util.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 硅基流动(SiliconFlow) API 客户端
 *
 * 实现远程翻译引擎接口,提供基于云端的文本翻译服务。
 *
 * ## API 认证
 * - 使用 Bearer Token 认证
 * - API Key 通过 ConfigManager 加密存储
 *
 * ## 错误处理
 * - 401 → ApiKeyInvalidException
 * - 429 → RateLimitException
 * - 网络错误 → NetworkException
 * - 超时 → TranslationTimeoutException
 *
 * @property configManager 配置管理器
 * @property httpClient HTTP 客户端
 *
 * @constructor 创建客户端实例
 */
class SiliconFlowClient(
    private val configManager: ConfigManager,
    private val httpClient: HttpClient = defaultHttpClient()
) : IRemoteTranslationEngine {

    private var cachedApiKey: String? = null

    /**
     * 设置 API Key
     *
     * 使用 ConfigManager 加密存储 API Key。
     *
     * @param apiKey API 密钥
     */
    override suspend fun setApiKey(apiKey: String) {
        configManager.saveApiKey(apiKey)
        cachedApiKey = apiKey
        Logger.i("[SiliconFlow] API Key 已保存")
    }

    /**
     * 获取当前 API Key
     *
     * 优先从缓存读取,如果缓存为空则从 ConfigManager 读取。
     *
     * @return API Key,如果未设置则返回 null
     */
    override suspend fun getApiKey(): String? {
        if (cachedApiKey == null) {
            cachedApiKey = configManager.getApiKey()
        }
        return cachedApiKey
    }

    /**
     * 检查是否已设置 API Key
     *
     * @return 是否已设置
     */
    override suspend fun hasApiKey(): Boolean {
        return getApiKey() != null
    }

    /**
     * 验证 API Key 有效性
     *
     * 发送测试请求到服务器验证 API Key 是否有效。
     * 使用小模型进行快速验证。
     *
     * @param apiKey 要验证的 API Key
     * @return 是否有效
     */
    override suspend fun validateApiKey(apiKey: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d("[SiliconFlow] 验证 API Key...")

                // 构建测试请求
                val testRequest = ChatCompletionRequest.builder()
                    .model(DEFAULT_MODEL)
                    .addMessage(ChatMessage.user("Hi"))
                    .temperature(0.7)
                    .maxTokens(10)
                    .build()

                // 发送请求
                val response = httpClient.post {
                    url(API_URL)
                    bearerAuth(apiKey)
                    setBody(testRequest)
                    timeout {
                        requestTimeoutMillis = VALIDATION_TIMEOUT_MS
                    }
                }

                val isValid = response.status.value == HttpStatusCode.OK.value
                if (isValid) {
                    Logger.i("[SiliconFlow] API Key 验证成功")
                } else {
                    Logger.w("[SiliconFlow] API Key 验证失败: HTTP ${response.status.value}")
                }

                isValid

            } catch (e: ClientRequestException) {
                when (e.response.status.value) {
                    401 -> {
                        Logger.w("[SiliconFlow] API Key 无效: 401 Unauthorized")
                        false
                    }
                    else -> {
                        Logger.e(e, "[SiliconFlow] API Key 验证失败: ${e.message}")
                        false
                    }
                }
            } catch (e: Exception) {
                Logger.e(e, "[SiliconFlow] API Key 验证异常")
                false
            }
        }
    }

    /**
     * 翻译文本
     *
     * 使用硅基流动 Chat Completions API 进行文本翻译。
     *
     * @param text 要翻译的文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 翻译后的文本
     * @throws ApiKeyInvalidException API Key 无效
     * @throws RateLimitException API 配额超限
     * @throws NetworkException 网络错误
     * @throws TranslationTimeoutException 翻译超时
     */
    override suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String {
        return withContext(Dispatchers.Default) {
            val apiKey = getApiKey()
                ?: throw ApiKeyInvalidException("API Key 未设置")

            val startTime = System.currentTimeMillis()

            try {
                // 1. 构建翻译 Prompt
                val prompt = buildTranslationPrompt(text, sourceLang, targetLang)

                // 2. 构建请求
                val request = ChatCompletionRequest.builder()
                    .model(DEFAULT_MODEL)
                    .addMessage(ChatMessage.user(prompt))
                    .temperature(0.7)
                    .maxTokens(2000)
                    .stream(false)  // 批量模式,非流式
                    .build()

                Logger.translationStart(1)

                // 3. 发送请求
                val response = httpClient.post {
                    url(API_URL)
                    bearerAuth(apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                    timeout {
                        requestTimeoutMillis = TRANSLATION_TIMEOUT_MS
                    }
                }

                // 4. 解析响应
                val result: ChatCompletionResponse = response.body()
                val translatedText = result.content?.trim()
                    ?: throw NetworkException("翻译结果为空")

                val elapsed = System.currentTimeMillis() - startTime
                Logger.translationSuccess(1, elapsed)

                translatedText

            } catch (e: ClientRequestException) {
                val statusCode = e.response.status.value
                val elapsed = System.currentTimeMillis() - startTime

                when (statusCode) {
                    401 -> {
                        Logger.e(e, "[SiliconFlow] API Key 无效: 401")
                        throw ApiKeyInvalidException("API Key 无效或过期")
                    }
                    429 -> {
                        Logger.e(e, "[SiliconFlow] API 配额超限: 429")
                        throw RateLimitException("API 调用频率超限,请稍后再试")
                    }
                    in 400..499 -> {
                        Logger.e(e, "[SiliconFlow] 客户端错误: $statusCode")
                        throw NetworkException("网络错误 ($statusCode): ${e.message}", e)
                    }
                    in 500..599 -> {
                        Logger.e(e, "[SiliconFlow] 服务器错误: $statusCode")
                        throw NetworkException("服务器错误 ($statusCode),请稍后再试", e)
                    }
                    else -> {
                        Logger.e(e, "[SiliconFlow] 未知错误")
                        throw NetworkException("未知网络错误: ${e.message}", e)
                    }
                }
            } catch (e: HttpRequestTimeoutException) {
                val elapsed = System.currentTimeMillis() - startTime
                Logger.e(e, "[SiliconFlow] 翻译超时: ${elapsed}ms")
                throw TranslationTimeoutException("翻译请求超时,请检查网络连接")
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                Logger.e(e, "[SiliconFlow] 翻译失败 (耗时 ${elapsed}ms)")
                throw NetworkException("翻译失败: ${e.message}", e)
            }
        }
    }

    /**
     * 释放资源
     *
     * 清理缓存,HTTP Client 会自动管理连接。
     */
    override fun release() {
        cachedApiKey = null
        Logger.d("[SiliconFlow] 客户端资源已释放")
    }

    /**
     * 构建翻译 Prompt
     *
     * 创建精确的翻译指令,要求模型只返回翻译结果。
     *
     * @param text 原文
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 翻译 Prompt
     */
    private fun buildTranslationPrompt(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String {
        val sourceText = if (sourceLang == Language.AUTO) "自动检测的语言" else sourceLang.displayName
        return """
            请将以下${sourceText}文本翻译为${targetLang.displayName}。
            只返回翻译结果,不要添加任何解释或额外内容。

            原文:
            $text

            翻译:
        """.trimIndent()
    }

    companion object {
        private const val TAG = "SiliconFlowClient"
        private const val API_URL = "https://api.siliconflow.cn/v1/chat/completions"

        /**
         * 默认模型
         */
        const val DEFAULT_MODEL = "Qwen/Qwen2.5-7B-Instruct"

        /**
         * 验证请求超时时间(毫秒)
         * 默认 5 秒,快速验证 API Key 有效性
         */
        private const val VALIDATION_TIMEOUT_MS = 5_000L

        /**
         * 翻译请求超时时间(毫秒)
         * 默认 5 秒,避免用户等待过久
         */
        private const val TRANSLATION_TIMEOUT_MS = 5_000L

        /**
         * 创建默认 HTTP 客户端
         */
        private fun defaultHttpClient(): HttpClient {
            return HttpClient {
                // Content Negotiation (Kotlinx Serialization)
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    // JSON 序列化由我们手动处理,不需要 ContentNegotiation
                }

                // 超时配置
                install(HttpTimeout) {
                    requestTimeoutMillis = TRANSLATION_TIMEOUT_MS
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 30_000
                }

                // 日志(可选)
                // install(Logging) {
                //     level = LogLevel.INFO
                // }
            }
        }
    }
}

/**
 * JSON 序列化配置
 */
private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = false
}
