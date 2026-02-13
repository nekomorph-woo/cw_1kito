package com.cw2.cw_1kito.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.LlmModel

/**
 * 默认翻译提示词模板
 */
private val DEFAULT_CLOUD_LLM_PROMPT = """
Translate the following {sourceLanguage} text into {targetLanguage}. Return only the translation result without any explanation or additional text.

Original text:
{text}
""".trimIndent()

/**
 * 云端 LLM 配置编辑器组件
 *
 * 提供模型选择和提示词编辑功能，支持配置持久化。
 *
 * @param selectedModel 当前选中的模型
 * @param onModelChanged 模型变化回调
 * @param customPrompt 自定义提示词（null 表示使用默认）
 * @param onPromptChanged 提示词变化回调
 * @param onResetPrompt 重置提示词回调
 * @param sourceLanguage 源语言
 * @param targetLanguage 目标语言
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudLlmConfigEditor(
    selectedModel: LlmModel,
    onModelChanged: (LlmModel) -> Unit,
    customPrompt: String?,
    onPromptChanged: (String) -> Unit,
    onResetPrompt: () -> Unit,
    sourceLanguage: Language,
    targetLanguage: Language,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentPrompt = customPrompt ?: DEFAULT_CLOUD_LLM_PROMPT
    val isPromptModified = customPrompt != null && customPrompt != DEFAULT_CLOUD_LLM_PROMPT

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 模型选择区域
        Text(
            text = "翻译模型",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedModel.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("选择模型") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                // 普通模型组
                LlmModel.normalModels.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (model == selectedModel) "✓" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = model.displayName,
                                        color = if (model == selectedModel) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = model.modelId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = {
                            onModelChanged(model)
                            expanded = false
                        }
                    )
                }

                // 分隔线
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Thinking 模型组
                Text(
                    text = "Thinking 模型",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LlmModel.thinkingModels.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (model == selectedModel) "✓" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = model.displayName,
                                        color = if (model == selectedModel) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = model.modelId,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = {
                            onModelChanged(model)
                            expanded = false
                        }
                    )
                }
            }
        }

        // 模型说明卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = buildString {
                    append("不同模型的翻译效果和速度不同。\n")
                    append("• 普通模型：响应快速，适合日常翻译\n")
                    append("• Thinking 模型：深度理解，适合复杂文本")
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        HorizontalDivider()

        // 提示词编辑区域
        Text(
            text = "翻译提示词",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = currentPrompt,
            onValueChange = onPromptChanged,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            label = { Text("自定义提示词") },
            placeholder = { Text(DEFAULT_CLOUD_LLM_PROMPT) },
            maxLines = 10,
            minLines = 6
        )

        // 占位符说明
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "可用占位符：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "• {text} - 待翻译的文本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "• {sourceLanguage} - 源语言（${sourceLanguage.displayName}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "• {targetLanguage} - 目标语言（${targetLanguage.displayName}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        // 重置按钮
        OutlinedButton(
            onClick = onResetPrompt,
            enabled = isPromptModified,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("重置为默认提示词")
        }
    }
}

/**
 * 云端 LLM 配置区域组件（简化版）
 *
 * 仅提供模型选择，不包含提示词编辑。
 *
 * @param selectedModel 当前选中的模型
 * @param onModelChanged 模型变化回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudLlmModelSelector(
    selectedModel: LlmModel,
    onModelChanged: (LlmModel) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "云端 LLM 模型",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedModel.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("选择模型") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                // 推荐模型优先显示
                LlmModel.recommendedNormalModels.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (model == selectedModel) "✓" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = model.displayName,
                                        color = if (model == selectedModel) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        },
                        onClick = {
                            onModelChanged(model)
                            expanded = false
                        }
                    )
                }

                // 分隔线
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Thinking 模型
                Text(
                    text = "Thinking 模型",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LlmModel.recommendedThinkingModels.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (model == selectedModel) "✓" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = model.displayName,
                                        color = if (model == selectedModel) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }
                        },
                        onClick = {
                            onModelChanged(model)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
