package com.dps.mytestc

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.min

class FingerprintVisualView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fingerprintPath = Path()
    private var rippleRadius = 0f
    private var rippleAlpha = 0
    private var isAnimating = false
    private var centerX = 0f
    private var centerY = 0f
    private var scale = 1f
    private val maxScale = 1.5f

    private val rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500
        repeatCount = ValueAnimator.INFINITE
        interpolator = DecelerateInterpolator()
        addUpdateListener { animator ->
            val progress = animator.animatedValue as Float
            rippleRadius = width * 0.4f * progress
            rippleAlpha = ((1f - progress) * 255).toInt()
            invalidate()
        }
    }

    private val scaleAnimator = ValueAnimator.ofFloat(1f, maxScale).apply {
        duration = 300
        interpolator = DecelerateInterpolator()
        addUpdateListener { animator ->
            scale = animator.animatedValue as Float
            invalidate()
        }
    }

    init {
        paint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.WHITE
        }

        ripplePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = ContextCompat.getColor(context, R.color.cardBackground)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        setupFingerprintPath()
    }

    private fun setupFingerprintPath() {
        val size = min(width, height) * 0.4f
        fingerprintPath.reset()

        // Outer oval
        fingerprintPath.addOval(
            centerX - size,
            centerY - size * 1.3f,
            centerX + size,
            centerY + size * 1.3f,
            Path.Direction.CW
        )

        // Inner patterns (simplified fingerprint-like curves)
        for (i in 1..5) {
            val factor = 1f - (i * 0.15f)
            fingerprintPath.addOval(
                centerX - size * factor,
                centerY - size * factor * 1.3f,
                centerX + size * factor,
                centerY + size * factor * 1.3f,
                Path.Direction.CW
            )
        }

        // Add some arcs for more detail
        for (i in 0..3) {
            val startAngle = i * 90f
            fingerprintPath.addArc(
                centerX - size * 0.7f,
                centerY - size * 0.7f,
                centerX + size * 0.7f,
                centerY + size * 0.7f,
                startAngle,
                60f
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        canvas.scale(scale, scale, centerX, centerY)

        // Draw ripple effect
        if (isAnimating) {
            ripplePaint.alpha = rippleAlpha
            canvas.drawCircle(centerX, centerY, rippleRadius, ripplePaint)
        }

        // Draw fingerprint pattern
        canvas.drawPath(fingerprintPath, paint)
        canvas.restore()
    }

    fun startAnimation() {
        if (!isAnimating) {
            isAnimating = true
            rippleAnimator.start()
            scaleAnimator.start()
        }
    }

    fun stopAnimation() {
        if (isAnimating) {
            isAnimating = false
            rippleAnimator.cancel()
            scaleAnimator.reverse()
        }
    }
} 