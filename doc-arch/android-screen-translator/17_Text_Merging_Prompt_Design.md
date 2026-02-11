# 文本提取合并提示词功能设计方案

> **文档版本:** 1.2
> **创建日期:** 2025-02-11
> **更新日期:** 2025-02-11
> **功能类别:** 实验室功能
> **依赖:** 无新依赖

## 更新日志

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| 1.2 | 2025-02-11 | 修正 ConfigManagerImpl 持久化实现与现有代码模式一致；修正参数添加位置为 TranslationConfig；补充 clearAll() 和 loadSavedConfig() 更新；修正组件变更清单文件名 |
| 1.1 | 2025-02-11 | 修复 `data object` 语法错误；补充 ConfigManagerImpl 实现细节；补充 UI Tab 切换实现；补充调用链说明；移除测试章节 |
| 1.0 | 2025-02-11 | 初始版本 |

---

## 1. 功能概述

在实验室中新增"文本提取合并"开关，开启时使用专门的合并提示词请求 VLM，将位置靠近的文本合并到一个提取对象中，而非逐句分隔。

### 1.1 核心特性

- **非替换式设计**：新提示词与原有提示词并存，二选一使用
- **独立编辑**：两个提示词在主界面通过横向 Tab 切换编辑
- **独立重置**：每个提示词有独立的重置按钮
- **持久化存储**：两个提示词均持久化存储，与默认值一致时清除持久化

---

## 2. UI 设计

### 2.1 主界面布局

```
┌─────────────────────────────────────────────────────┐
│ Kito 设置                              [⚙ 实验室]  │
├─────────────────────────────────────────────────────┤
│                                                     │
│ ┌─ 提示词配置 ────────────────────────────────────┐ │
│ │ [标准提示词] [合并提示词]                        │ │
│ │ ─────────                                      │ │
│ │                                               │ │
│ │ ┌─────────────────────────────────────────────┐ │ │
│ │ │ 提示词编辑框                               │ │ │
│ │ │ (根据选中 Tab 显示对应提示词)              │ │ │
│ │ │                                             │ │ │
│ │ │                                             │ │ │
│ │ └─────────────────────────────────────────────┘ │ │
│ │                                               │ │
│ │              [重置为默认]                       │ │
│ └───────────────────────────────────────────────┘ │
│                                                     │
│ ... 其他配置区域 ...                               │
└─────────────────────────────────────────────────────┘
```

### 2.2 实验室页面布局

开关顺序：流式翻译 → 文本提取合并 → 主题色

```
┌─────────────────────────────────────────────────────┐
│ < 实验室                                            │
├─────────────────────────────────────────────────────┤
│                                                     │
│ 实验性功能                                           │
│ 以下功能仍在测试中，可能不稳定                        │
│                                                     │
│ ┌─────────────────────────────────────────────────┐ │
│ │ 流式翻译                         [开关]        │ │
│ │ 翻译结果逐条显示，减少等待时间                  │ │
│ └─────────────────────────────────────────────────┘ │
│                                                     │
│ ┌─────────────────────────────────────────────────┐ │
│ │ 文本提取合并                     [开关]        │ │
│ │ 将位置靠近的文本合并到同一对象，而非逐句分隔    │ │
│ └─────────────────────────────────────────────────┘ │
│                                                     │
│ ───────────────── 主题色 ─────────────────          │
│ ...                                                │
└─────────────────────────────────────────────────────┘
```

### 2.3 交互说明

| 元素 | 行为 |
|------|------|
| Tab 切换 | 点击切换显示/编辑对应的提示词 |
| 编辑框 | 输入内容自动保存到当前选中的提示词 |
| 重置按钮 | 重置当前 Tab 中的提示词为默认值 |
| 合并开关 | 开启时使用合并提示词请求 VLM |

---

## 3. 数据模型

### 3.1 扩展 ConfigManager

```kotlin
interface ConfigManager {
    // ... 现有方法 ...

    /**
     * 获取文本合并提示词（null 表示使用默认）
     */
    suspend fun getTextMergingPrompt(): String?

    /**
     * 保存文本合并提示词
     */
    suspend fun saveTextMergingPrompt(prompt: String?)

    /**
     * 获取文本合并功能开关状态
     */
    suspend fun getTextMergingEnabled(): Boolean

    /**
     * 保存文本合并功能开关状态
     */
    suspend fun saveTextMergingEnabled(enabled: Boolean)
}
```

### 3.2 扩展 SettingsUiState

```kotlin
// 在 SettingsScreen.kt 中作为顶层定义
enum class PromptTab {
    Standard,   // 标准提示词
    Merging     // 合并提示词
}

data class SettingsUiState(
    // ... 现有字段 ...

    // 新增字段
    val textMergingEnabled: Boolean = false,
    val textMergingPrompt: String = "",

    // 提示词 Tab 选中状态
    val selectedPromptTab: PromptTab = PromptTab.Standard
)
```

### 3.3 扩展 SettingsEvent

```kotlin
sealed class SettingsEvent {
    // ... 现有事件 ...

    data class PromptTabChanged(val tab: PromptTab) : SettingsEvent()
    data class TextMergingPromptChanged(val prompt: String) : SettingsEvent()
    data object ResetTextMergingPrompt : SettingsEvent()
    data class TextMergingEnabledChanged(val enabled: Boolean) : SettingsEvent()
}
```

---

## 4. API 调用逻辑

### 4.1 提示词选择逻辑

```kotlin
class TranslationApiClientImpl {

    companion object {
        // 标准提示词（现有）
        const val DEFAULT_PROMPT = "..."

        // 合并提示词（新增）
        const val DEFAULT_MERGING_PROMPT = """
            You are an OCR and translation engine with text merging capability.
            The image resolution is {{imageWidth}}x{{imageHeight}} pixels.

            Your task:
            1. Extract ALL visible text in the image.
            2. Merge nearby text blocks that belong to the same logical content:
               - Text in the same paragraph/section
               - Text in the same speech bubble or UI container
               - Text that flows across multiple lines naturally
            3. Translate each merged text block to {{targetLanguage}}.
            4. Provide bounding box that covers the entire merged region.

            Rules:
            - MERGE text blocks that are spatially close and logically related
            - Keep separate text blocks that are clearly independent (e.g., different UI elements)
            - Coordinates must be pixel values covering the full merged region
            - SKIP: page numbers, decorative single characters, navigation elements

            Return ONLY a valid JSON array, no markdown:
            [{"original_text":"...","translated_text":"...","coordinates":[left,top,right,bottom]}]
        """.trimIndent()

        /**
         * 根据模式获取默认提示词
         */
        fun getDefaultPrompt(useMerging: Boolean): String {
            return if (useMerging) DEFAULT_MERGING_PROMPT else DEFAULT_PROMPT
        }
    }

    private fun buildPrompt(
        targetLanguage: Language,
        imageWidth: Int,
        imageHeight: Int,
        customPrompt: String? = null,
        useMergingPrompt: Boolean = false
    ): String {
        val template = when {
            useMergingPrompt -> customPrompt ?: DEFAULT_MERGING_PROMPT
            else -> customPrompt ?: DEFAULT_PROMPT
        }
        return template
            .replace("{{targetLanguage}}", targetLanguage.displayName)
            .replace("{{imageWidth}}", imageWidth.toString())
            .replace("{{imageHeight}}", imageHeight.toString())
    }
}
```

### 4.2 领域模型扩展

**注意：** 参数应添加到 `TranslationConfig`（领域模型）而非 `TranslationApiRequest`（API 层模型）。

```kotlin
// 在 TranslationConfig.kt 中
data class TranslationConfig(
    val model: VlmModel,
    val targetLanguage: Language,
    val sourceLanguage: Language = Language.AUTO,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2000,
    val customPrompt: String? = null,
    val useMergingPrompt: Boolean = false     // 新增
)
```

### 4.3 调用链传递

开关状态需要通过以下调用链传递：

```
MainViewModel.startTranslation()
    ↓ (获取 uiState.textMergingEnabled)
TranslationManager.translate(config = TranslationConfig(..., useMergingEnabled = ...))
    ↓ (构建请求)
TranslationApiRequest / SiliconFlowRequest 构建
    ↓
TranslationApiClientImpl.buildPrompt(..., useMergingPrompt)
```

**关键点：** `useMergingEnabled` 从 ViewModel → TranslationConfig → API 调用层层传递。

---

## 5. 实现步骤

### 阶段 1：数据层扩展
1. 在 `ConfigManager` 接口添加新方法
2. 在 `ConfigManagerImpl` 实现持久化存储（DataStore）
3. 定义默认合并提示词常量

### 阶段 2：ViewModel 扩展
1. 扩展 `SettingsUiState` 添加新字段
2. 扩展 `SettingsEvent` 添加新事件
3. 在 `MainViewModel` 中实现新事件处理
4. 在 `loadSavedConfig()` 中加载合并提示词配置

### 阶段 3：UI 实现
1. 在 `SettingsScreen.kt` 顶层创建 `PromptTab` 枚举
2. 重构 `PromptSection` 组件支持 Tab 切换
3. 扩展 `LabSettingsScreen` 函数签名，添加合并开关参数
4. 实现重置按钮的独立逻辑

### 阶段 4：API 集成
1. 在 `TranslationApiRequest` 添加 `useMergingPrompt` 参数
2. 修改 `buildPrompt()` 方法根据参数选择提示词
3. 在调用链中传递开关状态

---

## 6. 组件变更清单

| 文件 | 变更类型 | 说明 |
|------|----------|------|
| `ConfigManager.kt` | 新增方法 | 添加合并提示词相关接口 |
| `ConfigManagerImpl.kt` | 实现 | 持久化实现（含新增键定义、clearAll() 更新）|
| `TranslationConfig.kt` | 修改 | 添加 `useMergingPrompt` 参数 |
| `MainViewModel.kt` | 修改 | 新状态、新事件处理、loadSavedConfig() 更新 |
| `SettingsScreen.kt` | 修改 | 新增 `PromptTab` 枚举，`PromptSection` 支持 Tab |
| `LabSettingsScreen.kt` | 修改 | 扩展函数签名，添加合并开关 |
| `TranslationApiClientImpl.kt` | 修改 | 添加合并提示词默认值及 `getDefaultPrompt()` 方法 |

---

## 7. 持久化实现

### 7.1 DataStore 键定义

```kotlin
// 在 ConfigManagerImpl.kt 中
private val TEXT_MERGING_ENABLED_KEY = booleanPreferencesKey("text_merging_enabled")
private val TEXT_MERGING_PROMPT_KEY = stringPreferencesKey("text_merging_prompt")
```

### 7.2 ConfigManagerImpl 实现

**注意：** 现有代码使用 `saveString/getString` 统一方式，不使用 `booleanPreferencesKey`。

```kotlin
override suspend fun getTextMergingEnabled(): Boolean {
    return getString(TEXT_MERGING_ENABLED_KEY)?.toBoolean() ?: false
}

override suspend fun saveTextMergingEnabled(enabled: Boolean) {
    saveString(TEXT_MERGING_ENABLED_KEY, enabled.toString())
}

override suspend fun getTextMergingPrompt(): String? {
    return getString(TEXT_MERGING_PROMPT_KEY)
}

override suspend fun saveTextMergingPrompt(prompt: String?) {
    if (prompt == null) {
        remove(TEXT_MERGING_PROMPT_KEY)
    } else {
        saveString(TEXT_MERGING_PROMPT_KEY, prompt)
    }
}
```

### 7.3 clearAll() 更新

```kotlin
override suspend fun clearAll() {
    remove(PreferencesKeys().apiKey)
    remove(PreferencesKeys().sourceLanguage)
    remove(PreferencesKeys().targetLanguage)
    remove(PreferencesKeys().selectedModel)
    remove(CUSTOM_PROMPT_KEY)
    remove(STREAMING_ENABLED_KEY)
    remove(THEME_HUE_KEY)
    remove(DARK_MODE_KEY)
    remove(TEXT_MERGING_ENABLED_KEY)  // 新增
    remove(TEXT_MERGING_PROMPT_KEY)   // 新增
}
```

---

## 8. UI 实现细节

### 8.1 PromptSection Tab 切换

```kotlin
@Composable
fun PromptSection(
    customPrompt: String,
    mergingPrompt: String,
    selectedTab: PromptTab,
    onPromptChange: (String) -> Unit,
    onTabChange: (PromptTab) -> Unit,
    onResetPrompt: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "提示词配置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Tab 切换
        // 注意：.entries 需要 Kotlin 1.9+，项目当前版本 2.3.0 已支持
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            PromptTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabChange(tab) },
                    text = { Text(tab.displayName) }
                )
            }
        }

        // 根据选中 Tab 显示对应编辑框
        val currentPrompt = when (selectedTab) {
            PromptTab.Standard -> customPrompt
            PromptTab.Merging -> mergingPrompt
        }
        val isDefault = when (selectedTab) {
            PromptTab.Standard -> currentPrompt == TranslationApiClientImpl.DEFAULT_PROMPT
            PromptTab.Merging -> currentPrompt == TranslationApiClientImpl.DEFAULT_MERGING_PROMPT
        }

        OutlinedTextField(
            value = currentPrompt,
            onValueChange = onPromptChange,
            label = { Text("提示词") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp),
            maxLines = 20,
            textStyle = MaterialTheme.typography.bodySmall
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = onResetPrompt,
                enabled = !isDefault
            ) {
                Text("重置为默认")
            }
        }
    }
}

// PromptTab 扩展属性
val PromptTab.displayName: String
    get() = when (this) {
        PromptTab.Standard -> "标准提示词"
        PromptTab.Merging -> "合并提示词"
    }
```

### 8.2 LabSettingsScreen 参数扩展

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabSettingsScreen(
    streamingEnabled: Boolean,
    onStreamingEnabledChange: (Boolean) -> Unit,
    textMergingEnabled: Boolean = false,
    onTextMergingEnabledChange: (Boolean) -> Unit = {},
    themeConfig: ThemeConfig = ThemeConfig.DEFAULT,
    onThemeHueChange: (ThemeHue) -> Unit = {},
    onDarkModeChange: (DarkModeOption) -> Unit = {},
    onResetTheme: () -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ... 现有代码 ...

    // 在流式翻译开关后添加
    LabSwitchItem(
        title = "文本提取合并",
        description = "将位置靠近的文本合并到同一对象，而非逐句分隔",
        checked = textMergingEnabled,
        onCheckedChange = onTextMergingEnabledChange
    )
}
```

---

## 9. ViewModel 扩展

### 9.1 事件处理

```kotlin
// 在 MainViewModel.onEvent() 中添加
is SettingsEvent.PromptTabChanged -> {
    _uiState.update { it.copy(selectedPromptTab = event.tab) }
}
is SettingsEvent.TextMergingPromptChanged -> {
    _uiState.update { it.copy(textMergingPrompt = event.prompt) }
    viewModelScope.launch {
        try {
            // 与标准提示词保持一致的持久化逻辑
            if (event.prompt == TranslationApiClientImpl.DEFAULT_MERGING_PROMPT) {
                configManager.saveTextMergingPrompt(null)
            } else {
                configManager.saveTextMergingPrompt(event.prompt)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "保存合并提示词失败", e)
        }
    }
}
SettingsEvent.ResetTextMergingPrompt -> {
    _uiState.update {
        it.copy(textMergingPrompt = TranslationApiClientImpl.DEFAULT_MERGING_PROMPT)
    }
    viewModelScope.launch {
        try {
            configManager.saveTextMergingPrompt(null)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "重置合并提示词失败", e)
        }
    }
}
is SettingsEvent.TextMergingEnabledChanged -> {
    _uiState.update { it.copy(textMergingEnabled = event.enabled) }
    viewModelScope.launch {
        try {
            configManager.saveTextMergingEnabled(event.enabled)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "保存文本合并开关失败", e)
        }
    }
}
```

### 9.2 loadSavedConfig() 更新

```kotlin
private fun loadSavedConfig() {
    viewModelScope.launch {
        try {
                // ... 现有加载逻辑 ...

                // 加载文本合并配置（新增）
                val mergingEnabled = configManager.getTextMergingEnabled()
                val mergingPrompt = configManager.getTextMergingPrompt()
                _uiState.update { it.copy(
                    textMergingEnabled = mergingEnabled,
                    textMergingPrompt = mergingPrompt ?: TranslationApiClientImpl.DEFAULT_MERGING_PROMPT
                ) }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "加载配置失败", e)
        }
    }
}
```

---

## 10. 未来扩展

- 支持用户自定义更多提示词模板（如：仅 OCR 不翻译、特定领域术语优化等）
- 提示词导入/导出功能
- 提示词 A/B 测试能力
