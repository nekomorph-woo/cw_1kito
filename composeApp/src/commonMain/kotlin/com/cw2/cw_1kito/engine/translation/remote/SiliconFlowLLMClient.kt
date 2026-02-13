package com.cw2.cw_1kito.engine.translation.remote

import com.cw2.cw_1kito.config.SystemMessageConfig
import com.cw2.cw_1kito.data.config.ConfigManager
import com.cw2.cw_1kito.engine.translation.ICloudLlmEngine
import com.cw2.cw_1kito.model.ChatCompletionRequest
import com.cw2.cw_1kito.model.ChatCompletionResponse
import com.cw2.cw_1kito.model.ChatMessage
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.LlmModel
import com.cw2.cw_1kito.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 硅基流动(SiliconFlow) LLM 客户端
 *
 * 使用自定义 Json 配置以忽略 API 响应中的未知字段。
 *
 * 实现云端 LLM 翻译引擎接口，使用普通 LLM（非视觉模型）进行纯文本翻译。
 *
 * ## 支持的模型
 * - 9 个普通模型：Qwen2.5-7B/72B、Qwen3-8B、GLM-4-9B/32B、GLM-4.5、DeepSeek-V3/R1 等
 * - 10 个 Thinking 模型：需要 enable_thinking=false 参数
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
class SiliconFlowLLMClient(
    private val configManager: ConfigManager,
    private val httpClient: HttpClient = defaultHttpClient()
) : ICloudLlmEngine {

    private var cachedApiKey: String? = null

    /**
     * 当前使用的模型
     */
    var currentModel: LlmModel = LlmModel.DEFAULT
        private set

    /**
     * 设置翻译模型
     *
     * @param model LLM 模型
     */
    fun setModel(model: LlmModel) {
        currentModel = model
        Logger.d("[SiliconFlowLLM] 模型已切换: ${model.displayName}")
    }

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
        Logger.i("[SiliconFlowLLM] API Key 已保存")
    }

    /**
     * 获取当前 API Key
     *
     * 优先从缓存读取，如果缓存为空则从 ConfigManager 读取。
     *
     * @return API Key，如果未设置则返回 null
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
                Logger.d("[SiliconFlowLLM] 验证 API Key...")

                // 构建测试请求
                val testRequest = ChatCompletionRequest.builder()
                    .model(LlmModel.DEFAULT.modelId)
                    .addMessage(ChatMessage.user("Hi"))
                    .temperature(0.7)
                    .maxTokens(10)
                    .build()

                // 发送请求
                val response = httpClient.post {
                    url(API_URL)
                    bearerAuth(apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(testRequest))
                    timeout {
                        requestTimeoutMillis = VALIDATION_TIMEOUT_MS
                    }
                }

                val isValid = response.status.value == HttpStatusCode.OK.value
                if (isValid) {
                    Logger.i("[SiliconFlowLLM] API Key 验证成功")
                } else {
                    Logger.w("[SiliconFlowLLM] API Key 验证失败: HTTP ${response.status.value}")
                }

                isValid

            } catch (e: Exception) {
                when {
                    isUnauthorizedError(e) -> {
                        Logger.w("[SiliconFlowLLM] API Key 无效: 401 Unauthorized")
                        false
                    }
                    else -> {
                        Logger.e(e, "[SiliconFlowLLM] API Key 验证异常")
                        false
                    }
                }
            }
        }
    }

    /**
     * 批量翻译文本
     *
     * 使用协程并发翻译多个文本，提高翻译效率。
     *
     * @param texts 要翻译的文本列表
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param customPrompt 自定义提示词（可选）
     * @param concurrency 并发数，由调用方传入（默认 8）
     * @return 翻译后的文本列表（顺序与输入一致）
     */
    override suspend fun translateBatch(
        texts: List<String>,
        sourceLang: Language,
        targetLang: Language,
        customPrompt: String?,
        model: LlmModel?,
        concurrency: Int
    ): List<String> {
        // 使用传入的模型或当前模型
        val useModel = model ?: currentModel

        if (texts.isEmpty()) return emptyList()
        if (texts.size == 1) {
            return listOf(translate(texts[0], sourceLang, targetLang, customPrompt, model))
        }

        val actualConcurrency = concurrency.coerceIn(1, 10)
        Logger.d("[SiliconFlowLLM] 批量翻译 ${texts.size} 个文本，并发数: $actualConcurrency，模型: ${useModel.displayName}")

        return coroutineScope {
            // 使用分块并发处理，避免同时发起过多请求
            val chunks = texts.chunked(actualConcurrency)
            val results = mutableListOf<String>()

            for ((chunkIndex, chunk) in chunks.withIndex()) {
                val chunkResults = chunk.mapIndexed { itemIndex, text ->
                    async(Dispatchers.IO) {
                        translate(text, sourceLang, targetLang, customPrompt, useModel)
                    }
                }.awaitAll()
                results.addAll(chunkResults)
                Logger.d("[SiliconFlowLLM] 批量翻译进度: ${results.size}/${texts.size}")
            }

            results
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
     * @param customPrompt 自定义提示词（可选）
     * @return 翻译后的文本
     * @throws com.cw2.cw_1kito.error.ApiKeyInvalidException API Key 无效
     * @throws com.cw2.cw_1kito.error.RateLimitException API 配额超限
     * @throws com.cw2.cw_1kito.error.NetworkException 网络错误
     * @throws com.cw2.cw_1kito.error.TranslationTimeoutException 翻译超时
     */
    override suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language,
        customPrompt: String?,
        model: LlmModel?
    ): String {
        // 使用传入的模型或当前模型
        val useModel = model ?: currentModel

        return withContext(Dispatchers.Default) {
            val apiKey = getApiKey()
                ?: throw com.cw2.cw_1kito.error.ApiKeyInvalidException("API Key 未设置")

            val startTime = System.currentTimeMillis()

            try {
                // 1. 构建翻译 Prompt
                val prompt = customPrompt ?: buildTranslationPrompt(text, sourceLang, targetLang)

                // 2. 构建请求（可选添加 system message）
                val requestBuilder = ChatCompletionRequest.builder()
                    .model(useModel.modelId)
                    .temperature(0.7)
                    .maxTokens(2000)
                    .stream(false)

                // 添加 system message（如果配置了的话）
                SystemMessageConfig.getDecodedSystemMessage()?.let { systemContent ->
                    requestBuilder.addMessage(ChatMessage.system(systemContent))
                }

                // 添加 user message
                requestBuilder.addMessage(ChatMessage.user(prompt))

                // Thinking 模型需要设置 enable_thinking=false
                if (useModel.isThinkingModel) {
                    requestBuilder.enableThinking(false)
                }

                val request = requestBuilder.build()

                Logger.translationStart(text.length, sourceLang.code, targetLang.code)

                // 3. 发送请求
                val response = httpClient.post {
                    url(API_URL)
                    bearerAuth(apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(request))
                    timeout {
                        requestTimeoutMillis = TRANSLATION_TIMEOUT_MS
                    }
                }

                // 4. 处理响应
                when (response.status.value) {
                    HttpStatusCode.OK.value -> {
                        val responseBody = response.bodyAsText()
                        val result = json.decodeFromString<ChatCompletionResponse>(responseBody)
                        val translatedText = result.content?.trim()
                            ?: throw com.cw2.cw_1kito.error.NetworkException("翻译结果为空")

                        val elapsed = System.currentTimeMillis() - startTime
                        Logger.translationSuccess(1, elapsed)

                        translatedText
                    }
                    401 -> {
                        Logger.e("[SiliconFlowLLM] API Key 无效: 401")
                        throw com.cw2.cw_1kito.error.ApiKeyInvalidException("API Key 无效或过期")
                    }
                    429 -> {
                        Logger.e("[SiliconFlowLLM] API 配额超限: 429")
                        throw com.cw2.cw_1kito.error.RateLimitException("API 调用频率超限，请稍后再试")
                    }
                    in 400..499 -> {
                        Logger.e("[SiliconFlowLLM] 客户端错误: ${response.status.value}")
                        throw com.cw2.cw_1kito.error.NetworkException("网络错误 (${response.status.value})")
                    }
                    in 500..599 -> {
                        Logger.e("[SiliconFlowLLM] 服务器错误: ${response.status.value}")
                        throw com.cw2.cw_1kito.error.NetworkException("服务器错误 (${response.status.value})，请稍后再试")
                    }
                    else -> {
                        Logger.e("[SiliconFlowLLM] 未知错误: ${response.status.value}")
                        throw com.cw2.cw_1kito.error.NetworkException("未知网络错误")
                    }
                }

            } catch (e: com.cw2.cw_1kito.error.ApiKeyInvalidException) {
                throw e
            } catch (e: com.cw2.cw_1kito.error.RateLimitException) {
                throw e
            } catch (e: HttpRequestTimeoutException) {
                val elapsed = System.currentTimeMillis() - startTime
                Logger.e(e, "[SiliconFlowLLM] 翻译超时: ${elapsed}ms")
                throw com.cw2.cw_1kito.error.TranslationTimeoutException("翻译请求超时，请检查网络连接")
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                Logger.e(e, "[SiliconFlowLLM] 翻译失败 (耗时 ${elapsed}ms)")
                throw com.cw2.cw_1kito.error.NetworkException("翻译失败: ${e.message}", e)
            }
        }
    }

    /**
     * 释放资源
     *
     * 清理缓存，HTTP Client 会自动管理连接。
     */
    override fun release() {
        cachedApiKey = null
        Logger.d("[SiliconFlowLLM] 客户端资源已释放")
    }

    /**
     * 构建翻译 Prompt
     *
     * 创建精确的翻译指令，要求模型只返回翻译结果。
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
            请将以下${sourceText}文本翻译成${targetLang.displayName}。只返回翻译结果，不要添加任何解释或说明。

            原文：
            $text
        """.trimIndent()
    }

    /**
     * 检查是否为认证错误
     */
    private fun isUnauthorizedError(e: Exception): Boolean {
        return e.message?.contains("401", ignoreCase = true) == true ||
                e.message?.contains("Unauthorized", ignoreCase = true) == true
    }

    companion object {
        private const val TAG = "SiliconFlowLLM"
        private const val API_URL = "https://api.siliconflow.cn/v1/chat/completions"

        /**
         * 自定义 JSON 配置
         * 忽略未知字段以兼容 API 响应中的额外字段（如 system_fingerprint）
         */
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

        /**
         * 验证请求超时时间（毫秒）
         * 默认 5 秒，快速验证 API Key 有效性
         */
        private const val VALIDATION_TIMEOUT_MS = 5_000L

        /**
         * 翻译请求超时时间（毫秒）
         * 默认 60 秒（1 分钟），本地 OCR 方案的云端 LLM 翻译可能需要较长时间
         * 注意：此超时仅用于 LLM 文本翻译，不影响 VLM 图像识别的超时设置
         */
        private const val TRANSLATION_TIMEOUT_MS = 60_000L

        /**
         * 创建默认 HTTP 客户端
         */
        private fun defaultHttpClient(): HttpClient {
            return HttpClient {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                    // JSON 序列化由我们手动处理
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = TRANSLATION_TIMEOUT_MS
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 30_000
                }
            }
        }
    }
}
