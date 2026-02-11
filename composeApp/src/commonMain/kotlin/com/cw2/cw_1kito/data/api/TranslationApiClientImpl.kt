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
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
        val siliconRequest = buildSiliconFlowRequest(request, stream = false)

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

    override fun translateStream(request: TranslationApiRequest): Flow<String> = flow {
        val apiKey = currentApiKey ?: throw AuthError("API Key 未设置")
        val siliconRequest = buildSiliconFlowRequest(request, stream = true)

        client.preparePost(baseUrl) {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            setBody(siliconRequest)
        }.execute { response ->
            if (response.status != HttpStatusCode.OK) {
                handleStreamErrorResponse(response)
            }

            val channel: ByteReadChannel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                // 忽略 SSE 注释行（常用作 keep-alive ping）
                if (line.startsWith(":")) continue
                // 忽略空行和非 data 行
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                val chunk = json.decodeFromString<SiliconFlowStreamChunk>(data)
                val content = chunk.choices.firstOrNull()?.delta?.content
                if (content != null) emit(content)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun validateApiKey(apiKey: String): Boolean {
        return try {
            currentApiKey = apiKey
            val testRequest = TranslationApiRequest(
                model = com.cw2.cw_1kito.model.VlmModel.DEFAULT,
                imageData = createMinimalImageBase64(),
                targetLanguage = com.cw2.cw_1kito.model.Language.ZH,
                maxTokens = 10
            )
            translate(testRequest)
            true
        } catch (e: AuthError) {
            false
        } catch (e: Exception) {
            true
        }
    }

    /**
     * 构建 SiliconFlow API 请求体（流式和非流式共用）
     */
    private fun buildSiliconFlowRequest(request: TranslationApiRequest, stream: Boolean = false): SiliconFlowRequest {
        return SiliconFlowRequest(
            model = request.model.id,
            messages = listOf(
                SiliconFlowMessage(
                    role = "user",
                    content = buildList {
                        add(
                            SiliconFlowContent(
                                type = "text",
                                text = buildPrompt(
                                    request.targetLanguage,
                                    request.imageWidth,
                                    request.imageHeight,
                                    request.customPrompt,
                                    request.useMergingPrompt
                                )
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
            maxTokens = request.maxTokens,
            stream = stream
        )
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
     * 处理 HTTP 响应（非流式）
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
     * 处理流式请求的错误响应
     */
    private suspend fun handleStreamErrorResponse(response: HttpResponse) {
        when (response.status) {
            HttpStatusCode.Unauthorized -> throw AuthError("API Key 无效或已过期")
            HttpStatusCode.TooManyRequests -> {
                val retryAfter = response.headers["Retry-After"]?.toLongOrNull()
                throw RateLimitError(retryAfter)
            }
            else -> {
                val errorBody = response.bodyAsText()
                throw ServerError(response.status.value, errorBody)
            }
        }
    }

    /**
     * 构建翻译 Prompt
     */
    private fun buildPrompt(
        targetLanguage: com.cw2.cw_1kito.model.Language,
        imageWidth: Int,
        imageHeight: Int,
        customPrompt: String? = null,
        useMergingPrompt: Boolean = false
    ): String {
        val template = when {
            customPrompt != null -> customPrompt
            useMergingPrompt -> DEFAULT_MERGING_PROMPT
            else -> DEFAULT_PROMPT
        }
        return template
            .replace("{{targetLanguage}}", targetLanguage.displayName)
            .replace("{{imageWidth}}", imageWidth.toString())
            .replace("{{imageHeight}}", imageHeight.toString())
    }

    /**
     * 创建最小化的测试图像（1x1 像素 PNG 的 base64）
     */
    private fun createMinimalImageBase64(): String {
        return "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg=="
    }

    companion object {
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

        const val DEFAULT_MERGING_PROMPT = "You are an OCR and translation engine with text merging capability. " +
            "The image resolution is {{imageWidth}}x{{imageHeight}} pixels.\n" +
            "\n" +
            "Your task:\n" +
            "1. Extract ALL visible text in the image.\n" +
            "2. Merge nearby text blocks that belong to the same logical content:\n" +
            "   - Text in the same paragraph/section\n" +
            "   - Text in the same speech bubble or UI container\n" +
            "   - Text that flows across multiple lines naturally\n" +
            "3. Translate each merged text block to {{targetLanguage}}.\n" +
            "4. Provide bounding box that covers the entire merged region.\n" +
            "\n" +
            "Rules:\n" +
            "- MERGE text blocks that are spatially close and logically related\n" +
            "- Keep separate text blocks that are clearly independent (e.g., different UI elements)\n" +
            "- Coordinates must be pixel values covering the full merged region\n" +
            "- SKIP: page numbers, decorative single characters, navigation elements\n" +
            "\n" +
            "Return ONLY a valid JSON array, no markdown:\n" +
            "[{\"original_text\":\"...\",\"translated_text\":\"...\",\"coordinates\":[left,top,right,bottom]}]"
    }
}
