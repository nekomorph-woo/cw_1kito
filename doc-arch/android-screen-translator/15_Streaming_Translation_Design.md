# 流式翻译方案设计文档

**Document Version:** 1.0
**Last Updated:** 2025-02-11
**Status:** DRAFT

---

## 1. 背景与目标

### 1.1 问题

当前翻译流程是完全串行的：

```
截图 → Base64 编码 → API 调用 (stream=false) → 等待完整响应 → 解析全部 JSON → 一次性渲染覆盖层
```

某些大模型（如 Qwen3-VL-235B）响应需要 30-60 秒，期间用户只能看到悬浮球转圈动画，体验很差。

### 1.2 目标

启用 SSE 流式传输，模型每生成一个完整的翻译结果对象就立刻渲染到屏幕上。
用户在几秒内即可看到第一条翻译，后续结果逐条出现。

### 1.3 共存策略

- **非流式方案（当前）** 作为默认模式，保持不变
- **流式方案** 作为实验室功能，通过设置页面的开关控制
- 开关默认关闭，用户主动开启后才使用流式模式

---

## 2. 整体架构

### 2.1 流式模式数据流

```
截图 → Base64 → API 调用 (stream=true)
                      ↓
              SSE 事件流 (token by token)
                      ↓
              StreamingJsonParser (拼接 token，检测完整 JSON 对象)
                      ↓
              每检测到一个 {...} → 解析为 TranslationResult
                      ↓
              overlayView.addResult() → invalidate() → 屏幕上出现新翻译框
```

### 2.2 非流式模式数据流（当前，保持不变）

```
截图 → Base64 → API 调用 (stream=false)
                      ↓
              等待完整 JSON 响应
                      ↓
              一次性解析所有 TranslationResult
                      ↓
              showOverlay(results) → 一次性渲染所有翻译框
```

### 2.3 模式切换

```
FloatingService.performTranslation()
    ↓
    if (streamingEnabled)  →  performStreamingTranslation()
    else                   →  performNonStreamingTranslation()  // 当前逻辑
```

---

## 3. 模块设计

### 3.1 SSE 流式数据模型

**文件:** `commonMain/.../data/api/SiliconFlowModels.kt`（追加）

SiliconFlow API 兼容 OpenAI 流式格式，每个 SSE chunk 结构：

```kotlin
@Serializable
data class SiliconFlowStreamChunk(
    val id: String,
    val choices: List<SiliconFlowStreamChoice>,
    val model: String? = null
)

@Serializable
data class SiliconFlowStreamChoice(
    val index: Int,
    val delta: SiliconFlowDelta,
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class SiliconFlowDelta(
    val role: String? = null,
    val content: String? = null
)
```

### 3.2 流式 API 接口

**文件:** `commonMain/.../data/api/TranslationApiClient.kt`（追加方法）

```kotlin
interface TranslationApiClient {
    // ... 现有方法保持不变 ...

    /**
     * 流式翻译请求，返回 token 流
     * @return Flow<String> 每个元素是一个 content delta token
     */
    fun translateStream(request: TranslationApiRequest): Flow<String>
}
```

### 3.3 SSE 流式接收实现

**文件:** `commonMain/.../data/api/TranslationApiClientImpl.kt`（追加方法）

核心实现：使用 Ktor 的 `preparePost` + `execute` 模式读取 SSE 字节流。

```kotlin
override fun translateStream(request: TranslationApiRequest): Flow<String> = flow {
    val apiKey = currentApiKey ?: throw AuthError("API Key 未设置")

    val siliconRequest = buildSiliconFlowRequest(request, stream = true)

    client.preparePost(baseUrl) {
        contentType(ContentType.Application.Json)
        header("Authorization", "Bearer $apiKey")
        setBody(siliconRequest)
    }.execute { response ->
        if (response.status != HttpStatusCode.OK) {
            handleErrorResponse(response)
        }

        val channel: ByteReadChannel = response.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            if (!line.startsWith("data: ")) continue
            val data = line.removePrefix("data: ").trim()
            if (data == "[DONE]") break

            val chunk = json.decodeFromString<SiliconFlowStreamChunk>(data)
            val content = chunk.choices.firstOrNull()?.delta?.content
            if (content != null) emit(content)
        }
    }
}.flowOn(Dispatchers.IO)
```

**重构说明：** 将现有 `translate()` 中构建 `SiliconFlowRequest` 的逻辑提取为 `buildSiliconFlowRequest(request, stream)` 私有方法，供两个方法复用。

### 3.4 增量 JSON 解析器（新建）

**文件:** `commonMain/.../data/api/StreamingJsonParser.kt`（新建）

模型输出的是 JSON 数组 `[{...},{...},...]`，但 token 是碎片化的。
使用花括号计数状态机，每检测到一个完整的 `{...}` 就立刻返回。

```kotlin
class StreamingJsonParser {
    private val buffer = StringBuilder()
    private var braceDepth = 0
    private var inString = false
    private var escaped = false
    private var objectStartIndex = -1

    /**
     * 喂入新 token，返回本次检测到的完整 JSON 对象列表
     */
    fun feed(token: String): List<String> {
        val completed = mutableListOf<String>()
        for (char in token) {
            buffer.append(char)
            if (escaped) { escaped = false; continue }
            when {
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                !inString && char == '{' -> {
                    if (braceDepth == 0) objectStartIndex = buffer.length - 1
                    braceDepth++
                }
                !inString && char == '}' -> {
                    braceDepth--
                    if (braceDepth == 0 && objectStartIndex >= 0) {
                        completed.add(buffer.substring(objectStartIndex, buffer.length))
                        objectStartIndex = -1
                    }
                }
            }
        }
        return completed
    }

    fun reset() { /* 重置所有状态 */ }
}
```

**容错能力：**
- 正确处理字符串内的 `{` `}` `"` `\\`
- 忽略 JSON 数组外层的 `[` `]` 和逗号
- 忽略模型可能输出的 markdown 包裹（```json ... ```）和前导文本

### 3.5 覆盖层增量渲染

**文件:** `androidMain/.../service/overlay/TranslationOverlayView.kt`（修改）

将 `results` 从构造参数改为内部可变列表，新增 `addResult()` 方法：

```kotlin
class TranslationOverlayView(
    context: Context,
    initialResults: List<TranslationResult> = emptyList(),  // 可为空
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val onDismiss: () -> Unit
) : View(context) {

    private val results = mutableListOf<TranslationResult>().apply {
        addAll(initialResults)
    }

    /** 增量添加单条结果并触发重绘 */
    fun addResult(result: TranslationResult) {
        results.add(result)
        invalidate()
    }

    /** 增量添加多条结果并触发重绘 */
    fun addResults(newResults: List<TranslationResult>) {
        results.addAll(newResults)
        invalidate()
    }
}
```

**兼容性：** 非流式模式仍然可以通过 `initialResults` 传入完整列表，行为不变。

### 3.6 FloatingService 流式翻译流程

**文件:** `androidMain/.../service/floating/FloatingService.kt`（修改）

```kotlin
private suspend fun performTranslation() {
    val streamingEnabled = configManager.getStreamingEnabled()
    if (streamingEnabled) {
        performStreamingTranslation()
    } else {
        performNonStreamingTranslation()  // 当前逻辑，原封不动
    }
}

private suspend fun performStreamingTranslation() {
    updateLoadingState(STATE_LOADING)
    try {
        val imageBytes = captureScreen()
        val (screenWidth, screenHeight) = getScreenDimensions()
        val request = buildTranslationRequest(imageBytes, screenWidth, screenHeight)

        // 立刻创建空覆盖层
        withContext(Dispatchers.Main) {
            showEmptyOverlay(screenWidth, screenHeight)
        }

        // 流式接收 + 增量解析
        val parser = StreamingJsonParser()
        var resultCount = 0
        var coordinateMode: CoordinateMode = CoordinateMode.UNKNOWN

        apiClient.translateStream(request).collect { token ->
            for (jsonStr in parser.feed(token)) {
                val result = parseOneResult(jsonStr, screenWidth, screenHeight, coordinateMode)
                if (result != null) {
                    // 首条结果锁定坐标模式
                    if (coordinateMode == CoordinateMode.UNKNOWN) {
                        coordinateMode = detectCoordinateMode(jsonStr)
                    }
                    resultCount++
                    withContext(Dispatchers.Main) {
                        overlayView?.addResult(result)
                    }
                }
            }
        }

        updateLoadingState(if (resultCount > 0) STATE_SUCCESS else STATE_ERROR)
    } catch (e: Exception) {
        // 错误处理（同当前逻辑）
        updateLoadingState(STATE_ERROR)
    }
}
```

**坐标系检测策略：** 首条结果锁定模式（0-1000 归一化 vs 像素坐标），后续结果沿用。
同一次 API 调用中模型不会切换坐标系。

### 3.7 实验室设置页面

#### 3.7.1 ConfigManager 新增方法

**文件:** `commonMain/.../data/config/ConfigManager.kt`（追加）

```kotlin
interface ConfigManager {
    // ... 现有方法 ...
    suspend fun getStreamingEnabled(): Boolean
    suspend fun saveStreamingEnabled(enabled: Boolean)
}
```

**文件:** `commonMain/.../data/config/ConfigManagerImpl.kt`（追加）

```kotlin
companion object {
    private const val STREAMING_ENABLED_KEY = "lab_streaming_enabled"
}

override suspend fun getStreamingEnabled(): Boolean {
    return getString(STREAMING_ENABLED_KEY)?.toBoolean() ?: false  // 默认关闭
}

override suspend fun saveStreamingEnabled(enabled: Boolean) {
    saveString(STREAMING_ENABLED_KEY, enabled.toString())
}
```

#### 3.7.2 实验室设置 UI

**文件:** `commonMain/.../ui/screen/LabSettingsScreen.kt`（新建）

```kotlin
@Composable
fun LabSettingsScreen(
    streamingEnabled: Boolean,
    onStreamingEnabledChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("实验室") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { /* 返回图标 */ } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("实验性功能", style = MaterialTheme.typography.titleMedium)
            Text("以下功能仍在测试中，可能不稳定", style = MaterialTheme.typography.bodySmall)

            Spacer(modifier = Modifier.height(16.dp))

            // 流式翻译开关
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("流式翻译")
                    Text(
                        "翻译结果逐条显示，减少等待时间",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = streamingEnabled,
                    onCheckedChange = onStreamingEnabledChange
                )
            }
        }
    }
}
```

#### 3.7.3 SettingsScreen 入口

**文件:** `commonMain/.../ui/screen/SettingsScreen.kt`（修改）

在权限配置区域下方、启动按钮上方添加"实验室"入口：

```kotlin
// 实验室入口
OutlinedButton(
    onClick = { onEvent(SettingsEvent.NavigateToLab) },
    modifier = Modifier.fillMaxWidth()
) {
    Text("🔬 实验室")
}
```

---

## 4. 修改文件清单

| # | 文件路径 | 操作 | 说明 |
|---|---------|------|------|
| 1 | `commonMain/.../data/api/SiliconFlowModels.kt` | 修改 | 追加 3 个流式 chunk 数据类 |
| 2 | `commonMain/.../data/api/TranslationApiClient.kt` | 修改 | 追加 `translateStream()` 接口方法 |
| 3 | `commonMain/.../data/api/TranslationApiClientImpl.kt` | 修改 | 实现 SSE 流式读取 + 提取公共方法 |
| 4 | `commonMain/.../data/api/StreamingJsonParser.kt` | **新建** | 增量 JSON 解析器 |
| 5 | `androidMain/.../service/overlay/TranslationOverlayView.kt` | 修改 | results 改为可变列表 + addResult() |
| 6 | `androidMain/.../service/floating/FloatingService.kt` | 修改 | 新增流式翻译流程 + 模式分支 |
| 7 | `commonMain/.../data/config/ConfigManager.kt` | 修改 | 追加 streaming 配置方法 |
| 8 | `commonMain/.../data/config/ConfigManagerImpl.kt` | 修改 | 实现 streaming 配置存取 |
| 9 | `commonMain/.../ui/screen/LabSettingsScreen.kt` | **新建** | 实验室设置页面 |
| 10 | `commonMain/.../ui/screen/SettingsScreen.kt` | 修改 | 添加实验室入口 |
| 11 | `commonMain/.../MainViewModel.kt` | 修改 | 添加 streaming 状态和导航事件 |

---

## 5. 风险与注意事项

1. **部分成功处理：** 流式传输中途断开时，已渲染的结果保留在屏幕上，Toast 提示"翻译未完成"
2. **Markdown 包裹：** 部分模型会输出 ` ```json ``` ` 包裹，StreamingJsonParser 天然忽略（只追踪 `{}`）
3. **坐标系一致性：** 首条结果锁定坐标模式，避免逐条检测的不一致
4. **线程安全：** `addResult()` 必须在主线程调用，通过 `withContext(Dispatchers.Main)` 保证
5. **Ktor 兼容性：** Ktor 2.3.8 CIO 引擎支持 `ByteReadChannel.readUTF8Line()`，无需升级

---

## 6. 验证方案

1. **开关默认关闭：** 安装后进入设置 → 实验室 → 确认"流式翻译"开关默认 OFF
2. **非流式模式不受影响：** 开关关闭时，翻译行为与当前完全一致
3. **流式模式基本功能：** 开启开关 → 点击悬浮球 → 翻译结果逐条出现在屏幕上
4. **中断恢复：** 流式传输中途网络断开 → 已显示的结果保留 → 错误提示
5. **多模型兼容：** 分别测试 GLM-4.6V 和 Qwen3-VL-32B 的流式输出格式
