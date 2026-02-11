package com.cw2.cw_1kito.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.VlmModel
import com.cw2.cw_1kito.ui.component.*

/**
 * 主界面组件
 *
 * 这是应用的入口界面，提供设置和启动悬浮窗服务的功能
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
    SettingsScreen(
        uiState = uiState,
        onEvent = onEvent,
        modifier = modifier
    )
}
