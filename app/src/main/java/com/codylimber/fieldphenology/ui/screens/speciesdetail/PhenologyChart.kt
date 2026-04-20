package com.codylimber.fieldphenology.ui.screens.speciesdetail

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import com.codylimber.fieldphenology.data.model.WeeklyEntry
import com.codylimber.fieldphenology.ui.theme.Primary

private val MONTHS = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
// Approximate week number where each month starts
private val MONTH_WEEKS = listOf(1, 5, 9, 14, 18, 22, 27, 31, 35, 40, 44, 48)

@Composable
fun PhenologyChart(
    weekly: List<WeeklyEntry>,
    currentWeek: Int,
    peakWeek: Int,
    modifier: Modifier = Modifier
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val labelHeight = 28f
        val chartHeight = h - labelHeight
        val barWidth = w / 53f
        val gap = barWidth * 0.1f

        // Grid lines at 25%, 50%, 75%
        for (frac in listOf(0.25f, 0.5f, 0.75f)) {
            val y = chartHeight * (1f - frac)
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
        }

        // Bars
        for (entry in weekly) {
            if (entry.week < 1 || entry.week > 53) continue
            val x = (entry.week - 1) * barWidth
            val barH = entry.relAbundance * chartHeight
            if (barH > 0.5f) {
                val isPeak = entry.week == peakWeek
                val alpha = if (isPeak) 1f else 0.3f + 0.7f * entry.relAbundance
                drawRect(
                    color = Primary.copy(alpha = alpha),
                    topLeft = Offset(x + gap / 2, chartHeight - barH),
                    size = Size(barWidth - gap, barH)
                )
            }
        }

        // Current week dashed line
        if (currentWeek in 1..53) {
            val cx = (currentWeek - 0.5f) * barWidth
            drawLine(
                color = Color.White.copy(alpha = 0.8f),
                start = Offset(cx, 0f),
                end = Offset(cx, chartHeight),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
            )
        }

        // Month labels
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(
                (labelColor.alpha * 255).toInt(),
                (labelColor.red * 255).toInt(),
                (labelColor.green * 255).toInt(),
                (labelColor.blue * 255).toInt()
            )
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        for (i in MONTHS.indices) {
            val x = (MONTH_WEEKS[i] - 0.5f) * barWidth
            drawContext.canvas.nativeCanvas.drawText(
                MONTHS[i],
                x,
                h - 2f,
                paint
            )
        }
    }
}
