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

    // 进度状态（用于流式翻译）
    private var expectedTotalCount: Int? = null // 预期的总结果数（null 表示未知）

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
        // 触摸事件传递优化：只拦截翻译框区域的触摸事件
        // 其他区域的触摸事件传递给下层 UI，保持原 UI 可点击

        val x = event.x.toInt()
        val y = event.y.toInt()

        // 检查触摸点是否在任何翻译框区域内
        val touchInResultArea = renderer.getDrawAreas(results).any { rect ->
            rect.contains(x, y)
        }

        // 如果触摸点不在任何翻译框内，传递事件给下层 UI
        if (!touchInResultArea) {
            return false // 不消费事件，允许穿透
        }

        // 触摸点在翻译框内，处理双击关闭逻辑
        if (event.action == MotionEvent.ACTION_UP) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < doubleTapTimeout) {
                // 双击检测成功
                doubleTapHandler.removeCallbacks(doubleTapRunnable)
                lastTapTime = 0L
                Log.d(tag, "Overlay dismissed by double tap on translation box")
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

        return true // 消费事件，阻止穿透
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val density = resources.displayMetrics.density

        // 1. 绘制所有翻译结果
        renderer.drawAllResults(canvas, results, density)

        // 2. 绘制进度指示器（仅在流式模式下显示）
        if (expectedTotalCount != null || results.size > 0) {
            // 如果设置了预期总数，使用预期总数；否则只显示当前数量
            renderer.drawProgressIndicator(
                canvas,
                currentCount = results.size,
                totalCount = expectedTotalCount,
                density = density
            )
        }
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
     * 设置预期的总结果数量（用于显示进度指示器）
     * @param total 预期的总数，null 表示未知
     */
    fun setExpectedTotalCount(total: Int?) {
        expectedTotalCount = total
        invalidate()
        Log.d(tag, "Set expected total count: $total")
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
     * 获取绘图区域（用于调试和触摸检测）
     */
    fun getDrawAreas(): List<android.graphics.Rect> {
        return renderer.getDrawAreas(results)
    }

    /**
     * 清空所有翻译结果并触发重绘（必须在主线程调用）
     */
    fun clear() {
        results.clear()
        invalidate()
        Log.d(tag, "Cleared all results")
    }
}
