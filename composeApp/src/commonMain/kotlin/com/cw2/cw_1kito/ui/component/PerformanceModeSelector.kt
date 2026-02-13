package com.cw2.cw_1kito.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cw2.cw_1kito.model.PerformanceMode
import com.cw2.cw_1kito.model.getEstimatedLatency

/**
 * 性能模式选择器组件
 *
 * 允许用户选择不同的性能模式（极速/平衡/高精）
 *
 * @param currentMode 当前性能模式
 * @param onModeChange 模式变化回调
 * @param modifier 修饰符
 */
@Composable
fun PerformanceModeSelector(
    currentMode: PerformanceMode,
    onModeChange: (PerformanceMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "性能模式",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        PerformanceMode.values().forEach { mode ->
            PerformanceModeOption(
                mode = mode,
                selected = currentMode == mode,
                onClick = { onModeChange(mode) }
            )
        }
    }
}

/**
 * 性能模式选项卡片
 *
 * 显示单个性能模式的详细信息
 *
 * @param mode 性能模式
 * @param selected 是否选中
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun PerformanceModeOption(
    mode: PerformanceMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        onClick = onClick,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else null,
        colors = if (selected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.titleSmall
                )
                if (selected) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = getPerformanceModeDescription(mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "预估延迟: ${mode.getEstimatedLatency()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 获取性能模式描述
 */
private fun getPerformanceModeDescription(mode: PerformanceMode): String {
    return when (mode) {
        PerformanceMode.FAST -> "极速模式：短边 672px，本地模型"
        PerformanceMode.BALANCED -> "平衡模式：短边 896px，混合模型"
        PerformanceMode.QUALITY -> "高精模式：短边 1080px，云端 VL 32B"
    }
}
