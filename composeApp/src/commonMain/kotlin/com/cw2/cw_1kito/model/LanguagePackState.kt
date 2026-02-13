package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 语言包下载状态
 */
@Serializable
enum class LanguagePackStatus {
    /** 已下载 */
    DOWNLOADED,
    /** 下载中 */
    DOWNLOADING,
    /** 未下载 */
    NOT_DOWNLOADED,
    /** 下载失败 */
    DOWNLOAD_FAILED
}

/**
 * 语言包状态数据模型
 *
 * @property sourceLang 源语言
 * @property targetLang 目标语言
 * @property status 下载状态
 * @property sizeBytes 语言包大小（字节）
 * @property downloadProgress 下载进度 (0.0 - 1.0)，仅在下载中时有效
 * @property errorMessage 错误信息，仅在失败时有效
 */
@Serializable
data class LanguagePackState(
    val sourceLang: Language,
    val targetLang: Language,
    val status: LanguagePackStatus = LanguagePackStatus.NOT_DOWNLOADED,
    val sizeBytes: Long = 0L,
    val downloadProgress: Float = 0f,
    val errorMessage: String? = null
) {
    /**
     * 语言对唯一标识
     */
    val pairId: String
        get() = "${sourceLang.code}-${targetLang.code}"

    /**
     * 格式化后的语言包大小显示
     */
    val formattedSize: String
        get() = formatBytes(sizeBytes)

    /**
     * 格式化后的下载进度百分比
     */
    val formattedProgress: String
        get() = "${(downloadProgress * 100).toInt()}%"
}

/**
 * 格式化字节大小为可读字符串
 *
 * @param bytes 字节数
 * @return 格式化后的字符串，如 "120 MB"
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

/**
 * 计算总存储占用
 *
 * @param states 语言包状态列表
 * @return 总字节数
 */
fun calculateTotalStorage(states: List<LanguagePackState>): Long {
    return states
        .filter { it.status == LanguagePackStatus.DOWNLOADED }
        .sumOf { it.sizeBytes }
}

/**
 * 获取默认支持的语言包列表
 *
 * 返回常用的语言对配置，用于在语言包管理页面显示可下载的语言包
 */
fun getDefaultLanguagePackStates(): List<LanguagePackState> {
    return listOf(
        // 中英互译
        LanguagePackState(
            sourceLang = Language.ZH,
            targetLang = Language.EN,
            status = LanguagePackStatus.NOT_DOWNLOADED,
            sizeBytes = 30 * 1024 * 1024L // 约 30MB
        ),
        LanguagePackState(
            sourceLang = Language.EN,
            targetLang = Language.ZH,
            status = LanguagePackStatus.NOT_DOWNLOADED,
            sizeBytes = 30 * 1024 * 1024L
        ),
        // 中日互译
        LanguagePackState(
            sourceLang = Language.ZH,
            targetLang = Language.JA,
            status = LanguagePackStatus.NOT_DOWNLOADED,
            sizeBytes = 35 * 1024 * 1024L // 约 35MB
        ),
        LanguagePackState(
            sourceLang = Language.JA,
            targetLang = Language.ZH,
            status = LanguagePackStatus.NOT_DOWNLOADED,
            sizeBytes = 35 * 1024 * 1024L
        ),
        // 中韩互译
        LanguagePackState(
            sourceLang = Language.ZH,
            targetLang = Language.KO,
            status = LanguagePackStatus.NOT_DOWNLOADED,
            sizeBytes = 32 * 1024 * 1024L // 约 32MB
        ),
        LanguagePackState(
            sourceLang = Language.KO,
            targetLang = Language.ZH,
            status = LanguagePackStatus.NOT_DOWNLOADED,
            sizeBytes = 32 * 1024 * 1024L
        ),
        // 英日互译
        LanguagePackState(
            sourceLang = Language.EN,
            targetLang = Language.JA,
            status = LanguagePackStatus.NOT_DOWNLOADED,
            sizeBytes = 28 * 1024 * 1024L
        ),
        LanguagePackState(
            sourceLang = Language.JA,
            targetLang = Language.EN,
            status = LanguagePackStatus.NOT_DOWNLOADED,
            sizeBytes = 28 * 1024 * 1024L
        ),
        // 英韩互译
        LanguagePackState(
            sourceLang = Language.EN,
            targetLang = Language.KO,
            status = LanguagePackStatus.NOT_DOWNLOADED,
            sizeBytes = 25 * 1024 * 1024L
        ),
        LanguagePackState(
            sourceLang = Language.KO,
            targetLang = Language.EN,
            status = LanguagePackStatus.NOT_DOWNLOADED,
            sizeBytes = 25 * 1024 * 1024L
        )
    )
}

// ========== 语言模型管理（新） ==========

/**
 * 单个语言模型的状态
 *
 * 以"语言模型"为单位管理，而非"语言对"。
 * ML Kit 底层以语言模型为单位存储，两个已下载的模型即可组成双向翻译对。
 */
@Serializable
data class LanguageModelState(
    val language: Language,
    val status: LanguagePackStatus = LanguagePackStatus.NOT_DOWNLOADED,
    val sizeBytes: Long = 0L,
    val errorMessage: String? = null
) {
    val formattedSize: String
        get() = formatBytes(sizeBytes)

    val isDownloaded: Boolean
        get() = status == LanguagePackStatus.DOWNLOADED
}

/**
 * 语言模型的估算大小（字节）
 */
fun Language.estimatedModelSizeBytes(): Long = when (this) {
    Language.ZH -> 25 * 1024 * 1024L
    Language.EN -> 15 * 1024 * 1024L
    Language.JA -> 20 * 1024 * 1024L
    Language.KO -> 18 * 1024 * 1024L
    else -> 20 * 1024 * 1024L
}

/**
 * 获取默认的语言模型状态列表（ZH/EN/JA/KO）
 */
fun getDefaultLanguageModelStates(): List<LanguageModelState> {
    return listOf(Language.ZH, Language.EN, Language.JA, Language.KO).map { lang ->
        LanguageModelState(
            language = lang,
            status = LanguagePackStatus.NOT_DOWNLOADED,
            sizeBytes = lang.estimatedModelSizeBytes()
        )
    }
}

/**
 * 根据已下载的模型计算可用语言对（两两组合）
 */
fun computeAvailablePairs(models: List<LanguageModelState>): List<Pair<Language, Language>> {
    val downloaded = models.filter { it.isDownloaded }.map { it.language }
    if (downloaded.size < 2) return emptyList()
    val pairs = mutableListOf<Pair<Language, Language>>()
    for (i in downloaded.indices) {
        for (j in i + 1 until downloaded.size) {
            pairs.add(downloaded[i] to downloaded[j])
        }
    }
    return pairs
}

/**
 * 格式化可用语言对为展示文案
 *
 * 例如："中文 ↔ English, 中文 ↔ 日本語, English ↔ 日本語"
 * 无可用对时返回 "暂无可用翻译方向"
 */
fun formatAvailablePairs(pairs: List<Pair<Language, Language>>): String {
    if (pairs.isEmpty()) return "暂无可用翻译方向"
    return pairs.joinToString(", ") { (a, b) ->
        "${a.displayName} ↔ ${b.displayName}"
    }
}

/**
 * 计算语言模型的总存储占用
 */
fun calculateModelTotalStorage(states: List<LanguageModelState>): Long {
    return states.filter { it.isDownloaded }.sumOf { it.sizeBytes }
}

/**
 * 计算删除某个语言模型后受影响的翻译方向
 */
fun computeAffectedPairs(
    language: Language,
    allModels: List<LanguageModelState>
): List<Pair<Language, Language>> {
    val otherDownloaded = allModels
        .filter { it.isDownloaded && it.language != language }
        .map { it.language }
    return otherDownloaded.map { other -> language to other }
}
