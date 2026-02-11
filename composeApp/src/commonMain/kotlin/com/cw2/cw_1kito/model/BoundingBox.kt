package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 归一化边界框 (0-1)
 * 坐标系统: left/top 为左上角，right/bottom 为右下角
 */
@Serializable
data class BoundingBox(
    val left: Float,    // 0.0 - 1.0
    val top: Float,     // 0.0 - 1.0
    val right: Float,   // 0.0 - 1.0
    val bottom: Float   // 0.0 - 1.0
) {
    /** 框的宽度 */
    val width: Float get() = (right - left).coerceAtLeast(0f)

    /** 框的高度 */
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    /** 框的中心 X 坐标 */
    val centerX: Float get() = (left + right) / 2

    /** 框的中心 Y 坐标 */
    val centerY: Float get() = (top + bottom) / 2

    /** 框的面积 */
    val area: Float get() = width * height

    /**
     * 转换为屏幕坐标的 Rect
     * @param screenWidth 屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @return 屏幕坐标的矩形
     */
    fun toScreenRect(screenWidth: Int, screenHeight: Int): ScreenRect {
        return ScreenRect(
            left = (left * screenWidth).toInt().coerceIn(0, screenWidth),
            top = (top * screenHeight).toInt().coerceIn(0, screenHeight),
            right = (right * screenWidth).toInt().coerceIn(0, screenWidth),
            bottom = (bottom * screenHeight).toInt().coerceIn(0, screenHeight)
        )
    }

    /**
     * 裁剪到 [0, 1] 范围内
     */
    fun clipToBounds(): BoundingBox = copy(
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        right = right.coerceIn(0f, 1f),
        bottom = bottom.coerceIn(0f, 1f)
    ).also {
        require(it.left <= it.right && it.top <= it.bottom) {
            "无效的边界框: left=${it.left}, top=${it.top}, right=${it.right}, bottom=${it.bottom}"
        }
    }

    /**
     * 扩展框体到最小尺寸
     * @param minSize 最小尺寸（归一化 0-1）
     * @param padding 额外的内边距
     */
    fun expandToMinSize(minSize: Float, padding: Float = 0f): BoundingBox {
        val currentWidth = right - left
        val currentHeight = bottom - top

        if (currentWidth >= minSize && currentHeight >= minSize) {
            return this
        }

        // 计算需要的扩展量
        val widthExpansion = maxOf(0f, minSize - currentWidth) / 2
        val heightExpansion = maxOf(0f, minSize - currentHeight) / 2

        return copy(
            left = (left - widthExpansion - padding).coerceAtLeast(0f),
            top = (top - heightExpansion - padding).coerceAtLeast(0f),
            right = (right + widthExpansion + padding).coerceAtMost(1f),
            bottom = (bottom + heightExpansion + padding).coerceAtMost(1f)
        )
    }

    /**
     * 计算与另一个框的交集面积
     */
    fun intersectionArea(other: BoundingBox): Float {
        val intersectionLeft = maxOf(left, other.left)
        val intersectionTop = maxOf(top, other.top)
        val intersectionRight = minOf(right, other.right)
        val intersectionBottom = minOf(bottom, other.bottom)

        if (intersectionLeft >= intersectionRight || intersectionTop >= intersectionBottom) {
            return 0f
        }

        return (intersectionRight - intersectionLeft) * (intersectionBottom - intersectionTop)
    }

    /**
     * 计算与另一个框的并集面积
     */
    fun unionArea(other: BoundingBox): Float {
        return area + other.area - intersectionArea(other)
    }

    /**
     * 计算与另一个框的 IoU (Intersection over Union)
     */
    fun iou(other: BoundingBox): Float {
        val intersection = intersectionArea(other)
        val union = unionArea(other)
        return if (union > 0f) intersection / union else 0f
    }

    /**
     * 检查是否与另一个框重叠超过阈值
     */
    fun hasSignificantOverlap(other: BoundingBox, threshold: Float = 0.5f): Boolean {
        return iou(other) > threshold
    }

    companion object {
        /**
         * 从外部 API 坐标创建（归一化 0-1000 -> 0-1）
         * 外部 API 使用 0-1000 的坐标范围
         */
        fun fromExternal(coords: List<Int>): BoundingBox {
            require(coords.size == 4) {
                "坐标必须包含 4 个点 [left, top, right, bottom]，实际: $coords"
            }
            return BoundingBox(
                left = (coords[0] / 1000f).coerceIn(0f, 1f),
                top = (coords[1] / 1000f).coerceIn(0f, 1f),
                right = (coords[2] / 1000f).coerceIn(0f, 1f),
                bottom = (coords[3] / 1000f).coerceIn(0f, 1f)
            )
        }

        /**
         * 创建空的边界框
         */
        val EMPTY = BoundingBox(0f, 0f, 0f, 0f)

        /**
         * 创建全屏边界框
         */
        val FULL_SCREEN = BoundingBox(0f, 0f, 1f, 1f)
    }
}

/**
 * 屏幕坐标矩形（像素单位）
 */
@Serializable
data class ScreenRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    /** 矩形宽度 */
    val width: Int get() = (right - left).coerceAtLeast(0)

    /** 矩形高度 */
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    /** 矩形中心 X */
    val centerX: Int get() = (left + right) / 2

    /** 矩形中心 Y */
    val centerY: Int get() = (top + bottom) / 2

    /** 矩形面积 */
    val area: Int get() = width * height

    /**
     * 检查点是否在矩形内
     */
    fun contains(x: Int, y: Int): Boolean {
        return x in left..right && y in top..bottom
    }

    /**
     * 转换为归一化边界框
     */
    fun toNormalized(screenWidth: Int, screenHeight: Int): BoundingBox {
        require(screenWidth > 0 && screenHeight > 0) {
            "屏幕尺寸必须大于 0: width=$screenWidth, height=$screenHeight"
        }
        return BoundingBox(
            left = left.toFloat() / screenWidth,
            top = top.toFloat() / screenHeight,
            right = right.toFloat() / screenWidth,
            bottom = bottom.toFloat() / screenHeight
        )
    }
}
