package com.codylimber.fieldphenology.ui.screens.specieslist

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.codylimber.fieldphenology.data.model.WeeklyEntry
import com.codylimber.fieldphenology.ui.theme.Primary

@Composable
fun MiniBarChart(
    weekly: List<WeeklyEntry>,
    currentWeek: Int,
    modifier: Modifier = Modifier,
    barColor: Color = Primary
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barWidth = w / 53f
        val gap = barWidth * 0.15f

        // Draw bars
        for (entry in weekly) {
            if (entry.week < 1 || entry.week > 53) continue
            val x = (entry.week - 1) * barWidth
            val barH = entry.relAbundance * h
            if (barH > 0.5f) {
                val alpha = 0.3f + 0.7f * entry.relAbundance
                drawRect(
                    color = barColor.copy(alpha = alpha),
                    topLeft = Offset(x + gap / 2, h - barH),
                    size = Size(barWidth - gap, barH)
                )
            }
        }

        // Current week indicator
        if (currentWeek in 1..53) {
            val cx = (currentWeek - 0.5f) * barWidth
            drawLine(
                color = Color.White.copy(alpha = 0.7f),
                start = Offset(cx, 0f),
                end = Offset(cx, h),
                strokeWidth = 1.5f
            )
        }
    }
}
