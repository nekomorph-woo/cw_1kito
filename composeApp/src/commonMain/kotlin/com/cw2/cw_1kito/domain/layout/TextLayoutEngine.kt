package com.cw2.cw_1kito.domain.layout

import com.cw2.cw_1kito.model.BoundingBox

/**
 * 文本排版引擎接口
 *
 * 负责处理翻译后文本的自适应排版，确保文本在矩形框内正确显示。
 */
interface TextLayoutEngine {
    /**
     * 计算文本布局
     *
     * @param text 要排版的文本
     * @param boundingBox 归一化边界框
     * @param screenWidth 屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @return 文本布局结果
     */
    fun calculateLayout(
        text: String,
        boundingBox: BoundingBox,
        screenWidth: Int,
        screenHeight: Int
    ): TextLayout

    /**
     * 测量文本尺寸
     *
     * @param text 要测量的文本
     * @param textSize 字体大小（sp）
     * @param maxWidth 最大宽度（像素）
     * @return 文本测量结果
     */
    fun measureText(
        text: String,
        textSize: Float,
        maxWidth: Float
    ): TextMetrics
}

/**
 * 文本布局结果
 */
data class TextLayout(
    /** 字体大小（sp） */
    val textSize: Float,

    /** 分行后的文本列表 */
    val lines: List<String>,

    /** 每行的边界（屏幕坐标），如果无法计算则为 null */
    val lineBounds: List<LineBound?>,

    /** 是否溢出（文本无法完全显示） */
    val isOverflow: Boolean,

    /** 建议的框体（如果溢出） */
    val suggestedBox: BoundingBox? = null,

    /** 实际使用的内边距（像素） */
    val padding: Float
) {
    /** 行数 */
    val lineCount: Int get() = lines.size

    /** 是否为空布局 */
    val isEmpty: Boolean get() = lines.isEmpty() || lines.all { it.isEmpty() }

    /** 总文本高度（估算） */
    val totalHeight: Float
        get() = lineCount * textSize * LINE_HEIGHT_MULTIPLIER
}

/**
 * 单行边界信息
 */
data class LineBound(
    /** 行索引 */
    val index: Int,
    /** 左边距（像素） */
    val left: Float,
    /** 上边距（像素） */
    val top: Float,
    /** 右边距（像素） */
    val right: Float,
    /** 下边距（像素） */
    val bottom: Float
) {
    /** 行宽度 */
    val width: Float get() = right - left

    /** 行高度 */
    val height: Float get() = bottom - top
}

/**
 * 文本测量结果
 */
data class TextMetrics(
    /** 文本宽度（像素） */
    val width: Float,

    /** 文本高度（像素） */
    val height: Float,

    /** 行数 */
    val lineCount: Int,

    /** 是否需要换行 */
    val requiresWrap: Boolean
)

/**
 * 排版策略
 */
enum class LayoutStrategy {
    /** 固定字号，超出截断 */
    FIXED_SIZE,

    /** 自适应字号（调整字号以适应框体） */
    ADJUST_SIZE,

    /** 扩展框体（保持字号，扩大框体） */
    EXPAND_BOX,

    /** 多行换行（自动换行） */
    MULTI_LINE
}

/**
 * 排版配置
 */
data class TextLayoutConfig(
    /** 排版策略 */
    val strategy: LayoutStrategy = LayoutStrategy.ADJUST_SIZE,

    /** 最小字号（sp） */
    val minTextSize: Float = 12f,

    /** 最大字号（sp） */
    val maxTextSize: Float = 24f,

    /** 默认字号（sp） */
    val defaultTextSize: Float = 16f,

    /** 行高倍数 */
    val lineHeightMultiplier: Float = 1.2f,

    /** 内边距（dp） */
    val padding: Float = 8f,

    /** 是否考虑中英文混排 */
    val considerMixedText: Boolean = true,

    /** 二分查找精度 */
    val binarySearchPrecision: Float = 0.5f,

    /** 最大行数限制 */
    val maxLines: Int = 10
) {
    companion object {
        /** 默认配置 */
        val DEFAULT = TextLayoutConfig()

        /** 紧凑配置（更多文本） */
        val COMPACT = TextLayoutConfig(
            strategy = LayoutStrategy.ADJUST_SIZE,
            minTextSize = 10f,
            maxTextSize = 18f,
            defaultTextSize = 14f,
            padding = 4f
        )

        /** 宽松配置（更大字号） */
        val SPACIOUS = TextLayoutConfig(
            strategy = LayoutStrategy.ADJUST_SIZE,
            minTextSize = 14f,
            maxTextSize = 32f,
            defaultTextSize = 20f,
            padding = 12f
        )
    }
}

/** 行高倍数常量 */
private const val LINE_HEIGHT_MULTIPLIER = 1.2f

/** 平均字符宽度估算（相对于字号） */
private const val AVG_CHAR_WIDTH_RATIO = 0.6f

/** 中文字符宽度估算（相对于字号） */
private const val CHINESE_CHAR_WIDTH_RATIO = 1.0f

/** 英文字符宽度估算（相对于字号） */
private const val ENGLISH_CHAR_WIDTH_RATIO = 0.5f
