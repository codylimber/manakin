package com.codylimber.fieldphenology.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.codylimber.fieldphenology.ui.theme.MapClusterHigh
import com.codylimber.fieldphenology.ui.theme.MapClusterLow
import com.codylimber.fieldphenology.ui.theme.MapClusterMid
import com.codylimber.fieldphenology.ui.theme.MapObservation
import com.google.gson.JsonObject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val SRC_HEAT = "manakin-heat"
private const val LYR_HEAT = "manakin-heat"
private const val SRC_OBS = "manakin-obs"
private const val LYR_CLUSTER = "manakin-obs-cluster"
private const val LYR_CLUSTER_COUNT = "manakin-obs-cluster-count"
private const val LYR_POINT = "manakin-obs-point"
private const val LYR_POINT_OBSCURED = "manakin-obs-obscured"

/** Fallback camera: the continental US, roughly. */
private val DEFAULT_CENTER = LatLng(39.5, -98.35)
private const val DEFAULT_ZOOM = 3.4

/** What the map is currently showing, for the chrome drawn around it. */
data class MapUiState(
    val loading: Boolean = false,
    /** True once the camera is close enough that individual records are drawn. */
    val pointsMode: Boolean = false,
    val loadedCount: Int = 0,
    val totalInView: Int = 0,
) {
    /** More observations exist here than a single page could fetch. */
    val truncated: Boolean get() = totalInView > loadedCount
}

/**
 * Camera handle for chrome outside the map — zoom buttons, "fit to region".
 *
 * The map owns its own camera; this just gives the surrounding screen a way to
 * nudge it without hoisting the whole MapLibre instance into composition state.
 */
class MapCameraController {
    internal var map: MapLibreMap? = null
    internal var home: Viewport? = null

    fun zoomIn() {
        map?.animateCamera(CameraUpdateFactory.zoomIn(), 250)
    }

    fun zoomOut() {
        map?.animateCamera(CameraUpdateFactory.zoomOut(), 250)
    }

    /** Return to the dataset's region, or the default view if there isn't one. */
    fun resetCamera() {
        val m = map ?: return
        val bounds = home
        if (bounds != null) {
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.toLatLngBounds(), 64), 500)
        } else {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM), 500)
        }
    }
}

@Composable
fun rememberMapCameraController(): MapCameraController = remember { MapCameraController() }

private fun Viewport.toLatLngBounds(): LatLngBounds =
    LatLngBounds.from(north, east, south, west)

private fun LatLngBounds.toViewport(): Viewport =
    Viewport(
        north = latitudeNorth,
        east = longitudeEast,
        south = latitudeSouth,
        west = longitudeWest,
    )

/**
 * The observation map for a single taxon.
 *
 * Two representations, chosen by zoom: iNaturalist's density heatmap while the
 * viewport is wide, and individual (clustered) observations once it is narrow
 * enough that fetching them is meaningful. The heatmap fades out across a zoom
 * level rather than snapping off, so the handover is invisible.
 *
 * @param interactive when false the map is a still preview — gestures are off
 *   and it stays on the heatmap, so it can live inside a scrolling screen
 *   without stealing drags or firing network requests.
 */
@Composable
fun SpeciesMapView(
    taxonId: Int,
    placeId: Int?,
    modifier: Modifier = Modifier,
    basemap: Basemap = Basemap.MINIMAL,
    interactive: Boolean = true,
    controller: MapCameraController? = null,
    onState: (MapUiState) -> Unit = {},
    onObservationTap: (Observation) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val currentOnState by rememberUpdatedState(onState)
    val currentOnTap by rememberUpdatedState(onObservationTap)

    val mapView = remember {
        // Must be initialised before any MapView is constructed.
        MapLibre.getInstance(context)
        MapView(context)
    }

    // Mutable bits the MapLibre callbacks need to reach. Held in a plain holder
    // rather than Compose state because they are read from map listeners on the
    // UI thread, not from composition.
    val session = remember(taxonId) { MapSession(taxonId) }

    // MapView is an Android View with its own lifecycle contract; drive it
    // explicitly so it starts rendering as soon as it is composed and releases
    // its GL context on the way out.
    DisposableEffect(mapView) {
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Acquire the map once, then wire gestures, camera and tap handling.
    LaunchedEffect(mapView, interactive) {
        mapView.getMapAsync { map ->
            session.map = map
            controller?.map = map

            map.setMinZoomPreference(1.5)
            map.setMaxZoomPreference(18.0)
            map.uiSettings.apply {
                isLogoEnabled = false
                // OpenStreetMap's licence requires attribution; MapLibre's own
                // control reads the credits straight out of the loaded style.
                isAttributionEnabled = interactive
                isCompassEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                setScrollGesturesEnabled(interactive)
                setZoomGesturesEnabled(interactive)
                isDoubleTapGesturesEnabled = interactive
                isQuickZoomGesturesEnabled = interactive
            }

            if (map.cameraPosition.zoom < 2.0) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM))
            }

            // MapLibre appends listeners rather than replacing them, and this
            // effect restarts if its keys change — so register exactly once or
            // a single pan would fire a fetch for every past registration.
            if (interactive && !session.listenersWired) {
                session.listenersWired = true
                map.addOnCameraIdleListener {
                    session.onCameraIdle(scope, currentOnState)
                }
                map.addOnMapClickListener { latLng ->
                    session.handleTap(map, latLng, currentOnTap)
                }
            }
        }
    }

    // (Re)build the style whenever the basemap changes. A style swap wipes every
    // source and layer, so the data layers are re-added inside the load callback
    // rather than once at startup.
    LaunchedEffect(mapView, basemap, taxonId, interactive) {
        val heatColor = MapObservation
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(basemap.styleUri)) { style ->
                session.style = style
                installLayers(
                    style = style,
                    taxonId = taxonId,
                    heatColor = heatColor,
                    showPoints = interactive,
                )
                // A style reload drops the marker data with the layers; put the
                // observations we already have back so the map doesn't blank.
                session.reapplyObservations()
                if (interactive) session.onCameraIdle(scope, currentOnState)
            }
        }
    }

    // Frame the dataset's region once its bounds are known.
    LaunchedEffect(mapView, placeId) {
        if (placeId == null) return@LaunchedEffect
        val bounds = fetchPlaceBounds(placeId) ?: return@LaunchedEffect
        session.home = bounds
        controller?.home = bounds
        mapView.getMapAsync { map ->
            // Padding keeps the region clear of the chrome overlaid on the map.
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.toLatLngBounds(), 64))
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
    )
}

/**
 * Per-taxon map state that lives outside composition: the current MapLibre
 * handles, the observations on screen, and the debounce bookkeeping that keeps
 * panning from hammering the iNaturalist API.
 */
private class MapSession(val taxonId: Int) {
    var map: MapLibreMap? = null
    var style: Style? = null
    var home: Viewport? = null
    var listenersWired = false

    private var observations: List<Observation> = emptyList()
    private var byUuid: Map<String, Observation> = emptyMap()
    private var lastFetched: Viewport? = null
    private var lastZoomStep: Int = -1
    private var fetchJob: Job? = null

    fun onCameraIdle(
        scope: kotlinx.coroutines.CoroutineScope,
        onState: (MapUiState) -> Unit,
    ) {
        val map = this.map ?: return
        val zoom = map.cameraPosition.zoom
        val viewport = map.projection.visibleRegion.latLngBounds.toViewport()

        if (zoom < PIN_ZOOM) {
            // Back out to heatmap range: drop the points so returning to a
            // detailed view doesn't briefly show a stale set.
            fetchJob?.cancel()
            lastFetched = null
            lastZoomStep = -1
            if (observations.isNotEmpty()) {
                observations = emptyList()
                byUuid = emptyMap()
                pushToSource()
            }
            onState(MapUiState(pointsMode = false))
            return
        }

        // Only refetch when the camera has actually moved somewhere new, or
        // crossed a whole zoom level (which changes what fits in the page).
        val zoomStep = zoom.toInt()
        val previous = lastFetched
        val moved = previous == null || zoomStep != lastZoomStep || viewport.hasMovedFrom(previous)
        if (!moved) return

        lastFetched = viewport
        lastZoomStep = zoomStep
        fetchJob?.cancel()
        onState(MapUiState(loading = true, pointsMode = true, loadedCount = observations.size))
        fetchJob = scope.launch {
            // Settle first: a pinch fires several idle events, and only the last
            // one describes where the user actually stopped.
            delay(220)
            val page = fetchObservations(taxonId, viewport)
            val stillZoomedIn = (this@MapSession.map?.cameraPosition?.zoom ?: 0.0) >= PIN_ZOOM
            if (!stillZoomedIn) return@launch
            observations = page.observations
            byUuid = page.observations.associateBy { it.uuid }
            pushToSource()
            onState(
                MapUiState(
                    loading = false,
                    pointsMode = true,
                    loadedCount = page.observations.size,
                    totalInView = page.totalResults,
                )
            )
        }
    }

    /** Re-push the current observations after a style reload. */
    fun reapplyObservations() = pushToSource()

    private fun pushToSource() {
        val source = style?.getSourceAs<GeoJsonSource>(SRC_OBS) ?: return
        source.setGeoJson(FeatureCollection.fromFeatures(observations.map { it.toFeature() }))
    }

    /**
     * Resolve a tap: clusters zoom in, individual observations open their sheet.
     * Returns true when the tap hit something, so the map doesn't also treat it
     * as a background tap.
     */
    fun handleTap(
        map: MapLibreMap,
        latLng: LatLng,
        onObservation: (Observation) -> Unit,
    ): Boolean {
        val screenPoint = map.projection.toScreenLocation(latLng)
        // Fingers are wider than a 7dp dot, so search a box around the tap.
        val slop = 24f
        val box = android.graphics.RectF(
            screenPoint.x - slop, screenPoint.y - slop,
            screenPoint.x + slop, screenPoint.y + slop,
        )

        val clusters = map.queryRenderedFeatures(box, LYR_CLUSTER)
        if (clusters.isNotEmpty()) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(latLng, map.cameraPosition.zoom + 2.0),
                350,
            )
            return true
        }

        val points = map.queryRenderedFeatures(box, LYR_POINT, LYR_POINT_OBSCURED)
        val uuid = points.firstNotNullOfOrNull { it.getStringProperty(ObsProps.UUID) }
        val observation = uuid?.let { byUuid[it] } ?: return false
        onObservation(observation)
        return true
    }
}

private fun Observation.toFeature(): Feature {
    val props = JsonObject().apply {
        addProperty(ObsProps.UUID, uuid)
        addProperty(ObsProps.OBSCURED, obscured)
        addProperty(ObsProps.DATE, date)
        addProperty(ObsProps.USER, user)
        addProperty(ObsProps.PHOTO, photoUrl)
        addProperty(ObsProps.QUALITY, qualityGrade)
        addProperty(ObsProps.PLACE, placeGuess)
        addProperty(ObsProps.TAXON, taxonName)
    }
    return Feature.fromGeometry(Point.fromLngLat(lng, lat), props)
}

/**
 * Add the data layers on top of a freshly loaded basemap style.
 *
 * The heatmap goes *below* the basemap's first label layer so town and city
 * names stay readable through it; the observation markers go on top of
 * everything, since they are what the user is actually here for.
 */
private fun installLayers(
    style: Style,
    taxonId: Int,
    heatColor: Color,
    showPoints: Boolean,
) {
    val heatHex = String.format("%06X", heatColor.toArgb() and 0xFFFFFF)
    val tiles = TileSet("2.2.0", heatmapTileUrl(taxonId, heatHex)).apply {
        minZoom = 1f
        maxZoom = 18f
    }
    style.addSource(RasterSource(SRC_HEAT, tiles, 256))

    // iNaturalist's tiles already encode density in their own alpha, so for an
    // abundant species a high layer opacity just saturates into a solid slab.
    // Keeping it well under 1 lets coastlines and roads read through the heat,
    // which is what makes the density legible as a shape.
    val heatOpacity = if (showPoints) {
        // Cross-fade into the individual markers as they take over.
        PropertyFactory.rasterOpacity(
            Expression.interpolate(
                Expression.linear(), Expression.zoom(),
                Expression.stop(PIN_ZOOM - 2.0, 0.75f),
                Expression.stop(PIN_ZOOM + HEAT_FADE_SPAN, 0f),
            )
        )
    } else {
        // Static preview: there are no markers to hand over to, so the density
        // layer stays up at every zoom.
        PropertyFactory.rasterOpacity(0.62f)
    }

    val heatLayer = RasterLayer(LYR_HEAT, SRC_HEAT).withProperties(
        heatOpacity,
        PropertyFactory.rasterFadeDuration(180f),
    )
    val firstLabelLayer = style.layers.firstOrNull { it is SymbolLayer }?.id
    if (firstLabelLayer != null) {
        style.addLayerBelow(heatLayer, firstLabelLayer)
    } else {
        style.addLayer(heatLayer)
    }

    if (!showPoints) return

    style.addSource(
        GeoJsonSource(
            SRC_OBS,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions()
                .withCluster(true)
                .withClusterRadius(46)
                // Stop clustering near max zoom so dense patches finally split
                // into the individual records they're made of.
                .withClusterMaxZoom(15),
        )
    )

    // Tuned for the pale basemap: white halos separate markers from the map and
    // from each other, and white numerals sit on the filled cluster discs.
    val strokeColor = android.graphics.Color.WHITE
    val countColor = android.graphics.Color.WHITE

    // Clusters: bigger and hotter as they hold more records.
    style.addLayer(
        CircleLayer(LYR_CLUSTER, SRC_OBS).apply {
            setFilter(Expression.has("point_count"))
            withProperties(
                PropertyFactory.circleColor(
                    Expression.step(
                        Expression.toNumber(Expression.get("point_count")),
                        Expression.color(MapClusterLow.toArgb()),
                        Expression.stop(15, Expression.color(MapClusterMid.toArgb())),
                        Expression.stop(60, Expression.color(MapClusterHigh.toArgb())),
                    )
                ),
                PropertyFactory.circleRadius(
                    Expression.step(
                        Expression.toNumber(Expression.get("point_count")),
                        Expression.literal(15f),
                        Expression.stop(15, Expression.literal(20f)),
                        Expression.stop(60, Expression.literal(26f)),
                    )
                ),
                PropertyFactory.circleOpacity(0.92f),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(strokeColor),
            )
        }
    )

    style.addLayer(
        SymbolLayer(LYR_CLUSTER_COUNT, SRC_OBS).apply {
            setFilter(Expression.has("point_count"))
            withProperties(
                PropertyFactory.textField(Expression.get("point_count_abbreviated")),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor(countColor),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            )
        }
    )

    val isSingle = Expression.not(Expression.has("point_count"))

    // Obscured records: hollow ring, because iNaturalist has deliberately
    // fuzzed the coordinates and a solid dot would overstate the precision.
    style.addLayer(
        CircleLayer(LYR_POINT_OBSCURED, SRC_OBS).apply {
            setFilter(Expression.all(isSingle, Expression.eq(Expression.get(ObsProps.OBSCURED), true)))
            withProperties(
                PropertyFactory.circleRadius(6.5f),
                PropertyFactory.circleColor(android.graphics.Color.TRANSPARENT),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(heatColor.toArgb()),
            )
        }
    )

    // Precise records: filled dot with a halo so it reads on any basemap.
    style.addLayer(
        CircleLayer(LYR_POINT, SRC_OBS).apply {
            setFilter(Expression.all(isSingle, Expression.eq(Expression.get(ObsProps.OBSCURED), false)))
            withProperties(
                PropertyFactory.circleRadius(6f),
                PropertyFactory.circleColor(heatColor.toArgb()),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(strokeColor),
            )
        }
    )
}
