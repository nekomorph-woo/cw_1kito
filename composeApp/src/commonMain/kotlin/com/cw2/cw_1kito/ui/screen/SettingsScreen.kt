package com.cw2.cw_1kito.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cw2.cw_1kito.data.api.TranslationApiClientImpl
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.VlmModel
import com.cw2.cw_1kito.ui.component.*
import com.cw2.cw_1kito.ui.theme.ThemeConfig

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
    val themeConfig: ThemeConfig = ThemeConfig.DEFAULT
) {
    val isApiKeyConfigured: Boolean
        get() = apiKey.isNotEmpty() && isApiKeyValid

    val isReadyToStart: Boolean
        get() = isApiKeyConfigured && hasOverlayPermission && hasScreenCapturePermission
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
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // API Key 配置
            ApiKeySection(
                apiKey = uiState.apiKey,
                isValid = uiState.isApiKeyValid,
                onApiKeyChange = { onEvent(SettingsEvent.ApiKeyChanged(it)) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                onPromptChange = { onEvent(SettingsEvent.PromptChanged(it)) },
                onResetPrompt = { onEvent(SettingsEvent.ResetPrompt) }
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

            Spacer(modifier = Modifier.weight(1f))

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
                        !uiState.isApiKeyConfigured -> "请先配置 API Key"
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

/**
 * 提示词配置区域
 */
@Composable
fun PromptSection(
    customPrompt: String,
    onPromptChange: (String) -> Unit,
    onResetPrompt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDefault = customPrompt == TranslationApiClientImpl.DEFAULT_PROMPT

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "提示词配置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "自定义发送给大模型的提示词。支持模板变量：{{targetLanguage}}、{{imageWidth}}、{{imageHeight}}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = customPrompt,
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
