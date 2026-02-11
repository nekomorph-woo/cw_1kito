package com.cw2.cw_1kito.domain.coordinate

import com.cw2.cw_1kito.model.BoundingBox

/**
 * 坐标验证器接口
 *
 * 负责处理大模型返回的坐标偏差，提供容错机制。
 */
interface CoordinateValidator {
    /**
     * 验证和调整单个边界框
     *
     * @param box 原始边界框
     * @param screenWidth 屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @return 调整后的边界框
     */
    fun validateAndAdjust(
        box: BoundingBox,
        screenWidth: Int,
        screenHeight: Int
    ): BoundingBox

    /**
     * 验证和调整多个边界框（处理重叠）
     *
     * @param boxes 原始边界框列表
     * @param screenWidth 屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @return 调整后的边界框列表（顺序可能与输入不同）
     */
    fun validateAndAdjustAll(
        boxes: List<BoundingBox>,
        screenWidth: Int,
        screenHeight: Int
    ): List<BoundingBox>

    /**
     * 检查边界框是否有效
     *
     * @param box 要检查的边界框
     * @return 是否有效
     */
    fun isValid(box: BoundingBox): Boolean
}

/**
 * 坐标验证器配置
 */
data class CoordinateValidatorConfig(
    /** 最小框尺寸（屏幕比例） */
    val minBoxSize: Float = 0.02f,

    /** 扩展边距 */
    val expansionPadding: Float = 0.01f,

    /** 重叠阈值（0-1），超过此值认为需要处理 */
    val overlapThreshold: Float = 0.3f,

    /** 最大重叠处理迭代次数 */
    val maxOverlapIterations: Int = 10,

    /** 是否启用框体合并 */
    val enableMerging: Boolean = true,

    /** 合并距离阈值 */
    val mergeDistanceThreshold: Float = 0.05f
) {
    companion object {
        /** 默认配置 */
        val DEFAULT = CoordinateValidatorConfig()

        /** 严格配置（更严格的验证） */
        val STRICT = CoordinateValidatorConfig(
            minBoxSize = 0.03f,
            expansionPadding = 0.005f,
            overlapThreshold = 0.2f,
            enableMerging = true
        )

        /** 宽松配置（更宽松的验证） */
        val LOOSE = CoordinateValidatorConfig(
            minBoxSize = 0.01f,
            expansionPadding = 0.02f,
            overlapThreshold = 0.5f,
            enableMerging = false
        )
    }
}
