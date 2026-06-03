package com.codylimber.fieldphenology.ui.screens.speciesdetail

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
            .url("https://api.inaturalist.org/v1/places/$placeId")
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
    }

    // Global heatmap — no place_id filter so worldwide observations show
    val inatTileSource = remember(taxonId) {
        object : OnlineTileSourceBase(
            "iNaturalist-$taxonId", 1, 13, 256, ".png",
            arrayOf("https://api.inaturalist.org/")
        ) {
            override fun getTileURLString(pMapTileIndex: Long): String {
                val z = MapTileIndex.getZoom(pMapTileIndex)
                val x = MapTileIndex.getX(pMapTileIndex)
                val y = MapTileIndex.getY(pMapTileIndex)
                return "https://api.inaturalist.org/v1/colored_heatmap/$z/$x/$y.png?taxon_id=$taxonId&color=%23e8000d"
            }
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(39.5, -98.35))
            setTileSource(TileSourceFactory.MAPNIK)

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
        modifier = modifier
    )
}
