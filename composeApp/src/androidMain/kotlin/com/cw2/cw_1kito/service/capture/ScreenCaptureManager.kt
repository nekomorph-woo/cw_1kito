package com.cw2.cw_1kito.service.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 屏幕截图管理器单例
 *
 * 管理 ScreenCapture 实例，保持 MediaProjection 权限状态
 * 以便在后台服务中使用
 */
object ScreenCaptureManager {

    private const val TAG = "ScreenCaptureManager"

    private var screenCapture: ScreenCaptureImpl? = null
    private var pendingResultCode: Int = 0
    private var pendingData: Intent? = null
    private var hasUserGrantedPermission = false

    // 权限失效监听器
    private var onPermissionExpired: (() -> Unit)? = null

    // 用于异步初始化的协程作用域
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 初始化截图管理器
     */
    fun init(context: Context) {
        if (screenCapture == null) {
            screenCapture = ScreenCaptureImpl(context) { onSessionInvalidated() }
            Log.d(TAG, "ScreenCapture initialized")
        }
    }

    /**
     * 设置权限过期回调
     */
    fun setOnPermissionExpiredListener(listener: (() -> Unit)?) {
        onPermissionExpired = listener
    }

    /**
     * 请求录屏权限
     */
    fun requestPermission(activity: Activity, requestCode: Int): Intent {
        ensureInitialized()
        return screenCapture!!.requestPermission(activity, requestCode)
    }

    /**
     * 设置权限结果（从 Activity 的 onActivityResult 中调用）
     */
    fun setPermissionResult(resultCode: Int, data: Intent?) {
        pendingResultCode = resultCode
        pendingData = data
        Log.d(TAG, "Permission result set: resultCode=$resultCode, data=$data")

        hasUserGrantedPermission = (resultCode == Activity.RESULT_OK && data != null)

        if (!hasUserGrantedPermission) {
            Log.w(TAG, "User denied screen capture permission")
        }
    }

    /**
     * 当 MediaProjection session 失效时调用（系统停止、token 过期等）
     */
    private fun onSessionInvalidated() {
        Log.w(TAG, "MediaProjection session invalidated, clearing permission state")
        hasUserGrantedPermission = false
        pendingResultCode = 0
        pendingData = null
        onPermissionExpired?.invoke()
    }

    /**
     * 执行截图
     */
    suspend fun captureScreen(): CaptureResult {
        ensureInitialized()

        // 如果有待处理的权限结果，使用它
        val result = if (pendingData != null && hasUserGrantedPermission) {
            screenCapture!!.captureScreen(pendingResultCode, pendingData)
        } else {
            Log.w(TAG, "No permission available")
            CaptureResult.PermissionDenied
        }

        return result
    }

    /**
     * 检查是否有录屏权限
     */
    fun hasPermission(): Boolean {
        return hasUserGrantedPermission
    }

    /**
     * 获取屏幕尺寸
     */
    fun getScreenSize(): Pair<Int, Int>? {
        return screenCapture?.getScreenSize()
    }

    /**
     * 停止截图并释放资源
     */
    fun stopCapture() {
        screenCapture?.stopCapture()
        Log.d(TAG, "Screen capture stopped")
    }

    /**
     * 释放所有资源
     */
    fun release() {
        screenCapture?.stopCapture()
        screenCapture = null
        pendingResultCode = 0
        pendingData = null
        hasUserGrantedPermission = false
        onPermissionExpired = null
        Log.d(TAG, "ScreenCaptureManager released")
    }

    private fun ensureInitialized() {
        check(screenCapture != null) {
            "ScreenCaptureManager not initialized. Call init(context) first."
        }
    }
}
