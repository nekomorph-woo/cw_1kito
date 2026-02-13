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
import com.cw2.cw_1kito.data.api.StreamingJsonParser
import com.cw2.cw_1kito.data.api.TranslationApiClient
import com.cw2.cw_1kito.data.api.TranslationApiClientImpl
import com.cw2.cw_1kito.data.api.TranslationApiRequest
import com.cw2.cw_1kito.data.config.AndroidConfigManagerImpl
import com.cw2.cw_1kito.engine.DependencyStatus
import com.cw2.cw_1kito.engine.TranslationDependencyChecker
import com.cw2.cw_1kito.engine.merge.TextMergerEngine
import com.cw2.cw_1kito.engine.ocr.IOcrEngine
import com.cw2.cw_1kito.engine.ocr.MLKitOCRManager
import com.cw2.cw_1kito.engine.ocr.OcrEngineFactory
import com.cw2.cw_1kito.engine.translation.TranslationManager
import com.cw2.cw_1kito.engine.translation.BatchTranslationManagerFactory
import com.cw2.cw_1kito.engine.translation.local.MLKitTranslator
import com.cw2.cw_1kito.engine.translation.remote.SiliconFlowClient
import com.cw2.cw_1kito.engine.translation.remote.SiliconFlowLLMClient
import com.cw2.cw_1kito.model.BoundingBox
import com.cw2.cw_1kito.model.CoordinateMode
import com.cw2.cw_1kito.model.Language
import com.cw2.cw_1kito.model.MergedText
import com.cw2.cw_1kito.model.MergingConfig
import com.cw2.cw_1kito.model.ModelPoolConfig
import com.cw2.cw_1kito.model.OcrDetection
import com.cw2.cw_1kito.model.PerformanceMode
import com.cw2.cw_1kito.model.TranslationMode
import com.cw2.cw_1kito.model.TranslationResult
import com.cw2.cw_1kito.model.VlmModel
import com.cw2.cw_1kito.service.capture.ScreenCaptureManager
import com.cw2.cw_1kito.util.Logger
import com.cw2.cw_1kito.util.PerformanceMetrics
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
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

    // 当前流式翻译的 Job，用于取消
    private var streamingJob: Job? = null

    // 协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // API 客户端（云端 VLM 翻译）
    private val apiClient = TranslationApiClientImpl()

    // 配置管理器（延迟初始化，因为 Service 初始化时 Context 还未准备好）
    private lateinit var configManager: AndroidConfigManagerImpl

    // ==================== 本地 OCR 翻译组件 ====================

    /**
     * 本地 OCR 引擎（Google ML Kit Text Recognition v2）
     */
    private var ocrEngine: IOcrEngine? = null

    /**
     * 文本合并引擎（TextMerger）
     */
    private var textMerger: TextMergerEngine? = null

    /**
     * 翻译管理器（统一本地/云端翻译）
     */
    private var translationManager: TranslationManager? = null

    /**
     * 本地翻译引擎（ML Kit）
     */
    private var localTranslator: MLKitTranslator? = null

    /**
     * 云端翻译引擎（SiliconFlow）
     */
    private var remoteTranslator: SiliconFlowClient? = null

    /**
     * 云端 LLM 翻译引擎（SiliconFlowLLM）
     * 用于批量翻译管理器
     */
    private var cloudLlmTranslator: SiliconFlowLLMClient? = null

    /**
     * 翻译依赖检查器
     */
    private var dependencyChecker: TranslationDependencyChecker? = null

    /**
     * 批量翻译管理器（延迟创建）
     */
    private var batchTranslationManager: com.cw2.cw_1kito.engine.translation.IBatchTranslationManager? = null

    /**
     * 配置变更监听 Job
     */
    private var configChangesJob: kotlinx.coroutines.Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): FloatingService = this@FloatingService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingService onCreate")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        // 使用单例配置管理器，确保与 MainViewModel 共享同一个实例
        // 这样配置变更事件可以正确传递
        if (com.cw2.cw_1kito.data.config.ConfigManagerProvider.isInitialized()) {
            configManager = com.cw2.cw_1kito.data.config.ConfigManagerProvider.get()
            Logger.d("[FloatingService] 使用共享的 ConfigManager 实例")
        } else {
            // 如果尚未初始化（不应该发生），创建新实例
            configManager = AndroidConfigManagerImpl(applicationContext)
            Logger.w("[FloatingService] ConfigManagerProvider 未初始化，创建新实例")
        }
        // 初始化 ScreenCaptureManager（如果还未初始化）
        ScreenCaptureManager.init(this)
        // 监听权限过期事件
        ScreenCaptureManager.setOnPermissionExpiredListener {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(this, "录屏权限已过期，请重新授权", Toast.LENGTH_LONG).show()
            }
        }
        createNotificationChannel()

        // 初始化本地 OCR 翻译组件
        initializeLocalOcrComponents()

        // 监听配置变更
        startConfigChangesListener()
    }

    /**
     * 启动配置变更监听
     *
     * 监听配置变更事件，当模型池配置变更时更新 BatchTranslationManager。
     */
    private fun startConfigChangesListener() {
        configChangesJob = serviceScope.launch {
            configManager.configChanges.collect { change ->
                when (change) {
                    is com.cw2.cw_1kito.data.config.ConfigChange.ModelPoolConfigChanged -> {
                        Logger.d("[FloatingService] 收到模型池配置变更事件: ${change.config.models.map { it.displayName }}")
                        // 更新现有的 BatchTranslationManager 配置
                        val manager = batchTranslationManager
                        if (manager != null) {
                            (manager as? com.cw2.cw_1kito.engine.translation.BatchTranslationManagerImpl)?.updateModelPoolConfig(change.config)
                            Logger.d("[FloatingService] BatchTranslationManager 模型池配置已更新")
                        } else {
                            Logger.d("[FloatingService] BatchTranslationManager 尚未创建，配置将在首次翻译时加载")
                        }
                    }
                    // 可以根据需要监听其他配置变更
                    else -> {
                        // 忽略其他配置变更
                    }
                }
            }
        }
    }

    /**
     * 停止配置变更监听
     */
    private fun stopConfigChangesListener() {
        configChangesJob?.cancel()
        configChangesJob = null
    }

    /**
     * 初始化本地 OCR 翻译组件
     */
    private fun initializeLocalOcrComponents() {
        try {
            Logger.d("[FloatingService] 正在初始化本地 OCR 翻译组件...")

            // 1. 创建 OCR 引擎（延迟加载，首次使用时初始化）
            ocrEngine = OcrEngineFactory.create(applicationContext)

            // 2. 创建文本合并引擎
            textMerger = TextMergerEngine()

            // 3. 创建本地翻译引擎
            localTranslator = MLKitTranslator(applicationContext)

            // 4. 创建云端翻译引擎
            remoteTranslator = SiliconFlowClient(configManager)

            // 4.5. 创建云端 LLM 翻译引擎（用于批量翻译）
            cloudLlmTranslator = SiliconFlowLLMClient(configManager)

            // 5. 创建翻译管理器
            translationManager = TranslationManager(
                configManager = configManager,
                localEngine = localTranslator,
                remoteEngine = remoteTranslator
            )

            // 6. 创建依赖检查器
            dependencyChecker = TranslationDependencyChecker(
                configManager = configManager,
                localTranslationEngine = localTranslator!!
            )

            Logger.d("[FloatingService] 本地 OCR 翻译组件创建成功")
        } catch (e: Exception) {
            Logger.e(e, "[FloatingService] 本地 OCR 翻译组件初始化失败")
            Toast.makeText(this, "本地翻译组件初始化失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingService onDestroy")
        ScreenCaptureManager.setOnPermissionExpiredListener(null)
        stopConfigChangesListener()
        serviceScope.cancel()
        removeFloatingView()
        hideOverlay()

        // 释放本地 OCR 翻译组件
        releaseLocalOcrComponents()
    }

    /**
     * 释放本地 OCR 翻译组件
     */
    private fun releaseLocalOcrComponents() {
        try {
            Logger.d("[FloatingService] 正在释放本地 OCR 翻译组件...")

            // 1. 释放翻译管理器
            translationManager?.release()
            translationManager = null

            // 2. 释放本地翻译引擎
            localTranslator?.release()
            localTranslator = null

            // 3. 释放云端翻译引擎
            remoteTranslator?.release()
            remoteTranslator = null

            // 3.5. 释放云端 LLM 翻译引擎
            cloudLlmTranslator?.release()
            cloudLlmTranslator = null

            // 4. 释放 OCR 引擎
            ocrEngine?.release()
            ocrEngine = null

            // 5. 清空文本合并引擎
            textMerger = null

            // 6. 清空依赖检查器
            dependencyChecker = null

            // 7. 清空批量翻译管理器
            batchTranslationManager = null

            Logger.d("[FloatingService] 本地 OCR 翻译组件已释放")
        } catch (e: Exception) {
            Logger.e(e, "[FloatingService] 释放本地 OCR 翻译组件失败")
        }
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
     * 执行翻译流程（入口，根据配置选择翻译方案）
     *
     * ## 方案选择逻辑
     * 1. **VLM 云端方案** (useLocalOcrScheme = false)
     *    - 使用云端 VLM 一体化完成 OCR 和翻译
     *    - 根据流式翻译开关选择流式/非流式
     *
     * 2. **本地 OCR 方案** (useLocalOcrScheme = true)
     *    - 本地 OCR 识别文本
     *    - 根据 localOcrTranslationMode 选择翻译方式：
     *      - LOCAL: 使用 ML Kit 本地翻译
     *      - REMOTE: 使用 SiliconFlow 云端翻译
     *      - HYBRID: 优先本地，失败时降级到云端
     */
    private suspend fun performTranslation() {
        // 1. 依赖检查
        val depStatus = dependencyChecker?.checkDependencies()
        if (depStatus !is DependencyStatus.Satisfied) {
            handleMissingDependency(depStatus)
            return
        }

        // 2. 读取翻译方案配置
        val useLocalOcrScheme = configManager.getUseLocalOcrScheme()

        Logger.d("[FloatingService] 当前翻译方案: ${if (useLocalOcrScheme) "本地 OCR 方案" else "VLM 云端方案"}")

        if (useLocalOcrScheme) {
            // 本地 OCR 方案
            performLocalOcrSchemeTranslation()
        } else {
            // VLM 云端方案
            val streamingEnabled = configManager.getStreamingEnabled()
            if (streamingEnabled) {
                performStreamingTranslation()
            } else {
                performNonStreamingTranslation()
            }
        }
    }

    /**
     * 处理缺失依赖
     *
     * 根据依赖检查结果显示相应的提示信息或引导界面
     *
     * @param status 依赖检查结果
     */
    private fun handleMissingDependency(status: DependencyStatus?) {
        when (status) {
            is DependencyStatus.MissingApiKey -> {
                // 显示 API Key 配置引导
                Logger.d("[FloatingService] 缺少 API Key: ${status.message}")
                Toast.makeText(this, status.message, Toast.LENGTH_LONG).show()
                // 可选：跳转到设置页面
                launchMainActivityForSettings()
            }
            is DependencyStatus.MissingLanguagePack -> {
                // 显示语言包下载引导
                Logger.d("[FloatingService] 缺少语言包: ${status.message}")
                Toast.makeText(
                    this,
                    "${status.message}\n${status.action}",
                    Toast.LENGTH_LONG
                ).show()
                // 可选：跳转到语言包管理页面
                launchMainActivityForSettings()
            }
            is DependencyStatus.MissingPermission -> {
                // 提示权限缺失
                Logger.d("[FloatingService] 缺少权限: ${status.message}")
                Toast.makeText(this, status.message, Toast.LENGTH_LONG).show()
            }
            is DependencyStatus.UnsupportedLanguage -> {
                // 提示语言不支持
                Logger.d("[FloatingService] 不支持的语言: ${status.message}")
                Toast.makeText(
                    this,
                    "${status.message}\n${status.action}",
                    Toast.LENGTH_LONG
                ).show()
            }
            is DependencyStatus.MultipleMissing -> {
                // 多个依赖缺失
                Logger.d("[FloatingService] 多个依赖缺失: ${status.message}")
                Toast.makeText(
                    this,
                    status.message,
                    Toast.LENGTH_LONG
                ).show()
                launchMainActivityForSettings()
            }
            null -> {
                // dependencyChecker 未初始化，直接显示错误
                Logger.w("[FloatingService] 依赖检查器未初始化")
                Toast.makeText(
                    this,
                    "服务未完全初始化，请重试",
                    Toast.LENGTH_SHORT
                ).show()
            }
            is DependencyStatus.Satisfied -> {
                // 不会进入此分支
            }
        }
        updateLoadingState(STATE_ERROR)
    }

    /**
     * 跳转到 MainActivity 设置页面
     */
    private fun launchMainActivityForSettings() {
        try {
            val intent = Intent(this, com.cw2.cw_1kito.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_settings", true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch MainActivity for settings", e)
        }
    }

    /**
     * 执行非流式翻译流程（原有逻辑）
     */
    private suspend fun performNonStreamingTranslation() {
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
     * 执行流式翻译流程
     */
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
                            coordinateMode = CoordinateMode.detectCoordinateMode(
                                raw.coordinates.map { it.toInt() },
                                screenWidth, screenHeight
                            )
                            Log.d(TAG, "Detected coordinate mode: $coordinateMode")
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
                Log.d(TAG, "Streaming translation completed: $resultCount results")
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

    /**
     * 执行本地 OCR 翻译流程
     *
     * ## 完整流程
     * 1. 截取屏幕
     * 2. 根据性能模式调整图片分辨率
     * 3. MLKit OCR 识别（TODO）
     * 4. TextMergerEngine 文本合并（Y轴聚类 + X轴合并 + 横竖检测）
     * 5. TranslationManager 翻译（并发处理）
     * 6. TranslationOverlayView 显示结果
     *
     * ## 错误处理和降级
     * - OCR 失败 → 降级到云端 VLM（如果有 API Key）
     * - 翻译失败 → 根据翻译模式降级（HYBRID 模式自动切换到云端）
     * - 截图失败 → 提示用户重新授权
     */
    private suspend fun performLocalTranslation() {
        updateLoadingState(STATE_LOADING)

        // 性能记录变量
        var screenshotTimeMs = 0L
        var ocrTimeMs = 0L
        var mergeTimeMs = 0L
        var translateTimeMs = 0L
        var ocrResults: List<OcrDetection>? = null
        var mergedTexts: List<MergedText>? = null
        var translations: List<String>? = null

        try {
            // 1. 截图
            Logger.ocrStart()
            val screenshotStartTime = System.currentTimeMillis()

            val (imageBytes, bitmap) = captureScreenWithBitmap()
            screenshotTimeMs = System.currentTimeMillis() - screenshotStartTime

            Logger.d("[FloatingService] 截图完成: ${bitmap.width}x${bitmap.height}, ${imageBytes.size} bytes")

            // 2. 获取性能模式并调整图片分辨率
            val performanceMode = configManager.getPerformanceMode()
            val targetSize = when (performanceMode) {
                PerformanceMode.FAST -> 672
                PerformanceMode.BALANCED -> 896
                PerformanceMode.QUALITY -> 1080
            }
            val resizedBitmap = resizeBitmap(bitmap, targetSize)

            Logger.d("[FloatingService] 性能模式: ${performanceMode.displayName}, 目标短边: ${targetSize}px")
            Logger.d("[FloatingService] 调整后图片: ${resizedBitmap.width}x${resizedBitmap.height}")

            // 3. 初始化翻译管理器（首次使用）
            if (translationManager != null && !translationManager!!.isInitialized()) {
                Logger.d("[FloatingService] 初始化翻译管理器...")
                val initSuccess = translationManager!!.initialize()
                if (!initSuccess) {
                    throw Exception("翻译管理器初始化失败")
                }
            }

            // 4. OCR 识别
            val ocrStartTime = System.currentTimeMillis()
            ocrResults = performOcrRecognition(resizedBitmap)
            ocrTimeMs = System.currentTimeMillis() - ocrStartTime

            Logger.ocrSuccess(ocrResults!!.size, ocrTimeMs)

            if (ocrResults!!.isEmpty()) {
                Toast.makeText(this, "未识别到任何文本", Toast.LENGTH_SHORT).show()
                updateLoadingState(STATE_ERROR)
                return
            }

            // 5. 文本合并
            val mergeStartTime = System.currentTimeMillis()
            val mergingConfig = configManager.getMergingConfig()
            mergedTexts = textMerger?.merge(ocrResults!!, mergingConfig) ?: emptyList()
            mergeTimeMs = System.currentTimeMillis() - mergeStartTime

            Logger.mergeStart(ocrResults!!.size)
            Logger.mergeSuccess(ocrResults!!.size, mergedTexts!!.size, mergeTimeMs)

            // 6. 翻译（并发处理）
            val translateStartTime = System.currentTimeMillis()
            translations = translateMergedTexts(mergedTexts!!)
            translateTimeMs = System.currentTimeMillis() - translateStartTime

            Logger.translationSuccess(
                mergedTexts!!.sumOf { it.text.length },
                translateTimeMs
            )

            // 7. 创建 TranslationResult 并显示
            val (screenWidth, screenHeight) = getScreenDimensions()
            val results = createTranslationResults(
                mergedTexts!!,
                translations!!,
                resizedBitmap.width,
                resizedBitmap.height,
                screenWidth,
                screenHeight
            )

            showOverlay(results, screenWidth, screenHeight)

            updateLoadingState(STATE_SUCCESS)

            // 8. 记录性能指标
            val totalTimeMs = screenshotTimeMs + ocrTimeMs + mergeTimeMs + translateTimeMs
            Logger.d("[FloatingService] === 性能统计 ===")
            Logger.d("[FloatingService] 截图: ${screenshotTimeMs}ms")
            Logger.d("[FloatingService] OCR: ${ocrTimeMs}ms (${ocrResults!!.size} 个文本框)")
            Logger.d("[FloatingService] 合并: ${mergeTimeMs}ms (${ocrResults!!.size} → ${mergedTexts!!.size})")
            Logger.d("[FloatingService] 翻译: ${translateTimeMs}ms (${translations!!.size} 条)")
            Logger.d("[FloatingService] 总计: ${totalTimeMs}ms")

            // 清理 bitmap
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }
            bitmap.recycle()

        } catch (e: Exception) {
            Logger.e(e, "[FloatingService] 本地 OCR 翻译失败")
            when (e) {
                is ApiException.AuthError -> {
                    Toast.makeText(this, "录屏权限已过期，请重新授权", Toast.LENGTH_LONG).show()
                }
                else -> {
                    // 尝试降级到云端 VLM
                    val hasApiKey = !configManager.getApiKey().isNullOrEmpty()
                    if (hasApiKey) {
                        Logger.d("[FloatingService] 本地 OCR 失败，降级到云端 VLM")
                        Toast.makeText(this, "本地 OCR 失败，切换到云端翻译", Toast.LENGTH_SHORT).show()
                        performNonStreamingTranslation()
                    } else {
                        Toast.makeText(this, "翻译失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            updateLoadingState(STATE_ERROR)
        }
    }

    /**
     * 执行本地 OCR 方案翻译流程
     *
     * ## 完整流程
     * 1. 截取屏幕
     * 2. 根据性能模式调整图片分辨率
     * 3. 本地 OCR 识别（ML Kit）
     * 4. 文本合并（TextMergerEngine）
     * 5. 翻译（根据 localOcrTranslationMode 选择）
     *    - LOCAL: 使用 ML Kit 本地翻译
     *    - REMOTE: 使用 SiliconFlow 云端翻译
     *    - HYBRID: 优先本地，失败时降级到云端
     * 6. 显示翻译覆盖层
     *
     * ## 错误处理
     * - OCR 失败 → 提示用户，可选降级到云端 VLM
     * - 翻译失败 → 根据翻译模式自动处理降级
     * - 截图失败 → 提示用户重新授权
     */
    private suspend fun performLocalOcrSchemeTranslation() {
        updateLoadingState(STATE_LOADING)

        // 性能记录变量
        var screenshotTimeMs = 0L
        var ocrTimeMs = 0L
        var mergeTimeMs = 0L
        var translateTimeMs = 0L
        var ocrResults: List<OcrDetection>? = null
        var mergedTexts: List<MergedText>? = null
        var translations: List<String>? = null

        try {
            // 1. 截图
            Logger.ocrStart()
            val screenshotStartTime = System.currentTimeMillis()

            val (imageBytes, bitmap) = captureScreenWithBitmap()
            screenshotTimeMs = System.currentTimeMillis() - screenshotStartTime

            Logger.d("[FloatingService] 截图完成: ${bitmap.width}x${bitmap.height}, ${imageBytes.size} bytes")

            // 2. 获取性能模式并调整图片分辨率
            val performanceMode = configManager.getPerformanceMode()
            val targetSize = when (performanceMode) {
                PerformanceMode.FAST -> 672
                PerformanceMode.BALANCED -> 896
                PerformanceMode.QUALITY -> 1080
            }
            val resizedBitmap = resizeBitmap(bitmap, targetSize)

            Logger.d("[FloatingService] 性能模式: ${performanceMode.displayName}, 目标短边: ${targetSize}px")
            Logger.d("[FloatingService] 调整后图片: ${resizedBitmap.width}x${resizedBitmap.height}")

            // 3. 获取本地 OCR 翻译模式
            val translationMode = configManager.getLocalOcrTranslationMode()
            Logger.d("[FloatingService] 本地 OCR 翻译模式: ${translationMode.displayName}")

            // 4. 根据翻译模式初始化相应的翻译引擎
            when (translationMode) {
                TranslationMode.LOCAL -> {
                    // 仅本地翻译
                    if (localTranslator != null && !localTranslator!!.isInitialized()) {
                        Logger.d("[FloatingService] 初始化本地翻译引擎...")
                        val initSuccess = localTranslator!!.initialize()
                        if (!initSuccess) {
                            throw Exception("本地翻译引擎初始化失败")
                        }
                    }
                }
                TranslationMode.REMOTE -> {
                    // 云端翻译无需初始化
                    Logger.d("[FloatingService] 云端翻译引擎无需初始化")
                }
                TranslationMode.HYBRID -> {
                    // 混合模式：初始化本地翻译引擎
                    if (localTranslator != null && !localTranslator!!.isInitialized()) {
                        Logger.d("[FloatingService] 初始化本地翻译引擎（HYBRID 模式）...")
                        val initSuccess = localTranslator!!.initialize()
                        if (!initSuccess) {
                            throw Exception("本地翻译引擎初始化失败")
                        }
                    }
                }
            }

            // 5. OCR 识别
            val ocrStartTime = System.currentTimeMillis()
            ocrResults = performOcrRecognition(resizedBitmap)
            ocrTimeMs = System.currentTimeMillis() - ocrStartTime

            Logger.ocrSuccess(ocrResults!!.size, ocrTimeMs)

            if (ocrResults!!.isEmpty()) {
                Toast.makeText(this, "未识别到任何文本", Toast.LENGTH_SHORT).show()
                updateLoadingState(STATE_ERROR)
                return
            }

            // 6. 文本合并
            val mergeStartTime = System.currentTimeMillis()
            val mergingConfig = configManager.getMergingConfig()
            mergedTexts = textMerger?.merge(ocrResults!!, mergingConfig) ?: emptyList()
            mergeTimeMs = System.currentTimeMillis() - mergeStartTime

            Logger.mergeStart(ocrResults!!.size)
            Logger.mergeSuccess(ocrResults!!.size, mergedTexts!!.size, mergeTimeMs)

            // 7. 翻译（使用 TranslationManager 根据模式自动选择）
            val translateStartTime = System.currentTimeMillis()
            val (screenWidth, screenHeight) = getScreenDimensions()

            // 检查是否启用流式翻译
            val streamingEnabled = configManager.getStreamingEnabled()
            Logger.d("[FloatingService] 流式翻译: ${if (streamingEnabled) "已启用" else "已禁用"}")

            if (streamingEnabled) {
                // 流式模式：先创建空覆盖层，逐批添加结果
                showEmptyOverlay(screenWidth, screenHeight)
                overlayView?.setExpectedTotalCount(mergedTexts!!.size)

                translations = translateWithLocalOcrSchemeStreaming(
                    mergedTexts!!,
                    translationMode,
                    resizedBitmap.width,
                    resizedBitmap.height,
                    screenWidth,
                    screenHeight
                ) { batchResults ->
                    // 每批完成后，在主线程更新覆盖层
                    withContext(Dispatchers.Main) {
                        overlayView?.addResults(batchResults)
                    }
                }
            } else {
                // 非流式模式：等待所有翻译完成后统一显示
                translations = translateWithLocalOcrScheme(mergedTexts!!, translationMode)

                // 创建 TranslationResult 并显示
                val results = createTranslationResults(
                    mergedTexts!!,
                    translations!!,
                    resizedBitmap.width,
                    resizedBitmap.height,
                    screenWidth,
                    screenHeight
                )
                showOverlay(results, screenWidth, screenHeight)
            }

            translateTimeMs = System.currentTimeMillis() - translateStartTime

            Logger.translationSuccess(
                mergedTexts!!.sumOf { it.text.length },
                translateTimeMs
            )

            updateLoadingState(STATE_SUCCESS)

            // 9. 记录性能指标
            val totalTimeMs = screenshotTimeMs + ocrTimeMs + mergeTimeMs + translateTimeMs
            Logger.d("[FloatingService] === 性能统计 ===")
            Logger.d("[FloatingService] 截图: ${screenshotTimeMs}ms")
            Logger.d("[FloatingService] OCR: ${ocrTimeMs}ms (${ocrResults!!.size} 个文本框)")
            Logger.d("[FloatingService] 合并: ${mergeTimeMs}ms (${ocrResults!!.size} → ${mergedTexts!!.size})")
            Logger.d("[FloatingService] 翻译: ${translateTimeMs}ms (${translations!!.size} 条, 模式: ${translationMode.displayName})")
            Logger.d("[FloatingService] 总计: ${totalTimeMs}ms")

            // 清理 bitmap
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }
            bitmap.recycle()

        } catch (e: Exception) {
            Logger.e(e, "[FloatingService] 本地 OCR 方案翻译失败")
            when (e) {
                is ApiException.AuthError -> {
                    Toast.makeText(this, "录屏权限已过期，请重新授权", Toast.LENGTH_LONG).show()
                }
                else -> {
                    // 尝试降级到云端 VLM
                    val hasApiKey = !configManager.getApiKey().isNullOrEmpty()
                    if (hasApiKey) {
                        Logger.d("[FloatingService] 本地 OCR 方案失败，降级到云端 VLM")
                        Toast.makeText(this, "本地 OCR 失败，切换到云端翻译", Toast.LENGTH_SHORT).show()
                        performNonStreamingTranslation()
                    } else {
                        Toast.makeText(this, "翻译失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            updateLoadingState(STATE_ERROR)
        }
    }

    /**
     * 使用本地 OCR 方案翻译合并后的文本
     */
    private suspend fun translateWithLocalOcrScheme(
        mergedTexts: List<MergedText>,
        translationMode: TranslationMode
    ): List<String> {
        return withContext(Dispatchers.IO) {
            val langConfig = configManager.getLanguageConfig()
            val sourceLang = langConfig.sourceLanguage
            val targetLang = langConfig.targetLanguage

            Logger.d("[FloatingService] 开始翻译 ${mergedTexts.size} 条文本...")
            Logger.d("[FloatingService] 源语言: ${sourceLang.displayName}, 目标语言: ${targetLang.displayName}")
            Logger.d("[FloatingService] 翻译模式: ${translationMode.displayName}")

            // 确保翻译引擎已初始化
            if (localTranslator == null || cloudLlmTranslator == null) {
                Logger.e("[FloatingService] 翻译引擎未初始化")
                return@withContext List(mergedTexts.size) { "" }
            }

            // 创建或复用批量翻译管理器
            if (batchTranslationManager == null) {
                // 获取当前模型池配置
                val modelPoolConfig = configManager.getModelPoolConfig()
                batchTranslationManager = BatchTranslationManagerFactory.create(
                    localTranslationEngine = localTranslator!!,
                    cloudLlmEngine = cloudLlmTranslator!!,
                    configManager = configManager,
                    batchSize = com.cw2.cw_1kito.engine.translation.IBatchTranslationManager.DEFAULT_BATCH_SIZE,
                    initialModelPoolConfig = modelPoolConfig
                )
            }

            // 提取待翻译文本列表
            val texts = mergedTexts.map { it.text }

            // 使用批量翻译管理器进行翻译
            val translations = batchTranslationManager!!.translateBatch(
                texts = texts,
                sourceLang = sourceLang,
                targetLang = targetLang,
                mode = translationMode,
                onBatchComplete = { batchIndex, totalBatches ->
                    // 批次完成回调：记录进度
                    Logger.d("[FloatingService] 批量翻译进度: $batchIndex/$totalBatches")
                }
            )

            Logger.d("[FloatingService] 批量翻译完成: ${translations.size} 条结果")
            translations
        }
    }

    /**
     * 使用本地 OCR 方案翻译合并后的文本（流式模式）
     *
     * 每批翻译完成后，调用回调函数传递 TranslationResult 列表。
     *
     * @param mergedTexts 合并后的文本列表
     * @param translationMode 翻译模式
     * @param bitmapWidth 位图宽度
     * @param bitmapHeight 位图高度
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param onBatchResults 每批翻译完成后的回调，传递 TranslationResult 列表
     * @return 所有翻译结果列表
     */
    private suspend fun translateWithLocalOcrSchemeStreaming(
        mergedTexts: List<MergedText>,
        translationMode: TranslationMode,
        bitmapWidth: Int,
        bitmapHeight: Int,
        screenWidth: Int,
        screenHeight: Int,
        onBatchResults: suspend (List<com.cw2.cw_1kito.model.TranslationResult>) -> Unit
    ): List<String> = coroutineScope {
        val langConfig = configManager.getLanguageConfig()
        val sourceLang = langConfig.sourceLanguage
        val targetLang = langConfig.targetLanguage

        Logger.d("[FloatingService] 开始流式翻译 ${mergedTexts.size} 条文本...")
        Logger.d("[FloatingService] 源语言: ${sourceLang.displayName}, 目标语言: ${targetLang.displayName}")
        Logger.d("[FloatingService] 翻译模式: ${translationMode.displayName}")

        // 确保翻译引擎已初始化
        if (localTranslator == null || cloudLlmTranslator == null) {
            Logger.e("[FloatingService] 翻译引擎未初始化")
            onBatchResults(emptyList())
            return@coroutineScope List(mergedTexts.size) { "" }
        }

        // 创建或复用批量翻译管理器
        if (batchTranslationManager == null) {
            // 获取当前模型池配置
            val modelPoolConfig = configManager.getModelPoolConfig()
            batchTranslationManager = BatchTranslationManagerFactory.create(
                localTranslationEngine = localTranslator!!,
                cloudLlmEngine = cloudLlmTranslator!!,
                configManager = configManager,
                batchSize = com.cw2.cw_1kito.engine.translation.IBatchTranslationManager.DEFAULT_BATCH_SIZE,
                initialModelPoolConfig = modelPoolConfig
            )
        }

        // 分批处理文本
        val batchSize = com.cw2.cw_1kito.engine.translation.IBatchTranslationManager.DEFAULT_BATCH_SIZE
        val batches = mergedTexts.chunked(batchSize)
        val allTranslations = mutableListOf<String>()

        Logger.d("[FloatingService] 流式翻译: ${mergedTexts.size} 个文本, ${batches.size} 批")

        batches.forEachIndexed { batchIndex, batch ->
            // 提取当前批次的文本
            val batchTexts = batch.map { it.text }

            // 翻译当前批次
            val batchTranslations = batchTranslationManager!!.translateBatch(
                texts = batchTexts,
                sourceLang = sourceLang,
                targetLang = targetLang,
                mode = translationMode,
                onBatchComplete = null  // 使用我们自己的进度跟踪
            )

            // 为当前批次创建 TranslationResult
            val batchResults = batch.mapIndexed { indexInBatch, mergedText ->
                val translation = batchTranslations.getOrElse(indexInBatch) { mergedText.text }

                // 将图片坐标转换为屏幕坐标
                val screenBox = mergedText.boundingBox.toScreenRect(
                    screenWidth, screenHeight, bitmapWidth, bitmapHeight
                )

                // 转换为归一化坐标（0-1）
                com.cw2.cw_1kito.model.TranslationResult(
                    originalText = mergedText.text,
                    translatedText = translation,
                    boundingBox = com.cw2.cw_1kito.model.BoundingBox(
                        left = screenBox.left.toFloat() / screenWidth,
                        top = screenBox.top.toFloat() / screenHeight,
                        right = screenBox.right.toFloat() / screenWidth,
                        bottom = screenBox.bottom.toFloat() / screenHeight
                    )
                )
            }

            // 调用回调，传递当前批次的结果
            onBatchResults(batchResults)

            // 累积翻译结果
            allTranslations.addAll(batchTranslations)

            Logger.d("[FloatingService] 流式翻译批次 ${batchIndex + 1}/${batches.size} 完成, 累计 ${allTranslations.size}/${mergedTexts.size}")
        }

        Logger.d("[FloatingService] 流式翻译完成: ${allTranslations.size} 条结果")
        allTranslations
    }

    /**
     * 截取屏幕并返回字节数组和 Bitmap
     */
    private suspend fun captureScreenWithBitmap(): Pair<ByteArray, Bitmap> {
        return withContext(Dispatchers.IO) {
            if (!ScreenCaptureManager.hasPermission()) {
                Logger.w("[FloatingService] Screen capture permission not granted")
                launchMainActivityForReAuth()
                throw ApiException.AuthError("录屏权限已过期，请重新授权")
            }

            val result = ScreenCaptureManager.captureScreen()

            when (result) {
                is com.cw2.cw_1kito.service.capture.CaptureResult.Success -> {
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(
                        result.imageBytes,
                        0,
                        result.imageBytes.size
                    )
                    Pair(result.imageBytes, bitmap)
                }
                is com.cw2.cw_1kito.service.capture.CaptureResult.PermissionDenied -> {
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
     * 调整 Bitmap 尺寸（保持宽高比，短边缩放到目标尺寸）
     */
    private fun resizeBitmap(bitmap: Bitmap, targetShortSide: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val shortSide = minOf(width, height)

        // 如果短边已经小于等于目标尺寸，不需要缩放
        if (shortSide <= targetShortSide) {
            return bitmap
        }

        val scale = targetShortSide.toFloat() / shortSide.toFloat()
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        Logger.d("[FloatingService] 缩放图片: ${width}x${height} → ${newWidth}x${newHeight} (scale=$scale)")

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * 执行 OCR 识别
     */
    private suspend fun performOcrRecognition(bitmap: Bitmap): List<OcrDetection> {
        return withContext(Dispatchers.IO) {
            val engine = ocrEngine
            if (engine == null) {
                throw Exception("OCR 引擎未初始化")
            }

            // 根据当前翻译配置更新 OCR 语言
            if (engine is MLKitOCRManager) {
                val languageConfig = configManager.getLanguageConfig()
                val ocrLanguage = configManager.getOcrLanguage()
                val inferredLanguage = OcrEngineFactory.inferOcrLanguage(
                    sourceLanguage = languageConfig.sourceLanguage,
                    targetLanguage = languageConfig.targetLanguage,
                    manualOcrLanguage = ocrLanguage
                )

                // 如果语言不同，更新语言设置
                if (engine.getLanguage() != inferredLanguage) {
                    Logger.d("[FloatingService] 切换 OCR 语言: ${engine.getLanguage().displayName} -> ${inferredLanguage.displayName}")
                    engine.setLanguage(inferredLanguage)
                }
            }

            // 首次使用时初始化
            if (!engine.isInitialized()) {
                Logger.d("[FloatingService] 初始化 OCR 引擎...")
                val initSuccess = engine.initialize()
                if (!initSuccess) {
                    throw Exception("OCR 引擎初始化失败")
                }
                Logger.d("[FloatingService] OCR 引擎初始化成功")
            }

            // 执行识别
            val results = engine.recognize(bitmap)
            results
        }
    }

    /**
     * 翻译合并后的文本（并发处理）
     */
    private suspend fun translateMergedTexts(mergedTexts: List<MergedText>): List<String> {
        return withContext(Dispatchers.IO) {
            val langConfig = configManager.getLanguageConfig()
            val sourceLang = langConfig.sourceLanguage
            val targetLang = langConfig.targetLanguage

            Logger.d("[FloatingService] 开始翻译 ${mergedTexts.size} 条文本...")
            Logger.d("[FloatingService] 源语言: ${sourceLang.displayName}, 目标语言: ${targetLang.displayName}")

            // 并发翻译所有文本
            val translations = mergedTexts.map { merged ->
                async {
                    try {
                        translationManager?.translate(
                            merged.text,
                            sourceLang,
                            targetLang
                        ) ?: ""
                    } catch (e: Exception) {
                        Logger.e(e, "[FloatingService] 翻译失败: ${merged.text.take(30)}...")
                        "" // 翻译失败时返回空字符串
                    }
                }
            }.awaitAll()

            translations
        }
    }

    /**
     * 创建 TranslationResult 列表
     */
    private fun createTranslationResults(
        mergedTexts: List<MergedText>,
        translations: List<String>,
        imageWidth: Int,
        imageHeight: Int,
        screenWidth: Int,
        screenHeight: Int
    ): List<TranslationResult> {
        return mergedTexts.mapIndexed { index, merged ->
            val translated = translations.getOrElse(index) { "" }

            // 将图片坐标转换为屏幕坐标
            val screenBox = merged.boundingBox.toScreenRect(
                screenWidth, screenHeight, imageWidth, imageHeight
            )

            // 转换为归一化坐标（0-1）
            TranslationResult(
                originalText = merged.text,
                translatedText = translated,
                boundingBox = com.cw2.cw_1kito.model.BoundingBox(
                    left = screenBox.left.toFloat() / screenWidth,
                    top = screenBox.top.toFloat() / screenHeight,
                    right = screenBox.right.toFloat() / screenWidth,
                    bottom = screenBox.bottom.toFloat() / screenHeight
                )
            )
        }
    }

    /**
     * 获取屏幕尺寸
     */
    private fun getScreenDimensions(): Pair<Int, Int> {
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * 构建翻译 API 请求
     */
    private suspend fun buildTranslationRequest(
        imageBytes: ByteArray,
        screenWidth: Int,
        screenHeight: Int
    ): TranslationApiRequest {
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

        return TranslationApiRequest(
            model = model,
            imageData = base64Image,
            targetLanguage = targetLanguage,
            temperature = 0.7,
            maxTokens = 4000,
            imageWidth = screenWidth,
            imageHeight = screenHeight,
            customPrompt = customPrompt
        )
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
        initialResults: List<TranslationResult>,
        screenWidth: Int,
        screenHeight: Int
    ) {
        hideOverlay()

        overlayView = com.cw2.cw_1kito.service.overlay.TranslationOverlayView(
            context = this,
            initialResults = initialResults,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            onDismiss = {
                hideOverlay()
            }
        )

        val params = createOverlayLayoutParams(screenWidth, screenHeight)

        try {
            windowManager.addView(overlayView, params)
            Log.d(TAG, "Overlay view added with ${initialResults.size} results")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
        }
    }

    /**
     * 创建空覆盖层（流式模式专用）
     * 后续通过 addResult() 逐条填充内容
     */
    private fun showEmptyOverlay(screenWidth: Int, screenHeight: Int) {
        hideOverlay()

        overlayView = com.cw2.cw_1kito.service.overlay.TranslationOverlayView(
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

        val params = createOverlayLayoutParams(screenWidth, screenHeight)

        try {
            windowManager.addView(overlayView, params)
            Log.d(TAG, "Empty overlay view added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add empty overlay", e)
        }
    }

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
