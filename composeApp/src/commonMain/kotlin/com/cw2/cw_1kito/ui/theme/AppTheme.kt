package com.cw2.cw_1kito.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 色调枚举
 */
enum class ThemeHue(val displayName: String, val seedColor: Color) {
    DEFAULT("默认蓝", Color(0xFF4285F4)),
    TEAL("靛青", Color(0xFF006A6A)),
    VIOLET("紫罗兰", Color(0xFF7B5EA7)),
    ROSE("玫瑰", Color(0xFFB4637A)),
    FOREST("森林", Color(0xFF4A7C59)),
    AMBER("琥珀", Color(0xFFC77B00));

    companion object {
        fun fromName(name: String?): ThemeHue {
            return values().find { it.name == name } ?: DEFAULT
        }
    }
}

/**
 * 明暗模式选项
 */
enum class DarkModeOption(val displayName: String) {
    FOLLOW_SYSTEM("跟随系统"),
    ALWAYS_LIGHT("始终浅色"),
    ALWAYS_DARK("始终深色");

    companion object {
        fun fromName(name: String?): DarkModeOption {
            return values().find { it.name == name } ?: FOLLOW_SYSTEM
        }
    }
}

/**
 * 完整主题配置
 */
data class ThemeConfig(
    val hue: ThemeHue = ThemeHue.DEFAULT,
    val darkMode: DarkModeOption = DarkModeOption.FOLLOW_SYSTEM
) {
    companion object {
        val DEFAULT = ThemeConfig()
    }
}
