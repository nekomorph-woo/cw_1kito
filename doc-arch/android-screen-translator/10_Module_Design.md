# Module Design Document

**Document Version:** 1.0
**Last Updated:** 2025-02-10
**Status:** DRAFT

---

## 1. Overview

### 1.1 Purpose

本文档详细定义 Android 全局屏幕翻译应用的各个功能模块，包括模块职责、接口定义、数据模型、依赖关系和关键技术点。

### 1.2 Architecture Principles

- **分层架构:** UI Layer → Service Layer → Domain Layer → Data Layer
- **防腐层模式:** 隔离外部 API 结构变化
- **单一职责:** 每个模块只负责一个明确的功能
- **接口隔离:** 模块间通过接口通信，降低耦合

### 1.3 Module Dependency Graph

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ MainActivity │  │SettingsScreen│  │ FloatingView │      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                 │                 │               │
└─────────┼─────────────────┼─────────────────┼───────────────┘
          │                 │                 │
┌─────────┼─────────────────┼─────────────────┼───────────────┐
│         ▼                 ▼                 ▼               │
│                    Service Layer                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │FloatingService│  │CaptureService│  │OverlayService│      │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘      │
│         │                 │                 │               │
└─────────┼─────────────────┼─────────────────┼───────────────┘
          │                 │                 │
┌─────────┼─────────────────┼─────────────────┼───────────────┐
│         ▼                 ▼                 ▼               │
│                    Domain Layer                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │TranslationMgr│  │CoordinateVld │  │TextLayoutEng │      │
│  └──────┬───────┘  └──────────────┘  └──────────────┘      │
│         │                                                          │
└─────────┼──────────────────────────────────────────────────────┘
          │
┌─────────┼──────────────────────────────────────────────────────┐
│         ▼                                                       │
│                    Data Layer                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ConfigManager │  │   ApiClient  │  │  AntiCorruptn│      │
│  └──────────────┘  └──────┬───────┘  └──────┬───────┘      │
│                           │                 │               │
└───────────────────────────┼─────────────────┼───────────────┘
                            ▼                 ▼
                      ┌──────────┐      ┌──────────┐
                      │ External │      │  Local   │
                      │   API    │      │  Storage │
                      └──────────┘      └──────────┘
```

---

## 2. Data Layer Modules

### 2.1 ConfigManager (配置管理器)

**Package:** `commonMain/kotlin/data/config`

**Purpose:** 管理应用配置的持久化存储，包括语言设置、API Key、模型选择等。

**Responsibilities:**
- 保存和加载用户配置
- 提供配置变更通知
- 加密存储敏感信息（API Key）

**Interface Definition:**

```kotlin
/**
 * 配置管理器接口
 */
interface ConfigManager {
    /**
     * 获取当前语言配置
     */
    suspend fun getLanguageConfig(): LanguageConfig

    /**
     * 保存语言配置
     */
    suspend fun saveLanguageConfig(config: LanguageConfig)

    /**
     * 获取 API Key
     */
    suspend fun getApiKey(): String?

    /**
     * 保存 API Key（加密存储）
     */
    suspend fun saveApiKey(apiKey: String)

    /**
     * 验证 API Key 有效性
     */
    suspend fun validateApiKey(apiKey: String): ValidationResult

    /**
     * 获取选中的 VLM 模型
     */
    suspend fun getSelectedModel(): VlmModel

    /**
     * 保存选中的 VLM 模型
     */
    suspend fun saveSelectedModel(model: VlmModel)

    /**
     * 配置变更流
     */
    val configChanges: Flow<ConfigChange>
}

/**
 * 语言配置
 */
@Serializable
data class LanguageConfig(
    val sourceLanguage: Language,
    val targetLanguage: Language
)

/**
 * API Key 验证结果
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()
    data object Invalid : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

/**
 * 配置变更事件
 */
sealed class ConfigChange {
    data class LanguageChanged(val config: LanguageConfig) : ConfigChange()
    data class ApiKeyChanged(val isValid: Boolean) : ConfigChange()
    data class ModelChanged(val model: VlmModel) : ConfigChange()
}
```

**Data Models:**

```kotlin
/**
 * 支持的语言枚举
 */
enum class Language(val code: String, val displayName: String) {
    AUTO("auto", "自动识别"),
    EN("en", "English"),
    ZH("zh", "中文"),
    JA("ja", "日本語"),
    KO("ko", "한국어"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    ES("es", "Español");

    companion object {
        fun fromCode(code: String): Language? =
            values().find { it.code == code }
    }
}

/**
 * VLM 模型枚举
 */
enum class VlmModel(val id: String, val displayName: String) {
    GLM_4_6V("zai-org/GLM-4.6V", "GLM-4.6V"),
    GLM_4_5V("zai-org/GLM-4.5V", "GLM-4.5V"),
    QWEN3_VL_32B("Qwen/Qwen3-VL-32B-Instruct", "Qwen3-VL-32B"),
    QWEN3_VL_30B("Qwen/Qwen3-VL-30B-A3B-Instruct", "Qwen3-VL-30B"),
    QWEN3_VL_235B("Qwen/Qwen3-VL-235B-A22B-Instruct", "Qwen3-VL-235B"),
    QWEN2_5_VL_32B("Qwen/Qwen2.5-VL-32B-Instruct", "Qwen2.5-VL-32B"),
    QWEN2_5_VL_72B("Qwen/Qwen2.5-VL-72B-Instruct", "Qwen2.5-VL-72B");

    companion object {
        val DEFAULT = GLM_4_6V

        fun fromId(id: String): VlmModel? =
            values().find { it.id == id }
    }
}
```

**Implementation Notes:**
- 使用 DataStore 进行持久化存储
- API Key 使用 Android Keystore 加密
- 配置变更通过 Flow 通知观察者

**Dependencies:**
- `androidx.datastore:datastore-preferences:1.1.1`
- Android Keystore (androidMain)

---

### 2.2 TranslationApiClient (翻译 API 客户端)

**Package:** `commonMain/kotlin/data/api`

**Purpose:** 封装与云端大模型 API 的 HTTP 交互。

**Responsibilities:**
- 构建标准化的 API 请求
- 处理 HTTP 通信
- 解析 API 响应
- 处理网络错误和超时

**Interface Definition:**

```kotlin
/**
 * 翻译 API 客户端接口
 */
interface TranslationApiClient {
    /**
     * 发送翻译请求
     * @param request 翻译请求参数
     * @return 翻译响应结果
     * @throws ApiException 当 API 返回错误时抛出
     */
    suspend fun translate(request: TranslationApiRequest): TranslationApiResponse

    /**
     * 验证 API Key 有效性
     */
    suspend fun validateApiKey(apiKey: String): Boolean
}

/**
 * API 异常
 */
sealed class ApiException : Exception {
    data class NetworkError(val cause: Throwable) : ApiException()
    data class AuthError(val message: String) : ApiException()
    data class RateLimitError(val retryAfter: Long?) : ApiException()
    data class ServerError(val code: Int, val message: String) : ApiException()
    data class ParseError(val message: String) : ApiException()
}

/**
 * API 请求参数（内部格式）
 */
@Serializable
data class TranslationApiRequest(
    val model: VlmModel,
    val imageData: String,        // Base64 编码的图像
    val targetLanguage: Language,
    val sourceLanguage: Language = Language.AUTO,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2000
)

/**
 * Token 使用统计
 */
@Serializable
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)
```

**Implementation Notes:**
- 使用 Ktor CIO 引擎
- 超时设置为 30 秒
- 支持重试机制（最多 3 次）
- 使用 Kotlinx Serialization 解析 JSON

**Dependencies:**
- `io.ktor:ktor-client-core:2.3.8`
- `io.ktor:ktor-client-cio:2.3.8`
- `io.ktor:ktor-client-content-negotiation:2.3.8`
- `io.ktor:ktor-serialization-kotlinx-json:2.3.8`

---

### 2.3 AntiCorruptionLayer (防腐层)

**Package:** `commonMain/kotlin/data/adapter`

**Purpose:** 隔离外部 API 结构变化，维护内部领域模型的稳定性。

**Responsibilities:**
- 将外部 API 响应转换为内部领域模型
- 处理坐标归一化（0-1000 → 0-1）
- 验证和清理外部数据
- 提供统一的错误处理

**Interface Definition:**

```kotlin
/**
 * 防腐层接口
 */
interface TranslationApiAdapter {
    /**
     * 将内部请求转换为外部 API 格式
     */
    fun toExternalRequest(request: TranslationApiRequest): ExternalApiRequest

    /**
     * 将外部 API 响应转换为内部领域模型
     */
    fun toInternalResponse(response: ExternalApiResponse): TranslationResponse

    /**
     * 构建翻译 Prompt
     */
    fun buildPrompt(targetLanguage: Language): String
}

/**
 * 内部领域响应模型
 */
@Serializable
data class TranslationResponse(
    val results: List<TranslationResult>,
    val resultId: String,
    val model: VlmModel,
    val usage: TokenUsage? = null
)

/**
 * 单个翻译结果（内部领域模型）
 */
@Serializable
data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val boundingBox: BoundingBox
)

/**
 * 归一化边界框 (0-1)
 */
@Serializable
data class BoundingBox(
    val left: Float,    // 0.0 - 1.0
    val top: Float,     // 0.0 - 1.0
    val right: Float,   // 0.0 - 1.0
    val bottom: Float   // 0.0 - 1.0
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2

    /**
     * 转换为屏幕坐标
     */
    fun toScreenRect(screenWidth: Int, screenHeight: Int): Rect {
        return Rect(
            (left * screenWidth).toInt(),
            (top * screenHeight).toInt(),
            (right * screenWidth).toInt(),
            (bottom * screenHeight).toInt()
        )
    }

    companion object {
        /**
         * 从外部 API 坐标创建（归一化 0-1000 -> 0-1）
         */
        fun fromExternal(coords: List<Int>): BoundingBox {
            require(coords.size == 4) {
                "坐标必须包含 4 个点 [left, top, right, bottom]，实际: $coords"
            }
            return BoundingBox(
                left = (coords[0] / 1000f).coerceIn(0f, 1f),
                top = (coords[1] / 1000f).coerceIn(0f, 1f),
                right = (coords[2] / 1000f).coerceIn(0f, 1f),
                bottom = (coords[3] / 1000f).coerceIn(0f, 1f)
            )
        }
    }
}

/**
 * 外部 API 请求格式（硅基流动）
 */
@Serializable
data class ExternalApiRequest(
    val model: String,
    val messages: List<ExternalMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 2000
)

@Serializable
data class ExternalMessage(
    val role: String,
    val content: List<ExternalContent>
)

@Serializable
data class ExternalContent(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null
)

@Serializable
data class ImageUrl(
    val url: String
)

/**
 * 外部 API 响应格式（硅基流动）
 */
@Serializable
data class ExternalApiResponse(
    val id: String,
    val choices: List<ExternalChoice>,
    val usage: ExternalUsage? = null
)

@Serializable
data class ExternalChoice(
    val message: ExternalMessageResponse
)

@Serializable
data class ExternalMessageResponse(
    val content: String
)

@Serializable
data class ExternalUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)
```

**硅基流动适配器实现:**

```kotlin
/**
 * 硅基流动 API 适配器
 */
class SiliconFlowAdapter : TranslationApiAdapter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun toExternalRequest(request: TranslationApiRequest): ExternalApiRequest {
        return ExternalApiRequest(
            model = request.model.id,
            messages = listOf(
                ExternalMessage(
                    role = "user",
                    content = listOf(
                        ExternalContent(
                            type = "text",
                            text = buildPrompt(request.targetLanguage)
                        ),
                        ExternalContent(
                            type = "image_url",
                            image_url = ImageUrl(
                                url = "data:image/jpeg;base64,${request.imageData}"
                            )
                        )
                    )
                )
            ),
            temperature = request.temperature,
            max_tokens = request.maxTokens
        )
    }

    override fun toInternalResponse(response: ExternalApiResponse): TranslationResponse {
        val content = response.choices.firstOrNull()?.message?.content
            ?: throw ApiException.ParseError("API 响应中没有内容")

        // 解析 API 返回的 JSON 数组
        val results = try {
            json.decodeFromString<List<ExternalTranslationResult>>(content)
        } catch (e: SerializationException) {
            throw ApiException.ParseError("无法解析翻译结果: ${e.message}")
        }

        return TranslationResponse(
            results = results.map { ext ->
                TranslationResult(
                    originalText = ext.original_text,
                    translatedText = ext.translated_text,
                    boundingBox = BoundingBox.fromExternal(ext.coordinates)
                )
            },
            resultId = response.id,
            model = VlmModel.DEFAULT, // 从响应中获取
            usage = response.usage?.let {
                TokenUsage(
                    promptTokens = it.prompt_tokens,
                    completionTokens = it.completion_tokens,
                    totalTokens = it.total_tokens
                )
            }
        )
    }

    override fun buildPrompt(targetLanguage: Language): String {
        return """
            Identify all text in this image. For each text block, provide:
            1. The original text as it appears
            2. The translation to ${targetLanguage.displayName}
            3. The bounding box coordinates normalized to 0-1000 scale as [left, top, right, bottom]

            Return the results strictly in this JSON format:
            [
              {
                "original_text": "...",
                "translated_text": "...",
                "coordinates": [left, top, right, bottom]
              }
            ]

            Only return valid JSON, no additional text.
        """.trimIndent()
    }
}

@Serializable
data class ExternalTranslationResult(
    val original_text: String,
    val translated_text: String,
    val coordinates: List<Int>
)
```

**Dependencies:**
- Kotlinx Serialization
- TranslationApiClient

---

## 3. Domain Layer Modules

### 3.1 TranslationManager (翻译管理器)

**Package:** `commonMain/kotlin/domain/translation`

**Purpose:** 协调截图、API 调用和结果处理的完整翻译流程。

**Responsibilities:**
- 编排完整的翻译流程
- 处理翻译状态
- 错误处理和重试
- 结果分发

**Interface Definition:**

```kotlin
/**
 * 翻译管理器接口
 */
interface TranslationManager {
    /**
     * 翻译状态流
     */
    val translationState: StateFlow<TranslationState>

    /**
     * 执行翻译
     * @param imageBytes 截图数据
     * @param config 语言配置
     */
    suspend fun translate(imageBytes: ByteArray, config: TranslationConfig)

    /**
     * 取消当前翻译
     */
    fun cancelTranslation()

    /**
     * 重试上次翻译
     */
    suspend fun retryLastTranslation()
}

/**
 * 翻译配置
 */
data class TranslationConfig(
    val model: VlmModel,
    val sourceLanguage: Language,
    val targetLanguage: Language,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2000
)

/**
 * 翻译状态
 */
sealed class TranslationState {
    data object Idle : TranslationState()
    data object Processing : TranslationState()
    data class Success(val results: List<TranslationResult>) : TranslationState()
    data class Error(val error: TranslationError) : TranslationState()
}

/**
 * 翻译错误
 */
sealed class TranslationError {
    data object NoApiKey : TranslationError()
    data object NetworkUnavailable : TranslationError()
    data object CaptureFailed : TranslationError()
    data class ApiError(val message: String) : TranslationError()
    data class ParseError(val message: String) : TranslationError()
}
```

**Implementation Notes:**
- 使用 StateFlow 管理状态
- 实现自动重试机制
- 截图数据使用后立即释放

**Dependencies:**
- TranslationApiClient
- AntiCorruptionLayer
- CoordinateValidator
- ConfigManager

---

### 3.2 CoordinateValidator (坐标验证器)

**Package:** `commonMain/kotlin/domain/coordinate`

**Purpose:** 处理大模型返回的坐标偏差，提供容错机制。

**Responsibilities:**
- 验证坐标有效性
- 裁剪超出屏幕的坐标
- 扩展过小的坐标框
- 处理重叠的文本框

**Interface Definition:**

```kotlin
/**
 * 坐标验证器接口
 */
interface CoordinateValidator {
    /**
     * 验证和调整单个边界框
     * @param box 原始边界框
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 调整后的边界框
     */
    fun validateAndAdjust(
        box: BoundingBox,
        screenWidth: Int,
        screenHeight: Int
    ): BoundingBox

    /**
     * 验证和调整多个边界框（处理重叠）
     */
    fun validateAndAdjustAll(
        boxes: List<BoundingBox>,
        screenWidth: Int,
        screenHeight: Int
    ): List<BoundingBox>
}

/**
 * 坐标验证器配置
 */
data class CoordinateValidatorConfig(
    val minBoxSize: Float = 0.02f,      // 最小框尺寸（屏幕比例）
    val expansionPadding: Float = 0.01f, // 扩展边距
    val overlapThreshold: Float = 0.5f   // 重叠阈值
)
```

**Implementation:**

```kotlin
/**
 * 坐标验证器实现
 */
class CoordinateValidatorImpl(
    private val config: CoordinateValidatorConfig = CoordinateValidatorConfig()
) : CoordinateValidator {

    override fun validateAndAdjust(
        box: BoundingBox,
        screenWidth: Int,
        screenHeight: Int
    ): BoundingBox {
        // 1. 边界裁剪
        var adjusted = box.clipToBounds()

        // 2. 检查是否需要扩展
        if (adjusted.width < config.minBoxSize || adjusted.height < config.minBoxSize) {
            adjusted = adjusted.expandToMinSize(
                config.minBoxSize,
                config.expansionPadding
            )
        }

        // 3. 最终边界检查
        return adjusted.clipToBounds()
    }

    override fun validateAndAdjustAll(
        boxes: List<BoundingBox>,
        screenWidth: Int,
        screenHeight: Int
    ): List<BoundingBox> {
        // 先验证和调整每个框
        val validated = boxes.map { validateAndAdjust(it, screenWidth, screenHeight) }

        // 处理重叠
        return resolveOverlaps(validated)
    }

    private fun BoundingBox.clipToBounds() = copy(
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        right = right.coerceIn(0f, 1f),
        bottom = bottom.coerceIn(0f, 1f)
    ).also { box ->
        // 确保 left < right, top < bottom
        require(box.left < box.right && box.top < box.bottom) {
            "无效的边界框: $box"
        }
    }

    private fun BoundingBox.expandToMinSize(minSize: Float, padding: Float): BoundingBox {
        val currentWidth = right - left
        val currentHeight = bottom - top

        if (currentWidth >= minSize && currentHeight >= minSize) {
            return this
        }

        // 计算需要的扩展量
        val widthExpansion = maxOf(0f, minSize - currentWidth) / 2
        val heightExpansion = maxOf(0f, minSize - currentHeight) / 2

        return copy(
            left = (left - widthExpansion - padding).coerceAtLeast(0f),
            top = (top - heightExpansion - padding).coerceAtLeast(0f),
            right = (right + widthExpansion + padding).coerceAtMost(1f),
            bottom = (bottom + heightExpansion + padding).coerceAtMost(1f)
        )
    }

    private fun resolveOverlaps(boxes: List<BoundingBox>): List<BoundingBox> {
        // 简单的重叠处理：按面积排序，优先保留大的框
        val sorted = boxes.sortedByDescending { it.width * it.height }
        val result = mutableListOf<BoundingBox>()

        for (box in sorted) {
            var adjustedBox = box
            for (existing in result) {
                adjustedBox = adjustedBox.resolveOverlap(existing, config.overlapThreshold)
            }
            result.add(adjustedBox)
        }

        return result
    }

    private fun BoundingBox.resolveOverlap(
        other: BoundingBox,
        threshold: Float
    ): BoundingBox {
        val intersection = BoundingBox(
            left = maxOf(left, other.left),
            top = maxOf(top, other.top),
            right = minOf(right, other.right),
            bottom = minOf(bottom, other.bottom)
        )

        val intersectionArea = intersection.width * intersection.height
        val thisArea = width * height

        return if (intersectionArea > thisArea * threshold) {
            // 有显著重叠，向远离另一个框的方向扩展
            val centerX = (left + right) / 2
            val otherCenterX = (other.left + other.right) / 2

            if (centerX < otherCenterX) {
                // 在左边，向左扩展
                copy(right = other.left - 0.01f)
            } else {
                // 在右边，向右扩展
                copy(left = other.right + 0.01f)
            }
        } else {
            this
        }
    }
}
```

**Dependencies:**
- 无

---

### 3.3 TextLayoutEngine (文本排版引擎)

**Package:** `commonMain/kotlin/domain/layout`

**Purpose:** 处理翻译后文本的自适应排版，确保文本在矩形框内正确显示。

**Responsibilities:**
- 根据可用空间计算最佳字体大小
- 处理文本换行
- 支持中英文混合排版
- 计算文本在矩形框内的最佳布局

**Interface Definition:**

```kotlin
/**
 * 文本排版引擎接口
 */
interface TextLayoutEngine {
    /**
     * 计算文本布局
     * @param text 要排版的文本
     * @param boundingBox 归一化边界框
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 文本布局结果
     */
    fun calculateLayout(
        text: String,
        boundingBox: BoundingBox,
        screenWidth: Int,
        screenHeight: Int
    ): TextLayout

    /**
     * 测量文本尺寸
     */
    fun measureText(
        text: String,
        textSize: Float,
        maxWidth: Float
    ): TextMetrics
}

/**
 * 文本布局结果
 */
data class TextLayout(
    val textSize: Float,           // 字体大小 (sp)
    val lines: List<String>,       // 分行后的文本
    val lineBounds: List<Rect>,    // 每行的边界
    val isOverflow: Boolean,       // 是否溢出
    val suggestedBox: BoundingBox? = null  // 建议的框体（如果溢出）
)

/**
 * 文本测量结果
 */
data class TextMetrics(
    val width: Float,
    val height: Float,
    val lineCount: Int
)

/**
 * 排版策略
 */
enum class LayoutStrategy {
    FIXED_SIZE,      // 固定字号，超出截断
    ADJUST_SIZE,     // 自适应字号
    EXPAND_BOX,      // 扩展框体
    MULTI_LINE       // 多行换行
}

/**
 * 排版配置
 */
data class TextLayoutConfig(
    val strategy: LayoutStrategy = LayoutStrategy.ADJUST_SIZE,
    val minTextSize: Float = 12f,     // 最小字号 (sp)
    val maxTextSize: Float = 24f,     // 最大字号 (sp)
    val defaultTextSize: Float = 16f, // 默认字号 (sp)
    val lineHeightMultiplier: Float = 1.2f,  // 行高倍数
    val padding: Float = 8f           // 内边距 (dp)
)
```

**Implementation Notes:**
- 使用二分查找确定最优字号
- 支持多种排版策略
- 考虑中英文混排

**Dependencies:**
- Android Canvas/Paint (androidMain)

---

## 4. Service Layer Modules

### 4.1 FloatingService (悬浮窗服务)

**Package:** `androidMain/kotlin/service/floating`

**Purpose:** 前台服务，管理悬浮球和覆盖层的显示。

**Responsibilities:**
- 保持应用在后台运行
- 显示和管理悬浮球
- 处理悬浮球点击和拖拽事件
- 管理覆盖层的显示和隐藏

**Interface Definition:**

```kotlin
/**
 * 悬浮窗服务
 */
class FloatingService : Service() {

    companion object {
        const val ACTION_START = "com.cw2.cw_1kito.ACTION_START"
        const val ACTION_STOP = "com.cw2.cw_1kito.ACTION_STOP"
        const val ACTION_SHOW_OVERLAY = "com.cw2.cw_1kito.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.cw2.cw_1kito.ACTION_HIDE_OVERLAY"

        const val EXTRA_RESULTS = "results"
        const val EXTRA_SCREEN_WIDTH = "screen_width"
        const val EXTRA_SCREEN_HEIGHT = "screen_height"
    }

    private lateinit var windowManager: WindowManager
    private var floatingView: FloatingBallView? = null
    private var overlayView: TranslationOverlayView? = null
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): FloatingService = this@FloatingService
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startAsForeground()
        addFloatingView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> addFloatingView()
            ACTION_STOP -> removeFloatingView()
            ACTION_SHOW_OVERLAY -> {
                val results = intent.getParcelableArrayListExtra<TranslationResult>(
                    EXTRA_RESULTS,
                    TranslationResult::class.java
                )
                val width = intent.getIntExtra(EXTRA_SCREEN_WIDTH, 0)
                val height = intent.getIntExtra(EXTRA_SCREEN_HEIGHT, 0)
                if (results != null && width > 0 && height > 0) {
                    showOverlay(results, width, height)
                }
            }
            ACTION_HIDE_OVERLAY -> hideOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        removeFloatingView()
        hideOverlay()
    }

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("屏幕翻译")
            .setContentText("悬浮窗已启用")
            .setSmallIcon(R.drawable.ic_translate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    private fun addFloatingView() {
        if (floatingView != null) return

        floatingView = FloatingBallView(this).apply {
            setOnClickListener {
                // 触发截图翻译
                onFloatingBallClicked()
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        windowManager.addView(floatingView, params)
    }

    private fun removeFloatingView() {
        floatingView?.let {
            windowManager.removeView(it)
            floatingView = null
        }
    }

    private fun showOverlay(
        results: List<TranslationResult>,
        screenWidth: Int,
        screenHeight: Int
    ) {
        // 实现覆盖层显示
    }

    private fun hideOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    private fun onFloatingBallClicked() {
        // 发送广播或使用其他方式通知 Activity 处理点击
        val intent = Intent("com.cw2.cw_1kito.TRANSLATE_REQUEST")
        sendBroadcast(intent)
    }
}
```

**Dependencies:**
- WindowManager
- NotificationManager
- TranslationOverlayView

---

### 4.2 CaptureService (截图服务)

**Package:** `androidMain/kotlin/service/capture`

**Purpose:** 使用 MediaProjection API 截取屏幕内容。

**Responsibilities:**
- 请求录屏权限
- 创建 VirtualDisplay
- 从 ImageReader 获取图像
- 将图像转换为 ByteArray

**Interface Definition:**

```kotlin
/**
 * 截图服务
 */
interface ScreenCapture {
    /**
     * 请求录屏权限
     * @param activity 用于启动权限请求的 Activity
     * @param requestCode 请求码
     */
    fun requestPermission(activity: Activity, requestCode: Int)

    /**
     * 开始截图
     * @param resultCode 权限请求结果
     * @param data 权限返回数据
     * @return 截图结果流
     */
    suspend fun captureScreen(
        resultCode: Int,
        data: Intent?
    ): CaptureResult

    /**
     * 停止截图并释放资源
     */
    fun stopCapture()

    /**
     * 检查是否有录屏权限
     */
    fun hasPermission(): Boolean
}

/**
 * 截图结果
 */
sealed class CaptureResult {
    data class Success(
        val imageBytes: ByteArray,
        val width: Int,
        val height: Int
    ) : CaptureResult()

    data object PermissionDenied : CaptureResult()
    data class Error(val message: String) : CaptureResult()
}

/**
 * 截图配置
 */
data class CaptureConfig(
    val width: Int = 1080,
    val height: Int = 1920,
    val density: Int = 320,
    val quality: Int = 85,  // JPEG 压缩质量
    val format: Int = PixelFormat.RGBA_8888
)
```

**Implementation Notes:**
- 使用 MediaProjectionManager 获取权限
- 创建 VirtualDisplay 镜像屏幕
- 使用 ImageReader 获取画面
- 压缩图像为 JPEG 格式
- 使用完立即释放资源

**Dependencies:**
- MediaProjectionManager
- MediaProjection
- VirtualDisplay
- ImageReader

---

### 4.3 OverlayView (覆盖层视图)

**Package:** `androidMain/kotlin/service/overlay`

**Purpose:** 在屏幕上绘制翻译结果。

**Responsibilities:**
- 创建全屏透明覆盖层
- 根据坐标绘制白色矩形框
- 在矩形框内绘制翻译文本
- 处理点击关闭事件

**Interface Definition:**

```kotlin
/**
 * 翻译覆盖层视图
 */
class TranslationOverlayView(
    context: Context,
    private val results: List<TranslationResult>,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val onDismiss: () -> Unit
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val validator = CoordinateValidatorImpl()
    private val layoutEngine = TextLayoutEngineImpl()

    init {
        // 设置视图为全屏透明
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // 不响应触摸，允许点击穿透到下层
        // 点击白色框区域时关闭
        setOnClickListener {
            onDismiss()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制每个翻译结果
        for (result in results) {
            drawTranslationResult(canvas, result)
        }
    }

    private fun drawTranslationResult(canvas: Canvas, result: TranslationResult) {
        // 1. 验证和调整坐标
        val validatedBox = validator.validateAndAdjust(
            result.boundingBox,
            screenWidth,
            screenHeight
        )

        // 2. 转换为屏幕坐标
        val rect = validatedBox.toScreenRect(screenWidth, screenHeight)

        // 3. 计算文本布局
        val layout = layoutEngine.calculateLayout(
            result.translatedText,
            validatedBox,
            screenWidth,
            screenHeight
        )

        // 4. 绘制白色背景
        paint.color = Color.WHITE
        canvas.drawRect(rect, paint)

        // 5. 绘制边框
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.BLACK
        canvas.drawRect(rect, paint)

        // 6. 绘制文本
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        textPaint.textSize = layout.textSize * resources.displayMetrics.scaledDensity

        layout.lines.forEachIndexed { index, line ->
            val x = rect.left + layout.padding
            val y = rect.top + layout.padding +
                (index + 1) * layout.textSize * layout.lineHeightMultiplier
            canvas.drawText(line, x, y, textPaint)
        }
    }
}
```

**Dependencies:**
- Canvas
- Paint
- CoordinateValidator
- TextLayoutEngine

---

## 5. UI Layer Modules

### 5.1 MainActivity (主界面)

**Package:** `androidMain/kotlin/ui/main`

**Purpose:** 应用入口，提供设置界面和启动悬浮窗服务的入口。

**Responsibilities:**
- 显示语言选择界面
- 显示 API Key 配置界面
- 引导用户授予必要权限
- 启动悬浮窗服务
- 监听翻译请求

**Interface Definition:**

```kotlin
/**
 * 主 Activity
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onRequestPermissions: () -> Unit,
    onStartService: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("屏幕翻译设置") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // API Key 配置
            ApiKeySection(
                apiKey = uiState.apiKey,
                isValid = uiState.isApiKeyValid,
                onApiKeyChange = { viewModel.saveApiKey(it) }
            )

            Divider()

            // 语言配置
            LanguageSection(
                sourceLanguage = uiState.sourceLanguage,
                targetLanguage = uiState.targetLanguage,
                onSourceLanguageChange = { viewModel.updateSourceLanguage(it) },
                onTargetLanguageChange = { viewModel.updateTargetLanguage(it) }
            )

            Divider()

            // 模型选择
            ModelSection(
                selectedModel = uiState.selectedModel,
                onModelChange = { viewModel.updateModel(it) }
            )

            Divider()

            // 权限状态
            PermissionSection(
                hasOverlayPermission = uiState.hasOverlayPermission,
                hasBatteryOptimizationDisabled = uiState.hasBatteryOptimizationDisabled,
                onRequestPermissions = onRequestPermissions
            )

            Spacer(modifier = Modifier.weight(1f))

            // 启动按钮
            Button(
                onClick = onStartService,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.canStartService
            ) {
                Text("启动全局悬浮窗")
            }
        }
    }
}
```

**Dependencies:**
- MainViewModel
- PermissionManager
- ConfigManager

---

### 5.2 PermissionManager (权限管理器)

**Package:** `androidMain/kotlin/permission`

**Purpose:** 管理应用所需的各种权限。

**Responsibilities:**
- 检查权限状态
- 请求权限
- 引导用户到系统设置

**Interface Definition:**

```kotlin
/**
 * 权限管理器
 */
interface PermissionManager {
    /**
     * 检查悬浮窗权限
     */
    fun hasOverlayPermission(): Boolean

    /**
     * 请求悬浮窗权限（跳转到设置页面）
     */
    fun requestOverlayPermission(context: Context)

    /**
     * 检查电池优化是否已关闭
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean

    /**
     * 请求忽略电池优化
     */
    fun requestIgnoreBatteryOptimization(activity: Activity)

    /**
     * 检查是否有录屏权限
     */
    fun hasScreenCapturePermission(): Boolean

    /**
     * 获取所有必需权限的状态
     */
    fun getAllPermissionStatus(context: Context): PermissionStatus
}

/**
 * 权限状态
 */
data class PermissionStatus(
    val hasOverlayPermission: Boolean,
    val hasBatteryOptimizationDisabled: Boolean,
    val canStartService: Boolean
)
```

**Implementation Notes:**
- 使用 Settings.canDrawOverlays() 检查悬浮窗权限
- 使用 PowerManager.isIgnoringBatteryOptimizations() 检查电池优化
- 权限请求需要跳转到系统设置页面

**Dependencies:**
- Android Settings API
- PowerManager

---

### 5.3 LoadingIndicator (加载指示器)

**Package:** `androidMain/kotlin/ui/loading`

**Purpose:** 在悬浮球周围显示加载动画，提供网络请求进度反馈。

**Responsibilities:**
- 绘制环绕悬浮球的加载动画
- 显示不同状态（空闲、加载中、成功、失败）
- 动画效果流畅执行

**Interface Definition:**

```kotlin
/**
 * 加载指示器
 */
class LoadingIndicator(
    context: Context,
    private val state: LoadingState
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rotationAngle = 0f
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { animation ->
            rotationAngle = animation.animatedValue as Float
            invalidate()
        }
    }

    /**
     * 加载状态
     */
    sealed class LoadingState {
        data object Idle : LoadingState()
        data object Loading : LoadingState()
        data object Success : LoadingState()
        data class Error(val message: String) : LoadingState()
    }

    /**
     * 设置状态
     */
    fun setState(newState: LoadingState) {
        when (newState) {
            is LoadingState.Loading -> {
                animator.start()
                paint.color = Color.BLUE
            }
            is LoadingState.Success -> {
                animator.cancel()
                paint.color = Color.GREEN
                postDelayed({ animator.cancel() }, 500)
            }
            is LoadingState.Error -> {
                animator.cancel()
                paint.color = Color.RED
                postDelayed({ animator.cancel() }, 500)
            }
            is LoadingState.Idle -> {
                animator.cancel()
                paint.color = Color.GRAY
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) / 2f + 8f // 悬浮球外圈

        when (state) {
            is LoadingState.Loading -> {
                // 绘制旋转的加载圆环
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                canvas.save()
                canvas.rotate(rotationAngle, centerX, centerY)
                canvas.drawArc(
                    centerX - radius,
                    centerY - radius,
                    centerX + radius,
                    centerY + radius,
                    0f,
                    270f,
                    paint
                )
                canvas.restore()
            }
            is LoadingState.Success -> {
                // 绘制绿色对勾
                paint.style = Paint.Style.FILL
                drawCheckMark(canvas, centerX, centerY)
            }
            is LoadingState.Error -> {
                // 绘制红色叉号
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 4f
                drawCrossMark(canvas, centerX, centerY)
            }
            is LoadingState.Idle -> {
                // 空闲状态不绘制
            }
        }
    }

    private fun drawCheckMark(canvas: Canvas, cx: Float, cy: Float) {
        // 绘制对勾
    }

    private fun drawCrossMark(canvas: Canvas, cx: Float, cy: Float) {
        // 绘制叉号
    }
}
```

**状态颜色定义:**

```kotlin
object LoadingColors {
    val IDLE = Color.argb(128, 128, 128, 128)      // 半透明灰色
    val LOADING = Color.argb(255, 33, 150, 243)     // 蓝色
    val SUCCESS = Color.argb(255, 76, 175, 80)       // 绿色
    val ERROR = Color.argb(255, 244, 67, 54)         // 红色
}
```

**动画效果:**
- **加载中**: 270° 圆弧顺时针旋转
- **成功**: 绿色对勾闪烁 500ms
- **失败**: 红色叉号闪烁 500ms

**Dependencies:**
- Canvas
- Paint
- ValueAnimator

---

### 5.4 NetworkMonitor (网络监听器)

**Package:** `androidMain/kotlin/network`

**Purpose:** 监听网络连接状态变化，提供网络可用性信息。

**Responsibilities:**
- 监听网络连接状态
- 发送网络状态变化事件
- 检测网络类型（WiFi/Mobile）

**Interface Definition:**

```kotlin
/**
 * 网络监听器
 */
interface NetworkMonitor {
    /**
     * 网络状态流
     */
    val networkState: StateFlow<NetworkState>

    /**
     * 当前是否在线
     */
    val isOnline: Boolean

    /**
     * 开始监听
     */
    fun startMonitoring()

    /**
     * 停止监听
     */
    fun stopMonitoring()
}

/**
 * 网络状态
 */
sealed class NetworkState {
    data object Available : NetworkState()
    data object Unavailable : NetworkState()
    data class Lost(val message: String) : NetworkState()
    data class Restored(val message: String) : NetworkState()
}
```

**Implementation Notes:**
- 使用 ConnectivityManager.NetworkCallback
- 通过 StateFlow 发送状态变化
- 自动处理配置变化

**Dependencies:**
- ConnectivityManager
- NetworkCallback

---

### 5.5 OnboardingScreen (首次使用引导)

**Package:** `commonMain/kotlin/ui/onboarding`

**Purpose:** 首次启动时的用户引导教程。

**Responsibilities:**
- 展示功能介绍
- 引导 API Key 配置
- 引导权限授予
- 教程悬浮球使用

**Interface Definition:**

```kotlin
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = viewModel(),
    onComplete: () -> Unit
) {
    val currentPage by viewModel.currentPage.collectAsState()
    val totalPages = 4

    HorizontalPager(
        state = rememberPagerState(
            initialPage = currentPage,
            pageCount = { totalPages }
        )
    ) { page ->
        when (page) {
            0 -> WelcomePage(onNext = { viewModel.nextPage() })
            1 -> ApiKeyGuidePage(onNext = { viewModel.nextPage() })
            2 -> PermissionGuidePage(onNext = { viewModel.nextPage() })
            3 -> TutorialPage(onComplete = onComplete)
        }
    }
}
```

**Dependencies:**
- Accomponent Pager
- DataStore（保存引导完成状态）

---

## 6. Directory Structure (更新)

```
composeApp/src/
├── commonMain/kotlin/
│   ├── data/
│   │   ├── config/
│   │   │   ├── ConfigManager.kt
│   │   │   ├── ConfigManagerImpl.kt
│   │   │   └── LanguageConfig.kt
│   │   ├── api/
│   │   │   ├── TranslationApiClient.kt
│   │   │   ├── TranslationApiClientImpl.kt
│   │   │   └── ApiModels.kt
│   │   └── adapter/
│   │       ├── TranslationApiAdapter.kt
│   │       ├── SiliconFlowAdapter.kt
│   │       └── ExternalModels.kt
│   ├── domain/
│   │   ├── translation/
│   │   │   ├── TranslationManager.kt
│   │   │   └── TranslationManagerImpl.kt
│   │   ├── coordinate/
│   │   │   ├── CoordinateValidator.kt
│   │   │   └── CoordinateValidatorImpl.kt
│   │   └── layout/
│   │       ├── TextLayoutEngine.kt
│   │       └── TextLayoutEngineImpl.kt
│   ├── ui/
│   │   ├── main/
│   │   │   ├── MainScreen.kt
│   │   │   ├── MainViewModel.kt
│   │   │   └── components/
│   │   │       ├── ApiKeySection.kt
│   │   │       ├── LanguageSection.kt
│   │   │       ├── ModelSection.kt
│   │   │       └── PermissionSection.kt
│   │   ├── onboarding/
│   │   │   ├── OnboardingScreen.kt
│   │   │   ├── OnboardingViewModel.kt
│   │   │   └── pages/
│   │   │       ├── WelcomePage.kt
│   │   │       ├── ApiKeyGuidePage.kt
│   │   │       ├── PermissionGuidePage.kt
│   │   │       └── TutorialPage.kt
│   │   └── theme/
│   │       ├── Color.kt
│   │       ├── Theme.kt
│   │       └── Type.kt
│   └── model/
│       ├── TranslationResult.kt
│       ├── BoundingBox.kt
│       └── Language.kt
├── androidMain/kotlin/
│   ├── service/
│   │   ├── floating/
│   │   │   ├── FloatingService.kt
│   │   │   ├── FloatingBallView.kt
│   │   │   └── LoadingIndicator.kt
│   │   ├── capture/
│   │   │   ├── ScreenCapture.kt
│   │   │   └── ScreenCaptureImpl.kt
│   │   └── overlay/
│   │       ├── TranslationOverlayView.kt
│   │       ├── OverlayRenderer.kt
│   │       └── ScreenOrientationListener.kt
│   ├── permission/
│   │   ├── PermissionManager.kt
│   │   └── PermissionManagerImpl.kt
│   ├── network/
│   │   ├── NetworkMonitor.kt
│   │   └── NetworkMonitorImpl.kt
│   ├── security/
│   │   ├── KeystoreManager.kt
│   │   └── SecureStorage.kt
│   ├── logging/
│   │   └── TimberConfig.kt
│   └── MainActivity.kt
├── androidMain/res/
│   ├── drawable/
│   ├── values/
│   │   ├── strings.xml
│   │   └── colors.xml
│   └── xml/
│       └── network_security_config.xml
└── commonTest/kotlin/
    ├── data/
    │   ├── adapter/
    │   │   └── SiliconFlowAdapterTest.kt
    │   └── api/
    │       └── TranslationApiClientTest.kt
    └── domain/
        ├── coordinate/
        │   └── CoordinateValidatorTest.kt
        └── layout/
            └── TextLayoutEngineTest.kt
```

---

## 7. Dependencies Summary

### 7.1 Common Dependencies

```toml
[versions]
kotlin = "2.3.0"
compose = "1.10.0"
material3 = "1.10.0-alpha05"
lifecycle = "2.9.6"
ktor = "2.3.8"
serialization = "1.6.2"
datastore = "1.1.1"
accompanist = "0.36.0"
timber = "5.0.1"

[libraries]
# Compose
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "compose" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "material3" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "compose" }

# Lifecycle
lifecycle-viewmodel = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }

# Ktor
ktor-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }

# Serialization
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }

# DataStore
datastore-preferences = { module = "androidx.datastore:datastore-preferences-core", version.ref = "datastore" }

# Accompanist (用于权限处理和引导页面)
accompanist-pager = { module = "com.google.accompanist:accompanist-pager", version.ref = "accompanist" }
accompanist-permissions = { module = "com.google.accompanist:accompanist-permissions", version.ref = "accompanist" }

# Logging
timber = { module = "com.jakewharton.timber:timber", version.ref = "timber" }
```

### 7.2 Android-Specific Dependencies

```toml
[libraries]
# AndroidX
androidx-core-ktx = { module = "androidx.core:core-ktx", version = "1.15.0" }
androidx-appcompat = { module = "androidx.appcompat:appcompat", version = "1.7.0" }

# Permissions
google-accompanist-permissions = { module = "com.google.accompanist:accompanist-permissions", version = "0.36.0" }
```

---

## 8. Test Strategy

### 8.1 Unit Tests

| Module | Test Coverage | Key Scenarios |
|--------|---------------|---------------|
| ConfigManager | 80% | 保存/加载配置、加密/解密 API Key |
| TranslationApiClient | 85% | 请求构建、响应解析、错误处理 |
| SiliconFlowAdapter | 90% | 格式转换、坐标归一化 |
| CoordinateValidator | 95% | 边界裁剪、框体扩展、重叠处理 |
| TextLayoutEngine | 85% | 字体计算、换行处理 |

### 8.2 Integration Tests

| Scenario | Description |
|----------|-------------|
| 端到端翻译流程 | 从截图到显示翻译结果 |
| 权限请求流程 | 验证所有权限正确请求 |
| 配置持久化 | 验证配置正确保存和加载 |

### 8.3 UI Tests

| Scenario | Description |
|----------|-------------|
| 主界面交互 | 验证所有 UI 元素正常工作 |
| 悬浮窗交互 | 验证悬浮球拖拽和点击 |

---

## 9. Security Considerations

### 9.1 API Key Storage

- 使用 Android Keystore 加密存储
- 密钥永不以明文形式写入日志
- 密钥仅在内存中使用

### 9.2 Screenshot Data

- 截图数据仅存储在内存
- 使用后立即释放
- 不写入日志或缓存

### 9.3 Network Communication

- 强制使用 HTTPS
- 验证 SSL 证书
- 超时限制防止挂起

---

## 10. Open Questions

| ID | Question | Priority |
|----|----------|----------|
| M-001 | 坐标验证参数如何根据实际使用优化？ | P1 |
| M-002 | 是否需要支持更多 VLM 模型？ | P2 |
| M-003 | 如何实现离线模式？ | P2 |

---

## Appendix

### A. Type Aliases

```kotlin
typealias CoordinateQuad = List<Float>  // [x1, y1, x2, y2]
typealias TranslationResults = List<TranslationResult>
```

### B. Constants

```kotlin
object ApiConstants {
    const val BASE_URL = "https://api.siliconflow.cn/v1/chat/completions"
    const val TIMEOUT_MS = 30000L
    const val MAX_RETRIES = 3
}

object CoordinateConstants {
    const val NORMALIZATION_SCALE = 1000  // 外部 API 坐标范围
    const val MIN_BOX_SIZE = 0.02f        // 最小框尺寸（屏幕比例）
    const val EXPANSION_PADDING = 0.01f   // 扩展边距
}
```
