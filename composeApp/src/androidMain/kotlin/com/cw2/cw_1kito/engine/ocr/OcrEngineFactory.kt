package com.cw2.cw_1kito.engine.ocr

import android.content.Context
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.OcrLanguage
import com.cw2.cw_1kito.model.PerformanceMode
import com.cw2.cw_1kito.model.PerformanceMode.BALANCED

/**
 * OCR 引擎工厂
 *
 * 创建不同类型的 OCR 引擎实例
 *
 * ## 支持的引擎
 * - **MLKIT**: Google ML Kit Text Recognition（默认，零配置）
 *
 * ## 使用示例
 * ```kotlin
 * // 创建默认引擎（MLKit）
 * val engine = OcrEngineFactory.create(context)
 *
 * // 创建 MLKit 引擎（指定语言）
 * val mlkitEngine = OcrEngineFactory.createMLKit(
 *     context,
 *     language = MLKitOCRManager.OcrLanguage.CHINESE
 * )
 * ```
 *
 * @property context 应用上下文
 */
object OcrEngineFactory {

    /**
     * OCR 引擎类型
     */
    enum class EngineType {
        /** Google ML Kit Text Recognition */
        MLKIT
    }

    /**
     * 创建 OCR 引擎（默认 MLKit）
     *
     * @param context 应用上下文
     * @param performanceMode 性能模式（默认 BALANCED）
     * @param ocrLanguage OCR 语言（null 表示根据系统语言推断）
     * @return OCR 引擎实例
     */
    fun create(
        context: Context,
        performanceMode: PerformanceMode = BALANCED,
        ocrLanguage: OcrLanguage? = null
    ): IOcrEngine {
        val language = ocrLanguage ?: getRecommendedLanguage(context)
        return createMLKit(context, performanceMode, language)
    }

    /**
     * 创建 MLKit 引擎
     *
     * @param context 应用上下文
     * @param performanceMode 性能模式
     * @param language OCR 语言（默认中文）
     * @return MLKit OCR 引擎实例
     */
    fun createMLKit(
        context: Context,
        performanceMode: PerformanceMode = BALANCED,
        language: OcrLanguage = OcrLanguage.CHINESE
    ): MLKitOCRManager {
        return MLKitOCRManager(context, performanceMode, language)
    }

    /**
     * 创建指定类型的 OCR 引擎
     *
     * @param context 应用上下文
     * @param type 引擎类型
     * @param performanceMode 性能模式
     * @return OCR 引擎实例
     * @throws UnsupportedOperationException 不支持的引擎类型
     */
    fun create(
        context: Context,
        type: EngineType,
        performanceMode: PerformanceMode = BALANCED
    ): IOcrEngine {
        return when (type) {
            EngineType.MLKIT -> createMLKit(context, performanceMode)
        }
    }

    /**
     * 获取推荐的引擎类型
     *
     * 根据系统语言推荐引擎
     *
     * @param context 应用上下文
     * @return 推荐的引擎类型
     */
    fun getRecommendedEngine(context: Context): EngineType {
        // 默认使用 MLKit
        return EngineType.MLKIT
    }

    /**
     * 根据系统语言推荐 OCR 语言
     *
     * @param context 应用上下文
     * @return 推荐的 OCR 语言
     */
    fun getRecommendedLanguage(context: Context): OcrLanguage {
        val locale = context.resources.configuration.locales.get(0)
        val language = locale.language

        return when (language) {
            "zh" -> OcrLanguage.CHINESE
            "ja" -> OcrLanguage.JAPANESE
            "ko" -> OcrLanguage.KOREAN
            else -> OcrLanguage.LATIN
        }
    }

    /**
     * 根据翻译配置智能推断 OCR 语言
     *
     * @param sourceLanguage 源语言
     * @param targetLanguage 目标语言
     * @param manualOcrLanguage 用户手动指定的 OCR 语言（优先级最高）
     * @return 推断的 OCR 语言
     */
    fun inferOcrLanguage(
        sourceLanguage: Language,
        targetLanguage: Language,
        manualOcrLanguage: OcrLanguage? = null
    ): OcrLanguage {
        // 如果用户手动指定，优先使用
        if (manualOcrLanguage != null) {
            return manualOcrLanguage
        }

        // 否则根据翻译语言智能推断
        return OcrLanguage.inferFromTranslationLanguage(sourceLanguage, targetLanguage)
    }
}
