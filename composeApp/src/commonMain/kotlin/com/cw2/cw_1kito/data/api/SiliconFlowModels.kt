package com.cw2.cw_1kito.data.api

import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.VlmModel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 硅基流动 API 请求模型
 */

/**
 * 外部 API 请求格式（硅基流动）
 */
@Serializable
data class SiliconFlowRequest(
    val model: String,
    val messages: List<SiliconFlowMessage>,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int = 2000,
    val stream: Boolean = false
)

/**
 * API 消息
 */
@Serializable
data class SiliconFlowMessage(
    val role: String,
    val content: List<SiliconFlowContent>
)

/**
 * 消息内容（支持文本和图像）
 */
@Serializable
data class SiliconFlowContent(
    val type: String,
    val text: String? = null,
    @SerialName("image_url")
    val imageUrl: ImageUrl? = null
)

/**
 * 图像 URL（支持 base64）
 */
@Serializable
data class ImageUrl(
    val url: String
)

/**
 * 硅基流动 API 响应模型
 */

/**
 * 外部 API 响应格式（硅基流动）
 */
@Serializable
data class SiliconFlowResponse(
    val id: String,
    val choices: List<SiliconFlowChoice>,
    val created: Long? = null,
    val model: String? = null,
    @SerialName("usage")
    val usage: SiliconFlowUsage? = null
)

/**
 * API 选择项
 */
@Serializable
data class SiliconFlowChoice(
    val index: Int,
    val message: SiliconFlowMessageResponse,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

/**
 * 消息响应
 */
@Serializable
data class SiliconFlowMessageResponse(
    val role: String,
    val content: String
)

/**
 * Token 使用统计
 */
@Serializable
data class SiliconFlowUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

/**
 * API 错误响应
 */
@Serializable
data class SiliconFlowErrorResponse(
    val error: SiliconFlowError
)

@Serializable
data class SiliconFlowError(
    val message: String,
    val type: String? = null,
    val code: String? = null
)

/**
 * 外部翻译结果（从 LLM 返回的 JSON 解析）
 */
@Serializable
data class ExternalTranslationResult(
    @SerialName("original_text")
    val originalText: String,
    @SerialName("translated_text")
    val translatedText: String,
    val coordinates: List<Int>
)

/**
 * 内部 API 请求参数（转换前的格式）
 */
@Serializable
data class TranslationApiRequest(
    val model: VlmModel,
    val imageData: String,        // Base64 编码的图像
    val targetLanguage: Language,
    val sourceLanguage: Language = Language.AUTO,
    val temperature: Double = 0.7,
    @SerialName("max_tokens")
    val maxTokens: Int = 2000,
    val imageWidth: Int = 0,      // 图像实际宽度（像素）
    val imageHeight: Int = 0,     // 图像实际高度（像素）
    val customPrompt: String? = null,  // 自定义提示词（null 使用默认）
    @SerialName("use_merging_prompt")
    val useMergingPrompt: Boolean = false  // 是否使用文本合并提示词
)

/**
 * Token 使用统计（内部格式）
 */
@Serializable
data class TokenUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
) {
    companion object {
        fun fromExternal(usage: SiliconFlowUsage): TokenUsage {
            return TokenUsage(
                promptTokens = usage.promptTokens,
                completionTokens = usage.completionTokens,
                totalTokens = usage.totalTokens
            )
        }
    }
}

/**
 * 流式响应 chunk
 */
@Serializable
data class SiliconFlowStreamChunk(
    val id: String,
    val choices: List<SiliconFlowStreamChoice>,
    val model: String? = null
)

@Serializable
data class SiliconFlowStreamChoice(
    val index: Int,
    val delta: SiliconFlowDelta,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class SiliconFlowDelta(
    val role: String? = null,
    val content: String? = null
)
