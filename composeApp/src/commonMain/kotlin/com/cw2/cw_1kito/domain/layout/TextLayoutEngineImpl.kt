package com.cw2.cw_1kito.domain.layout

import com.cw2.cw_1kito.model.BoundingBox
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 文本排版常量
 */
internal object TextLayoutConstants {
    const val LINE_HEIGHT_MULTIPLIER = 1.2f
    const val AVG_CHAR_WIDTH_RATIO = 0.5f
    const val CHINESE_CHAR_WIDTH_RATIO = 1.0f
    const val ENGLISH_CHAR_WIDTH_RATIO = 0.6f
}

/**
 * 文本排版引擎实现
 *
 * 使用二分查找确定最优字号，支持多种排版策略。
 */
class TextLayoutEngineImpl(
    private val config: TextLayoutConfig = TextLayoutConfig.DEFAULT
) : TextLayoutEngine {

    override fun calculateLayout(
        text: String,
        boundingBox: BoundingBox,
        screenWidth: Int,
        screenHeight: Int
    ): TextLayout {
        if (text.isEmpty()) {
            return createEmptyLayout(boundingBox, screenWidth, screenHeight)
        }

        // 转换为屏幕坐标
        val screenRect = boundingBox.toScreenRect(screenWidth, screenHeight)
        val padding = config.padding * screenWidth / 360f // 根据 DPI 调整内边距

        // 可用宽度和高度
        val availableWidth = (screenRect.width - 2 * padding).coerceAtLeast(1f)
        val availableHeight = (screenRect.height - 2 * padding).coerceAtLeast(1f)

        // 根据策略计算布局
        return when (config.strategy) {
            LayoutStrategy.FIXED_SIZE -> calculateFixedLayout(
                text, screenRect, availableWidth, availableHeight, padding
            )
            LayoutStrategy.ADJUST_SIZE -> calculateAdjustSizeLayout(
                text, screenRect, availableWidth, availableHeight, padding
            )
            LayoutStrategy.EXPAND_BOX -> calculateExpandBoxLayout(
                text, boundingBox, screenRect, availableWidth, padding
            )
            LayoutStrategy.MULTI_LINE -> calculateMultiLineLayout(
                text, screenRect, availableWidth, availableHeight, padding
            )
        }
    }

    override fun measureText(
        text: String,
        textSize: Float,
        maxWidth: Float
    ): TextMetrics {
        if (text.isEmpty()) {
            return TextMetrics(0f, textSize * TextLayoutConstants.LINE_HEIGHT_MULTIPLIER, 1, false)
        }

        // 估算文本宽度
        val estimatedWidth = estimateTextWidth(text, textSize)

        // 检查是否需要换行
        val requiresWrap = estimatedWidth > maxWidth

        // 计算行数
        val lineCount = if (requiresWrap) {
            val charsPerLine = (maxWidth / (textSize * TextLayoutConstants.AVG_CHAR_WIDTH_RATIO)).toInt()
            ceil(text.length.toFloat() / maxOf(1, charsPerLine)).toInt()
        } else {
            1
        }

        // 估算高度
        val height = lineCount * textSize * TextLayoutConstants.LINE_HEIGHT_MULTIPLIER

        return TextMetrics(
            width = minOf(estimatedWidth, maxWidth),
            height = height,
            lineCount = lineCount,
            requiresWrap = requiresWrap
        )
    }

    /**
     * 创建空布局
     */
    private fun createEmptyLayout(
        boundingBox: BoundingBox,
        screenWidth: Int,
        screenHeight: Int
    ): TextLayout {
        return TextLayout(
            textSize = config.defaultTextSize,
            lines = emptyList(),
            lineBounds = emptyList(),
            isOverflow = false,
            padding = config.padding
        )
    }

    /**
     * 固定字号布局
     */
    private fun calculateFixedLayout(
        text: String,
        screenRect: com.cw2.cw_1kito.model.ScreenRect,
        availableWidth: Float,
        availableHeight: Float,
        padding: Float
    ): TextLayout {
        val textSize = config.defaultTextSize
        val lines = wrapText(text, textSize, availableWidth)
        val lineHeight = textSize * TextLayoutConstants.LINE_HEIGHT_MULTIPLIER
        val totalHeight = lines.size * lineHeight

        return TextLayout(
            textSize = textSize,
            lines = lines,
            lineBounds = calculateLineBounds(lines, screenRect, padding, lineHeight),
            isOverflow = totalHeight > availableHeight || lines.lastIndex >= config.maxLines,
            padding = padding
        )
    }

    /**
     * 自适应字号布局
     */
    private fun calculateAdjustSizeLayout(
        text: String,
        screenRect: com.cw2.cw_1kito.model.ScreenRect,
        availableWidth: Float,
        availableHeight: Float,
        padding: Float
    ): TextLayout {
        // 使用二分查找找到合适的字号
        val optimalSize = findOptimalTextSize(
            text = text,
            maxWidth = availableWidth,
            maxHeight = availableHeight,
            minSize = config.minTextSize,
            maxSize = config.maxTextSize
        )

        val lines = wrapText(text, optimalSize, availableWidth)
        val lineHeight = optimalSize * TextLayoutConstants.LINE_HEIGHT_MULTIPLIER
        val totalHeight = lines.size * lineHeight

        return TextLayout(
            textSize = optimalSize,
            lines = lines,
            lineBounds = calculateLineBounds(lines, screenRect, padding, lineHeight),
            isOverflow = totalHeight > availableHeight || lines.lastIndex >= config.maxLines,
            padding = padding
        )
    }

    /**
     * 扩展框体布局
     */
    private fun calculateExpandBoxLayout(
        text: String,
        originalBox: BoundingBox,
        screenRect: com.cw2.cw_1kito.model.ScreenRect,
        availableWidth: Float,
        padding: Float
    ): TextLayout {
        val textSize = config.defaultTextSize
        val metrics = measureText(text, textSize, Float.MAX_VALUE)
        val requiredWidth = metrics.width + 2 * padding
        val requiredHeight = metrics.height + 2 * padding

        // 计算建议的框体
        val suggestedBox = if (metrics.width > availableWidth || metrics.height > (screenRect.height - 2 * padding)) {
            val expandX = maxOf(0f, (requiredWidth - screenRect.width) / 2 / screenRect.width)
            val expandY = maxOf(0f, (requiredHeight - screenRect.height) / 2 / screenRect.height)

            originalBox.expandToMinSize(
                minSize = maxOf(originalBox.width, originalBox.height) + maxOf(expandX, expandY)
            )
        } else {
            null
        }

        val lines = listOf(text)
        val lineHeight = textSize * TextLayoutConstants.LINE_HEIGHT_MULTIPLIER

        return TextLayout(
            textSize = textSize,
            lines = lines,
            lineBounds = calculateLineBounds(lines, screenRect, padding, lineHeight),
            isOverflow = false,
            suggestedBox = suggestedBox,
            padding = padding
        )
    }

    /**
     * 多行布局
     */
    private fun calculateMultiLineLayout(
        text: String,
        screenRect: com.cw2.cw_1kito.model.ScreenRect,
        availableWidth: Float,
        availableHeight: Float,
        padding: Float
    ): TextLayout {
        // 先尝试用默认字号
        var textSize = config.defaultTextSize
        var lines = wrapText(text, textSize, availableWidth)
        var lineHeight = textSize * TextLayoutConstants.LINE_HEIGHT_MULTIPLIER

        // 如果默认字号下行数过多，减小字号
        while (lines.size > config.maxLines && textSize > config.minTextSize) {
            textSize -= 1f
            lines = wrapText(text, textSize, availableWidth)
            lineHeight = textSize * TextLayoutConstants.LINE_HEIGHT_MULTIPLIER
        }

        val totalHeight = lines.size * lineHeight

        return TextLayout(
            textSize = textSize,
            lines = lines,
            lineBounds = calculateLineBounds(lines, screenRect, padding, lineHeight),
            isOverflow = totalHeight > availableHeight || lines.size > config.maxLines,
            padding = padding
        )
    }

    /**
     * 使用二分查找找到最优字号
     */
    private fun findOptimalTextSize(
        text: String,
        maxWidth: Float,
        maxHeight: Float,
        minSize: Float,
        maxSize: Float
    ): Float {
        var low = minSize
        var high = maxSize
        var optimalSize = minSize

        while (high - low > config.binarySearchPrecision) {
            val mid = (low + high) / 2
            val metrics = measureText(text, mid, maxWidth)

            if (metrics.height <= maxHeight && !metrics.requiresWrap) {
                optimalSize = mid
                low = mid
            } else if (metrics.height <= maxHeight) {
                // 可以放下但需要换行，尝试更大的字号
                optimalSize = mid
                low = mid
            } else {
                high = mid
            }
        }

        return optimalSize.coerceIn(minSize, maxSize)
    }

    /**
     * 文本换行
     */
    private fun wrapText(text: String, textSize: Float, maxWidth: Float): List<String> {
        if (text.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        val currentLine = StringBuilder()
        var currentWidth = 0f

        for (char in text) {
            val charWidth = estimateCharWidth(char, textSize)

            if (currentWidth + charWidth > maxWidth && currentLine.isNotEmpty()) {
                // 需要换行
                lines.add(currentLine.toString())
                currentLine.clear()
                currentLine.append(char)
                currentWidth = charWidth
            } else if (char == '\n') {
                // 显式换行
                lines.add(currentLine.toString())
                currentLine.clear()
                currentWidth = 0f
            } else {
                currentLine.append(char)
                currentWidth += charWidth
            }
        }

        // 添加最后一行
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines.take(config.maxLines)
    }

    /**
     * 估算文本宽度
     */
    private fun estimateTextWidth(text: String, textSize: Float): Float {
        return text.sumOf { estimateCharWidth(it, textSize).toDouble() }.toFloat()
    }

    /**
     * 估算字符宽度
     */
    private fun estimateCharWidth(char: Char, textSize: Float): Float {
        return when {
            char.isChineseCharacter() -> textSize * TextLayoutConstants.CHINESE_CHAR_WIDTH_RATIO
            char.isLetter() || char.isDigit() -> textSize * TextLayoutConstants.ENGLISH_CHAR_WIDTH_RATIO
            else -> textSize * TextLayoutConstants.AVG_CHAR_WIDTH_RATIO
        }
    }

    /**
     * 计算每行的边界
     */
    private fun calculateLineBounds(
        lines: List<String>,
        screenRect: com.cw2.cw_1kito.model.ScreenRect,
        padding: Float,
        lineHeight: Float
    ): List<LineBound?> {
        return lines.mapIndexed { index, _ ->
            LineBound(
                index = index,
                left = screenRect.left + padding,
                top = screenRect.top + padding + index * lineHeight,
                right = screenRect.right - padding,
                bottom = screenRect.top + padding + (index + 1) * lineHeight
            )
        }
    }

    /**
     * 扩展函数：判断字符是否为中文字符
     */
    private fun Char.isChineseCharacter(): Boolean {
        val codePoint = code
        return codePoint in 0x4E00..0x9FFF ||
               codePoint in 0x3400..0x4DBF ||
               codePoint in 0x20000..0x2A6DF ||
               codePoint in 0x2A700..0x2B73F ||
               codePoint in 0x2B740..0x2B81F ||
               codePoint in 0x2B820..0x2CEAF ||
               codePoint in 0xF900..0xFAFF ||
               codePoint in 0x2F800..0x2FA1F
    }
}
