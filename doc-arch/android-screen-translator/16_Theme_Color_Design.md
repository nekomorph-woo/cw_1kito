# APP 主题色更换方案设计文档

**Document Version:** 1.1
**Last Updated:** 2025-02-11
**Status:** DRAFT

---

## 1. 背景与目标

### 1.1 现状

- 项目使用 Material3 默认 `MaterialTheme`，无自定义 ColorScheme
- 无深色/浅色模式切换，完全跟随系统
- 悬浮球和覆盖层使用硬编码颜色（`FloatingBallView`、`OverlayRenderer`）
- 主题应用入口在 `MainActivity.kt` 的 `setContent` 中

### 1.2 目标

在实验室设置中提供主题色更换功能：
- 多种浅色主题（不同主色调）
- 多种深色主题（不同主色调）
- 用户可自由选择，实时预览切换
- 默认跟随系统深浅模式 + 使用默认配色
- 支持恢复默认设置

---

## 2. 主题方案设计

### 2.1 主题结构

采用 **色调 × 明暗** 二维组合模型：

```
主题 = 色调 (Hue) + 明暗模式 (Light/Dark)
```

**色调选项（预设 6 种）：**

| 色调名称 | Seed Color | 说明 |
|---------|------------|------|
| 默认蓝 | `#4285F4` | Material 默认蓝，当前风格 |
| 靛青 | `#006A6A` | 沉稳冷色调 |
| 紫罗兰 | `#7B5EA7` | 优雅紫色系 |
| 玫瑰 | `#B4637A` | 温暖粉色系 |
| 森林 | `#4A7C59` | 自然绿色系 |
| 琥珀 | `#C77B00` | 暖色调橙黄 |

**明暗模式选项：**

| 模式 | 说明 |
|------|------|
| 跟随系统 | 默认，根据系统设置自动切换 |
| 始终浅色 | 强制浅色模式 |
| 始终深色 | 强制深色模式 |

组合后用户可得到 6 色调 × 3 明暗 = 18 种主题体验。

### 2.2 技术方案：Material3 手动配色

使用 Material3 的 `lightColorScheme()` / `darkColorScheme()` 手动定义每种色调的完整配色方案。

每种色调需要定义的核心色槽：

```
primary / onPrimary / primaryContainer / onPrimaryContainer
secondary / onSecondary / secondaryContainer / onSecondaryContainer
tertiary / onTertiary / tertiaryContainer / onTertiaryContainer
background / onBackground
surface / onSurface / surfaceVariant / onSurfaceVariant
error / onError / errorContainer / onErrorContainer
outline / outlineVariant
```

---

## 3. 模块设计

### 3.1 主题数据模型

**新建文件:** `composeApp/src/commonMain/kotlin/com/cw2/cw_1kito/ui/theme/AppTheme.kt`

```kotlin
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
```

### 3.2 配色定义（完整 180 种颜色）

**新建文件:** `composeApp/src/commonMain/kotlin/com/cw2/cw_1kito/ui/theme/Color.kt`

为每种色调定义浅色和深色两套 ColorScheme。每种色调约 15 个色槽 × 2 = 30 个颜色值，共 6 × 30 = 180 个。

```kotlin
package com.cw2.cw_1kito.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// ========== 默认蓝 (Default Blue) ==========

val DefaultBlueLightScheme = lightColorScheme(
    primary = Color(0xFF4285F4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E3F7),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFFDFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C7CF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

val DefaultBlueDarkScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF1B6EF3),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F7),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2947),
    tertiaryContainer = Color(0xFF523F5E),
    onTertiaryContainer = Color(0xFFF2DAFF),
    background = Color(0xFF111316),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF111316),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C7CF),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// ========== 靛青 (Teal) ==========

val TealLightScheme = lightColorScheme(
    primary = Color(0xFF006A6A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF6FF7F7),
    onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E8),
    onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4A607C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD2E4FF),
    onTertiaryContainer = Color(0xFF041C35),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF191C1C),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF191C1C),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFFBEC9C8),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

val TealDarkScheme = darkColorScheme(
    primary = Color(0xFF4CDADB),
    onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF005050),
    onPrimaryContainer = Color(0xFF6FF7F7),
    secondary = Color(0xFFB0CCCB),
    onSecondary = Color(0xFF1B3434),
    secondaryContainer = Color(0xFF334A4A),
    onSecondaryContainer = Color(0xFFCCE8E8),
    tertiary = Color(0xFFB5C9EA),
    onTertiary = Color(0xFF1C314D),
    tertiaryContainer = Color(0xFF334865),
    onTertiaryContainer = Color(0xFFD2E4FF),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE0E3E2),
    surface = Color(0xFF101414),
    onSurface = Color(0xFFE0E3E2),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C8),
    outline = Color(0xFF889392),
    outlineVariant = Color(0xFF3F4948),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// ========== 紫罗兰 (Violet) ==========

val VioletLightScheme = lightColorScheme(
    primary = Color(0xFF7B5EA7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF2A1A4E),
    secondary = Color(0xFF635B70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9DEF7),
    onSecondaryContainer = Color(0xFF1F192B),
    tertiary = Color(0xFF7E525D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF31101D),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

val VioletDarkScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF413158),
    primaryContainer = Color(0xFF59427E),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCDC2DB),
    onSecondary = Color(0xFF342D41),
    secondaryContainer = Color(0xFF4B4358),
    onSecondaryContainer = Color(0xFFE9DEF7),
    tertiary = Color(0xFFEFB8C7),
    onTertiary = Color(0xFF4A2530),
    tertiaryContainer = Color(0xFF633B47),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF141317),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF141317),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// ========== 玫瑰 (Rose) ==========

val RoseLightScheme = lightColorScheme(
    primary = Color(0xFFB4637A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E3),
    onPrimaryContainer = Color(0xFF3F1F31),
    secondary = Color(0xFF75565C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E3),
    onSecondaryContainer = Color(0xFF2B151B),
    tertiary = Color(0xFF7C5636),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF2C1503),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1F1A1C),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1F1A1C),
    surfaceVariant = Color(0xFFF2DED4),
    onSurfaceVariant = Color(0xFF514345),
    outline = Color(0xFF837376),
    outlineVariant = Color(0xFFD6C2C7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

val RoseDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF5E3347),
    primaryContainer = Color(0xFF8B4A61),
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = Color(0xFFE3BEC5),
    onSecondary = Color(0xFF422930),
    secondaryContainer = Color(0xFF5A3F46),
    onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFFEFC1A0),
    onTertiary = Color(0xFF452A14),
    tertiaryContainer = Color(0xFF5E4028),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF171214),
    onBackground = Color(0xFFEBE0E1),
    surface = Color(0xFF171214),
    onSurface = Color(0xFFEBE0E1),
    surfaceVariant = Color(0xFF514345),
    onSurfaceVariant = Color(0xFFD6C2C7),
    outline = Color(0xFF9E8C90),
    outlineVariant = Color(0xFF514345),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// ========== 森林 (Forest) ==========

val ForestLightScheme = lightColorScheme(
    primary = Color(0xFF4A7C59),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCFFD8),
    onPrimaryContainer = Color(0xFF052F14),
    secondary = Color(0xFF5D6254),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E7D6),
    onSecondaryContainer = Color(0xFF1A1F15),
    tertiary = Color(0xFF3C6473),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC4E9FB),
    onTertiaryContainer = Color(0xFF001F29),
    background = Color(0xFFFAFDF7),
    onBackground = Color(0xFF191C19),
    surface = Color(0xFFFAFDF7),
    onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFDEE4D8),
    onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C8BD),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

val ForestDarkScheme = darkColorScheme(
    primary = Color(0xFFB0E0BD),
    onPrimary = Color(0xFF0C381F),
    primaryContainer = Color(0xFF334F34),
    onPrimaryContainer = Color(0xFFCCFFD8),
    secondary = Color(0xFFC6CABB),
    onSecondary = Color(0xFF2F3429),
    secondaryContainer = Color(0xFF454B3F),
    onSecondaryContainer = Color(0xFFE2E7D6),
    tertiary = Color(0xFFA8CDDE),
    onTertiary = Color(0xFF0F3443),
    tertiaryContainer = Color(0xFF244B5A),
    onTertiaryContainer = Color(0xFFC4E9FB),
    background = Color(0xFF121412),
    onBackground = Color(0xFFE2E3DD),
    surface = Color(0xFF121412),
    onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C8BD),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// ========== 琥珀 (Amber) ==========

val AmberLightScheme = lightColorScheme(
    primary = Color(0xFFC77B00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDC5),
    onPrimaryContainer = Color(0xFF3F1800),
    secondary = Color(0xFF725A43),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFDDDC4),
    onSecondaryContainer = Color(0xFF281805),
    tertiary = Color(0xFF58633A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE9B6),
    onTertiaryContainer = Color(0xFF171F06),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF2B1700),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF2B1700),
    surfaceVariant = Color(0xFFF2EDE2),
    onSurfaceVariant = Color(0xFF4C4639),
    outline = Color(0xFF7E7667),
    outlineVariant = Color(0xFFD0C7B7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

val AmberDarkScheme = darkColorScheme(
    primary = Color(0xFFFFB95D),
    onPrimary = Color(0xFF522900),
    primaryContainer = Color(0xFF973D00),
    onPrimaryContainer = Color(0xFFFFDDC5),
    secondary = Color(0xFFE0C1A5),
    onSecondary = Color(0xFF3E2E1A),
    secondaryContainer = Color(0xFF5A4430),
    onSecondaryContainer = Color(0xFFFDDDC4),
    tertiary = Color(0xFFC0CD9B),
    onTertiary = Color(0xFF2B350F),
    tertiaryContainer = Color(0xFF424D29),
    onTertiaryContainer = Color(0xFFDCE9B6),
    background = Color(0xFF1E1200),
    onBackground = Color(0xFFFFE8D6),
    surface = Color(0xFF1E1200),
    onSurface = Color(0xFFFFE8D6),
    surfaceVariant = Color(0xFF4C4639),
    onSurfaceVariant = Color(0xFFD0C7B7),
    outline = Color(0xFF999180),
    outlineVariant = Color(0xFF4C4639),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

// ========== 获取 ColorScheme 的工具函数 ==========

/**
 * 根据色调和明暗模式获取对应的 ColorScheme
 */
fun getColorScheme(hue: ThemeHue, isDark: Boolean): androidx.compose.material3.ColorScheme {
    return when (hue) {
        ThemeHue.DEFAULT -> if (isDark) DefaultBlueDarkScheme else DefaultBlueLightScheme
        ThemeHue.TEAL -> if (isDark) TealDarkScheme else TealLightScheme
        ThemeHue.VIOLET -> if (isDark) VioletDarkScheme else VioletLightScheme
        ThemeHue.ROSE -> if (isDark) RoseDarkScheme else RoseLightScheme
        ThemeHue.FOREST -> if (isDark) ForestDarkScheme else ForestLightScheme
        ThemeHue.AMBER -> if (isDark) AmberDarkScheme else AmberLightScheme
    }
}
```

### 3.3 主题提供者

**新建文件:** `composeApp/src/commonMain/kotlin/com/cw2/cw_1kito/ui/theme/Theme.kt`

```kotlin
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
```

### 3.4 ViewModel 扩展

**修改文件:** `composeApp/src/commonMain/kotlin/com/cw2/cw_1kito/ui/screen/SettingsScreen.kt`

在 `SettingsUiState` 中添加主题配置字段：

```kotlin
// 在 data class SettingsUiState 中追加
val themeConfig: ThemeConfig = ThemeConfig.DEFAULT,
```

在 `SettingsEvent` 中添加主题相关事件：

```kotlin
// 在 sealed class SettingsEvent 中追加
data class ThemeHueChanged(val hue: ThemeHue) : SettingsEvent()
data class DarkModeChanged(val darkMode: DarkModeOption) : SettingsEvent()
data object ResetTheme : SettingsEvent()
```

**修改文件:** `composeApp/src/commonMain/kotlin/com/cw2/cw_1kito/MainViewModel.kt`

在 `loadSavedConfig()` 中追加加载主题配置：

```kotlin
// loadSavedConfig() 中追加：
val themeConfig = configManager.getThemeConfig()
_uiState.update { it.copy(themeConfig = themeConfig) }
```

在 `onEvent()` 中追加事件处理分支：

```kotlin
// onEvent() 中追加：
is SettingsEvent.ThemeHueChanged -> {
    val newConfig = _uiState.value.themeConfig.copy(hue = event.hue)
    _uiState.update { it.copy(themeConfig = newConfig) }
    viewModelScope.launch {
        try {
            configManager.saveThemeConfig(newConfig)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "保存主题配置失败", e)
        }
    }
}
is SettingsEvent.DarkModeChanged -> {
    val newConfig = _uiState.value.themeConfig.copy(darkMode = event.darkMode)
    _uiState.update { it.copy(themeConfig = newConfig) }
    viewModelScope.launch {
        try {
            configManager.saveThemeConfig(newConfig)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "保存主题配置失败", e)
        }
    }
}
SettingsEvent.ResetTheme -> {
    val defaultConfig = ThemeConfig.DEFAULT
    _uiState.update { it.copy(themeConfig = defaultConfig) }
    viewModelScope.launch {
        try {
            configManager.saveThemeConfig(defaultConfig)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "重置主题配置失败", e)
        }
    }
}
```

### 3.5 配置持久化

**修改文件:** `composeApp/src/commonMain/kotlin/com/cw2/cw_1kito/data/config/ConfigManager.kt`（追加）

```kotlin
// 在 interface ConfigManager 中追加
/**
 * 获取主题配置
 */
suspend fun getThemeConfig(): ThemeConfig

/**
 * 保存主题配置
 */
suspend fun saveThemeConfig(config: ThemeConfig)
```

**修改文件:** `composeApp/src/commonMain/kotlin/com/cw2/cw_1kito/data/config/ConfigManagerImpl.kt`（追加）

```kotlin
// 在 companion object 中追加
private const val THEME_HUE_KEY = "theme_hue"
private const val DARK_MODE_KEY = "dark_mode"

// 在 ConfigManagerImpl 类中追加方法
override suspend fun getThemeConfig(): com.cw2.cw_1kito.ui.theme.ThemeConfig {
    val hueName = getString(THEME_HUE_KEY)
    val darkModeName = getString(DARK_MODE_KEY)

    val hue = com.cw2.cw_1kito.ui.theme.ThemeHue.fromName(hueName)
    val darkMode = com.cw2.cw_1kito.ui.theme.DarkModeOption.fromName(darkModeName)

    return com.cw2.cw_1kito.ui.theme.ThemeConfig(hue, darkMode)
}

override suspend fun saveThemeConfig(config: com.cw2.cw_1kito.ui.theme.ThemeConfig) {
    saveString(THEME_HUE_KEY, config.hue.name)
    saveString(DARK_MODE_KEY, config.darkMode.name)
}
```

同时在 `clearAll()` 方法中追加清除主题配置：

```kotlin
// clearAll() 中追加
remove(THEME_HUE_KEY)
remove(DARK_MODE_KEY)
```

### 3.6 主题应用入口

**修改文件:** `composeApp/src/androidMain/kotlin/com/cw2/cw_1kito/MainActivity.kt`

将现有的 `MaterialTheme { ... }` 替换为 `KitoTheme`：

```kotlin
// 之前 (第 72-80 行)
setContent {
    MaterialTheme {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        MainScreen(
            uiState = uiState,
            onEvent = { event -> handleEvent(event) }
        )
    }
}

// 之后
setContent {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    KitoTheme(themeConfig = uiState.themeConfig) {
        MainScreen(
            uiState = uiState,
            onEvent = { event -> handleEvent(event) }
        )
    }
}
```

添加导入语句：

```kotlin
import com.cw2.cw_1kito.ui.theme.KitoTheme
```

### 3.7 实验室 - 主题设置 UI

**修改文件:** `composeApp/src/commonMain/kotlin/com/cw2/cw_1kito/ui/screen/LabSettingsScreen.kt`

在实验室页面中增加主题色更换区域：

```
┌─────────────────────────────────┐
│ ← 实验室                        │
├─────────────────────────────────┤
│                                 │
│ 实验性功能                       │
│ 以下功能仍在测试中，可能不稳定     │
│                                 │
│ ─── 流式翻译 ─────────── [OFF] │
│ 翻译结果逐条显示，减少等待时间     │
│                                 │
│ ─── 主题色 ─────────────────── │
│                                 │
│ 明暗模式                         │
│ ○ 跟随系统  ○ 始终浅色  ○ 始终深色│
│                                 │
│ 色调                             │
│ ┌────┐ ┌────┐ ┌────┐           │
│ │ 🔵 │ │ 🟢 │ │ 🟣 │           │
│ │默认蓝│ │ 靛青 │ │紫罗兰│          │
│ └────┘ └────┘ └────┘           │
│ ┌────┐ ┌────┐ ┌────┐           │
│ │ 🌸 │ │ 🌲 │ │ 🟠 │           │
│ │ 玫瑰 │ │ 森林 │ │ 琥珀 │          │
│ └────┘ └────┘ └────┘           │
│                                 │
│ ┌─────────────────────────────┐ │
│ │ 预览区域                      │ │
│ │ ┌─────────────────────────┐ │ │
│ │ │ 主要按钮 │ 次要按钮 │     │ │
│ │ ├─────────────────────────┤ │ │
│ │ │ 卡片内容示例              │ │ │
│ │ │ 显示文本效果              │ │ │
│ │ └─────────────────────────┘ │ │
│ └─────────────────────────────┘ │
│                                 │
│ [恢复默认主题]                    │
│                                 │
└─────────────────────────────────┘
```

完整实现代码：

```kotlin
package com.cw2.cw_1kito.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cw2.cw_1kito.ui.theme.*

/**
 * 实验室设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabSettingsScreen(
    streamingEnabled: Boolean,
    onStreamingEnabledChange: (Boolean) -> Unit,
    themeConfig: ThemeConfig = ThemeConfig.DEFAULT,
    onThemeHueChange: (ThemeHue) -> Unit = {},
    onDarkModeChange: (DarkModeOption) -> Unit = {},
    onResetTheme: () -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("实验室") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(
                            text = "←",
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 24.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 标题说明
            Text(
                text = "实验性功能",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "以下功能仍在测试中，可能不稳定",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 流式翻译开关
            LabSwitchItem(
                title = "流式翻译",
                description = "翻译结果逐条显示，减少等待时间",
                checked = streamingEnabled,
                onCheckedChange = onStreamingEnabledChange
            )

            Divider()

            // 主题色设置区域
            ThemeSettingsSection(
                currentTheme = themeConfig,
                onThemeHueChange = onThemeHueChange,
                onDarkModeChange = onDarkModeChange,
                onResetTheme = onResetTheme
            )
        }
    }
}

/**
 * 主题设置区域
 */
@Composable
private fun ThemeSettingsSection(
    currentTheme: ThemeConfig,
    onThemeHueChange: (ThemeHue) -> Unit,
    onDarkModeChange: (DarkModeOption) -> Unit,
    onResetTheme: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "主题色",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // 明暗模式选择
        Text(
            text = "明暗模式",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DarkModeOption.values().forEach { option ->
                DarkModeOptionChip(
                    option = option,
                    selected = currentTheme.darkMode == option,
                    onClick = { onDarkModeChange(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 色调选择
        Text(
            text = "色调",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        // 色调网格：2 行 3 列
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val firstRow = listOf(ThemeHue.DEFAULT, ThemeHue.TEAL, ThemeHue.VIOLET)
            val secondRow = listOf(ThemeHue.ROSE, ThemeHue.FOREST, ThemeHue.AMBER)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                firstRow.forEach { hue ->
                    HueSelector(
                        hue = hue,
                        selected = currentTheme.hue == hue,
                        onClick = { onThemeHueChange(hue) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                secondRow.forEach { hue ->
                    HueSelector(
                        hue = hue,
                        selected = currentTheme.hue == hue,
                        onClick = { onThemeHueChange(hue) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 预览区域
        ThemePreviewCard()

        // 恢复默认按钮
        OutlinedButton(
            onClick = onResetTheme,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("恢复默认主题")
        }
    }
}

/**
 * 明暗模式选项芯片
 */
@Composable
private fun DarkModeOptionChip(
    option: DarkModeOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = option.displayName,
                style = MaterialTheme.typography.bodySmall
            )
        },
        modifier = modifier
    )
}

/**
 * 色调选择器
 */
@Composable
private fun HueSelector(
    hue: ThemeHue,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(hue.seedColor)
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text(
                    text = "✓",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Text(
            text = hue.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * 主题预览卡片
 */
@Composable
private fun ThemePreviewCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "预览",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f)
                ) {
                    Text("主要按钮", style = MaterialTheme.typography.bodySmall)
                }

                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f)
                ) {
                    Text("次要按钮", style = MaterialTheme.typography.bodySmall)
                }
            }

            // 卡片示例
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "卡片示例",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "这里显示文本效果",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/**
 * 实验室开关项
 */
@Composable
private fun LabSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
```

**修改文件:** `composeApp/src/commonMain/kotlin/com/cw2/cw_1kito/ui/screen/MainScreen.kt`

更新 `LabSettingsScreen` 调用，传入主题相关参数：

```kotlin
// 在 MainScreen 的 if (showLabSettings) 分支中
LabSettingsScreen(
    streamingEnabled = uiState.streamingEnabled,
    onStreamingEnabledChange = { onEvent(SettingsEvent.StreamingEnabledChanged(it)) },
    themeConfig = uiState.themeConfig,
    onThemeHueChange = { hue -> onEvent(SettingsEvent.ThemeHueChanged(hue)) },
    onDarkModeChange = { mode -> onEvent(SettingsEvent.DarkModeChanged(mode)) },
    onResetTheme = { onEvent(SettingsEvent.ResetTheme) },
    onNavigateBack = { showLabSettings = false }
)
```

### 3.8 悬浮球/覆盖层适配（可选，后续优化）

`FloatingBallView` 和 `OverlayRenderer` 使用硬编码颜色，不受 MaterialTheme 影响。
如需适配主题色，可通过 Service 启动时从 ConfigManager 读取当前色调，传入对应颜色值。

这部分属于锦上添花，可在主体功能完成后再做。

---

## 4. 修改文件清单（按依赖关系排序）

| 顺序 | 文件路径 | 操作 | 说明 |
|-----|---------|------|------|
| 1 | `commonMain/.../ui/theme/AppTheme.kt` | **新建** | ThemeHue、DarkModeOption、ThemeConfig 数据模型 |
| 2 | `commonMain/.../ui/theme/Color.kt` | **新建** | 6 种色调 × 2 明暗 = 12 套 ColorScheme 定义 (180 个颜色值) |
| 3 | `commonMain/.../ui/theme/Theme.kt` | **新建** | KitoTheme Composable，根据配置选择 ColorScheme |
| 4 | `commonMain/.../ui/screen/SettingsScreen.kt` | 修改 | SettingsUiState 追加 themeConfig，SettingsEvent 追加主题事件 |
| 5 | `commonMain/.../MainViewModel.kt` | 修改 | loadSavedConfig 追加主题加载，onEvent 追加主题事件处理 |
| 6 | `commonMain/.../data/config/ConfigManager.kt` | 修改 | 追加 getThemeConfig/saveThemeConfig 接口 |
| 7 | `commonMain/.../data/config/ConfigManagerImpl.kt` | 修改 | 实现主题配置存取，clearAll 追加清除 |
| 8 | `androidMain/.../MainActivity.kt` | 修改 | MaterialTheme → KitoTheme |
| 9 | `commonMain/.../ui/screen/MainScreen.kt` | 修改 | LabSettingsScreen 调用传入主题参数 |
| 10 | `commonMain/.../ui/screen/LabSettingsScreen.kt` | 修改 | 增加完整主题选择 UI（明暗模式、色调选择器、预览区域、恢复默认） |

---

## 5. 与流式翻译方案的关系

两个实验室功能完全独立，共享同一个实验室入口页面：

```
设置页面
  └── 实验室（入口按钮）
        ├── 流式翻译 [开关]        ← 15_Streaming_Translation_Design.md
        └── 主题色 [色调选择+明暗]  ← 本文档
```

---

## 6. 验证方案

1. **默认状态：** 安装后主题与当前一致（默认蓝 + 跟随系统）
2. **色调切换：** 在实验室中选择不同色调 → 返回设置页面 → 配色立刻变化
3. **明暗切换：** 选择"始终深色" → 全局切换为深色模式，不受系统设置影响
4. **持久化：** 切换主题后杀掉 APP 重启 → 主题保持
5. **全局一致性：** 所有使用 MaterialTheme.colorScheme.xxx 的组件自动适配新配色
6. **恢复默认：** 点击"恢复默认主题" → 主题重置为默认蓝 + 跟随系统
7. **预览效果：** 在实验室选择色调时，预览区域实时展示当前配色效果
