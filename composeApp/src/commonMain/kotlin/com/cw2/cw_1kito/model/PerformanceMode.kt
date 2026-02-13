package com.cw2.cw_1kito.model

import kotlinx.serialization.Serializable

/**
 * 性能模式枚举
 *
 * 定义不同的性能模式，平衡速度和精度
 */
@Serializable
enum class PerformanceMode(val displayName: String, val description: String) {
    /**
     * 极速模式：短边 672px，本地模型
     */
    FAST("极速模式", "短边 672px，本地模型"),

    /**
     * 平衡模式：短边 896px，混合模型
     */
    BALANCED("平衡模式", "短边 896px，混合模型"),

    /**
     * 高精模式：短边 1080px，云端 VL 32B
     */
    QUALITY("高精模式", "短边 1080px，云端 VL 32B");

    companion object {
        val DEFAULT = BALANCED
    }
}

/** 图像短边尺寸（像素） */
val PerformanceMode.imageSize: Int
    get() = when (this) {
        PerformanceMode.FAST -> 672
        PerformanceMode.BALANCED -> 896
        PerformanceMode.QUALITY -> 1080
    }

/** 预期 OCR 时间范围（毫秒） */
val PerformanceMode.expectedOcrTimeMs: IntRange
    get() = when (this) {
        PerformanceMode.FAST -> 100..250
        PerformanceMode.BALANCED -> 250..500
        PerformanceMode.QUALITY -> 500..1000
    }

/** 是否使用本地模型 */
val PerformanceMode.useLocalModel: Boolean
    get() = when (this) {
        PerformanceMode.FAST, PerformanceMode.BALANCED -> true
        PerformanceMode.QUALITY -> false  // 高精模式使用云端 VL 32B 模型
    }

/**
 * 获取预估延迟
 */
fun PerformanceMode.getEstimatedLatency(): String {
    return when (this) {
        PerformanceMode.FAST -> "<250ms"
        PerformanceMode.BALANCED -> "<500ms"
        PerformanceMode.QUALITY -> "<1s"
    }
}

/**
 * 获取图片分辨率（短边）
 */
fun PerformanceMode.getImageResolution(): Int = imageSize

/**
 * 从图像尺寸推断性能模式
 * @param imageSize 图像短边尺寸（像素）
 */
fun PerformanceMode.fromImageSize(imageSize: Int): PerformanceMode {
    return when {
        imageSize <= 700 -> PerformanceMode.FAST
        imageSize <= 900 -> PerformanceMode.BALANCED
        else -> PerformanceMode.QUALITY
    }
}

/**
 * 从显示名称获取性能模式
 */
fun PerformanceMode.fromDisplayName(displayName: String): PerformanceMode? {
    return PerformanceMode.entries.find { it.displayName == displayName }
}
