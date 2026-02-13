package com.cw2.cw_1kito.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.LanguagePackState
import com.cw2.cw_1kito.model.LanguagePackStatus
import com.cw2.cw_1kito.model.calculateTotalStorage
import com.cw2.cw_1kito.model.formatBytes

/**
 * 语言包管理页面
 *
 * 显示已下载的语言包并支持管理操作
 *
 * @param languagePackStates 语言包状态列表
 * @param totalStorageUsed 总存储占用（可选，默认自动计算）
 * @param onDownload 下载语言包回调
 * @param onDelete 删除语言包回调
 * @param onNavigateBack 返回回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePackManagementScreen(
    languagePackStates: List<LanguagePackState>,
    totalStorageUsed: String? = null,
    onDownload: (Language, Language) -> Unit,
    onDelete: (Language, Language) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val calculatedStorage = formatBytes(calculateTotalStorage(languagePackStates))

    // 删除确认弹框状态
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingDeleteSource by remember { mutableStateOf<Language?>(null) }
    var pendingDeleteTarget by remember { mutableStateOf<Language?>(null) }

    // 删除确认弹框
    if (showDeleteDialog && pendingDeleteSource != null && pendingDeleteTarget != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = {
                Text("确定要删除「${pendingDeleteSource!!.displayName} → ${pendingDeleteTarget!!.displayName}」语言包吗？\n\n删除后需要重新下载才能使用本地翻译。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(pendingDeleteSource!!, pendingDeleteTarget!!)
                        showDeleteDialog = false
                        pendingDeleteSource = null
                        pendingDeleteTarget = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("语言包管理") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(
                            text = "<",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // 存储占用卡片
            StorageUsageCard(
                totalStorageUsed = totalStorageUsed ?: calculatedStorage
            )

            // 说明文案
            LanguagePackInfoCard()

            Spacer(modifier = Modifier.height(16.dp))

            // 语言包列表
            LanguagePackList(
                states = languagePackStates,
                onDownload = onDownload,
                onDelete = { source, target ->
                    pendingDeleteSource = source
                    pendingDeleteTarget = target
                    showDeleteDialog = true
                }
            )
        }
    }
}

/**
 * 存储占用卡片
 */
@Composable
private fun StorageUsageCard(
    totalStorageUsed: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "存储占用",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = totalStorageUsed,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 语言包说明文案
 */
@Composable
private fun LanguagePackInfoCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "语言包用于本地翻译，下载后无需联网即可翻译。每个语言模型约 10-30 MB，" +
                    "同一语言的模型在多个语言对之间共享（如「中→英」和「中→日」共用中文模型）。" +
                    "首次下载需要网络连接，建议在 Wi-Fi 环境下进行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 语言包列表
 */
@Composable
private fun LanguagePackList(
    states: List<LanguagePackState>,
    onDownload: (Language, Language) -> Unit,
    onDelete: (Language, Language) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "语言包列表",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (states.isEmpty()) {
            EmptyState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(states) { state ->
                    LanguagePackItem(
                        state = state,
                        onDownload = { onDownload(state.sourceLang, state.targetLang) },
                        onDelete = { onDelete(state.sourceLang, state.targetLang) }
                    )
                }
            }
        }
    }
}

/**
 * 空状态提示
 */
@Composable
private fun EmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "暂无语言包",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 语言包列表项
 */
@Composable
private fun LanguagePackItem(
    state: LanguagePackState,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${state.sourceLang.displayName} → ${state.targetLang.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StatusChip(state.status)
                    }
                    Text(
                        text = state.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 操作按钮
                ActionButton(
                    status = state.status,
                    downloadProgress = state.downloadProgress,
                    onDownload = onDownload,
                    onDelete = onDelete
                )
            }

            // 下载进度条
            if (state.status == LanguagePackStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "正在下载...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 错误信息
            if (state.status == LanguagePackStatus.DOWNLOAD_FAILED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.errorMessage ?: "下载失败",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 状态芯片
 */
@Composable
private fun StatusChip(status: LanguagePackStatus) {
    val (text, color) = when (status) {
        LanguagePackStatus.DOWNLOADED -> "已下载" to MaterialTheme.colorScheme.primary
        LanguagePackStatus.DOWNLOADING -> "下载中" to MaterialTheme.colorScheme.tertiary
        LanguagePackStatus.NOT_DOWNLOADED -> "未下载" to MaterialTheme.colorScheme.onSurfaceVariant
        LanguagePackStatus.DOWNLOAD_FAILED -> "失败" to MaterialTheme.colorScheme.error
    }

    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * 操作按钮
 */
@Composable
private fun ActionButton(
    status: LanguagePackStatus,
    downloadProgress: Float,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    when (status) {
        LanguagePackStatus.DOWNLOADED -> {
            TextButton(onClick = onDelete) {
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        LanguagePackStatus.DOWNLOADING -> {
            Box(
                modifier = Modifier.width(48.dp).height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.width(28.dp).height(28.dp),
                    strokeWidth = 3.dp
                )
            }
        }
        LanguagePackStatus.NOT_DOWNLOADED, LanguagePackStatus.DOWNLOAD_FAILED -> {
            OutlinedButton(onClick = onDownload) {
                Text(if (status == LanguagePackStatus.DOWNLOAD_FAILED) "重试" else "下载")
            }
        }
    }
}
