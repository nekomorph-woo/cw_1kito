package com.cw2.cw_1kito.engine

import com.cw2.cw_1kito.data.config.ConfigManager
import com.cw2.cw_1kito.engine.translation.local.ILocalTranslationEngine
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.TranslationMode

/**
 * 翻译依赖检查结果
 */
sealed class DependencyStatus {
    /** 依赖条件已满足 */
    data class Satisfied(val message: String = "依赖条件满足") : DependencyStatus()

    /** 缺少 API Key */
    data class MissingApiKey(
        val message: String = "需要配置 API Key",
        val action: String = "前往设置页面配置 API Key"
    ) : DependencyStatus()

    /** 缺少语言包 */
    data class MissingLanguagePack(
        val languagePair: String,
        val message: String,
        val action: String = "下载语言包"
    ) : DependencyStatus()

    /** 缺少权限 */
    data class MissingPermission(
        val permission: String,
        val message: String,
        val action: String = "授予权限"
    ) : DependencyStatus()

    /** 语言不支持 */
    data class UnsupportedLanguage(
        val language: Language,
        val message: String,
        val action: String = "选择其他语言"
    ) : DependencyStatus()

    /** 多个依赖缺失 */
    data class MultipleMissing(
        val issues: List<DependencyStatus>,
        val message: String = "存在多个依赖问题"
    ) : DependencyStatus()
}

/**
 * 翻译依赖检查器
 *
 * 在翻译执行前检查当前方案的依赖条件，缺失时引导用户配置。
 *
 * ## 检查规则
 *
 * | 方案 | 翻译模式 | 需要检查 |
 * |------|----------|----------|
 * | VLM 云端 | - | API Key |
 * | 本地 OCR | LOCAL (ML Kit) | 语言包 |
 * | 本地 OCR | REMOTE (Cloud LLM) | API Key |
 * | 本地 OCR | HYBRID | 语言包 + API Key（可选） |
 *
 * ## 使用方法
 * ```kotlin
 * val checker = TranslationDependencyChecker(configManager, localTranslationEngine)
 * val status = checker.checkDependencies()
 * when (status) {
 *     is DependencyStatus.Satisfied -> { /* 执行翻译 */ }
 *     is DependencyStatus.MissingApiKey -> { /* 提示配置 API Key */ }
 *     is DependencyStatus.MissingLanguagePack -> { /* 提示下载语言包 */ }
 *     ...
 * }
 * ```
 *
 * @param configManager 配置管理器
 * @param localTranslationEngine 本地翻译引擎（用于检查语言包）
 */
class TranslationDependencyChecker(
    private val configManager: ConfigManager,
    private val localTranslationEngine: ILocalTranslationEngine
) {

    /**
     * 检查当前方案的依赖
     *
     * 根据当前选择的翻译方案（VLM 云端/本地 OCR）和翻译模式，
     * 检查所需的依赖条件是否满足。
     *
     * @return 依赖检查结果
     */
    suspend fun checkDependencies(): DependencyStatus {
        val useLocalOcrScheme = configManager.getUseLocalOcrScheme()

        return if (useLocalOcrScheme) {
            checkLocalOcrDependencies()
        } else {
            checkVlmCloudDependencies()
        }
    }

    /**
     * 检查 VLM 云端方案依赖
     *
     * VLM 云端方案需要 API Key 才能使用。
     *
     * @return 依赖检查结果
     */
    suspend fun checkVlmCloudDependencies(): DependencyStatus {
        val apiKey = configManager.getApiKey()

        return if (apiKey.isNullOrBlank()) {
            DependencyStatus.MissingApiKey(
                message = "VLM 云端翻译需要配置 API Key",
                action = "前往实验室设置页面配置 SiliconFlow API Key"
            )
        } else {
            DependencyStatus.Satisfied("VLM 云端翻译依赖已满足")
        }
    }

    /**
     * 检查本地 OCR 方案依赖
     *
     * 根据翻译模式检查不同的依赖：
     * - LOCAL: 需要语言包
     * - REMOTE: 需要 API Key
     * - HYBRID: 优先检查语言包，API Key 可选（降级时使用）
     *
     * @return 依赖检查结果
     */
    suspend fun checkLocalOcrDependencies(): DependencyStatus {
        val translationMode = configManager.getLocalOcrTranslationMode()
        val languageConfig = configManager.getLanguageConfig()

        return when (translationMode) {
            TranslationMode.LOCAL -> {
                // 仅本地模式：检查语言包
                checkLanguagePackDependency(languageConfig.sourceLanguage, languageConfig.targetLanguage)
            }

            TranslationMode.REMOTE -> {
                // 仅云端模式：检查 API Key
                checkApiKeyForLocalOcr()
            }

            TranslationMode.HYBRID -> {
                // 混合模式：检查语言包（必需），API Key 可选
                val langPackStatus = checkLanguagePackDependency(
                    languageConfig.sourceLanguage,
                    languageConfig.targetLanguage
                )

                if (langPackStatus is DependencyStatus.MissingLanguagePack) {
                    // 语言包缺失，返回语言包问题
                    langPackStatus
                } else {
                    // 语言包可用，检查 API Key（可选）
                    val apiKey = configManager.getApiKey()
                    if (apiKey.isNullOrBlank()) {
                        DependencyStatus.Satisfied(
                            "本地翻译可用（云端降级不可用，请配置 API Key 以启用降级功能）"
                        )
                    } else {
                        DependencyStatus.Satisfied("混合模式依赖已满足（本地 + 云端降级）")
                    }
                }
            }
        }
    }

    /**
     * 检查语言包依赖
     *
     * 检查本地翻译引擎是否已初始化并支持当前语言对。
     *
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 依赖检查结果
     */
    private suspend fun checkLanguagePackDependency(
        sourceLang: Language,
        targetLang: Language
    ): DependencyStatus {
        // 直接检查语言对是否可用（isLanguageAvailable 会查询 ML Kit 实际模型状态）
        // 不再依赖 isInitialized() 标志，因为语言包可能通过 LanguagePackManager 下载
        val isAvailable = localTranslationEngine.isLanguageAvailable(sourceLang, targetLang)

        return if (isAvailable) {
            val pairName = "${sourceLang.displayName} → ${targetLang.displayName}"
            DependencyStatus.Satisfied("语言包已就绪：$pairName")
        } else {
            DependencyStatus.MissingLanguagePack(
                languagePair = "${sourceLang.displayName} → ${targetLang.displayName}",
                message = "当前语言对的语言包未下载",
                action = "前往语言包管理页面下载语言包"
            )
        }
    }

    /**
     * 检查本地 OCR 方案中的 API Key（云端翻译模式）
     *
     * @return 依赖检查结果
     */
    private suspend fun checkApiKeyForLocalOcr(): DependencyStatus {
        val apiKey = configManager.getApiKey()

        return if (apiKey.isNullOrBlank()) {
            DependencyStatus.MissingApiKey(
                message = "云端翻译模式需要配置 API Key",
                action = "前往实验室设置页面配置 SiliconFlow API Key"
            )
        } else {
            DependencyStatus.Satisfied("云端翻译 API Key 已配置")
        }
    }

    /**
     * 检查特定语言对的本地翻译可用性
     *
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 是否可用及错误信息（如果不可用）
     */
    suspend fun checkLocalTranslationAvailable(
        sourceLang: Language,
        targetLang: Language
    ): Pair<Boolean, String?> {
        val isAvailable = localTranslationEngine.isLanguageAvailable(sourceLang, targetLang)

        return if (isAvailable) {
            true to null
        } else {
            false to "语言对 ${sourceLang.displayName} → ${targetLang.displayName} 的语言包未下载"
        }
    }

    /**
     * 获取友好的错误提示消息
     *
     * @param status 依赖检查结果
     * @return 用户友好的错误消息
     */
    fun getFriendlyMessage(status: DependencyStatus): String {
        return when (status) {
            is DependencyStatus.Satisfied -> status.message
            is DependencyStatus.MissingApiKey ->
                "${status.message}\n${status.action}"
            is DependencyStatus.MissingLanguagePack ->
                "${status.message}：${status.languagePair}\n${status.action}"
            is DependencyStatus.MissingPermission ->
                "${status.message}：${status.permission}\n${status.action}"
            is DependencyStatus.UnsupportedLanguage ->
                "${status.message}：${status.language.displayName}\n${status.action}"
            is DependencyStatus.MultipleMissing -> {
                val issues = status.issues.joinToString("\n") { getFriendlyMessage(it) }
                "${status.message}\n$issues"
            }
        }
    }
}
