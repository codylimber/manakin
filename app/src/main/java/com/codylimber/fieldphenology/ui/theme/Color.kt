package com.codylimber.fieldphenology.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Tonal palettes
//
// Material 3 style ramps (tone 0 = black, 100 = white). Building the scheme out
// of ramps rather than one-off hex values is what lets surfaces sit at slightly
// different tones and actually read as stacked layers instead of one flat slab.
// ---------------------------------------------------------------------------

// Leaf green — the brand hue. Deep and slightly blue-shifted so it reads as
// foliage rather than the stock Material "traffic light" green.
val Green10 = Color(0xFF00210F)
val Green20 = Color(0xFF00391C)
val Green30 = Color(0xFF00522B)
val Green40 = Color(0xFF006D3A)
val Green50 = Color(0xFF00894B)
val Green60 = Color(0xFF1FA65E)
val Green70 = Color(0xFF43C277)
val Green80 = Color(0xFF62DF92)
val Green90 = Color(0xFF7FFCAD)
val Green95 = Color(0xFFC6FFD3)

// Warm ochre — peak season, targets, anything that should feel like sunlight.
val Amber10 = Color(0xFF2A1700)
val Amber20 = Color(0xFF472A00)
val Amber30 = Color(0xFF663E00)
val Amber40 = Color(0xFF875300)
val Amber50 = Color(0xFFA96900)
val Amber60 = Color(0xFFCC8100)
val Amber70 = Color(0xFFEE9A00)
val Amber80 = Color(0xFFFFB951)
val Amber90 = Color(0xFFFFDDB4)
val Amber95 = Color(0xFFFFEEDD)

// Sky blue — life-list / "already seen" signals.
val Blue10 = Color(0xFF001E30)
val Blue20 = Color(0xFF003450)
val Blue30 = Color(0xFF004B72)
val Blue40 = Color(0xFF106495)
val Blue70 = Color(0xFF6FC5FF)
val Blue80 = Color(0xFFA3D3FF)
val Blue90 = Color(0xFFD1E8FF)

// Clay red — rarity and error states.
val Red10 = Color(0xFF410002)
val Red20 = Color(0xFF690005)
val Red30 = Color(0xFF93000A)
val Red40 = Color(0xFFBA1A1A)
val Red70 = Color(0xFFFF897D)
val Red80 = Color(0xFFFFB4AB)
val Red90 = Color(0xFFFFDAD6)

// Neutrals, warmed toward green so the dark theme reads as "forest at night"
// rather than the flat charcoal it used to be.
val Neutral04 = Color(0xFF0A0F0B)
val Neutral06 = Color(0xFF101510)
val Neutral10 = Color(0xFF171D18)
val Neutral12 = Color(0xFF1B211C)
val Neutral17 = Color(0xFF252B26)
val Neutral22 = Color(0xFF303630)
val Neutral24 = Color(0xFF343A35)
val Neutral80 = Color(0xFFC3C9C1)
val Neutral90 = Color(0xFFE0E4DC)
val Neutral95 = Color(0xFFEEF2E9)
val Neutral98 = Color(0xFFF7FBF2)
val Neutral100 = Color(0xFFFFFFFF)

// Light-theme container ramp. Light schemes stack downward from white, so these
// darken slightly as elevation rises — the mirror of the dark ramp above.
val NeutralLight96 = Color(0xFFF1F5EC)
val NeutralLight94 = Color(0xFFEBEFE6)
val NeutralLight92 = Color(0xFFE6EAE1)
val NeutralLight90 = Color(0xFFE0E4DB)
val NeutralLight87 = Color(0xFFD8DCD3)

val NeutralVariant30 = Color(0xFF414942)
val NeutralVariant50 = Color(0xFF717972)
val NeutralVariant60 = Color(0xFF8B938B)
val NeutralVariant70 = Color(0xFFA5ADA4)
val NeutralVariant80 = Color(0xFFC1C9BF)
val NeutralVariant90 = Color(0xFFDDE5DA)

// ---------------------------------------------------------------------------
// Semantic aliases
//
// These names are used directly across the screens (as tints, chart colors and
// so on). They stay stable; only the values behind them move.
// ---------------------------------------------------------------------------

// `Primary` lives in Theme.kt: it has to follow the active scheme, and a plain
// val can't. The bright dark-theme green is unreadable on a white background.
// Everything else the schemes need comes straight from the ramps above.

/** Light-theme neutral with a hint of the brand green in it. */
val LightSurfaceVariant = Color(0xFFE8EEE4)

/** Targets / favourites. */
val FavoriteGold = Amber80
/** Species already on the user's life list. */
val ObservedBlue = Blue70

// ---------------------------------------------------------------------------
// Data colors
//
// Each of these is a pair: a saturated `…Color` for dots, chart bars and map
// markers, plus container/on-container tones for chips. Chips built from a
// tonal pair stay legible in both themes without white-on-pastel washouts.
// ---------------------------------------------------------------------------

data class ToneRole(
    val solid: Color,
    val container: Color,
    val onContainer: Color,
    val solidLight: Color = solid,
    val containerLight: Color = container,
    val onContainerLight: Color = onContainer,
) {
    fun solid(dark: Boolean) = if (dark) solid else solidLight
    fun container(dark: Boolean) = if (dark) container else containerLight
    fun onContainer(dark: Boolean) = if (dark) onContainer else onContainerLight
}

/** Phenology status roles, keyed to how "on" a species is right now. */
val StatusPeakRole = ToneRole(
    solid = Amber80, container = Amber30, onContainer = Amber90,
    solidLight = Amber50, containerLight = Amber90, onContainerLight = Amber20,
)
val StatusActiveRole = ToneRole(
    solid = Green70, container = Green30, onContainer = Green90,
    solidLight = Green40, containerLight = Green95, onContainerLight = Green10,
)
val StatusEdgeRole = ToneRole(
    solid = Blue70, container = Blue30, onContainer = Blue90,
    solidLight = Blue40, containerLight = Blue90, onContainerLight = Blue10,
)
val StatusInactiveRole = ToneRole(
    solid = NeutralVariant60, container = Neutral22, onContainer = NeutralVariant80,
    solidLight = NeutralVariant50, containerLight = LightSurfaceVariant, onContainerLight = NeutralVariant30,
)

/** Rarity roles, shared by the rarity dot and the rarity chip. */
val RarityCommonRole = StatusActiveRole
val RarityUncommonRole = StatusPeakRole
val RarityRareRole = ToneRole(
    solid = Red70, container = Red30, onContainer = Red90,
    solidLight = Red40, containerLight = Red90, onContainerLight = Red10,
)

/** IUCN conservation status, least concern → critically endangered. */
val ConservationLC = Green70
val ConservationNT = Amber80
val ConservationVU = Color(0xFFFF9E6B)
val ConservationEN = Red70
val ConservationCR = Color(0xFFFF6B6B)

// ---------------------------------------------------------------------------
// Map colors
// ---------------------------------------------------------------------------

/** iNaturalist's own observation red, reused so the heatmap and pins agree. */
val MapObservation = Color(0xFFE8000D)
/** Clusters shade from green (few) to amber to red (many). */
val MapClusterLow = Green60
val MapClusterMid = Amber70
val MapClusterHigh = Color(0xFFE8542A)
