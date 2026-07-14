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
import java.time.LocalDate

/**
 * Client for GBIF's open occurrence/species APIs. No authentication is required for
 * the search + facet endpoints used here.
 *
 * GBIF's only temporal facet is month, so we build the app's weekly (1..53) phenology
 * by sampling real occurrence records and binning their eventDate into weeks.
 */
class GbifApiClient(private val client: OkHttpClient) {

    companion object {
        const val BASE_URL = "https://api.gbif.org/v1"
        const val DATA_INTERVAL_MS = 300L
        const val INTERACTIVE_INTERVAL_MS = 250L
        const val PAGE_SIZE = 300              // GBIF max page size for occurrence search
        const val FACET_PAGE = 300             // speciesKey facet buckets per request
        const val MAX_RETRIES = 4
        val RETRY_STATUSES = setOf(429, 500, 502, 503, 504)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val throttleMutex = Mutex()
    private var lastRequestTime = 0L

    private suspend fun throttle(intervalMs: Long) {
        throttleMutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime
            if (elapsed < intervalMs) delay(intervalMs - elapsed)
            lastRequestTime = System.currentTimeMillis()
        }
    }

    private suspend fun get(
        endpoint: String,
        params: Map<String, String> = emptyMap(),
        intervalMs: Long = DATA_INTERVAL_MS
    ): JsonElement {
        val urlBuilder = StringBuilder("$BASE_URL/$endpoint")
        if (params.isNotEmpty()) {
            urlBuilder.append("?")
            urlBuilder.append(params.entries.joinToString("&") {
                "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
            })
        }
        val url = urlBuilder.toString()

        var lastStatus = 0
        for (attempt in 0 until MAX_RETRIES) {
            throttle(intervalMs)
            try {
                val body = withContext(Dispatchers.IO) {
                    val response = client.newCall(Request.Builder().url(url).build()).execute()
                    lastStatus = response.code
                    val responseBody = response.body?.string() ?: "{}"
                    response.close()
                    if (!response.isSuccessful) {
                        if (lastStatus in RETRY_STATUSES) return@withContext null // retry
                        throw RuntimeException("GBIF API error $lastStatus")
                    }
                    responseBody
                }
                if (body == null) {
                    delay(3000L * (1L shl attempt))
                    continue
                }
                return json.parseToJsonElement(body)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Exception) {
                if (attempt < MAX_RETRIES - 1) {
                    delay(3000L * (1L shl attempt))
                    continue
                }
                throw RuntimeException("Network error after $MAX_RETRIES retries: ${e.message}")
            }
        }
        throw RuntimeException("GBIF failed after $MAX_RETRIES retries (last status: $lastStatus)")
    }

    private fun yearRange(yearStart: Int, yearEnd: Int) = "$yearStart,$yearEnd"

    private fun weekOfYear(year: Int?, month: Int?, day: Int?): Int? {
        if (year == null || month == null || day == null) return null
        return try {
            val doy = LocalDate.of(year, month, day).dayOfYear
            ((doy - 1) / 7 + 1).coerceIn(1, 53)
        } catch (_: Exception) { null }
    }

    // --- Interactive endpoints ---

    /** Search GADM administrative regions (countries, states, districts). */
    suspend fun searchAreas(query: String): List<GbifArea> {
        val data = get(
            "geocode/gadm/search",
            mapOf("q" to query, "limit" to "15"),
            intervalMs = INTERACTIVE_INTERVAL_MS
        )
        val results = data.jsonObjectOrNull()?.get("results")?.jsonArrayOrNull() ?: return emptyList()
        return results.mapNotNull { r ->
            val obj = r.jsonObjectOrNull() ?: return@mapNotNull null
            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val level = obj["gadmLevel"]?.jsonPrimitive?.intOrNull ?: 0
            GbifArea(id, name, level)
        }
    }

    /** Autocomplete against the GBIF backbone taxonomy. Returns TaxonResult with id = GBIF key. */
    suspend fun searchTaxa(query: String): List<TaxonResult> {
        val data = get(
            "species/suggest",
            mapOf("q" to query, "limit" to "15"),
            intervalMs = INTERACTIVE_INTERVAL_MS
        )
        val arr = data.jsonArrayOrNull() ?: return emptyList()
        val allowedRanks = setOf(
            "SPECIES", "SUBSPECIES", "GENUS", "FAMILY",
            "ORDER", "CLASS", "PHYLUM", "KINGDOM"
        )
        return arr.mapNotNull { r ->
            val obj = r.jsonObjectOrNull() ?: return@mapNotNull null
            val key = obj["key"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val rank = obj["rank"]?.jsonPrimitive?.contentOrNull ?: ""
            if (rank.uppercase() !in allowedRanks) return@mapNotNull null
            val sci = obj["canonicalName"]?.jsonPrimitive?.contentOrNull
                ?: obj["scientificName"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val display = "$sci [${rank.lowercase()}]"
            TaxonResult(key, display, sci, "", rank.lowercase())
        }
    }

    // --- Data endpoints ---

    /**
     * One page of a speciesKey facet within an area+taxon+year filter.
     * Returns total occurrence count and a list of (speciesKey, occurrenceCount) buckets.
     */
    suspend fun getSpeciesFacet(
        taxonKey: Int?,
        gadmGid: String,
        yearStart: Int,
        yearEnd: Int,
        facetOffset: Int = 0,
        facetLimit: Int = FACET_PAGE
    ): Pair<Int, List<Pair<Int, Int>>> {
        val params = mutableMapOf(
            "gadmGid" to gadmGid,
            "year" to yearRange(yearStart, yearEnd),
            "facet" to "speciesKey",
            "facetLimit" to facetLimit.toString(),
            "facetOffset" to facetOffset.toString(),
            "limit" to "0"
        )
        if (taxonKey != null) params["taxonKey"] = taxonKey.toString()

        val data = get("occurrence/search", params)
        val obj = data.jsonObjectOrNull() ?: return Pair(0, emptyList())
        val total = obj["count"]?.jsonPrimitive?.intOrNull ?: 0
        val counts = obj["facets"]?.jsonArrayOrNull()
            ?.firstOrNull()?.jsonObjectOrNull()
            ?.get("counts")?.jsonArrayOrNull() ?: return Pair(total, emptyList())
        val buckets = counts.mapNotNull { c ->
            val co = c.jsonObjectOrNull() ?: return@mapNotNull null
            val sk = co["name"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
            val n = co["count"]?.jsonPrimitive?.intOrNull ?: 0
            sk to n
        }
        return Pair(total, buckets)
    }

    /** Estimate distinct species count across the given taxa + areas (capped by facet size). */
    suspend fun getSpeciesCountEstimate(
        taxonKeys: List<Int?>,
        gadmGids: List<String>,
        yearStart: Int,
        yearEnd: Int
    ): Int {
        val keys = mutableSetOf<Int>()
        for (tk in taxonKeys) {
            for (gid in gadmGids) {
                val (_, buckets) = getSpeciesFacet(tk, gid, yearStart, yearEnd, 0, FACET_PAGE)
                buckets.forEach { keys.add(it.first) }
            }
        }
        return keys.size
    }

    /**
     * Sample up to [maxRecords] occurrences of one species within an area+year filter,
     * binning eventDate into weeks (1..53). Also captures the species' names + family/order.
     */
    suspend fun sampleOccurrences(
        speciesKey: Int,
        gadmGid: String,
        yearStart: Int,
        yearEnd: Int,
        maxRecords: Int
    ): GbifSpeciesSample {
        val weekly = mutableMapOf<Int, Int>()
        var scientificName = ""
        var commonName = ""
        var family: String? = null
        var order: String? = null

        var offset = 0
        while (offset < maxRecords && offset < 100_000) {
            val pageLimit = minOf(PAGE_SIZE, maxRecords - offset)
            val data = get("occurrence/search", mapOf(
                "speciesKey" to speciesKey.toString(),
                "gadmGid" to gadmGid,
                "year" to yearRange(yearStart, yearEnd),
                "limit" to pageLimit.toString(),
                "offset" to offset.toString()
            ))
            val results = data.jsonObjectOrNull()?.get("results")?.jsonArrayOrNull() ?: break
            if (results.isEmpty()) break
            for (r in results) {
                val obj = r.jsonObjectOrNull() ?: continue
                if (scientificName.isEmpty()) {
                    scientificName = obj["species"]?.jsonPrimitive?.contentOrNull ?: ""
                    commonName = obj["vernacularName"]?.jsonPrimitive?.contentOrNull ?: ""
                    family = obj["family"]?.jsonPrimitive?.contentOrNull
                    order = obj["order"]?.jsonPrimitive?.contentOrNull
                }
                val w = weekOfYear(
                    obj["year"]?.jsonPrimitive?.intOrNull,
                    obj["month"]?.jsonPrimitive?.intOrNull,
                    obj["day"]?.jsonPrimitive?.intOrNull
                ) ?: continue
                weekly[w] = (weekly[w] ?: 0) + 1
            }
            if (results.size < pageLimit) break
            offset += pageLimit
        }
        return GbifSpeciesSample(weekly, scientificName, commonName, family, order)
    }

    // --- JSON helpers (tolerant of nulls / wrong shapes) ---
    private fun JsonElement.jsonObjectOrNull(): JsonObject? =
        try { jsonObject } catch (_: Exception) { null }

    private fun JsonElement.jsonArrayOrNull(): JsonArray? =
        try { jsonArray } catch (_: Exception) { null }
}
