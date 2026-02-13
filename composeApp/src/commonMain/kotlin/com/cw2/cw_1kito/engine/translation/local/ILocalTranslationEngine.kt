package com.cw2.cw_1kito.engine.translation.local

import com.cw2.cw_1kito.model.Language

/**
 * 本地翻译引擎接口
 *
 * 定义本地翻译引擎的标准接口，支持不同的实现方式（ML Kit）
 *
 * ## 实现要求
 * - 支持异步初始化（加载翻译模型）
 * - 支持多语言对翻译
 * - 支持本地模式（无需网络）
 * - 正确管理资源（释放模型）
 */
interface ILocalTranslationEngine {

    /**
     * 初始化翻译引擎
     *
     * 执行步骤：
     * 1. 检查/加载翻译模型
     * 2. 初始化翻译客户端
     * 3. 预加载常用语言对（可选）
     *
     * @return 初始化是否成功
     */
    suspend fun initialize(): Boolean

    /**
     * 翻译文本
     *
     * @param text 待翻译的文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 翻译结果
     */
    suspend fun translate(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String

    /**
     * 检查语言对是否可用
     *
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 是否支持该语言对
     */
    suspend fun isLanguageAvailable(
        sourceLang: Language,
        targetLang: Language
    ): Boolean

    /**
     * 释放翻译引擎资源
     *
     * 注意：释放后需要重新初始化才能使用
     */
    fun release()

    /**
     * 检查引擎是否已初始化
     */
    fun isInitialized(): Boolean

    /**
     * 获取引擎版本信息
     */
    fun getVersion(): String
}
