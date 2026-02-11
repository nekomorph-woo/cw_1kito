# API Documentation

**Document Version:** 1.1
**Last Updated:** 2025-02-10
**API Version:** 1.0
**Base URL:** https://api.siliconflow.cn/v1/chat/completions

---

## 1. API Overview

### 1.1 Purpose

本文档描述 Android 翻译应用与硅基流动多模态大模型 API 的交互规范。应用通过发送截图图像和配置参数，获取文本识别和翻译结果。

**注意:** 系统内部使用防腐层隔离外部 API 结构，外部 API 变更不应影响内部领域模型。

### 1.2 Target Audience

- Android 应用开发者
- API 集成工程师
- 后端服务开发者

### 1.3 API Style

本应用调用第三方大模型 API，主要采用 RESTful 风格：
- 请求格式: HTTP POST (multipart/form-data 或 JSON)
- 响应格式: JSON
- 认证方式: Bearer Token 或 API Key

### 1.4 Versioning Strategy

应用内部通过防腐层抽象 API 接口，支持多个 LLM 提供商：
- **当前使用:** 硅基流动 GLM-4V / GLM-4.6V
- **兼容格式:** OpenAI Chat Completions API 格式
- **未来扩展:** 可添加其他兼容 OpenAI 格式的提供商

---

## 2. Authentication

### 2.1 Authentication Mechanism

| Mechanism | Description |
|-----------|-------------|
| **API Key** | 应用内配置，用户自行提供或使用统一后端 |
| **Bearer Token** | 某些提供商使用 Token 认证 |

### 2.2 Obtaining Credentials

**用户自备 Key 模式（当前方案）:**
- 用户在硅基流动官网 (https://cloud.siliconflow.cn/) 注册并获取 API Key
- 应用内提供配置界面输入 Key
- Key 使用 Android Keystore 加密存储
- 应用不存储集中密钥，用户自行管理

### 2.3 Using Credentials

#### 硅基流动 API (兼容 OpenAI 格式)
```http
Authorization: Bearer YOUR_API_KEY
Content-Type: application/json
```

### 2.4 Token Management

| Operation | Description |
|-----------|-------------|
| 存储 | 使用 Android Keystore 加密存储 |
| 验证 | 首次使用时发送测试请求验证 |
| 更新 | 用户可在设置界面更新 API Key |
| 删除 | 清除应用数据时删除 |

---

## 3. Common Behavior

### 3.1 Request Format

**图像上传方式:**

| 方式 | 格式 | 说明 |
|------|------|------|
| Base64 | JSON | 图像编码为 Base64 字符串 |
| Multipart | multipart/form-data | 原始二进制图像数据 |

### 3.2 Response Format

标准翻译响应格式（应用期望）:

```json
{
  "results": [
    {
      "original_text": "Hello World",
      "translated_text": "你好世界",
      "coordinates": [100, 200, 500, 250]
    }
  ]
}
```

### 3.3 Error Handling

常见错误类型：

| HTTP Code | 含义 | 应用处理 |
|-----------|------|----------|
| 400 | 请求参数错误 | 提示用户检查配置 |
| 401 | 认证失败 | 提示 API Key 无效 |
| 429 | 请求频率限制 | 延迟后重试 |
| 500 | 服务器错误 | 显示错误提示，提供重试 |

### 3.4 Rate Limiting

各提供商的限流策略不同：

| 提供商 | 限制类型 | 限制值（示例） |
|--------|----------|----------------|
| OpenAI | RPM/TPM | 500 req/min |
| Gemini | RPD | 1500 req/day |
| Claude | TPM | 50k tokens/min |

### 3.5 Image Guidelines

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| 格式 | PNG/JPEG | 兼容性和压缩比平衡 |
| 最大尺寸 | 2048px | 保持清晰度同时减少流量 |
| 文件大小 | < 5MB | API 限制 |
| 编码 | Base64 | 用于 JSON 请求 |

---

## 4. API Endpoints

### 4.1 翻译端点 (Translation Endpoint)

#### 硅基流动多模态 API

```http
POST https://api.siliconflow.cn/v1/chat/completions
```

**Headers:**
```http
Authorization: Bearer YOUR_API_KEY
Content-Type: application/json
```

**Request Body:**

```json
{
  "model": "zai-org/GLM-4.6V",
  "messages": [
    {
      "role": "user",
      "content": [
        {
          "type": "text",
          "text": "Identify all text in this image. For each text block, provide: 1) original text, 2) translation to {target_language}, 3) bounding box coordinates normalized to 0-1000 scale [left, top, right, bottom]. Return strictly in JSON format."
        },
        {
          "type": "image_url",
          "image_url": {
            "url": "data:image/jpeg;base64,/9j/4AAQSkZJRg..."
          }
        }
      ]
    }
  ],
  "temperature": 0.7,
  "max_tokens": 1000,
  "enable_thinking": false
}
```

**请求参数说明:**

| 参数 | 类型 | 必需 | 说明 |
|------|------|------|------|
| model | string | 是 | 模型名称 |
| messages | array | 是 | 消息数组 |
| temperature | number | 否 | 控制响应随机性，默认 0.7 |
| max_tokens | integer | 否 | 最大生成 token 数，默认 1000 |
| enable_thinking | boolean | 否 | 启用思考模式（仅部分模型支持） |
| thinking_budget | integer | 否 | 思考模式最大 token 数，范围 128-32768 |

**Response:** `200 OK`

```json
{
  "id": "019bda85c39aba6a5fccce598dac8587",
  "object": "chat.completion",
  "created": 1768897758,
  "model": "zai-org/GLM-4.6V",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "[{\"original_text\":\"Hello\",\"translated_text\":\"你好\",\"coordinates\":[100,200,300,250]}]",
        "reasoning_content": "..."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 1383,
    "completion_tokens": 205,
    "total_tokens": 1588,
    "completion_tokens_details": {
      "reasoning_tokens": 118
    },
    "prompt_tokens_details": {
      "cached_tokens": 0
    },
    "prompt_cache_hit_tokens": 0,
    "prompt_cache_miss_tokens": 1383
  },
  "system_fingerprint": ""
}
```

**响应参数说明:**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | string | 请求唯一标识 |
| object | string | 对象类型，固定为 "chat.completion" |
| created | number | 创建时间戳 |
| model | string | 实际使用的模型 |
| choices | array | 生成结果数组 |
| choices[].message.content | string | 模型返回的内容 |
| choices[].message.reasoning_content | string | 思考模式内容（部分模型） |
| choices[].finish_reason | string | 结束原因（stop/length） |
| usage | object | Token 使用统计 |
| usage.prompt_tokens | number | 输入 token 数 |
| usage.completion_tokens | number | 输出 token 数 |
| usage.total_tokens | number | 总 token 数 |
| usage.completion_tokens_details.reasoning_tokens | number | 思考模式 token 数 |
| usage.prompt_cache_hit_tokens | number | 缓存命中 token 数 |
| usage.prompt_cache_miss_tokens | number | 缓存未命中 token 数 |

### 4.2 支持的模型

#### VLM (视觉语言模型) - 用于屏幕翻译

| 模型名称 | 推荐场景 |
|----------|----------|
| zai-org/GLM-4.6V | 屏幕翻译（推荐） |
| zai-org/GLM-4.5V | 通用翻译 |
| Qwen/Qwen3-VL-32B-Instruct | 高质量翻译 |
| Qwen/Qwen3-VL-30B-A3B-Instruct | 平衡性能 |
| Qwen/Qwen3-VL-235B-A22B-Instruct | 高精度翻译 |
| Qwen/Qwen2.5-VL-32B-Instruct | 通用场景 |
| Qwen/Qwen2.5-VL-72B-Instruct | 高质量场景 |

#### LLM (纯文本模型) - 用于后续优化

| 模型名称 | 推荐场景 |
|----------|----------|
| Pro/deepseek-ai/DeepSeek-V3 | 推理优化 |
| Pro/deepseek-ai/DeepSeek-V3.1-Terminus | 推理优化 |
| Pro/deepseek-ai/DeepSeek-V3.2 | 推理优化 |
| Pro/zai-org/GLM-4.7 | 文本生成 |
| zai-org/GLM-4.5-Air | 轻量级处理 |
| Pro/moonshotai/Kimi-K2-Instruct-0905 | 长文本处理 |

---

## 5. Data Models

### 5.1 外部 API 请求模型 (硅基流动格式)

**Description:** 发送给硅基流动 API 的请求

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| model | string | Yes | 模型名称，如 "zai-org/GLM-4.6V" |
| messages | array | Yes | 消息数组 |
| messages[].role | string | Yes | "user" |
| messages[].content | array | Yes | 内容数组 |
| temperature | number | No | 温度参数，默认 0.7 |
| max_tokens | number | No | 最大 token 数，默认 1000 |

### 5.2 内部领域模型 (防腐层后)

**Description:** 应用内部使用的标准化模型

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| model | string | Yes | 模型标识符 |
| imageData | string | Yes | Base64 编码的图像 |
| targetLanguage | string | Yes | 目标语言代码 |
| sourceLanguage | string | No | 源语言代码，默认 "auto" |

**Example:**

```kotlin
// 内部请求模型（防腐层）
data class TranslationRequest(
    val model: VlmModel,
    val imageData: String,  // Base64
    val targetLanguage: Language,
    val sourceLanguage: Language = Language.AUTO
)

// VLM 模型枚举
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
    }
}

// 语言枚举
enum class Language(val code: String, val displayName: String) {
    AUTO("auto", "自动识别"),
    EN("en", "English"),
    ZH("zh", "中文"),
    JA("ja", "日本語"),
    KO("ko", "한국어"),
    FR("fr", "Français"),
    DE("de", "Deutsch"),
    ES("es", "Español")
}

// 外部 API 请求模型（硅基流动格式）
data class SiliconFlowRequest(
    val model: String,
    val messages: List<SiliconFlowMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int = 1000
)
```

### 5.3 外部 API 响应模型 (硅基流动格式)

**Description:** 硅基流动 API 返回的原始响应

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| id | string | 请求 ID |
| object | string | 对象类型 |
| created | number | 创建时间戳 |
| model | string | 使用的模型 |
| choices | array | 选择数组 |
| choices[].message.content | string | 模型返回的 JSON 字符串 |
| usage | object | Token 使用情况 |

### 5.4 内部领域模型 (防腐层转换后)

**Description:** 应用内部使用的标准化响应模型

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| results | List\<TranslationResult\> | Yes | 翻译结果列表 |
| resultId | string | Yes | 请求追踪 ID |
| model | string | Yes | 使用的模型标识 |
| usage | TokenUsage | No | Token 使用信息 |

**Example:**

```kotlin
// 内部响应模型（防腐层）
data class TranslationResponse(
    val results: List<TranslationResult>,
    val resultId: String,
    val model: LlmModel,
    val usage: TokenUsage? = null
)

data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val boundingBox: BoundingBox  // 内部坐标模型
)

data class BoundingBox(
    val left: Float,    // 归一化 0-1
    val top: Float,     // 归一化 0-1
    val right: Float,   // 归一化 0-1
    val bottom: Float   // 归一化 0-1
) {
    companion object {
        // 从外部 API 坐标创建（归一化 0-1000 -> 0-1）
        fun fromExternal(coords: List<Int>): BoundingBox {
            require(coords.size == 4) { "坐标必须包含 4 个点" }
            return BoundingBox(
                left = coords[0] / 1000f,
                top = coords[1] / 1000f,
                right = coords[2] / 1000f,
                bottom = coords[3] / 1000f
            )
        }
    }
}
```

### 5.5 ErrorResponse

**Description:** 错误响应格式

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| error | object | 错误详情 |
| error.code | string | 错误代码 |
| error.message | string | 错误信息 |
| error.type | string | 错误类型 |

**Example:**

```json
{
  "error": {
    "code": "invalid_api_key",
    "message": "Incorrect API key provided",
    "type": "invalid_request_error"
  }
}
```

---

## 6. Prompt Engineering

### 6.1 Recommended Prompt

```
Identify all text in this image. For each text block, provide:
1. The original text as it appears
2. The translation to {target_language}
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
```

### 6.2 Prompt Variations

| 场景 | Prompt 调整 |
|------|------------|
| 手写文本 | "This may contain handwritten text" |
| 多语言混合 | "The image may contain text in multiple languages" |
| 表格数据 | "Preserve table structure and relationships" |

---

## 7. Error Codes

### 7.1 HTTP Status Codes

| Code | Meaning | Usage |
|------|---------|-------|
| 200 | OK | 请求成功 |
| 400 | Bad Request | 无效的请求参数 |
| 401 | Unauthorized | API Key 无效 |
| 403 | Forbidden | 权限不足 |
| 404 | Not Found | 端点不存在 |
| 429 | Too Many Requests | 超过限流 |
| 500 | Internal Server Error | 服务器错误 |
| 503 | Service Unavailable | 服务暂时不可用 |

### 7.2 Application Error Codes

| Code | Message | Description |
|------|---------|-------------|
| NO_NETWORK | 无网络连接 | 设备未连接到互联网 |
| API_KEY_MISSING | API Key 未配置 | 用户未配置 API Key |
| CAPTURE_FAILED | 截图失败 | MediaProjection 错误 |
| PARSE_ERROR | 解析失败 | 无法解析 API 响应 |
| COORDINATE_INVALID | 坐标无效 | 返回的坐标格式错误 |

---

## 8. SDKs & Libraries

### 8.1 Official SDKs

| Language | Library | Version | Repository |
|----------|---------|---------|------------|
| Kotlin | OpenAI Kotlin Client | - | github.com/aaalanse/compose-openai-api |
| Kotlin | Ktor Client | 2.3.8 | ktor.io |
| Kotlin | OkHttp | 4.12.0 | square.github.io/okhttp |

### 8.2 Community Libraries

| Language | Library | Repository |
|----------|---------|------------|
| Kotlin | Kotlinx Serialization | github.com/Kotlin/kotlinx.serialization |

---

## 9. Integration Example

### 9.1 Kotlin Implementation

```kotlin
// 使用 Ktor 客户端
class TranslationApiClient(
    private val apiKey: String,
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
) {
    suspend fun translate(
        imageBase64: String,
        targetLanguage: String
    ): TranslationResponse {
        val request = TranslationRequest(
            model = "gpt-4o",
            imageBase64 = imageBase64,
            prompt = buildPrompt(targetLanguage),
            targetLanguage = targetLanguage
        )

        return httpClient.post("https://api.openai.com/v1/chat/completions") {
            headers {
                append("Authorization", "Bearer $apiKey")
                append("Content-Type", "application/json")
            }
            setBody(request)
        }.body()
    }

    private fun buildPrompt(targetLanguage: String): String {
        return """
            Identify all text in this image. For each text block, provide:
            1. The original text
            2. The translation to $targetLanguage
            3. Bounding box coordinates [left, top, right, bottom] normalized to 0-1000

            Return strictly as JSON array.
        """.trimIndent()
    }
}
```

---

## 10. Changelog

### Version 1.1.0 (2025-02-10)

**更新内容**

- 确定使用硅基流动 API 作为提供商
- 添加防腐层设计，隔离外部 API 结构
- 更新内部领域模型定义
- 添加坐标验证和文本排版机制说明
- 确认用户自备 Key 商业模式

### Version 1.0.0 (2025-02-10)

**Initial Release**

- 支持多模态翻译 API 调用
- 定义标准请求/响应格式
- 坐标归一化规范 (0-1000)

---

## Appendix

### A. Glossary

| Term | Definition |
|------|------------|
| Base64 | 二进制到文本编码方案 |
| 归一化坐标 | 将坐标映射到固定范围 (0-1000 或 0-1) |
| Multipart | HTTP 多部分表单数据格式 |

### B. Support & Contact

- **硅基流动文档:** https://docs.siliconflow.cn/docs
- **硅基流动控制台:** https://cloud.siliconflow.cn/
