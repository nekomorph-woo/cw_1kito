# CLAUDE.md - cw_1Kito 项目指南

> **项目概述:** Kotlin Multiplatform 全局屏幕翻译应用
> **架构模式:** 单栈 (Kotlin/Android)
> **UI 框架:** Compose Multiplatform + Material3
> **创建日期:** 2025-02-10
> **最后更新:** 2025-02-12

---

## 项目概述

这是一个基于 Kotlin Multiplatform Compose 的 Android 应用，实现全局屏幕翻译功能。用户可以通过悬浮窗快速截图，云端大模型进行 OCR 识别和翻译，然后在屏幕上以覆盖层形式展示翻译结果。

### 核心功能
1. **多语言翻译** - 支持中/英/日/韩等多种语言互译
2. **全局悬浮窗** - 系统级浮动图标，可拖拽、长按关闭、双击关闭覆盖层
3. **屏幕截图翻译** - MediaProjection API 截图 + 多模态大模型 OCR
4. **流式翻译** - 支持逐条显示翻译结果，减少等待时间
5. **覆盖层绘制** - 在原位置绘制白色背景框 + 翻译文本
6. **语言配置** - 源语言（自动/指定）和目标语言设置
7. **主题定制** - 6 种色调 × 3 种明暗模式 = 18 种主题方案
8. **文本合并** - 可选功能，将位置靠近的文本合并到同一对象

---

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| **语言** | Kotlin | 2.3.0 |
| **框架** | Compose Multiplatform | 1.10.0 |
| **UI 库** | Material3 | 1.10.0-alpha05 |
| **生命周期** | Lifecycle ViewModel Compose | 2.9.6 |
| **网络** | Ktor Client | 2.3.8 |
| **序列化** | Kotlinx Serialization | 1.6.2 |
| **日志** | Timber | 5.0.1 |
| **权限** | Accompanist Permissions | 0.36.0 |
| **本地 OCR** | Google ML Kit Text Recognition | 19.0.0 |
| **本地翻译** | Google ML Kit Translation | 19.0.0 |
| **构建** | Gradle (Kotlin DSL) | - |
| **目标平台** | Android (API 24-36) | - |
| **JVM 目标** | Java 11 | - |

---

## 项目结构

```
cw_1Kito/
├── composeApp/
│   └── src/
│       ├── commonMain/              # 跨平台共享代码
│       │   └── kotlin/com/cw2/cw_1kito/
│       │       ├── model/            # 数据模型 (Language, VlmModel, TranslationResult, BoundingBox)
│       │       ├── ui/              # Compose UI 组件
│       │       │   ├── screen/      # 页面组件 (MainScreen, SettingsScreen, LabSettingsScreen)
│       │       │   ├── component/   # 通用组件 (LanguageSelector, ModelSelector, ApiKeySection)
│       │       │   └── theme/       # 主题系统 (Color.kt, Theme.kt, AppTheme.kt)
│       │       ├── data/            # 数据层
│       │       │   ├── api/         # API 客户端 (TranslationApiClient, StreamingJsonParser)
│       │       │   ├── adapter/     # API 适配器 (SiliconFlowAdapter)
│       │       │   └── config/      # 配置管理 (ConfigManager)
│       │       ├── domain/          # 领域层
│       │       │   ├── coordinate/  # 坐标验证 (CoordinateValidator)
│       │       │   ├── layout/       # 文本排版引擎 (TextLayoutEngine)
│       │       │   └── translation/ # 翻译管理器 (TranslationManager)
│       │       └── permission/      # 权限管理接口
│       ├── androidMain/             # Android 特定代码
│       │   └── kotlin/com/cw2/cw_1kito/
│       │       ├── service/
│       │       │   ├── capture/      # 屏幕捕获 (ScreenCaptureManager, ScreenCaptureImpl)
│       │       │   ├── floating/     # 悬浮窗服务 (FloatingService, FloatingBallView)
│       │       │   └── overlay/      # 翻译覆盖层 (TranslationOverlayView, OverlayRenderer)
│       │       ├── data/config/    # Android 配置实现 (AndroidConfigManagerImpl)
│       │       └── permission/     # Android 权限实现 (PermissionManagerImpl)
│       └── androidMain/res/         # Android 资源
├── gradle/                          # Gradle 配置
│   └── libs.versions.toml           # 版本目录 (Dep Version Catalog)
├── build.gradle.kts                 # 根项目构建配置
└── settings.gradle.kts              # 项目设置
```

### 目录约定
- `commonMain/kotlin/model/` - 数据模型（Language, VlmModel, TranslationResult, BoundingBox, ThemeConfig）
- `commonMain/kotlin/ui/` - Compose UI 组件和主题
- `commonMain/kotlin/data/` - 数据层（API 客户端、配置管理）
- `commonMain/kotlin/domain/` - 领域层（坐标验证、文本排版、翻译管理）
- `androidMain/kotlin/service/` - Service 实现、权限处理、MediaProjection、系统 API
- `androidMain/kotlin/permission/` - Android 权限管理实现
- `androidMain/res/` - Android 资源

---

## 开发规范

### Kotlin 惯用法

#### 1. 协程 (Coroutines)
- 优先使用 `CoroutineScope` 管理异步操作
- 网络请求、图像处理等耗时操作使用 `suspend` 函数
- UI 状态更新使用 `rememberCoroutineScope()` 或 `lifecycleScope`

```kotlin
// 推荐：使用挂起函数处理网络请求
suspend fun translateImage(bitmap: Bitmap): TranslationResult {
    return withContext(Dispatchers.IO) {
        apiClient.translate(bitmap)
    }
}

// 在 Compose 中调用
val scope = rememberCoroutineScope()
scope.launch {
    val result = translateImage(bitmap)
    // 更新 UI 状态
}
```

#### 2. Data Class
- 使用 `data class` 定义数据模型，简洁且自动生成 equals/hashCode/toString
- 使用 `@Serializable` 注解支持序列化

```kotlin
@Serializable
data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val boundingBox: BoundingBox
)

@Serializable
data class BoundingBox(
    val left: Float,   // 0.0 - 1.0 归一化坐标
    val top: Float,    // 0.0 - 1.0
    val right: Float,  // 0.0 - 1.0
    val bottom: Float  // 0.0 - 1.0
)
```

#### 3. 空安全 (Null Safety)
- 明确处理可空类型，避免 NPE
- 优先使用不可空类型，仅在必要时使用 `?`

```kotlin
// 不推荐：过度使用可空
fun processResult(result: TranslationResult?) { ... }

// 推荐：明确非空，或提供默认值
fun processResult(result: TranslationResult) { ... }
```

#### 4. 扩展函数
- 为常用操作创建扩展函数，提高代码可读性

```kotlin
// 扩展函数：归一化坐标转屏幕坐标
fun BoundingBox.toScreenRect(screenWidth: Int, screenHeight: Int): Rect {
    return Rect(
        (left * screenWidth).toInt(),
        (top * screenHeight).toInt(),
        (right * screenWidth).toInt(),
        (bottom * screenHeight).toInt()
    )
}
```

#### 5. 类型别名
- 为复杂类型创建别名，提高可读性

```kotlin
typealias CoordinateQuad = List<Float>  // [x1, y1, x2, y2]
typealias TranslationResults = List<TranslationResult>
```

---

### Compose 最佳实践

#### 1. 状态管理 (State Management)
- 使用 `remember` + `mutableStateOf` 管理局部状态
- 复杂状态使用 `ViewModel` + `StateFlow`
- 状态提升 (State Hoisting)：将状态提升到父组件

```kotlin
@Composable
fun TranslationScreen(viewModel: TranslationViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    TranslationContent(
        uiState = uiState,
        onStartTranslation = { viewModel.startTranslation() }
    )
}
```

#### 2. 副作用处理 (Side Effects)
- 使用 `LaunchedEffect` 处理一次性事件
- 使用 `rememberCoroutineScope` 处理用户触发的协程
- 使用 `DisposableEffect` 处理资源清理

```kotlin
LaunchedEffect(Unit) {
    // 初始化时执行一次
    viewModel.loadInitialConfig()
}

DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_DESTROY) {
            // 清理资源
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
    }
}
```

#### 3. 组件设计
- 遵循单一职责原则，每个组件专注一件事
- 使用 `@Composable` 函数而不是类
- 参数优先使用 `Modifier`，遵循良好顺序

```kotlin
@Composable
fun TranslationOverlay(
    results: List<TranslationResult>,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    // 组件实现
}
```

#### 4. 预览 (Preview)
- 为 UI 组件添加 `@Preview` 注解，便于快速迭代

```kotlin
@Composable
@Preview(showBackground = true)
fun TranslationOverlayPreview() {
    MaterialTheme {
        TranslationOverlay(
            results = listOf(
                TranslationResult("Hello", "你好", BoundingBox(0.1f, 0.1f, 0.5f, 0.2f))
            )
        )
    }
}
```

---

## Android 特定规范

### 本地 OCR 引擎 (Google ML Kit)

#### 依赖配置
```kotlin
// gradle/libs.versions.toml
mlkit = "19.0.0"

// composeApp/build.gradle.kts
implementation(libs.google.mlkit.text.recognition)
implementation(libs.google.mlkit.translation)
```

#### ML Kit Text Recognition 使用
```kotlin
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MLKitOCRManager(context: Context) : IOcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(bitmap: Bitmap): List<OcrDetection> {
        return suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val detections = result.textBlocks.map { block ->
                        OcrDetection(
                            text = block.text,
                            boundingBox = convertToBoundingBox(block.boundingBox),
                            confidence = block.confidence ?: 0f,
                            angle = 0f
                        )
                    }
                    continuation.resume(detections)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }
}
```

#### ML Kit Translation 使用
```kotlin
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class MLKitTranslator(context: Context) : ILocalTranslationEngine {
    private var translator: Translator? = null

    suspend fun downloadModel(sourceLanguage: String, targetLanguage: String) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.fromLanguageTag(sourceLanguage)!!)
            .setTargetLanguage(TranslateLanguage.fromLanguageTag(targetLanguage)!!)
            .build()

        translator = Translation.getClient(options)
        translator?.downloadModelIfNeeded()
    }

    override suspend fun translate(text: String, targetLanguage: String): String {
        return translator?.translate(text) ?: text
    }
}
```

---

### 悬浮窗 (Floating Window)

#### 权限声明
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

#### 悬浮窗 Service 实现
```kotlin
// FloatingService.kt - 完整实现已存在于项目
class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatingView: FloatingBallView? = null
    private var overlayView: TranslationOverlayView? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        configManager = AndroidConfigManagerImpl(applicationContext)
        ScreenCaptureManager.init(this)
        createNotificationChannel()
    }

    private fun addFloatingView() {
        floatingView = FloatingBallView(this, windowManager).apply {
            setOnClickListener { onFloatingBallClicked() }
            setOnLongClickListener { onFloatingBallLongPressed() }
        }
        val params = createFloatingLayoutParams()
        windowManager.addView(floatingView, params)
    }
}
```

#### 悬浮球特性
- **拖拽**: 支持自由拖拽，释放后自动吸附到左右边缘
- **长按**: 长按关闭悬浮窗服务
- **状态动画**: 空闲(呼吸)、加载(脉冲+旋转)、成功(绿色对勾)、错误(红色叉号)
- **双层阴影**: 外阴影 + 内发光效果

---

### 屏幕截图 (MediaProjection)

#### 权限声明
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
```

#### 截图流程
```kotlin
// 1. 初始化 ScreenCaptureManager
ScreenCaptureManager.init(context)

// 2. 请求截图权限（在 Activity 中）
val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
val intent = projectionManager.createScreenCaptureIntent()
startActivityForResult(intent, SCREEN_CAPTURE_REQUEST_CODE)

// 3. 在 onActivityResult 中获取 MediaProjection
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
        ScreenCaptureManager.setMediaProjection(resultCode, data)
    }
}

// 4. 执行截图
val result = ScreenCaptureManager.captureScreen()
when (result) {
    is CaptureResult.Success -> {
        val imageBytes = result.imageBytes
        // 处理截图数据
    }
    is CaptureResult.PermissionDenied -> {
        // 权限过期，需要重新授权
    }
}
```

---

## 网络层设计

### API 抽象（支持多种 LLM）

```kotlin
interface TranslationApiClient {
    fun setApiKey(apiKey: String)
    fun getApiKey(): String?
    suspend fun translate(request: TranslationApiRequest): SiliconFlowResponse
    suspend fun validateApiKey(apiKey: String): Boolean
    fun translateStream(request: TranslationApiRequest): Flow<String>
}
```

### 流式翻译
- 使用 Ktor 的 `preparePost` + `execute` 获取 SSE 流
- `StreamingJsonParser` 使用状态机增量解析 JSON 对象
- 支持实时增量渲染到覆盖层

```kotlin
// 流式翻译流程
val parser = StreamingJsonParser()
apiClient.translateStream(request).collect { token ->
    for (jsonStr in parser.feed(token)) {
        val result = parseOneResult(jsonStr)
        withContext(Dispatchers.Main) {
            overlayView?.addResult(result)  // 增量添加
        }
    }
}
```

---

## 坐标系统规范

### 坐标模式检测
项目支持多种坐标模式，自动检测：

```kotlin
enum class CoordinateMode {
    PENDING,           // 等待判断
    NORMALIZED_1000,   // 0-1000 归一化（GLM 系列）
    PIXEL              // 像素坐标
}

// 自动检测
fun detectCoordinateMode(coords: List<Int>, imageWidth: Int, imageHeight: Int): CoordinateMode {
    val maxVal = coords.max()
    return when {
        maxVal <= 1000 -> CoordinateMode.NORMALIZED_1000
        else -> CoordinateMode.PIXEL
    }
}
```

### 坐标存储
- 内部统一使用 **归一化 0-1 坐标**存储
- 绘制时转换为实际屏幕像素

```kotlin
// 像素转归一化（存储）
val boundingBox = BoundingBox(
    left = pixelLeft / screenWidth.toFloat(),
    top = pixelTop / screenHeight.toFloat(),
    right = pixelRight / screenWidth.toFloat(),
    bottom = pixelBottom / screenHeight.toFloat()
)

// 归一化转像素（绘制）
val rect = boundingBox.toScreenRect(screenWidth, screenHeight)
```

---

## 主题系统

### 主题配置
```kotlin
data class ThemeConfig(
    val hue: ThemeHue = ThemeHue.DEFAULT,
    val darkMode: DarkModeOption = DarkModeOption.FOLLOW_SYSTEM
) {
    companion object {
        val DEFAULT = ThemeConfig()
    }
}

enum class ThemeHue(val displayName: String, val seedColor: Color) {
    DEFAULT("默认蓝", Color(0xFF4285F4)),
    TEAL("青色", Color(0xFF006A6A)),
    VIOLET("紫罗兰", Color(0xFF7B5EA7)),
    ROSE("玫瑰", Color(0xFFB4637A)),
    FOREST("森林", Color(0xFF4A7C59)),
    AMBER("琥珀", Color(0xFFC77B00))
}

enum class DarkModeOption(val displayName: String) {
    FOLLOW_SYSTEM("跟随系统"),
    ALWAYS_LIGHT("浅色"),
    ALWAYS_DARK("深色")
}
```

### 主题应用
```kotlin
@Composable
fun KitoTheme(
    themeConfig: ThemeConfig = ThemeConfig.DEFAULT,
    content: @Composable () -> Unit
) {
    val isDark = when (themeConfig.darkMode) {
        DarkModeOption.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        DarkModeOption.ALWAYS_LIGHT -> false
        DarkModeOption.ALWAYS_DARK -> true
    }
    val colorScheme = getColorScheme(themeConfig.hue, isDark)
    MaterialTheme(colorScheme = colorScheme, content = content)
}
```

---

## VLM 模型支持

```kotlin
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
```

---

## 实验室功能

### 流式翻译
- 逐条显示翻译结果，减少等待时间
- 通过 `StreamingJsonParser` 增量解析 SSE 响应
- 覆盖层支持 `addResult()` 动态添加

### 文本合并
- 将位置靠近的文本合并到同一对象
- 使用专门的 Prompt 模板 (`DEFAULT_MERGING_PROMPT`)
- 通过 `TranslationConfig.useMergingPrompt` 控制

### 主题定制
- 6 种色调（默认蓝、青色、紫罗兰、玫瑰、森林、琥珀）
- 3 种明暗模式（跟随系统、浅色、深色）
- 18 种主题组合，支持预览

---

## 翻译覆盖层

### 覆盖层视图
```kotlin
class TranslationOverlayView(
    context: Context,
    initialResults: List<TranslationResult>,
    screenWidth: Int,
    screenHeight: Int,
    onDismiss: () -> Unit
) : View(context) {
    // 支持增量添加（流式模式）
    fun addResult(result: TranslationResult)

    // 双击关闭
    override fun onTouchEvent(event: MotionEvent): Boolean
}
```

### 绘制引擎
- `OverlayRenderer`: 负责实际绘制逻辑
- 支持自适应字号、多行排版
- 使用 `TextLayoutEngine` 计算文本布局

---

## Git 配置

### 推荐的 .gitattributes
```
# 强制使用 LF 换行符
*.kt text eol=lf
*.xml text eol=lf
*.md text eol=lf
.gradle text eol=lf
```

### .gitignore (确保包含)
```
.gradle/
build/
captures/
local.properties
.idea/
*.iml
.cxx/
```

---

## 命令速查

### 构建和运行
```bash
# 构建 Debug APK
./gradlew :composeApp:assembleDebug

# 安装到设备
./gradlew :composeApp:installDebug

# 清理构建
./gradlew clean
```

---

## 注意事项

1. **性能**: 截图 + 网络请求存在延迟，需添加加载状态提示
2. **电池优化**: 引导用户关闭电池优化，防止服务被杀
3. **坐标准确性**: 大模型返回的坐标可能有偏差，已实现自动检测和转换
4. **文字排版**: 翻译后文本长度变化，使用 `TextLayoutEngine` 自适应排版
5. **隐私保护**: 截图数据不持久化存储
6. **权限过期**: MediaProjection token 只能使用一次，权限过期后需重新授权
7. **API 34+**: 需要指定前台服务类型 (`FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION | SPECIAL_USE`)

---

> 本文档由 Code Cock 自动生成和维护，最后同步于 2025-02-12。
