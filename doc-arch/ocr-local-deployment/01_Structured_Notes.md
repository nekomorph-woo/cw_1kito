# 结构化笔记

**分类日期:** 2026-02-12
**源文档:** ocr-local-first.md

---

## 维度 1: 业务/价值

### 痛点 (Pain Points)

#### [P1] 云端 VL 模型推理速度慢
- **当前问题:** 使用 Qwen2.5-VL-32B-Instruct 进行 OCR+坐标+翻译,单次请求 2-5 秒
- **影响:** 用户等待时间长,交互体验差,无法实时翻译
- **根本原因:** VL 模型需处理图像编码器 (ViT),token 消耗大,计算量是纯文本模型的 3-10 倍
- **用户场景:** 手机截图翻译 (游戏界面/漫画/菜单),需要快速反馈

#### [P2] 云端 API 依赖网络,延迟不稳定
- **当前问题:** 依赖网络请求,受限于网络质量和服务器负载
- **影响:** 网络差时无法使用,流量消耗,隐私担忧
- **用户期望:** 离线可用,数据不上传到服务器

#### [P3] 文本框拆分导致阅读困难
- **当前问题:** OCR 输出常将同行文字拆成多个小框 (字/词级别)
- **影响:** 翻译后文本支离破碎,无法保持原文排版
- **典型场景:** 日文游戏 UI、漫画对话框、菜单列表

#### [P4] 横竖混合布局识别混乱
- **当前问题:** 日文漫画/游戏常混排横竖文本,简单 Y 聚类会误合并
- **影响:** 翻译结果错乱,用户无法阅读
- **技术难点:** 需区分方向并分别处理

#### [P5] 云端 API 成本高
- **当前问题:** 使用硅基流动等云服务,按调用量付费
- **影响:** 高频用户成本累积,开发者/用户付费压力大
- **期望:** 本地部署后零边际成本

---

### 核心交互 (Core Interaction)

#### [I1] 截图触发翻译流程
1. 用户通过悬浮窗点击/手势触发截图
2. MediaProjection API 捕获屏幕图像
3. 自动执行 OCR → 文本合并 → 翻译
4. 覆盖层在原位置绘制翻译文本

**关键要素:**
- 截图权限引导 (首次使用)
- 加载状态提示 (OCR/翻译进度)
- 双击关闭覆盖层

#### [I2] 参数配置界面
- **OCR 设置:** 模型选择 (云端 VL vs 本地 PaddleOCR)
- **翻译设置:** 源语言/目标语言,合并阈值调整
- **性能设置:** 图片分辨率限制,流式模式开关

**用户控制点:**
- 平衡速度/精度的模型选择
- 自适应合并阈值微调
- 离线/在线模式切换

#### [I3] 覆盖层交互反馈
- **流式模式:** 逐条显示翻译结果,减少感知延迟
- **触觉反馈:** 翻译完成时震动提示
- **错误处理:** OCR 失败/翻译超时时显示友好提示

**关键场景:**
- 游戏界面翻译 (保持 UI 元素可点击)
- 漫画翻译 (气泡式覆盖,避免遮挡原画)
- 菜单翻译 (列表式对齐)

#### [I4] 语言包管理
- **首次启动:** 下载必需语言包 (中英日韩,每个 30-60MB)
- **设置页:** 显示已下载语言,支持删除/重新下载
- **存储提示:** 低存储空间时警告用户

**用户控制:**
- Wi-Fi 下载偏好
- 后台下载策略
- 清理缓存选项

---

### 价值主张 (Value Proposition)

#### [V1] 本地优先,零网络依赖
- **优势:**
  - 离线可用,无网络延迟
  - 数据不上传,隐私保护
  - 无 API 调用成本
- **对比:**
  - 云端 VL 模型: 2-5 秒/次,网络不稳定时不可用
  - 本地方案: 0.5-1 秒/次,稳定可靠

#### [V2] 分离架构,性能优化 3-10 倍
- **技术原理:** 专用 OCR 模型提取文字 → 轻量文本模型翻译
- **速度提升:**
  - PaddleOCR 本地 OCR: 80-250ms (vs 云端 VL 2-5 秒)
  - Google ML Kit 翻译: <100ms (vs 云端 VL 1-2 秒)
  - **整体延迟:** 从 5-7 秒降至 0.5-1 秒

#### [V3] 智能合并,保持原文排版
- **核心能力:**
  - Y 轴聚类识别行
  - X 轴合并同行文本
  - 横竖排自适应区分
- **用户价值:**
  - 翻译后文本完整可读
  - 保持原文段落结构
  - 支持复杂布局 (漫画、游戏 UI)

#### [V4] 模型选型灵活性
- **本地模式:** PaddleOCR + Google ML Kit (极速、离线)
- **云端模式:** 保留 VL 模型作为 "高质量备选"
- **用户决策:**
  - 离线时自动切换本地
  - 高精度需求时手动选择云端
  - 平衡速度/质量/成本

#### [V5] 设备适配性强
- **旗舰机 (OnePlus 15):**
  - Snapdragon 8 Elite Gen 5: 80-250ms OCR + <100ms 翻译
  - 支持 NPU 加速,功耗优化
- **中端机:** 通过图片分辨率降级、模型量化保持可用
- **渐进策略:**
  - 检测设备性能
  - 动态调整模型复杂度
  - 提供性能模式切换 (极速/平衡/高精)

#### [V6] 成本可控
- **开发者:** 无云服务 API 账单,模型一次性部署
- **用户:** 无流量消耗,无使用次数限制
- **对比:**
  - 云端 API: $1.5/1000 次 (高频用户月成本 $10-50)
  - 本地部署: 零边际成本,仅 APK 体积一次性增加

---

## 维度 2: 技术/架构

### 数据流 (Data Flow)

#### [D1] 截图 → OCR 提取
```
用户截图 → MediaProjection API → Bitmap
  → PaddleOCR.detect(Bitmap)
  → List<TextBox>(text, left, top, right, bottom)
  → (可选) 方向分类标记 (横/竖)
```

**关键转换:**
- 像素坐标 → 归一化 0-1 坐标 (存储)
- 四点多边形 → [left, top, right, bottom] 矩形 (合并)

#### [D2] 文本合并流程
```
原始 OCR 结果 → 按 centerY 排序
  → Y 轴聚类 (yTolerance 阈值)
  → 每行内 X 轴排序
  → X 轴合并 (xTolerance 阈值)
  → 合并后文本 + 坐标 + "\n" 分行
```

**参数来源:**
- yTolerance: 0.3-0.6 × 平均高度 (可配置)
- xTolerance: 1.0-2.5 × 平均字符宽 (可配置)

#### [D3] 翻译流程 (本地模式)
```
合并文本 → Google ML Kit Translator
  → downloadModelIfNeeded().await()
  → translate(text).await()
  → 翻译后文本
```

**优化点:**
- 协程异步下载模型
- 翻译结果缓存 (相同文本复用)
- 错误重试机制 (网络/解析失败)

#### [D4] 翻译流程 (云端模式)
```
合并文本 → SiliconFlow API
  → POST /v1/chat/completions
  → model: "Qwen/Qwen2.5-7B-Instruct"
  → stream: true/false
  → SSE 流式响应或单次 JSON
  → 解析翻译结果
```

**Prompt 模板:**
```
你是一个专业的简体中文翻译助手。将以下日文文本翻译成流畅的简体中文,
保留原文结构和换行,不要添加额外解释:

[粘贴合并后的日文文本]
```

#### [D5] 覆盖层绘制
```
归一化坐标 → 屏幕像素坐标 (× screenWidth/Height)
  → Rect(x, y, width, height)
  → Canvas.drawRect() 白色背景
  → Canvas.drawText() 翻译文本
  → 自适应字号 (根据框大小)
```

**流式模式增量绘制:**
- 每解析一个翻译结果 → addResult()
- 触发 invalidate() 重绘
- 避免等待全部完成才显示

---

### 关键组件 (Key Components)

#### [C1] PaddleOCR Manager (Kotlin)
- **职责:**
  - 加载 Paddle-Lite .nb 模型 (从 assets 复制到内部存储)
  - JNI 调用 C++ 推理接口
  - 返回 `List<OCRResult>(text, boundingBox)`
- **接口:**
  ```kotlin
  class PaddleOCRManager(context: Context) {
      fun init(): Boolean  // 加载模型
      fun recognize(bitmap: Bitmap): List<OCRResult>
      fun release()  // 释放模型资源
  }
  ```
- **依赖:** Paddle-Lite Android AAR (arm64-v8a)

#### [C2] Text Merger Engine
- **职责:**
  - Y 轴聚类 (DBSCAN 简化版或排序分组)
  - X 轴合并 (间隙阈值判断)
  - 横竖排检测 (基于宽高比或方向分类器)
- **接口:**
  ```kotlin
  class TextMerger {
      fun merge(
          boxes: List<TextBox>,
          yTolerance: Float = 0.4f,
          xToleranceFactor: Float = 1.5f
      ): List<MergedText>
  }
  ```
- **算法复杂度:** O(n log n) (排序主导)

#### [C3] Translation Manager (本地/云端适配器)
- **职责:**
  - 模式选择 (本地 ML Kit / 云端 SiliconFlow)
  - 协程封装 (suspend 函数)
  - 错误处理和降级策略
- **接口:**
  ```kotlin
  class TranslationManager {
      suspend fun translate(
          text: String,
          sourceLang: Language,
          targetLang: Language
      ): String
  }
  ```
- **依赖:** Google ML Kit Translate / Ktor HTTP Client

#### [C4] Overlay Renderer
- **职责:**
  - 归一化坐标 → 屏幕像素转换
  - 文本布局计算 (字号自适应、多行排版)
  - Canvas 绘制 (背景框 + 文本)
- **接口:**
  ```kotlin
  class OverlayRenderer(context: Context, screenWidth: Int, screenHeight: Int) {
      fun render(canvas: Canvas, results: List<TranslationResult>)
      fun addResult(result: TranslationResult)  // 流式增量
  }
  ```
- **优化:** 离屏缓存 (Bitmap 缓存已绘制区域)

#### [C5] Configuration Manager
- **职责:**
  - 持久化配置 (EncryptedSharedPreferences)
  - 模型选择状态管理
  - 性能模式切换 (极速/平衡/高精)
- **数据:**
  ```kotlin
  data class AppConfig(
      val ocrMode: OcrMode,  // LOCAL/REMOTE/HYBRID
      val translationMode: TranslationMode,
      val mergingConfig: MergingConfig,
      val performanceMode: PerformanceMode
  )
  ```

---

### 约束 (Constraints)

#### [T1] Android 平台限制
- **MediaProjection 权限:**
  - API 29+ 需要用户手动授权
  - token 只能使用一次,权限过期需重新授权
  - API 34+ 需声明 `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION | SPECIAL_USE`
- **悬浮窗权限:**
  - `SYSTEM_ALERT_WINDOW` 权限需设置页引导
  - 不同 Android 版本 LayoutParams 类型不同
- **前台服务:**
  - 必须显示 Notification (Android 12+ 需请求权限)
  - 电池优化可能杀死服务,需引导用户白名单

#### [T2] 性能约束
- **内存占用:**
  - PaddleOCR 模型 ~15MB
  - Google ML Kit 语言包 ~300MB (中英日韩)
  - Bitmap 缓存需控制 (2K 截图 ~16MB)
  - **限制:** 单次加载不超过 512MB
- **推理速度:**
  - OCR 目标: <250ms (旗舰机), <500ms (中端机)
  - 翻译目标: <100ms/段
  - **超时:** 整体流程 <3 秒,否则取消并提示用户
- **电池优化:**
  - NPU 加速降功耗 (但兼容性不确定)
  - 后台服务需限制唤醒频率

#### [T3] 存储与分发
- **APK 体积:**
  - Google Play 上限 150MB (压缩前)
  - **策略:**
    - 核心模型打包进 APK (~50MB)
    - 可选语言包首次启动下载
    - 提供 "精简版" 和 "完整版" SKU
- **模型更新:**
  - 需版本管理和增量更新机制
  - 下载失败时重试和恢复逻辑
  - 用户可卸载不需要的语言包

#### [T4] 精度与速度平衡
- **模型选型:**
  - 3B 模型: 最快,但专有名词翻译较差
  - 7B 模型: 平衡,推荐默认
  - 32B VL 模型: 最准,但仅云端可用
- **图片分辨率:**
  - 短边 672px: 速度优先
  - 短边 896px: 平衡模式
  - 短边 1080px: 精度优先
- **用户配置:**
  - 提供 "性能模式" 切换 (极速/平衡/高精)
  - 不同模式自动应用不同参数

#### [T5] 技术兼容性
- **PaddleOCR:**
  - 官方 Demo 为 Java,需适配 Kotlin
  - JNI 层 C++ 代码需手动维护
  - **备选:** 社区库 (equationl/paddleocr4android)
- **Google ML Kit:**
  - 最低 API 21 (Android 5.0)
  - 翻译语言包需 Wi-Fi 下载 (可选条件)
  - 不支持自定义 Prompt (专用模型限制)
- **云端 API:**
  - 硅基流动需 API Key (安全存储在 Android Keystore)
  - 流式响应需解析 SSE (Server-Sent Events)

#### [T6] 坐标系统
- **归一化格式:**
  - 内部存储: 0-1 浮点数 (与屏幕尺寸无关)
  - PaddleOCR-VL: 0-1000 整数 (需 ÷1000)
  - 像素坐标: 需 ÷ screenWidth/Height
- **绘制转换:**
  - left = normLeft × screenWidth
  - top = normTop × screenHeight
  - right = normRight × screenWidth
  - bottom = normBottom × screenHeight
- **检测逻辑:**
  - maxVal <= 1000 → 归一化 1000
  - maxVal > 1000 → 像素坐标
  - 结合 imageWidth/imageHeight 元数据判断

---

## 维度 3: 规格/约束

### 输入/输出 (Input/Output)

#### [S1] OCR 输入
- **格式:** Android Bitmap (RGBA_8888)
- **预处理:**
  - Resize 到短边 672-896px (可选)
  - 转灰度 (PaddleOCR 要求)
- **限制:**
  - 最小尺寸: 32×32px
  - 最大尺寸: 4096×4096px (超出需裁剪/缩放)
  - **元数据:** 必须提供 imageWidth/imageHeight (用于坐标转换)

#### [S2] OCR 输出
- **PaddleOCR 格式:**
  ```kotlin
  data class OCRResult(
      val text: String,              // 识别文字
      val boundingBox: RectF,         // 像素坐标矩形
      val confidence: Float,           // 置信度 0-1
      val polygon: List<PointF>?       // 四点多边形 (可选)
  )
  ```
- **PaddleOCR-VL 格式:**
  ```
  原始文本 + <|LOC_x1|><|LOC_y1|>... (8 个 LOC token)
  → 解析为 [x1, y1, x2, y2, x3, y3, x4, y4]
  → 转换为矩形 [left=min(x), top=min(y), right=max(x), bottom=max(y)]
  ```

#### [S3] 文本合并输入/输出
- **输入:** `List<OCRResult>` (原始 OCR 框)
- **配置参数:**
  ```kotlin
  data class MergingConfig(
      val yTolerance: Float = 0.4f,      // Y 轴聚类阈值
      val xToleranceFactor: Float = 1.5f,  // X 轴合并倍数
      val enableDirectionDetection: Boolean = true  // 横竖排检测
  )
  ```
- **输出:**
  ```kotlin
  data class MergedText(
      val text: String,              // 合并后文本 (含 \n 分行)
      val boundingBox: RectF,         // 合并后外接矩形
      val isVertical: Boolean          // 横竖排标记
  )
  ```

#### [S4] 翻译输入/输出
- **本地 ML Kit 输入:**
  ```kotlin
  val sourceLanguage: TranslateLanguage  // JAPANESE/ENGLISH/CHINESE/KOREAN
  val targetLanguage: TranslateLanguage
  val text: String                  // 合并后原文
  ```
- **本地 ML Kit 输出:**
  ```kotlin
  val translatedText: String         // 翻译结果
  val latency: Long               // 推理耗时 (ms)
  ```

- **云端 API 输入:**
  ```json
  {
    "model": "Qwen/Qwen2.5-7B-Instruct",
    "messages": [
      {
        "role": "user",
        "content": "翻译 prompt + 合并后文本"
      }
    ],
    "stream": false
  }
  ```
- **云端 API 输出:**
  ```json
  {
    "choices": [
      {
        "message": {
          "content": "翻译后文本"
        }
      }
    ]
  }
  ```

#### [S5] 覆盖层绘制输入/输出
- **输入:**
  ```kotlin
  data class TranslationResult(
      val originalText: String,
      val translatedText: String,
      val boundingBox: BoundingBox  // 归一化 0-1 坐标
  )

  data class BoundingBox(
      val left: Float,   // 0.0 - 1.0
      val top: Float,
      val right: Float,
      val bottom: Float
  )
  ```
- **输出:** 屏幕 View (通过 WindowManager addView)
  - 绘制白色背景框
  - 绘制翻译文本 (自适应字号、颜色)
  - 支持触摸事件传递 (保持原 UI 可点击)

---

### 目录结构 (Directory Structure)

#### [R1] Kotlin 源码组织
```
composeApp/src/commonMain/kotlin/com/cw2/cw_1kito/
├── model/
│   ├── OcrResult.kt
│   ├── BoundingBox.kt
│   ├── MergedText.kt
│   └── AppConfig.kt
├── data/
│   ├── local/                    # 本地模式实现
│   │   ├── PaddleOCRManager.kt
│   │   ├── MLKitTranslator.kt
│   │   └── jni/              # JNI C++ 代码
│   │       ├── paddle_lite_wrapper.cpp
│   │       └── paddle_lite_wrapper.h
│   └── remote/                   # 云端模式实现
│       ├── SiliconFlowApiClient.kt
│       └── StreamingJsonParser.kt
├── domain/
│   ├── TextMergerEngine.kt      # 合并算法
│   ├── CoordinateConverter.kt     # 坐标转换
│   └── TranslationManager.kt     # 模式适配器
└── ui/
    ├── screen/
    │   ├── SettingsScreen.kt     # 模型/语言/性能设置
    │   └── TranslationModeSelector.kt
    └── component/
        ├── OverlayView.kt         # 覆盖层 Compose 封装
        └── DownloadLanguageDialog.kt  # 语言包下载 UI
```

#### [R2] Android 资源组织
```
composeApp/src/androidMain/
├── jniLibs/
│   └── arm64-v8a/
│       ├── libpaddle_lite_jni.so   # Paddle-Lite 库
│       └── libc++_shared.so
├── assets/
│   ├── models/                   # 模型文件 (首次运行复制到内部存储)
│   │   ├── ch_PP-OCRv5_det_mobile.nb
│   │   ├── ch_PP-OCRv5_rec_mobile.nb
│   │   └── ch_ppocr_mobile_cls.nb
│   └── mlkit/                   # ML Kit 配置 (可选)
│       └── translation_config.json
└── res/
    ├── values/
    │   └── strings.xml          # 翻译字符串
    └── xml/
        └── file_paths.xml        # FileProvider 路径配置
```

#### [R3] 模型文件管理
```
/data/data/com.cw2.cw_1kito/files/
├── models/                      # PaddleOCR 模型
│   ├── det.nb                  # 检测模型
│   ├── rec.nb                  # 识别模型
│   └── cls.nb                  # 方向分类模型
├── mlkit/                      # Google ML Kit 缓存
│   ├── translate_ja_zh.bin
│   ├── translate_en_zh.bin
│   └── translate_ko_zh.bin
└── cache/                      # 图片/翻译缓存
    ├── screenshots/
    └── translations/
```

**生命周期:**
- 首次启动: 从 assets 复制 models/ 到 files/models/
- 后台更新: 从服务器下载 .nb 模型并替换
- 用户清理: 设置页提供 "删除语言包" 和 "清空缓存"

---

### 错误处理 (Error Handling)

#### [E1] OCR 错误
- **模型未加载:**
  - **检测:** `PaddleOCRManager.init()` 返回 false
  - **用户提示:** "OCR 模型加载失败,请检查存储空间或重启应用"
  - **降级:** 切换到云端 API 模式 (如果可用)
- **推理失败:**
  - **检测:** JNI 抛出异常或返回空列表
  - **用户提示:** "OCR 识别失败,请重试或使用更清晰的截图"
  - **日志:** 记录图片尺寸、模型版本、错误堆栈

#### [E2] 文本合并错误
- **合并后文本过长:**
  - **检测:** 单个 MergedText.text.length > 5000 字符
  - **处理:** 强制拆分为多个块 (避免翻译 API 超限)
  - **日志:** 警告 "合并文本过长,已拆分"
- **坐标异常:**
  - **检测:** left > right 或 top > bottom
  - **处理:** 丢弃该框并记录,跳过绘制
  - **日志:** ERROR 级别记录原始坐标

#### [E3] 翻译错误 (本地)
- **语言包未下载:**
  - **检测:** `downloadModelIfNeeded()` 超时 (30 秒)
  - **用户提示:** "翻译语言包下载失败,请检查网络或稍后重试"
  - **重试:** 提供 "重试下载" 按钮
- **翻译失败:**
  - **检测:** `translate()` 抛出异常
  - **用户提示:** "翻译失败,请检查网络或切换语言"
  - **降级:** 尝试云端 API (如果配置了 API Key)

#### [E4] 翻译错误 (云端)
- **API Key 无效:**
  - **检测:** HTTP 401/403
  - **用户提示:** "API Key 无效,请检查设置"
  - **引导:** 跳转到设置页输入正确 Key
- **配额超限:**
  - **检测:** HTTP 429
  - **用户提示:** "API 调用次数已达上限,请稍后重试或切换到本地模式"
  - **降级:** 自动切换到本地翻译
- **网络超时:**
  - **检测:** Ktor 超时异常 (>10 秒)
  - **用户提示:** "网络请求超时,请检查网络连接"
  - **重试:** 提供 "重新翻译" 按钮

#### [E5] 覆盖层绘制错误
- **坐标越界:**
  - **检测:** 转换后坐标超出屏幕范围
  - **处理:** clamp 到 [0, screenWidth] 和 [0, screenHeight]
  - **日志:** WARN 级别记录原始值和修正值
- **文本过长无法显示:**
  - **检测:** `TextLayout` 计算高度 > boundingBox.height
  - **处理:** 降低字号或截断文本并添加 "..."
  - **日志:** INFO 级别记录框大小和文本长度

#### [E6] 权限错误
- **MediaProjection 过期:**
  - **检测:** `ScreenCaptureManager.captureScreen()` 返回 `PermissionDenied`
  - **用户提示:** "截图权限已过期,请重新授权"
  - **引导:** 跳转到设置页重新授权
- **悬浮窗权限被拒:**
  - **检测:** `WindowManager.addView()` 抛出异常
  - **用户提示:** "需要悬浮窗权限才能显示翻译结果"
  - **引导:** 打开系统设置页手动开启

#### [E7] 内存/存储错误
- **OOM (内存不足):**
  - **检测:** `OutOfMemoryError` 或 Bitmap 分配失败
  - **用户提示:** "内存不足,请关闭其他应用后重试"
  - **缓解:** 降低截图分辨率、释放未使用模型
- **存储空间不足:**
  - **检测:** 下载模型时 `IOException` (No space left on device)
  - **用户提示:** "存储空间不足,请清理后重试"
  - **建议:** 提供清理缓存按钮

#### [E8] 恢复机制
- **自动重试:**
  - 网络请求: 指数退避 (1s → 2s → 4s → 8s)
  - OCR 失败: 重试 1 次 (避免浪费资源)
- **状态保持:**
  - 翻译中断时保存截图到缓存
  - 用户重启应用后从缓存恢复
- **降级策略:**
  - 本地失败 → 尝试云端 (如果配置了)
  - 云端失败 → 尝试本地 (如果语言包可用)
  - **最终:** 显示友好的错误提示和操作建议

---

**下一步:** 进入 Phase 4 (Document Mapping),将结构化笔记映射到模板文档。
