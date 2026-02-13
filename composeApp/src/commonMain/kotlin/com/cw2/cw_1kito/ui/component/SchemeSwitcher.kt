package com.cw2.cw_1kito.ui.component

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
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 方案类型枚举
 */
enum class SchemeType(val displayName: String, val description: String) {
    VLM_CLOUD("VLM云端", "使用云端大模型进行 OCR 和翻译，支持更多语言和更高精度"),
    LOCAL_OCR("本地OCR", "使用设备本地 OCR 引擎识别文字，配合本地或云端翻译")
}

/**
 * 方案切换器组件
 *
 * 用于在 VLM 云端方案和本地 OCR 方案之间切换
 *
 * @param useLocalOcr 是否使用本地 OCR 方案（true = 本地OCR，false = VLM云端）
 * @param onSchemeChanged 方案切换回调
 * @param modifier 修饰符
 * @param enabled 是否启用切换（实验室功能未开启时禁用）
 */
@Composable
fun SchemeSwitcher(
    useLocalOcr: Boolean,
    onSchemeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "翻译方案",
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 分段按钮
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            SchemeType.entries.forEachIndexed { index, scheme ->
                val isSelected = if (scheme == SchemeType.LOCAL_OCR) useLocalOcr else !useLocalOcr
                SegmentedButton(
                    selected = isSelected,
                    onClick = { onSchemeChanged(scheme == SchemeType.LOCAL_OCR) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = SchemeType.entries.size
                    ),
                    label = {
                        Text(scheme.displayName)
                    }
                )
            }
        }

        // 描述文本
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (enabled) "ℹ️" else "🔒",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = if (enabled) {
                        if (useLocalOcr) SchemeType.LOCAL_OCR.description else SchemeType.VLM_CLOUD.description
                    } else {
                        "请在实验室设置中启用本地 OCR 功能"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
