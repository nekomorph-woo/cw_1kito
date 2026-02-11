package com.cw2.cw_1kito.service.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 屏幕截图服务实现
 *
 * 使用 MediaProjection API 实现屏幕截图功能。
 * 采用持久会话模式：创建一次 MediaProjection + VirtualDisplay，
 * 保持存活，每次截图只从 ImageReader 读取最新帧。
 */
class ScreenCaptureImpl(
    private val context: Context,
    private val config: CaptureConfig = CaptureConfig(),
    private val onSessionInvalidated: (() -> Unit)? = null
) : ScreenCapture {

    private val tag = "ScreenCapture"

    private val mediaProjectionManager: MediaProjectionManager by lazy {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val windowManager: WindowManager by lazy {
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReaderWrapper: ImageReaderWrapper? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null

    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var screenDensity: Int = 0

    // 会话是否已建立（MediaProjection + VirtualDisplay 已创建）
    private var sessionActive = false
    // session 是否被系统强制停止（token 失效）
    private var sessionInvalidatedBySystem = false

    init {
        initScreenMetrics()
    }

    private fun initScreenMetrics() {
        val metrics = DisplayMetrics()
        // 使用 getRealMetrics 获取真实全屏尺寸（含状态栏和导航栏）
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi
        Log.d(tag, "Screen real metrics: ${screenWidth}x${screenHeight}, density: $screenDensity")
    }

    override fun requestPermission(activity: Activity, requestCode: Int): Intent {
        return mediaProjectionManager.createScreenCaptureIntent()
    }

    override suspend fun captureScreen(resultCode: Int, data: Intent?): CaptureResult {
        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.w(tag, "Screen capture permission denied (resultCode=$resultCode)")
            return CaptureResult.PermissionDenied
        }

        // 如果 session 被系统停止过（息屏、token 超时），需要重新授权
        if (sessionInvalidatedBySystem) {
            Log.w(tag, "Session was invalidated by system, need re-authorization")
            return CaptureResult.PermissionDenied
        }

        return try {
            // 如果会话不存在，建立新会话
            if (!sessionActive) {
                startSession(resultCode, data)
            }

            // 从 ImageReader 获取最新帧
            captureFrame()
        } catch (e: SecurityException) {
            Log.e(tag, "Screen capture SecurityException (token expired)", e)
            // SecurityException 说明 token 已失效，需要重新授权
            releaseSession(systemInvalidated = true)
            CaptureResult.PermissionDenied
        } catch (e: Exception) {
            Log.e(tag, "Screen capture failed", e)
            releaseSession(systemInvalidated = false)
            CaptureResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 建立持久截图会话：创建 MediaProjection + VirtualDisplay + ImageReader
     */
    private fun startSession(resultCode: Int, data: Intent) {
        Log.d(tag, "Starting capture session")

        // 创建 MediaProjection
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)

        // Android 14+ 注册 callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerMediaProjectionCallback()
        }

        // 创建 ImageReader
        val wrapper = ImageReaderWrapper(
            width = screenWidth,
            height = screenHeight,
            format = PixelFormat.RGBA_8888
        )
        imageReaderWrapper = wrapper
        wrapper.init()

        // 创建 VirtualDisplay（只创建一次）
        createVirtualDisplay(wrapper.surface)

        sessionActive = true
        sessionInvalidatedBySystem = false
        Log.d(tag, "Capture session started: ${screenWidth}x${screenHeight}")
    }

    /**
     * 从已存在的会话中捕获一帧
     */
    private suspend fun captureFrame(): CaptureResult {
        val wrapper = imageReaderWrapper
            ?: return CaptureResult.Error("ImageReader not initialized")

        return withContext(Dispatchers.IO) {
            // 等一小段时间让帧更新
            delay(150)

            var image = wrapper.acquireLatestImage()
            var attempts = 0
            val maxAttempts = 10

            while (image == null && attempts < maxAttempts) {
                delay(100)
                image = wrapper.acquireLatestImage()
                attempts++
            }

            if (image == null) {
                Log.w(tag, "Failed to acquire image after $maxAttempts attempts")
                return@withContext CaptureResult.Error("Failed to acquire image")
            }

            try {
                val imageBytes = wrapper.imageToByteArray(image, config.quality)
                Log.d(tag, "Frame captured: ${imageBytes.size} bytes")

                if (imageBytes.isEmpty()) {
                    return@withContext CaptureResult.Error("Captured image is empty")
                }

                CaptureResult.Success(
                    imageBytes = imageBytes,
                    width = screenWidth,
                    height = screenHeight
                )
            } finally {
                image.close()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun registerMediaProjectionCallback() {
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(tag, "MediaProjection stopped by system")
                releaseSession(systemInvalidated = true)
            }
        }
        mediaProjection?.registerCallback(callback, Handler(Looper.getMainLooper()))
        mediaProjectionCallback = callback
        Log.d(tag, "MediaProjection callback registered")
    }

    private fun createVirtualDisplay(surface: android.view.Surface) {
        virtualDisplay?.release()

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        )

        Log.d(tag, "VirtualDisplay created: ${screenWidth}x${screenHeight}")
    }

    /**
     * 释放截图会话
     *
     * @param systemInvalidated 是否由系统强制停止（token 失效）
     */
    private fun releaseSession(systemInvalidated: Boolean = false) {
        virtualDisplay?.release()
        virtualDisplay = null

        imageReaderWrapper?.release()
        imageReaderWrapper = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mediaProjectionCallback?.let { cb ->
                try { mediaProjection?.unregisterCallback(cb) } catch (_: Exception) {}
            }
            mediaProjectionCallback = null
        }

        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null

        sessionActive = false

        if (systemInvalidated) {
            sessionInvalidatedBySystem = true
            Log.d(tag, "Capture session released (invalidated by system)")
            // 通知 Manager 权限已失效
            onSessionInvalidated?.invoke()
        } else {
            Log.d(tag, "Capture session released")
        }
    }

    override fun stopCapture() {
        releaseSession(systemInvalidated = false)
    }

    override fun hasPermission(): Boolean {
        return sessionActive || mediaProjection != null
    }

    override fun getScreenSize(): Pair<Int, Int>? {
        return if (screenWidth > 0 && screenHeight > 0) {
            Pair(screenWidth, screenHeight)
        } else {
            null
        }
    }

    fun refreshScreenMetrics(): Pair<Int, Int> {
        initScreenMetrics()
        return Pair(screenWidth, screenHeight)
    }
}

fun createScreenCapture(
    context: Context,
    config: CaptureConfig = CaptureConfig()
): ScreenCapture {
    return ScreenCaptureImpl(context, config)
}
