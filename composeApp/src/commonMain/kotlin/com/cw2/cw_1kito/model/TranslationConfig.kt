package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 翻译配置
 */
@Serializable
data class TranslationConfig(
    /** 选择的 VLM 模型 */
    val model: VlmModel = VlmModel.DEFAULT,

    /** 源语言 */
    val sourceLanguage: Language = Language.AUTO,

    /** 目标语言 */
    val targetLanguage: Language = Language.ZH,

    /** Temperature 参数（控制随机性） */
    val temperature: Double = 0.7,

    /** 最大 Token 数量 */
    val maxTokens: Int = 2000,

    /** 是否使用文本合并 Prompt（用于合并相邻文本块） */
    val useMergingPrompt: Boolean = false,

    /** 性能模式（控制 OCR 图像处理质量） */
    val performanceMode: PerformanceMode = PerformanceMode.BALANCED,

    /** 翻译模式（本地/云端/混合） */
    val translationMode: TranslationMode = TranslationMode.HYBRID,

    /** 文本合并配置 */
    val mergingConfig: MergingConfig = MergingConfig.DEFAULT,

    /** OCR 语言（null 表示自动推断） */
    val ocrLanguage: OcrLanguage? = null
) {
    init {
        require(temperature in 0.0..2.0) {
            "Temperature 必须在 0.0 到 2.0 之间，实际: $temperature"
        }
        require(maxTokens in 1..100000) {
            "maxTokens 必须在 1 到 100000 之间，实际: $maxTokens"
        }
    }

    /** 检查配置是否有效 */
    val isValid: Boolean
        get() = sourceLanguage != targetLanguage

    companion object {
        /**
         * 默认配置（中译英）
         */
        val DEFAULT = TranslationConfig(
            model = VlmModel.DEFAULT,
            sourceLanguage = Language.AUTO,
            targetLanguage = Language.ZH,
            useMergingPrompt = false,
            performanceMode = PerformanceMode.BALANCED,
            translationMode = TranslationMode.HYBRID,
            mergingConfig = MergingConfig.DEFAULT
        )

        /**
         * 创建精简配置（更快、更便宜）
         */
        fun fast(targetLanguage: Language = Language.ZH) = TranslationConfig(
            model = VlmModel.GLM_4_5V,
            sourceLanguage = Language.AUTO,
            targetLanguage = targetLanguage,
            temperature = 0.5,
            maxTokens = 1000,
            useMergingPrompt = false,
            performanceMode = PerformanceMode.FAST,
            translationMode = TranslationMode.LOCAL,
            mergingConfig = MergingConfig.DEFAULT
        )

        /**
         * 创建高质量配置
         */
        fun highQuality(targetLanguage: Language = Language.ZH) = TranslationConfig(
            model = VlmModel.QWEN3_VL_235B,
            sourceLanguage = Language.AUTO,
            targetLanguage = targetLanguage,
            temperature = 0.7,
            maxTokens = 2000,
            useMergingPrompt = false,
            performanceMode = PerformanceMode.QUALITY,
            translationMode = TranslationMode.REMOTE,
            mergingConfig = MergingConfig.DEFAULT
        )

        /**
         * 创建本地优先配置
         */
        fun localFirst(targetLanguage: Language = Language.ZH) = TranslationConfig(
            model = VlmModel.DEFAULT,
            sourceLanguage = Language.AUTO,
            targetLanguage = targetLanguage,
            temperature = 0.5,
            maxTokens = 1000,
            useMergingPrompt = false,
            performanceMode = PerformanceMode.BALANCED,
            translationMode = TranslationMode.HYBRID,
            mergingConfig = MergingConfig.DEFAULT
        )
    }
}

/**
 * 语言配置（仅语言设置）
 */
@Serializable
data class LanguageConfig(
    val sourceLanguage: Language,
    val targetLanguage: Language
) {
    init {
        require(sourceLanguage != targetLanguage) {
            "源语言和目标语言不能相同"
        }
    }

    /** 转换为完整配置 */
    fun toTranslationConfig(model: VlmModel = VlmModel.DEFAULT): TranslationConfig {
        return TranslationConfig(
            model = model,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage
        )
    }

    companion object {
        /** 默认配置（自动识别 -> 中文） */
        val DEFAULT = LanguageConfig(
            sourceLanguage = Language.AUTO,
            targetLanguage = Language.ZH
        )
    }
}
