package com.dps.mytestc

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val waveformPath = Path()
    private val points = mutableListOf<Float>()
    private val maxPoints = 100
    private var amplitude = 0f

    fun addAmplitude(amp: Float) {
        val scaledAmp = (amp / 50).coerceIn(0f, 1f)
        amplitude = scaledAmp
        points.add(0, scaledAmp)
        if (points.size > maxPoints) {
            points.removeAt(points.size - 1)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (points.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2

        waveformPath.reset()
        val pointWidth = width / maxPoints

        points.forEachIndexed { index, point ->
            val x = width - (index * pointWidth)
            val y = centerY - (point * height / 2)

            if (index == 0) {
                waveformPath.moveTo(x, y)
            } else {
                waveformPath.lineTo(x, y)
            }
        }

        // Draw mirrored bottom half
        for (i in points.size - 1 downTo 0) {
            val x = width - (i * pointWidth)
            val y = centerY + (points[i] * height / 2)
            waveformPath.lineTo(x, y)
        }

        waveformPath.close()

        // Create gradient
        val gradient = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(
                Color.parseColor("#4CAF50"),
                Color.parseColor("#2196F3")
            ),
            null,
            Shader.TileMode.CLAMP
        )

        paint.shader = gradient
        paint.style = Paint.Style.FILL
        canvas.drawPath(waveformPath, paint)

        // Draw outline
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.alpha = 50
        canvas.drawPath(waveformPath, paint)
    }
} 