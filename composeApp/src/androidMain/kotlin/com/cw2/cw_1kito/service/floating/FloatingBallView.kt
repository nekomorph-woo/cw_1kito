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

    // 颜色常量（Material 3 色板）
    private val BALL_COLOR_IDLE = Color.parseColor("#9CA3AF")        // 中性灰，更柔和
    private val BALL_COLOR_LOADING = Color.parseColor("#6366F1")      // Indigo，更有活力
    private val BALL_COLOR_SUCCESS = Color.parseColor("#10B981")     // Emerald，更现代
    private val BALL_COLOR_ERROR = Color.parseColor("#EF4444")       // Red，保持一致

    private val ballSize: Int
    private val density: Float

    // 主球体画笔（带双层阴影效果）
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BALL_COLOR_IDLE
        style = Paint.Style.FILL
        // 外阴影：更柔和、更扩散
        setShadowLayer(16f, 0f, 6f, Color.parseColor("#1A000000"))
    }

    // 内发光画笔（双层阴影的第二层）
    private val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BALL_COLOR_IDLE
        style = Paint.Style.FILL
        alpha = 180  // 70% 不透明度
    }

    private val loadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BALL_COLOR_LOADING
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }

    // 脉冲外圈画笔
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = BALL_COLOR_LOADING
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 80  // 30% 不透明度
    }

    private var loadingAngle = 0f
    private var loadingAnimator: ValueAnimator? = null
    private var pulseRadius = 0f  // 脉冲动画半径
    private var pulseAlpha = 80    // 脉冲动画透明度

    // 呼吸动画相关
    private var breathAnimator: ValueAnimator? = null
    private var breathScale = 1f
    private var breathAlpha = 255f

    // 按压反馈动画相关
    private var pressAnimator: ValueAnimator? = null
    private var currentScale = 1f

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
        val baseRadius = ballSize / 2f

        // 应用按压反馈和呼吸动画的综合缩放
        val totalScale = currentScale * breathScale
        val radius = baseRadius * totalScale

        // 应用呼吸动画的透明度（仅在空闲状态）
        if (currentState == FloatingService.STATE_IDLE) {
            ballPaint.alpha = breathAlpha.toInt()
            innerGlowPaint.alpha = (breathAlpha * 0.7f).toInt()
        } else {
            ballPaint.alpha = 255
            innerGlowPaint.alpha = 180
        }

        // 绘制脉冲外圈（加载状态）
        if (currentState == FloatingService.STATE_LOADING && pulseRadius > 0f) {
            pulsePaint.alpha = (pulseAlpha * (255 / 100f)).toInt()
            canvas.drawCircle(centerX, centerY, radius + pulseRadius, pulsePaint)
        }

        // 绘制加载旋转圆环
        if (currentState == FloatingService.STATE_LOADING) {
            val loadingRadius = radius + 8f
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

        // 绘制内发光层（双层阴影效果）
        val innerGlowRadius = radius * 0.92f
        canvas.drawCircle(centerX, centerY, innerGlowRadius, innerGlowPaint)

        // 绘制主悬浮球
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
                // 按压反馈动画
                startPressAnimation()
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
                    // 拖拽时恢复原始大小
                    startReleaseAnimation()
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
                    // 释放反馈动画
                    startReleaseAnimation()
                } else if (isDragging) {
                    // 拖拽结束，应用磁吸效果
                    applySnapToEdge()
                } else {
                    // 长按触发后释放
                    startReleaseAnimation()
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
     * 设置加载状态（带颜色过渡动画）
     */
    fun setLoadingState(@State state: Int) {
        val oldState = currentState
        currentState = state

        val targetColor = when (state) {
            FloatingService.STATE_IDLE -> BALL_COLOR_IDLE
            FloatingService.STATE_LOADING -> BALL_COLOR_LOADING
            FloatingService.STATE_SUCCESS -> BALL_COLOR_SUCCESS
            FloatingService.STATE_ERROR -> BALL_COLOR_ERROR
            else -> BALL_COLOR_IDLE
        }

        // 颜色过渡动画
        if (oldState != state && oldState != FloatingService.STATE_IDLE) {
            // 从非空闲状态切换时，使用颜色过渡动画
            val startColor = ballPaint.color
            animateColorChange(startColor, targetColor, 300)
        } else {
            // 直接设置颜色
            ballPaint.color = targetColor
            innerGlowPaint.color = targetColor
        }

        when (state) {
            FloatingService.STATE_IDLE -> {
                stopLoadingAnimation()
                stopBreathAnimation()
                startBreathAnimation()
            }
            FloatingService.STATE_LOADING -> {
                stopBreathAnimation()
                startLoadingAnimation()
            }
            FloatingService.STATE_SUCCESS -> {
                stopBreathAnimation()
                stopLoadingAnimation()
                // 2秒后恢复空闲状态
                postDelayed({
                    if (currentState == FloatingService.STATE_SUCCESS) {
                        setLoadingState(FloatingService.STATE_IDLE)
                    }
                }, 2000)
            }
            FloatingService.STATE_ERROR -> {
                stopBreathAnimation()
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
     * 颜色过渡动画
     */
    private fun animateColorChange(startColor: Int, endColor: Int, duration: Long) {
        ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val newColor = interpolateColor(startColor, endColor, fraction)
                ballPaint.color = newColor
                innerGlowPaint.color = newColor
                invalidate()
            }
            start()
        }
    }

    /**
     * 颜色插值计算
     */
    private fun interpolateColor(startColor: Int, endColor: Int, fraction: Float): Int {
        val startA = (startColor shr 24) and 0xFF
        val startR = (startColor shr 16) and 0xFF
        val startG = (startColor shr 8) and 0xFF
        val startB = startColor and 0xFF

        val endA = (endColor shr 24) and 0xFF
        val endR = (endColor shr 16) and 0xFF
        val endG = (endColor shr 8) and 0xFF
        val endB = endColor and 0xFF

        val a = (startA + (endA - startA) * fraction).toInt()
        val r = (startR + (endR - startR) * fraction).toInt()
        val g = (startG + (endG - startG) * fraction).toInt()
        val b = (startB + (endB - startB) * fraction).toInt()

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * 开始加载动画（旋转圆环 + 脉冲效果）
     */
    private fun startLoadingAnimation() {
        if (loadingAnimator?.isRunning == true) return

        val animators = listOf(
            // 旋转圆环动画
            ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 1200
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
            },
            // 脉冲扩散动画
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1500
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
        )

        loadingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener { animator ->
                // 旋转角度
                loadingAngle = (animator.animatedValue as Float * 360) % 360

                // 脉冲效果: radius 0 → 12, alpha 80 → 0
                val pulseValue = ((animator.currentPlayTime % 1500) / 1500f)
                pulseRadius = pulseValue * 12f
                pulseAlpha = (80 * (1 - pulseValue)).toInt()

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
        pulseRadius = 0f
        pulseAlpha = 80
    }

    /**
     * 开始呼吸动画（空闲状态）
     */
    private fun startBreathAnimation() {
        if (breathAnimator?.isRunning == true) return

        breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3000  // 3秒周期
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Float
                // 缩放: 0.98 ↔ 1.0
                breathScale = 0.98f + (value * 0.02f)
                // 透明度: 217 ↔ 255 (85% ↔ 100%)
                breathAlpha = 217f + (value * 38f)
                invalidate()
            }
            start()
        }
    }

    /**
     * 停止呼吸动画
     */
    private fun stopBreathAnimation() {
        breathAnimator?.cancel()
        breathAnimator = null
        breathScale = 1f
        breathAlpha = 255f
    }

    /**
     * 开始按压反馈动画
     */
    private fun startPressAnimation() {
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(1f, 0.9f).apply {
            duration = 100
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { animator ->
                currentScale = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * 开始释放反馈动画（带弹性效果）
     */
    private fun startReleaseAnimation() {
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(currentScale, 1f).apply {
            duration = 200
            interpolator = android.view.animation.OvershootInterpolator(1.5f)
            addUpdateListener { animator ->
                currentScale = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
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
