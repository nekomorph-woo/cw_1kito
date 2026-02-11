package com.cw2.cw_1kito.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cw2.cw_1kito.model.Language

/**
 * 语言选择器组件
 *
 * @param label 标签文本
 * @param selectedLanguage 当前选中的语言
 * @param onLanguageChange 语言变化回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(
    label: String,
    selectedLanguage: Language,
    onLanguageChange: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLanguage.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
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
            Language.entries.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (language == selectedLanguage) "✓" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = language.displayName,
                                color = if (language == selectedLanguage) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    },
                    onClick = {
                        onLanguageChange(language)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 语言配置区域组件
 *
 * @param sourceLanguage 源语言
 * @param targetLanguage 目标语言
 * @param onSourceLanguageChange 源语言变化回调
 * @param onTargetLanguageChange 目标语言变化回调
 * @param modifier 修饰符
 */
@Composable
fun LanguageSection(
    sourceLanguage: Language,
    targetLanguage: Language,
    onSourceLanguageChange: (Language) -> Unit,
    onTargetLanguageChange: (Language) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "语言配置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        LanguageSelector(
            label = "源语言",
            selectedLanguage = sourceLanguage,
            onLanguageChange = onSourceLanguageChange
        )

        LanguageSelector(
            label = "目标语言",
            selectedLanguage = targetLanguage,
            onLanguageChange = onTargetLanguageChange
        )
    }
}
