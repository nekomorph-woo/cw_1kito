# 模型池方案设计文档

> **版本**: v1.1
> **创建日期**: 2025-02-13
> **最后更新**: 2025-02-13
> **状态**: 待实现

---

## 1. 需求背景

### 1.1 问题描述

- 硅基流动的每个 LLM 模型都有 RPM（每分钟请求数量上限）
- 当前只支持用户选择单个模型，高频翻译时容易触发限流
- 用户体验不佳：某分钟内翻译次数过多后，模型无法使用

### 1.2 解决目标

- 利用模型池分散 RPM 压力
- 降低单一模型达到 RPM 上限的风险
- 每个模型的 RPM 独立计算
- 保持现有的 5 并发批量翻译效率

### 1.3 适用范围

- **模型类型**: LLM 模型（纯文本翻译），非 VLM 模型（图像识别）
- **使用场景**: 本地 OCR + 云端 LLM 翻译模式

---

## 2. 方案概述

### 2.1 核心概念

| 概念 | 说明 |
|------|------|
| **模型池** | 用户配置的 1-5 个云端 LLM 翻译模型的集合 |
| **RPM 限制** | 每个模型默认 RPM=500（可配置） |
| **故障转移** | 当前模型限流时，自动切换到备用模型 |
| **智能调度** | 故障转移时选择 RPM 最充裕的模型 |

### 2.2 工作流程

```
┌──────────────────────────────────────────────────────────────────┐
│                        翻译请求流程                               │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. 用户触发翻译                                                  │
│         ↓                                                        │
│  2. 批量翻译管理器获取待翻译文本列表                               │
│         ↓                                                        │
│  3. 并发分发（5个并发通道）                                        │
│         ├─→ 通道1: 选择模型 → 检查RPM → 可用? → 发起请求           │
│         ├─→ 通道2: 选择模型 → 检查RPM → 不可用 → 故障转移 → 发起   │
│         ├─→ 通道3: 选择模型 → 检查RPM → 可用? → 发起请求           │
│         ├─→ 通道4: 选择模型 → 检查RPM → 可用? → 发起请求           │
│         └─→ 通道5: 选择模型 → 检查RPM → 可用? → 发起请求           │
│         ↓                                                        │
│  4. 记录请求时间戳到滑动窗口                                       │
│         ↓                                                        │
│  5. 返回翻译结果                                                  │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. UI 设计

### 3.1 模型池配置界面

```
┌─────────────────────────────────────────────────────┐
│  云端翻译模型池设置                                   │
├─────────────────────────────────────────────────────┤
│                                                     │
│  首选模型 *                                          │
│  ┌─────────────────────────────────────────────┐   │
│  │ Qwen2.5-7B                            [▼]   │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  备用模型 1                                          │
│  ┌─────────────────────────────────────────────┐   │
│  │ GLM-4.5                               [▼]   │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  备用模型 2                                          │
│  ┌─────────────────────────────────────────────┐   │
│  │ DeepSeek-V3                           [▼]   │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  备用模型 3                                          │
│  ┌─────────────────────────────────────────────┐   │
│  │ 不选择                                [▼]   │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  备用模型 4                                          │
│  ┌─────────────────────────────────────────────┐   │
│  │ 不选择                                [▼]   │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  ℹ️ 模型池可分散请求压力，避免单一模型限流            │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 3.2 UI 规则

| 规则 | 说明 |
|------|------|
| 首选模型必选 | 第一个选择框不能为空 |
| 备用模型可选 | 后4个可选择"不选择" |
| 不可重复 | 同一模型在池中只能出现一次 |
| 默认配置 | 首选=Qwen2.5-7B，其余为"不选择" |

### 3.3 下拉选项（LLM 模型列表）

**普通模型（9个，推荐用于翻译）：**
```
├── Qwen2.5-7B (默认，快速轻量)
├── Qwen2.5-72B (高性能)
├── Qwen3-8B (新一代)
├── GLM-4-9B (智谱轻量)
├── GLM-4-32B (智谱标准)
├── GLM-4.5 (智谱4.5)
├── DeepSeek-V3 (DeepSeek)
├── DeepSeek-R1-8B (Pro镜像)
└── DeepSeek-R1 (推理模型)
```

**Thinking 模型（10个，带特殊参数）：**
```
├── Qwen3-30B (Thinking)
├── Qwen3-235B (Thinking)
├── Qwen2.5-32B (Thinking)
├── Qwen2.5-32B Pro (Thinking)
├── GLM-Z1-9B (Thinking)
├── GLM-Z1-32B (Thinking)
├── GLM-Z1-32B Pro (Thinking)
├── DeepSeek-R1-Distill-32B (Thinking)
├── DeepSeek-R1-Distill-32B Pro (Thinking)
└── DeepSeek-V3-0324 (Thinking)
```

---

## 4. 数据模型

### 4.1 模型池配置

```kotlin
package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 模型池配置
 *
 * 管理用户配置的云端 LLM 翻译模型集合，用于分散 RPM 请求压力。
 *
 * @property models 模型列表，至少 1 个，最多 5 个
 * @property rpmLimit 每个模型的 RPM（每分钟请求数）限制，默认 500
 */
@Serializable
data class ModelPoolConfig(
    val models: List<LlmModel>,
    val rpmLimit: Int = 500
) {
    /**
     * 首选模型，列表中的第一个模型
     */
    val primaryModel: LlmModel get() = models.first()

    /**
     * 备用模型列表，除第一个外的所有模型
     */
    val backupModels: List<LlmModel> get() = models.drop(1)

    init {
        require(models.isNotEmpty()) { "模型池至少需要一个模型" }
        require(models.size <= 5) { "模型池最多支持5个模型" }
        require(models.distinct().size == models.size) { "模型池中不能有重复模型" }
    }

    companion object {
        /**
         * 默认模型池配置，仅包含默认的 Qwen2.5-7B 模型
         */
        val DEFAULT = ModelPoolConfig(
            models = listOf(LlmModel.DEFAULT)
        )
    }
}
```

### 4.2 RPM 追踪器

```kotlin
package com.cw2.cw_1kito.engine.translation

import com.cw2.cw_1kito.model.LlmModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 模型 RPM 追踪器（滑动窗口实现）
 *
 * 追踪每个模型在指定时间窗口内的请求次数，用于本地限流控制。
 *
 * @property rpmLimit RPM 限制，默认 500
 * @property windowSizeMs 时间窗口大小（毫秒），默认 60000（60秒）
 */
class ModelRpmTracker(
    private val rpmLimit: Int = 500,
    private val windowSizeMs: Long = 60_000L
) {
    // 每个模型的请求时间戳队列
    private val requestTimestamps = mutableMapOf<LlmModel, ArrayDeque<Long>>()
    private val lock = Mutex()

    /**
     * 检查模型是否可用（未达到RPM限制）
     */
    suspend fun isAvailable(model: LlmModel): Boolean {
        return lock.withLock {
            val currentCount = getCleanedCount(model)
            currentCount < rpmLimit
        }
    }

    /**
     * 获取模型当前RPM使用量
     */
    suspend fun getCurrentRpm(model: LlmModel): Int {
        return lock.withLock {
            getCleanedCount(model)
        }
    }

    /**
     * 记录一次请求
     */
    suspend fun recordRequest(model: LlmModel) {
        lock.withLock {
            val timestamps = requestTimestamps.getOrPut(model) { ArrayDeque() }
            timestamps.addLast(System.currentTimeMillis())
        }
    }

    /**
     * 获取模型池中RPM最充裕的可用模型
     * @return RPM使用量最少的可用模型，如果全部不可用则返回null
     */
    suspend fun getMostAvailableModel(modelPool: List<LlmModel>): LlmModel? {
        return lock.withLock {
            modelPool
                .filter { getCleanedCount(it) < rpmLimit }
                .minByOrNull { getCleanedCount(it) }
        }
    }

    /**
     * 获取下一个可用时间（毫秒）
     * 用于计算需要等待多久才有配额
     */
    suspend fun getNextAvailableTime(model: LlmModel): Long? {
        return lock.withLock {
            val timestamps = requestTimestamps[model] ?: return null
            if (timestamps.isEmpty() || timestamps.size < rpmLimit) return null

            val now = System.currentTimeMillis()
            val oldestTimestamp = timestamps.first()
            val waitTime = oldestTimestamp + windowSizeMs - now
            if (waitTime > 0) waitTime else null
        }
    }

    /**
     * 清理过期时间戳并返回当前计数
     */
    private fun getCleanedCount(model: LlmModel): Int {
        val now = System.currentTimeMillis()
        val timestamps = requestTimestamps.getOrPut(model) { ArrayDeque() }

        // 移除60秒前的记录
        while (timestamps.isNotEmpty() && now - timestamps.first() > windowSizeMs) {
            timestamps.removeFirst()
        }

        return timestamps.size
    }
}
```

---

## 5. 模型选择器

### 5.1 核心逻辑

```kotlin
package com.cw2.cw_1kito.engine.translation

import com.cw2.cw_1kito.model.LlmModel
import com.cw2.cw_1kito.model.ModelPoolConfig
import kotlinx.coroutines.delay

/**
 * 模型选择器 - 负责从模型池中选择合适的模型
 *
 * 实现优先首选、故障转移的选择策略。
 */
class ModelSelector(
    private val modelPoolConfig: ModelPoolConfig,
    private val rpmTracker: ModelRpmTracker
) {
    /**
     * 选择一个可用模型
     * @return 选中的模型，如果全部不可用则返回null
     */
    suspend fun selectModel(): LlmModel? {
        // 1. 优先尝试首选模型
        val primaryModel = modelPoolConfig.primaryModel
        if (rpmTracker.isAvailable(primaryModel)) {
            return primaryModel
        }

        // 2. 首选模型限流，尝试故障转移到备用模型
        val backupModels = modelPoolConfig.backupModels
        if (backupModels.isNotEmpty()) {
            val availableBackup = rpmTracker.getMostAvailableModel(backupModels)
            if (availableBackup != null) {
                return availableBackup
            }
        }

        // 3. 所有模型都限流，返回null由调用方决定策略
        return null
    }

    /**
     * 选择模型，如果全部不可用则等待
     * @param maxWaitMs 最大等待时间（毫秒）
     * @return 选中的模型
     */
    suspend fun selectModelWithWait(maxWaitMs: Long = 5000): LlmModel? {
        val selected = selectModel()
        if (selected != null) return selected

        // 全部限流，计算最短等待时间
        val allModels = modelPoolConfig.models
        val waitInfo = allModels.mapNotNull { model ->
            rpmTracker.getNextAvailableTime(model)?.let { model to it }
        }.minByOrNull { it.second }

        if (waitInfo != null && waitInfo.second <= maxWaitMs) {
            delay(waitInfo.second)
            return waitInfo.first
        }

        return null
    }
}
```

### 5.2 选择策略总结

```
┌─────────────────────────────────────────────────────────┐
│                    模型选择策略                          │
├─────────────────────────────────────────────────────────┤
│  优先级1: 首选模型可用                                   │
│           → 直接使用首选模型                             │
│                                                         │
│  优先级2: 首选模型限流，备用模型可用                      │
│           → 故障转移到RPM最充裕的备用模型                 │
│                                                         │
│  优先级3: 所有模型限流                                   │
│           → 等待最短时间获得配额的模型                    │
│           → 若等待时间>5秒，返回null（提示用户）          │
└─────────────────────────────────────────────────────────┘
```

---

## 6. 批量翻译集成

### 6.1 与现有架构的整合

```kotlin
/**
 * 批量翻译管理器（增强版）
 */
class BatchTranslationManagerImpl(
    private val cloudLlmEngine: ICloudLlmEngine,
    private val configManager: ConfigManager,
    private val batchSize: Int = 5
) : IBatchTranslationManager {

    private val rpmTracker = ModelRpmTracker()
    private var currentModelPoolConfig: ModelPoolConfig = ModelPoolConfig.DEFAULT

    suspend fun updateModelPoolConfig(config: ModelPoolConfig) {
        currentModelPoolConfig = config
    }

    private suspend fun translateWithModelPool(
        texts: List<String>,
        sourceLang: Language,
        targetLang: Language,
        customPrompt: String?
    ): List<String> = coroutineScope {
        val modelSelector = ModelSelector(currentModelPoolConfig, rpmTracker)

        texts.map { text ->
            async(Dispatchers.IO) {
                translateSingleWithModelPool(text, sourceLang, targetLang, customPrompt, modelSelector)
            }
        }.awaitAll()
    }

    private suspend fun translateSingleWithModelPool(
        text: String,
        sourceLang: Language,
        targetLang: Language,
        customPrompt: String?,
        modelSelector: ModelSelector
    ): String {
        // 1. 从模型池选择可用模型
        val model = modelSelector.selectModelWithWait(maxWaitMs = 3000)
            ?: throw ModelPoolExhaustedException()

        // 2. 记录请求（在选择后、发送前）
        rpmTracker.recordRequest(model)

        // 3. 发起翻译请求
        return cloudLlmEngine.translate(
            text = text,
            sourceLang = sourceLang,
            targetLang = targetLang,
            customPrompt = customPrompt,
            model = model
        )
    }
}
```

### 6.2 并发流程图

```
并发=5，模型池=[A, B, C]

时间线：
────────────────────────────────────────────────────────►

T1:
  通道1: 选择A → RPM(A)=1 → 请求中...
  通道2: 选择A → RPM(A)=2 → 请求中...
  通道3: 选择A → RPM(A)=3 → 请求中...
  通道4: 选择A → RPM(A)=4 → 请求中...
  通道5: 选择A → RPM(A)=5 → 请求中...

T2: (假设A接近限流，RPM=496)
  通道1: 选择A → RPM(A)=497 → 请求中...
  通道2: 选择A → RPM(A)=498 → 请求中...
  通道3: A限流 → 故障转移B → RPM(B)=1 → 请求中...
  通道4: A限流 → 故障转移B → RPM(B)=2 → 请求中...
  通道5: A限流 → 故障转移B → RPM(B)=3 → 请求中...

T3: (假设A、B都限流)
  通道1: A限流 → B限流 → 故障转移C → RPM(C)=1 → 请求中...
  通道2: A限流 → B限流 → 故障转移C → RPM(C)=2 → 请求中...
  通道3: A限流 → B限流 → C限流 → 等待A释放 → ...
  ...
```

---

## 7. 配置持久化

### 7.1 ConfigManager 接口扩展

```kotlin
// ConfigManager.kt 中新增
interface ConfigManager {
    // ... 现有方法

    /**
     * 获取模型池配置
     */
    suspend fun getModelPoolConfig(): ModelPoolConfig

    /**
     * 保存模型池配置
     */
    suspend fun saveModelPoolConfig(config: ModelPoolConfig)
}

// ConfigChange sealed class 中新增
sealed class ConfigChange {
    // ... 现有事件

    /**
     * 模型池配置变更事件
     */
    data class ModelPoolConfigChanged(val config: ModelPoolConfig) : ConfigChange()
}
```

### 7.2 存储位置

| 平台 | 存储方式 |
|------|---------|
| Android | SharedPreferences / DataStore |
| 未来iOS | UserDefaults |

---

## 8. 边界情况处理

### 8.1 场景列表

| 场景 | 处理方式 |
|------|---------|
| 模型池只有1个模型 | 正常工作，无故障转移能力 |
| 所有模型达到RPM限制 | 等待最短时间或提示用户 |
| 网络请求失败 | 不回退RPM计数，抛出异常由上层处理 |
| 用户修改模型池配置 | 即时生效，新请求使用新配置 |
| 应用重启 | RPM计数器重置（正常，不影响体验） |
| Thinking 模型特殊处理 | ICloudLlmEngine 实现中已处理 enable_thinking 参数 |

### 8.2 全部限流时的用户提示

```
┌─────────────────────────────────────┐
│  ⚠️ 请求过于频繁                      │
│                                     │
│  当前所有模型均达到每分钟请求上限，    │
│  请等待约 X 秒后重试                 │
│                                     │
│         [ 知道了 ]                   │
└─────────────────────────────────────┘
```

---

## 9. 性能考虑

### 9.1 内存占用

| 数据 | 大小 |
|------|------|
| 每个时间戳 | 8 bytes (Long) |
| 每个模型最大记录 | 500 × 8 = 4KB |
| 5个模型最大占用 | ~20KB |

### 9.2 时间复杂度

| 操作 | 复杂度 |
|------|--------|
| isAvailable() | O(n) 清理过期 + O(1) 比较 |
| recordRequest() | O(1) |
| getMostAvailableModel() | O(n × m)，n=模型数，m=时间戳数 |

---

## 10. 模型使用统计（扩展功能）

### 10.1 数据模型

```kotlin
/**
 * 模型使用统计
 */
data class ModelUsageStats(
    val model: LlmModel,
    val dailyStats: List<DailyStats>  // 近7天数据
)

data class DailyStats(
    val date: LocalDate,
    val requestCount: Int
)
```

### 10.2 UI 展示（近7天折线图）

```
┌─────────────────────────────────────────────────────┐
│  模型使用统计（近7天）                                │
├─────────────────────────────────────────────────────┤
│                                                     │
│  Qwen2.5-7B                                         │
│  500│    ╭─╮                                        │
│  400│    │ │  ╭╮                                     │
│  300│ ╭╮ │ │  ││ ╭╮                                  │
│  200│ ││ ││  ││ ││                                   │
│  100│ ││ ││  ││ ││ ╭╮                                │
│    0└──┴─┴──┴┴──┴┴─┴┴──┘                            │
│      Mon Tue Wed Thu Fri Sat Sun                     │
│                                                     │
│  GLM-4.5                                            │
│  300│        ╭╮                                      │
│  200│   ╭╮   ││  ╭╮                                  │
│  100│   ││ ╭╮││  ││                                  │
│    0└───┴┴─┴┴┴┴──┴┴──┘                              │
│      Mon Tue Wed Thu Fri Sat Sun                     │
│                                                     │
└─────────────────────────────────────────────────────┘
```

### 10.3 实现要点

- 使用 Room/SQLite 持久化每日统计
- 每次请求成功后记录一次
- 定期清理超过7天的数据
- 使用简单的 Canvas 绘制折线图

---

## 11. 后续扩展

### 11.1 可配置 RPM

```kotlin
// 未来可支持每个模型单独配置RPM
data class ModelPoolConfig(
    val models: List<ModelConfig>,  // 改为包含RPM配置
    val defaultRpmLimit: Int = 500
)

data class ModelConfig(
    val model: LlmModel,
    val rpmLimit: Int? = null  // null则使用默认值
)
```

---

## 12. 实现清单

### 12.1 新增文件

| 文件 | 说明 |
|------|------|
| `model/ModelPoolConfig.kt` | 模型池配置数据类（使用 LlmModel） |
| `engine/translation/ModelRpmTracker.kt` | RPM追踪器（使用 LlmModel） |
| `engine/translation/ModelSelector.kt` | 模型选择器 |
| `engine/translation/ModelPoolExhaustedException.kt` | 模型池耗尽异常 |
| `ui/component/ModelPoolSelector.kt` | 模型池配置UI组件 |

### 12.2 修改文件

| 文件 | 修改内容 |
|------|---------|
| `engine/translation/BatchTranslationManager.kt` | 集成模型池选择逻辑 |
| `engine/translation/ICloudLlmEngine.kt` | 添加 model 参数支持 |
| `model/LlmModel.kt` | 确保 @Serializable 注解 |
| `ui/screen/SettingsScreen.kt` | 添加模型池配置UI |
| `data/config/ConfigManager.kt` | 添加模型池配置存取方法 |
| `data/config/ConfigManagerImpl.kt` | 实现模型池配置存取 |
| `MainViewModel.kt` | 传递模型池配置到翻译流程 |

---

## 13. 方案总结

| 项目 | 说明 |
|------|------|
| **方案类型** | 方案C：并发通道 + 故障转移 |
| **限流检测** | 本地滑动窗口（60秒窗口，精确到毫秒） |
| **重试策略** | 故障转移时选择RPM最充裕的模型 |
| **默认RPM** | 500（所有模型统一） |
| **模型池大小** | 1-5个，首选必选，备用可选 |
| **模型类型** | LLM 模型（19个可选） |
| **并发数** | 5 |

---

## 附录：方案对比（决策记录）

### A.1 备选方案

| 方案 | 核心思路 | 优点 | 缺点 |
|------|---------|------|------|
| A. 轮询分发 | 按固定顺序循环使用模型 | 实现简单 | 不感知RPM状态 |
| B. RPM追踪+智能调度 | 实时追踪RPM，精确控制 | 效果最好 | 实现复杂 |
| **C. 故障转移** | 优先首选，限流时切换 | 用户体验好，复杂度适中 | 需重试逻辑 |

### A.2 选择理由

1. **用户直觉**：首选模型优先，符合用户期望
2. **故障转移**：自动切换到备用模型，用户无感知
3. **复杂度适中**：既能有效分散压力，又不过度设计
4. **可扩展**：后续可叠加RPM精确追踪功能
