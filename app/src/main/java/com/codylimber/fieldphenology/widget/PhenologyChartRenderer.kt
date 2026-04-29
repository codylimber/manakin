package com.codylimber.fieldphenology.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.codylimber.fieldphenology.data.model.WeeklyEntry

object PhenologyChartRenderer {

    /**
     * Renders a mini 52-week phenology bar chart as a Bitmap.
     * Bars are colored with a gradient from green (low) to bright green (high),
     * with the current week highlighted.
     */
    fun render(
        weekly: List<WeeklyEntry>,
        currentWeek: Int,
        width: Int = 200,
        height: Int = 40,
        barColor: Int = 0xFF4CAF50.toInt(),
        highlightColor: Int = 0xFFFFEB3B.toInt(),
        backgroundColor: Int = 0x00000000
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (backgroundColor != 0x00000000) {
            canvas.drawColor(backgroundColor)
        }

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highlightColor
        }

        val totalBars = 52
        val gap = 1f
        val barWidth = (width.toFloat() - (totalBars - 1) * gap) / totalBars

        // Build a lookup map for quick access
        val weekMap = weekly.associateBy { it.week }

        for (i in 1..totalBars) {
            val entry = weekMap[i]
            val abundance = entry?.relAbundance ?: 0f

            if (abundance <= 0f) continue

            val x = (i - 1) * (barWidth + gap)
            val barHeight = abundance * height * 0.9f
            val y = height - barHeight

            val isCurrentWeek = i == currentWeek
            barPaint.color = if (isCurrentWeek) highlightColor else barColor
            barPaint.alpha = if (isCurrentWeek) 255 else (150 + (abundance * 105).toInt())

            canvas.drawRoundRect(
                RectF(x, y, x + barWidth, height.toFloat()),
                barWidth / 3f, barWidth / 3f,
                if (isCurrentWeek) highlightPaint else barPaint
            )
        }

        // Draw a small marker line at current week if there's no bar
        if ((weekMap[currentWeek]?.relAbundance ?: 0f) <= 0f) {
            val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = highlightColor
                alpha = 180
                strokeWidth = 2f
            }
            val x = (currentWeek - 1) * (barWidth + gap) + barWidth / 2f
            canvas.drawLine(x, 0f, x, height.toFloat(), markerPaint)
        }

        return bitmap
    }

    /**
     * Renders a small horizontal activity bar showing the current relative abundance.
     */
    fun renderActivityBar(
        abundance: Float,
        width: Int = 100,
        height: Int = 8,
        barColor: Int = 0xFF4CAF50.toInt(),
        trackColor: Int = 0x33FFFFFF
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val radius = height / 2f

        // Track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, trackPaint)

        // Fill
        if (abundance > 0f) {
            val fillWidth = abundance * width
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = barColor
                alpha = (180 + (abundance * 75).toInt()).coerceAtMost(255)
            }
            canvas.drawRoundRect(RectF(0f, 0f, fillWidth, height.toFloat()), radius, radius, fillPaint)
        }

        return bitmap
    }
}
