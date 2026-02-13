package com.cw2.cw_1kito.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cw2.cw_1kito.model.LlmModel
import com.cw2.cw_1kito.model.ModelPoolConfig

/**
 * 模型池选择器组件
 *
 * 允许用户配置首选模型和最多4个备用模型，用于分散请求压力避免单一模型限流。
 *
 * @param config 当前模型池配置
 * @param onConfigChange 配置变化回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPoolSelector(
    config: ModelPoolConfig,
    onConfigChange: (ModelPoolConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "云端翻译模型池设置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // 首选模型（必选）
        val primaryModel = config.primaryModel
        LlmModelDropdown(
            label = "首选模型",
            selectedModel = primaryModel,
            availableModels = LlmModel.entries.toList(),
            onModelSelected = { newModel ->
                // 首选模型必选，newModel 不会为 null
                if (newModel != null) {
                    // 过滤掉与首选模型相同的备用模型，避免重复
                    val filteredBackupModels = config.backupModels.filter { it != newModel }
                    val newModels = buildList<LlmModel> {
                        add(newModel)
                        addAll(filteredBackupModels.take(4))
                    }
                    onConfigChange(config.copy(models = newModels))
                }
            },
            isRequired = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 备用模型 1-4（可选）
        val backupModels = config.backupModels
        repeat(4) { index ->
            val currentModel = backupModels.getOrNull(index)

            // 排除已在池中的模型（包括当前选中的）
            val otherModels = (listOf(primaryModel) + backupModels.filterNotNull().filter { it != currentModel }).toSet()
            val availableModels = LlmModel.entries.filter { it !in otherModels }

            LlmModelDropdown(
                label = "备用模型 ${index + 1}",
                selectedModel = currentModel,
                availableModels = availableModels,
                onModelSelected = { newModel ->
                    val newModels = buildList<LlmModel> {
                        add(primaryModel)
                        // 添加到当前位置之前的备用模型
                        repeat(index) { i ->
                            backupModels.getOrNull(i)?.let { add(it) }
                        }
                        // 添加新选择的模型（如果不为null且不重复）
                        if (newModel != null && newModel != primaryModel && newModel !in backupModels.take(index)) {
                            add(newModel)
                        }
                        // 添加当前位置之后的备用模型（跳过当前位置）
                        for (i in (index + 1) until backupModels.size) {
                            val model = backupModels[i]
                            // 确保不添加重复的模型
                            if (model != primaryModel && model !in backupModels.take(index) && model != newModel) {
                                add(model)
                            }
                        }
                    }.distinct() // 最终去重确保不会有重复
                    onConfigChange(config.copy(models = newModels))
                },
                isRequired = false
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 说明卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "i",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "模型池可分散请求压力，避免单一模型限流",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "优先使用首选模型，限流时自动切换到备用模型。每个模型 RPM 独立计算。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * LLM 模型下拉选择器
 *
 * 支持分组显示：普通模型（9个）和 Thinking 模型（10个）
 *
 * @param label 标签文本
 * @param selectedModel 当前选中的模型（null 表示未选择）
 * @param availableModels 可选模型列表
 * @param onModelSelected 模型选择回调，传入 null 表示取消选择
 * @param isRequired 是否必选
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LlmModelDropdown(
    label: String,
    selectedModel: LlmModel?,
    availableModels: List<LlmModel>,
    onModelSelected: (LlmModel?) -> Unit,
    isRequired: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // 分组：普通模型和 Thinking 模型
    val normalModels = availableModels.filter { !it.isThinkingModel }
    val thinkingModels = availableModels.filter { it.isThinkingModel }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedModel?.displayName ?: "不选择",
            onValueChange = {},
            readOnly = true,
            label = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label)
                    if (isRequired) {
                        Text(
                            text = "*",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // "不选择"选项（仅备用模型）
            if (!isRequired) {
                val isSelected = selectedModel == null
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSelected) "✓" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "不选择",
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    },
                    onClick = {
                        onModelSelected(null)
                        expanded = false
                    }
                )
                if (normalModels.isNotEmpty()) {
                    HorizontalDivider()
                }
            }

            // 普通模型分组
            if (normalModels.isNotEmpty()) {
                // 分组标题
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "普通模型",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = { },
                    enabled = false
                )

                normalModels.forEach { model ->
                    val isSelected = model == selectedModel
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSelected) "✓" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = model.displayName,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        },
                        onClick = {
                            onModelSelected(model)
                            expanded = false
                        }
                    )
                }

                // Thinking 模型分隔符
                if (thinkingModels.isNotEmpty()) {
                    HorizontalDivider()
                }
            }

            // Thinking 模型分组
            if (thinkingModels.isNotEmpty()) {
                // 分组标题
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Thinking 模型",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = { },
                    enabled = false
                )

                thinkingModels.forEach { model ->
                    val isSelected = model == selectedModel
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSelected) "✓" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = model.displayName,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        },
                        onClick = {
                            onModelSelected(model)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * 模型池配置区域组件（用于设置页面）
 *
 * @param config 当前模型池配置
 * @param onConfigChange 配置变化回调
 * @param modifier 修饰符
 */
@Composable
fun ModelPoolSection(
    config: ModelPoolConfig,
    onConfigChange: (ModelPoolConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModelPoolSelector(
            config = config,
            onConfigChange = onConfigChange
        )
    }
}
