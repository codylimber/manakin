package com.codylimber.fieldphenology.ui.map

import com.codylimber.fieldphenology.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

// iNaturalist asks API clients to identify themselves; read the version from
// the build so it can't drift out of date the way a literal would.
private val USER_AGENT =
    "Manakin/${BuildConfig.VERSION_NAME} (Android; github.com/codylimber/manakin)"

/** Feature property keys, shared between the GeoJSON builder and the tap handler. */
object ObsProps {
    const val UUID = "uuid"
    const val OBSCURED = "obscured"
    const val DATE = "date"
    const val USER = "user"
    const val PHOTO = "photo"
    const val QUALITY = "quality"
    const val PLACE = "place"
    const val TAXON = "taxon"
}

/**
 * One mappable iNaturalist observation.
 *
 * [obscured] matters for how it is drawn: iNat randomises the coordinates of
 * obscured observations to a ~0.2° cell (for sensitive species, or at the
 * observer's request), so those points are a neighbourhood, not a location.
 * They get a hollow ring instead of a filled dot so the map never implies more
 * precision than the data has.
 */
data class Observation(
    val uuid: String,
    val lat: Double,
    val lng: Double,
    val obscured: Boolean,
    val date: String?,
    val user: String?,
    val photoUrl: String?,
    val qualityGrade: String?,
    val placeGuess: String?,
    val taxonName: String?,
) {
    /** iNat serves square thumbnails by default; ask for a larger crop. */
    val mediumPhotoUrl: String? get() = photoUrl?.replace("/square.", "/medium.")
    val webUrl: String get() = "https://www.inaturalist.org/observations/$uuid"
}

/** A page of observations plus how many exist in the viewport overall. */
data class ObservationPage(
    val observations: List<Observation>,
    val totalResults: Int,
)

/**
 * Geographic bounds of a map viewport.
 *
 * Kept separate from MapLibre's `LatLngBounds` so the fetching logic stays
 * testable and free of map-SDK types.
 */
data class Viewport(
    val north: Double,
    val east: Double,
    val south: Double,
    val west: Double,
) {
    val latSpan get() = north - south
    val lngSpan get() = east - west
    val centerLat get() = (north + south) / 2.0
    val centerLng get() = (east + west) / 2.0

    /**
     * Whether [other] has drifted far enough from this viewport to be worth
     * refetching — a quarter of the visible span in either direction. Without
     * this the map would re-query iNat on every small nudge of the camera.
     */
    fun hasMovedFrom(other: Viewport): Boolean =
        kotlin.math.abs(centerLat - other.centerLat) > latSpan * 0.25 ||
            kotlin.math.abs(centerLng - other.centerLng) > lngSpan * 0.25
}

/**
 * Fetch mappable observations of [taxonId] inside [viewport].
 *
 * Uses the v2 API with an explicit field selection: the v1 default payload is
 * roughly ten times the size for the same records, and on a phone that
 * difference is the map feeling instant versus feeling stuck.
 */
suspend fun fetchObservations(
    taxonId: Int,
    viewport: Viewport,
    limit: Int = 200,
): ObservationPage = withContext(Dispatchers.IO) {
    val fields = "(geojson:!t,obscured:!t,observed_on_details:(date:!t),user:(login:!t)," +
        "photos:(url:!t),quality_grade:!t,place_guess:!t,taxon:(preferred_common_name:!t,name:!t))"
    val url = "https://api.inaturalist.org/v2/observations" +
        "?taxon_id=$taxonId&mappable=true&per_page=$limit" +
        "&order_by=observed_on&order=desc" +
        "&nelat=${viewport.north}&nelng=${viewport.east}" +
        "&swlat=${viewport.south}&swlng=${viewport.west}" +
        "&fields=$fields"

    try {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val body = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext ObservationPage(emptyList(), 0)
            response.body?.string()
        } ?: return@withContext ObservationPage(emptyList(), 0)

        val json = JSONObject(body)
        val results = json.optJSONArray("results") ?: return@withContext ObservationPage(emptyList(), 0)
        val list = ArrayList<Observation>(results.length())
        for (i in 0 until results.length()) {
            val o = results.optJSONObject(i) ?: continue
            val coords = o.optJSONObject("geojson")?.optJSONArray("coordinates") ?: continue
            val lng = coords.optDouble(0, Double.NaN)
            val lat = coords.optDouble(1, Double.NaN)
            if (lat.isNaN() || lng.isNaN()) continue

            val taxon = o.optJSONObject("taxon")
            list.add(
                Observation(
                    uuid = o.optString("uuid"),
                    lat = lat,
                    lng = lng,
                    obscured = o.optBoolean("obscured", false),
                    date = o.optJSONObject("observed_on_details")?.optString("date")?.ifEmpty { null },
                    user = o.optJSONObject("user")?.optString("login")?.ifEmpty { null },
                    photoUrl = o.optJSONArray("photos")
                        ?.optJSONObject(0)?.optString("url")?.ifEmpty { null },
                    qualityGrade = o.optString("quality_grade").ifEmpty { null },
                    placeGuess = o.optString("place_guess").ifEmpty { null },
                    taxonName = taxon?.optString("preferred_common_name")?.ifEmpty { null }
                        ?: taxon?.optString("name")?.ifEmpty { null },
                )
            )
        }
        ObservationPage(list, json.optInt("total_results", list.size))
    } catch (e: Exception) {
        ObservationPage(emptyList(), 0)
    }
}

/**
 * Look up the geographic extent of an iNaturalist place, so the map can open
 * framed on the dataset's region rather than on a hardcoded view of the US.
 */
suspend fun fetchPlaceBounds(placeId: Int): Viewport? = withContext(Dispatchers.IO) {
    try {
        val request = Request.Builder()
            .url(
                "https://api.inaturalist.org/v2/places/$placeId" +
                    "?fields=(bounding_box_geojson:!t,geometry_geojson:!t)"
            )
            .header("User-Agent", USER_AGENT)
            .build()
        val body = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body?.string()
        } ?: return@withContext null

        val result = JSONObject(body).optJSONArray("results")?.optJSONObject(0)
            ?: return@withContext null
        val geo = result.optJSONObject("bounding_box_geojson")
            ?: result.optJSONObject("geometry_geojson")
            ?: return@withContext null
        val ring = geo.optJSONArray("coordinates")?.optJSONArray(0) ?: return@withContext null

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE
        var maxLng = -Double.MAX_VALUE
        for (i in 0 until ring.length()) {
            val pt = ring.optJSONArray(i) ?: continue
            val lng = pt.optDouble(0, Double.NaN)
            val lat = pt.optDouble(1, Double.NaN)
            if (lat.isNaN() || lng.isNaN()) continue
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lng < minLng) minLng = lng
            if (lng > maxLng) maxLng = lng
        }
        if (minLat > maxLat || minLng > maxLng) return@withContext null
        Viewport(north = maxLat, east = maxLng, south = minLat, west = minLng)
    } catch (e: Exception) {
        null
    }
}
