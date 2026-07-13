package com.codylimber.fieldphenology.ui.screens.speciesdetail

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.modules.MapTileApproximater
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.TilesOverlay

private val httpClient = OkHttpClient()

// Above this zoom the map swaps the density heatmap for individual observation
// markers (precise = pin, obscured = ring). iNat does the same: heatmap when
// zoomed out, real points when zoomed in.
private const val PIN_ZOOM_THRESHOLD = 12.0

private data class MapObservation(val lat: Double, val lng: Double, val obscured: Boolean)

/**
 * Fetch individual observations of a taxon within a viewport. Each carries whether
 * its location is obscured — iNat randomizes obscured coordinates to a ~0.2° cell,
 * so those points are only approximate and are drawn as rings rather than pins.
 */
private suspend fun fetchObservations(taxonId: Int, bbox: BoundingBox): List<MapObservation> =
    withContext(Dispatchers.IO) {
        try {
            val url = "https://api.inaturalist.org/v1/observations" +
                "?taxon_id=$taxonId&mappable=true&per_page=200&order_by=created_at&order=desc" +
                "&nelat=${bbox.latNorth}&nelng=${bbox.lonEast}" +
                "&swlat=${bbox.latSouth}&swlng=${bbox.lonWest}"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Manakin/1.0")
                .build()
            val body = httpClient.newCall(request).execute().use { it.body?.string() }
                ?: return@withContext emptyList()
            val results = JSONObject(body).getJSONArray("results")
            val list = ArrayList<MapObservation>(results.length())
            for (i in 0 until results.length()) {
                val o = results.getJSONObject(i)
                val geo = o.optJSONObject("geojson") ?: continue
                val coords = geo.optJSONArray("coordinates") ?: continue
                val lng = coords.optDouble(0, Double.NaN)
                val lat = coords.optDouble(1, Double.NaN)
                if (lat.isNaN() || lng.isNaN()) continue
                val obscured = o.optBoolean("obscured", false) ||
                    o.optString("geoprivacy") == "obscured" ||
                    o.optString("taxon_geoprivacy") == "obscured"
                list.add(MapObservation(lat, lng, obscured))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

/**
 * Draws observations on top of the base map: precise ones as a teardrop pin whose
 * tip marks the exact spot, obscured ones as a hollow ring (location approximate).
 * Both get a white halo so they read against any basemap color.
 */
private class ObservationsOverlay(context: Context) : Overlay() {
    private class Marker(val point: GeoPoint, val obscured: Boolean)

    @Volatile private var markers: List<Marker> = emptyList()
    private val d = context.resources.displayMetrics.density
    private val red = Color.parseColor("#E8000D")

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = red }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.WHITE; strokeWidth = 3f * d
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = red; strokeWidth = 2.5f * d
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }

    // Reused across the whole draw pass so panning/zooming doesn't allocate per pin
    // per frame (that GC churn was a big source of the map jank).
    private val pt = Point()
    private val path = Path()
    private val rect = RectF()

    fun setObservations(list: List<MapObservation>) {
        markers = list.map { Marker(GeoPoint(it.lat, it.lng), it.obscured) }
    }

    override fun draw(canvas: Canvas, projection: Projection) {
        if (!isEnabled) return
        val snapshot = markers
        if (snapshot.isEmpty()) return
        val r = 6.5f * d
        for (m in snapshot) {
            projection.toPixels(m.point, pt)
            val x = pt.x.toFloat()
            val y = pt.y.toFloat()
            if (m.obscured) {
                canvas.drawCircle(x, y, r, haloPaint)
                canvas.drawCircle(x, y, r, ringPaint)
            } else {
                buildPin(x, y, r)
                canvas.drawPath(path, haloPaint)
                canvas.drawPath(path, fillPaint)
                canvas.drawCircle(x, y - 2.4f * r, r * 0.42f, eyePaint)
            }
        }
    }

    // Teardrop pin with the tip anchored at (cx, tipY) so it points at the exact spot.
    // Builds into the reused `path` (rewind, not allocate).
    private fun buildPin(cx: Float, tipY: Float, r: Float) {
        val headCy = tipY - 2.4f * r
        rect.set(cx - r, headCy - r, cx + r, headCy + r)
        path.rewind()
        path.moveTo(cx, tipY)
        path.cubicTo(cx - r * 0.85f, tipY - r * 1.1f, cx - r, headCy + r * 0.55f, cx - r, headCy)
        path.arcTo(rect, 180f, 180f, false)
        path.cubicTo(cx + r, headCy + r * 0.55f, cx + r * 0.85f, tipY - r * 1.1f, cx, tipY)
        path.close()
    }
}

private suspend fun fetchPlaceBounds(placeId: Int): BoundingBox? = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder()
            .url("https://api.inaturalist.org/v2/places/$placeId?fields=(bounding_box_geojson:!t,geometry_geojson:!t)")
            .header("User-Agent", "Manakin/1.0")
            .build()
        val body = httpClient.newCall(request).execute().use { it.body?.string() } ?: return@withContext null
        val result = JSONObject(body).getJSONArray("results").getJSONObject(0)
        val bbox = result.optJSONObject("bounding_box_geojson")
            ?: result.optJSONObject("geometry_geojson")
            ?: return@withContext null
        val ring = bbox.getJSONArray("coordinates").getJSONArray(0)
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE; var maxLng = -Double.MAX_VALUE
        for (i in 0 until ring.length()) {
            val pt = ring.getJSONArray(i)
            val lng = pt.getDouble(0); val lat = pt.getDouble(1)
            if (lat < minLat) minLat = lat; if (lat > maxLat) maxLat = lat
            if (lng < minLng) minLng = lng; if (lng > maxLng) maxLng = lng
        }
        BoundingBox(maxLat, maxLng, minLat, minLng)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun SpeciesMapView(
    taxonId: Int,
    placeId: Int?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidTileCache = java.io.File(context.cacheDir, "osmdroid")
        tileFileSystemCacheMaxBytes = 50L * 1024 * 1024 // 50MB tile cache
        // Fetch tiles with more parallelism so the heatmap re-appears quickly
        // after a zoom instead of trickling in (which looks like lag).
        tileDownloadThreads = 6
        tileDownloadMaxQueueSize = 48
    }

    // High-DPI ("retina", @2x = 512px) base map. The classic OSM "Mapnik"
    // server only serves 256px tiles, so on a dense phone screen those get
    // upscaled ~2.6x and look soft/pixelated. Carto's Voyager style is an
    // OSM-derived colorful basemap (green parks, blue water, colored roads)
    // that serves native 512px @2x tiles with no API key, so the base only
    // has to scale ~1.3x to fill the DPI-scaled tile grid below — crisp.
    val baseTileSource = remember {
        object : OnlineTileSourceBase(
            "CartoVoyagerRetina", 0, 20, 512, "@2x.png",
            arrayOf(
                "https://a.basemaps.cartocdn.com/",
                "https://b.basemaps.cartocdn.com/",
                "https://c.basemaps.cartocdn.com/",
                "https://d.basemaps.cartocdn.com/"
            )
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val z = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                return baseUrl + "rastertiles/voyager/$z/$x/$y@2x.png"
            }
        }
    }

    // Global heatmap — no place_id filter so worldwide observations show
    val inatTileSource = remember(taxonId) {
        object : OnlineTileSourceBase(
            "iNaturalist-$taxonId", 1, 18, 256, ".png",
            arrayOf("https://api.inaturalist.org/")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val z = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                // Map tiles are only served from v1 (v2 returns 404 for colored_heatmap).
                return "https://api.inaturalist.org/v1/colored_heatmap/$z/$x/$y.png?taxon_id=$taxonId&color=%23e8000d"
            }
        }
    }

    val scope = rememberCoroutineScope()
    val obsOverlay = remember { ObservationsOverlay(context) }
    val legendState = remember { mutableStateOf(false) }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            // iNat serves native heatmap tiles up to ~z18, so allow zooming in that
            // far — dots stay at native size instead of being scaled into big blobs.
            minZoomLevel = 2.0
            maxZoomLevel = 18.0
            // Crisp colorful OSM-style base via 512px @2x retina tiles.
            setTileSource(baseTileSource)
            // Scale tiles to the screen density so the (256px) heatmap dots are big
            // enough to read on high-DPI phones. The base is already @2x, so it only
            // scales mildly and stays sharp instead of being upscaled from 256px.
            // Set after the tile source so the DPI scale is derived from the 512px base.
            isTilesScaledToDpi = true

            // Apply the initial camera only once the view has real dimensions.
            // Setting zoom/center while the view is still 0×0 leaves OSMDroid unable
            // to work out which tiles to load, so the map stays blank until a touch
            // forces a re-layout.
            addOnFirstLayoutListener { _, _, _, _, _ ->
                controller.setZoom(4.0)
                controller.setCenter(GeoPoint(39.5, -98.35))
                // postInvalidate (not invalidate) so the redraw lands on a fresh
                // frame after layout rather than being swallowed mid-layout. Without
                // this OSMDroid waits for a touch before drawing the first tiles.
                postInvalidate()
            }

            // Disable tile approximation for the iNat overlay — without this, OSMDroid
            // stretches tiles from adjacent zoom levels while correct tiles load,
            // causing the giant blurry blob effect when zooming.
            val inatProvider = MapTileProviderBasic(context, inatTileSource).apply {
                tileRequestCompleteHandlers.removeIf { it is MapTileApproximater }
            }
            val inatOverlay = TilesOverlay(inatProvider, context).apply {
                loadingBackgroundColor = android.graphics.Color.TRANSPARENT
                loadingLineColor = android.graphics.Color.TRANSPARENT
            }
            overlays.add(inatOverlay)

            // Observation markers overlay — hidden until the user zooms in past the
            // pin threshold, at which point it replaces the heatmap.
            obsOverlay.isEnabled = false
            overlays.add(obsOverlay)

            // Carto's free tiles require attribution to OSM and CARTO.
            overlays.add(CopyrightOverlay(context).apply {
                setCopyrightNotice("© OpenStreetMap contributors, © CARTO")
            })

            // Once the map settles after a pan/zoom, decide what to show:
            //  - zoomed out -> density heatmap (markers off)
            //  - zoomed in  -> individual observation markers for the current viewport.
            // We keep the heatmap up until the markers have actually loaded, then swap
            // it off — so there's no blank flash and the heatmap doesn't blink on every
            // zoom. Vector markers track the projection during the gesture, so they don't
            // need hiding mid-zoom. Refetches are skipped when the viewport barely moved.
            val settleHandler = Handler(Looper.getMainLooper())
            var fetchJob: Job? = null
            var lastLat = Double.NaN
            var lastLng = Double.NaN
            var lastZoomInt = -1
            val settle = Runnable {
                if (zoomLevelDouble >= PIN_ZOOM_THRESHOLD) {
                    obsOverlay.isEnabled = true
                    legendState.value = true
                    val bb = boundingBox
                    val cLat = (bb.latNorth + bb.latSouth) / 2.0
                    val cLng = (bb.lonEast + bb.lonWest) / 2.0
                    val zInt = zoomLevelDouble.toInt()
                    val spanLat = bb.latNorth - bb.latSouth
                    val spanLng = bb.lonEast - bb.lonWest
                    val moved = lastLat.isNaN() || zInt != lastZoomInt ||
                        kotlin.math.abs(cLat - lastLat) > spanLat * 0.25 ||
                        kotlin.math.abs(cLng - lastLng) > spanLng * 0.25
                    if (moved) {
                        lastLat = cLat; lastLng = cLng; lastZoomInt = zInt
                        fetchJob?.cancel()
                        fetchJob = scope.launch {
                            delay(250)
                            val obs = fetchObservations(taxonId, bb)
                            // Only apply if we're still zoomed in — the user may have
                            // zoomed back out while this was in flight.
                            if (zoomLevelDouble >= PIN_ZOOM_THRESHOLD) {
                                obsOverlay.setObservations(obs)
                                inatOverlay.isEnabled = false
                                postInvalidate()
                            }
                        }
                    }
                } else {
                    fetchJob?.cancel()
                    inatOverlay.isEnabled = true
                    obsOverlay.isEnabled = false
                    obsOverlay.setObservations(emptyList())
                    legendState.value = false
                    lastLat = Double.NaN; lastLng = Double.NaN; lastZoomInt = -1
                }
                invalidate()
            }
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    settleHandler.removeCallbacks(settle)
                    settleHandler.postDelayed(settle, 200)
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    settleHandler.removeCallbacks(settle)
                    settleHandler.postDelayed(settle, 200)
                    return false
                }
            })
        }
    }

    // Fetch place bounds and zoom in once loaded
    LaunchedEffect(placeId) {
        if (placeId != null) {
            val bounds = fetchPlaceBounds(placeId)
            if (bounds != null) {
                mapView.post {
                    mapView.zoomToBoundingBox(bounds, false, 48)
                }
            }
        }
    }

    // OSMDroid hosted in a Compose AndroidView (inside a scrolling screen) doesn't
    // reliably repaint as the first tiles arrive — the async tile-loaded invalidate
    // can fire before the view is attached and gets dropped, so the map stays blank
    // until a touch forces a redraw. Nudge it from the Compose side, where the view
    // is attached, for a few seconds after it appears.
    LaunchedEffect(taxonId, placeId) {
        repeat(12) {
            kotlinx.coroutines.delay(250)
            mapView.postInvalidate()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            // Kick a redraw once the view is composed/attached. OSMDroid otherwise
            // renders nothing until the first touch event invalidates the map.
            update = { it.onResume(); it.invalidate() },
            modifier = Modifier.fillMaxSize()
        )
        // Legend appears only in pin mode so users know pin = exact, ring = obscured.
        if (legendState.value) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(androidx.compose.ui.graphics.Color(0xCC1A1A1A))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("📍 Precise", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp)
                Text("○ Obscured", color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp)
            }
        }
    }
}
