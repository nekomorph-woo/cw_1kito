# API 文档: 本地优先 OCR 翻译系统

**文档版本:** 1.1
**最后更新:** 2026-02-13
**API 版本:** 1.1
**目标受众:** 内部开发者、第三方集成者

---

## 1. API 概述

### 1.1 目的

本文档定义本地优先 OCR 翻译系统的内部 API 接口,涵盖组件间通信协议、数据模型、错误处理机制。系统采用模块化设计,各组件通过 Kotlin 接口解耦,支持独立测试和替换。

### 1.2 目标受众

- **内部开发者:** 实现和维护系统组件的工程师
- **第三方集成者:** 将 OCR 或翻译功能集成到其他应用的开发者
- **测试工程师:** 编写单元测试和集成测试的 QA

### 1.3 API 风格

- **接口优先:** 所有公共 API 通过 Kotlin `interface` 定义,便于 Mock 和测试
- **协程支持:** 异步操作使用 `suspend` 函数或 `Flow<T>` 返回
- **不可变性:** 数据模型使用 `data class` 和 `val` 属性,确保线程安全
- **错误透明:** 使用密封类 (sealed class) 表示错误结果,类型安全

### 1.4 版本控制策略

- **语义化版本:** 主版本.次版本.补丁版本 (如 1.2.3)
- **向后兼容:** 次版本更新保持兼容,主版本更新可能破坏兼容性
- **弃用策略:** 通过 `@Deprecated` 注解标记,至少保留两个次版本

---

## 2. 认证

### 2.1 认证机制

| 机制 | 使用者 | 说明 |
|--------|--------|------|
| **无认证 (本地模式)** | MLKitOCRManager、MLKitTranslator | 本地 OCR 和翻译无需认证,直接调用 |
| **API Key (云端模式)** | SiliconFlowClient | 使用 API Key 验证身份,存储在 Android Keystore |
| **权限检查 (系统 API)** | ScreenCaptureManager、FloatingService | MediaProjection、悬浮窗等系统权限需用户手动授予 |

### 2.2 获取凭据

#### 云端 VLM API Key（硅基流动）

用于访问硅基流动的视觉语言模型（VLM）和普通大语言模型（LLM）。

```http
POST /v1/chat/completions HTTP/1.1
Host: api.siliconflow.cn
Authorization: Bearer <api_key>
Content-Type: application/json

{
  "model": "Qwen/Qwen2.5-VL-32B-Instruct",
  "messages": [...],
  "stream": false
}
```

#### 云端 LLM API（纯文本翻译）

使用普通 LLM 进行文本翻译，支持自定义提示词。

```http
POST /v1/chat/completions HTTP/1.1
Host: api.siliconflow.cn
Authorization: Bearer <api_key>
Content-Type: application/json

{
  "model": "Qwen/Qwen2.5-7B-Instruct",
  "messages": [
    {
      "role": "user",
      "content": "请将以下日文翻译成中文：こんにちは"
    }
  ],
  "stream": false
}
```

```kotlin
class ApiKeyManager(private val context: Context) {
    private val keystore = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    fun saveApiKey(apiKey: String) {
        val encryptedKey = keystore.encryptString(apiKey)
        preferences.edit().putString("api_key", encryptedKey).apply()
    }

    fun getApiKey(): String? {
        val encryptedKey = preferences.getString("api_key", null) ?: return null
        return keystore.decryptString(encryptedKey)
    }

    fun hasApiKey(): Boolean = getApiKey() != null
}
```

**存储位置:** `Android Keystore` + `EncryptedSharedPreferences`
**加密算法:** AES256-GCM (AndroidX Security 库)

### 2.3 使用凭据

```http
POST /v1/chat/completions HTTP/1.1
Host: api.siliconflow.cn
Authorization: Bearer <api_key>
Content-Type: application/json

{
  "model": "Qwen/Qwen2.5-7B-Instruct",
  "messages": [...],
  "stream": false
}
```

### 2.4 Token 管理

| 操作 | 端点 | 描述 |
|--------|--------|------|
| **保存** | `ApiKeyManager.saveApiKey()` | 加密存储 API Key 到 Keystore |
| **读取** | `ApiKeyManager.getApiKey()` | 解密并返回 API Key |
| **删除** | `ApiKeyManager.clearApiKey()` | 移除 API Key,切换到本地模式 |
| **验证** | `SiliconFlowClient.validateApiKey()` | 调用测试端点验证 Key 有效性 |

---

## 3. 通用行为

### 3.1 请求格式

**OCR 引擎请求:**
```kotlin
val bitmap: Bitmap  // ARGB_8888 格式
val request = OcrRequest(
    image = bitmap,
    preprocessing = OcrPreprocessing(
        resize = true,
        targetShortEdge = 672  // 像素
    )
)
```

**翻译引擎请求:**
```kotlin
val request = TranslationRequest(
    text = "合并后的日文文本",
    sourceLanguage = Language.JAPANESE,
    targetLanguage = Language.CHINESE,
    options = TranslationOptions(
        timeout = 5000  // 毫秒
    )
)
```

### 3.2 响应格式

**成功响应 (OCR):**
```kotlin
sealed class OcrResult {
    data class Success(
        val results: List<OcrDetection>
    ) : OcrResult()

    data class Error(
        val exception: Throwable
    ) : OcrResult()
}

data class OcrDetection(
    val text: String,
    val boundingBox: RectF,      // 像素坐标
    val confidence: Float,
    val angle: Float? = null
)
```

**成功响应 (翻译):**
```kotlin
sealed class TranslationResult {
    data class Success(
        val translatedText: String,
        val latency: Long  // 毫秒
    ) : TranslationResult()

    data class Error(
        val code: ErrorCode,
        val message: String
    ) : TranslationResult()
}
```

### 3.3 错误处理

详见 [第 7 节: 错误码](#7-错误码)

### 3.4 速率限制

| 指标 | 限制 | 时间窗口 |
|--------|-------|----------|
| **OCR 并发请求数** | 3 (可配置) | 无限制 (本地模式) |
| **翻译并发请求数** | 5 (可配置) | 无限制 (本地模式) |
| **云端 API 调用** | 根据硅基流动套餐 | 每月 / 每日 |

**本地模式:** 无速率限制 (仅受设备性能约束)

### 3.5 分页

不适用 (本地处理单张截图,无分页概念)

### 3.6 过滤与排序

```kotlin
data class TranslationListOptions(
    val sortBy: SortField = SortField.CONFIDENCE,
    val sortOrder: SortOrder = SortOrder.DESCENDING,
    val minConfidence: Float? = null,
    val language: Language? = null
)

// 示例: 仅显示置信度 >0.8 的日文翻译结果
val options = TranslationListOptions(minConfidence = 0.8f)
```

---

## 4. API 端点

### 4.1 OCR 引擎

#### 初始化 OCR 引擎

```kotlin
suspend fun initialize(): Boolean
```

**描述:** 初始化 Google ML Kit Text Recognition v2 识别器

**返回:** `true` 表示成功,`false` 表示失败

**错误处理:**
- Google Play Services 不可用 → 返回 `false`
- ML Kit 初始化失败 → 抛出 `OcrInitializationException`

---

#### 执行 OCR 识别

```kotlin
suspend fun recognize(bitmap: Bitmap): List<OcrDetection>
```

**描述:** 对给定 Bitmap 执行文字识别,返回所有检测框

**请求参数:**

| 参数 | 类型 | 必需 | 描述 |
|--------|------|--------|------|
| bitmap | Bitmap | 是 | ARGB_8888 格式图像,建议短边 672-896px |

**响应:** `List<OcrDetection>`

**错误处理:**
- `OcrRuntimeException`: ML Kit 识别失败
- `OutOfMemoryError`: 图片过大或内存不足
- `IllegalArgumentException`: Bitmap 格式不正确

---

#### 释放 OCR 资源

```kotlin
fun release()
```

**描述:** 关闭 ML Kit Text Recognizer,释放资源

**调用时机:** 应用退出、内存不足、切换到云端模式

---

### 4.2 文本合并引擎

#### 合并文本框

```kotlin
fun merge(
    boxes: List<TextBox>,
    config: MergingConfig = MergingConfig.DEFAULT
): List<MergedText>
```

**描述:** 将 OCR 检测的多个小框智能合并为逻辑行

**请求参数:**

| 参数 | 类型 | 必需 | 描述 |
|--------|------|--------|------|
| boxes | List<TextBox> | 是 | OCR 原始检测框列表 |
| config | MergingConfig | 否 | 合并配置 (阈值、方向检测开关) |

**MergingConfig 属性:**

| 字段 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| yTolerance | Float | 0.4f | Y 轴聚类容差 (相对于平均高度) |
| xToleranceFactor | Float | 1.5f | X 轴合并容差倍数 (相对于平均字符宽) |
| enableDirectionDetection | Boolean | true | 是否启用横竖排方向检测 |

**响应:** `List<MergedText>`

**示例:**
```kotlin
val boxes: List<TextBox> = ocrEngine.recognize(bitmap)
val merged: List<MergedText> = merger.merge(boxes, MergingConfig(
    yTolerance = 0.5f,      // 更宽松的 Y 轴容差
    xToleranceFactor = 2.0f   // 更宽松的 X 轴容差
    enableDirectionDetection = true
))
```

---

### 4.3 翻译管理器

#### 翻译文本 (本地或云端)

```kotlin
suspend fun translate(
    text: String,
    sourceLang: Language,
    targetLang: Language,
    mode: TranslationMode
): String
```

**描述:** 将源语言文本翻译为目标语言，根据模式选择引擎

**请求参数:**

| 参数 | 类型 | 必需 | 描述 |
|--------|------|--------|------|
| text | String | 是 | 待翻译文本 (建议长度 <5000 字符) |
| sourceLang | Language | 是 | 源语言 (JAPANESE/ENGLISH/KOREAN) |
| targetLang | Language | 是 | 目标语言 |
| mode | TranslationMode | 是 | 翻译模式 (LOCAL_MLKIT/CLOUD_LLM) |

**响应:** `String` (翻译后文本)

**TranslationMode 枚举:**
```kotlin
enum class TranslationMode {
    LOCAL_MLKIT,   // 本地 Google ML Kit 翻译
    CLOUD_LLM      // 云端 LLM 翻译 (硅基流动)
}
```

---

### 4.4 云端 LLM 翻译引擎

#### 使用自定义提示词翻译

```kotlin
suspend fun translateWithPrompt(
    text: String,
    customPrompt: String?,
    modelId: String
): String
```

**描述:** 使用云端 LLM 和自定义提示词进行翻译

**请求参数:**

| 参数 | 类型 | 必需 | 描述 |
|--------|------|--------|------|
| text | String | 是 | 待翻译文本 |
| customPrompt | String? | 否 | 自定义提示词（使用 {text} 占位符） |
| modelId | String | 是 | 模型 ID（如 Qwen/Qwen2.5-7B-Instruct） |

**默认提示词模板:**
```
You are a professional expert in localizing Japanese/Korean comics and Japanese games. Please translate the following ${sourceLang.displayName} text into ${targetLang.displayName}.
Return ONLY the translation result.
NO explanation.NO clarification.

Original text:
{text}
```

**响应:** `String` (翻译后文本)

**支持的模型列表:**

#### 普通模型（直接使用）
| 模型 ID | 显示名称 | 描述 |
|---------|----------|------|
| Pro/deepseek-ai/DeepSeek-V3 | DeepSeek-V3 Pro | 高质量通用模型 |
| Kwaipilot/KAT-Dev | KAT-Dev | 快乐开发模型 |
| zai-org/GLM-4.5-Air | GLM-4.5-Air | 轻量级 GLM 模型 |
| Qwen/Qwen2.5-72B-Instruct | Qwen2.5-72B | 72B 参数，最高质量 |
| Qwen/Qwen2.5-32B-Instruct | Qwen2.5-32B | 32B 参数，高质量 |
| Qwen/Qwen2.5-14B-Instruct | Qwen2.5-14B | 14B 参数，平衡选择 |
| Qwen/Qwen2.5-7B-Instruct | Qwen2.5-7B | 7B 参数，快速响应 |
| Pro/THUDM/glm-4-9b-chat | GLM-4-9B Pro | 9B 参数 GLM |
| Pro/Qwen/Qwen2.5-7B-Instruct | Qwen2.5-7B Pro | 7B 参数 Pro 版本 |

#### Thinking 模型（需要 enable_thinking=false）
| 模型 ID | 显示名称 | 特殊参数 |
|---------|----------|----------|
| Pro/zai-org/GLM-4.7 | GLM-4.7 Pro | `enable_thinking=false` |
| deepseek-ai/DeepSeek-V3.2 | DeepSeek-V3.2 | `enable_thinking=false` |
| Pro/deepseek-ai/DeepSeek-V3.2 | DeepSeek-V3.2 Pro | `enable_thinking=false` |
| zai-org/GLM-4.6 | GLM-4.6 | `enable_thinking=false` |
| Qwen/Qwen3-8B | Qwen3-8B | `enable_thinking=false` |
| Qwen/Qwen3-14B | Qwen3-14B | `enable_thinking=false` |
| Qwen/Qwen3-32B | Qwen3-32B | `enable_thinking=false` |
| Qwen/Qwen3-30B-A3B | Qwen3-30B-A3B | `enable_thinking=false` |
| tencent/Hunyuan-A13B-Instruct | Hunyuan-A13B | `enable_thinking=false` |
| Pro/deepseek-ai/DeepSeek-V3.1-Terminus | DeepSeek-V3.1-Terminus Pro | `enable_thinking=false` |

**默认模型:** `Qwen/Qwen2.5-7B-Instruct`

---

### 4.5 配置管理器

#### 获取本地 OCR 开关状态

```kotlin
fun isLocalOcrEnabled(): Boolean
```

**描述:** 返回实验室中本地 OCR 开关的状态

**响应:** `Boolean` (true = 开启, false = 关闭)

---

#### 设置本地 OCR 开关状态

```kotlin
fun setLocalOcrEnabled(enabled: Boolean)
```

**描述:** 更新本地 OCR 开关状态并持久化

---

#### 获取当前翻译方案

```kotlin
fun getTranslationScheme(): TranslationScheme
```

**描述:** 返回当前选择的翻译方案

**响应:**
```kotlin
enum class TranslationScheme {
    VLM_CLOUD,   // VLM 云端方案（默认）
    LOCAL_OCR    // 本地 OCR 方案
}
```

---

#### 设置翻译方案

```kotlin
fun setTranslationScheme(scheme: TranslationScheme)
```

**描述:** 更新翻译方案选择并持久化

---

#### 获取翻译模式

```kotlin
fun getTranslationMode(): TranslationMode
```

**描述:** 返回本地 OCR 方案下的翻译模式（ML Kit 或 云端 LLM）

---

#### 设置翻译模式

```kotlin
fun setTranslationMode(mode: TranslationMode)
```

**描述:** 更新翻译模式并持久化

---

#### 获取云端 LLM 配置

```kotlin
fun getCloudLlmConfig(): CloudLlmConfig?
```

**描述:** 返回云端 LLM 翻译的配置（模型 ID、提示词）

**响应:**
```kotlin
data class CloudLlmConfig(
    val modelId: String,
    val customPrompt: String
)
```

---

#### 设置云端 LLM 配置

```kotlin
fun setCloudLlmConfig(config: CloudLlmConfig)
```

**描述:** 更新云端 LLM 配置并持久化

---

#### 重置提示词为默认值

```kotlin
fun resetCloudLlmPrompt()
```

**描述:** 将云端 LLM 提示词重置为默认值

---

### 4.6 覆盖层渲染器

```kotlin
suspend fun translate(
    text: String,
    sourceLang: Language,
    targetLang: Language
): String
```

**描述:** 将源语言文本翻译为目标语言,自动选择本地或云端引擎

**请求参数:**

| 参数 | 类型 | 必需 | 描述 |
|--------|------|--------|------|
| text | String | 是 | 待翻译文本 (建议长度 <5000 字符) |
| sourceLang | Language | 是 | 源语言 (JAPANESE/ENGLISH/KOREAN) |
| targetLang | Language | 是 | 目标语言 |

**响应:** `String` (翻译后文本)

**错误处理:**
- `TranslationException`: 本地或云端翻译失败
- `NetworkException`: 云端 API 网络错误
- `RateLimitException`: 云端 API 配额超限

**模式切换:**
```kotlin
enum class TranslationMode {
    LOCAL,      // 仅使用 Google ML Kit
    REMOTE,     // 仅使用硅基流动 API
    HYBRID      // 优先本地,失败时降级到云端
}
```

---

### 4.4 覆盖层渲染器

#### 添加翻译结果

```kotlin
fun addResult(result: TranslationResult)
```

**描述:** 增量添加翻译结果到覆盖层,触发重绘

**请求参数:**

| 参数 | 类型 | 必需 | 描述 |
|--------|------|--------|------|
| result | TranslationResult | 是 | 包含翻译文本和归一化坐标 |

**响应:** 无 (void)

**副作用:** 触发 `invalidate()` 导致 `onDraw()` 重绘

---

#### 清空所有结果

```kotlin
fun clear()
```

**描述:** 移除所有翻译结果,清空覆盖层

**调用时机:** 用户关闭覆盖层、开始新翻译会话

---

### 4.5 屏幕捕获管理器

#### 请求截图权限

```kotlin
fun requestPermission(activity: Activity, requestCode: Int)
```

**描述:** 引导用户授予 MediaProjection 权限

**流程:**
1. 创建 `MediaProjectionManager.createScreenCaptureIntent()`
2. 调用 `activity.startActivityForResult()`
3. 在 `onActivityResult()` 中接收结果

---

#### 执行截图

```kotlin
suspend fun captureScreen(): CaptureResult
```

**描述:** 捕获当前屏幕内容为 Bitmap

**响应类型:**
```kotlin
sealed class CaptureResult {
    data class Success(val imageBytes: ByteArray) : CaptureResult()
    object PermissionDenied : CaptureResult()
    data class Error(val exception: Throwable) : CaptureResult()
}
```

**错误处理:**
- `PermissionDenied`: MediaProjection 权限未授予或已过期
- `SecurityException`: 系统安全限制 (如屏幕录制被禁用)

---

## 5. 数据模型

### 5.1 TextBox (OCR 检测框)

**描述:** OCR 引擎返回的单个文字检测框

**字段:**

| 字段 | 类型 | 必需 | 描述 | 验证规则 |
|------|------|--------|------|----------|
| text | String | 是 | 识别出的文字内容 | 非空,长度 1-100 |
| left | Float | 是 | 左边界 (像素坐标) | ≥0, <right |
| top | Float | 是 | 上边界 (像素坐标) | ≥0, <bottom |
| right | Float | 是 | 右边界 (像素坐标) | >left, ≤imageWidth |
| bottom | Float | 是 | 下边界 (像素坐标) | >top, ≤imageHeight |
| confidence | Float | 是 | 置信度 (0-1) | 0-1 范围 |
| angle | Float? | 否 | 旋转角度 (0°/90°/180°/270°) | 0-360 范围 |

**示例:**
```kotlin
val box = TextBox(
    text = "こんにちは",
    left = 120.5f,
    top = 340.2f,
    right = 450.8f,
    bottom = 380.1f,
    confidence = 0.95f,
    angle = 0f  // 横排
)
```

---

### 5.2 BoundingBox (归一化坐标)

**描述:** 与屏幕尺寸无关的相对坐标 (0-1 范围)

**字段:**

| 字段 | 类型 | 必需 | 描述 | 验证规则 |
|------|------|--------|------|----------|
| left | Float | 是 | 左边界 (0-1) | ≥0, <right |
| top | Float | 是 | 上边界 (0-1) | ≥0, <bottom |
| right | Float | 是 | 右边界 (0-1) | >left, ≤1 |
| bottom | Float | 是 | 下边界 (0-1) | >top, ≤1 |

**转换为屏幕像素:**
```kotlin
fun toScreenRect(screenWidth: Int, screenHeight: Int): Rect {
    return Rect(
        (left * screenWidth).toInt(),
        (top * screenHeight).toInt(),
        (right * screenWidth).toInt(),
        (bottom * screenHeight).toInt()
    )
}
```

**从像素坐标转换:**
```kotlin
fun fromPixelRect(rect: Rect, screenWidth: Int, screenHeight: Int): BoundingBox {
    return BoundingBox(
        left = rect.left.toFloat() / screenWidth,
        top = rect.top.toFloat() / screenHeight,
        right = rect.right.toFloat() / screenWidth,
        bottom = rect.bottom.toFloat() / screenHeight
    )
}
```

---

### 5.3 MergedText (合并后文本)

**描述:** 文本合并引擎输出的逻辑行

**字段:**

| 字段 | 类型 | 必需 | 描述 |
|------|------|--------|------|
| text | String | 是 | 合并后的完整文本 (含 "\n" 分行符) |
| boundingBox | RectF | 是 | 合并后外接矩形 (像素坐标) |
| direction | TextDirection | 是 | 横排/竖排/混合 |
| originalBoxCount | Int | 是 | 合并前的框数量 (用于调试) |

**TextDirection 枚举:**
```kotlin
enum class TextDirection {
    HORIZONTAL,  // 横排 (从左到右)
    VERTICAL,    // 竖排 (从上到下)
    MIXED        // 混合 (同一行包含不同方向)
}
```

---

### 5.4 TranslationRequest (翻译请求)

**描述:** 翻译引擎的输入参数

**字段:**

| 字段 | 类型 | 必需 | 描述 |
|------|------|--------|------|
| text | String | 是 | 待翻译文本 |
| sourceLanguage | Language | 是 | 源语言代码 |
| targetLanguage | Language | 是 | 目标语言代码 |
| options | TranslationOptions? | 否 | 可选参数 (超时、格式等) |

**Language 枚举:**
```kotlin
enum class Language(val code: String) {
    JAPANESE("ja"),
    ENGLISH("en"),
    CHINESE("zh"),
    KOREAN("ko");

    companion object {
        fun fromCode(code: String): Language? =
            values.find { it.code == code }
    }
}
```

---

### 5.5 AppConfig (应用配置)

**描述:** 持久化的应用配置

**字段:**

| 字段 | 类型 | 必需 | 描述 |
|------|------|--------|------|
| enableLocalOcr | Boolean | 是 | 实验室本地 OCR 开关 |
| translationScheme | TranslationScheme | 是 | 翻译方案（VLM 云端 / 本地 OCR） |
| translationMode | TranslationMode | 是 | 本地 OCR 方案下的翻译模式 |
| cloudLlmConfig | CloudLlmConfig? | 否 | 云端 LLM 配置 |
| mergingConfig | MergingConfig | 否 | 文本合并配置 |
| performanceMode | PerformanceMode | 是 | 性能模式 (FAST/BALANCED/QUALITY) |
| apiKey | String? | 否 | 云端 API Key (加密存储) |

**TranslationScheme 枚举:**
```kotlin
enum class TranslationScheme {
    VLM_CLOUD,   // VLM 云端方案（默认）
    LOCAL_OCR    // 本地 OCR 方案
}
```

**TranslationMode 枚举:**
```kotlin
enum class TranslationMode {
    LOCAL_MLKIT,  // 本地 ML Kit 翻译
    CLOUD_LLM     // 云端 LLM 翻译
}
```

**CloudLlmConfig 数据类:**
```kotlin
data class CloudLlmConfig(
    val modelId: String = LlmModel.default().id,
    val customPrompt: String = "",
    val enableThinking: Boolean = false  // 对于 thinking 模型需要设为 false
)
```

**LlmModel 枚举:**
```kotlin
enum class LlmModel(
    val id: String,
    val displayName: String,
    val requiresDisableThinking: Boolean = false
) {
    // 普通模型
    DEEPSEEK_V3_PRO("Pro/deepseek-ai/DeepSeek-V3", "DeepSeek-V3 Pro"),
    KAT_DEV("Kwaipilot/KAT-Dev", "KAT-Dev"),
    GLM_45_AIR("zai-org/GLM-4.5-Air", "GLM-4.5-Air"),
    QWEN_25_72B("Qwen/Qwen2.5-72B-Instruct", "Qwen2.5-72B"),
    QWEN_25_32B("Qwen/Qwen2.5-32B-Instruct", "Qwen2.5-32B"),
    QWEN_25_14B("Qwen/Qwen2.5-14B-Instruct", "Qwen2.5-14B"),
    QWEN_25_7B("Qwen/Qwen2.5-7B-Instruct", "Qwen2.5-7B"),
    GLM_4_9B_PRO("Pro/THUDM/glm-4-9b-chat", "GLM-4-9B Pro"),
    QWEN_25_7B_PRO("Pro/Qwen/Qwen2.5-7B-Instruct", "Qwen2.5-7B Pro"),

    // Thinking 模型（需要 enable_thinking=false）
    GLM_47_PRO("Pro/zai-org/GLM-4.7", "GLM-4.7 Pro", true),
    DEEPSEEK_V32("deepseek-ai/DeepSeek-V3.2", "DeepSeek-V3.2", true),
    DEEPSEEK_V32_PRO("Pro/deepseek-ai/DeepSeek-V3.2", "DeepSeek-V3.2 Pro", true),
    GLM_46("zai-org/GLM-4.6", "GLM-4.6", true),
    QWEN3_8B("Qwen/Qwen3-8B", "Qwen3-8B", true),
    QWEN3_14B("Qwen/Qwen3-14B", "Qwen3-14B", true),
    QWEN3_32B("Qwen/Qwen3-32B", "Qwen3-32B", true),
    QWEN3_30B_A3B("Qwen/Qwen3-30B-A3B", "Qwen3-30B-A3B", true),
    HUNYUAN_A13B("tencent/Hunyuan-A13B-Instruct", "Hunyuan-A13B", true),
    DEEPSEEK_V31_TERMINUS_PRO("Pro/deepseek-ai/DeepSeek-V3.1-Terminus", "DeepSeek-V3.1-Terminus Pro", true);

    companion object {
        fun default(): LlmModel = QWEN_25_7B

        fun fromId(id: String): LlmModel? =
            entries.find { it.id == id }

        // 获取所有普通模型
        val normalModels: List<LlmModel>
            get() = entries.filter { !it.requiresDisableThinking }

        // 获取所有 thinking 模型
        val thinkingModels: List<LlmModel>
            get() = entries.filter { it.requiresDisableThinking }
    }
}
```

**默认翻译提示词:**
```kotlin
fun getDefaultTranslationPrompt(sourceLang: Language, targetLang: Language): String {
    return """
You are a professional expert in localizing Japanese/Korean comics and Japanese games. Please translate the following ${sourceLang.displayName} text into ${targetLang.displayName}.
Return ONLY the translation result.
NO explanation.NO clarification.

Original text:
{text}
    """.trimIndent()
}

// 占位符版本（用于存储和编辑）
const val DEFAULT_PROMPT_TEMPLATE = """
You are a professional expert in localizing Japanese/Korean comics and Japanese games. Please translate the following \${sourceLang.displayName} text into \${targetLang.displayName}.
Return ONLY the translation result.
NO explanation.NO clarification.

Original text:
{text}
""".trimIndent()
```

**PerformanceMode 枚举:**
```kotlin
enum class PerformanceMode {
    FAST,        // 极速 (短边 672px,本地模型)
    BALANCED,    // 平衡 (短边 896px,混合模型)
    QUALITY      // 高精 (短边 1080px,云端 VL 32B)
}
```

---

### 5.6 ChatCompletionRequest (云端 LLM 请求)

**描述:** 云端 LLM 翻译请求格式

**字段:**

| 字段 | 类型 | 必需 | 描述 |
|------|------|--------|------|
| model | String | 是 | 模型 ID |
| messages | List<ChatMessage> | 是 | 对话消息列表 |
| stream | Boolean | 否 | 是否流式响应（默认 false） |
| temperature | Float | 否 | 温度参数（默认 0.7） |
| maxTokens | Int | 否 | 最大输出 token 数 |

**ChatMessage 数据类:**
```kotlin
data class ChatMessage(
    val role: String,      // "system" | "user" | "assistant"
    val content: String    // 消息内容
)
```

**示例请求:**
```json
{
  "model": "Qwen/Qwen2.5-7B-Instruct",
  "messages": [
    {
      "role": "user",
      "content": "请将以下日文翻译成中文：\n\nこんにちは、世界！"
    }
  ],
  "stream": false,
  "temperature": 0.7
}
```

---

### 4.7 语言包下载管理器

#### 获取所有语言包状态

```kotlin
fun getLanguagePackStates(): Flow<List<LanguagePackState>>
```

**描述:** 返回所有支持语言对的下载状态

**响应:** `Flow<List<LanguagePackState>>`

**LanguagePackState 数据结构:**
```kotlin
data class LanguagePackState(
    val sourceLang: Language,
    val targetLang: Language,
    val status: DownloadStatus,
    val sizeBytes: Long = 0,
    val progress: Float = 0f
)
```

---

#### 下载所有常用语言对

```kotlin
suspend fun downloadAllCommonPairs(
    requireWifi: Boolean = true,
    onProgress: (Int, Int) -> Unit
): DownloadAllResult
```

**描述:** 后台下载中英日韩 12 个语言对

**请求参数:**

| 参数 | 类型 | 必需 | 描述 |
|--------|------|--------|------|
| requireWifi | Boolean | 否 | 是否要求 Wi-Fi 环境（默认 true） |
| onProgress | (Int, Int) -> Unit | 否 | 进度回调（已下载数, 总数） |

**响应:**
```kotlin
data class DownloadAllResult(
    val successCount: Int,    // 成功下载数量
    val failedCount: Int,     // 失败数量
    val skippedCount: Int,    // 跳过数量（已存在）
    val totalBytes: Long      // 总下载大小（字节）
)
```

---

#### 下载单个语言对

```kotlin
suspend fun downloadLanguagePair(
    sourceLang: Language,
    targetLang: Language,
    requireWifi: Boolean = true
): DownloadResult
```

**描述:** 下载指定语言对的翻译模型

---

#### 检查是否需要下载

```kotlin
suspend fun needsDownload(): Boolean
```

**描述:** 检查是否有语言包需要下载

---

#### 获取存储占用

```kotlin
fun getTotalStorageUsed(): Flow<Long>
```

**描述:** 获取已下载语言包的总存储占用（字节）

---

#### 删除语言包

```kotlin
suspend fun deleteLanguagePair(
    sourceLang: Language,
    targetLang: Language
): Boolean
```

**描述:** 删除指定语言对的翻译模型，释放存储空间

---

### 4.8 批量翻译管理器

#### 批量翻译文本

```kotlin
suspend fun translateBatch(
    texts: List<String>,
    sourceLang: Language,
    targetLang: Language,
    mode: TranslationMode,
    onBatchComplete: ((batchIndex: Int, totalBatches: Int) -> Unit)? = null
): List<String>
```

**描述:** 批量翻译多个文本，每批 3 个并发请求，自动分批处理

**请求参数:**

| 参数 | 类型 | 必需 | 描述 |
|--------|------|--------|------|
| texts | List<String> | 是 | 待翻译文本列表 |
| sourceLang | Language | 是 | 源语言 |
| targetLang | Language | 是 | 目标语言 |
| mode | TranslationMode | 是 | 翻译模式 |
| onBatchComplete | ((Int, Int) -> Unit)? | 否 | 批次完成回调 |

**响应:** `List<String>` - 翻译结果列表（保持原顺序）

**并发处理逻辑:**
```
输入: 7 个文本
处理:
  - Batch 1: 并发请求 texts[0-2] → awaitAll() → 完成回调
  - Batch 2: 并发请求 texts[3-5] → awaitAll() → 完成回调
  - Batch 3: 并发请求 texts[6]   → awaitAll() → 完成回调
输出: 7 个翻译结果（保持顺序）

本地翻译: 3 个 ML Kit 翻译同时执行
云端翻译: 3 个 LLM API 请求同时发送
```

**示例:**
```kotlin
val texts = listOf("こんにちは", "ありがとう", "さようなら", "おはよう")
val results = batchTranslationManager.translateBatch(
    texts = texts,
    sourceLang = Language.JA,
    targetLang = Language.ZH,
    mode = TranslationMode.LOCAL_MLKIT,
    onBatchComplete = { batch, total ->
        println("批次进度: $batch / $total")
    }
)
// 结果: ["你好", "谢谢", "再见", "早上好"]
```

---

### 4.9 API Key 配置管理

#### 保存 API Key

```kotlin
suspend fun saveApiKey(apiKey: String)
```

**描述:** 保存硅基流动 API Key 到安全存储

---

#### 获取 API Key

```kotlin
fun getApiKey(): String?
```

**描述:** 获取已保存的 API Key（加密存储）

---

#### 检查 API Key 是否已配置

```kotlin
fun hasApiKey(): Boolean
```

**描述:** 检查是否已配置 API Key

---

#### 验证 API Key

```kotlin
suspend fun validateApiKey(apiKey: String): Boolean
```

**描述:** 验证 API Key 是否有效

---

### 4.10 悬浮窗校验规则

#### 校验悬浮窗权限

```kotlin
suspend fun checkFloatingWindowRequirements(): FloatingWindowCheckResult
```

**描述:** 检查打开悬浮窗所需的权限（不包含 API Key 和语言包校验）

**校验内容:**
- ✅ 悬浮窗权限 (SYSTEM_ALERT_WINDOW)
- ✅ 通知权限 (POST_NOTIFICATIONS) - Android 13+
- ✅ 截图权限 (MediaProjection)

**不校验:**
- ❌ API Key 配置状态
- ❌ 语言包下载状态

**响应:**
```kotlin
sealed class FloatingWindowCheckResult {
    object AllGranted : FloatingWindowCheckResult()
    data class MissingPermissions(val permissions: List<String>) : FloatingWindowCheckResult()
}
```

---

## 6. Webhooks (不适用)

本地优先架构不涉及 Webhook 回调。

---

## 7. 错误码

### 7.1 HTTP 状态码

| 码 | 含义 | 使用场景 |
|------|--------|----------|
| 200 | OK | 翻译成功 |
| 201 | Created | 新资源创建成功 |
| 204 | No Content | 删除成功 |
| 400 | Bad Request | 无效参数 (云端 API) |
| 401 | Unauthorized | API Key 无效或过期 |
| 403 | Forbidden | 权限不足 (云端 API) |
| 404 | Not Found | 资源不存在 (模型文件) |
| 409 | Conflict | 配置冲突 |
| 422 | Unprocessable Entity | 参数格式错误 |
| 429 | Too Many Requests | API 配额超限 (云端) |
| 500 | Internal Server Error | 云端服务错误 |
| 503 | Service Unavailable | 云端服务不可用 |

### 7.2 错误响应格式

```kotlin
sealed class AppError {
    data class OcrError(
        val code: OcrErrorCode,
        val message: String,
        val details: Map<String, Any>? = null
    ) : AppError()

    data class TranslationError(
        val code: TranslationErrorCode,
        val message: String,
        val details: Map<String, Any>? = null
    ) : AppError()

    data class SystemError(
        val code: SystemErrorCode,
        val message: String,
        val details: Map<String, Any>? = null
    ) : AppError()
}
```

### 7.3 常见错误码

#### OCR 错误码

| 码 | 消息 | 描述 |
|------|--------|------|
| `OCR_INIT_FAILED` | OCR 引擎初始化失败 | Google Play Services 不可用或 ML Kit 初始化错误 |
| `OCR_RUNTIME_ERROR` | OCR 推理运行时错误 | ML Kit 识别失败或系统错误 |
| `OCR_INVALID_IMAGE` | 无效图像输入 | Bitmap 格式不正确或尺寸异常 |
| `OCR_NOT_INITIALIZED` | OCR 引擎未初始化 | 未调用 initialize() 前执行 recognize() |

#### 翻译错误码

| 码 | 消息 | 描述 |
|------|--------|------|
| `TRANSLATION_INIT_FAILED` | 翻译引擎初始化失败 | 语言包下载失败或 ML Kit 初始化错误 |
| `TRANSLATION_TIMEOUT` | 翻译超时 | 超过指定时间未完成 |
| `TRANSLATION_API_KEY_INVALID` | API Key 无效 | 云端 API 认证失败 |
| `TRANSLATION_RATE_LIMIT_EXCEEDED` | API 配额超限 | 云端 API 请求超限 |
| `TRANSLATION_NETWORK_ERROR` | 网络错误 | 云端 API 连接失败 |
| `TRANSLATION_UNSUPPORTED_LANGUAGE` | 不支持的语言对 | 语言包未下载或云端 API 不支持 |

#### 系统错误码

| 码 | 消息 | 描述 |
|------|--------|------|
| `PERMISSION_DENIED` | 权限被拒 | MediaProjection 或悬浮窗权限未授予 |
| `PERMISSION_EXPIRED` | 权限过期 | MediaProjection token 失效 |
| `STORAGE_INSUFFICIENT` | 存储空间不足 | 无法下载语言包 |
| `OUT_OF_MEMORY` | 内存不足 | Bitmap 分配失败或系统内存不足 |
| `UNKNOWN_ERROR` | 未知错误 | 未分类的系统错误 |

---

## 8. SDK 与库

### 8.1 官方 SDK

| 语言 | 库 | 版本 | 仓库 |
|--------|------|------|--------|
| Kotlin | Kotlinx Coroutines | 1.8.0 | [GitHub](https://github.com/Kotlin/kotlinx.coroutines) |
| UI | Compose Multiplatform | 1.10.0 | [GitHub](https://github.com/JetBrains/compose-multiplatform) |
| UI | Material3 | 1.10.0-alpha05 | [GitHub](https://github.com/androidx/compose-material3) |
| OCR | Google ML Kit Text Recognition | 19.0.0 | [Google Developers](https://developers.google.com/ml-kit) |
| 翻译 | Google ML Kit Translation | 19.0.0 | [Google Developers](https://developers.google.com/ml-kit) |
| 网络 | Ktor Client | 2.3.8 | [GitHub](https://github.com/ktorio/ktor) |
| 日志 | Timber | 5.0.1 | [GitHub](https://github.com/JakeWharton/Timber) |
| 权限 | Accompanist Permissions | 0.36.0 | [GitHub](https://github.com/google/accompanist) |

---

## 9. 指南

### 9.1 快速开始

#### 集成 OCR 引擎

```kotlin
// 1. 初始化
val ocrEngine = MLKitOCRManager(context)
if (!ocrEngine.initialize()) {
    Log.e("OCR", "初始化失败")
    return
}

// 2. 执行识别
val bitmap = captureScreen()
val detections = ocrEngine.recognize(bitmap)

// 3. 处理结果
detections.forEach { detection ->
    if (detection.confidence > 0.8f) {
        println("识别到: ${detection.text}")
    }
}

// 4. 释放资源
ocrEngine.release()
```

#### 集成翻译引擎

```kotlin
// 1. 初始化本地翻译
val translator = MLKitTranslator(context)
translator.downloadModelIfNeeded(Language.JAPANESE, Language.CHINESE)
    .addOnSuccessListener {
        println("模型下载完成")
    }
    .addOnFailureListener { e ->
        Log.e("Translation", "模型下载失败", e)
    }

// 2. 执行翻译
lifecycleScope.launch {
    val translated = translator.translate(
        text = "こんにちは",
        sourceLang = Language.JAPANESE,
        targetLang = Language.CHINESE
    )
    println("翻译结果: $translated")
}
```

### 9.2 认证指南

#### 存储 API Key

```kotlin
// 使用 Android Keystore 加密存储
val keyManager = ApiKeyManager(context)
keyManager.saveApiKey("sk-xxxxxxxxxxxx")

// 使用 API Key
if (keyManager.hasApiKey()) {
    val apiKey = keyManager.getApiKey()
    apiClient.setApiKey(apiKey)
}
```

#### 权限请求

```kotlin
// 在 Activity 中请求 MediaProjection 权限
val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
val intent = projectionManager.createScreenCaptureIntent()
startActivityForResult(intent, SCREEN_CAPTURE_REQUEST_CODE)

// 在 onActivityResult 中处理
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
        ScreenCaptureManager.setMediaProjection(resultCode, data)
    }
}
```

### 9.3 常见用例

#### 用例 1: 完整翻译流程

```kotlin
class TranslationService : Service() {
    suspend fun translateScreen() {
        // 1. 截图
        val captureResult = ScreenCaptureManager.captureScreen()
        if (captureResult !is CaptureResult.Success) return

        // 2. OCR 识别
        val ocrResults = ocrEngine.recognize(captureResult.bitmap)

        // 3. 文本合并
        val mergedTexts = merger.merge(ocrResults)

        // 4. 翻译 (并发)
        val translations = mergedTexts.map { merged ->
            async {
                translator.translate(merged.text, Language.JAPANESE, Language.CHINESE)
            }
        }.awaitAll()

        // 5. 显示覆盖层
        translations.forEachIndexed { index, translated ->
            val result = TranslationResult(
                    originalText = mergedTexts[index].text,
                    translatedText = translated,
                    boundingBox = BoundingBox.fromPixelRect(mergedTexts[index].boundingBox)
            )
            overlayRenderer.addResult(result)
        }
    }
}
```

#### 用例 2: 混合模式 (本地 + 云端降级)

```kotlin
class HybridTranslationManager {
    suspend fun translateWithFallback(text: String): String {
        return try {
            // 优先尝试本地翻译
            localTranslator.translate(text, sourceLang, targetLang)
        } catch (e: TranslationException) {
            // 本地失败,降级到云端
            if (apiKeyManager.hasApiKey()) {
                Log.w("Translation", "本地失败,降级到云端")
                remoteClient.translate(text, sourceLang, targetLang)
            } else {
                // 无 API Key,抛出原始错误
                throw e
            }
        }
    }
}
```

#### 用例 3: 流式覆盖层更新

```kotlin
class StreamingOverlayRenderer {
    private val resultQueue = Channel<TranslationResult>(capacity = 10)

    fun startStreaming() {
        launch {
            for (result in resultQueue) {
                // 逐条添加结果
                overlayRenderer.addResult(result)
                // 自动触发重绘
                delay(100)  // 控制显示速度
            }
        }
    }

    suspend fun translateAndStream(texts: List<String>) {
        texts.forEach { text ->
            launch {
                val translated = translator.translate(text)
                resultQueue.send(TranslationResult(text, translated))
            }
        }
    }
}
```

### 9.4 最佳实践

#### 性能优化

- **图片预处理:** 短边缩放到 672-896px,减少 OCR 计算量
- **并发处理:** OCR 和翻译使用独立协程,充分利用多核 CPU
- **内存管理:** 及时释放 Bitmap 和模型资源,避免 OOM
- **缓存策略:** 翻译结果缓存 (LRU),避免重复翻译

#### 错误处理

- **超时控制:** OCR 和翻译设置合理超时 (如 5 秒)
- **重试机制:** 网络请求指数退避重试 (1s → 2s → 4s)
- **降级策略:** 本地失败自动切换云端,云端失败提示用户
- **用户友好:** 错误信息清晰可操作,提供具体建议

#### 日志规范

```kotlin
// 使用 Timber 分级日志
Timber.d("[OCR] Recognized ${results.size} boxes in ${latency}ms")
Timber.i("[Translation] Translated in ${latency}ms")
Timber.w("[Merge] Large gap detected: ${gap}px")
Timber.e("[System] Out of memory", exception)

// 生产环境关闭 DEBUG 日志
if (BuildConfig.DEBUG) {
    Timber.v("[Verbose] Detailed debug info")
}
```

---

## 10. 更新日志

### 版本 1.0.0 (2026-02-12)

**初始版本**

- ✅ OCR 引擎 API (MLKitOCRManager)
- ✅ 文本合并引擎 API (TextMergerEngine)
- ✅ 翻译管理器 API (TranslationManager)
- ✅ 覆盖层渲染器 API (OverlayRenderer)
- ✅ 屏幕捕获 API (ScreenCaptureManager)
- ✅ 配置管理 API (ConfigManager)
- ✅ 完整数据模型定义
- ✅ 错误处理和错误码规范

---

## 附录

### A. 术语表

| 术语 | 定义 |
|------|------|
| **OCR** | Optical Character Recognition (光学字符识别) |
| **VL Model** | Vision-Language Model (视觉语言模型) |
| **ML Kit** | Google Machine Learning Kit (机器学习 SDK) |
| **MediaProjection** | Android 系统 API,允许捕获屏幕内容 |
| **归一化坐标** | 0-1 范围的相对坐标,与屏幕尺寸无关 |
| **密封类** | Kotlin sealed class,表示受限的类层次结构 |
| **协程** | Kotlin Coroutines,异步编程框架 |
| **Flow** | Kotlinx 异步数据流 |

### B. 支持与联系

- **OCR 文档:** [Google ML Kit Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- **翻译文档:** [Google ML Kit Translation](https://developers.google.com/ml-kit/language/translation)
- **云端 API:** [硅基流动文档](https://docs.siliconflow.cn/)
- **Android 指南:** [Android Developers](https://developer.android.com/guide)
- **Kotlin 语言:** [Kotlin Lang](https://kotlinlang.org/)
- **Compose UI:** [Jetpack Compose](https://developer.android.com/jetpack/compose)

---

**文档状态:** 待评审
**下一步:** Phase 5 迭代验证
