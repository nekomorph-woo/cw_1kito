package com.cw2.cw_1kito.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cw2.cw_1kito.data.api.TranslationApiClientImpl
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.LanguageModelState
import com.cw2.cw_1kito.model.OcrLanguage
import com.cw2.cw_1kito.model.VlmModel
import com.cw2.cw_1kito.model.LlmModel
import com.cw2.cw_1kito.model.TranslationMode
import com.cw2.cw_1kito.model.getDefaultLanguageModelStates
import com.cw2.cw_1kito.model.getDefaultLanguagePackStates
import com.cw2.cw_1kito.ui.component.LanguageSection
import com.cw2.cw_1kito.ui.component.ModelSection
import com.cw2.cw_1kito.ui.component.PermissionSection
import com.cw2.cw_1kito.ui.component.SchemeSwitcher
import com.cw2.cw_1kito.ui.component.TranslationModeSelector
import com.cw2.cw_1kito.ui.component.CloudLlmPromptEditor
import com.cw2.cw_1kito.ui.component.ModelPoolSelector
import com.cw2.cw_1kito.model.ModelPoolConfig
import com.cw2.cw_1kito.ui.theme.ThemeConfig

/**
 * 提示词标签页枚举
 */
enum class PromptTab {
    Standard,
    Merging;

    val displayName: String
        @Composable
        get() = when (this) {
            Standard -> "标准翻译"
            Merging -> "文本合并"
        }
}

/**
 * 主界面翻译方案标签页枚举
 */
enum class TranslationSchemeTab {
    VLM_CLOUD,
    LOCAL_OCR;

    val displayName: String
        get() = when (this) {
            VLM_CLOUD -> "VLM云端翻译"
            LOCAL_OCR -> "本地OCR翻译"
        }
}

/**
 * 语言包下载提示信息
 *
 * @param languagePair 语言对显示文本，如 "中文 → 英文"
 * @param estimatedSize 下载大小估算，如 "约 30MB"
 * @param isWifiConnected 是否连接 Wi-Fi
 */
data class LanguagePackPrompt(
    val languagePair: String,
    val estimatedSize: String,
    val isWifiConnected: Boolean
)

/**
 * 设置界面 UI 状态
 */
data class SettingsUiState(
    val apiKey: String = "",
    val isApiKeyValid: Boolean = false,
    val sourceLanguage: Language = Language.AUTO,
    val targetLanguage: Language = Language.ZH,
    val selectedModel: VlmModel = VlmModel.DEFAULT,
    val customPrompt: String = "",
    val hasOverlayPermission: Boolean = false,
    val hasBatteryOptimizationDisabled: Boolean = false,
    val hasScreenCapturePermission: Boolean = false,
    val canStartService: Boolean = false,
    val streamingEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val themeConfig: ThemeConfig = ThemeConfig.DEFAULT,
    val textMergingEnabled: Boolean = false,
    val textMergingPrompt: String = "",
    val selectedPromptTab: PromptTab = PromptTab.Standard,
    val enableLocalOcr: Boolean = false,
    val useLocalOcrScheme: Boolean = true,
    /** OCR 识别语言（null 表示自动推断） */
    val ocrLanguage: OcrLanguage? = null,
    /** 是否显示语言包下载引导对话框 */
    val showLanguagePackGuide: Boolean = false,
    /** 语言包下载提示信息 */
    val languagePackPrompt: LanguagePackPrompt? = null,
    /** 是否已检查过语言包状态（用于首次启动检查） */
    val hasCheckedLanguagePacks: Boolean = false,

    // ========== 本地 OCR 新增配置 ==========
    /** 本地 OCR 翻译模式 */
    val localOcrTranslationMode: TranslationMode = TranslationMode.LOCAL,
    /** 云端 LLM 自定义提示词 */
    val cloudLlmPrompt: String? = null,
    /** 是否显示语言包管理界面 */
    val showLanguagePackManagement: Boolean = false,
    /** 语言包状态列表（旧，保留兼容） */
    val languagePackStates: List<com.cw2.cw_1kito.model.LanguagePackState>? = null,
    /** 语言模型状态列表（新） */
    val languageModelStates: List<LanguageModelState>? = null,

    // ========== 合并配置 ==========
    /** 合并阈值配置 */
    val mergingConfig: com.cw2.cw_1kito.model.MergingConfig = com.cw2.cw_1kito.model.MergingConfig.DEFAULT,

    // ========== 模型池配置 ==========
    /** 模型池配置 */
    val modelPoolConfig: ModelPoolConfig = ModelPoolConfig.DEFAULT
) {
    val isApiKeyConfigured: Boolean
        get() = apiKey.isNotEmpty() && isApiKeyValid

    val isReadyToStart: Boolean
        get() = hasOverlayPermission && hasScreenCapturePermission
}

/**
 * 设置界面事件
 */
sealed class SettingsEvent {
    data class ApiKeyChanged(val apiKey: String) : SettingsEvent()
    data class SourceLanguageChanged(val language: Language) : SettingsEvent()
    data class TargetLanguageChanged(val language: Language) : SettingsEvent()
    data class ModelChanged(val model: VlmModel) : SettingsEvent()
    data class PromptChanged(val prompt: String) : SettingsEvent()
    data object ResetPrompt : SettingsEvent()
    data object RequestOverlayPermission : SettingsEvent()
    data object RequestBatteryOptimization : SettingsEvent()
    data object RequestScreenCapturePermission : SettingsEvent()
    data object StartService : SettingsEvent()
    data object ClearError : SettingsEvent()
    data object NavigateToLab : SettingsEvent()
    data class StreamingEnabledChanged(val enabled: Boolean) : SettingsEvent()
    data class ThemeHueChanged(val hue: com.cw2.cw_1kito.ui.theme.ThemeHue) : SettingsEvent()
    data class DarkModeChanged(val darkMode: com.cw2.cw_1kito.ui.theme.DarkModeOption) : SettingsEvent()
    data object ResetTheme : SettingsEvent()
    data class PromptTabChanged(val tab: PromptTab) : SettingsEvent()
    data class TextMergingPromptChanged(val prompt: String) : SettingsEvent()
    data object ResetTextMergingPrompt : SettingsEvent()
    data class TextMergingEnabledChanged(val enabled: Boolean) : SettingsEvent()
    data class EnableLocalOcrChanged(val enabled: Boolean) : SettingsEvent()
    data class UseLocalOcrSchemeChanged(val useLocalOcr: Boolean) : SettingsEvent()
    data class OcrLanguageChanged(val language: OcrLanguage?) : SettingsEvent()

    // 语言包相关事件
    data object ShowLanguagePackGuide : SettingsEvent()
    data object DismissLanguagePackGuide : SettingsEvent()
    data class DownloadLanguagePack(val requireWifi: Boolean) : SettingsEvent()
    data object LanguagePackCheckComplete : SettingsEvent()

    // ========== 本地 OCR 新增事件 ==========
    /** 本地 OCR 翻译模式变更 */
    data class LocalOcrTranslationModeChanged(val mode: TranslationMode) : SettingsEvent()
    /** 云端 LLM 提示词变更 */
    data class CloudLlmPromptChanged(val prompt: String) : SettingsEvent()
    /** 重置云端 LLM 提示词为默认 */
    data object ResetCloudLlmPrompt : SettingsEvent()
    /** 导航到语言包管理界面 */
    data object NavigateToLanguagePackManagement : SettingsEvent()
    /** 关闭语言包管理界面 */
    data object DismissLanguagePackManagement : SettingsEvent()
    /** 下载指定语言包 */
    data class DownloadLanguagePackFor(val source: Language, val target: Language) : SettingsEvent()
    /** 删除指定语言包 */
    data class DeleteLanguagePackFor(val source: Language, val target: Language) : SettingsEvent()
    /** 下载单个语言模型 */
    data class DownloadLanguageModel(val language: Language) : SettingsEvent()
    /** 删除单个语言模型 */
    data class DeleteLanguageModel(val language: Language) : SettingsEvent()

    // ========== 合并配置事件 ==========
    /** 合并配置变更 */
    data class MergingConfigChanged(val config: com.cw2.cw_1kito.model.MergingConfig) : SettingsEvent()

    // ========== 模型池配置事件 ==========
    /** 模型池配置变更 */
    data class ModelPoolConfigChanged(val config: ModelPoolConfig) : SettingsEvent()
}

/**
 * 设置界面组件
 *
 * @param uiState UI 状态
 * @param onEvent 事件回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // 显示错误信息
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "确定",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onEvent(SettingsEvent.ClearError)
            }
        }
    }

    // 语言包管理界面（独立页面）
    if (uiState.showLanguagePackManagement) {
        // 拦截返回键：返回设置页面
        BackHandler(enabled = true) {
            onEvent(SettingsEvent.DismissLanguagePackManagement)
        }

        LanguagePackManagementScreen(
            languageModelStates = uiState.languageModelStates ?: getDefaultLanguageModelStates(),
            onDownloadModel = { language ->
                onEvent(SettingsEvent.DownloadLanguageModel(language))
            },
            onDeleteModel = { language ->
                onEvent(SettingsEvent.DeleteLanguageModel(language))
            },
            onNavigateBack = {
                onEvent(SettingsEvent.DismissLanguagePackManagement)
            }
        )
        return
    }

    // 当启用本地OCR时，默认选中本地OCR标签页
    var selectedSchemeTab by remember {
        mutableStateOf(
            if (uiState.enableLocalOcr) TranslationSchemeTab.LOCAL_OCR else TranslationSchemeTab.VLM_CLOUD
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Kito 设置") },
                actions = {
                    TextButton(onClick = { onEvent(SettingsEvent.NavigateToLab) }) {
                        Text(
                            text = "⚙",
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "实验室",
                            style = MaterialTheme.typography.bodyMedium
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
                .fillMaxSize()
        ) {
            // 滚动内容区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 方案切换器（仅在启用本地OCR时显示）
                if (uiState.enableLocalOcr) {
                    SchemeSwitcher(
                        useLocalOcr = uiState.useLocalOcrScheme,
                        onSchemeChanged = { onEvent(SettingsEvent.UseLocalOcrSchemeChanged(it)) },
                        enabled = true
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Tab 导航（仅本地OCR开启时显示）
                if (uiState.enableLocalOcr) {
                    TabRow(selectedTabIndex = selectedSchemeTab.ordinal) {
                        TranslationSchemeTab.entries.forEach { tab ->
                            Tab(
                                selected = selectedSchemeTab == tab,
                                onClick = { selectedSchemeTab = tab },
                                text = { Text(tab.displayName) }
                            )
                        }
                    }
                }

                // Tab 内容区域
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    when {
                        // 未启用本地OCR时，显示原有完整设置
                        !uiState.enableLocalOcr -> {
                            FullSettingsContent(
                                uiState = uiState,
                                onEvent = onEvent
                            )
                        }
                        // VLM 云端翻译 Tab
                        selectedSchemeTab == TranslationSchemeTab.VLM_CLOUD -> {
                            VLMCloudTabContent(
                                uiState = uiState,
                                onEvent = onEvent
                            )
                        }
                        // 本地 OCR 翻译 Tab
                        selectedSchemeTab == TranslationSchemeTab.LOCAL_OCR -> {
                            LocalOcrTabContent(
                                uiState = uiState,
                                onEvent = onEvent
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }

            // 底部固定区域（启动按钮和提示）
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 启动按钮
                    FilledTonalButton(
                        onClick = { onEvent(SettingsEvent.StartService) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.isReadyToStart && !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            when {
                                uiState.isLoading -> "启动中..."
                                !uiState.hasOverlayPermission -> "请先授予悬浮窗权限"
                                !uiState.hasScreenCapturePermission -> "请先授予录屏权限"
                                else -> "启动全局悬浮窗"
                            }
                        )
                    }

                    // 底部提示
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✅",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "启动后可通过悬浮球快速截图翻译",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 完整设置内容（未启用本地OCR时显示）
 */
@Composable
private fun FullSettingsContent(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit
) {
    // 语言配置
    LanguageSection(
        sourceLanguage = uiState.sourceLanguage,
        targetLanguage = uiState.targetLanguage,
        onSourceLanguageChange = { onEvent(SettingsEvent.SourceLanguageChanged(it)) },
        onTargetLanguageChange = { onEvent(SettingsEvent.TargetLanguageChanged(it)) }
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    // 模型选择
    ModelSection(
        selectedModel = uiState.selectedModel,
        onModelChange = { onEvent(SettingsEvent.ModelChanged(it)) }
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    // 提示词配置
    PromptSection(
        customPrompt = uiState.customPrompt,
        mergingPrompt = uiState.textMergingPrompt,
        selectedTab = uiState.selectedPromptTab,
        onPromptChange = { onEvent(SettingsEvent.PromptChanged(it)) },
        onTabChange = { onEvent(SettingsEvent.PromptTabChanged(it)) },
        onResetPrompt = { onEvent(SettingsEvent.ResetPrompt) },
        onResetMergingPrompt = { onEvent(SettingsEvent.ResetTextMergingPrompt) },
        onMergingPromptChange = { onEvent(SettingsEvent.TextMergingPromptChanged(it)) }
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    // 权限配置
    PermissionSection(
        hasOverlayPermission = uiState.hasOverlayPermission,
        hasBatteryOptimizationDisabled = uiState.hasBatteryOptimizationDisabled,
        hasScreenCapturePermission = uiState.hasScreenCapturePermission,
        onRequestOverlayPermission = { onEvent(SettingsEvent.RequestOverlayPermission) },
        onRequestBatteryOptimization = { onEvent(SettingsEvent.RequestBatteryOptimization) },
        onRequestScreenCapturePermission = { onEvent(SettingsEvent.RequestScreenCapturePermission) }
    )
}

/**
 * VLM 云端翻译 Tab 内容
 */
@Composable
private fun VLMCloudTabContent(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit
) {
    // 语言配置
    LanguageSection(
        sourceLanguage = uiState.sourceLanguage,
        targetLanguage = uiState.targetLanguage,
        onSourceLanguageChange = { onEvent(SettingsEvent.SourceLanguageChanged(it)) },
        onTargetLanguageChange = { onEvent(SettingsEvent.TargetLanguageChanged(it)) }
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    // 模型选择
    ModelSection(
        selectedModel = uiState.selectedModel,
        onModelChange = { onEvent(SettingsEvent.ModelChanged(it)) }
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    // 提示词配置
    PromptSection(
        customPrompt = uiState.customPrompt,
        mergingPrompt = uiState.textMergingPrompt,
        selectedTab = uiState.selectedPromptTab,
        onPromptChange = { onEvent(SettingsEvent.PromptChanged(it)) },
        onTabChange = { onEvent(SettingsEvent.PromptTabChanged(it)) },
        onResetPrompt = { onEvent(SettingsEvent.ResetPrompt) },
        onResetMergingPrompt = { onEvent(SettingsEvent.ResetTextMergingPrompt) },
        onMergingPromptChange = { onEvent(SettingsEvent.TextMergingPromptChanged(it)) }
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    // 权限配置
    PermissionSection(
        hasOverlayPermission = uiState.hasOverlayPermission,
        hasBatteryOptimizationDisabled = uiState.hasBatteryOptimizationDisabled,
        hasScreenCapturePermission = uiState.hasScreenCapturePermission,
        onRequestOverlayPermission = { onEvent(SettingsEvent.RequestOverlayPermission) },
        onRequestBatteryOptimization = { onEvent(SettingsEvent.RequestBatteryOptimization) },
        onRequestScreenCapturePermission = { onEvent(SettingsEvent.RequestScreenCapturePermission) }
    )
}

/**
 * 本地 OCR 翻译 Tab 内容
 */
@Composable
private fun LocalOcrTabContent(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 翻译模式选择器
        TranslationModeSelector(
            currentMode = uiState.localOcrTranslationMode,
            onModeChange = { onEvent(SettingsEvent.LocalOcrTranslationModeChanged(it)) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 2. 云端 LLM 配置（选择云端 LLM 时显示）
        AnimatedVisibility(
            visible = uiState.localOcrTranslationMode == TranslationMode.REMOTE,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(
                animationSpec = tween(durationMillis = 300)
            ),
            exit = shrinkVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeOut(
                animationSpec = tween(durationMillis = 300)
            )
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 模型池配置（包含首选模型和备用模型）
                ModelPoolSelector(
                    config = uiState.modelPoolConfig,
                    onConfigChange = { newConfig ->
                        onEvent(SettingsEvent.ModelPoolConfigChanged(newConfig))
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 翻译提示词配置
                CloudLlmPromptEditor(
                    customPrompt = uiState.cloudLlmPrompt,
                    onPromptChanged = { onEvent(SettingsEvent.CloudLlmPromptChanged(it)) },
                    onResetPrompt = { onEvent(SettingsEvent.ResetCloudLlmPrompt) },
                    sourceLanguage = uiState.sourceLanguage,
                    targetLanguage = uiState.targetLanguage
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // 3. 语言包管理（仅在选择本地 ML Kit 翻译时显示）
        if (uiState.localOcrTranslationMode == TranslationMode.LOCAL) {
            // 语言包说明卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "本地翻译说明",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "使用本地 ML Kit 翻译需要先下载对应的语言包。每个语言对约 20-40MB，下载后可离线使用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 语言包管理入口
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                onClick = { onEvent(SettingsEvent.NavigateToLanguagePackManagement) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "语言包管理",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "点击下载或管理语言包",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = ">",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        // 4. 权限配置（本地 OCR 同样需要）
        PermissionSection(
            hasOverlayPermission = uiState.hasOverlayPermission,
            hasBatteryOptimizationDisabled = uiState.hasBatteryOptimizationDisabled,
            hasScreenCapturePermission = uiState.hasScreenCapturePermission,
            onRequestOverlayPermission = { onEvent(SettingsEvent.RequestOverlayPermission) },
            onRequestBatteryOptimization = { onEvent(SettingsEvent.RequestBatteryOptimization) },
            onRequestScreenCapturePermission = { onEvent(SettingsEvent.RequestScreenCapturePermission) }
        )
    }
}

/**
 * 提示词配置区域
 */
@Composable
fun PromptSection(
    customPrompt: String,
    mergingPrompt: String,
    selectedTab: PromptTab,
    onPromptChange: (String) -> Unit,
    onTabChange: (PromptTab) -> Unit,
    onResetPrompt: () -> Unit,
    onResetMergingPrompt: () -> Unit,
    onMergingPromptChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDefaultStandard = customPrompt == TranslationApiClientImpl.DEFAULT_PROMPT
    val isDefaultMerging = mergingPrompt == TranslationApiClientImpl.DEFAULT_MERGING_PROMPT
    val currentPrompt = when (selectedTab) {
        PromptTab.Standard -> customPrompt
        PromptTab.Merging -> mergingPrompt
    }

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
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            PromptTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabChange(tab) },
                    text = { Text(tab.displayName) }
                )
            }
        }

        Text(
            text = when (selectedTab) {
                PromptTab.Standard -> "自定义发送给大模型的提示词。支持模板变量：{{targetLanguage}}、{{imageWidth}}、{{imageHeight}}"
                PromptTab.Merging -> "用于文本合并模式的后处理提示词，定义如何将相邻文本块合并。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = currentPrompt,
            onValueChange = {
                when (selectedTab) {
                    PromptTab.Standard -> onPromptChange(it)
                    PromptTab.Merging -> onMergingPromptChange(it)
                }
            },
            label = { Text(when (selectedTab) {
                PromptTab.Standard -> "标准翻译提示词"
                PromptTab.Merging -> "文本合并提示词"
            }) },
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
                onClick = {
                    when (selectedTab) {
                        PromptTab.Standard -> onResetPrompt()
                        PromptTab.Merging -> onResetMergingPrompt()
                    }
                },
                enabled = when (selectedTab) {
                    PromptTab.Standard -> !isDefaultStandard
                    PromptTab.Merging -> !isDefaultMerging
                }
            ) {
                Text("重置为默认")
            }
        }
    }
}
