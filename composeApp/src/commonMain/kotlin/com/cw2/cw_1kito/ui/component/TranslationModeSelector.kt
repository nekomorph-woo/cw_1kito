package com.cw2.cw_1kito.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cw2.cw_1kito.model.TranslationMode

/**
 * 翻译模式选择器组件
 *
 * 用于本地OCR场景下的翻译方式选择
 * - LOCAL: 使用 Google ML Kit 本地翻译
 * - REMOTE: 使用云端 LLM 翻译
 *
 * @param currentMode 当前翻译模式
 * @param onModeChange 模式变化回调
 * @param modifier 修饰符
 */
@Composable
fun TranslationModeSelector(
    currentMode: TranslationMode,
    onModeChange: (TranslationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "翻译模式",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 本地 ML Kit 翻译选项
        TranslationModeRadioOption(
            title = "本地 ML Kit 翻译",
            description = "完全离线，需要下载语言包",
            selected = currentMode == TranslationMode.LOCAL,
            onClick = { onModeChange(TranslationMode.LOCAL) }
        )

        // 云端 LLM 翻译选项
        TranslationModeRadioOption(
            title = "云端 LLM 翻译",
            description = "使用普通LLM（非VLM），支持更多语言和更高质量",
            selected = currentMode == TranslationMode.REMOTE,
            onClick = { onModeChange(TranslationMode.REMOTE) },
            showCloudHint = true
        )
    }
}

/**
 * 翻译模式单选选项
 *
 * @param title 选项标题
 * @param description 选项描述
 * @param selected 是否选中
 * @param onClick 点击回调
 * @param showCloudHint 是否显示云端配置提示
 * @param modifier 修饰符
 */
@Composable
private fun TranslationModeRadioOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCloudHint: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showCloudHint) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "提示: 选择后可在下方配置云端模型和提示词",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
