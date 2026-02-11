package com.cw2.cw_1kito.service.capture

import android.media.ImageReader
import android.hardware.HardwareBuffer
import android.media.Image
import android.os.Build
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * ImageReader 包装类
 *
 * 封装 ImageReader 的生命周期管理，提供协程友好的 API
 */
class ImageReaderWrapper(
    private val width: Int,
    private val height: Int,
    private val format: Int,
    private val maxImages: Int = 2
) {
    private val tag = "ImageReaderWrapper"

    private var imageReader: ImageReader? = null
    private var isReleased = false

    /**
     * 初始化 ImageReader
     */
    fun init(): ImageReader {
        if (isReleased) {
            throw IllegalStateException("ImageReaderWrapper has been released")
        }

        if (imageReader == null) {
            imageReader = ImageReader.newInstance(width, height, format, maxImages)
        }
        return imageReader!!
    }

    /**
     * 获取 Surface
     */
    val surface: android.view.Surface
        get() = imageReader?.surface ?: throw IllegalStateException("ImageReader not initialized")

    /**
     * 获取最新的图像
     *
     * @return Image 实例，使用完毕后必须调用 close()
     */
    suspend fun acquireLatestImage(): Image? = suspendCancellableCoroutine { continuation ->
        val reader = imageReader
        if (reader == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        try {
            val image = reader.acquireLatestImage()
            continuation.resume(image)
        } catch (e: Exception) {
            Log.e(tag, "Failed to acquire image", e)
            continuation.resume(null)
        }
    }

    /**
     * 将 Image 转换为 JPEG ByteArray
     *
     * @param image 图像数据
     * @param quality JPEG 压缩质量 0-100
     * @return JPEG 格式的字节数组
     */
    fun imageToByteArray(image: Image, quality: Int = 85): ByteArray {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        // 创建 Bitmap
        val bitmap = android.graphics.Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // 裁剪掉 padding
        val croppedBitmap = if (rowPadding != 0) {
            android.graphics.Bitmap.createBitmap(bitmap, 0, 0, width, height)
        } else {
            bitmap
        }

        // 压缩为 JPEG
        val outputStream = ByteArrayOutputStream()
        croppedBitmap.compress(
            android.graphics.Bitmap.CompressFormat.JPEG,
            quality.coerceIn(0, 100),
            outputStream
        )

        // 回收 Bitmap
        if (croppedBitmap != bitmap) {
            croppedBitmap.recycle()
        }
        bitmap.recycle()

        return outputStream.toByteArray()
    }

    /**
     * 释放资源
     */
    fun release() {
        imageReader?.close()
        imageReader = null
        isReleased = true
    }

    /**
     * 检查是否已释放
     */
    val isClosed: Boolean
        get() = isReleased || imageReader == null
}

/**
 * 使用 HardwareBuffer 的 ImageReader 包装类（API 26+）
 *
 * 性能更好的实现方式，适用于 Android 8.0+
 */
class HardwareBufferImageReaderWrapper(
    private val width: Int,
    private val height: Int,
    private val format: Int = HardwareBuffer.RGBA_8888,
    private val maxImages: Int = 2
) {
    private val tag = "HardwareBufferImageReader"

    private var imageReader: ImageReader? = null
    private var isReleased = false

    /**
     * 初始化 ImageReader（仅 API 26+）
     */
    fun init(): ImageReader {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            throw UnsupportedOperationException("HardwareBuffer requires API 26+")
        }

        if (isReleased) {
            throw IllegalStateException("ImageReaderWrapper has been released")
        }

        if (imageReader == null) {
            @Suppress("NewApi")
            imageReader = ImageReader.newInstance(
                width,
                height,
                format,
                maxImages,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
            )
        }
        return imageReader!!
    }

    /**
     * 获取 Surface
     */
    val surface: android.view.Surface
        get() = imageReader?.surface ?: throw IllegalStateException("ImageReader not initialized")

    /**
     * 释放资源
     */
    fun release() {
        imageReader?.close()
        imageReader = null
        isReleased = true
    }
}
