package com.cw2.cw_1kito.service.overlay

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
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

    // 双击检测
    private var lastTapTime = 0L
    private val doubleTapTimeout = 300L // 双击时间间隔阈值（毫秒）
    private val doubleTapHandler = Handler(Looper.getMainLooper())
    private val doubleTapRunnable = Runnable {
        // 超时后重置，说明是单击而不是双击
        lastTapTime = 0L
    }

    init {
        // 设置视图为全屏
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // 设置背景为透明
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        Log.d(tag, "TranslationOverlayView created with ${results.size} results")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < doubleTapTimeout) {
                // 双击检测成功
                doubleTapHandler.removeCallbacks(doubleTapRunnable)
                lastTapTime = 0L
                Log.d(tag, "Overlay dismissed by double tap")
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onDismiss()
                return true
            } else {
                // 第一次点击，启动超时检测
                lastTapTime = currentTime
                doubleTapHandler.removeCallbacks(doubleTapRunnable)
                doubleTapHandler.postDelayed(doubleTapRunnable, doubleTapTimeout)
            }
        }
        return super.onTouchEvent(event)
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
