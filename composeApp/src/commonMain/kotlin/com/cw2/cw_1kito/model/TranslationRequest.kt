package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 翻译请求
 * 封装翻译所需的参数和选项
 */
@Serializable
data class TranslationRequest(
    /** 要翻译的文本 */
    val text: String,

    /** 源语言 */
    val sourceLanguage: Language,

    /** 目标语言 */
    val targetLanguage: Language,

    /** 翻译选项（可选） */
    val options: TranslationOptions? = null
) {
    init {
        require(text.isNotBlank()) {
            "待翻译文本不能为空"
        }
        require(sourceLanguage != targetLanguage) {
            "源语言和目标语言不能相同: ${sourceLanguage.code} -> ${targetLanguage.code}"
        }
    }

    /** 文本长度 */
    val textLength: Int get() = text.length

    /** 检查是否为批量翻译（文本包含多个句子） */
    val isBatchTranslation: Boolean get() = text.length > 100 || text.lines().size > 1

    companion object {
        /**
         * 创建简单翻译请求
         */
        fun simple(
            text: String,
            sourceLanguage: Language,
            targetLanguage: Language
        ): TranslationRequest {
            return TranslationRequest(
                text = text,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                options = null
            )
        }

        /**
         * 创建带超时的翻译请求
         */
        fun withTimeout(
            text: String,
            sourceLanguage: Language,
            targetLanguage: Language,
            timeoutMs: Int
        ): TranslationRequest {
            return TranslationRequest(
                text = text,
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                options = TranslationOptions(timeout = timeoutMs)
            )
        }

        /**
         * 创建批量翻译请求
         */
        fun batch(
            texts: List<String>,
            sourceLanguage: Language,
            targetLanguage: Language,
            options: TranslationOptions? = null
        ): List<TranslationRequest> {
            return texts.map { text ->
                TranslationRequest(text, sourceLanguage, targetLanguage, options)
            }
        }
    }
}

/**
 * 翻译选项
 * 控制翻译行为的各种参数
 */
@Serializable
data class TranslationOptions(
    /** 超时时间（毫秒），默认 5 秒 */
    val timeout: Int = 5000,

    /** 是否启用缓存，默认 true */
    val enableCache: Boolean = true,

    /** 是否启用自动纠错，默认 false */
    val enableAutoCorrect: Boolean = false,

    /** 最大重试次数，默认 1 */
    val maxRetries: Int = 1,

    /** 是否跳过已翻译内容，默认 false */
    val skipTranslated: Boolean = false,

    /** 自定义模型参数（可选） */
    val modelParams: Map<String, String> = emptyMap()
) {
    init {
        require(timeout in 100..60000) {
            "超时时间必须在 100ms 到 60000ms 之间，实际: $timeout"
        }
        require(maxRetries in 0..5) {
            "最大重试次数必须在 0 到 5 之间，实际: $maxRetries"
        }
    }

    companion object {
        /** 默认选项 */
        val DEFAULT = TranslationOptions()

        /** 快速选项（1 秒超时，无重试） */
        val FAST = TranslationOptions(
            timeout = 1000,
            maxRetries = 0,
            enableCache = true
        )

        /** 高质量选项（10 秒超时，允许重试） */
        val HIGH_QUALITY = TranslationOptions(
            timeout = 10000,
            maxRetries = 3,
            enableAutoCorrect = true
        )

        /** 离线优先选项 */
        val OFFLINE_FIRST = TranslationOptions(
            timeout = 3000,
            maxRetries = 0,
            enableCache = true,
            skipTranslated = true
        )
    }
}

/**
 * 批量翻译请求
 * 一次性翻译多个文本片段
 */
@Serializable
data class BatchTranslationRequest(
    /** 要翻译的文本列表 */
    val texts: List<String>,

    /** 源语言 */
    val sourceLanguage: Language,

    /** 目标语言 */
    val targetLanguage: Language,

    /** 翻译选项（可选） */
    val options: TranslationOptions? = null
) {
    init {
        require(texts.isNotEmpty()) {
            "待翻译文本列表不能为空"
        }
        require(texts.all { it.isNotBlank() }) {
            "所有待翻译文本都不能为空"
        }
    }

    /** 文本数量 */
    val size: Int get() = texts.size

    /** 总文本长度 */
    val totalLength: Int get() = texts.sumOf { it.length }

    /** 转换为单个翻译请求列表 */
    fun toIndividualRequests(): List<TranslationRequest> {
        return texts.map { text ->
            TranslationRequest(text, sourceLanguage, targetLanguage, options)
        }
    }

    companion object {
        /**
         * 从合并文本创建批量翻译请求
         */
        fun fromMergedTexts(
            mergedTexts: List<MergedText>,
            sourceLanguage: Language,
            targetLanguage: Language,
            options: TranslationOptions? = null
        ): BatchTranslationRequest {
            return BatchTranslationRequest(
                texts = mergedTexts.map { it.text },
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                options = options
            )
        }
    }
}
