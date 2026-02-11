# 流式翻译方案设计文档

**Document Version:** 2.0
**Last Updated:** 2026-02-11
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
- 流式方案的解析逻辑与非流式方案**隔离实现**，仅共用公共基础方法（如请求构建、坐标转换），避免相互影响

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
              每检测到一个 {...} → 解析为 TranslationResult → 销毁已消费的 buffer
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
    if (streamingEnabled)  →  performStreamingTranslation()   // 独立的流式流程
    else                   →  performNonStreamingTranslation() // 当前逻辑，原封不动
```

---

## 3. 模块设计

### 3.1 SSE 流式数据模型

**文件:** `commonMain/.../data/api/SiliconFlowModels.kt`（追加数据类）

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

**注意：** 现有 `SiliconFlowRequest` 已包含 `stream: Boolean = false` 字段，无需修改。流式请求时传入 `stream = true` 即可。

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

**重要约束：** `preparePost + execute` 模式下，response 在 `execute` block 结束后自动关闭，因此 Flow 的所有 emit 操作必须在 block 内完成。
The SSE stream parsing pseudocode is described below (see implementation notes for the actual `translateStream()` method):

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
            // 忽略 SSE 注释行（常用作 keep-alive ping）
            if (line.startsWith(":")) continue
            // 忽略空行和非 data 行
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

**重构说明：** 将现有 `translate()` 中构建 `SiliconFlowRequest` 的逻辑提取为 `buildSiliconFlowRequest(request, stream)` 私有方法，供两个方法复用。其他逻辑（如 `handleErrorResponse`、`buildPrompt`）也由流式与非流式共用。

### 3.4 坐标模式枚举（新建）

**文件:** `commonMain/.../model/CoordinateMode.kt`（新建）

流式解析中，需要在第一条结果到达时判断模型返回的坐标格式，后续结果沿用同一模式。

```kotlin
/**
 * 坐标模式枚举
 *
 * 用于标识大模型返回的坐标系类型。
 * 非流式模式下：解析完所有结果后统一检测（现有逻辑不变）。
 * 流式模式下：首条结果锁定模式，后续沿用。
 */
enum class CoordinateMode {
    /** 尚未检测到，等待首条结果 */
    PENDING,
    /** 0-1000 归一化坐标（GLM 系列常见） */
    NORMALIZED_1000,
    /** 像素坐标（prompt 约定的默认格式） */
    PIXEL
}
```

**检测策略（方案 A 改进版）：**

由于当前 prompt 明确要求模型返回像素坐标（`coordinates must be pixel values`），默认模式设为 `PIXEL`。首条结果到达时做验证性检测：

```kotlin
/**
 * 根据首条结果的坐标值检测坐标模式
 *
 * 策略：默认 PIXEL（prompt 约定），仅当坐标值明显偏小
 * 且屏幕分辨率远大于 1000 时才切换为 NORMALIZED_1000。
 */
fun detectCoordinateMode(
    coords: List<Float>,
    screenWidth: Int,
    screenHeight: Int
): CoordinateMode {
    val maxCoord = coords.maxOrNull() ?: 0f
    return if (maxCoord in 1f..1000f && (screenWidth > 1080 || screenHeight > 1080)) {
        // 坐标值很小但屏幕分辨率远大于 1000，大概率是 0-1000 归一化
        CoordinateMode.NORMALIZED_1000
    } else {
        CoordinateMode.PIXEL
    }
}
```

> **适用范围：** 此检测逻辑仅供流式模式使用。非流式模式继续使用现有的全局 `maxCoord` 检测（`parseTranslationResults` 中），两套逻辑互不影响。

### 3.5 流式单条结果解析（新建）

**文件:** `androidMain/.../service/floating/StreamingResultParser.kt`（新建）

流式模式专用的单条 JSON 对象解析器，与非流式模式的 `parseTranslationResults` 隔离。

```kotlin
/**
 * 流式模式下的单条翻译结果解析器
 *
 * 与非流式模式的 parseTranslationResults() 隔离，
 * 仅共用坐标转换的基础逻辑。
 */
object StreamingResultParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 解析单个 JSON 对象字符串为 TranslationResult
     *
     * @param jsonStr 完整的单个 JSON 对象 (如 {"original_text":..., "coordinates":[...]})
     * @param screenWidth 屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @param mode 坐标模式（PIXEL 或 NORMALIZED_1000）
     * @return 解析后的 TranslationResult，无效时返回 null
     */
    fun parseOne(
        jsonStr: String,
        screenWidth: Int,
        screenHeight: Int,
        mode: CoordinateMode
    ): TranslationResult? {
        return try {
            val raw = json.decodeFromString<JsonTranslationResult>(jsonStr)
            if (raw.coordinates.size < 4) return null

            val (left, top, right, bottom) = convertCoordinates(
                raw.coordinates, screenWidth, screenHeight, mode
            )

            // 过滤无效框
            val boxWidth = right - left
            val boxHeight = bottom - top
            if (boxWidth <= 0 || boxHeight <= 0) return null
            if (boxWidth < 5 || boxHeight < 5) return null

            // 存为归一化 0-1 坐标
            TranslationResult(
                originalText = raw.original_text,
                translatedText = raw.translated_text,
                boundingBox = BoundingBox(
                    left = left.toFloat() / screenWidth,
                    top = top.toFloat() / screenHeight,
                    right = right.toFloat() / screenWidth,
                    bottom = bottom.toFloat() / screenHeight
                )
            )
        } catch (e: Exception) {
            Log.w("StreamingResultParser", "Failed to parse: ${e.message}")
            null
        }
    }

    /**
     * 根据坐标模式转换原始坐标为像素值
     */
    private fun convertCoordinates(
        coords: List<Float>,
        screenWidth: Int,
        screenHeight: Int,
        mode: CoordinateMode
    ): List<Int> {
        val rawLeft = coords[0]
        val rawTop = coords[1]
        val rawRight = coords[2]
        val rawBottom = coords[3]

        return when (mode) {
            CoordinateMode.NORMALIZED_1000 -> listOf(
                (rawLeft / 1000f * screenWidth).toInt().coerceIn(0, screenWidth),
                (rawTop / 1000f * screenHeight).toInt().coerceIn(0, screenHeight),
                (rawRight / 1000f * screenWidth).toInt().coerceIn(0, screenWidth),
                (rawBottom / 1000f * screenHeight).toInt().coerceIn(0, screenHeight)
            )
            else -> listOf(
                rawLeft.toInt().coerceIn(0, screenWidth),
                rawTop.toInt().coerceIn(0, screenHeight),
                rawRight.toInt().coerceIn(0, screenWidth),
                rawBottom.toInt().coerceIn(0, screenHeight)
            )
        }
    }
}
```

### 3.6 增量 JSON 解析器（新建）

**文件:** `commonMain/.../data/api/StreamingJsonParser.kt`（新建）

模型输出的是 JSON 数组 `[{...},{...},...]`，但 token 是碎片化的。
使用花括号计数状态机，每检测到一个完整的 `{...}` 就立刻返回，并**清理已消费的 buffer**。

```kotlin
class StreamingJsonParser {
    private val buffer = StringBuilder()
    private var braceDepth = 0
    private var inString = false
    private var escaped = false
    private var objectStartIndex = -1

    /**
     * 喂入新 token，返回本次检测到的完整 JSON 对象列表。
     * 每提取一个完整对象后立即清理 buffer 中已消费的部分。
     */
    fun feed(token: String): List<String> {
        val completed = mutableListOf<String>()
        for (char in token) {
            buffer.append(char)
            if (escaped) { escaped = false; continue }
            when {
                char == '\' && inString -> escaped = true
                char == '"' -> inString = !inString
                !inString && char == '{' -> {
                    if (braceDepth == 0) objectStartIndex = buffer.length - 1
                    braceDepth++
                }
                !inString && char == '}' -> {
                    braceDepth--
                    if (braceDepth == 0 && objectStartIndex >= 0) {
                        completed.add(buffer.substring(objectStartIndex, buffer.length))
                        // 清理已消费的 buffer，保留未处理部分
                        buffer.delete(0, buffer.length)
                        objectStartIndex = -1
                    }
                }
            }
        }
        return completed
    }

    fun reset() {
        buffer.clear()
        braceDepth = 0
        inString = false
        escaped = false
        objectStartIndex = -1
    }
}
```

**容错能力：**
- 正确处理字符串内的 `{` `}` `"` `\`
- 忽略 JSON 数组外层的 `[` `]` 和逗号
- 忽略模型可能输出的 markdown 包裹（\\）和前导文本
- 每次提取完整对象后清理 buffer，避免内存无限增长

### 3.7 覆盖层增量渲染

**文件:** `androidMain/.../service/overlay/TranslationOverlayView.kt`（修改）

将 `results` 从构造参数改为内部可变列表，新增 `addResult()` 方法。

**当前代码（修改前）：**
```kotlin
class TranslationOverlayView(
    context: Context,
    private val results: List<TranslationResult>,  // 不可变
    ...
)
```

**修改后：**
```kotlin
class TranslationOverlayView(
    context: Context,
    initialResults: List<TranslationResult> = emptyList(),  // 可为空，参数名变更
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val onDismiss: () -> Unit
) : View(context) {

    private val results = mutableListOf<TranslationResult>().apply {
        addAll(initialResults)
    }

    /** 增量添加单条结果并触发重绘（必须在主线程调用） */
    fun addResult(result: TranslationResult) {
        results.add(result)
        invalidate()
    }

    /** 增量添加多条结果并触发重绘（必须在主线程调用） */
    fun addResults(newResults: List<TranslationResult>) {
        results.addAll(newResults)
        invalidate()
    }
}
```

**受影响的调用点：**
- `FloatingService.showOverlay()`（第 667 行）：参数名从 `results` 改为 `initialResults`（Kotlin 命名参数调用需同步修改）
- 非流式模式通过 `initialResults` 传入完整列表，行为不变

### 3.8 FloatingService 流式翻译流程

**文件:** `androidMain/.../service/floating/FloatingService.kt`（修改）

#### 3.8.1 模式分支

将当前 `performTranslation()` 重命名为 `performNonStreamingTranslation()`（内部逻辑原封不动），新增分支入口：

```kotlin
private suspend fun performTranslation() {
    val streamingEnabled = configManager.getStreamingEnabled()
    if (streamingEnabled) {
        performStreamingTranslation()
    } else {
        performNonStreamingTranslation()  // 当前逻辑，原封不动
    }
}
```

> **配置读取方式：** `configManager` 是 `AndroidConfigManagerImpl` 实例，已在 `onCreate()` 中初始化。`getStreamingEnabled()` 直接从 SharedPreferences 读取持久化配置，无需通过 Intent extras 传递。

#### 3.8.2 流式翻译主流程

```kotlin
/** 当前流式翻译的 Job，用于取消 */
private var streamingJob: Job? = null

private suspend fun performStreamingTranslation() {
    updateLoadingState(STATE_LOADING)

    streamingJob = serviceScope.launch {
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
            var coordinateMode = CoordinateMode.PENDING

            apiClient.translateStream(request).collect { token ->
                for (jsonStr in parser.feed(token)) {
                    // 首条结果：先检测坐标模式，再解析
                    if (coordinateMode == CoordinateMode.PENDING) {
                        val raw = Json.decodeFromString<JsonTranslationResult>(jsonStr)
                        coordinateMode = detectCoordinateMode(
                            raw.coordinates, screenWidth, screenHeight
                        )
                    }

                    val result = StreamingResultParser.parseOne(
                        jsonStr, screenWidth, screenHeight, coordinateMode
                    )
                    if (result != null) {
                        resultCount++
                        withContext(Dispatchers.Main) {
                            overlayView?.addResult(result)
                        }
                    }
                }
            }

            updateLoadingState(if (resultCount > 0) STATE_SUCCESS else STATE_ERROR)
        } catch (e: Exception) {
            Log.e(TAG, "Streaming translation failed", e)
            // 已渲染的覆盖层保留在屏幕上
            when (e) {
                is ApiException.NetworkError ->
                    Toast.makeText(this@FloatingService, "网络异常: ${e.message}", Toast.LENGTH_SHORT).show()
                is ApiException.AuthError ->
                    Toast.makeText(this@FloatingService, "认证错误: 请检查 API Key", Toast.LENGTH_LONG).show()
                is kotlinx.coroutines.CancellationException ->
                    Log.d(TAG, "Streaming cancelled by user")
                else ->
                    Toast.makeText(this@FloatingService, "翻译失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            // 悬浮球显示红叉（与非流式异常交互一致）
            updateLoadingState(STATE_ERROR)
        }
    }
}
```

#### 3.8.3 空覆盖层创建

```kotlin
/**
 * 创建空覆盖层（流式模式专用）
 * 后续通过 addResult() 逐条填充内容
 */
private fun showEmptyOverlay(screenWidth: Int, screenHeight: Int) {
    hideOverlay()

    overlayView = TranslationOverlayView(
        context = this,
        initialResults = emptyList(),
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        onDismiss = {
            // 用户点击覆盖层 → 取消流式传输 + 关闭覆盖层
            streamingJob?.cancel()
            hideOverlay()
        }
    )

    // WindowManager params 与 showOverlay() 相同
    val params = createOverlayLayoutParams(screenWidth, screenHeight)

    try {
        windowManager.addView(overlayView, params)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to add empty overlay", e)
    }
}
```

#### 3.8.4 公共方法提取

从现有 `showOverlay()` 中提取 `WindowManager.LayoutParams` 构建逻辑为公共方法：

```kotlin
/**
 * 创建覆盖层的 WindowManager.LayoutParams（流式和非流式共用）
 */
private fun createOverlayLayoutParams(screenWidth: Int, screenHeight: Int): WindowManager.LayoutParams {
    return WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        width = screenWidth
        height = screenHeight
    }
}
```

#### 3.8.5 流式取消处理

用户点击覆盖层时触发 dismiss：
1. `streamingJob?.cancel()` 取消协程 → Flow 收集中断 → Ktor channel 关闭
2. `hideOverlay()` 移除覆盖层
3. 协程 `catch` 块捕获 `CancellationException`，不显示错误提示

#### 3.8.6 流式中途异常处理

网络断开或其他异常时：
1. **已渲染的覆盖层保留在屏幕上**，用户仍可查看已显示的翻译结果
2. **悬浮球显示红叉**（`STATE_ERROR`），与当前非流式异常的交互逻辑一致
3. **Toast 提示具体错误**（网络异常 / 认证错误等）

### 3.9 实验室设置页面

#### 3.9.1 ConfigManager 新增方法

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
    private const val CUSTOM_PROMPT_KEY = "custom_prompt"
    private const val STREAMING_ENABLED_KEY = "lab_streaming_enabled"
}

override suspend fun getStreamingEnabled(): Boolean {
    return getString(STREAMING_ENABLED_KEY)?.toBoolean() ?: false  // 默认关闭
}

override suspend fun saveStreamingEnabled(enabled: Boolean) {
    saveString(STREAMING_ENABLED_KEY, enabled.toString())
}
```

#### 3.9.2 实验室设置 UI

**文件:** `commonMain/.../ui/screen/LabSettingsScreen.kt`（新建）

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
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
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
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

#### 3.9.3 SettingsScreen 入口

**文件:** `commonMain/.../ui/screen/SettingsScreen.kt`（修改）

在 TopAppBar 右上角添加实验室图标按钮：

```kotlin
TopAppBar(
    title = { Text("Kito 设置") },
    actions = {
        IconButton(onClick = { onEvent(SettingsEvent.NavigateToLab) }) {
            Icon(Icons.Filled.Science, contentDescription = "实验室")
        }
    },
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
)
```

**注意：** `Icons.Filled.Science` 需要引入 `androidx.compose.material.icons.extended` 依赖。如该依赖过大，可替换为 `Icons.Filled.Settings` 或自定义图标。

#### 3.9.4 导航方案

**文件:** `commonMain/.../ui/screen/MainScreen.kt`（修改）

当前 `MainScreen` 是 `SettingsScreen` 的薄封装。改为状态驱动的双屏切换：

```kotlin
@Composable
fun MainScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var showLabSettings by remember { mutableStateOf(false) }

    if (showLabSettings) {
        LabSettingsScreen(
            streamingEnabled = uiState.streamingEnabled,
            onStreamingEnabledChange = { onEvent(SettingsEvent.StreamingEnabledChanged(it)) },
            onNavigateBack = { showLabSettings = false }
        )
    } else {
        SettingsScreen(
            uiState = uiState,
            onEvent = { event ->
                if (event is SettingsEvent.NavigateToLab) {
                    showLabSettings = true
                } else {
                    onEvent(event)
                }
            },
            modifier = modifier
        )
    }
}
```

#### 3.9.5 SettingsEvent 与 SettingsUiState 扩展

**文件:** `commonMain/.../ui/screen/SettingsScreen.kt`（修改）

```kotlin
// SettingsUiState 新增字段
data class SettingsUiState(
    // ... 现有字段 ...
    val streamingEnabled: Boolean = false   // 新增
)

// SettingsEvent 新增事件
sealed class SettingsEvent {
    // ... 现有事件 ...
    data object NavigateToLab : SettingsEvent()                           // 新增
    data class StreamingEnabledChanged(val enabled: Boolean) : SettingsEvent()  // 新增
}
```

#### 3.9.6 MainViewModel 扩展

**文件:** `commonMain/.../MainViewModel.kt`（修改）

```kotlin
// loadSavedConfig() 中追加：
val streamingEnabled = configManager.getStreamingEnabled()
_uiState.update { it.copy(streamingEnabled = streamingEnabled) }

// onEvent() 中追加分支：
is SettingsEvent.StreamingEnabledChanged -> {
    _uiState.update { it.copy(streamingEnabled = event.enabled) }
    viewModelScope.launch {
        try {
            configManager.saveStreamingEnabled(event.enabled)
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "保存流式配置失败", e)
        }
    }
}
SettingsEvent.NavigateToLab -> {
    // 导航由 MainScreen 内部状态处理，ViewModel 无需操作
}
```

---

## 4. 修改文件清单

| # | 文件路径 | 操作 | 说明 |
|---|---------|------|------|
| 1 | `commonMain/.../data/api/SiliconFlowModels.kt` | 修改 | 追加 3 个流式 chunk 数据类（SiliconFlowStreamChunk/StreamChoice/Delta） |
| 2 | `commonMain/.../data/api/TranslationApiClient.kt` | 修改 | 追加 `translateStream()` 接口方法 |
| 3 | `commonMain/.../data/api/TranslationApiClientImpl.kt` | 修改 | 实现 SSE 流式读取 + 提取 `buildSiliconFlowRequest()` 公共方法 |
| 4 | `commonMain/.../data/api/StreamingJsonParser.kt` | **新建** | 增量 JSON 解析器（花括号状态机 + buffer 清理） |
| 5 | `commonMain/.../model/CoordinateMode.kt` | **新建** | 坐标模式枚举 + 检测函数 |
| 6 | `androidMain/.../service/floating/StreamingResultParser.kt` | **新建** | 流式模式专用单条结果解析器 |
| 7 | `androidMain/.../service/overlay/TranslationOverlayView.kt` | 修改 | results 改为可变列表 + addResult()；参数名 results → initialResults |
| 8 | `androidMain/.../service/floating/FloatingService.kt` | 修改 | 新增流式翻译流程 + 模式分支 + streamingJob 取消 + showEmptyOverlay + createOverlayLayoutParams 公共方法 |
| 9 | `commonMain/.../data/config/ConfigManager.kt` | 修改 | 追加 getStreamingEnabled / saveStreamingEnabled 接口方法 |
| 10 | `commonMain/.../data/config/ConfigManagerImpl.kt` | 修改 | 实现 streaming 配置存取 + 追加 STREAMING_ENABLED_KEY |
| 11 | `commonMain/.../ui/screen/LabSettingsScreen.kt` | **新建** | 实验室设置页面 |
| 12 | `commonMain/.../ui/screen/SettingsScreen.kt` | 修改 | TopAppBar 添加实验室图标；SettingsUiState 追加 streamingEnabled；SettingsEvent 追加 NavigateToLab / StreamingEnabledChanged |
| 13 | `commonMain/.../ui/screen/MainScreen.kt` | 修改 | 状态驱动的双屏切换（SettingsScreen ↔ LabSettingsScreen） |
| 14 | `commonMain/.../MainViewModel.kt` | 修改 | 加载 streamingEnabled 配置 + 处理 StreamingEnabledChanged / NavigateToLab 事件 |

---

## 5. 风险与注意事项

1. **流式中途异常处理：** 网络断开时，已渲染的覆盖层保留在屏幕上，悬浮球显示红叉（与非流式异常交互一致），Toast 提示具体错误
2. **Markdown 包裹：** 部分模型会输出 \\ 包裹，StreamingJsonParser 天然忽略（只追踪 `{}`）
3. **坐标系一致性：** 首条结果锁定坐标模式（默认 PIXEL），避免逐条检测的不一致
4. **线程安全：** `addResult()` 必须在主线程调用，通过 `withContext(Dispatchers.Main)` 保证
5. **Ktor 兼容性：** Ktor 2.3.8 CIO 引擎支持 `ByteReadChannel.readUTF8Line()`，`preparePost + execute` 模式下 response 在 block 结束后自动关闭，无需升级
6. **流式取消：** 用户点击覆盖层时取消 streamingJob，协程中 CancellationException 不显示错误提示
7. **隔离性：** 流式解析（StreamingResultParser + StreamingJsonParser）与非流式解析（parseTranslationResults）完全隔离，开关切换不影响对方

---

## 6. 验证方案

1. **开关默认关闭：** 安装后进入设置 → 右上角实验室图标 → 确认"流式翻译"开关默认 OFF
2. **非流式模式不受影响：** 开关关闭时，翻译行为与当前完全一致
3. **流式模式基本功能：** 开启开关 → 点击悬浮球 → 翻译结果逐条出现在屏幕上
4. **用户取消：** 流式传输进行中 → 点击覆盖层 → 流式停止 + 覆盖层关闭
5. **中途异常：** 流式传输中途网络断开 → 已显示的结果保留 → 悬浮球显示红叉 → Toast 错误提示
6. **多模型兼容：** 分别测试 GLM-4.6V（可能返回 0-1000 坐标）和 Qwen3-VL-32B（像素坐标）的流式输出格式
7. **导航流程：** 设置首页 → 右上角图标进入实验室 → 切换开关 → 返回键回到设置首页 → 开关状态保持
