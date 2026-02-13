package com.cw2.cw_1kito.engine.translation.local

import android.content.Context
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.util.Logger
import com.cw2.cw_1kito.util.NetworkUtils
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Google ML Kit 语言包管理器实现
 *
 * 使用 `RemoteModelManager` 管理翻译语言包的下载、状态检查和删除。
 *
 * ## 功能特性
 * - **状态检查**：查询语言包是否已下载
 * - **条件下载**：支持 Wi-Fi 下载条件
 * - **批量管理**：批量下载/删除多个语言包
 * - **进度跟踪**：下载进度回调
 * - **存储统计**：计算语言包占用空间
 *
 * ## 语言包大小参考
 * - 每个语言模型约 10-30 MB
 * - 常用 4 语言（中英日韩）约 40-120 MB
 *
 * ## 使用方法
 * ```kotlin
 * val manager = MLKitLanguagePackManager(context)
 *
 * // 检查状态
 * val states = manager.getLanguagePackStates()
 *
 * // 下载语言对
 * val result = manager.downloadLanguagePair(Language.ZH, Language.EN)
 *
 * // 批量下载
 * val allResult = manager.downloadAllCommonPairs(
 *     requireWifi = true,
 *     onProgress = { current, total -> ... }
 * )
 * ```
 *
 * @param context Android 上下文
 * @see ILanguagePackManager
 */
class MLKitLanguagePackManager(
    private val context: Context
) : ILanguagePackManager {

    private val modelManager: RemoteModelManager = RemoteModelManager.getInstance()

    // 状态缓存，避免频繁查询
    private val stateCache = MutableStateFlow<Map<String, LanguagePackState>>(emptyMap())

    // 状态更新锁
    private val stateMutex = Mutex()

    // 常用语言对（用于批量操作）
    private val commonLanguagePairs: List<Pair<Language, Language>> = listOf(
        Language.ZH to Language.EN,
        Language.EN to Language.ZH,
        Language.ZH to Language.JA,
        Language.JA to Language.ZH,
        Language.ZH to Language.KO,
        Language.KO to Language.ZH,
        Language.EN to Language.JA,
        Language.JA to Language.EN,
        Language.EN to Language.KO,
        Language.KO to Language.EN,
        Language.JA to Language.KO,
        Language.KO to Language.JA
    )

    init {
        Logger.d("[LangPack] MLKitLanguagePackManager 初始化")
    }

    /**
     * 获取所有常用语言对的状态
     */
    override fun getLanguagePackStates(): Flow<List<LanguagePackState>> {
        return stateCache.map { cache ->
            commonLanguagePairs.map { (source, target) ->
                val key = getLanguagePairKey(source, target)
                cache[key] ?: LanguagePackState(
                    sourceLang = source,
                    targetLang = target,
                    status = DownloadStatus.NOT_DOWNLOADED
                )
            }
        }
    }

    /**
     * 获取指定语言对的状态
     */
    override suspend fun getLanguagePackState(
        sourceLang: Language,
        targetLang: Language
    ): LanguagePackState = withContext(Dispatchers.IO) {
        val sourceCode = sourceLang.toTranslateLanguage()
        val targetCode = targetLang.toTranslateLanguage()

        if (sourceCode == null || targetCode == null) {
            return@withContext LanguagePackState(
                sourceLang = sourceLang,
                targetLang = targetLang,
                status = DownloadStatus.FAILED
            )
        }

        // 检查两个语言模型是否都已下载
        val sourceModel = TranslateRemoteModel.Builder(sourceCode).build()
        val targetModel = TranslateRemoteModel.Builder(targetCode).build()

        val isSourceDownloaded = isModelDownloaded(sourceModel)
        val isTargetDownloaded = isModelDownloaded(targetModel)

        val status = if (isSourceDownloaded && isTargetDownloaded) {
            DownloadStatus.DOWNLOADED
        } else {
            DownloadStatus.NOT_DOWNLOADED
        }

        // 估算大小（每个语言模型约 15-25 MB）
        val estimatedSize = if (status == DownloadStatus.DOWNLOADED) {
            estimateModelSize(sourceCode) + estimateModelSize(targetCode)
        } else {
            0L
        }

        LanguagePackState(
            sourceLang = sourceLang,
            targetLang = targetLang,
            status = status,
            sizeBytes = estimatedSize,
            progress = 0f
        )
    }

    /**
     * 下载指定语言对
     */
    override suspend fun downloadLanguagePair(
        sourceLang: Language,
        targetLang: Language,
        requireWifi: Boolean
    ): DownloadResult = withContext(Dispatchers.IO) {
        Logger.d("[LangPack] 开始下载语言对：${sourceLang.code} -> ${targetLang.code}")

        // 检查 Wi-Fi 条件
        if (requireWifi && !NetworkUtils.isWifiConnected(context)) {
            Logger.w("[LangPack] 需要 Wi-Fi 连接")
            return@withContext DownloadResult.WifiRequired
        }

        val sourceCode = sourceLang.toTranslateLanguage()
        val targetCode = targetLang.toTranslateLanguage()

        if (sourceCode == null || targetCode == null) {
            Logger.w("[LangPack] 不支持的语言对")
            return@withContext DownloadResult.UnsupportedLanguage
        }

        try {
            // 更新状态为下载中
            updateLanguagePairStatus(sourceLang, targetLang, DownloadStatus.DOWNLOADING)

            // 下载源语言模型（如果需要）
            val sourceModel = TranslateRemoteModel.Builder(sourceCode).build()
            var sourceSize = 0L

            if (!isModelDownloaded(sourceModel)) {
                Logger.d("[LangPack] 下载源语言模型：$sourceCode")
                sourceSize = downloadModel(sourceModel, requireWifi)
            }

            // 下载目标语言模型（如果需要）
            val targetModel = TranslateRemoteModel.Builder(targetCode).build()
            var targetSize = 0L

            if (!isModelDownloaded(targetModel)) {
                Logger.d("[LangPack] 下载目标语言模型：$targetCode")
                targetSize = downloadModel(targetModel, requireWifi)
            }

            // 下载完成后，只更新当前语言对的状态
            updateLanguagePairStatus(sourceLang, targetLang, DownloadStatus.DOWNLOADED)

            val totalSize = sourceSize + targetSize
            Logger.i("[LangPack] 语言对下载成功：${sourceLang.code} -> ${targetLang.code} (${totalSize / 1024} KB)")

            DownloadResult.Success(totalSize)

        } catch (e: Exception) {
            Logger.e(e, "[LangPack] 语言对下载失败")
            updateLanguagePairStatus(sourceLang, targetLang, DownloadStatus.FAILED)
            DownloadResult.Failed(e)
        }
    }

    /**
     * 下载所有常用语言对
     */
    override suspend fun downloadAllCommonPairs(
        requireWifi: Boolean,
        onProgress: (Int, Int) -> Unit
    ): DownloadAllResult = withContext(Dispatchers.IO) {
        Logger.d("[LangPack] 开始批量下载常用语言对")

        // 检查 Wi-Fi 条件
        if (requireWifi && !NetworkUtils.isWifiConnected(context)) {
            Logger.w("[LangPack] 需要 Wi-Fi 连接进行批量下载")
            return@withContext DownloadAllResult(0, 0, 0, 0)
        }

        var successCount = 0
        var failedCount = 0
        var skippedCount = 0
        var totalBytes = 0L

        val totalPairs = commonLanguagePairs.size
        var processed = 0

        // 收集需要下载的语言模型
        val modelsToDownload = mutableSetOf<String>()

        for ((source, target) in commonLanguagePairs) {
            val sourceCode = source.toTranslateLanguage()
            val targetCode = target.toTranslateLanguage()

            if (sourceCode != null) modelsToDownload.add(sourceCode)
            if (targetCode != null) modelsToDownload.add(targetCode)
        }

        Logger.d("[LangPack] 需要下载 ${modelsToDownload.size} 个语言模型")

        // 下载每个语言模型
        for (languageCode in modelsToDownload) {
            try {
                val model = TranslateRemoteModel.Builder(languageCode).build()

                if (isModelDownloaded(model)) {
                    skippedCount++
                    Logger.d("[LangPack] 跳过已下载的模型：$languageCode")
                } else {
                    Logger.d("[LangPack] 下载语言模型：$languageCode")
                    val size = downloadModel(model, requireWifi)
                    totalBytes += size
                    successCount++
                }

            } catch (e: Exception) {
                Logger.e(e, "[LangPack] 模型下载失败：$languageCode")
                failedCount++
            }

            processed++
            onProgress(processed, modelsToDownload.size)
        }

        // 更新所有语言对状态
        refreshStates()

        val result = DownloadAllResult(
            successCount = successCount,
            failedCount = failedCount,
            skippedCount = skippedCount,
            totalBytes = totalBytes
        )

        Logger.i("[LangPack] 批量下载完成：成功=$successCount, 失败=$failedCount, 跳过=$skippedCount, 总大小=${totalBytes / 1024}KB")

        result
    }

    /**
     * 删除指定语言对
     *
     * 注意：只有当语言模型不被其他已下载的语言对使用时，才会真正删除模型。
     * 例如：如果 ZH->EN 和 ZH->JA 都已下载，删除 ZH->EN 时不会删除 ZH 模型，
     * 因为 ZH->JA 仍然需要它。但 EN 模型如果没有其他语言对使用，则会被删除。
     */
    override suspend fun deleteLanguagePair(
        sourceLang: Language,
        targetLang: Language
    ): Boolean = withContext(Dispatchers.IO) {
        Logger.d("[LangPack] 删除语言对：${sourceLang.code} -> ${targetLang.code}")

        val sourceCode = sourceLang.toTranslateLanguage()
        val targetCode = targetLang.toTranslateLanguage()

        if (sourceCode == null || targetCode == null) {
            Logger.w("[LangPack] 不支持的语言对，无法删除")
            return@withContext false
        }

        try {
            // 找出哪些其他已下载的语言对也在使用这些模型
            val otherPairsUsingSource = mutableListOf<Pair<Language, Language>>()
            val otherPairsUsingTarget = mutableListOf<Pair<Language, Language>>()

            for ((s, t) in commonLanguagePairs) {
                if (s == sourceLang && t == targetLang) continue // 跳过当前要删除的语言对

                val state = getLanguagePackState(s, t)
                if (state.status == DownloadStatus.DOWNLOADED) {
                    if (s.toTranslateLanguage() == sourceCode || t.toTranslateLanguage() == sourceCode) {
                        otherPairsUsingSource.add(s to t)
                    }
                    if (s.toTranslateLanguage() == targetCode || t.toTranslateLanguage() == targetCode) {
                        otherPairsUsingTarget.add(s to t)
                    }
                }
            }

            // 只删除没有被其他语言对使用的模型
            val sourceModel = TranslateRemoteModel.Builder(sourceCode).build()
            if (otherPairsUsingSource.isEmpty() && isModelDownloaded(sourceModel)) {
                deleteModel(sourceModel)
                Logger.d("[LangPack] 已删除源语言模型：$sourceCode")
            } else if (otherPairsUsingSource.isNotEmpty()) {
                Logger.d("[LangPack] 源语言模型 $sourceCode 被其他语言对使用，保留")
            }

            val targetModel = TranslateRemoteModel.Builder(targetCode).build()
            if (otherPairsUsingTarget.isEmpty() && isModelDownloaded(targetModel)) {
                deleteModel(targetModel)
                Logger.d("[LangPack] 已删除目标语言模型：$targetCode")
            } else if (otherPairsUsingTarget.isNotEmpty()) {
                Logger.d("[LangPack] 目标语言模型 $targetCode 被其他语言对使用，保留")
            }

            // 更新当前语言对状态
            updateLanguagePairStatus(sourceLang, targetLang, DownloadStatus.NOT_DOWNLOADED)

            Logger.i("[LangPack] 语言对删除成功")
            true

        } catch (e: Exception) {
            Logger.e(e, "[LangPack] 语言对删除失败")
            false
        }
    }

    /**
     * 检查当前配置是否需要下载
     *
     * 注意：此方法需要访问 ConfigManager，但在当前实现中，
     * 我们默认检查常用语言对是否已下载。
     */
    override suspend fun needsDownload(): Boolean = withContext(Dispatchers.IO) {
        // 检查是否至少有一个常用语言对未下载
        for ((source, target) in commonLanguagePairs) {
            if (needsDownload(source, target)) {
                return@withContext true
            }
        }
        false
    }

    /**
     * 检查指定语言对是否需要下载
     */
    override suspend fun needsDownload(
        sourceLang: Language,
        targetLang: Language
    ): Boolean = withContext(Dispatchers.IO) {
        val state = getLanguagePackState(sourceLang, targetLang)
        !state.isDownloaded
    }

    /**
     * 获取总存储占用
     */
    override fun getTotalStorageUsed(): Flow<Long> = callbackFlow {
        trySend(calculateTotalStorage())

        // 监听状态变化
        val stateJob = launch {
            stateCache.collect {
                trySend(calculateTotalStorage())
            }
        }

        awaitClose { stateJob.cancel() }
    }

    /**
     * 刷新语言包状态
     */
    override suspend fun refreshStates() = withContext(Dispatchers.IO) {
        Logger.d("[LangPack] 刷新语言包状态")

        val newStates = mutableMapOf<String, LanguagePackState>()

        for ((source, target) in commonLanguagePairs) {
            val state = getLanguagePackState(source, target)
            val key = getLanguagePairKey(source, target)
            newStates[key] = state
        }

        stateMutex.withLock {
            stateCache.value = newStates
        }

        Logger.d("[LangPack] 状态刷新完成，共 ${newStates.size} 个语言对")
    }

    // ========== 私有辅助方法 ==========

    /**
     * 检查模型是否已下载
     */
    private suspend fun isModelDownloaded(model: TranslateRemoteModel): Boolean {
        return suspendCancellableCoroutine { continuation ->
            modelManager.isModelDownloaded(model)
                .addOnSuccessListener { isDownloaded ->
                    continuation.resume(isDownloaded)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    /**
     * 下载单个语言模型
     */
    private suspend fun downloadModel(
        model: TranslateRemoteModel,
        requireWifi: Boolean
    ): Long {
        val conditions = DownloadConditions.Builder().apply {
            if (requireWifi) {
                requireWifi()
            }
        }.build()

        return suspendCancellableCoroutine { continuation ->
            modelManager.download(model, conditions)
                .addOnSuccessListener {
                    // 估算模型大小（ML Kit 不提供实际大小）
                    val estimatedSize = estimateModelSizeFromLanguage(model)
                    continuation.resume(estimatedSize)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    /**
     * 删除单个语言模型
     */
    private suspend fun deleteModel(model: TranslateRemoteModel) {
        suspendCancellableCoroutine<Unit> { continuation ->
            modelManager.deleteDownloadedModel(model)
                .addOnSuccessListener {
                    continuation.resume(Unit)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    /**
     * 估算模型大小
     */
    private fun estimateModelSize(languageCode: String): Long {
        // ML Kit 语言模型大小估算
        // 英语较小，其他语言较大
        return when {
            languageCode == TranslateLanguage.ENGLISH -> 15 * 1024 * 1024L  // 15 MB
            languageCode == TranslateLanguage.CHINESE -> 25 * 1024 * 1024L  // 25 MB
            languageCode == TranslateLanguage.JAPANESE -> 20 * 1024 * 1024L // 20 MB
            languageCode == TranslateLanguage.KOREAN -> 18 * 1024 * 1024L   // 18 MB
            else -> 20 * 1024 * 1024L  // 默认 20 MB
        }
    }

    /**
     * 从模型估算大小
     */
    private fun estimateModelSizeFromLanguage(model: TranslateRemoteModel): Long {
        // TranslateRemoteModel 不直接暴露语言代码
        // 使用默认估算
        return 20 * 1024 * 1024L
    }

    /**
     * 计算总存储占用
     */
    private suspend fun calculateTotalStorage(): Long {
        var total = 0L

        for ((source, target) in commonLanguagePairs) {
            val state = getLanguagePackState(source, target)
            if (state.isDownloaded) {
                total += state.sizeBytes
            }
        }

        return total
    }

    /**
     * 获取语言对的唯一键
     */
    private fun getLanguagePairKey(source: Language, target: Language): String {
        return "${source.code}-${target.code}"
    }

    /**
     * 更新语言对状态
     */
    private suspend fun updateLanguagePairStatus(
        source: Language,
        target: Language,
        status: DownloadStatus
    ) {
        val key = getLanguagePairKey(source, target)
        val newState = LanguagePackState(
            sourceLang = source,
            targetLang = target,
            status = status,
            sizeBytes = 0,
            progress = 0f
        )

        stateMutex.withLock {
            val current = stateCache.value.toMutableMap()
            current[key] = newState
            stateCache.value = current
        }
    }

    /**
     * 将 Language 转换为 TranslateLanguage 代码
     */
    private fun Language.toTranslateLanguage(): String? {
        return when (this) {
            Language.ZH -> TranslateLanguage.CHINESE
            Language.EN -> TranslateLanguage.ENGLISH
            Language.JA -> TranslateLanguage.JAPANESE
            Language.KO -> TranslateLanguage.KOREAN
            Language.FR -> TranslateLanguage.FRENCH
            Language.DE -> TranslateLanguage.GERMAN
            Language.ES -> TranslateLanguage.SPANISH
            Language.AUTO -> null  // 自动识别不支持本地翻译
        }
    }

    companion object {
        /**
         * ML Kit 支持的语言列表
         */
        val SUPPORTED_LANGUAGES = listOf(
            Language.ZH,
            Language.EN,
            Language.JA,
            Language.KO,
            Language.FR,
            Language.DE,
            Language.ES
        )

        /**
         * 检查语言是否被支持
         */
        fun isLanguageSupported(language: Language): Boolean {
            return SUPPORTED_LANGUAGES.contains(language)
        }
    }
}
