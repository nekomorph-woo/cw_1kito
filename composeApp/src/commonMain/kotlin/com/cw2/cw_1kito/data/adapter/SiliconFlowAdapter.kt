package com.cw2.cw_1kito.data.adapter

import com.cw2.cw_1kito.config.SystemMessageConfig
import com.cw2.cw_1kito.data.api.*
import com.cw2.cw_1kito.model.BoundingBox
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.TranslationResult
import com.cw2.cw_1kito.model.VlmModel
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * 硅基流动 API 适配器
 * 实现防腐层模式，隔离外部 API 结构变化
 */
class SiliconFlowAdapter : TranslationApiAdapter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

    override fun toExternalRequest(request: TranslationApiRequest): SiliconFlowRequest {
        // 构建消息列表，可选添加 system message
        val messages = mutableListOf<SiliconFlowMessage>()

        // 添加 system message（如果配置了的话）
        SystemMessageConfig.getDecodedSystemMessage()?.let { systemContent ->
            messages.add(
                SiliconFlowMessage(
                    role = "system",
                    content = listOf(
                        SiliconFlowContent(
                            type = "text",
                            text = systemContent
                        )
                    )
                )
            )
        }

        // 添加 user message
        messages.add(
            SiliconFlowMessage(
                role = "user",
                content = listOf(
                    SiliconFlowContent(
                        type = "text",
                        text = buildPrompt(request.targetLanguage)
                    ),
                    SiliconFlowContent(
                        type = "image_url",
                        imageUrl = ImageUrl(
                            url = "data:image/jpeg;base64,${request.imageData}"
                        )
                    )
                )
            )
        )

        return SiliconFlowRequest(
            model = request.model.id,
            messages = messages,
            temperature = request.temperature,
            maxTokens = request.maxTokens,
            stream = false
        )
    }

    override fun toInternalResponse(
        response: SiliconFlowResponse,
        requestModel: VlmModel
    ): TranslationResponse {
        val content = response.choices.firstOrNull()?.message?.content
            ?: throw ApiException.ParseError("API 响应中没有内容")

        // 多级容错解析
        val results = try {
            parseTranslationResults(content)
        } catch (e: JsonExtractionException) {
            throw ApiException.ParseError("无法解析翻译结果: ${e.message}")
        }

        return TranslationResponse(
            results = results,
            resultId = response.id,
            model = requestModel,
            usage = response.usage?.let { TokenUsage.fromExternal(it) }
        )
    }

    override fun buildPrompt(targetLanguage: Language): String {
        return """
            Identify all text in this image. For each text block, provide:
            1. The original text as it appears
            2. The translation to ${targetLanguage.displayName}
            3. The bounding box coordinates normalized to 0-1000 scale as [left, top, right, bottom]

            Return the results strictly in this JSON format:
            [
              {
                "original_text": "...",
                "translated_text": "...",
                "coordinates": [left, top, right, bottom]
              }
            ]

            Only return valid JSON, no additional text.
        """.trimIndent()
    }

    /**
     * 多级容错解析翻译结果
     *
     * 解析顺序：
     * 1. 直接解析为 JSON 数组
     * 2. 移除 Markdown 代码块包裹后解析
     * 3. 使用正则表达式提取 JSON 数组
     */
    private fun parseTranslationResults(content: String): List<TranslationResult> {
        // 1. 尝试直接解析
        try {
            val externalResults = json.decodeFromString<List<ExternalTranslationResult>>(content)
            return externalResults.map { it.toTranslationResult() }
        } catch (e: SerializationException) {
            // 继续尝试下一种方法
        }

        // 2. 尝试移除 Markdown 代码块包裹
        val extractedContent = extractJsonFromMarkdown(content)
        if (extractedContent != content) {
            try {
                val externalResults = json.decodeFromString<List<ExternalTranslationResult>>(extractedContent)
                return externalResults.map { it.toTranslationResult() }
            } catch (e: SerializationException) {
                // 继续尝试下一种方法
            }
        }

        // 3. 尝试使用正则表达式提取 JSON 数组
        val jsonMatch = JSON_ARRAY_REGEX.find(content)
        if (jsonMatch != null) {
            try {
                val jsonString = jsonMatch.groupValues[1]
                val externalResults = json.decodeFromString<List<ExternalTranslationResult>>(jsonString)
                return externalResults.map { it.toTranslationResult() }
            } catch (e: SerializationException) {
                // 继续尝试下一种方法
            }
        }

        // 4. 尝试提取 JSON 对象（单个结果的情况）
        val jsonObjectMatch = JSON_OBJECT_REGEX.find(content)
        if (jsonObjectMatch != null) {
            try {
                val jsonString = jsonObjectMatch.groupValues[1]
                val externalResult = json.decodeFromString<ExternalTranslationResult>(jsonString)
                return listOf(externalResult.toTranslationResult())
            } catch (e: SerializationException) {
                // 最终失败
            }
        }

        throw JsonExtractionException("无法从内容中提取有效的 JSON: ${content.take(100)}...")
    }

    /**
     * 从 Markdown 代码块中提取 JSON
     */
    private fun extractJsonFromMarkdown(content: String): String {
        var trimmed = content.trim()

        // 移除 ```json 或 ``` 包裹
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.removePrefix("```json").trim()
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.removePrefix("```").trim()
        }

        if (trimmed.endsWith("```")) {
            trimmed = trimmed.removeSuffix("```").trim()
        }

        // 尝试找到第一个 [ 和最后一个 ]
        val firstBracket = trimmed.indexOf('[')
        val lastBracket = trimmed.lastIndexOf(']')

        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            return trimmed.substring(firstBracket, lastBracket + 1)
        }

        return trimmed
    }

    companion object {
        /**
         * JSON 数组正则表达式
         */
        private val JSON_ARRAY_REGEX = """\[\s*\{.*?\}\s*(?:,\s*\{.*?\}\s*)*\]""".toRegex(
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)
        )

        /**
         * JSON 对象正则表达式（用于单个结果）
         */
        private val JSON_OBJECT_REGEX = """\{\s*"original_text"\s*:.*?\}""".toRegex(
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE)
        )
    }
}

/**
 * 外部翻译结果扩展函数
 */
private fun ExternalTranslationResult.toTranslationResult(): TranslationResult {
    return TranslationResult(
        originalText = originalText,
        translatedText = translatedText,
        boundingBox = BoundingBox.fromExternal(coordinates)
    )
}
