package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 文本合并配置
 *
 * 控制文本框合并的阈值和策略
 */
@Serializable
data class MergingConfig(
    /** Y 轴合并阈值（控制行识别的宽松程度） */
    val yTolerance: Float = 0.4f,

    /** X 轴合并阈值因子（控制同行文本合并的宽松程度） */
    val xToleranceFactor: Float = 1.5f
) {
    init {
        require(yTolerance in 0.1f..1.0f) {
            "Y 轴阈值必须在 0.1 到 1.0 之间，实际: $yTolerance"
        }
        require(xToleranceFactor in 0.5f..3.0f) {
            "X 轴阈值因子必须在 0.5 到 3.0 之间，实际: $xToleranceFactor"
        }
    }

    companion object {
        /**
         * 默认配置
         */
        val DEFAULT = MergingConfig(
            yTolerance = 0.4f,
            xToleranceFactor = 1.5f
        )

        /**
         * 游戏预设（更宽松的合并）
         */
        val GAME = MergingConfig(
            yTolerance = 0.5f,
            xToleranceFactor = 2.0f
        )

        /**
         * 漫画预设（更严格的合并）
         */
        val MANGA = MergingConfig(
            yTolerance = 0.3f,
            xToleranceFactor = 1.0f
        )

        /**
         * 文档预设（平衡配置）
         */
        val DOCUMENT = MergingConfig(
            yTolerance = 0.4f,
            xToleranceFactor = 1.5f
        )
    }
}

/**
 * 合并预设枚举
 */
@Serializable
enum class MergingPreset(val displayName: String) {
    /** 游戏预设 */
    GAME("游戏"),

    /** 漫画预设 */
    MANGA("漫画"),

    /** 文档预设 */
    DOCUMENT("文档"),

    /** 自定义 */
    CUSTOM("自定义");

    companion object {
        fun fromConfig(config: MergingConfig): MergingPreset {
            return when (config) {
                MergingConfig.GAME -> GAME
                MergingConfig.MANGA -> MANGA
                MergingConfig.DOCUMENT -> DOCUMENT
                else -> CUSTOM
            }
        }
    }
}

/**
 * 获取预设配置
 */
fun MergingPreset.toConfig(): MergingConfig {
    return when (this) {
        MergingPreset.GAME -> MergingConfig.GAME
        MergingPreset.MANGA -> MergingConfig.MANGA
        MergingPreset.DOCUMENT -> MergingConfig.DOCUMENT
        MergingPreset.CUSTOM -> MergingConfig.DEFAULT
    }
}
