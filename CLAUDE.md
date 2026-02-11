# CLAUDE.md - cw_1Kito 项目指南

> **项目概述:** Kotlin Multiplatform 全局屏幕翻译应用
> **架构模式:** 单栈 (Kotlin/Android)
> **UI 框架:** Compose Multiplatform + Material3
> **创建日期:** 2025-02-10

---

## 项目概述

这是一个基于 Kotlin Multiplatform Compose 的 Android 应用，实现全局屏幕翻译功能。用户可以通过悬浮窗快速截图，云端大模型进行 OCR 识别和翻译，然后在屏幕上以覆盖层形式展示翻译结果。

### 核心功能
1. **多语言翻译** - 支持中/英/日/韩等多种语言互译
2. **全局悬浮窗** - 系统级浮动图标，可拖拽
3. **屏幕截图翻译** - MediaProjection API 截图 + 多模态大模型 OCR
4. **覆盖层绘制** - 在原位置绘制白色背景框 + 翻译文本
5. **语言配置** - 源语言（自动/指定）和目标语言设置

---

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| **语言** | Kotlin | 2.3.0 |
| **框架** | Compose Multiplatform | 1.10.0 |
| **UI 库** | Material3 | 1.10.0-alpha05 |
| **生命周期** | Lifecycle ViewModel Compose | 2.9.6 |
| **构建** | Gradle (Kotlin DSL) | - |
| **目标平台** | Android (API 24+) | - |
| **JVM 目标** | Java 11 | - |

---

## 项目结构

```
cw_1Kito/
├── composeApp/
│   └── src/
│       ├── commonMain/              # 跨平台共享代码
│       │   └── kotlin/              # Compose UI、业务逻辑
│       ├── androidMain/             # Android 特定代码
│       │   ├── kotlin/              # Service、权限、平台 API
│       │   └── res/                 # Android 资源
│       └── commonTest/              # 共享测试代码
├── gradle/                          # Gradle 配置
│   └── libs.versions.toml           # 版本目录 (Dep Version Catalog)
├── build.gradle.kts                 # 根项目构建配置
└── settings.gradle.kts              # 项目设置
```

### 目录约定
- `commonMain/kotlin/` - UI 组件、ViewModel、数据模型、业务逻辑
- `androidMain/kotlin/` - Service 实现、权限处理、MediaProjection、系统 API
- `commonMain/kotlin/core/` - 核心业务模块（如需要）
- `commonMain/kotlin/ui/` - Compose UI 组件
- `commonMain/kotlin/data/` - 数据模型和 Repository

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

```kotlin
data class TranslationResult(
    val originalText: String,
    val translatedText: String,
    val coordinates: List<Float>  // [x1, y1, x2, y2] 归一化坐标 0-1
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
fun List<Float>.toScreenCoords(width: Int, height: Int): Rect {
    require(this.size == 4) { "坐标必须包含 4 个点" }
    return Rect(
        (this[0] * width).toInt(),
        (this[1] * height).toInt(),
        (this[2] * width).toInt(),
        (this[3] * height).toInt()
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
                TranslationResult("Hello", "你好", listOf(0.1f, 0.1f, 0.5f, 0.2f))
            )
        )
    }
}
```

---

## Android 特定规范

### 悬浮窗 (Floating Window)

#### 权限声明
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

#### 检查和请求权限
```kotlin
// 检查悬浮窗权限
fun hasOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}

// 请求悬浮窗权限（需要引导用户到系统设置）
fun requestOverlayPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    context.startActivity(intent)
}
```

#### 悬浮窗 Service 实现
```kotlin
// 在 androidMain/kotlin/ 中实现
class FloatingService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        addFloatingView()
    }

    private fun addFloatingView() {
        floatingView = createFloatingView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(floatingView, params)
    }
}
```

---

### 屏幕截图 (MediaProjection)

#### 权限声明
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
```

#### 截图流程
```kotlin
// 1. 请求截图权限
val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
val intent = projectionManager.createScreenCaptureIntent()
startActivityForResult(intent, SCREEN_CAPTURE_REQUEST_CODE)

// 2. 在 onActivityResult 中获取 MediaProjection
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
        val mediaProjection = projectionManager.getMediaProjection(resultCode, data!!)
        startCapture(mediaProjection)
    }
}

// 3. 创建 VirtualDisplay 并捕获画面
private fun startCapture(mediaProjection: MediaProjection) {
    val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
    val virtualDisplay = mediaProjection.createVirtualDisplay(
        "ScreenCapture",
        width, height, density,
        VIRTUAL_DISPLAY_FLAGS,
        imageReader.surface,
        null, null
    )
}
```

---

## 网络层设计

### API 抽象（保持灵活，支持多种 LLM）

```kotlin
// 定义通用接口，便于切换不同 LLM 提供商
interface TranslationApi {
    suspend fun translateWithOCR(
        imageBytes: ByteArray,
        sourceLanguage: Language,
        targetLanguage: Language
    ): List<TranslationResult>
}

enum class Language(val code: String, val displayName: String) {
    AUTO("auto", "自动识别"),
    ENGLISH("en", "English"),
    CHINESE("zh", "中文"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어");
}
```

### 网络库
- 使用 Ktor 或 OkHttp 进行 HTTP 请求
- 使用 Kotlinx Serialization 进行 JSON 解析

```kotlin
// build.gradle.kts 依赖
dependencies {
    implementation("io.ktor:ktor-client-core:2.3.8")
    implementation("io.ktor:ktor-client-cio:2.3.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}
```

---

## 坐标系统规范

### 坐标归一化
- 大模型返回的坐标应为**归一化坐标** (0.0 - 1.0)
- 绘制时需转换为实际屏幕像素

```kotlin
// 归一化坐标转屏幕坐标
data class NormalizedRect(
    val left: Float,   // 0.0 - 1.0
    val top: Float,    // 0.0 - 1.0
    val right: Float,  // 0.0 - 1.0
    val bottom: Float  // 0.0 - 1.0
) {
    fun toScreenRect(screenWidth: Int, screenHeight: Int): Rect {
        return Rect(
            (left * screenWidth).toInt(),
            (top * screenHeight).toInt(),
            (right * screenWidth).toInt(),
            (bottom * screenHeight).toInt()
        )
    }
}
```

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

## 开发流程建议

### 阶段 1: MVP 验证
1. 创建主界面（语言选择、启动按钮）
2. 实现图片选择 + API 调用（验证 Prompt 效果）
3. 简单的绘制层展示翻译结果

### 阶段 2: 悬浮窗
1. 实现 FloatingService
2. 添加悬浮球（可拖拽）
3. 处理权限检查和引导

### 阶段 3: 截图集成
1. 集成 MediaProjection
2. 实现截图和图像预处理
3. 连接网络层

### 阶段 4: 覆盖层绘制
1. 坐标转换逻辑
2. 自适应文字排版
3. 性能优化

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
3. **坐标准确性**: 大模型返回的坐标可能有偏差，需测试验证
4. **文字排版**: 翻译后文本长度变化，需考虑字体自适应或框体扩展
5. **隐私保护**: 截图数据需妥善处理，不应在本地持久化存储

---

> 本文档由 Code Cock 自动生成，如有问题请根据项目实际情况调整。
