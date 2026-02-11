# Key Points List

**Generated:** 2025-02-10
**Updated:** 2025-02-10 (基于用户澄清)
**Source:** 安卓翻译app功能需求分析.md

---

## Keywords

- **屏幕即时翻译** (Screen Instant Translation)
- **全局悬浮窗** (Global Floating Window)
- **多模态大模型** (VLM - Vision Language Model)
- **OCR 坐标定位** (OCR Coordinate Localization)
- **MediaProjection API**
- **WindowManager**
- **Kotlin**
- **Jetpack Compose**
- **Android Service**
- **覆盖层绘制** (Overlay Rendering)
- **防腐层** (Anti-Corruption Layer)
- **硅基流动 API** (SiliconFlow)

---

## New Concepts

### 全局透明覆盖层
- 应用隐藏后留下小图标（悬浮球）
- 其他应用在透明层下方运行
- 点击悬浮球触发截图和翻译流程

### 多模态坐标映射
- 大模型识别文字区域的四个坐标点
- 返回坐标 + 原文 + 翻译文本
- 坐标归一化（外部 API 返回 0-1000，内部转换为 0-1）

### 防腐层设计
- 隔离外部 API 结构变化
- 内部使用独立的领域模型
- 外部 API 响应通过适配器转换为内部模型

### 坐标容错机制
- 边界裁剪（超出 [0,1] 的坐标）
- 最小尺寸扩展（过小的框）
- 中心扩展（向四周均匀扩展）
- 重叠处理（处理重叠的文本框）

### 文本自适应排版
- 固定字号模式
- 自适应字号模式
- 扩展框体模式
- 多行换行模式

---

## Decision Points

### 技术选型
- **开发语言:** Kotlin（优于 Java 的协程、data class、空安全）
- **UI 框架:** Jetpack Compose（声明式 UI，高效）
- **截图方式:** MediaProjection API（Android 5.0+ 强制要求）
- **网络库:** Ktor 或 OkHttp
- **JSON 解析:** Kotlinx Serialization

### 架构决策
- **服务模式:** 前台 Service 保持后台运行
- **悬浮窗类型:** TYPE_APPLICATION_OVERLAY
- **坐标系统:** 外部 API 使用 0-1000，内部转换为 0-1
- **防腐层:** 隔离外部 API，保持内部模型稳定

### API 选择（已确定）
- **提供商:** 硅基流动 (SiliconFlow)
- **API 格式:** 兼容 OpenAI Chat Completions
- **VLM 模型:** GLM-4.6V（默认），支持 Qwen3-VL 系列
- **商业模式:** 用户自备 API Key

### 产品策略（已确定）
- **电池优化:** 需要引导用户关闭电池优化
- **隐私保护:** 截图数据使用后立即从内存销毁
- **延迟优化:** 暂不优化，后续迭代
- **权限体验:** 按通用方案申请和引导用户

---

## Question Points

### 待解决问题（优先级排序）
- **坐标容错参数优化:** 根据实际使用数据调整（P1）
- **文字排版算法优化:** 根据不同场景选择最佳策略（P1）
- **离线模式:** 是否支持本地 OCR（P2）

### 技术挑战（已有方案）
- **延迟优化:** 暂不优化，后续迭代
- **文字排版:** 提供自适应方案（见 New Concepts）
- **坐标准确性:** 提供容错机制（见 New Concepts）
- **权限体验:** 通用方案处理
