# Structured Notes

**Generated:** 2025-02-10
**Updated:** 2025-02-10 (基于用户澄清)
**Source:** 安卓翻译app功能需求分析.md

---

## Dimension 1: Business / Value

### Pain Points

#### [P1] 传统翻译应用的局限性
- 需要切换应用，复制粘贴文本
- 无法翻译图片中的文字（如游戏界面、社交媒体图片）
- 无法在任意应用内直接获取翻译

#### [P2] OCR + 翻译分离的问题
- 现有方案通常是先 OCR 识别，再翻译，两次操作
- 识别后的文字丢失了原文的上下文位置
- 翻译结果无法在原位置展示

#### [P3] 多语言场景的痛点
- 阅读外文游戏、应用界面时频繁切换字典
- 看漫画/读图时无法快速理解外文内容
- 语言学习者的即时翻译需求

---

### Core Interaction

#### [I1] 主设置界面交互
1. 用户打开应用
2. 配置源语言（自动识别/英/日/韩等）
3. 配置目标语言（如中文）
4. **配置 API Key（用户自备，从硅基流动获取）**
5. 点击"开启全局悬浮窗"按钮
6. **引导用户关闭电池优化（确保后台服务稳定）**

#### [I2] 悬浮窗交互
1. 应用后台运行，显示悬浮球图标
2. 悬浮球可拖拽调整位置
3. 点击悬浮球触发截图翻译
4. 等待期间显示加载动画
5. 翻译结果以覆盖层形式展示在屏幕上

#### [I3] 翻译结果交互
1. 白色矩形框覆盖原文位置
2. 框内显示翻译文本（自适应排版）
3. 支持多组文本同时展示
4. 点击覆盖层关闭翻译结果

---

### Value Proposition

#### [V1] 一键即时翻译
- 无需切换应用，无需复制粘贴
- 单击悬浮球即可获取全屏翻译
- 保持应用上下文，不中断用户流程

#### [V2] 原位覆盖展示
- 翻译结果直接覆盖在原文位置
- 保留原文的视觉上下文
- 白色背景保证可读性

#### [V3] 多模态智能识别
- 利用硅基流动 VLM 的视觉能力
- 自动识别文字区域
- 同时提供 OCR 和翻译，一步完成

#### [V4] 多语言支持
- 支持中/英/日/韩等多种语言
- 自动识别源语言
- 灵活配置目标语言

#### [V5] 隐私保护
- 用户自备 API Key，数据不经过第三方服务器
- 截图数据使用后立即销毁
- 不收集用户使用行为数据

---

## Dimension 2: Technical / Architecture

### Data Flow

#### [D1] 截图翻译流程
```
用户点击悬浮球
    ↓
MediaProjection 截取屏幕
    ↓
图像压缩（保持清晰度前提下减少流量）
    ↓
防腐层转换为外部 API 格式
    ↓
发送到硅基流动 API（带 Prompt）
    ↓
API 返回 JSON（坐标 + 原文 + 译文）
    ↓
防腐层转换为内部领域模型
    ↓
坐标验证和调整（容错处理）
    ↓
文本自适应排版计算
    ↓
创建全屏透明覆盖层
    ↓
绘制白色矩形 + 翻译文本
    ↓
截图数据从内存中销毁
```

#### [D2] 数据格式
- **内部输入:** Bitmap (截图) + 语言配置 + VLM 模型选择
- **外部请求:** OpenAI 兼容格式，包含 Base64 图像和 Prompt
- **外部响应:** 硅基流动 API 格式（choices.message.content）
- **内部输出:** List\<TranslationResult\>，包含归一化坐标 (0-1)

---

### Key Components

#### [C1] 主设置模块 (MainActivity)
- 语言选择器
- **API Key 配置界面（用户自备）**
- **API Key 验证功能**
- 权限申请引导
- **电池优化引导**
- 启动悬浮窗服务入口

#### [C2] 悬浮窗服务 (FloatingService)
- 前台 Service 保持后台运行
- WindowManager 管理悬浮球
- 处理悬浮球点击/拖拽事件
- **电池优化检查和提示**

#### [C3] 屏幕截图模块 (ScreenCapture)
- MediaProjectionManager 权限请求
- VirtualDisplay + ImageReader 获取画面
- Bitmap 转换和压缩
- **使用后立即释放资源**

#### [C4] 防腐层 (Anti-Corruption Layer)
- **组件职责:**
  - 将内部请求转换为硅基流动 API 格式
  - 将硅基流动 API 响应转换为内部领域模型
  - 处理坐标归一化（0-1000 → 0-1）
  - 验证和清理外部数据
- **支持的模型:** GLM-4.6V, Qwen3-VL 系列

#### [C5] 坐标验证器 (CoordinateValidator)
- 边界裁剪（超出 [0,1] 的坐标）
- 最小尺寸扩展（框体过小时）
- 中心扩展策略
- 重叠检测和处理

#### [C6] 文本排版引擎 (TextLayoutEngine)
- 自适应字号计算
- 多行文本处理
- 溢出检测和处理
- 中英文混合排版

#### [C7] 覆盖层绘制模块 (OverlayRenderer)
- 自定义 View 或 Compose 组件
- Canvas 绘制白色矩形和文本
- 应用坐标验证和文本排版结果

---

### Constraints

#### [T1] Android 系统限制
- **悬浮窗权限:** SYSTEM_ALERT_WINDOW，需用户手动授权
- **录屏权限:** 每次启动服务需用户确认（按通用方案处理）
- **后台限制:** Android 8.0+ 需要前台服务
- **电池优化:** 系统可能杀死后台服务，需引导用户关闭

#### [T2] API 限制（硅基流动）
- **认证方式:** Bearer Token（用户自备 API Key）
- **请求格式:** OpenAI Chat Completions 兼容格式
- **支持模型:** GLM-4.6V, GLM-4.5V, Qwen3-VL 系列
- **额外参数:** enable_thinking, thinking_budget（仅部分模型）

#### [T3] 网络和性能限制
- **延迟:** 完整流程预计 3-10 秒（暂不优化）
- **流量:** 截图压缩后仍需上传
- **成本:** 用户自备 API Key，自行承担费用

#### [T4] UI 显示限制
- **坐标准确性:** 已设计容错机制
- **文字溢出:** 已设计自适应排版
- **多语言排版:** 已设计中英文混合处理

---

## Dimension 3: Specs / Constraints

### Input/Output

#### [S1] 内部 API 请求格式
```kotlin
data class TranslationRequest(
    val model: VlmModel,           // 内部模型枚举
    val imageData: String,          // Base64
    val targetLanguage: Language,
    val sourceLanguage: Language = Language.AUTO
)
```

#### [S2] 外部 API 请求格式（硅基流动）
```json
{
  "model": "zai-org/GLM-4.6V",
  "messages": [{
    "role": "user",
    "content": [
      {"type": "text", "text": "识别并翻译..."},
      {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}
    ]
  }],
  "temperature": 0.7,
  "max_tokens": 1000
}
```

#### [S3] 外部 API 响应格式（硅基流动）
```json
{
  "id": "...",
  "choices": [{
    "message": {
      "content": "[{\"original_text\":\"...\",\"translated_text\":\"...\",\"coordinates\":[...]}]"
    }
  }],
  "usage": {...}
}
```

#### [S4] 内部领域模型格式
```kotlin
data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val boundingBox: BoundingBox  // 归一化 0-1
)

data class BoundingBox(
    val left: Float,    // 0.0 - 1.0
    val top: Float,     // 0.0 - 1.0
    val right: Float,   // 0.0 - 1.0
    val bottom: Float   // 0.0 - 1.0
)
```

---

### Directory Structure

#### [R1] Kotlin Multiplatform 项目结构
```
cw_1Kito/
├── composeApp/
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/
│       │       ├── ui/                      # Compose UI 组件
│       │       ├── data/                    # 数据模型
│       │       │   ├── model/               # 内部领域模型
│       │       │   └── api/                 # 外部 API 模型
│       │       ├── domain/                  # 业务逻辑
│       │       │   ├── TranslationManager.kt
│       │       │   ├── CoordinateValidator.kt
│       │       │   └── TextLayoutEngine.kt
│       │       └── adapter/                 # 防腐层适配器
│       │           ├── TranslationApiAdapter.kt
│       │           └── SiliconFlowAdapter.kt
│       ├── androidMain/
│       │   ├── kotlin/
│       │   │   ├── service/                 # Service 实现
│       │   │   ├── permission/              # 权限处理
│       │   │   ├── capture/                 # MediaProjection
│       │   │   └── security/                # Keystore 管理
│       │   └── res/
│       └── commonTest/
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
└── settings.gradle.kts
```

---

### Error Handling

#### [E1] 权限相关错误
- **悬浮窗权限被拒绝:** 引导用户到系统设置
- **录屏权限被拒绝:** 显示说明，重新请求（通用方案）
- **权限被撤销:** 服务停止，通知用户

#### [E2] API 相关错误
- **API Key 无效:** 提示用户检查配置，提供验证功能
- **网络不可用:** 提示用户检查网络连接
- **API 超时:** 显示重试按钮
- **API 限流:** 显示提示，稍后重试

#### [E3] 渲染相关错误
- **坐标无效:** 应用坐标验证和裁剪
- **文本过长:** 应用文本自适应排版
- **绘制失败:** 降级为 Toast 显示纯文本

---

### Platform Requirements

#### [PR1] Android 版本
- **最低版本:** API 24 (Android 7.0)
- **目标版本:** API 36 (Android 14)
- **推荐版本:** API 26+ (更好的后台服务支持)

#### [PR2] 设备要求
- **内存:** 建议 3GB+
- **存储:** < 50MB 应用大小
- **网络:** Wi-Fi 或 4G/5G

#### [PR3] API 配置要求
- **API Key 来源:** 用户从硅基流动获取 (https://cloud.siliconflow.cn/)
- **支持模型:** GLM-4.6V（默认）或 Qwen3-VL 系列
- **费用:** 用户自行承担
