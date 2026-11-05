package com.example.ventiladorambiental

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** Gráfico simple de las últimas lecturas: línea amarilla = temperatura, azul = humedad. */
class SparklineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var readings: List<Reading> = emptyList()
    private val density = resources.displayMetrics.density

    private val tempPaint = linePaint(R.color.sun_dark)
    private val humPaint = linePaint(R.color.primary)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.outline)
        strokeWidth = 1f * density
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_gray)
        textSize = 11f * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun linePaint(colorRes: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, colorRes)
        strokeWidth = 2.5f * density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun setReadings(list: List<Reading>) {
        readings = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bottomPad = 18f * density
        val h = height - bottomPad
        val w = width.toFloat()
        for (i in 0..3) {
            val y = h * i / 3f
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
        if (readings.size < 2) return

        // Temperatura en escala 15–35 °C, humedad en 20–100 %
        drawSeries(canvas, readings.map { (it.temperature - 15) / 20f }, w, h, tempPaint, true)
        drawSeries(canvas, readings.map { (it.humidity - 20) / 80f }, w, h, humPaint, false)

        canvas.drawText(readings.first().timeLabel, 0f, height - 4f * density, labelPaint)
        val last = readings.last().timeLabel
        canvas.drawText(last, w - labelPaint.measureText(last), height - 4f * density, labelPaint)
    }

    private fun drawSeries(canvas: Canvas, values: List<Float>, w: Float, h: Float, paint: Paint, fill: Boolean) {
        val step = w / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * step
            val y = h - v.coerceIn(0f, 1f) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (fill) {
            val area = Path(path).apply { lineTo(w, h); lineTo(0f, h); close() }
            fillPaint.shader = LinearGradient(0f, 0f, 0f, h, (paint.color and 0x00FFFFFF) or 0x33000000, 0, Shader.TileMode.CLAMP)
            canvas.drawPath(area, fillPaint)
        }
        canvas.drawPath(path, paint)
        val lastY = h - values.last().coerceIn(0f, 1f) * h
        canvas.drawCircle(w, lastY, 3.5f * density, paint.apply { style = Paint.Style.FILL })
        paint.style = Paint.Style.STROKE
    }
}
