package com.cw2.cw_1kito.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cw2.cw_1kito.model.OcrLanguage

/**
 * OCR 语言选择器
 *
 * 允许用户手动指定 OCR 识别语言，或使用自动推断
 *
 * @param selectedLanguage 当前选择的语言（null 表示自动）
 * @param onLanguageSelected 语言选择回调
 * @param modifier Modifier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrLanguageSelector(
    selectedLanguage: OcrLanguage?,
    onLanguageSelected: (OcrLanguage?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = "OCR 识别语言",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = selectedLanguage?.displayName ?: "自动推断",
                onValueChange = {},
                readOnly = true,
                label = { Text("选择语言") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // 自动推断选项
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedLanguage == null) {
                                Text(
                                    text = "✓ ",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } else {
                                Text("   ")
                            }
                            Column {
                                Text("自动推断")
                                Text(
                                    "根据翻译语言智能选择",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onLanguageSelected(null)
                        expanded = false
                    }
                )

                HorizontalDivider()

                // 各语言选项
                OcrLanguage.values().forEach { language ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectedLanguage == language) {
                                    Text(
                                        text = "✓ ",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                } else {
                                    Text("   ")
                                }
                                Text(language.displayName)
                            }
                        },
                        onClick = {
                            onLanguageSelected(language)
                            expanded = false
                        }
                    )
                }
            }
        }

        // 说明文本
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when (selectedLanguage) {
                null -> "将根据源语言和目标语言自动选择最合适的识别器"
                OcrLanguage.CHINESE -> "使用中文识别器，适合识别简体中文、繁体中文"
                OcrLanguage.JAPANESE -> "使用日文识别器，适合识别平假名、片假名、汉字"
                OcrLanguage.KOREAN -> "使用韩文识别器，适合识别韩文字母"
                OcrLanguage.LATIN -> "使用拉丁语系识别器，适合识别英文、法文、德文等"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
