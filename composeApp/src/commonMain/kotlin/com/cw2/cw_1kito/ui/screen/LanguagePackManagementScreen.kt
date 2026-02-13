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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.cw2.cw_1kito.model.LanguageModelState
import com.cw2.cw_1kito.model.LanguagePackStatus
import com.cw2.cw_1kito.model.calculateModelTotalStorage
import com.cw2.cw_1kito.model.computeAffectedPairs
import com.cw2.cw_1kito.model.computeAvailablePairs
import com.cw2.cw_1kito.model.formatAvailablePairs
import com.cw2.cw_1kito.model.formatBytes

/**
 * 语言包管理页面（以语言模型为单位）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePackManagementScreen(
    languageModelStates: List<LanguageModelState>,
    onDownloadModel: (Language) -> Unit,
    onDeleteModel: (Language) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalStorage = formatBytes(calculateModelTotalStorage(languageModelStates))
    val availablePairs = computeAvailablePairs(languageModelStates)
    val availablePairsText = formatAvailablePairs(availablePairs)

    // 删除确认弹框状态
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pendingDeleteLanguage by remember { mutableStateOf<Language?>(null) }

    // 删除确认弹框
    if (showDeleteDialog && pendingDeleteLanguage != null) {
        val lang = pendingDeleteLanguage!!
        val affected = computeAffectedPairs(lang, languageModelStates)
        val affectedText = if (affected.isNotEmpty()) {
            "\n\n删除后以下翻译方向将不可用：\n" +
                affected.joinToString(", ") { (a, b) ->
                    "${a.displayName} ↔ ${b.displayName}"
                }
        } else ""

        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = {
                Text("确定要删除「${lang.displayName}」语言模型吗？$affectedText")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteModel(lang)
                        showDeleteDialog = false
                        pendingDeleteLanguage = null
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
            StorageUsageCard(totalStorageUsed = totalStorage)

            // 说明文案
            LanguagePackInfoCard()

            // 可用语言对文案
            AvailablePairsCard(text = availablePairsText)

            Spacer(modifier = Modifier.height(8.dp))

            // 语言模型列表
            LanguageModelList(
                states = languageModelStates,
                onDownload = onDownloadModel,
                onDelete = { lang ->
                    pendingDeleteLanguage = lang
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
                text = "语言包用于本地翻译，下载后无需联网即可翻译。" +
                    "下载任意两个语言模型即可实现双向翻译。" +
                    "首次下载需要网络连接，建议在 Wi-Fi 环境下进行。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 可用语言对文案卡片
 */
@Composable
private fun AvailablePairsCard(
    text: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "当前可用翻译方向：",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * 语言模型列表
 */
@Composable
private fun LanguageModelList(
    states: List<LanguageModelState>,
    onDownload: (Language) -> Unit,
    onDelete: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "语言模型列表",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(states) { state ->
                LanguageModelItem(
                    state = state,
                    onDownload = { onDownload(state.language) },
                    onDelete = { onDelete(state.language) }
                )
            }
        }
    }
}

/**
 * 语言模型列表项
 */
@Composable
private fun LanguageModelItem(
    state: LanguageModelState,
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
                            text = "${state.language.displayName} (${state.language.code})",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ModelStatusChip(state.status)
                    }
                    Text(
                        text = "~${state.formattedSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ModelActionButton(
                    status = state.status,
                    onDownload = onDownload,
                    onDelete = onDelete
                )
            }

            // 下载进度条
            if (state.status == LanguagePackStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
 * 模型状态芯片
 */
@Composable
private fun ModelStatusChip(status: LanguagePackStatus) {
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
 * 模型操作按钮
 */
@Composable
private fun ModelActionButton(
    status: LanguagePackStatus,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    when (status) {
        LanguagePackStatus.DOWNLOADED -> {
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
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
