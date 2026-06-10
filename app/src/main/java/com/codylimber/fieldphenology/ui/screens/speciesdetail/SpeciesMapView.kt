package com.codylimber.fieldphenology.ui.screens.speciesdetail

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay

private val httpClient = OkHttpClient()

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

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            // Scale tiles to the screen density so base-map labels and the heatmap
            // dots are big enough to read on high-DPI phones (1:1 pixels = tiny).
            isTilesScaledToDpi = true
            // iNat serves native heatmap tiles up to ~z18, so allow zooming in that
            // far — dots stay at native size instead of being scaled into big blobs.
            minZoomLevel = 2.0
            maxZoomLevel = 18.0
            setTileSource(TileSourceFactory.MAPNIK)

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

            // While a zoom is in progress OSMDroid keeps painting the previous zoom
            // level's tiles (scaled up = big dots) next to freshly-loaded tiles
            // (small dots). Hide the heatmap during the gesture and bring it back —
            // at a single, consistent dot size — once the map settles. Panning leaves
            // the overlay alone since it doesn't change dot size.
            val idleHandler = Handler(Looper.getMainLooper())
            val showOverlay = Runnable {
                if (!inatOverlay.isEnabled) {
                    inatOverlay.isEnabled = true
                    invalidate()
                }
            }
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    // Keep extending the hidden window if a zoom is still settling.
                    if (!inatOverlay.isEnabled) {
                        idleHandler.removeCallbacks(showOverlay)
                        idleHandler.postDelayed(showOverlay, 180)
                    }
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    inatOverlay.isEnabled = false
                    idleHandler.removeCallbacks(showOverlay)
                    idleHandler.postDelayed(showOverlay, 180)
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

    AndroidView(
        factory = { mapView },
        // Kick a redraw once the view is composed/attached. OSMDroid otherwise
        // renders nothing until the first touch event invalidates the map.
        update = { it.onResume(); it.invalidate() },
        modifier = modifier
    )
}
