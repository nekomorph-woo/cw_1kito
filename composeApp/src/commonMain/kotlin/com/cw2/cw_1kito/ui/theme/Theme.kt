package com.cw2.cw_1kito.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

/**
 * Kito 应用主题
 *
 * @param themeConfig 主题配置
 * @param content 主题内容
 */
@Composable
fun KitoTheme(
    themeConfig: ThemeConfig = ThemeConfig.DEFAULT,
    content: @Composable () -> Unit
) {
    val isDark = when (themeConfig.darkMode) {
        DarkModeOption.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkModeOption.ALWAYS_LIGHT -> false
        DarkModeOption.ALWAYS_DARK -> true
    }

    val colorScheme = getColorScheme(themeConfig.hue, isDark)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
