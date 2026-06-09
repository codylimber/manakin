package com.codylimber.fieldphenology.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.security.MessageDigest

class INatApiClient(private val client: OkHttpClient) {

    companion object {
        const val BASE_URL = "https://api.inaturalist.org/v2"
        // Some endpoints (e.g. places/autocomplete) are not implemented in v2, so a
        // few calls still target v1 explicitly.
        const val V1_BASE_URL = "https://api.inaturalist.org/v1"
        const val DATA_INTERVAL_MS = 2000L
        const val INTERACTIVE_INTERVAL_MS = 500L
        const val MAX_RETRIES = 5
        val RETRY_STATUSES = setOf(429, 500, 502, 503, 504)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val throttleMutex = Mutex()
    private var lastRequestTime = 0L
    private val cache = LinkedHashMap<String, JsonObject>(128, 0.75f, true) // LRU access order
    private val MAX_CACHE_SIZE = 500

    private suspend fun throttle(intervalMs: Long) {
        throttleMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < intervalMs) {
                delay(intervalMs - elapsed)
            }
            lastRequestTime = System.currentTimeMillis()
        }
    }

    private fun cacheKey(endpoint: String, params: Map<String, String>): String {
        val raw = endpoint + "|" + params.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }
        return MessageDigest.getInstance("MD5").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private suspend fun get(
        endpoint: String,
        params: Map<String, String> = emptyMap(),
        intervalMs: Long = DATA_INTERVAL_MS,
        useCache: Boolean = true,
        baseUrl: String = BASE_URL
    ): JsonObject {
        if (useCache) {
            val key = cacheKey("$baseUrl/$endpoint", params)
            cache[key]?.let { return it }
        }

        val urlBuilder = StringBuilder("$baseUrl/$endpoint")
        if (params.isNotEmpty()) {
            urlBuilder.append("?")
            urlBuilder.append(params.entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" })
        }
        val url = urlBuilder.toString()

        var lastStatus = 0
        for (attempt in 0 until MAX_RETRIES) {
            throttle(intervalMs)

            try {
                val request = Request.Builder().url(url).build()
                val body = withContext(Dispatchers.IO) {
                    val response = client.newCall(request).execute()
                    lastStatus = response.code
                    val responseBody = response.body?.string() ?: "{}"
                    response.close()
                    if (!response.isSuccessful) {
                        if (lastStatus in RETRY_STATUSES) {
                            return@withContext null // signal retry
                        }
                        throw RuntimeException("iNaturalist API error $lastStatus")
                    }
                    responseBody
                }

                if (body == null) {
                    // Retry case
                    val backoff = 5000L * (1L shl attempt)
                    delay(backoff)
                    continue
                }

                val result = json.parseToJsonElement(body).jsonObject
                if (useCache) {
                    cache[cacheKey("$baseUrl/$endpoint", params)] = result
                    while (cache.size > MAX_CACHE_SIZE) {
                        cache.remove(cache.keys.first())
                    }
                }
                return result
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Exception) {
                if (attempt < MAX_RETRIES - 1) {
                    val backoff = 5000L * (1L shl attempt)
                    delay(backoff)
                    continue
                }
                throw RuntimeException("Network error after $MAX_RETRIES retries: ${e.message}")
            }
        }
        throw RuntimeException("Failed after $MAX_RETRIES retries (last status: $lastStatus)")
    }

    // --- Interactive endpoints ---

    suspend fun searchPlaces(query: String): List<PlaceResult> {
        val data = get(
            "places/autocomplete",
            mapOf("q" to query, "per_page" to "10"),
            intervalMs = INTERACTIVE_INTERVAL_MS,
            useCache = false,
            baseUrl = V1_BASE_URL
        )
        return data["results"]?.jsonArray?.mapNotNull { r ->
            val obj = r.jsonObject
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val name = obj["display_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val adminLevel = obj["admin_level"]?.jsonPrimitive?.intOrNull
            PlaceResult(id, name, adminLevel)
        } ?: emptyList()
    }

    suspend fun searchTaxa(query: String): List<TaxonResult> {
        val data = get(
            "taxa/autocomplete",
            mapOf(
                "q" to query,
                "per_page" to "10",
                "fields" to "(name:!t,preferred_common_name:!t,rank:!t)"
            ),
            intervalMs = INTERACTIVE_INTERVAL_MS,
            useCache = false
        )
        return data["results"]?.jsonArray?.mapNotNull { r ->
            val obj = r.jsonObject
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val sci = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
            val common = obj["preferred_common_name"]?.jsonPrimitive?.contentOrNull ?: ""
            val rank = obj["rank"]?.jsonPrimitive?.contentOrNull ?: ""
            val display = if (common.isNotEmpty() && common != sci) "$common ($sci)" else sci
            val displayWithRank = if (rank.isNotEmpty()) "$display [$rank]" else display
            TaxonResult(id, displayWithRank, sci, common, rank)
        } ?: emptyList()
    }

    suspend fun getObservationPhotos(
        taxonId: Int,
        placeIds: List<Int>,
        qualityGrade: String = "research",
        maxResults: Int = 5
    ): List<JsonObject> {
        val params = mutableMapOf(
            "taxon_id" to taxonId.toString(),
            "quality_grade" to qualityGrade,
            "photos" to "true",
            "photo_licensed" to "true",
            "per_page" to maxResults.toString(),
            "order_by" to "votes",
            "fields" to "(photos:(license_code:!t,url:!t,medium_url:!t,attribution:!t))"
        )
        if (placeIds.isNotEmpty()) {
            params["place_id"] = placeIds.joinToString(",")
        }
        val data = get("observations", params, intervalMs = INTERACTIVE_INTERVAL_MS)
        val results = data["results"]?.jsonArray ?: return emptyList()
        val photos = mutableListOf<JsonObject>()
        for (obs in results) {
            val obsPhotos = obs.jsonObject["photos"]?.jsonArray ?: continue
            for (p in obsPhotos) {
                val photo = try { p.jsonObject } catch (_: Exception) { continue }
                val license = photo["license_code"]?.jsonPrimitive?.contentOrNull
                if (license != null && license.startsWith("cc")) {
                    photos.add(photo)
                }
            }
        }
        return photos
    }

    suspend fun getSpeciesCountEstimate(
        taxonId: Int?,
        placeIds: List<Int>,
        qualityGrade: String = "research"
    ): Int {
        var total = 0
        for (placeId in placeIds) {
            val (count, _) = getSpeciesCounts(taxonId, placeId, qualityGrade, page = 1, perPage = 1)
            total += count
        }
        return total
    }

    // --- Data endpoints ---

    suspend fun getSpeciesCounts(
        taxonId: Int?,
        placeId: Int,
        qualityGrade: String = "research",
        page: Int = 1,
        perPage: Int = 200
    ): Pair<Int, List<JsonObject>> {
        val params = mutableMapOf(
            "quality_grade" to qualityGrade,
            "place_id" to placeId.toString(),
            "page" to page.toString(),
            "per_page" to perPage.toString(),
            "fields" to "(taxon:(id:!t,name:!t,preferred_common_name:!t))"
        )
        if (taxonId != null) params["taxon_id"] = taxonId.toString()

        val data = get("observations/species_counts", params)
        val totalResults = data["total_results"]?.jsonPrimitive?.intOrNull ?: 0
        val results = data["results"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        return Pair(totalResults, results)
    }

    suspend fun getHistogram(
        taxonId: Int,
        placeId: Int,
        qualityGrade: String = "research"
    ): Map<Int, Int> {
        val data = get("observations/histogram", mapOf(
            "taxon_id" to taxonId.toString(),
            "place_id" to placeId.toString(),
            "quality_grade" to qualityGrade,
            "date_field" to "observed",
            "interval" to "week_of_year"
        ))
        val weekMap = data["results"]?.jsonObject?.get("week_of_year")?.jsonObject ?: return emptyMap()
        return weekMap.entries.associate { (k, v) ->
            k.toIntOrNull()?.let { week -> week to (v.jsonPrimitive.intOrNull ?: 0) }
                ?: (0 to 0)
        }.filterKeys { it in 1..53 }
    }

    suspend fun getTaxaDetails(taxonIds: List<Int>): List<JsonObject> {
        val idsStr = taxonIds.take(30).joinToString(",")
        val data = get(
            "taxa/$idsStr",
            mapOf(
                "fields" to "(name:!t,rank:!t,preferred_common_name:!t," +
                    "default_photo:(medium_url:!t,url:!t)," +
                    "taxon_photos:(photo:(license_code:!t,url:!t,medium_url:!t,attribution:!t))," +
                    "ancestors:(id:!t,rank:!t,name:!t,preferred_common_name:!t))"
            )
        )
        return data["results"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
    }

    suspend fun downloadPhoto(url: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val bytes = response.body?.bytes()
                response.close()
                if (response.isSuccessful) bytes else null
            } catch (_: Exception) {
                null
            }
        }
    }
    suspend fun getUserSpeciesTaxonIds(
        username: String,
        taxonId: Int?,
        placeId: Int?
    ): Set<Int> {
        val allIds = mutableSetOf<Int>()
        var page = 1
        while (true) {
            val params = mutableMapOf(
                "user_id" to username,
                "quality_grade" to "research",
                "page" to page.toString(),
                "per_page" to "200",
                "fields" to "(taxon:(id:!t))"
            )
            if (taxonId != null) params["taxon_id"] = taxonId.toString()
            if (placeId != null) params["place_id"] = placeId.toString()
            val data = get("observations/species_counts", params)
            val totalResults = data["total_results"]?.jsonPrimitive?.intOrNull ?: 0
            val results = data["results"]?.jsonArray ?: break
            for (r in results) {
                val tid = r.jsonObject["taxon"]?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull
                if (tid != null) allIds.add(tid)
            }
            if (allIds.size >= totalResults || results.isEmpty()) break
            page++
        }
        return allIds
    }
    suspend fun getUserLifeListForTaxon(
        username: String,
        taxonId: Int,
        onProgress: (fetched: Int, total: Int) -> Unit = { _, _ -> }
    ): List<com.codylimber.fieldphenology.data.model.LifeListEntry> {
        val firstSeen = mutableMapOf<Int, String>()
        val lastSeen = mutableMapOf<Int, String>()
        val taxonNames = mutableMapOf<Int, Pair<String, String>>() // tid -> (common, scientific)
        var page = 1
        val maxPage = 50
        while (page <= maxPage) {
            val params = mutableMapOf(
                "user_id" to username,
                "taxon_id" to taxonId.toString(),
                "quality_grade" to "research",
                "order_by" to "observed_on",
                "order" to "asc",
                "page" to page.toString(),
                "per_page" to "200",
                "fields" to "(observed_on:!t,taxon:(id:!t,name:!t,preferred_common_name:!t))"
            )
            val data = get("observations", params, useCache = false)
            val totalResults = data["total_results"]?.jsonPrimitive?.intOrNull ?: 0
            if (totalResults == 0) break
            val results = try { data["results"]?.jsonArray } catch (_: Exception) { null } ?: break
            if (results.isEmpty()) break
            for (r in results) {
                val obj = try { r.jsonObject } catch (_: Exception) { continue }
                val taxon = obj["taxon"]?.jsonObject ?: continue
                val tid = taxon["id"]?.jsonPrimitive?.intOrNull ?: continue
                val date = obj["observed_on"]?.jsonPrimitive?.contentOrNull ?: continue
                if (!firstSeen.containsKey(tid)) {
                    firstSeen[tid] = date
                    val common = taxon["preferred_common_name"]?.jsonPrimitive?.contentOrNull ?: ""
                    val scientific = taxon["name"]?.jsonPrimitive?.contentOrNull ?: ""
                    taxonNames[tid] = Pair(common, scientific)
                }
                lastSeen[tid] = date
            }
            onProgress(firstSeen.size, totalResults)
            if (page * 200 >= totalResults) break
            page++
        }

        // Batch-fetch taxon details from /taxa endpoint to get reliable photo URLs,
        // resolve subspecies/varieties up to species rank, and drop anything above species.
        val photoUrls = mutableMapOf<Int, String?>()
        val speciesNames = mutableMapOf<Int, Pair<String, String>>() // resolved species id -> (common, sci)
        // Maps raw observed tid -> resolved species tid (same if already species)
        val resolvedId = mutableMapOf<Int, Int>()

        val subSpeciesRanks = setOf("subspecies", "variety", "form", "infrahybrid", "hybrid")
        val speciesRank = "species"

        val allIds = firstSeen.keys.toList()
        allIds.chunked(30).forEach { chunk ->
            try {
                val taxaResults = getTaxaDetails(chunk)
                for (t in taxaResults) {
                    val tid = t["id"]?.jsonPrimitive?.intOrNull ?: continue
                    val rank = t["rank"]?.jsonPrimitive?.contentOrNull ?: continue

                    val targetId: Int
                    val targetName: Pair<String, String>

                    if (rank == speciesRank) {
                        targetId = tid
                        val common = t["preferred_common_name"]?.jsonPrimitive?.contentOrNull ?: ""
                        val sci = t["name"]?.jsonPrimitive?.contentOrNull ?: ""
                        targetName = Pair(common, sci)
                    } else if (rank in subSpeciesRanks) {
                        // Find species-rank ancestor
                        val ancestors = try { t["ancestors"]?.jsonArray } catch (_: Exception) { null }
                        val speciesAncestor = ancestors?.mapNotNull { a ->
                            try { a.jsonObject } catch (_: Exception) { null }
                        }?.lastOrNull { a ->
                            a["rank"]?.jsonPrimitive?.contentOrNull == speciesRank
                        }
                        if (speciesAncestor == null) continue // can't resolve, skip
                        targetId = speciesAncestor["id"]?.jsonPrimitive?.intOrNull ?: continue
                        val common = speciesAncestor["preferred_common_name"]?.jsonPrimitive?.contentOrNull ?: ""
                        val sci = speciesAncestor["name"]?.jsonPrimitive?.contentOrNull ?: ""
                        targetName = Pair(common, sci)
                    } else {
                        continue // above species rank — skip
                    }

                    resolvedId[tid] = targetId
                    if (!speciesNames.containsKey(targetId)) {
                        speciesNames[targetId] = targetName
                    }

                    val photo = t["default_photo"]?.jsonObject
                    if (!photoUrls.containsKey(targetId)) {
                        photoUrls[targetId] = photo?.get("medium_url")?.jsonPrimitive?.contentOrNull
                            ?: photo?.get("url")?.jsonPrimitive?.contentOrNull
                    }
                }
            } catch (_: Exception) { /* leave nulls */ }
        }

        // Aggregate dates by resolved species ID
        val speciesFirstSeen = mutableMapOf<Int, String>()
        val speciesLastSeen = mutableMapOf<Int, String>()
        for ((rawId, date) in firstSeen) {
            val sid = resolvedId[rawId] ?: continue
            val existing = speciesFirstSeen[sid]
            if (existing == null || date < existing) speciesFirstSeen[sid] = date
        }
        for ((rawId, date) in lastSeen) {
            val sid = resolvedId[rawId] ?: continue
            val existing = speciesLastSeen[sid]
            if (existing == null || date > existing) speciesLastSeen[sid] = date
        }

        return speciesFirstSeen.keys.map { sid ->
            val (common, scientific) = speciesNames[sid] ?: Pair("", "")
            com.codylimber.fieldphenology.data.model.LifeListEntry(
                taxonId = sid,
                commonName = common,
                scientificName = scientific,
                firstObservedDate = speciesFirstSeen[sid]!!,
                lastObservedDate = speciesLastSeen[sid] ?: speciesFirstSeen[sid]!!,
                photoUrl = photoUrls[sid]
            )
        }
    }
}
