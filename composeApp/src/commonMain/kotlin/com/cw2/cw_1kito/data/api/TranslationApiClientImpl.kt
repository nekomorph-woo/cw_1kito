package com.cw2.cw_1kito.data.api

import com.cw2.cw_1kito.data.api.ApiException.AuthError
import com.cw2.cw_1kito.data.api.ApiException.NetworkError
import com.cw2.cw_1kito.data.api.ApiException.ParseError
import com.cw2.cw_1kito.data.api.ApiException.RateLimitError
import com.cw2.cw_1kito.data.api.ApiException.ServerError
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlin.math.pow

/**
 * Ktor HTTP 客户端实现
 */
class TranslationApiClientImpl(
    private val baseUrl: String = ApiConstants.BASE_URL,
    private val timeoutMs: Long = ApiConstants.TIMEOUT_MS,
    private val maxRetries: Int = ApiConstants.MAX_RETRIES
) : TranslationApiClient {

    private var currentApiKey: String? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = false
    }

    private val client = HttpClient {
        expectSuccess = false // 我们自己处理错误响应

        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = timeoutMs
            connectTimeoutMillis = timeoutMs / 3
            socketTimeoutMillis = timeoutMs
        }
    }

    override fun setApiKey(apiKey: String) {
        this.currentApiKey = apiKey
    }

    override fun getApiKey(): String? = currentApiKey

    override suspend fun translate(request: TranslationApiRequest): SiliconFlowResponse {
        val apiKey = currentApiKey ?: throw AuthError("API Key 未设置")

        val siliconRequest = SiliconFlowRequest(
            model = request.model.id,
            messages = listOf(
                SiliconFlowMessage(
                    role = "user",
                    content = buildList {
                        add(
                            SiliconFlowContent(
                                type = "text",
                                text = buildPrompt(request.targetLanguage, request.imageWidth, request.imageHeight, request.customPrompt)
                            )
                        )
                        add(
                            SiliconFlowContent(
                                type = "image_url",
                                imageUrl = ImageUrl(
                                    url = "data:image/jpeg;base64,${request.imageData}"
                                )
                            )
                        )
                    }
                )
            ),
            temperature = request.temperature,
            maxTokens = request.maxTokens
        )

        return executeWithRetry {
            client.post(baseUrl) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(siliconRequest)
            }.let { response ->
                handleResponse(response)
            }
        }
    }

    override suspend fun validateApiKey(apiKey: String): Boolean {
        return try {
            currentApiKey = apiKey
            val testRequest = TranslationApiRequest(
                model = com.cw2.cw_1kito.model.VlmModel.DEFAULT,
                imageData = createMinimalImageBase64(),
                targetLanguage = com.cw2.cw_1kito.model.Language.ZH,
                maxTokens = 10 // 最小 token 数
            )
            translate(testRequest)
            true
        } catch (e: AuthError) {
            false
        } catch (e: Exception) {
            // 其他错误不认为是 Key 无效
            true
        }
    }

    /**
     * 带重试的请求执行
     */
    private suspend fun <T> executeWithRetry(
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: RateLimitError) {
                lastException = e
                val delayMs = e.retryAfter?.let { it * 1000L } ?: calculateBackoff(attempt)
                delay(delayMs)
            } catch (e: NetworkError) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    delay(calculateBackoff(attempt))
                }
            } catch (e: ServerError) {
                // 服务器错误通常不需要重试
                throw e
            }
        }

        throw lastException ?: NetworkError(Exception("未知错误"))
    }

    /**
     * 计算退避时间（指数退避）
     */
    private fun calculateBackoff(attempt: Int): Long {
        return (2.0.pow(attempt.toDouble()) * 1000).toLong().coerceAtMost(5000)
    }

    /**
     * 处理 HTTP 响应
     */
    private suspend fun handleResponse(response: HttpResponse): SiliconFlowResponse {
        return when (response.status) {
            HttpStatusCode.OK -> {
                try {
                    response.body<SiliconFlowResponse>()
                } catch (e: Exception) {
                    throw ParseError("解析响应失败: ${e.message}")
                }
            }
            HttpStatusCode.Unauthorized -> {
                throw AuthError("API Key 无效或已过期")
            }
            HttpStatusCode.TooManyRequests -> {
                val retryAfter = response.headers["Retry-After"]?.toLongOrNull()
                throw RateLimitError(retryAfter)
            }
            in HttpStatusCode.InternalServerError..HttpStatusCode.InternalServerError -> {
                throw ServerError(response.status.value, "服务器内部错误")
            }
            HttpStatusCode.BadGateway -> {
                throw ServerError(response.status.value, "网关错误")
            }
            HttpStatusCode.ServiceUnavailable -> {
                throw ServerError(response.status.value, "服务暂时不可用")
            }
            else -> {
                val errorBody = response.bodyAsText()
                throw ServerError(response.status.value, errorBody)
            }
        }
    }

    /**
     * 构建翻译 Prompt
     *
     * 如果提供了 customPrompt，则使用自定义 prompt 并替换模板变量：
     * - {{targetLanguage}} -> 目标语言名称
     * - {{imageWidth}} -> 图像宽度
     * - {{imageHeight}} -> 图像高度
     */
    private fun buildPrompt(
        targetLanguage: com.cw2.cw_1kito.model.Language,
        imageWidth: Int,
        imageHeight: Int,
        customPrompt: String? = null
    ): String {
        val template = customPrompt ?: DEFAULT_PROMPT
        return template
            .replace("{{targetLanguage}}", targetLanguage.displayName)
            .replace("{{imageWidth}}", imageWidth.toString())
            .replace("{{imageHeight}}", imageHeight.toString())
    }

    /**
     * 创建最小化的测试图像（1x1 像素 PNG 的 base64）
     */
    private fun createMinimalImageBase64(): String {
        // 最小的 1x1 透明 PNG 的 base64
        return "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg=="
    }

    companion object {
        /**
         * 默认提示词模板
         *
         * 支持的模板变量：
         * - {{targetLanguage}} - 目标语言
         * - {{imageWidth}} - 图像宽度（像素）
         * - {{imageHeight}} - 图像高度（像素）
         */
        const val DEFAULT_PROMPT = "You are an OCR and translation engine. The image resolution is {{imageWidth}}x{{imageHeight}} pixels.\n" +
            "\n" +
            "Your task:\n" +
            "1. Extract **ALL** visible text in the image — do NOT skip any text block, even small ones.\n" +
            "2. Translate each text block to {{targetLanguage}}.\n" +
            "3. For each text block, provide its bounding box as pixel coordinates [left, top, right, bottom] within the {{imageWidth}}x{{imageHeight}} image.\n" +
            "\n" +
            "Rules:\n" +
            "- You MUST extract every piece of text you can see, including speech bubbles, captions, signs, labels, watermarks, etc.\n" +
            "- SKIP only: page numbers, single digits/letters that are clearly decorative, and UI navigation elements (like scrollbar, status bar).\n" +
            "- Merge text that belongs to the same logical block (e.g., a multi-line paragraph in one speech bubble = one entry).\n" +
            "- Coordinates must be pixel values: left >= 0, top >= 0, right <= {{imageWidth}}, bottom <= {{imageHeight}}.\n" +
            "- The bounding box should tightly wrap the original text region.\n" +
            "\n" +
            "Return ONLY a valid JSON array, no markdown, no explanation:\n" +
            "[{\"original_text\":\"...\",\"translated_text\":\"...\",\"coordinates\":[left,top,right,bottom]}]"
    }
}
