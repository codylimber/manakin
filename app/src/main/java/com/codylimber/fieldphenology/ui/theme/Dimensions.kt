package com.codylimber.fieldphenology.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Fallback height when not inside MainScreen's Scaffold */
val BottomNavBarPadding = 88.dp

/** Dynamic bottom padding provided by MainScreen's Scaffold, accounts for generation bar */
val LocalBottomPadding = compositionLocalOf<Dp> { 88.dp }
