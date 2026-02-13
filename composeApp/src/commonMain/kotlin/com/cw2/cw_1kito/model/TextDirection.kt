package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 文本方向
 * 用于识别文本是横排还是竖排
 */
@Serializable
enum class TextDirection {
    /** 横排（从左到右） */
    HORIZONTAL,

    /** 竖排（从上到下） */
    VERTICAL,

    /** 混合（同一行包含不同方向） */
    MIXED;

    companion object {
        /**
         * 从旋转角度推断方向
         * @param angle 旋转角度（0°/90°/180°/270°）
         */
        fun fromAngle(angle: Float?): TextDirection {
            return when (angle) {
                90f, 270f -> VERTICAL
                else -> HORIZONTAL
            }
        }

        /**
         * 根据宽高比推断方向
         * @param width 宽度
         * @param height 高度
         */
        fun fromAspectRatio(width: Float, height: Float): TextDirection {
            return if (height > width) VERTICAL else HORIZONTAL
        }
    }
}
