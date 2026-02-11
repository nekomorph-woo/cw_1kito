package com.cw2.cw_1kito.ui.screen

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

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

    if (showLabSettings) {
        LabSettingsScreen(
            streamingEnabled = uiState.streamingEnabled,
            onStreamingEnabledChange = { onEvent(SettingsEvent.StreamingEnabledChanged(it)) },
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
