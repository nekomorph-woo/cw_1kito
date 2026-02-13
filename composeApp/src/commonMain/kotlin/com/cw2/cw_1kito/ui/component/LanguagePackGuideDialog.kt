package com.cw2.cw_1kito.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 语言包下载引导对话框
 *
 * 在翻译执行时发现语言包缺失时显示，引导用户下载所需的语言包。
 *
 * ## 功能特性
 * - **Wi-Fi 检测**：根据当前网络环境显示不同的提示信息
 * - **仅 Wi-Fi 下载**：非 Wi-Fi 环境下提供"仅 Wi-Fi 下载"选项
 * - **继续下载**：允许用户在非 Wi-Fi 环境下继续下载（消耗流量）
 *
 * @param languagePair 语言对显示文本，如 "中文 → 英文"
 * @param estimatedSize 下载大小估算，如 "约 30MB"
 * @param isWifiConnected 是否连接 Wi-Fi
 * @param onDownload 下载回调，参数为是否需要 Wi-Fi 限制
 * @param onDismiss 关闭对话框回调
 * @param modifier 修饰符
 */
@Composable
fun LanguagePackGuideDialog(
    languagePair: String,
    estimatedSize: String,
    isWifiConnected: Boolean,
    onDownload: (requireWifi: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要下载语言包") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("当前翻译模式需要下载语言包才能使用。")
                Spacer(modifier = Modifier.height(8.dp))
                Text("语言对: $languagePair")
                Text("大小: $estimatedSize")

                if (!isWifiConnected) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "!",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "当前非 Wi-Fi 环境",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "下载将消耗移动数据流量",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "下载后可在离线状态下使用，无需消耗流量",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (isWifiConnected) {
                TextButton(onClick = { onDownload(false) }) {
                    Text("立即下载")
                }
            } else {
                // 非Wi-Fi环境：显示两个下载按钮
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // "稍后"按钮
                    TextButton(onClick = onDismiss) {
                        Text("稍后")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // "继续下载"按钮
                    TextButton(onClick = { onDownload(false) }) {
                        Text("继续下载")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // "仅 Wi-Fi 下载"按钮
                    TextButton(onClick = { onDownload(true) }) {
                        Text("仅 Wi-Fi 下载")
                    }
                }
            }
        },
        dismissButton = if (isWifiConnected) ({
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        }) else null,
        modifier = modifier
    )
}
