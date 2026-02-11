package com.cw2.cw_1kito.service.floating

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * 悬浮球视图
 *
 * 可拖拽的悬浮球，支持以下状态：
 * - IDLE: 空闲状态（半透明灰色）
 * - LOADING: 加载状态（旋转蓝色圆环）
 * - SUCCESS: 成功状态（绿色闪烁）
 * - ERROR: 错误状态（红色闪烁）
 */
class FloatingBallView(
    context: Context,
    private val windowManager: WindowManager
) : View(context) {

    companion object {
        private const val BALL_SIZE_DP = 48
    }

    // 颜色常量（不能是 const val，因为调用非常量函数）
    private val BALL_COLOR = Color.parseColor("#4285F4")
    private val BALL_COLOR_IDLE = Color.parseColor("#9E9E9E")
    private val BALL_COLOR_SUCCESS = Color.parseColor("#4CAF50")
    private val BALL_COLOR_ERROR = Color.parseColor("#F44336")
    private val BALL_COLOR_LOADING = Color.parseColor("#2196F3")

    private val ballSize: Int
    private val density: Float

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BALL_COLOR_IDLE
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 2f, Color.parseColor("#40000000"))
    }

    private val loadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BALL_COLOR_LOADING
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private var loadingAngle = 0f
    private var loadingAnimator: ValueAnimator? = null

    // 状态
    @State
    private var currentState: Int = FloatingService.STATE_IDLE

    // 拖拽相关
    private var isDragging = false
    private var lastX = 0f
    private var lastY = 0f
    private var pressStartTime = 0L
    private val touchSlop: Int
    private var isLongPressTriggered = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (!isDragging) {
            isLongPressTriggered = true
            // 震动反馈
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            longClickCallback?.invoke()
        }
    }

    // 回调
    private var clickCallback: (() -> Unit)? = null
    private var longClickCallback: (() -> Unit)? = null
    private var dragCallback: ((x: Int, y: Int) -> Unit)? = null

    init {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        density = metrics.density
        ballSize = (BALL_SIZE_DP * density).toInt()

        // 计算触摸阈值
        touchSlop = (8 * density).toInt()

        // 设置视图大小
        val size = ballSize + (16 * density).toInt() // 加上加载指示器的空间
        layoutParams = WindowManager.LayoutParams(
            size,
            size
        )

        // 启用阴影
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = ballSize + (16 * density).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = ballSize / 2f

        // 绘制加载状态
        if (currentState == FloatingService.STATE_LOADING) {
            val loadingRadius = radius + 6f
            canvas.save()
            canvas.rotate(loadingAngle, centerX, centerY)
            canvas.drawArc(
                centerX - loadingRadius,
                centerY - loadingRadius,
                centerX + loadingRadius,
                centerY + loadingRadius,
                0f,
                270f,
                false,
                loadingPaint
            )
            canvas.restore()
        }

        // 绘制悬浮球
        canvas.drawCircle(centerX, centerY, radius, ballPaint)

        // 绘制图标（根据状态）
        drawIcon(canvas, centerX, centerY, radius * 0.6f)
    }

    /**
     * 绘制状态图标
     */
    private fun drawIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
            strokeCap = Paint.Cap.ROUND
        }

        when (currentState) {
            FloatingService.STATE_SUCCESS -> {
                // 绘制对勾
                val path = android.graphics.Path()
                path.moveTo(cx - size * 0.3f, cy)
                path.lineTo(cx - size * 0.1f, cy + size * 0.2f)
                path.lineTo(cx + size * 0.3f, cy - size * 0.2f)
                canvas.drawPath(path, iconPaint)
            }
            FloatingService.STATE_ERROR -> {
                // 绘制叉号
                canvas.drawLine(
                    cx - size * 0.2f, cy - size * 0.2f,
                    cx + size * 0.2f, cy + size * 0.2f,
                    iconPaint
                )
                canvas.drawLine(
                    cx + size * 0.2f, cy - size * 0.2f,
                    cx - size * 0.2f, cy + size * 0.2f,
                    iconPaint
                )
            }
            else -> {
                // 绘制默认图标（翻译符号）
                canvas.drawLine(
                    cx - size * 0.3f, cy - size * 0.2f,
                    cx - size * 0.3f, cy + size * 0.2f,
                    iconPaint
                )
                canvas.drawLine(
                    cx - size * 0.3f, cy - size * 0.2f,
                    cx, cy - size * 0.2f,
                    iconPaint
                )
                canvas.drawLine(
                    cx, cy - size * 0.2f,
                    cx + size * 0.2f, cy,
                    iconPaint
                )
                canvas.drawLine(
                    cx + size * 0.2f, cy,
                    cx - size * 0.2f, cy,
                    iconPaint
                )
                canvas.drawLine(
                    cx - size * 0.2f, cy,
                    cx, cy + size * 0.2f,
                    iconPaint
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = false
                isLongPressTriggered = false
                pressStartTime = System.currentTimeMillis()
                lastX = event.rawX
                lastY = event.rawY
                // 启动长按计时
                longPressHandler.postDelayed(longPressRunnable, 500)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY

                // 检查是否超过触摸阈值
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                    // 开始拖拽，取消长按
                    longPressHandler.removeCallbacks(longPressRunnable)
                }

                if (isDragging) {
                    updatePosition(dx.toInt(), dy.toInt())
                    lastX = event.rawX
                    lastY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 取消长按计时
                longPressHandler.removeCallbacks(longPressRunnable)

                if (!isDragging && !isLongPressTriggered) {
                    // 短按点击事件
                    performClick()
                } else if (isDragging) {
                    // 拖拽结束，应用磁吸效果
                    applySnapToEdge()
                }
                // 长按事件已经在 longPressRunnable 中处理过了

                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 更新位置
     */
    private fun updatePosition(dx: Int, dy: Int) {
        val layoutParams = layoutParams as? WindowManager.LayoutParams ?: return

        var newX = layoutParams.x + dx
        var newY = layoutParams.y + dy

        // 边界检查
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        // 限制在屏幕范围内（不做磁吸限制，允许自由移动）
        newX = newX.coerceIn(0, screenWidth - width)
        newY = newY.coerceIn(0, screenHeight - height)

        layoutParams.x = newX
        layoutParams.y = newY

        dragCallback?.invoke(newX, newY)

        // 更新布局
        windowManager.updateViewLayout(this, layoutParams)
    }

    /**
     * 拖拽结束后应用磁吸效果
     */
    private fun applySnapToEdge() {
        val layoutParams = layoutParams as? WindowManager.LayoutParams ?: return

        val screenWidth = resources.displayMetrics.widthPixels
        val centerX = layoutParams.x + width / 2f

        // 根据中心点位置决定吸附到左还是右边缘
        val newX = if (centerX < screenWidth / 2f) {
            0  // 吸附到左边缘
        } else {
            screenWidth - width  // 吸附到右边缘
        }

        layoutParams.x = newX
        windowManager.updateViewLayout(this, layoutParams)
    }

    override fun performClick(): Boolean {
        super.performClick()
        clickCallback?.invoke()
        return true
    }

    /**
     * 设置点击监听器
     */
    fun setOnClickListener(listener: () -> Unit) {
        clickCallback = listener
    }

    /**
     * 设置长按监听器
     */
    fun setOnLongClickListener(listener: () -> Unit) {
        longClickCallback = listener
    }

    /**
     * 设置拖拽监听器
     */
    fun setOnDragListener(listener: (x: Int, y: Int) -> Unit) {
        dragCallback = listener
    }

    /**
     * 设置加载状态
     */
    fun setLoadingState(@State state: Int) {
        val oldState = currentState
        currentState = state

        when (state) {
            FloatingService.STATE_IDLE -> {
                ballPaint.color = BALL_COLOR_IDLE
                stopLoadingAnimation()
            }
            FloatingService.STATE_LOADING -> {
                ballPaint.color = BALL_COLOR_LOADING
                startLoadingAnimation()
            }
            FloatingService.STATE_SUCCESS -> {
                ballPaint.color = BALL_COLOR_SUCCESS
                stopLoadingAnimation()
                // 2秒后恢复空闲状态
                postDelayed({
                    if (currentState == FloatingService.STATE_SUCCESS) {
                        setLoadingState(FloatingService.STATE_IDLE)
                    }
                }, 2000)
            }
            FloatingService.STATE_ERROR -> {
                ballPaint.color = BALL_COLOR_ERROR
                stopLoadingAnimation()
                // 2秒后恢复空闲状态
                postDelayed({
                    if (currentState == FloatingService.STATE_ERROR) {
                        setLoadingState(FloatingService.STATE_IDLE)
                    }
                }, 2000)
            }
        }

        if (oldState != state) {
            invalidate()
        }
    }

    /**
     * 开始加载动画
     */
    private fun startLoadingAnimation() {
        if (loadingAnimator?.isRunning == true) return

        loadingAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { animator ->
                loadingAngle = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * 停止加载动画
     */
    private fun stopLoadingAnimation() {
        loadingAnimator?.cancel()
        loadingAnimator = null
        loadingAngle = 0f
    }

    /**
     * 注解定义
     */
    @Retention(AnnotationRetention.SOURCE)
    annotation class State {
        companion object {
            const val IDLE = 0
            const val LOADING = 1
            const val SUCCESS = 2
            const val ERROR = 3
        }
    }
}
