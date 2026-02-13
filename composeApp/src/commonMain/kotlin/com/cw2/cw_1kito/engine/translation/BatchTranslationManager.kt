package com.cw2.cw_1kito.engine.translation

import com.cw2.cw_1kito.engine.translation.local.ILocalTranslationEngine
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.TranslationMode
import com.cw2.cw_1kito.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 批量翻译管理器接口
 *
 * 提供批量翻译功能，支持分批处理和进度回调。
 * 适用于多文本翻译场景，如 OCR 结果批量翻译。
 *
 * ## 设计原则
 * - 分批处理：每批固定数量并发，避免同时发起过多请求
 * - 保持顺序：返回结果与输入顺序一致
 * - 进度回调：支持批次完成进度通知
 * - 错误处理：单个翻译失败不影响其他文本
 *
 * ## 实现要求
 * - 支持本地/云端/混合翻译模式
 * - 使用协程并发处理提高效率
 * - 正确处理空列表和单元素列表
 */
interface IBatchTranslationManager {

    /**
     * 批量翻译
     *
     * 将文本列表分批处理，每批并发翻译。支持进度回调。
     *
     * ## 处理流程
     * 1. 将文本列表按批次大小分块
     * 2. 每批使用协程并发处理
     * 3. 等待当前批次完成后再处理下一批
     * 4. 保持输入顺序返回结果
     *
     * ## 翻译模式行为
     * - LOCAL: 仅使用本地翻译引擎
     * - REMOTE: 仅使用云端 LLM 引擎
     * - HYBRID: 优先本地，失败时降级到云端
     *
     * @param texts 待翻译文本列表
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param mode 翻译模式
     * @param onBatchComplete 批次完成回调 (当前批次, 总批次数)
     * @return 翻译结果列表（保持原顺序），失败的文本返回原文
     */
    suspend fun translateBatch(
        texts: List<String>,
        sourceLang: Language,
        targetLang: Language,
        mode: TranslationMode,
        onBatchComplete: ((batchIndex: Int, totalBatches: Int) -> Unit)? = null
    ): List<String>

    companion object {
        /**
         * 默认批次大小
         * 每批同时处理的请求数量
         */
        const val DEFAULT_BATCH_SIZE = 8

        /**
         * 最大批次大小
         * 避免同时发起过多请求导致 API 限流
         */
        const val MAX_BATCH_SIZE = 10

        /**
         * 最小批次大小
         */
        const val MIN_BATCH_SIZE = 1
    }
}

/**
 * 批量翻译管理器实现
 *
 * 使用协程并发处理批量翻译请求。
 *
 * ## 性能优化
 * - 使用 Dispatchers.Default 进行 CPU 密集型任务
 * - 使用 Dispatchers.IO 进行网络请求
 * - 分批处理避免同时发起过多请求
 * - 云端翻译模式使用内置批量 API，减少网络往返
 *
 * ## 错误处理
 * - 单个翻译失败时记录日志并返回原文
 * - 不影响其他文本的翻译
 *
 * @property localTranslationEngine 本地翻译引擎
 * @property cloudLlmEngine 云端 LLM 翻译引擎
 * @property configManager 配置管理器（用于获取自定义提示词）
 * @property batchSize 批次大小，默认 3
 */
class BatchTranslationManagerImpl(
    private val localTranslationEngine: ILocalTranslationEngine,
    private val cloudLlmEngine: ICloudLlmEngine,
    private val configManager: com.cw2.cw_1kito.data.config.ConfigManager,
    private val batchSize: Int = IBatchTranslationManager.DEFAULT_BATCH_SIZE
) : IBatchTranslationManager {

    private val actualBatchSize: Int
        get() = batchSize.coerceIn(IBatchTranslationManager.MIN_BATCH_SIZE, IBatchTranslationManager.MAX_BATCH_SIZE)

    override suspend fun translateBatch(
        texts: List<String>,
        sourceLang: Language,
        targetLang: Language,
        mode: TranslationMode,
        onBatchComplete: ((Int, Int) -> Unit)?
    ): List<String> {
        // 边界条件处理
        if (texts.isEmpty()) {
            Logger.d("[BatchTranslationManager] 输入列表为空，返回空结果")
            return emptyList()
        }

        if (texts.size == 1) {
            Logger.d("[BatchTranslationManager] 单个文本，直接翻译")
            val result = translateSingle(texts[0], sourceLang, targetLang, mode)
            onBatchComplete?.invoke(1, 1)
            return listOf(result)
        }

        // 根据翻译模式选择处理策略
        return when (mode) {
            TranslationMode.REMOTE -> {
                // 云端模式：使用内置批量翻译 API
                translateWithCloudBatch(texts, sourceLang, targetLang, onBatchComplete)
            }
            TranslationMode.LOCAL, TranslationMode.HYBRID -> {
                // 本地/混合模式：使用原有的分批逻辑
                translateWithBatches(texts, sourceLang, targetLang, mode, onBatchComplete)
            }
        }
    }

    /**
     * 使用云端批量翻译 API
     *
     * 直接调用 cloudLlmEngine.translateBatch()，利用客户端内置的并发处理。
     *
     * @param texts 待翻译文本列表
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param onBatchComplete 批次完成回调
     * @return 翻译结果列表
     */
    private suspend fun translateWithCloudBatch(
        texts: List<String>,
        sourceLang: Language,
        targetLang: Language,
        onBatchComplete: ((Int, Int) -> Unit)?
    ): List<String> {
        val customPrompt = configManager.getCustomTranslationPrompt()

        Logger.d("[BatchTranslationManager] 云端批量翻译: ${texts.size} 个文本, 并发数: $actualBatchSize")

        return try {
            val results = cloudLlmEngine.translateBatch(
                texts = texts,
                sourceLang = sourceLang,
                targetLang = targetLang,
                customPrompt = customPrompt,
                concurrency = actualBatchSize
            )

            // 触发完成回调（云端批量 API 是一次性完成）
            onBatchComplete?.invoke(1, 1)
            Logger.i("[BatchTranslationManager] 云端批量翻译完成: ${results.size} 个结果")

            results
        } catch (e: Exception) {
            Logger.e(e, "[BatchTranslationManager] 云端批量翻译失败，降级到单条翻译")
            // 降级：使用单条翻译逐个处理
            texts.map { text ->
                try {
                    cloudLlmEngine.translate(text, sourceLang, targetLang, customPrompt)
                } catch (e2: Exception) {
                    Logger.e(e2, "[BatchTranslationManager] 单条翻译也失败: ${text.take(30)}...")
                    text
                }
            }
        }
    }

    /**
     * 使用分批处理（本地/混合模式）
     *
     * @param texts 待翻译文本列表
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param mode 翻译模式
     * @param onBatchComplete 批次完成回调
     * @return 翻译结果列表
     */
    private suspend fun translateWithBatches(
        texts: List<String>,
        sourceLang: Language,
        targetLang: Language,
        mode: TranslationMode,
        onBatchComplete: ((Int, Int) -> Unit)?
    ): List<String> {
        // 分批处理
        val batches = texts.chunked(actualBatchSize)
        val totalBatches = batches.size
        val results = mutableListOf<String>()

        Logger.d("[BatchTranslationManager] 批量翻译: ${texts.size} 个文本, $totalBatches 批, 每批 $actualBatchSize 个")

        batches.forEachIndexed { index, batch ->
            // 并发处理当前批次
            val batchResults = translateBatchConcurrent(batch, sourceLang, targetLang, mode)
            results.addAll(batchResults)

            // 回调进度
            onBatchComplete?.invoke(index + 1, totalBatches)
            Logger.d("[BatchTranslationManager] 批次 ${index + 1}/${totalBatches} 完成, 累计 ${results.size}/${texts.size}")
        }

        Logger.i("[BatchTranslationManager] 批量翻译完成: ${results.size} 个结果")
        return results
    }

    /**
     * 并发翻译一批文本
     *
     * 使用 async 协程并发处理，等待所有请求完成后返回结果。
     *
     * @param batch 当前批次的文本列表
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param mode 翻译模式
     * @return 翻译结果列表（顺序与输入一致）
     */
    private suspend fun translateBatchConcurrent(
        batch: List<String>,
        sourceLang: Language,
        targetLang: Language,
        mode: TranslationMode
    ): List<String> = coroutineScope {
        batch.map { text ->
            async(Dispatchers.IO) {
                translateSingle(text, sourceLang, targetLang, mode)
            }
        }.awaitAll()
    }

    /**
     * 翻译单个文本
     *
     * 根据翻译模式选择相应的翻译引擎。
     * 失败时返回原文并记录错误。
     *
     * @param text 待翻译文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param mode 翻译模式
     * @return 翻译结果，失败时返回原文
     */
    private suspend fun translateSingle(
        text: String,
        sourceLang: Language,
        targetLang: Language,
        mode: TranslationMode
    ): String {
        return when (mode) {
            TranslationMode.LOCAL -> {
                translateLocal(text, sourceLang, targetLang)
            }
            TranslationMode.REMOTE -> {
                translateRemote(text, sourceLang, targetLang)
            }
            TranslationMode.HYBRID -> {
                translateHybrid(text, sourceLang, targetLang)
            }
        }
    }

    /**
     * 本地翻译
     */
    private suspend fun translateLocal(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String {
        return try {
            localTranslationEngine.translate(text, sourceLang, targetLang)
        } catch (e: Exception) {
            Logger.e(e, "[BatchTranslationManager] 本地翻译失败: ${text.take(30)}...")
            text // 返回原文
        }
    }

    /**
     * 云端翻译
     */
    private suspend fun translateRemote(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String {
        return try {
            cloudLlmEngine.translate(text, sourceLang, targetLang, null)
        } catch (e: Exception) {
            Logger.e(e, "[BatchTranslationManager] 云端翻译失败: ${text.take(30)}...")
            text // 返回原文
        }
    }

    /**
     * 混合翻译（优先本地，失败时降级到云端）
     */
    private suspend fun translateHybrid(
        text: String,
        sourceLang: Language,
        targetLang: Language
    ): String {
        return try {
            // 优先尝试本地翻译
            localTranslationEngine.translate(text, sourceLang, targetLang)
        } catch (e: Exception) {
            Logger.w("[BatchTranslationManager] 本地翻译失败，降级到云端: ${text.take(30)}...")
            try {
                cloudLlmEngine.translate(text, sourceLang, targetLang, null)
            } catch (e2: Exception) {
                Logger.e(e2, "[BatchTranslationManager] 云端翻译也失败: ${text.take(30)}...")
                text // 返回原文
            }
        }
    }
}

/**
 * 批量翻译管理器工厂
 *
 * 创建批量翻译管理器实例的工厂方法。
 */
object BatchTranslationManagerFactory {

    /**
     * 创建批量翻译管理器
     *
     * @param localTranslationEngine 本地翻译引擎
     * @param cloudLlmEngine 云端 LLM 翻译引擎
     * @param configManager 配置管理器（用于获取自定义翻译提示词）
     * @param batchSize 批次大小，默认 3
     * @return 批量翻译管理器实例
     */
    fun create(
        localTranslationEngine: ILocalTranslationEngine,
        cloudLlmEngine: ICloudLlmEngine,
        configManager: com.cw2.cw_1kito.data.config.ConfigManager,
        batchSize: Int = IBatchTranslationManager.DEFAULT_BATCH_SIZE
    ): IBatchTranslationManager {
        return BatchTranslationManagerImpl(
            localTranslationEngine = localTranslationEngine,
            cloudLlmEngine = cloudLlmEngine,
            configManager = configManager,
            batchSize = batchSize
        )
    }
}
