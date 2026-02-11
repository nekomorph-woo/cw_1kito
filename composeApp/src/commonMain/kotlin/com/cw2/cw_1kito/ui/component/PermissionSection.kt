package com.cw2.cw_1kito.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 权限状态项
 */
sealed class PermissionItemStatus {
    data object Granted : PermissionItemStatus()
    data object Denied : PermissionItemStatus()
    data object Optional : PermissionItemStatus()
}

/**
 * 权限状态数据类
 */
data class PermissionItem(
    val title: String,
    val description: String,
    val status: PermissionItemStatus,
    val onRequest: (() -> Unit)? = null
)

/**
 * 单个权限状态组件
 */
@Composable
private fun PermissionStatusItem(
    emoji: String,
    title: String,
    description: String,
    statusColor: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (statusColor) {
                MaterialTheme.colorScheme.error -> MaterialTheme.colorScheme.errorContainer
                MaterialTheme.colorScheme.primary -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = when (statusColor) {
                        MaterialTheme.colorScheme.error -> MaterialTheme.colorScheme.onErrorContainer
                        MaterialTheme.colorScheme.primary -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (statusColor) {
                        MaterialTheme.colorScheme.error -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        MaterialTheme.colorScheme.primary -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (onClick != null) {
                Text(
                    text = "去授权",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (statusColor) {
                        MaterialTheme.colorScheme.error -> MaterialTheme.colorScheme.onErrorContainer
                        MaterialTheme.colorScheme.primary -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

/**
 * 权限配置区域组件
 *
 * @param hasOverlayPermission 是否有悬浮窗权限
 * @param hasBatteryOptimizationDisabled 是否已关闭电池优化
 * @param hasScreenCapturePermission 是否有录屏权限
 * @param onRequestOverlayPermission 请求悬浮窗权限回调
 * @param onRequestBatteryOptimization 请求忽略电池优化回调
 * @param onRequestScreenCapturePermission 请求录屏权限回调
 * @param modifier 修饰符
 */
@Composable
fun PermissionSection(
    hasOverlayPermission: Boolean,
    hasBatteryOptimizationDisabled: Boolean,
    hasScreenCapturePermission: Boolean = false,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestScreenCapturePermission: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val permissions = remember(
        hasOverlayPermission,
        hasBatteryOptimizationDisabled,
        hasScreenCapturePermission
    ) {
        buildList {
            add(
                PermissionItem(
                    title = "悬浮窗权限",
                    description = "允许在其他应用上层显示翻译结果",
                    status = when {
                        hasOverlayPermission -> PermissionItemStatus.Granted
                        else -> PermissionItemStatus.Denied
                    },
                    onRequest = if (!hasOverlayPermission) onRequestOverlayPermission else null
                )
            )
            if (onRequestScreenCapturePermission != null) {
                add(
                    PermissionItem(
                        title = "录屏权限",
                        description = "允许截取屏幕内容进行翻译",
                        status = when {
                            hasScreenCapturePermission -> PermissionItemStatus.Granted
                            else -> PermissionItemStatus.Denied
                        },
                        onRequest = if (!hasScreenCapturePermission) onRequestScreenCapturePermission else null
                    )
                )
            }
            add(
                PermissionItem(
                    title = "电池优化",
                    description = "关闭电池优化以保持服务运行（建议）",
                    status = when {
                        hasBatteryOptimizationDisabled -> PermissionItemStatus.Granted
                        else -> PermissionItemStatus.Optional
                    },
                    onRequest = if (!hasBatteryOptimizationDisabled) onRequestBatteryOptimization else null
                )
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "权限配置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        permissions.forEach { permission ->
            val (emoji, color) = when (permission.status) {
                is PermissionItemStatus.Granted -> "✅" to MaterialTheme.colorScheme.primary
                is PermissionItemStatus.Denied -> "❌" to MaterialTheme.colorScheme.error
                is PermissionItemStatus.Optional -> "⚠️" to MaterialTheme.colorScheme.onSurfaceVariant
            }

            PermissionStatusItem(
                emoji = emoji,
                title = permission.title,
                description = permission.description,
                statusColor = color,
                onClick = permission.onRequest
            )
        }

        // 权限说明
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Text(
                text = "首次使用需要授予悬浮窗权限。点击状态项可以跳转到系统设置页面。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}
