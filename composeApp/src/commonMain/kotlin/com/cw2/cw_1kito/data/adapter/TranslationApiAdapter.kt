package com.cw2.cw_1kito.data.adapter

import com.cw2.cw_1kito.data.api.SiliconFlowRequest
import com.cw2.cw_1kito.data.api.SiliconFlowResponse
import com.cw2.cw_1kito.data.api.TranslationApiRequest
import com.cw2.cw_1kito.data.api.ApiException
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.TranslationResult
import com.cw2.cw_1kito.model.VlmModel
import com.cw2.cw_1kito.model.BoundingBox

/**
 * 防腐层接口
 * 用于隔离外部 API 结构变化
 */
interface TranslationApiAdapter {

    /**
     * 将内部请求转换为外部 API 格式
     */
    fun toExternalRequest(request: TranslationApiRequest): SiliconFlowRequest

    /**
     * 将外部 API 响应转换为内部领域模型
     */
    fun toInternalResponse(
        response: SiliconFlowResponse,
        requestModel: VlmModel
    ): TranslationResponse

    /**
     * 构建翻译 Prompt
     */
    fun buildPrompt(targetLanguage: Language): String
}

/**
 * 内部领域响应模型
 */
data class TranslationResponse(
    val results: List<TranslationResult>,
    val resultId: String,
    val model: VlmModel,
    val usage: com.cw2.cw_1kito.data.api.TokenUsage? = null
) {
    /**
     * 判断响应是否有效
     */
    val isValid: Boolean
        get() = results.isNotEmpty() && results.all { it.isValid }

    /**
     * 获取结果数量
     */
    val resultCount: Int
        get() = results.size

    /**
     * 获取所有有效结果
     */
    val validResults: List<TranslationResult>
        get() = results.filter { it.isValid }
}

/**
 * JSON 解析异常
 */
class JsonExtractionException(message: String, cause: Throwable? = null) : Exception(message, cause)
