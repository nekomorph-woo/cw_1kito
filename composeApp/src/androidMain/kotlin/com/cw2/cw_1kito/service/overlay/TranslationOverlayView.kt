package com.cw2.cw_1kito.service.overlay

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.cw2.cw_1kito.model.TranslationResult

/**
 * 翻译覆盖层视图
 *
 * 在屏幕上绘制翻译结果，显示白色背景框和翻译文本
 * 支持流式模式：通过 addResult() 逐条添加翻译结果
 */
class TranslationOverlayView(
    context: Context,
    initialResults: List<TranslationResult> = emptyList(),
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val onDismiss: () -> Unit
) : View(context) {

    private val tag = "TranslationOverlayView"

    // 内部可变列表，支持流式增量添加
    private val results = mutableListOf<TranslationResult>().apply {
        addAll(initialResults)
    }

    private val renderer = OverlayRenderer(screenWidth, screenHeight)

    init {
        // 设置视图为全屏
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 设置背景为透明
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        // 点击任意位置关闭覆盖层
        setOnClickListener {
            Log.d(tag, "Overlay dismissed by click")
            onDismiss()
        }

        Log.d(tag, "TranslationOverlayView created with ${results.size} results")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density
        renderer.drawAllResults(canvas, results, density)
    }

    /**
     * 增量添加单条结果并触发重绘（必须在主线程调用）
     */
    fun addResult(result: TranslationResult) {
        results.add(result)
        invalidate()
        Log.d(tag, "Added result, total: ${results.size}")
    }

    /**
     * 增量添加多条结果并触发重绘（必须在主线程调用）
     */
    fun addResults(newResults: List<TranslationResult>) {
        results.addAll(newResults)
        invalidate()
        Log.d(tag, "Added ${newResults.size} results, total: ${results.size}")
    }

    /**
     * 更新翻译结果并重绘
     */
    fun updateResults(newResults: List<TranslationResult>) {
        results.clear()
        results.addAll(newResults)
        invalidate()
    }

    /**
     * 获取当前结果数量
     */
    fun getResultCount(): Int = results.size

    /**
     * 获取绘图区域（用于调试）
     */
    fun getDrawAreas(): List<android.graphics.Rect> {
        return renderer.getDrawAreas(results)
    }
}
