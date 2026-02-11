package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 单个翻译结果
 */
@Serializable
data class TranslationResult(
    /** 原始文本 */
    val originalText: String,

    /** 翻译后的文本 */
    val translatedText: String,

    /** 原文边界框（归一化坐标 0-1） */
    val boundingBox: BoundingBox,

    /** 源语言（如果检测到） */
    val detectedLanguage: Language? = null
) {
    /** 检查结果是否有效 */
    val isValid: Boolean
        get() = originalText.isNotBlank() && translatedText.isNotBlank()

    /** 文本长度变化比例 */
    val lengthChangeRatio: Float
        get() = if (originalText.isNotEmpty()) {
            translatedText.length.toFloat() / originalText.length
        } else {
            1f
        }

    companion object {
        /**
         * 创建空的翻译结果
         */
        val EMPTY = TranslationResult(
            originalText = "",
            translatedText = "",
            boundingBox = BoundingBox.EMPTY
        )
    }
}

/**
 * 翻译响应（包含多个结果）
 */
@Serializable
data class TranslationResponse(
    /** 所有翻译结果 */
    val results: List<TranslationResult>,

    /** 响应 ID（用于追踪） */
    val resultId: String,

    /** 使用的模型 */
    val model: VlmModel,

    /** Token 使用统计 */
    val usage: TokenUsage? = null
) {
    /** 有效结果数量 */
    val validResultCount: Int
        get() = results.count { it.isValid }

    /** 所有有效结果 */
    val validResults: List<TranslationResult>
        get() = results.filter { it.isValid }

    /** 是否有结果 */
    val hasResults: Boolean
        get() = validResults.isNotEmpty()
}

/**
 * Token 使用统计
 */
@Serializable
data class TokenUsage(
    /** 输入 Token 数量 */
    val promptTokens: Int,

    /** 输出 Token 数量 */
    val completionTokens: Int,

    /** 总 Token 数量 */
    val totalTokens: Int
) {
    /** 计算 Token 成本比例（输出/输入） */
    val costRatio: Float
        get() = if (promptTokens > 0) {
            completionTokens.toFloat() / promptTokens
        } else {
            0f
        }
}
