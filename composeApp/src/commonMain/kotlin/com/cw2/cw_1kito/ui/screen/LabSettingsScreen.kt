package com.cw2.cw_1kito.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cw2.cw_1kito.ui.component.ApiKeySection
import com.cw2.cw_1kito.ui.component.OcrLanguageSelector
import com.cw2.cw_1kito.ui.theme.*
import com.cw2.cw_1kito.model.OcrLanguage

/**
 * 实验室设置页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabSettingsScreen(
    enableLocalOcr: Boolean,
    onEnableLocalOcrChange: (Boolean) -> Unit,
    streamingEnabled: Boolean,
    onStreamingEnabledChange: (Boolean) -> Unit,
    textMergingEnabled: Boolean,
    onTextMergingEnabledChange: (Boolean) -> Unit,
    ocrLanguage: OcrLanguage?,
    onOcrLanguageChange: (OcrLanguage?) -> Unit,
    apiKey: String = "",
    isApiKeyValid: Boolean = false,
    onApiKeyChange: (String) -> Unit = {},
    themeConfig: ThemeConfig = ThemeConfig.DEFAULT,
    onThemeHueChange: (ThemeHue) -> Unit = {},
    onDarkModeChange: (DarkModeOption) -> Unit = {},
    onResetTheme: () -> Unit = {},
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("实验室") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text(
                            text = "<",
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 24.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题说明
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "实验性功能",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "以下功能仍在测试中，可能不稳定",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 本地 OCR 翻译开关
            LabSwitchItem(
                title = "本地OCR翻译",
                description = "启用本地OCR引擎进行文字识别，支持离线翻译",
                checked = enableLocalOcr,
                onCheckedChange = onEnableLocalOcrChange
            )

            // 流式翻译开关
            LabSwitchItem(
                title = "流式翻译",
                description = "翻译结果逐条显示，减少等待时间",
                checked = streamingEnabled,
                onCheckedChange = onStreamingEnabledChange
            )

            // 文本提取合并开关
            LabSwitchItem(
                title = "文本提取合并",
                description = "将位置靠近的文本合并到同一对象，而非逐句分隔",
                checked = textMergingEnabled,
                onCheckedChange = onTextMergingEnabledChange
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // OCR 语言选择
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                OcrLanguageSelector(
                    selectedLanguage = ocrLanguage,
                    onLanguageSelected = onOcrLanguageChange
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // API Key 配置区域
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                ApiKeySection(
                    apiKey = apiKey,
                    isValid = isApiKeyValid,
                    onApiKeyChange = onApiKeyChange
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // 主题色设置区域
            ThemeSettingsSection(
                currentTheme = themeConfig,
                onThemeHueChange = onThemeHueChange,
                onDarkModeChange = onDarkModeChange,
                onResetTheme = onResetTheme
            )
        }
    }
}

/**
 * 主题设置区域
 */
@Composable
private fun ThemeSettingsSection(
    currentTheme: ThemeConfig,
    onThemeHueChange: (ThemeHue) -> Unit,
    onDarkModeChange: (DarkModeOption) -> Unit,
    onResetTheme: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "主题色",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // 明暗模式选择
        Text(
            text = "明暗模式",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DarkModeOption.values().forEach { option ->
                DarkModeOptionChip(
                    option = option,
                    selected = currentTheme.darkMode == option,
                    onClick = { onDarkModeChange(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 色调选择
        Text(
            text = "色调",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        // 色调网格：2 行 3 列
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val firstRow = listOf(ThemeHue.DEFAULT, ThemeHue.TEAL, ThemeHue.VIOLET)
            val secondRow = listOf(ThemeHue.ROSE, ThemeHue.FOREST, ThemeHue.AMBER)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                firstRow.forEach { hue ->
                    HueSelector(
                        hue = hue,
                        selected = currentTheme.hue == hue,
                        onClick = { onThemeHueChange(hue) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                secondRow.forEach { hue ->
                    HueSelector(
                        hue = hue,
                        selected = currentTheme.hue == hue,
                        onClick = { onThemeHueChange(hue) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 预览区域
        ThemePreviewCard()

        // 恢复默认按钮
        OutlinedButton(
            onClick = onResetTheme,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("恢复默认主题")
        }
    }
}

/**
 * 明暗模式选项芯片
 */
@Composable
private fun DarkModeOptionChip(
    option: DarkModeOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = option.displayName,
                style = MaterialTheme.typography.bodySmall
            )
        },
        modifier = modifier
    )
}

/**
 * 色调选择器
 */
@Composable
private fun HueSelector(
    hue: ThemeHue,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(hue.seedColor)
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text(
                    text = "/",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Text(
            text = hue.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * 主题预览卡片
 */
@Composable
private fun ThemePreviewCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "预览",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {},
                    modifier = Modifier.weight(1f)
                ) {
                    Text("主要按钮", style = MaterialTheme.typography.bodySmall)
                }

                OutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f)
                ) {
                    Text("次要按钮", style = MaterialTheme.typography.bodySmall)
                }
            }

            // 卡片示例
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "卡片示例",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "这里显示文本效果",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

/**
 * 实验室开关项
 */
@Composable
private fun LabSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
