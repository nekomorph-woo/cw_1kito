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
 */
class TranslationOverlayView(
    context: Context,
    private val results: List<TranslationResult>,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val onDismiss: () -> Unit
) : View(context) {

    private val tag = "TranslationOverlayView"

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
     * 更新翻译结果并重绘
     */
    fun updateResults(newResults: List<TranslationResult>) {
        (results as? ArrayList)?.clear()
        (results as? ArrayList)?.addAll(newResults)
        invalidate()
    }

    /**
     * 获取绘图区域（用于调试）
     */
    fun getDrawAreas(): List<android.graphics.Rect> {
        return renderer.getDrawAreas(results)
    }
}
