package com.cw2.cw_1kito.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 语言包下载状态
 */
enum class DownloadStatus {
    /** 准备中 */
    PENDING,
    /** 下载中 */
    DOWNLOADING,
    /** 完成 */
    COMPLETED,
    /** 失败 */
    FAILED,
    /** 已取消 */
    CANCELLED
}

/**
 * 语言包下载进度对话框
 *
 * @param languagePair 语言对描述，如 "中文 → 英文"
 * @param progress 下载进度 0.0 - 1.0
 * @param status 当前下载状态
 * @param downloadedBytes 已下载字节数
 * @param totalBytes 总字节数
 * @param onCancel 取消下载回调
 * @param onRetry 重试下载回调
 * @param modifier 修饰符
 */
@Composable
fun LanguagePackDownloadDialog(
    languagePair: String,
    progress: Float,
    status: DownloadStatus,
    downloadedBytes: Long = 0L,
    totalBytes: Long = 0L,
    onCancel: () -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dismissRequest = if (status == DownloadStatus.DOWNLOADING) {
        // 下载中不可关闭
        { }
    } else {
        onCancel
    }

    AlertDialog(
        onDismissRequest = dismissRequest,
        title = {
            Text(
                text = when (status) {
                    DownloadStatus.PENDING -> "准备下载语言包"
                    DownloadStatus.DOWNLOADING -> "下载语言包"
                    DownloadStatus.COMPLETED -> "下载完成"
                    DownloadStatus.FAILED -> "下载失败"
                    DownloadStatus.CANCELLED -> "已取消"
                }
            )
        },
        text = {
            Column(modifier = modifier) {
                Text("语言对: $languagePair")

                when (status) {
                    DownloadStatus.PENDING -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在准备下载...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    DownloadStatus.DOWNLOADING -> {
                        Spacer(modifier = Modifier.height(16.dp))

                        // 进度条
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // 进度文本
                        Text(
                            text = buildProgressText(progress, downloadedBytes, totalBytes),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "语言包下载完成，现在可以使用本地翻译功能了。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    DownloadStatus.FAILED -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "下载失败，请检查网络连接后重试。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )

                        if (totalBytes > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "已下载: ${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    DownloadStatus.CANCELLED -> {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "下载已取消。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (status) {
                DownloadStatus.DOWNLOADING -> {
                    TextButton(onClick = onCancel) {
                        Text("取消")
                    }
                }
                DownloadStatus.FAILED -> {
                    TextButton(onClick = onRetry) {
                        Text("重试")
                    }
                }
                DownloadStatus.COMPLETED, DownloadStatus.CANCELLED -> {
                    TextButton(onClick = onCancel) {
                        Text("确定")
                    }
                }
                DownloadStatus.PENDING -> {
                    // 准备中不显示按钮
                }
            }
        }
    )
}

/**
 * 构建进度文本
 */
private fun buildProgressText(
    progress: Float,
    downloadedBytes: Long,
    totalBytes: Long
): String {
    val percentage = (progress * 100).toInt()
    return if (totalBytes > 0) {
        "$percentage% (${formatBytes(downloadedBytes)}/${formatBytes(totalBytes)})"
    } else {
        "$percentage%"
    }
}

/**
 * 格式化字节数为人类可读格式
 */
fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
