package com.codylimber.fieldphenology.data.generator

import android.content.Context
import com.codylimber.fieldphenology.data.api.INatApiClient
import com.codylimber.fieldphenology.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant

enum class GenerationPhase {
    FETCHING_SPECIES, FETCHING_HISTOGRAMS, FETCHING_DETAILS,
    DOWNLOADING_PHOTOS, SAVING
}

data class GenerationProgress(
    val phase: GenerationPhase,
    val current: Int,
    val total: Int,
    val message: String
)

private val datasetJson = Json { encodeDefaults = true }

class DatasetGenerator(
    private val apiClient: INatApiClient,
    private val context: Context
) {
    suspend fun generate(
        placeIds: List<Int>,
        placeName: String,
        taxonIds: List<Int?>,
        taxonName: String,
        groupName: String,
        minObs: Int = 1,
        qualityGrade: String = "research",
        maxPhotos: Int = 3,
        months: List<Int> = emptyList(),
        onProgress: (GenerationProgress) -> Unit
    ): String {
        val slug = GeneratorSupport.slug(groupName, placeName)
        val monthFilter = months.filter { it in 1..12 }.distinct().sorted()
            .takeIf { it.size in 1..11 } ?: emptyList()
        return GeneratorSupport.buildAtomically(context, slug) { outputDir ->
            build(
                outputDir, placeIds, placeName, taxonIds, taxonName, groupName,
                minObs, qualityGrade, maxPhotos, monthFilter, onProgress
            )
        }
    }

    private suspend fun build(
        outputDir: File,
        placeIds: List<Int>,
        placeName: String,
        taxonIds: List<Int?>,
        taxonName: String,
        groupName: String,
        minObs: Int,
        qualityGrade: String,
        maxPhotos: Int,
        months: List<Int>,
        onProgress: (GenerationProgress) -> Unit
    ) {
        val photosDir = File(outputDir, "photos")
        val perPage = INatApiClient.MAX_SPECIES_COUNTS_PER_PAGE

        // Step 1: species list across all taxon+place combos (optionally limited to months).
        onProgress(GenerationProgress(GenerationPhase.FETCHING_SPECIES, 0, 1, "Fetching species list..."))
        val speciesByTaxonId = mutableMapOf<Int, JsonObject>()
        // place -> taxon id -> count. Overlapping taxa (e.g. Aves + Passeriformes) return the
        // same species twice for one place, so take the max within a place and sum places.
        val countsByPlace = mutableMapOf<Int, MutableMap<Int, Int>>()
        // Full-year species totals per (taxon, place): used to price the weekly strategy below.
        val fullYearSpeciesTotals = mutableListOf<Int>()

        for (taxonId in taxonIds) {
            for (placeId in placeIds) {
                var page = 1
                while (true) {
                    val (total, results) = apiClient.getSpeciesCounts(
                        taxonId, placeId, qualityGrade = qualityGrade, page = page,
                        perPage = perPage, months = months
                    )
                    if (page == 1 && months.isEmpty()) fullYearSpeciesTotals.add(total)
                    for (r in results) {
                        val tid = r["taxon"]?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull ?: continue
                        val count = r["count"]?.jsonPrimitive?.intOrNull ?: 0
                        if (tid !in speciesByTaxonId) speciesByTaxonId[tid] = r
                        val placeCounts = countsByPlace.getOrPut(placeId) { mutableMapOf() }
                        placeCounts[tid] = maxOf(placeCounts[tid] ?: 0, count)
                    }
                    onProgress(GenerationProgress(GenerationPhase.FETCHING_SPECIES,
                        speciesByTaxonId.size, speciesByTaxonId.size,
                        "Found ${speciesByTaxonId.size} species so far..."))
                    if (results.size < perPage) break
                    page++
                }
            }
        }

        val obsCounts = mutableMapOf<Int, Int>()
        for (placeCounts in countsByPlace.values) {
            for ((tid, c) in placeCounts) obsCounts[tid] = (obsCounts[tid] ?: 0) + c
        }
        val filteredIds = obsCounts.filter { it.value >= minObs }.keys
        val filteredRaw = filteredIds.mapNotNull { tid ->
            speciesByTaxonId[tid]?.let { obj ->
                JsonObject(obj.toMutableMap().apply { put("count", JsonPrimitive(obsCounts[tid] ?: 0)) })
            }
        }

        val speciesList = DataProcessor.buildSpeciesList(filteredRaw)
        if (speciesList.isEmpty()) {
            throw RuntimeException(
                if (months.isEmpty()) "No species found matching criteria."
                else "No species recorded in the selected months match these criteria."
            )
        }

        val nSpecies = speciesList.size
        onProgress(GenerationProgress(GenerationPhase.FETCHING_SPECIES, nSpecies, nSpecies,
            "$nSpecies species found"))

        // Step 2: full-year weekly histograms.
        val histograms = fetchHistograms(
            speciesList, placeIds, taxonIds, qualityGrade, months, fullYearSpeciesTotals, onProgress
        )

        if (months.isNotEmpty()) {
            // The list counts were for the chosen months only; totals and rarity describe the
            // species across the whole year, consistent with the full-year charts.
            for (sp in speciesList) {
                val yearTotal = histograms[sp.scientificName]?.values?.sum() ?: 0
                sp.totalObs = maxOf(yearTotal, sp.totalObs)
            }
            DataProcessor.computeRarity(speciesList)
        }

        val weekly = DataProcessor.buildWeeklyMatrix(histograms)
        for (sp in speciesList) {
            val h = histograms[sp.scientificName] ?: continue
            DataProcessor.applyHistogram(sp, h)
        }

        // Step 3: taxa details (photos, ancestry, description, conservation status).
        onProgress(GenerationProgress(GenerationPhase.FETCHING_DETAILS, 0, nSpecies, "Fetching species details..."))
        val taxaDetails = mutableMapOf<Int, JsonObject>()
        val allTaxonIds = speciesList.map { it.taxonId }
        for (i in allTaxonIds.indices step 30) {
            val batch = allTaxonIds.subList(i, minOf(i + 30, allTaxonIds.size))
            val results = apiClient.getTaxaDetails(batch, includeProfile = true)
            for (t in results) {
                val id = t["id"]?.jsonPrimitive?.intOrNull ?: continue
                taxaDetails[id] = t
            }
            onProgress(GenerationProgress(GenerationPhase.FETCHING_DETAILS,
                minOf(i + 30, allTaxonIds.size), nSpecies,
                "Details: ${minOf(i + 30, allTaxonIds.size)}/$nSpecies"))
        }

        // Step 4: photos. Downloads come from the static photo host and overlap; any API
        // fallback calls still pass through the client's rate limiter.
        val photoLists = GeneratorSupport.forEachConcurrently(
            speciesList,
            onEach = { done, total, sp ->
                onProgress(GenerationProgress(GenerationPhase.DOWNLOADING_PHOTOS, done, total,
                    "Photos: ${sp.commonName.ifEmpty { sp.scientificName }} ($done/$total)"))
            }
        ) { sp -> downloadSpeciesPhotos(sp, taxaDetails[sp.taxonId], placeIds, qualityGrade, maxPhotos, photosDir) }
        val photoMap = speciesList.indices.associate { speciesList[it].taxonId to photoLists[it] }

        // Step 5: build and save.
        onProgress(GenerationProgress(GenerationPhase.SAVING, 0, 1, "Saving dataset..."))

        val weeklyBySpecies = mutableMapOf<String, MutableList<WeeklyEntry>>()
        for (row in weekly) {
            weeklyBySpecies.getOrPut(row.species) { mutableListOf() }
                .add(WeeklyEntry(row.week, row.n, row.relAbundance))
        }

        val speciesEntries = speciesList.map { sp ->
            val taxonInfo = taxaDetails[sp.taxonId]
            val conservation = safeJsonObject(taxonInfo?.get("conservation_status"))
            var description = taxonInfo?.get("wikipedia_summary")?.jsonPrimitive?.contentOrNull ?: ""
            description = description.replace(Regex("<[^>]+>"), "").trim()

            val spWeekly = weeklyBySpecies[sp.scientificName] ?: mutableListOf()
            val existingWeeks = spWeekly.map { it.week }.toSet()
            for (w in 1..53) {
                if (w !in existingWeeks) spWeekly.add(WeeklyEntry(w, 0, 0f))
            }
            spWeekly.sortBy { it.week }

            Species(
                taxonId = sp.taxonId,
                scientificName = sp.scientificName,
                commonName = sp.commonName,
                totalObs = sp.totalObs,
                rarity = sp.rarity,
                peakWeek = sp.peakWeek,
                firstWeek = sp.firstWeek,
                lastWeek = sp.lastWeek,
                periodCount = sp.periodCount,
                description = description,
                conservationStatus = conservation?.get("status")?.jsonPrimitive?.contentOrNull,
                conservationStatusName = conservation?.get("status_name")?.jsonPrimitive?.contentOrNull,
                family = extractAncestorByRank(taxonInfo, "family"),
                familyScientific = extractAncestorScientificByRank(taxonInfo, "family"),
                order = extractAncestorByRank(taxonInfo, "order"),
                orderScientific = extractAncestorScientificByRank(taxonInfo, "order"),
                photos = photoMap[sp.taxonId] ?: emptyList(),
                weekly = spWeekly
            )
        }

        val dataset = Dataset(
            metadata = DatasetMetadata(
                placeName = placeName,
                placeId = placeIds.firstOrNull() ?: 0,
                placeIds = placeIds,
                group = groupName,
                taxonName = taxonName,
                taxonIds = taxonIds.filterNotNull(),
                totalObs = speciesList.sumOf { it.totalObs },
                speciesCount = speciesList.size,
                generatedAt = Instant.now().toString(),
                minObs = minObs,
                qualityGrade = qualityGrade,
                maxPhotos = maxPhotos,
                months = months
            ),
            species = speciesEntries
        )

        File(outputDir, "dataset.json").writeText(datasetJson.encodeToString(dataset))

        onProgress(GenerationProgress(GenerationPhase.SAVING, 1, 1,
            "Complete! ${speciesEntries.size} species"))
    }

    /**
     * Weekly counts for every species, keyed by scientific name. Two strategies:
     *  - per species: one histogram call per species per place (the original approach);
     *  - per week: one species_counts call per week (x pages) per taxon+place, which returns
     *    every species' count for that week at once.
     * We pick whichever needs fewer requests: per-week wins for big packs (53 calls instead of
     * hundreds), per-species wins for small or month-filtered ones.
     */
    private suspend fun fetchHistograms(
        speciesList: List<RawSpecies>,
        placeIds: List<Int>,
        taxonIds: List<Int?>,
        qualityGrade: String,
        months: List<Int>,
        knownFullYearTotals: List<Int>,
        onProgress: (GenerationProgress) -> Unit
    ): Map<String, Map<Int, Int>> {
        val nSpecies = speciesList.size
        val perPage = INatApiClient.MAX_SPECIES_COUNTS_PER_PAGE
        val perSpeciesCost = nSpecies * placeIds.size

        // A week typically holds well under the year's species; 0.6 keeps the estimate
        // conservative so we only switch when the weekly path clearly wins.
        val fullYearTotals = if (knownFullYearTotals.isNotEmpty()) knownFullYearTotals else {
            // Month-filtered: we don't know the full-year list size; skip pricing unless it
            // could possibly win (needs at least 53 * combos requests).
            if (perSpeciesCost <= 53 * taxonIds.size * placeIds.size) emptyList()
            else taxonIds.flatMap { t ->
                placeIds.map { p ->
                    apiClient.getSpeciesCounts(t, p, qualityGrade = qualityGrade, perPage = 1).first
                }
            }
        }
        val weeklyCost = if (fullYearTotals.isEmpty()) Int.MAX_VALUE else fullYearTotals.sumOf { total ->
            53 * maxOf(1, Math.ceil(total * 0.6 / perPage).toInt())
        }

        if (weeklyCost < perSpeciesCost) {
            return fetchHistogramsByWeek(speciesList, placeIds, taxonIds, qualityGrade, onProgress)
        }

        val histograms = mutableMapOf<String, Map<Int, Int>>()
        for ((i, sp) in speciesList.withIndex()) {
            onProgress(GenerationProgress(GenerationPhase.FETCHING_HISTOGRAMS, i + 1, nSpecies,
                "Histogram: ${sp.commonName.ifEmpty { sp.scientificName }} (${i + 1}/$nSpecies)"))
            val combined = mutableMapOf<Int, Int>()
            for (placeId in placeIds) {
                val h = apiClient.getHistogram(sp.taxonId, placeId, qualityGrade = qualityGrade)
                for ((week, count) in h) combined[week] = (combined[week] ?: 0) + count
            }
            histograms[sp.scientificName] = combined
        }
        return histograms
    }

    private suspend fun fetchHistogramsByWeek(
        speciesList: List<RawSpecies>,
        placeIds: List<Int>,
        taxonIds: List<Int?>,
        qualityGrade: String,
        onProgress: (GenerationProgress) -> Unit
    ): Map<String, Map<Int, Int>> {
        val perPage = INatApiClient.MAX_SPECIES_COUNTS_PER_PAGE
        val nameById = speciesList.associate { it.taxonId to it.scientificName }
        val combined = mutableMapOf<String, MutableMap<Int, Int>>()
        val steps = 53 * placeIds.size
        var step = 0
        for (placeId in placeIds) {
            for (week in 1..53) {
                step++
                onProgress(GenerationProgress(GenerationPhase.FETCHING_HISTOGRAMS, step, steps,
                    "Weekly counts: week $week" +
                        (if (placeIds.size > 1) " (place ${placeIds.indexOf(placeId) + 1}/${placeIds.size})" else "")))
                // Overlapping taxa report the same species twice; keep the max per place+week.
                val weekCounts = mutableMapOf<Int, Int>()
                for (taxonId in taxonIds) {
                    var page = 1
                    while (true) {
                        val (_, results) = apiClient.getSpeciesCounts(
                            taxonId, placeId, qualityGrade = qualityGrade, page = page,
                            perPage = perPage, week = week
                        )
                        for (r in results) {
                            val tid = r["taxon"]?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull ?: continue
                            if (tid !in nameById) continue
                            val c = r["count"]?.jsonPrimitive?.intOrNull ?: 0
                            weekCounts[tid] = maxOf(weekCounts[tid] ?: 0, c)
                        }
                        if (results.size < perPage) break
                        page++
                    }
                }
                for ((tid, c) in weekCounts) {
                    val m = combined.getOrPut(nameById.getValue(tid)) { mutableMapOf() }
                    m[week] = (m[week] ?: 0) + c
                }
            }
        }
        return speciesList.associate { it.scientificName to (combined[it.scientificName] ?: emptyMap()) }
    }

    private suspend fun downloadSpeciesPhotos(
        sp: RawSpecies,
        taxonInfo: JsonObject?,
        placeIds: List<Int>,
        qualityGrade: String,
        maxPhotos: Int,
        photosDir: File
    ): List<SpeciesPhoto> {
        if (maxPhotos <= 0) return emptyList()
        // Creative Commons licensed photos only.
        val ccPhotos = extractPhotos(taxonInfo).filter { photo ->
            val license = photo["license_code"]?.jsonPrimitive?.contentOrNull
            license != null && license.startsWith("cc")
        }
        // Fall back to observation photos if taxon photos are insufficient.
        val allCcPhotos = if (ccPhotos.size < maxPhotos) {
            val obsPhotos = try {
                apiClient.getObservationPhotos(sp.taxonId, placeIds, qualityGrade, maxPhotos - ccPhotos.size)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) { emptyList() }
            ccPhotos + obsPhotos
        } else ccPhotos

        val spPhotos = mutableListOf<SpeciesPhoto>()
        for ((pi, photo) in allCcPhotos.take(maxPhotos).withIndex()) {
            val url = (photo["medium_url"]?.jsonPrimitive?.contentOrNull
                ?: photo["url"]?.jsonPrimitive?.contentOrNull
                ?: continue).replace("/square.", "/medium.")
            val filename = "${sp.taxonId}_$pi.jpg"
            val bytes = apiClient.downloadPhoto(url) ?: continue
            File(photosDir, filename).writeBytes(bytes)
            spPhotos.add(SpeciesPhoto(
                file = filename,
                attribution = photo["attribution"]?.jsonPrimitive?.contentOrNull,
                license = photo["license_code"]?.jsonPrimitive?.contentOrNull
            ))
        }
        return spPhotos
    }

    private fun safeJsonObject(element: JsonElement?): JsonObject? {
        if (element == null || element is JsonNull) return null
        return try { element.jsonObject } catch (_: Exception) { null }
    }

    private fun extractPhotos(taxonInfo: JsonObject?): List<JsonObject> {
        if (taxonInfo == null) return emptyList()
        val taxonPhotos = taxonInfo["taxon_photos"]
        if (taxonPhotos != null && taxonPhotos !is JsonNull && taxonPhotos is JsonArray) {
            return taxonPhotos.mapNotNull { element ->
                if (element is JsonNull) return@mapNotNull null
                val obj = try { element.jsonObject } catch (_: Exception) { return@mapNotNull null }
                val photo = obj["photo"]
                if (photo != null && photo !is JsonNull) {
                    try { photo.jsonObject } catch (_: Exception) { null }
                } else null
            }
        }
        val defaultPhoto = taxonInfo["default_photo"]
        if (defaultPhoto != null && defaultPhoto !is JsonNull) {
            return try { listOf(defaultPhoto.jsonObject) } catch (_: Exception) { emptyList() }
        }
        return emptyList()
    }

    private fun extractAncestorByRank(taxonInfo: JsonObject?, rank: String): String? {
        val ancestors = taxonInfo?.get("ancestors")
        if (ancestors == null || ancestors is JsonNull) return null
        val array = try { ancestors.jsonArray } catch (_: Exception) { return null }
        for (a in array) {
            if (a is JsonNull) continue
            val obj = try { a.jsonObject } catch (_: Exception) { continue }
            if (obj["rank"]?.jsonPrimitive?.contentOrNull == rank) {
                return obj["preferred_common_name"]?.jsonPrimitive?.contentOrNull
                    ?: obj["name"]?.jsonPrimitive?.contentOrNull
            }
        }
        return null
    }

    private fun extractAncestorScientificByRank(taxonInfo: JsonObject?, rank: String): String? {
        val ancestors = taxonInfo?.get("ancestors")
        if (ancestors == null || ancestors is JsonNull) return null
        val array = try { ancestors.jsonArray } catch (_: Exception) { return null }
        for (a in array) {
            if (a is JsonNull) continue
            val obj = try { a.jsonObject } catch (_: Exception) { continue }
            if (obj["rank"]?.jsonPrimitive?.contentOrNull == rank) {
                return obj["name"]?.jsonPrimitive?.contentOrNull
            }
        }
        return null
    }
}
