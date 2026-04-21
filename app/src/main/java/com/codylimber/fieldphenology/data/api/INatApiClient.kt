package com.codylimber.fieldphenology.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

class INatApiClient(private val client: OkHttpClient) {

    companion object {
        const val BASE_URL = "https://api.inaturalist.org/v1"
        const val DATA_INTERVAL_MS = 2000L
        const val INTERACTIVE_INTERVAL_MS = 500L
        const val MAX_RETRIES = 5
        val RETRY_STATUSES = setOf(429, 500, 502, 503, 504)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var lastRequestTime = 0L
    private val cache = mutableMapOf<String, JsonObject>()

    private suspend fun throttle(intervalMs: Long) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestTime
        if (elapsed < intervalMs) {
            delay(intervalMs - elapsed)
        }
        lastRequestTime = System.currentTimeMillis()
    }

    private fun cacheKey(endpoint: String, params: Map<String, String>): String {
        val raw = endpoint + "|" + params.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }
        return MessageDigest.getInstance("MD5").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private suspend fun get(
        endpoint: String,
        params: Map<String, String> = emptyMap(),
        intervalMs: Long = DATA_INTERVAL_MS,
        useCache: Boolean = true
    ): JsonObject {
        if (useCache) {
            val key = cacheKey(endpoint, params)
            cache[key]?.let { return it }
        }

        val urlBuilder = StringBuilder("$BASE_URL/$endpoint")
        if (params.isNotEmpty()) {
            urlBuilder.append("?")
            urlBuilder.append(params.entries.joinToString("&") { "${it.key}=${it.value}" })
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
                    cache[cacheKey(endpoint, params)] = result
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
            useCache = false
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
            mapOf("q" to query, "per_page" to "10"),
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
            "order_by" to "votes"
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
            "per_page" to perPage.toString()
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
        val data = get("taxa/$idsStr")
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
                "per_page" to "200"
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
}
