package com.cw2.cw_1kito.engine.translation.local

import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.LanguageModelState
import kotlinx.coroutines.flow.Flow

/**
 * 语言包管理器接口
 *
 * 负责管理 Google ML Kit 翻译语言包的下载、状态检查和删除操作。
 * 由于 ML Kit 的翻译语言包不是预装在 APK 中，需要在使用前下载。
 *
 * ## 使用场景
 * - **首次使用**：下载所需语言包
 * - **状态检查**：查询语言包是否已下载
 * - **批量管理**：下载/删除多个语言包
 * - **存储管理**：查看语言包占用空间
 *
 * ## 实现要求
 * - Android 平台使用 `RemoteModelManager` 与 ML Kit 交互
 * - 支持 Wi-Fi 下载条件检查
 * - 提供下载进度回调
 * - 线程安全的状态管理
 *
 * @see MLKitLanguagePackManager Android 平台实现
 */
interface ILanguagePackManager {

    /**
     * 获取所有常用语言对的状态
     *
     * 返回一个 Flow，持续推送语言包状态变化。
     * 常用语言对包括：
     * - 中 <-> 英
     * - 中 <-> 日
     * - 中 <-> 韩
     * - 英 <-> 日
     * - 英 <-> 韩
     * - 日 <-> 韩
     *
     * @return 语言包状态列表的 Flow
     */
    fun getLanguagePackStates(): Flow<List<LanguagePackState>>

    /**
     * 获取指定语言对的状态
     *
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 语言包状态
     */
    suspend fun getLanguagePackState(
        sourceLang: Language,
        targetLang: Language
    ): LanguagePackState

    /**
     * 下载指定语言对
     *
     * 根据需要下载源语言和目标语言的模型。
     * 例如：中->英 需要下载中文和英文两个模型。
     *
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @param requireWifi 是否只在 Wi-Fi 环境下下载（默认 true）
     * @return 下载结果
     */
    suspend fun downloadLanguagePair(
        sourceLang: Language,
        targetLang: Language,
        requireWifi: Boolean = true
    ): DownloadResult

    /**
     * 下载所有常用语言对
     *
     * 批量下载常用语言对，支持进度回调。
     *
     * @param requireWifi 是否只在 Wi-Fi 环境下下载（默认 true）
     * @param onProgress 进度回调 (已下载数, 总数)
     * @return 批量下载结果
     */
    suspend fun downloadAllCommonPairs(
        requireWifi: Boolean = true,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): DownloadAllResult

    /**
     * 删除指定语言对
     *
     * 删除对应的语言模型，释放存储空间。
     *
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 是否删除成功
     */
    suspend fun deleteLanguagePair(
        sourceLang: Language,
        targetLang: Language
    ): Boolean

    /**
     * 检查当前配置是否需要下载
     *
     * 根据用户当前的语言配置，检查是否需要下载语言包。
     *
     * @return 是否需要下载
     */
    suspend fun needsDownload(): Boolean

    /**
     * 检查指定语言对是否需要下载
     *
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 是否需要下载
     */
    suspend fun needsDownload(
        sourceLang: Language,
        targetLang: Language
    ): Boolean

    /**
     * 获取总存储占用
     *
     * 返回所有已下载语言包的总大小。
     *
     * @return 总字节数的 Flow
     */
    fun getTotalStorageUsed(): Flow<Long>

    /**
     * 刷新语言包状态
     *
     * 手动触发状态检查，更新缓存的下载状态。
     */
    suspend fun refreshStates()

    // ========== 语言模型管理（新） ==========

    /**
     * 获取单个语言模型的下载状态
     */
    suspend fun isLanguageModelDownloaded(language: Language): Boolean

    /**
     * 下载单个语言模型
     */
    suspend fun downloadLanguageModel(language: Language, requireWifi: Boolean = false): DownloadResult

    /**
     * 删除单个语言模型
     */
    suspend fun deleteLanguageModel(language: Language): Boolean

    /**
     * 获取所有支持的语言模型状态
     */
    suspend fun getLanguageModelStates(): List<LanguageModelState>
}

/**
 * 语言包状态
 *
 * @param sourceLang 源语言
 * @param targetLang 目标语言
 * @param status 下载状态
 * @param sizeBytes 语言包大小（字节）
 * @param progress 下载进度 0.0 - 1.0
 */
data class LanguagePackState(
    val sourceLang: Language,
    val targetLang: Language,
    val status: DownloadStatus,
    val sizeBytes: Long = 0,
    val progress: Float = 0f
) {
    /** 是否已下载 */
    val isDownloaded: Boolean
        get() = status == DownloadStatus.DOWNLOADED

    /** 是否正在下载 */
    val isDownloading: Boolean
        get() = status == DownloadStatus.DOWNLOADING

    /** 是否可以立即使用 */
    val isReady: Boolean
        get() = status == DownloadStatus.DOWNLOADED

    /** 获取可读的文件大小 */
    val readableSize: String
        get() = formatBytes(sizeBytes)

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}

/**
 * 下载状态
 */
enum class DownloadStatus {
    /** 未下载 */
    NOT_DOWNLOADED,
    /** 正在下载 */
    DOWNLOADING,
    /** 已下载 */
    DOWNLOADED,
    /** 下载失败 */
    FAILED,
    /** 已暂停 */
    PAUSED
}

/**
 * 单个语言对下载结果
 */
sealed class DownloadResult {
    /** 下载成功 */
    data class Success(val sizeBytes: Long) : DownloadResult()

    /** 下载失败 */
    data class Failed(val error: Throwable) : DownloadResult()

    /** 已取消 */
    object Cancelled : DownloadResult()

    /** 需要 Wi-Fi */
    object WifiRequired : DownloadResult()

    /** 不支持的语言 */
    object UnsupportedLanguage : DownloadResult()
}

/**
 * 批量下载结果
 *
 * @param successCount 成功下载数
 * @param failedCount 失败下载数
 * @param skippedCount 跳过数量（已下载）
 * @param totalBytes 总字节数
 */
data class DownloadAllResult(
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val totalBytes: Long
) {
    /** 总处理数量 */
    val totalCount: Int
        get() = successCount + failedCount + skippedCount

    /** 是否全部成功 */
    val isAllSuccess: Boolean
        get() = failedCount == 0 && successCount > 0

    /** 是否有任何变化 */
    val hasChanges: Boolean
        get() = successCount > 0 || failedCount > 0
}
