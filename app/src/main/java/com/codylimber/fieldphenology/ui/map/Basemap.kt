package com.codylimber.fieldphenology.ui.map

/**
 * Basemap choices.
 *
 * All styles come from OpenFreeMap, which serves OpenStreetMap-derived vector
 * tiles with no API key and no usage limits — the reason the map moved off
 * CARTO, whose keyless raster tiles are now stamped "API KEY REQUIRED".
 *
 * Vector tiles also mean the basemap re-renders at every zoom level instead of
 * being upscaled, so labels and coastlines stay sharp during a pinch.
 *
 * These stay light even when the app is in dark mode. The observation layer is
 * red, and red-on-near-black loses almost all of its tonal range — the density
 * gradient that the heatmap exists to show simply stops being visible. A pale
 * basemap keeps that contrast, so the map ignores the app theme by design.
 */
enum class Basemap(
    val label: String,
    val styleUri: String,
) {
    /**
     * Muted grey-green with sparse labels. The default: the observation heatmap
     * is the subject, and a quiet basemap is what lets the red read.
     */
    MINIMAL(
        label = "Minimal",
        styleUri = "https://tiles.openfreemap.org/styles/positron",
    ),

    /**
     * Full OSM detail — roads, trails, parks, place names. Useful once you are
     * zoomed into a patch and working out how to actually get there.
     */
    DETAILED(
        label = "Detailed",
        styleUri = "https://tiles.openfreemap.org/styles/liberty",
    );
}

/** iNaturalist density tiles, colored to match the observation markers. */
fun heatmapTileUrl(taxonId: Int, colorHex: String): String =
    "https://api.inaturalist.org/v1/colored_heatmap/{z}/{x}/{y}.png" +
        "?taxon_id=$taxonId&color=%23${colorHex.removePrefix("#")}"

/**
 * Zoom at which individual observations replace the density heatmap.
 *
 * Below this the viewport covers more ground than a single API page can
 * honestly represent, so a heatmap is the truthful picture; above it, fetching
 * the actual observations is both feasible and more useful.
 */
const val PIN_ZOOM = 9.5

/** Heatmap is fully faded out this far above [PIN_ZOOM]. */
const val HEAT_FADE_SPAN = 1.0
