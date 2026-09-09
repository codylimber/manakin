package com.codylimber.fieldphenology.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii. Rounder than Material's defaults across the board — the app is
 * full of photo thumbnails and chart cards, and softer corners are what stop a
 * dense list from reading as a spreadsheet.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Fully rounded — badges, chips, segmented toggles. */
val PillShape = RoundedCornerShape(percent = 50)

/** Standard card radius, for the places that build their own surfaces. */
val CardShape = RoundedCornerShape(16.dp)

/** Thumbnails and inline images inside cards. */
val ThumbShape = RoundedCornerShape(12.dp)
