package com.gilespii.radioex.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.gilespii.radioex.util.AccessibilityManager
import kotlin.random.Random

/**
 * Premium symmetric audio visualizer view:
 * - Centered sound wave bars expanding vertically from the centerline (capsule shaped)
 * - Subtle ambient glow around bars
 * - Full support for Reduced Motion (freezes gracefully at baseline, 0% CPU/GPU)
 * - Pre-generated pools and cached dimensions for high 10-foot TV performance
 */
class SimpleVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = resolveThemeAccent()
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = resolveThemeAccent()
        style = Paint.Style.FILL
        alpha = 45
    }

    private fun resolveThemeAccent(): Int {
        val typedValue = android.util.TypedValue()
        return if (context.theme.resolveAttribute(com.gilespii.radioex.R.attr.themeAccent, typedValue, true)) {
            typedValue.data
        } else {
            0xFF32B8C6.toInt()
        }
    }

    private var barCount = 20
    private var barHeights = FloatArray(32) { 10f }
    private var barTargets = FloatArray(32) { 50f }
    private var barSpeeds = FloatArray(32) { 0.45f }

    private val randomPoolSize = 100
    private var randomTargetPool = FloatArray(randomPoolSize) { Random.nextFloat() * 85 + 15 }
    private var randomSpeedPool = FloatArray(randomPoolSize) { Random.nextFloat() * 0.5f + 0.35f }
    private var randomPoolIndex = 0

    private var isPlaying = false
    private var reducedMotion = false
    private var isSymmetrical = true
    private val barRect = RectF()
    private val glowRect = RectF()

    private var cachedWidth = 0f
    private var cachedHeight = 0f
    private var cachedBarWidth = 0f
    private var cachedGap = 5f
    private var cachedTotalGap = 0f
    private var dimensionsDirty = true

    private val motionListener: (Boolean) -> Unit = { reduced ->
        post {
            reducedMotion = reduced
            invalidate()
        }
    }

    init {
        reducedMotion = AccessibilityManager.isReducedMotion(context)
        initBars()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        reducedMotion = AccessibilityManager.isReducedMotion(context)
        AccessibilityManager.addReducedMotionListener(motionListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        AccessibilityManager.removeReducedMotionListener(motionListener)
    }

    private fun initBars() {
        barHeights = FloatArray(barCount) { 10f }
        barTargets = FloatArray(barCount) { 50f }
        barSpeeds = FloatArray(barCount) { 0.45f }
        randomPoolIndex = 0
        for (i in 0 until barCount) {
            barTargets[i] = getNextRandomTarget()
            barSpeeds[i] = getNextRandomSpeed()
        }
    }

    private fun getNextRandomTarget(): Float {
        val value = randomTargetPool[randomPoolIndex % randomPoolSize]
        randomPoolIndex++
        return value
    }

    private fun getNextRandomSpeed(): Float {
        val value = randomSpeedPool[randomPoolIndex % randomPoolSize]
        randomPoolIndex++
        return value
    }

    fun setBarCount(count: Int) {
        if (barCount != count) {
            barCount = count
            initBars()
            dimensionsDirty = true
            invalidate()
        }
    }

    fun setPlaying(playing: Boolean) {
        this.isPlaying = playing
        invalidate()
    }

    fun setColor(color: Int) {
        paint.color = color
        glowPaint.color = color
        glowPaint.alpha = 45
        invalidate()
    }

    fun setSymmetrical(symmetrical: Boolean) {
        this.isSymmetrical = symmetrical
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cachedWidth = w.toFloat()
        cachedHeight = h.toFloat()
        cachedTotalGap = cachedGap * (barCount - 1)
        cachedBarWidth = (cachedWidth - cachedTotalGap) / barCount
        dimensionsDirty = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dimensionsDirty) {
            cachedWidth = width.toFloat()
            cachedHeight = height.toFloat()
            cachedTotalGap = cachedGap * (barCount - 1)
            cachedBarWidth = (cachedWidth - cachedTotalGap) / barCount
            dimensionsDirty = false
        }

        val cornerRadius = cachedBarWidth / 2f

        if (reducedMotion) {
            // Calm, fixed baseline (capsule pills) without animation
            val minHeightPx = (cachedHeight * 0.16f).coerceAtLeast(cornerRadius * 2f)
            for (i in 0 until barCount) {
                val left = i * (cachedBarWidth + cachedGap)
                val right = left + cachedBarWidth
                val top = if (isSymmetrical) {
                    (cachedHeight - minHeightPx) / 2f
                } else {
                    cachedHeight - minHeightPx
                }
                val bottom = top + minHeightPx
                barRect.set(left, top, right, bottom)
                canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, paint)
            }
            return
        }

        var needsInvalidate = false

        for (i in 0 until barCount) {
            var height = barHeights[i]
            var target = barTargets[i]
            var speed = barSpeeds[i]

            if (!isPlaying) {
                // Smooth decay to minimal baseline
                if (height > 10.1f) {
                    height += (10f - height) * 0.15f
                    needsInvalidate = true
                } else {
                    height = 10f
                }
            } else {
                // Smooth animation towards target
                val diff = target - height
                if (kotlin.math.abs(diff) < 2) {
                    target = getNextRandomTarget()
                    speed = getNextRandomSpeed()
                    barTargets[i] = target
                    barSpeeds[i] = speed
                }
                height += diff * speed
                needsInvalidate = true
            }

            barHeights[i] = height

            val left = i * (cachedBarWidth + cachedGap)
            val right = left + cachedBarWidth
            val heightPx = ((height / 100f) * cachedHeight).coerceAtLeast(cornerRadius * 2f)

            val top: Float
            val bottom: Float
            if (isSymmetrical) {
                top = (cachedHeight - heightPx) / 2f
                bottom = top + heightPx
            } else {
                top = cachedHeight - heightPx
                bottom = cachedHeight
            }

            // Glow bloom pass
            if (isPlaying && height > 20f) {
                glowRect.set(left - 1.5f, top - 1.5f, right + 1.5f, bottom + 1.5f)
                canvas.drawRoundRect(glowRect, cornerRadius + 1.5f, cornerRadius + 1.5f, glowPaint)
            }

            // Main bar
            barRect.set(left, top, right, bottom)
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, paint)
        }

        if (needsInvalidate || isPlaying) {
            postInvalidateDelayed(50) // ~20 FPS limit to prevent audio stuttering on Android 14
        }
    }
}