# 关键点列表

**提取日期:** 2026-02-13
**源文档:** ocr-local-first.md + UI/UX 新需求
**版本:** 1.1

---

## 关键词

### 核心技术
- Google ML Kit Text Recognition
- Google ML Kit Translation
- SiliconFlow (硅基流动)
- Qwen2.5-7B-Instruct (纯文本 LLM)
- Qwen2.5-VL-32B-Instruct (视觉语言 VLM)
- 文本框合并 (Text Bounding Box Merging)
- 横竖布局检测
- 归一化坐标
- 流式翻译
- 本地部署 (On-device Deployment)

### 新增 UI/UX 关键词
- 实验室开关 (Lab Toggle)
- Tab 切换 (Tab Navigation)
- 方案切换器 (Scheme Switcher)
- 翻译模式选择 (Translation Mode Selector)
- 云端 LLM 翻译 (Cloud LLM Translation)
- 本地 ML Kit 翻译 (Local ML Kit Translation)
- 提示词编辑 (Prompt Editor)
- 模型选择器 (Model Selector)
- 持久化配置 (Persistent Configuration)

---

## 新概念

### 1. 图片分辨率降级优化
- 降低 min_pixels/max_pixels 参数以加速推理
- 从 32B 降级到 7B/3B 模型
- 将 max_pixels 从 ~1000k 降到 400k-800k，速度提升 2-4 倍

### 2. 分离式架构 (两步走方案)
- 专用 OCR 模型 (Google ML Kit) 先提取文字+坐标
- 轻量文本模型 (云端 LLM) 纯文本翻译
- 避免 VL 模型的视觉编码开销

### 3. Google ML Kit 本地翻译
- 语言包每个 30-60MB (中英日韩 <300MB)
- 使用 TranslatorOptions.Builder 设置源语言/目标语言
- 协程集成: `translator.downloadModelIfNeeded().await()` + `translate().await()`

### 4. 文本框合并算法
- 按 Y 坐标聚类成行 (yTolerance = 0.3-0.6 × 平均高度)
- 行内按 X 排序合并 (xTolerance = 1.0-2.5 × 平均字符宽)
- 区分横排/竖排: 基于 angle 或宽高比

### 5. 云端 LLM 翻译 (非 VLM)
- 使用普通 LLM 进行文本翻译
- 支持自定义提示词
- 支持多个模型选择
- 需要 API Key 认证

### 6. 批量翻译优化
- **批量大小**: 3 个文本/批次
- **实现方式**: 使用协程并发发起 3 个翻译请求（本地和云端相同）
- **本地翻译**: 3 个 ML Kit 翻译请求并发执行
- **云端翻译**: 3 个 LLM API 请求并发执行
- **性能提升**: 并发处理，提升 3x 吞吐量

### 7. 方案切换架构
- 两种主要方案：
  - **VLM 云端方案**: 截图 → 云端 VLM OCR+翻译
  - **本地 OCR 方案**: 截图 → 本地 OCR → 本地/云端翻译
- 用户可通过 Switcher 在两种方案间切换

### 8. API Key 配置与校验规则
- **API Key 配置位置**: 实验室页面
- **默认方案**: 本地 OCR + 本地 ML Kit 翻译
- **需要 API Key 的场景**:
  - VLM 云端方案
  - 本地 OCR + 云端 LLM 翻译
- **悬浮窗校验规则**:
  - 仅校验权限（悬浮窗权限、截图权限）
  - 不校验 API Key 配置
  - 不校验语言包下载状态
- **用户引导时机**:
  - 使用本地翻译时，引导下载语言包
  - 使用需要 API Key 的方案时，引导配置 API Key

### 7. Tab 导航系统
- 主页面增加 Tab 切换
- Tab 1: VLM 云端翻译（现有功能）
- Tab 2: 本地 OCR 配置（新功能）
- 仅在开启本地 OCR 后显示

---

## 决策点

### 1. 模型选型决策
- **决定:** 使用 Google ML Kit 进行本地 OCR
- **理由:** 官方支持、稳定可靠、持续更新
- **替代方案:** PaddleOCR（已移除）

### 2. 本地优先架构
- **决定:** 采用 Google ML Kit 本地 OCR + 本地/云端翻译
- **理由:** 零网络延迟、隐私保护、成本可控
- **约束:** 需要首次下载语言包

### 3. UI 架构决策
- **决定:** 使用实验室开关控制本地 OCR 启用
- **理由:** 实验性功能、用户可控、逐步推出
- **实现:** Switch 组件 + 持久化存储

### 4. Tab 显示策略
- **决定:** 仅在开启本地 OCR 后显示 Tab
- **理由:** 简化 UI、避免用户困惑
- **默认状态:** 不显示 Tab，直接显示 VLM 云端方案

### 5. 方案切换器设计
- **决定:** 使用顶部 Switcher 切换方案
- **位置:** Tab 上方
- **默认状态:** 关闭（使用 VLM 云端方案）

### 6. 翻译模式选择
- **决定:** 本地 OCR 后可选择本地 ML Kit 或云端 LLM
- **理由:** 灵活性、用户可根据需求选择
- **存储:** 持久化用户选择

---

## 语言包下载策略

### ML Kit 语言包特性
- **OCR 模型**：已预装在 APK 中（通过 `google.mlkit.text.recognition.chinese/japanese/korean` 库）
- **翻译模型**：**无法预装**，需要首次使用时下载
  - 每个 语言对约 20-40MB
  - 由 Google Play Services 管理
  - 下载后缓存到设备本地存储

### 预下载方案
1. **启动时检查**：应用启动时检查语言包状态
2. **后台下载**：自动下载常用语言对（中英日韩 12 个语言对）
3. **进度显示**：显示下载进度和状态
4. **Wi-Fi 提示**：建议在 Wi-Fi 环境下下载
5. **后台服务**：即使退出应用也继续下载

### 需要下载的语言对
| 语言对 | 大小（约） | 优先级 |
|--------|----------|--------|
| 中文 ↔ 英文 | 40MB | P0 |
| 中文 ↔ 日文 | 35MB | P0 |
| 中文 ↔ 韩文 | 35MB | P0 |
| 英文 ↔ 日文 | 30MB | P1 |
| 英文 ↔ 韩文 | 30MB | P1 |
| 日文 ↔ 韩文 | 30MB | P1 |
| **总计** | **~240MB** | - |

---

## 疑问点

### 1. 语言包下载 UI
- **问题:** 是否需要语言包下载进度 UI？
- **状态:** ML Kit 自动管理，可能需要首次使用引导

### 2. 文本合并阈值调优
- **问题:** yTolerance/xTolerance 默认值是否通用？
- **风险:** 日文/中文/韩文间距特征不同
- **缓解:** 提供用户可调参数 + 预设方案

---

## 云端 LLM 模型列表

### 普通模型（直接使用）
| 模型 ID | 显示名称 |
|---------|----------|
| Pro/deepseek-ai/DeepSeek-V3 | DeepSeek-V3 Pro |
| Kwaipilot/KAT-Dev | KAT-Dev |
| zai-org/GLM-4.5-Air | GLM-4.5-Air |
| Qwen/Qwen2.5-72B-Instruct | Qwen2.5-72B |
| Qwen/Qwen2.5-32B-Instruct | Qwen2.5-32B |
| Qwen/Qwen2.5-14B-Instruct | Qwen2.5-14B |
| Qwen/Qwen2.5-7B-Instruct | Qwen2.5-7B ⭐ 默认 |
| Pro/THUDM/glm-4-9b-chat | GLM-4-9B Pro |
| Pro/Qwen/Qwen2.5-7B-Instruct | Qwen2.5-7B Pro |

### Thinking 模型（需要 enable_thinking=false）
| 模型 ID | 显示名称 |
|---------|----------|
| Pro/zai-org/GLM-4.7 | GLM-4.7 Pro |
| deepseek-ai/DeepSeek-V3.2 | DeepSeek-V3.2 |
| Pro/deepseek-ai/DeepSeek-V3.2 | DeepSeek-V3.2 Pro |
| zai-org/GLM-4.6 | GLM-4.6 |
| Qwen/Qwen3-8B | Qwen3-8B |
| Qwen/Qwen3-14B | Qwen3-14B |
| Qwen/Qwen3-32B | Qwen3-32B |
| Qwen/Qwen3-30B-A3B | Qwen3-30B-A3B |
| tencent/Hunyuan-A13B-Instruct | Hunyuan-A13B |
| Pro/deepseek-ai/DeepSeek-V3.1-Terminus | DeepSeek-V3.1-Terminus Pro |

---

## 默认翻译提示词

```
You are a professional expert in localizing Japanese/Korean comics and Japanese games. Please translate the following ${sourceLang.displayName} text into ${targetLang.displayName}.
Return ONLY the translation result.
NO explanation.NO clarification.

Original text:
{text}
```

---

## 新增 UI/UX 需求摘要

### 实验室页面
- 新增"本地 OCR"开关
- 控制本地 OCR 功能的启用/禁用

### 主页面布局（本地 OCR 开启后）
```
┌─────────────────────────────────┐
│  [方案切换器: VLM云端 / 本地OCR]  │
├─────────────────────────────────┤
│  [Tab: VLM云端] [Tab: 本地OCR]   │
├─────────────────────────────────┤
│                                 │
│     Tab 内容区域                │
│                                 │
├─────────────────────────────────┤
│     权限配置区域                │
└─────────────────────────────────┘
```

### 本地 OCR Tab 内容
- 翻译模式选择（单选）
  - [ ] 本地 ML Kit 翻译
  - [ ] 云端 LLM 翻译
- 云端 LLM 配置（选择云端时显示）
  - 提示词编辑框（多行文本）
  - 重置按钮
  - 模型选择器（下拉列表）

### VLM 云端 Tab 内容
- 保持现有功能
- API Key 配置
- 模型选择
- 提示词编辑

---

## 技术实现要点

### 1. 配置管理
```kotlin
data class OcrTranslationConfig(
    val enableLocalOcr: Boolean = false,      // 实验室开关
    val useLocalOcrScheme: Boolean = false,   // 方案切换器
    val translationMode: TranslationMode = TranslationMode.LOCAL_MLKIT,
    val cloudLlmConfig: CloudLlmConfig? = null
)

enum class TranslationMode {
    LOCAL_MLKIT,     // 本地 ML Kit 翻译
    CLOUD_LLM        // 云端 LLM 翻译
}

data class CloudLlmConfig(
    val modelId: String,
    val customPrompt: String,
    val apiProvider: ApiProvider
)
```

### 2. UI 组件
- `LocalOcrToggle`: 实验室开关
- `SchemeSwitcher`: 方案切换器
- `TranslationModeSelector`: 翻译模式选择器
- `CloudLlmConfigEditor`: 云端 LLM 配置编辑器
- `PromptEditor`: 提示词编辑器

### 3. 数据流
```
用户操作 → ConfigManager 更新 → Flow 发送变更
                                → UI 重组
                                → 翻译流程调整
```
