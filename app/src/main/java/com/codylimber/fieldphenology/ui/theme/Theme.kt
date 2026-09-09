package com.codylimber.fieldphenology.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private val DarkColorScheme = darkColorScheme(
    primary = Green70,
    onPrimary = Green10,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Amber80,
    onSecondary = Amber10,
    secondaryContainer = Amber30,
    onSecondaryContainer = Amber90,
    tertiary = Blue70,
    onTertiary = Blue10,
    tertiaryContainer = Blue30,
    onTertiaryContainer = Blue90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Neutral06,
    onBackground = Neutral90,
    // Deliberately a step above `background`: the app colors its cards, nav bar
    // and app bars with `surface`, and matching the two made every card
    // disappear into the page.
    surface = Neutral12,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    // The container ramp is what gives cards, sheets and bars a sense of
    // stacking — each step is a couple of tones lighter than the one below.
    surfaceContainerLowest = Neutral04,
    surfaceContainerLow = Neutral10,
    surfaceContainer = Neutral12,
    surfaceContainerHigh = Neutral17,
    surfaceContainerHighest = Neutral22,
    surfaceBright = Neutral24,
    surfaceDim = Neutral04,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral17,
    inversePrimary = Green40,
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    scrim = Neutral04,
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Neutral100,
    primaryContainer = Green95,
    onPrimaryContainer = Green10,
    secondary = Amber40,
    onSecondary = Neutral100,
    secondaryContainer = Amber90,
    onSecondaryContainer = Amber10,
    tertiary = Blue40,
    onTertiary = Neutral100,
    tertiaryContainer = Blue90,
    onTertiaryContainer = Blue10,
    error = Red40,
    onError = Neutral100,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = Neutral98,
    onBackground = Neutral10,
    surface = Neutral100,
    onSurface = Neutral10,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = NeutralVariant30,
    surfaceContainerLowest = Neutral100,
    surfaceContainerLow = NeutralLight96,
    surfaceContainer = NeutralLight94,
    surfaceContainerHigh = NeutralLight92,
    surfaceContainerHighest = NeutralLight90,
    surfaceBright = Neutral98,
    surfaceDim = NeutralLight87,
    inverseSurface = Neutral17,
    inverseOnSurface = Neutral95,
    inversePrimary = Green80,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    scrim = Neutral04,
)

/**
 * The brand green for the active theme.
 *
 * Screens across the app tint icons, links and accents with this. It has to be
 * a composable accessor rather than a constant: the dark scheme needs a bright
 * green to read against near-black, and that same green on a white background
 * washes out to something close to illegible. Reading it from the scheme means
 * every existing `Primary` call site gets the right one for free.
 */
val Primary: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.primary

object ThemeState {
    var isDarkMode by mutableStateOf(true)
}

/**
 * Whether the app is currently rendering its dark scheme.
 *
 * Composables that mix Material colors with the hand-rolled data palette (chips,
 * charts, map layers) need to pick the matching tone from a [ToneRole], and
 * reading it from a CompositionLocal keeps that from having to be threaded
 * through every call site.
 */
val LocalIsDarkTheme = compositionLocalOf { true }

@Composable
fun FieldPhenologyTheme(
    darkTheme: Boolean = ThemeState.isDarkMode,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
