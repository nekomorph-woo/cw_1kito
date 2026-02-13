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
    val xToleranceFactor: Float = 1.5f,

    /** 启用智能聚类（方案 A：自动分析间距分布） */
    val enableSmartClustering: Boolean = false,

    /** 启用二次合并（方案 D：对碎片进行二次合并） */
    val enableSecondPass: Boolean = false,

    /** 二次合并的碎片文本长度阈值（小于此值的文本被视为碎片） */
    val fragmentTextThreshold: Int = 3
) {
    init {
        require(yTolerance in 0.1f..1.0f) {
            "Y 轴阈值必须在 0.1 到 1.0 之间，实际: $yTolerance"
        }
        require(xToleranceFactor in 0.5f..3.0f) {
            "X 轴阈值因子必须在 0.5 到 3.0 之间，实际: $xToleranceFactor"
        }
        require(fragmentTextThreshold in 1..10) {
            "碎片文本阈值必须在 1 到 10 之间，实际: $fragmentTextThreshold"
        }
    }

    companion object {
        /**
         * 默认配置
         */
        val DEFAULT = MergingConfig(
            yTolerance = 0.4f,
            xToleranceFactor = 1.5f,
            enableSmartClustering = false,
            enableSecondPass = false
        )

        /**
         * 游戏预设（更宽松的合并）
         */
        val GAME = MergingConfig(
            yTolerance = 0.5f,
            xToleranceFactor = 2.0f,
            enableSmartClustering = false,
            enableSecondPass = false
        )

        /**
         * 漫画预设（优化日漫标题合并）
         */
        val MANGA = MergingConfig(
            yTolerance = 0.5f,
            xToleranceFactor = 2.5f,
            enableSmartClustering = false,
            enableSecondPass = false
        )

        /**
         * 文档预设（平衡配置）
         */
        val DOCUMENT = MergingConfig(
            yTolerance = 0.4f,
            xToleranceFactor = 1.5f,
            enableSmartClustering = false,
            enableSecondPass = false
        )

        /**
         * 智能聚类预设（方案 A：自动分析间距分布）
         * 适用于 OCR 识别碎片化严重的场景
         */
        val SMART_CLUSTERING = MergingConfig(
            yTolerance = 0.5f,
            xToleranceFactor = 1.5f,
            enableSmartClustering = true,
            enableSecondPass = false
        )

        /**
         * 二次合并预设（方案 D：碎片二次合并）
         * 适用于短文本碎片较多的场景
         */
        val SECOND_PASS = MergingConfig(
            yTolerance = 0.4f,
            xToleranceFactor = 1.5f,
            enableSmartClustering = false,
            enableSecondPass = true,
            fragmentTextThreshold = 3
        )

        /**
         * 混合预设（智能聚类 + 二次合并）
         * 最强大的合并策略
         */
        val AGGRESSIVE = MergingConfig(
            yTolerance = 0.5f,
            xToleranceFactor = 2.0f,
            enableSmartClustering = true,
            enableSecondPass = true,
            fragmentTextThreshold = 4
        )
    }
}

/**
 * 合并预设枚举
 */
@Serializable
enum class MergingPreset(val displayName: String, val description: String) {
    /** 游戏预设 */
    GAME("游戏", "宽松合并，适合游戏界面"),

    /** 漫画预设 */
    MANGA("漫画", "优化日漫标题，更宽松的 X 轴阈值"),

    /** 文档预设 */
    DOCUMENT("文档", "平衡配置，适合常规文本"),

    /** 智能聚类预设 */
    SMART_CLUSTERING("智能聚类", "自动分析间距分布，智能合并"),

    /** 二次合并预设 */
    SECOND_PASS("二次合并", "对短文本碎片进行二次合并"),

    /** 混合预设 */
    AGGRESSIVE("强力合并", "智能聚类 + 二次合并，最大合并"),

    /** 自定义 */
    CUSTOM("自定义", "手动调整参数");

    companion object {
        fun fromConfig(config: MergingConfig): MergingPreset {
            return when (config) {
                MergingConfig.GAME -> GAME
                MergingConfig.MANGA -> MANGA
                MergingConfig.DOCUMENT -> DOCUMENT
                MergingConfig.SMART_CLUSTERING -> SMART_CLUSTERING
                MergingConfig.SECOND_PASS -> SECOND_PASS
                MergingConfig.AGGRESSIVE -> AGGRESSIVE
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
        MergingPreset.SMART_CLUSTERING -> MergingConfig.SMART_CLUSTERING
        MergingPreset.SECOND_PASS -> MergingConfig.SECOND_PASS
        MergingPreset.AGGRESSIVE -> MergingConfig.AGGRESSIVE
        MergingPreset.CUSTOM -> MergingConfig.DEFAULT
    }
}
