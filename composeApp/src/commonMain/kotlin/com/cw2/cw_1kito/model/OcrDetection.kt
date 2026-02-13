package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * OCR 检测结果
 * 包含文本内容、边界框和置信度信息
 */
@Serializable
data class OcrDetection(
    /** 检测到的文本内容 */
    val text: String,

    /** 边界框（像素坐标） */
    val boundingBox: RectF,

    /** 置信度（0.0 - 1.0） */
    val confidence: Float,

    /** 旋转角度（0°/90°/180°/270°），null 表示未检测到旋转 */
    val angle: Float? = null
) {
    init {
        require(confidence in 0f..1f) {
            "置信度必须在 0.0 到 1.0 之间，实际: $confidence"
        }
        require(angle == null || angle in 0f..360f) {
            "旋转角度必须在 0° 到 360° 之间，实际: $angle"
        }
    }

    /** 检查是否为高质量检测（置信度 > 0.8） */
    val isHighQuality: Boolean get() = confidence > 0.8f

    /** 检查是否为低质量检测（置信度 < 0.5） */
    val isLowQuality: Boolean get() = confidence < 0.5f

    companion object {
        /**
         * 创建高置信度的检测结果
         */
        fun highQuality(text: String, boundingBox: RectF, angle: Float? = null) =
            OcrDetection(text, boundingBox, confidence = 1.0f, angle)

        /**
         * 创建中等置信度的检测结果
         */
        fun mediumQuality(text: String, boundingBox: RectF, angle: Float? = null) =
            OcrDetection(text, boundingBox, confidence = 0.7f, angle)
    }
}

/**
 * 像素坐标矩形
 */
@Serializable
data class RectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    /** 矩形宽度 */
    val width: Float get() = (right - left).coerceAtLeast(0f)

    /** 矩形高度 */
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    /** 矩形中心 X */
    val centerX: Float get() = (left + right) / 2

    /** 矩形中心 Y */
    val centerY: Float get() = (top + bottom) / 2

    /** 矩形面积 */
    val area: Float get() = width * height

    /**
     * 转换为归一化边界框
     * @param imageWidth 图像宽度（像素）
     * @param imageHeight 图像高度（像素）
     */
    fun toNormalized(imageWidth: Int, imageHeight: Int): BoundingBox {
        require(imageWidth > 0 && imageHeight > 0) {
            "图像尺寸必须大于 0: width=$imageWidth, height=$imageHeight"
        }
        return BoundingBox(
            left = left / imageWidth,
            top = top / imageHeight,
            right = right / imageWidth,
            bottom = bottom / imageHeight
        )
    }

    /**
     * 扩展矩形（增加边距）
     * @param horizontal 水平扩展量（像素）
     * @param vertical 垂直扩展量（像素）
     */
    fun expand(horizontal: Float = 0f, vertical: Float = 0f): RectF {
        return copy(
            left = left - horizontal,
            top = top - vertical,
            right = right + horizontal,
            bottom = bottom + vertical
        )
    }

    /**
     * 缩放矩形
     * @param scaleX X 轴缩放因子
     * @param scaleY Y 轴缩放因子
     */
    fun scale(scaleX: Float, scaleY: Float): RectF {
        return copy(
            left = left * scaleX,
            top = top * scaleY,
            right = right * scaleX,
            bottom = bottom * scaleY
        )
    }

    /**
     * 检查点是否在矩形内
     */
    fun contains(x: Float, y: Float): Boolean {
        return x in left..right && y in top..bottom
    }

    /**
     * 计算与另一个矩形的交集
     */
    fun intersection(other: RectF): RectF? {
        val intersectionLeft = maxOf(left, other.left)
        val intersectionTop = maxOf(top, other.top)
        val intersectionRight = minOf(right, other.right)
        val intersectionBottom = minOf(bottom, other.bottom)

        if (intersectionLeft >= intersectionRight || intersectionTop >= intersectionBottom) {
            return null
        }

        return RectF(intersectionLeft, intersectionTop, intersectionRight, intersectionBottom)
    }

    /**
     * 计算与另一个矩形的并集
     */
    fun union(other: RectF): RectF {
        return RectF(
            left = minOf(left, other.left),
            top = minOf(top, other.top),
            right = maxOf(right, other.right),
            bottom = maxOf(bottom, other.bottom)
        )
    }

    /**
     * 转换为屏幕坐标矩形
     * 注意：此方法假设 RectF 已经是相对于图像的像素坐标
     * @param screenWidth 屏幕宽度（像素）- 用于坐标缩放
     * @param screenHeight 屏幕高度（像素）- 用于坐标缩放
     * @param imageWidth 图像宽度（像素）- 如果与屏幕尺寸不同则需要缩放
     * @param imageHeight 图像高度（像素）- 如果与屏幕尺寸不同则需要缩放
     */
    fun toScreenRect(
        screenWidth: Int,
        screenHeight: Int,
        imageWidth: Int = screenWidth,
        imageHeight: Int = screenHeight
    ): ScreenRect {
        val scaleX = screenWidth.toFloat() / imageWidth
        val scaleY = screenHeight.toFloat() / imageHeight
        return ScreenRect(
            left = (left * scaleX).toInt().coerceIn(0, screenWidth),
            top = (top * scaleY).toInt().coerceIn(0, screenHeight),
            right = (right * scaleX).toInt().coerceIn(0, screenWidth),
            bottom = (bottom * scaleY).toInt().coerceIn(0, screenHeight)
        )
    }

    companion object {
        /** 创建空矩形 */
        val EMPTY = RectF(0f, 0f, 0f, 0f)

        /**
         * 从边界框创建像素矩形
         * @param boundingBox 归一化边界框（0-1）
         * @param imageWidth 图像宽度（像素）
         * @param imageHeight 图像高度（像素）
         */
        fun fromBoundingBox(boundingBox: BoundingBox, imageWidth: Int, imageHeight: Int): RectF {
            return RectF(
                left = boundingBox.left * imageWidth,
                top = boundingBox.top * imageHeight,
                right = boundingBox.right * imageWidth,
                bottom = boundingBox.bottom * imageHeight
            )
        }
    }
}
