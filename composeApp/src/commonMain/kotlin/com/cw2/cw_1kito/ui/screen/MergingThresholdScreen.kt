package com.cw2.cw_1kito.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cw2.cw_1kito.model.MergingConfig
import com.cw2.cw_1kito.model.MergingPreset
import com.cw2.cw_1kito.model.toConfig

/**
 * 合并阈值设置界面
 *
 * 允许用户调整文本合并的阈值参数
 *
 * @param config 当前合并配置
 * @param onConfigChange 配置变化回调
 * @param onBack 返回回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergingThresholdScreen(
    config: MergingConfig,
    onConfigChange: (MergingConfig) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var yTolerance by remember { mutableFloatStateOf(config.yTolerance) }
    var xToleranceFactor by remember { mutableFloatStateOf(config.xToleranceFactor) }
    var enableSmartClustering by remember { mutableStateOf(config.enableSmartClustering) }
    var enableSecondPass by remember { mutableStateOf(config.enableSecondPass) }
    var fragmentTextThreshold by remember { mutableIntStateOf(config.fragmentTextThreshold) }
    var selectedPreset by remember { mutableStateOf(MergingPreset.fromConfig(config)) }

    // 更新配置的辅助函数
    fun updateConfig(newConfig: MergingConfig) {
        yTolerance = newConfig.yTolerance
        xToleranceFactor = newConfig.xToleranceFactor
        enableSmartClustering = newConfig.enableSmartClustering
        enableSecondPass = newConfig.enableSecondPass
        fragmentTextThreshold = newConfig.fragmentTextThreshold
        onConfigChange(newConfig)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("合并阈值设置") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← 返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 预设方案（放在最上面）
            item {
                Text(
                    text = "预设方案",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 基础预设
            item {
                Text(
                    text = "基础预设",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                MergingPresetOption(
                    preset = MergingPreset.GAME,
                    selected = selectedPreset == MergingPreset.GAME,
                    onClick = {
                        selectedPreset = MergingPreset.GAME
                        updateConfig(MergingPreset.GAME.toConfig())
                    }
                )
            }

            item {
                MergingPresetOption(
                    preset = MergingPreset.MANGA,
                    selected = selectedPreset == MergingPreset.MANGA,
                    onClick = {
                        selectedPreset = MergingPreset.MANGA
                        updateConfig(MergingPreset.MANGA.toConfig())
                    }
                )
            }

            item {
                MergingPresetOption(
                    preset = MergingPreset.DOCUMENT,
                    selected = selectedPreset == MergingPreset.DOCUMENT,
                    onClick = {
                        selectedPreset = MergingPreset.DOCUMENT
                        updateConfig(MergingPreset.DOCUMENT.toConfig())
                    }
                )
            }

            // 高级预设
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "高级预设（智能算法）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                MergingPresetOption(
                    preset = MergingPreset.SMART_CLUSTERING,
                    selected = selectedPreset == MergingPreset.SMART_CLUSTERING,
                    onClick = {
                        selectedPreset = MergingPreset.SMART_CLUSTERING
                        updateConfig(MergingPreset.SMART_CLUSTERING.toConfig())
                    }
                )
            }

            item {
                MergingPresetOption(
                    preset = MergingPreset.SECOND_PASS,
                    selected = selectedPreset == MergingPreset.SECOND_PASS,
                    onClick = {
                        selectedPreset = MergingPreset.SECOND_PASS
                        updateConfig(MergingPreset.SECOND_PASS.toConfig())
                    }
                )
            }

            item {
                MergingPresetOption(
                    preset = MergingPreset.AGGRESSIVE,
                    selected = selectedPreset == MergingPreset.AGGRESSIVE,
                    onClick = {
                        selectedPreset = MergingPreset.AGGRESSIVE
                        updateConfig(MergingPreset.AGGRESSIVE.toConfig())
                    }
                )
            }

            // 分隔线
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "手动调整",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Y 轴阈值
            item {
                ThresholdSlider(
                    title = "Y 轴合并阈值",
                    value = yTolerance,
                    valueRange = 0.1f..1.0f,
                    onValueChange = {
                        yTolerance = it
                        selectedPreset = MergingPreset.CUSTOM
                        onConfigChange(config.copy(yTolerance = it))
                    },
                    description = "控制行识别的宽松程度"
                )
            }

            // X 轴阈值
            item {
                ThresholdSlider(
                    title = "X 轴合并阈值",
                    value = xToleranceFactor,
                    valueRange = 0.5f..3.0f,
                    onValueChange = {
                        xToleranceFactor = it
                        selectedPreset = MergingPreset.CUSTOM
                        onConfigChange(config.copy(xToleranceFactor = it))
                    },
                    description = "控制同行文本合并的宽松程度"
                )
            }

            // 高级功能开关
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "高级功能",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 智能聚类开关
            item {
                SwitchCard(
                    title = "智能聚类",
                    description = "自动分析间距分布，智能确定合并阈值。适用于 OCR 识别碎片化严重的场景。",
                    checked = enableSmartClustering,
                    onCheckedChange = {
                        enableSmartClustering = it
                        selectedPreset = MergingPreset.CUSTOM
                        onConfigChange(config.copy(enableSmartClustering = it))
                    }
                )
            }

            // 二次合并开关
            item {
                SwitchCard(
                    title = "二次合并",
                    description = "对短文本碎片进行二次合并。适用于日漫标题等碎片化文本。",
                    checked = enableSecondPass,
                    onCheckedChange = {
                        enableSecondPass = it
                        selectedPreset = MergingPreset.CUSTOM
                        onConfigChange(config.copy(enableSecondPass = it))
                    }
                )
            }

            // 碎片文本阈值（仅在二次合并开启时显示）
            if (enableSecondPass) {
                item {
                    ThresholdSlider(
                        title = "碎片文本长度阈值",
                        value = fragmentTextThreshold.toFloat(),
                        valueRange = 1f..10f,
                        onValueChange = {
                            fragmentTextThreshold = it.toInt()
                            selectedPreset = MergingPreset.CUSTOM
                            onConfigChange(config.copy(fragmentTextThreshold = it.toInt()))
                        },
                        description = "小于此长度的文本将被视为碎片进行二次合并"
                    )
                }
            }
        }
    }
}

/**
 * 应用预设配置
 */
private fun applyPreset(preset: MergingPreset, onConfigChange: (MergingConfig) -> Unit) {
    val config = preset.toConfig()
    onConfigChange(config)
}

/**
 * 阈值滑块组件
 *
 * 显示标题、描述和可调节的滑块
 *
 * @param title 标题
 * @param value 当前值
 * @param valueRange 取值范围
 * @param onValueChange 值变化回调
 * @param description 描述文本
 * @param modifier 修饰符
 */
@Composable
fun ThresholdSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format("%.2f", value),
                    modifier = Modifier.width(60.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 开关卡片组件
 */
@Composable
private fun SwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
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
}

/**
 * 合并预设选项卡片
 *
 * 显示单个预设方案的选项
 *
 * @param preset 预设类型
 * @param selected 是否选中
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun MergingPresetOption(
    preset: MergingPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = preset.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 显示配置详情
                val config = preset.toConfig()
                val features = mutableListOf<String>()
                features.add("Y:${config.yTolerance}")
                features.add("X:${config.xToleranceFactor}")
                if (config.enableSmartClustering) features.add("智能聚类")
                if (config.enableSecondPass) features.add("二次合并")

                Text(
                    text = features.joinToString(" | "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 获取预设方案描述
 */
private fun getPresetDescription(preset: MergingPreset): String {
    return preset.description
}
