package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 支持的语言枚举
 */
@Serializable
enum class Language(val code: String, val displayName: String) {
    AUTO("auto", "自动识别"),
    EN("en", "English"),
    ZH("zh", "中文"),
    JA("ja", "日本語"),
    KO("ko", "한국어"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    ES("es", "Español");

    companion object {
        fun fromCode(code: String): Language? =
            values().find { it.code == code }
    }
}
