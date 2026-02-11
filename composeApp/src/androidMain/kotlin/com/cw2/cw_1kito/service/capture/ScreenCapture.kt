package com.cw2.cw_1kito.service.capture

import android.app.Activity
import android.content.Intent

/**
 * 屏幕截图服务接口
 *
 * 使用 MediaProjection API 截取屏幕内容，用于翻译功能
 */
interface ScreenCapture {

    /**
     * 请求录屏权限
     *
     * @param activity 用于启动权限请求的 Activity
     * @param requestCode 请求码，用于在 onActivityResult 中识别结果
     * @return 权限请求 Intent，需要通过 startActivityForResult 启动
     */
    fun requestPermission(activity: Activity, requestCode: Int): Intent

    /**
     * 开始截图
     *
     * @param resultCode 权限请求结果 (Activity.RESULT_OK 或 RESULT_CANCELED)
     * @param data 权限返回数据
     * @return 截图结果
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
     *
     * @return true 如果已经获得权限并初始化成功
     */
    fun hasPermission(): Boolean

    /**
     * 获取屏幕尺寸
     *
     * @return Pair<宽度, 高度>，如果未初始化则返回 null
     */
    fun getScreenSize(): Pair<Int, Int>?
}

/**
 * 截图结果密封类
 */
sealed class CaptureResult {
    /**
     * 截图成功
     * @param imageBytes JPEG 格式的图片数据
     * @param width 屏幕宽度（像素）
     * @param height 屏幕高度（像素）
     */
    data class Success(
        val imageBytes: ByteArray,
        val width: Int,
        val height: Int
    ) : CaptureResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Success
            return width == other.width && height == other.height
        }

        override fun hashCode(): Int {
            var result = width
            result = 31 * result + height
            return result
        }
    }

    /**
     * 用户拒绝权限
     */
    data object PermissionDenied : CaptureResult()

    /**
     * 截图失败
     * @param message 错误信息
     */
    data class Error(val message: String) : CaptureResult()
}

/**
 * 截图配置
 */
data class CaptureConfig(
    val width: Int = 1080,
    val height: Int = 1920,
    val density: Int = 320,
    val quality: Int = 85, // JPEG 压缩质量 0-100
    val format: Int = android.graphics.PixelFormat.RGBA_8888
)
