package com.cw2.cw_1kito.service.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.cw2.cw_1kito.model.TranslationResult
import android.graphics.Rect as AndroidRect

/**
 * 覆盖层渲染器
 *
 * 使用 StaticLayout 进行文本排版，框体根据文本自适应扩展
 */
class OverlayRenderer(
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    private val tag = "OverlayRenderer"

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        alpha = 240
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#DDDDDD")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        alpha = 200
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#333333")
        style = Paint.Style.FILL
    }

    private val cornerRadius = 6f // dp

    // 进度指示器 Paint
    private val progressBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        alpha = 180
    }

    private val progressTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
    }

    /**
     * 绘制进度指示器（正在翻译: 3/10）
     * @param canvas 画布
     * @param currentCount 当前已完成数量
     * @param totalCount 预计总数（可选，null 表示未知）
     * @param density 屏幕密度
     */
    fun drawProgressIndicator(
        canvas: Canvas,
        currentCount: Int,
        totalCount: Int?,
        density: Float
    ) {
        if (currentCount <= 0) return

        val textSize = 14f * density
        progressTextPaint.textSize = textSize

        // 构建进度文本
        val progressText = if (totalCount != null && totalCount > 0) {
            "正在翻译: $currentCount/$totalCount"
        } else {
            "正在翻译: $currentCount"
        }

        // 计算文本宽度和高度
        val textWidth = progressTextPaint.measureText(progressText)
        val textHeight = textSize * 1.2f // 估算行高

        // 计算背景框大小
        val padding = 8f * density
        val bgWidth = textWidth + padding * 2
        val bgHeight = textHeight + padding * 2

        // 位置：屏幕顶部居中，距离顶部 20dp
        val xPos = (screenWidth - bgWidth) / 2f
        val yPos = 20f * density

        // 绘制半透明黑色背景
        val bgRect = RectF(xPos, yPos, xPos + bgWidth, yPos + bgHeight)
        canvas.drawRoundRect(bgRect, 4f * density, 4f * density, progressBackgroundPaint)

        // 绘制白色文本（垂直居中）
        val textX = xPos + bgWidth / 2f
        val textY = yPos + padding + textHeight - textSize * 0.2f // 垂直居中微调
        canvas.drawText(progressText, textX, textY, progressTextPaint)
    }

    /**
     * 绘制单个翻译结果
     */
    fun drawTranslationResult(canvas: Canvas, result: TranslationResult, density: Float) {
        val screenRect = result.boundingBox.toScreenRect(screenWidth, screenHeight)

        // 原始坐标框
        val origLeft = screenRect.left
        val origTop = screenRect.top
        val origRight = screenRect.right
        val origBottom = screenRect.bottom
        val origWidth = origRight - origLeft
        val origHeight = origBottom - origTop

        // 容错
        if (origWidth < 5 || origHeight < 5) return
        if (origLeft >= screenWidth || origTop >= screenHeight) return
        if (origRight <= 0 || origBottom <= 0) return

        val text = result.translatedText
        if (text.isBlank()) return

        val paddingPx = (4f * density).toInt() // 减小 padding

        // 根据原始框的高度来估算合适的字号
        val textSize = calculateTextSize(text, origWidth, origHeight, paddingPx, density)
        textPaint.textSize = textSize

        // 使用 StaticLayout 排版文字
        val textAvailWidth = (origWidth - 2 * paddingPx).coerceAtLeast(1)
        val staticLayout = createStaticLayout(text, textAvailWidth)

        // 计算实际需要的框体大小（根据排版后的文字高度）
        val textHeight = staticLayout.height
        val neededHeight = textHeight + 2 * paddingPx

        // 框体自适应：如果文字比原始框高，扩展框体
        val finalHeight = maxOf(origHeight, neededHeight)
        // 框体宽度：保持原始宽度（文字已经按原始宽度换行了）
        val finalWidth = origWidth

        // 确定最终绘制位置（以原始框中心为参考点垂直居中）
        val centerY = origTop + origHeight / 2
        val drawTop = (centerY - finalHeight / 2).coerceAtLeast(0)
        val drawBottom = (drawTop + finalHeight).coerceAtMost(screenHeight)
        val drawLeft = origLeft.coerceAtLeast(0)
        val drawRight = (drawLeft + finalWidth).coerceAtMost(screenWidth)

        val drawRect = RectF(
            drawLeft.toFloat(),
            drawTop.toFloat(),
            drawRight.toFloat(),
            drawBottom.toFloat()
        )

        val radiusPx = cornerRadius * density

        // 绘制白色圆角背景
        canvas.drawRoundRect(drawRect, radiusPx, radiusPx, backgroundPaint)

        // 绘制边框
        canvas.drawRoundRect(drawRect, radiusPx, radiusPx, borderPaint)

        // 绘制文字（垂直居中）
        val textStartY = drawTop + paddingPx +
            ((drawBottom - drawTop - 2 * paddingPx - textHeight) / 2).coerceAtLeast(0)

        canvas.save()
        canvas.translate(
            (drawLeft + paddingPx).toFloat(),
            textStartY.toFloat()
        )
        staticLayout.draw(canvas)
        canvas.restore()
    }

    /**
     * 计算合适的文字大小
     *
     * 策略：以原始框高度为基准，让文字尽量填满框体
     */
    private fun calculateTextSize(
        text: String,
        boxWidth: Int,
        boxHeight: Int,
        paddingPx: Int,
        density: Float
    ): Float {
        val minSizePx = 10f * density
        val maxSizePx = 22f * density

        val availWidth = (boxWidth - 2 * paddingPx).coerceAtLeast(1)
        val availHeight = (boxHeight - 2 * paddingPx).coerceAtLeast(1)

        // 从一个合理的初始字号开始（基于框高度）
        var testSize = (availHeight * 0.7f).coerceIn(minSizePx, maxSizePx)
        textPaint.textSize = testSize

        // 用二分法找到最佳字号：让文字刚好放进框内
        var lo = minSizePx
        var hi = testSize
        var bestSize = lo

        repeat(8) {
            val mid = (lo + hi) / 2
            textPaint.textSize = mid
            val layout = createStaticLayout(text, availWidth)
            if (layout.height <= availHeight) {
                bestSize = mid
                lo = mid
            } else {
                hi = mid
            }
        }

        return bestSize.coerceIn(minSizePx, maxSizePx)
    }

    /**
     * 创建 StaticLayout（兼容不同 API 版本）
     */
    private fun createStaticLayout(text: String, width: Int): StaticLayout {
        val safeWidth = width.coerceAtLeast(1)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, textPaint, safeWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text, textPaint, safeWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1.1f, 0f, false
            )
        }
    }

    /**
     * 绘制所有翻译结果
     */
    fun drawAllResults(canvas: Canvas, results: List<TranslationResult>, density: Float) {
        results.forEach { result ->
            try {
                drawTranslationResult(canvas, result, density)
            } catch (e: Exception) {
                Log.e(tag, "Failed to draw result: ${result.translatedText}", e)
            }
        }
    }

    /**
     * 获取绘图区域（用于点击检测）
     */
    fun getDrawAreas(results: List<TranslationResult>): List<AndroidRect> {
        return results.map {
            val screenRect = it.boundingBox.toScreenRect(screenWidth, screenHeight)
            AndroidRect(
                screenRect.left,
                screenRect.top,
                screenRect.right,
                screenRect.bottom
            )
        }
    }
}
