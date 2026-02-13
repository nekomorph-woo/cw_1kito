package com.cw2.cw_1kito.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cw2.cw_1kito.model.Language

/**
 * 默认翻译提示词模板
 */
private val DEFAULT_CLOUD_LLM_PROMPT = """
Translate the following {sourceLanguage} text into {targetLanguage}. Return only the translation result without any explanation or additional text.

Original text:
{text}
""".trimIndent()

/**
 * 云端 LLM 提示词编辑器组件
 *
 * 提供提示词编辑功能，支持配置持久化。
 *
 * 注意：模型选择已移至 ModelPoolSelector 组件。
 *
 * @param customPrompt 自定义提示词（null 表示使用默认）
 * @param onPromptChanged 提示词变化回调
 * @param onResetPrompt 重置提示词回调
 * @param sourceLanguage 源语言
 * @param targetLanguage 目标语言
 * @param modifier 修饰符
 */
@Composable
fun CloudLlmPromptEditor(
    customPrompt: String?,
    onPromptChanged: (String) -> Unit,
    onResetPrompt: () -> Unit,
    sourceLanguage: Language,
    targetLanguage: Language,
    modifier: Modifier = Modifier
) {
    val currentPrompt = customPrompt ?: DEFAULT_CLOUD_LLM_PROMPT
    val isPromptModified = customPrompt != null && customPrompt != DEFAULT_CLOUD_LLM_PROMPT

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
