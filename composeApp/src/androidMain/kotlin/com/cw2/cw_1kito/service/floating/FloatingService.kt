package com.cw2.cw_1kito.service.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import com.cw2.cw_1kito.data.api.ApiException
import com.cw2.cw_1kito.data.api.TranslationApiClient
import com.cw2.cw_1kito.data.api.TranslationApiClientImpl
import com.cw2.cw_1kito.data.api.TranslationApiRequest
import com.cw2.cw_1kito.data.config.AndroidConfigManagerImpl
import com.cw2.cw_1kito.model.BoundingBox
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.TranslationResult
import com.cw2.cw_1kito.model.VlmModel
import com.cw2.cw_1kito.service.capture.ScreenCaptureManager
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

/**
 * 悬浮窗前台服务
 *
 * 管理悬浮球和翻译覆盖层的显示
 */
class FloatingService : Service() {

    companion object {
        private const val TAG = "FloatingService"

        // Actions
        const val ACTION_START = "com.cw2.cw_1kito.ACTION_START"
        const val ACTION_STOP = "com.cw2.cw_1kito.ACTION_STOP"
        const val ACTION_SHOW_OVERLAY = "com.cw2.cw_1kito.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.cw2.cw_1kito.ACTION_HIDE_OVERLAY"

        // Extras
        const val EXTRA_RESULTS = "results"
        const val EXTRA_SCREEN_WIDTH = "screen_width"
        const val EXTRA_SCREEN_HEIGHT = "screen_height"

        // Loading states
        const val STATE_IDLE = 0
        const val STATE_LOADING = 1
        const val STATE_SUCCESS = 2
        const val STATE_ERROR = 3

        // Notification
        private const val CHANNEL_ID = "screen_translation_channel"
        private const val NOTIFICATION_ID = 1001

        /**
         * 启动服务
         */
        fun start(context: Context) {
            val intent = Intent(context, FloatingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, FloatingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val binder = LocalBinder()
    private lateinit var windowManager: WindowManager
    private var floatingView: FloatingBallView? = null
    private var overlayView: com.cw2.cw_1kito.service.overlay.TranslationOverlayView? = null

    // 协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // API 客户端
    private val apiClient = TranslationApiClientImpl()

    // 配置管理器（延迟初始化，因为 Service 初始化时 Context 还未准备好）
    private lateinit var configManager: AndroidConfigManagerImpl

    inner class LocalBinder : Binder() {
        fun getService(): FloatingService = this@FloatingService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingService onCreate")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // 初始化配置管理器（现在 applicationContext 可用了）
        configManager = AndroidConfigManagerImpl(applicationContext)
        // 初始化 ScreenCaptureManager（如果还未初始化）
        ScreenCaptureManager.init(this)
        // 监听权限过期事件
        ScreenCaptureManager.setOnPermissionExpiredListener {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(this, "录屏权限已过期，请重新授权", Toast.LENGTH_LONG).show()
            }
        }
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingService onDestroy")
        ScreenCaptureManager.setOnPermissionExpiredListener(null)
        serviceScope.cancel()
        removeFloatingView()
        hideOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                startAsForeground()
                addFloatingView()
            }
            ACTION_STOP -> {
                ScreenCaptureManager.release()
                removeFloatingView()
                hideOverlay()
                stopForeground(true)
                stopSelf()
            }
            ACTION_SHOW_OVERLAY -> {
                val results = intent.getParcelableArrayListExtra<ParcelableTranslationResult>(EXTRA_RESULTS)
                val width = intent.getIntExtra(EXTRA_SCREEN_WIDTH, 0)
                val height = intent.getIntExtra(EXTRA_SCREEN_HEIGHT, 0)
                if (results != null && width > 0 && height > 0) {
                    showOverlay(results.map { it.toTranslationResult() }, width, height)
                }
            }
            ACTION_HIDE_OVERLAY -> {
                hideOverlay()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "屏幕翻译",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮窗服务正在运行"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 启动前台服务
     */
    private fun startAsForeground() {
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            // API 34+ 需要指定前台服务类型
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 33 需要指定类型（但常量定义在 API 34）
            @Suppress("NewApi")
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, com.cw2.cw_1kito.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("屏幕翻译")
            .setContentText("悬浮窗已启用，点击返回设置")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
    }

    /**
     * 添加悬浮球视图
     */
    private fun addFloatingView() {
        if (floatingView != null) {
            Log.d(TAG, "Floating view already exists")
            return
        }

        floatingView = FloatingBallView(this, windowManager).apply {
            setOnClickListener {
                Log.d(TAG, "Floating ball clicked")
                onFloatingBallClicked()
            }
            setOnLongClickListener {
                Log.d(TAG, "Floating ball long pressed, stopping service")
                onFloatingBallLongPressed()
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        try {
            windowManager.addView(floatingView, params)
            Log.d(TAG, "Floating view added at x=${params.x}, y=${params.y}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view", e)
            Toast.makeText(this, "无法显示悬浮窗，请检查权限", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 移除悬浮球视图
     */
    private fun removeFloatingView() {
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove floating view", e)
            }
            floatingView = null
        }
    }

    /**
     * 悬浮球点击事件 - 执行截图翻译
     */
    private fun onFloatingBallClicked() {
        Log.d(TAG, "Floating ball clicked, starting translation process")

        serviceScope.launch {
            performTranslation()
        }
    }

    /**
     * 悬浮球长按事件 - 关闭悬浮窗服务
     */
    private fun onFloatingBallLongPressed() {
        Toast.makeText(this, "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
        // 释放截图资源并清除权限状态（MediaProjection token 已消耗，必须重新授权）
        ScreenCaptureManager.release()
        // 走关闭流程
        removeFloatingView()
        hideOverlay()
        stopForeground(true)
        stopSelf()
    }

    /**
     * 执行翻译流程
     */
    private suspend fun performTranslation() {
        updateLoadingState(STATE_LOADING)

        try {
            // 1. 截图
            val imageBytes = captureScreen()
            Log.d(TAG, "Screenshot captured: ${imageBytes.size} bytes")

            // 2. 获取屏幕真实全屏尺寸（含状态栏和导航栏，与截图坐标系一致）
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels

            // 3. 调用 API 进行翻译
            val results = translateImage(imageBytes, screenWidth, screenHeight)
            Log.d(TAG, "Translation completed: ${results.size} results")

            if (results.isEmpty()) {
                Toast.makeText(this, "翻译异常：未能解析翻译结果", Toast.LENGTH_SHORT).show()
                updateLoadingState(STATE_ERROR)
                return
            }

            // 4. 显示翻译覆盖层
            showOverlay(results, screenWidth, screenHeight)

            updateLoadingState(STATE_SUCCESS)
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            when (e) {
                is ApiException.NetworkError -> {
                    Toast.makeText(this, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                is ApiException.AuthError -> {
                    Toast.makeText(this, "认证错误: 请检查 API Key", Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(this, "翻译失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            updateLoadingState(STATE_ERROR)
        }
    }

    /**
     * 截取屏幕
     */
    private suspend fun captureScreen(): ByteArray {
        return withContext(Dispatchers.IO) {
            // 检查是否有录屏权限
            if (!ScreenCaptureManager.hasPermission()) {
                Log.w(TAG, "Screen capture permission not granted")
                // 跳转 MainActivity 让用户重新授权
                launchMainActivityForReAuth()
                throw ApiException.AuthError("录屏权限已过期，请重新授权")
            }

            // 使用 ScreenCaptureManager 截图
            val result = ScreenCaptureManager.captureScreen()

            when (result) {
                is com.cw2.cw_1kito.service.capture.CaptureResult.Success -> {
                    Log.d(TAG, "Screenshot captured: ${result.imageBytes.size} bytes, ${result.width}x${result.height}")
                    result.imageBytes
                }
                is com.cw2.cw_1kito.service.capture.CaptureResult.PermissionDenied -> {
                    // 权限过期，跳转 MainActivity 让用户重新授权
                    launchMainActivityForReAuth()
                    throw ApiException.AuthError("录屏权限已过期，请重新授权")
                }
                is com.cw2.cw_1kito.service.capture.CaptureResult.Error -> {
                    throw ApiException.NetworkError(Exception(result.message))
                }
            }
        }
    }

    /**
     * 跳转 MainActivity 让用户重新授权录屏权限
     */
    private fun launchMainActivityForReAuth() {
        try {
            val intent = Intent(this, com.cw2.cw_1kito.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("request_screen_capture", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch MainActivity for re-auth", e)
        }
    }

    /**
     * 调用翻译 API
     */
    private suspend fun translateImage(
        imageBytes: ByteArray,
        screenWidth: Int,
        screenHeight: Int
    ): List<TranslationResult> {
        // 获取 API Key 并设置到 API 客户端
        val apiKey = configManager.getApiKey()
        if (apiKey.isNullOrEmpty()) {
            throw ApiException.AuthError("请先在设置中配置 API Key")
        }
        apiClient.setApiKey(apiKey)

        // 获取语言配置
        val langConfig = configManager.getLanguageConfig()
        val targetLanguage = langConfig.targetLanguage

        // 获取模型配置
        val model = configManager.getSelectedModel()

        // 获取自定义提示词
        val customPrompt = configManager.getCustomPrompt()

        // 将图片转换为 Base64
        val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        Log.d(TAG, "Base64 image size: ${base64Image.length} characters")

        // 创建 API 请求
        val request = TranslationApiRequest(
            model = model,
            imageData = base64Image,
            targetLanguage = targetLanguage,
            temperature = 0.7,
            maxTokens = 4000,
            imageWidth = screenWidth,
            imageHeight = screenHeight,
            customPrompt = customPrompt
        )

        // 打印请求信息
        Log.d(TAG, "=== API Request ===")
        Log.d(TAG, "API Key: ${apiKey.take(10)}...")
        Log.d(TAG, "Model: ${request.model.id}")
        Log.d(TAG, "Source Language: ${langConfig.sourceLanguage.displayName}")
        Log.d(TAG, "Target Language: ${request.targetLanguage.displayName}")
        Log.d(TAG, "Image Data (first 100 chars): ${base64Image.take(100)}...")

        // 调用 API
        val response = apiClient.translate(request)

        // 打印响应信息
        Log.d(TAG, "=== API Response ===")
        Log.d(TAG, "Response ID: ${response.id}")
        Log.d(TAG, "Model: ${response.model}")
        Log.d(TAG, "Usage: ${response.usage}")
        Log.d(TAG, "Choices: ${response.choices.size}")

        val content = response.choices.firstOrNull()?.message?.content
        if (content != null) {
            Log.d(TAG, "Content (first 500 chars): ${content.take(500)}...")
        } else {
            Log.w(TAG, "No content in response")
        }

        // 解析翻译结果
        val results = parseTranslationResults(content, screenWidth, screenHeight)
        Log.d(TAG, "Parsed ${results.size} translation results")

        results.forEachIndexed { index, result ->
            Log.d(TAG, "Result[$index]:")
            Log.d(TAG, "  Original: ${result.originalText}")
            Log.d(TAG, "  Translated: ${result.translatedText}")
            Log.d(TAG, "  BoundingBox: ${result.boundingBox}")
        }

        return results
    }

    /**
     * 从响应内容中提取纯 JSON 文本
     * 处理 markdown 代码块、多余空白等格式
     */
    private fun extractJsonFromContent(content: String): String {
        var text = content.trim()

        // 移除 markdown 代码块: ```json ... ``` 或 ``` ... ```
        val codeBlockRegex = """```(?:json)?\s*\n?([\s\S]*?)\n?\s*```""".toRegex()
        val codeBlockMatch = codeBlockRegex.find(text)
        if (codeBlockMatch != null) {
            text = codeBlockMatch.groupValues[1].trim()
        }

        // 如果还不是以 [ 开头，尝试找到第一个 [ 到最后一个 ] 的范围
        if (!text.startsWith("[")) {
            val start = text.indexOf('[')
            val end = text.lastIndexOf(']')
            if (start >= 0 && end > start) {
                text = text.substring(start, end + 1)
            }
        }

        return text
    }

    /**
     * 解析翻译结果
     */
    private fun parseTranslationResults(
        content: String?,
        screenWidth: Int,
        screenHeight: Int
    ): List<TranslationResult> {
        if (content.isNullOrEmpty()) {
            Log.w(TAG, "Content is null or empty")
            return emptyList()
        }

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            allowTrailingComma = true
        }

        // 提取纯 JSON
        val jsonText = extractJsonFromContent(content)
        Log.d(TAG, "Extracted JSON (first 500 chars): ${jsonText.take(500)}")

        try {
            val jsonResults = json.decodeFromString<List<JsonTranslationResult>>(jsonText)
            Log.d(TAG, "Parsed ${jsonResults.size} raw results from JSON")

            // 自适应坐标系检测：
            // 如果所有坐标值都 <= 1000，说明模型使用了 0-1000 归一化（GLM 系列常见）
            // 否则视为像素坐标
            val allCoords = jsonResults.flatMap { it.coordinates }
            val maxCoord = allCoords.maxOrNull() ?: 0f
            val isNormalized1000 = maxCoord <= 1000f && maxCoord > 0f

            if (isNormalized1000) {
                Log.d(TAG, "Detected 0-1000 normalized coordinates (maxCoord=$maxCoord), scaling to ${screenWidth}x${screenHeight}")
            } else {
                Log.d(TAG, "Detected pixel coordinates (maxCoord=$maxCoord)")
            }

            return jsonResults.mapNotNull { jsonResult ->
                if (jsonResult.coordinates.size < 4) {
                    Log.w(TAG, "Skipping result with insufficient coordinates: ${jsonResult.original_text}")
                    return@mapNotNull null
                }

                // 根据检测到的坐标系进行转换
                val rawLeft = jsonResult.coordinates[0]
                val rawTop = jsonResult.coordinates[1]
                val rawRight = jsonResult.coordinates[2]
                val rawBottom = jsonResult.coordinates[3]

                val left: Int
                val top: Int
                val right: Int
                val bottom: Int

                if (isNormalized1000) {
                    // 0-1000 归一化 → 像素
                    left = (rawLeft / 1000f * screenWidth).toInt().coerceIn(0, screenWidth)
                    top = (rawTop / 1000f * screenHeight).toInt().coerceIn(0, screenHeight)
                    right = (rawRight / 1000f * screenWidth).toInt().coerceIn(0, screenWidth)
                    bottom = (rawBottom / 1000f * screenHeight).toInt().coerceIn(0, screenHeight)
                } else {
                    // 像素坐标直接使用
                    left = rawLeft.toInt().coerceIn(0, screenWidth)
                    top = rawTop.toInt().coerceIn(0, screenHeight)
                    right = rawRight.toInt().coerceIn(0, screenWidth)
                    bottom = rawBottom.toInt().coerceIn(0, screenHeight)
                }

                val boxWidth = right - left
                val boxHeight = bottom - top

                // 过滤无效框
                if (boxWidth <= 0 || boxHeight <= 0) {
                    Log.d(TAG, "Skipping inverted box: [$left,$top,$right,$bottom] for '${jsonResult.original_text}'")
                    return@mapNotNull null
                }
                if (boxWidth < 5 || boxHeight < 5) {
                    Log.d(TAG, "Skipping too small box: ${boxWidth}x${boxHeight} for '${jsonResult.original_text}'")
                    return@mapNotNull null
                }

                Log.d(TAG, "Accepted: '${jsonResult.original_text.take(20)}' -> [$left,$top,$right,$bottom] (${boxWidth}x${boxHeight})")

                // 存为归一化 0-1 坐标
                TranslationResult(
                    originalText = jsonResult.original_text,
                    translatedText = jsonResult.translated_text,
                    boundingBox = BoundingBox(
                        left = left.toFloat() / screenWidth,
                        top = top.toFloat() / screenHeight,
                        right = right.toFloat() / screenWidth,
                        bottom = bottom.toFloat() / screenHeight
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse translation JSON: ${e.message}")
            Log.e(TAG, "Raw content: $content")
            return emptyList()
        }
    }

    /**
     * 更新加载状态
     */
    private fun updateLoadingState(state: Int) {
        floatingView?.setLoadingState(state)
    }

    /**
     * 显示覆盖层
     */
    private fun showOverlay(
        results: List<TranslationResult>,
        screenWidth: Int,
        screenHeight: Int
    ) {
        hideOverlay()

        overlayView = com.cw2.cw_1kito.service.overlay.TranslationOverlayView(
            context = this,
            results = results,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            onDismiss = {
                hideOverlay()
            }
        )

        val params = WindowManager.LayoutParams(
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
            // 使用全屏尺寸，确保覆盖状态栏和导航栏
            width = screenWidth
            height = screenHeight
        }

        try {
            windowManager.addView(overlayView, params)
            Log.d(TAG, "Overlay view added with ${results.size} results")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
        }
    }

    /**
     * 隐藏覆盖层
     */
    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove overlay view", e)
            }
            overlayView = null
        }
    }
}

/**
 * JSON 翻译结果格式
 */
@kotlinx.serialization.Serializable
data class JsonTranslationResult(
    val original_text: String,
    val translated_text: String,
    val coordinates: List<Float>
)

/**
 * 用于传递的 Parcelable TranslationResult
 */
data class ParcelableTranslationResult(
    val originalText: String,
    val translatedText: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) : android.os.Parcelable {
    constructor(parcel: android.os.Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat()
    )

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
        parcel.writeString(originalText)
        parcel.writeString(translatedText)
        parcel.writeFloat(left)
        parcel.writeFloat(top)
        parcel.writeFloat(right)
        parcel.writeFloat(bottom)
    }

    override fun describeContents(): Int = 0

    fun toTranslationResult() = com.cw2.cw_1kito.model.TranslationResult(
        originalText = originalText,
        translatedText = translatedText,
        boundingBox = com.cw2.cw_1kito.model.BoundingBox(
            left = left,
            top = top,
            right = right,
            bottom = bottom
        )
    )

    companion object CREATOR : android.os.Parcelable.Creator<ParcelableTranslationResult> {
        override fun createFromParcel(parcel: android.os.Parcel): ParcelableTranslationResult {
            return ParcelableTranslationResult(parcel)
        }

        override fun newArray(size: Int): Array<ParcelableTranslationResult?> {
            return arrayOfNulls(size)
        }

        fun from(result: com.cw2.cw_1kito.model.TranslationResult): ParcelableTranslationResult {
            return ParcelableTranslationResult(
                originalText = result.originalText,
                translatedText = result.translatedText,
                left = result.boundingBox.left,
                top = result.boundingBox.top,
                right = result.boundingBox.right,
                bottom = result.boundingBox.bottom
            )
        }
    }
}
