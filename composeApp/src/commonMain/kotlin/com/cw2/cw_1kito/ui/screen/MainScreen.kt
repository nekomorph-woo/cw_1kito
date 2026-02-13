package com.cw2.cw_1kito.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.cw2.cw_1kito.ui.component.LanguagePackGuideDialog

/**
 * 主界面组件
 *
 * 这是应用的入口界面，通过状态驱动在设置页面和实验室页面之间切换
 *
 * @param uiState UI 状态
 * @param onEvent 事件回调
 * @param modifier 修饰符
 */
@Composable
fun MainScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLabSettings by remember { mutableStateOf(false) }

    // 拦截返回键：在实验室页面时返回设置主页
    BackHandler(enabled = showLabSettings) {
        showLabSettings = false
    }

    // 语言包下载引导对话框
    val languagePackPrompt = uiState.languagePackPrompt
    if (uiState.showLanguagePackGuide && languagePackPrompt != null) {
        LanguagePackGuideDialog(
            languagePair = languagePackPrompt.languagePair,
            estimatedSize = languagePackPrompt.estimatedSize,
            isWifiConnected = languagePackPrompt.isWifiConnected,
            onDownload = { requireWifi ->
                onEvent(SettingsEvent.DownloadLanguagePack(requireWifi))
            },
            onDismiss = {
                onEvent(SettingsEvent.DismissLanguagePackGuide)
            }
        )
    }

    if (showLabSettings) {
        LabSettingsScreen(
            enableLocalOcr = uiState.enableLocalOcr,
            onEnableLocalOcrChange = { onEvent(SettingsEvent.EnableLocalOcrChanged(it)) },
            streamingEnabled = uiState.streamingEnabled,
            onStreamingEnabledChange = { onEvent(SettingsEvent.StreamingEnabledChanged(it)) },
            textMergingEnabled = uiState.textMergingEnabled,
            onTextMergingEnabledChange = { onEvent(SettingsEvent.TextMergingEnabledChanged(it)) },
            ocrLanguage = uiState.ocrLanguage,
            onOcrLanguageChange = { onEvent(SettingsEvent.OcrLanguageChanged(it)) },
            apiKey = uiState.apiKey,
            isApiKeyValid = uiState.isApiKeyValid,
            onApiKeyChange = { onEvent(SettingsEvent.ApiKeyChanged(it)) },
            themeConfig = uiState.themeConfig,
            onThemeHueChange = { hue -> onEvent(SettingsEvent.ThemeHueChanged(hue)) },
            onDarkModeChange = { mode -> onEvent(SettingsEvent.DarkModeChanged(mode)) },
            onResetTheme = { onEvent(SettingsEvent.ResetTheme) },
            onNavigateBack = { showLabSettings = false }
        )
    } else {
        SettingsScreen(
            uiState = uiState,
            onEvent = { event ->
                if (event is SettingsEvent.NavigateToLab) {
                    showLabSettings = true
                } else {
                    onEvent(event)
                }
            },
            modifier = modifier
        )
    }
}
