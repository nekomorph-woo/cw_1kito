package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * OCR 识别语言
 *
 * 对应 Google ML Kit 支持的文字识别语言
 */
@Serializable
enum class OcrLanguage(val code: String, val displayName: String) {
    /** 中文识别器 */
    CHINESE("zh", "中文"),

    /** 日文识别器 */
    JAPANESE("ja", "日本語"),

    /** 韩文识别器 */
    KOREAN("ko", "한국어"),

    /** 英文/拉丁语系识别器 */
    LATIN("en", "English/Latin");

    companion object {
        /**
         * 从语言代码获取 OCR 语言
         */
        fun fromCode(code: String): OcrLanguage? {
            return values().find { it.code == code }
        }

        /**
         * 从翻译语言推断 OCR 语言
         *
         * 智能推断规则：
         * - 如果源语言明确指定，使用源语言
         * - 如果源语言是 AUTO，根据目标语言推断：
         *   - 目标是中文 -> 原文可能是英文/日文/韩文，优先英文
         *   - 目标是英文 -> 原文可能是中文/日文/韩文，优先中文
         *   - 目标是日文 -> 原文可能是中文/英文，优先中文
         *   - 目标是韩文 -> 原文可能是中文/英文，优先中文
         *
         * @param sourceLanguage 源语言
         * @param targetLanguage 目标语言
         * @return 推断的 OCR 语言
         */
        fun inferFromTranslationLanguage(
            sourceLanguage: Language,
            targetLanguage: Language
        ): OcrLanguage {
            // 如果源语言明确指定，直接使用
            if (sourceLanguage != Language.AUTO) {
                return when (sourceLanguage) {
                    Language.ZH -> CHINESE
                    Language.JA -> JAPANESE
                    Language.KO -> KOREAN
                    else -> LATIN
                }
            }

            // 源语言是 AUTO，根据目标语言推断
            return when (targetLanguage) {
                Language.ZH -> LATIN  // 翻译成中文，原文可能是英文
                Language.EN -> CHINESE  // 翻译成英文，原文可能是中文
                Language.JA -> CHINESE  // 翻译成日文，原文可能是中文
                Language.KO -> CHINESE  // 翻译成韩文，原文可能是中文
                else -> LATIN  // 其他情况默认英文
            }
        }
    }
}
