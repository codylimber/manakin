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
        onProgress: (GenerationProgress) -> Unit
    ): String {
        val slug = "${groupName}-${placeName}".lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val outputDir = File(context.filesDir, "datasets/$slug")
        val photosDir = File(outputDir, "photos")
        photosDir.mkdirs()

        // Step 1: Fetch species list across all taxon+place combos
        onProgress(GenerationProgress(GenerationPhase.FETCHING_SPECIES, 0, 1, "Fetching species list..."))
        // Map taxonId -> JsonObject, merge by taxon_id summing counts
        val speciesByTaxonId = mutableMapOf<Int, JsonObject>()
        val obsCounts = mutableMapOf<Int, Int>()

        for (taxonId in taxonIds) {
            for (placeId in placeIds) {
                var page = 1
                while (true) {
                    val (total, results) = apiClient.getSpeciesCounts(taxonId, placeId, qualityGrade = qualityGrade, page = page)
                    for (r in results) {
                        val tid = r["taxon"]?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull ?: continue
                        val count = r["count"]?.jsonPrimitive?.intOrNull ?: 0
                        if (tid !in speciesByTaxonId) {
                            speciesByTaxonId[tid] = r
                            obsCounts[tid] = count
                        } else {
                            obsCounts[tid] = (obsCounts[tid] ?: 0) + count
                        }
                    }
                    onProgress(GenerationProgress(GenerationPhase.FETCHING_SPECIES,
                        speciesByTaxonId.size, speciesByTaxonId.size,
                        "Found ${speciesByTaxonId.size} species so far..."))
                    if (results.size < 200 || results.isEmpty()) break
                    page++
                }
            }
        }

        // Filter by min obs (using merged counts)
        val filteredIds = obsCounts.filter { it.value >= minObs }.keys
        val filteredRaw = filteredIds.mapNotNull { tid ->
            speciesByTaxonId[tid]?.let { obj ->
                // Rebuild with merged count
                JsonObject(obj.toMutableMap().apply {
                    put("count", JsonPrimitive(obsCounts[tid] ?: 0))
                })
            }
        }

        val speciesList = DataProcessor.buildSpeciesList(filteredRaw)
        if (speciesList.isEmpty()) throw RuntimeException("No species found matching criteria.")

        val nSpecies = speciesList.size
        onProgress(GenerationProgress(GenerationPhase.FETCHING_SPECIES, nSpecies, nSpecies,
            "$nSpecies species found"))

        // Step 2: Fetch histograms — sum across all places
        val histograms = mutableMapOf<String, Map<Int, Int>>()
        for ((i, sp) in speciesList.withIndex()) {
            onProgress(GenerationProgress(GenerationPhase.FETCHING_HISTOGRAMS, i + 1, nSpecies,
                "Histogram: ${sp.commonName.ifEmpty { sp.scientificName }} (${i + 1}/$nSpecies)"))

            val combined = mutableMapOf<Int, Int>()
            for (placeId in placeIds) {
                val h = apiClient.getHistogram(sp.taxonId, placeId, qualityGrade = qualityGrade)
                for ((week, count) in h) {
                    combined[week] = (combined[week] ?: 0) + count
                }
            }
            histograms[sp.scientificName] = combined
        }

        val weekly = DataProcessor.buildWeeklyMatrix(histograms)

        // Update peak/first/last week
        for (sp in speciesList) {
            val h = histograms[sp.scientificName] ?: continue
            val active = h.filter { it.value > 0 }
            if (active.isNotEmpty()) {
                sp.peakWeek = active.maxByOrNull { it.value }!!.key
                sp.firstWeek = active.keys.min()
                sp.lastWeek = active.keys.max()
            }
            sp.periodCount = DataProcessor.detectFlightPeriods(weekly, sp.scientificName)
        }

        // Step 3: Fetch taxa details
        onProgress(GenerationProgress(GenerationPhase.FETCHING_DETAILS, 0, nSpecies, "Fetching species details..."))
        val taxaDetails = mutableMapOf<Int, JsonObject>()
        val allTaxonIds = speciesList.map { it.taxonId }
        for (i in allTaxonIds.indices step 30) {
            val batch = allTaxonIds.subList(i, minOf(i + 30, allTaxonIds.size))
            val results = apiClient.getTaxaDetails(batch)
            for (t in results) {
                val id = t["id"]?.jsonPrimitive?.intOrNull ?: continue
                taxaDetails[id] = t
            }
            onProgress(GenerationProgress(GenerationPhase.FETCHING_DETAILS,
                minOf(i + 30, allTaxonIds.size), nSpecies,
                "Details: ${minOf(i + 30, allTaxonIds.size)}/$nSpecies"))
        }

        // Step 4: Download photos
        val photoMap = mutableMapOf<Int, List<SpeciesPhoto>>()
        for ((idx, sp) in speciesList.withIndex()) {
            onProgress(GenerationProgress(GenerationPhase.DOWNLOADING_PHOTOS, idx + 1, nSpecies,
                "Photos: ${sp.commonName.ifEmpty { sp.scientificName }} (${idx + 1}/$nSpecies)"))

            val taxonInfo = taxaDetails[sp.taxonId]
            val photos = extractPhotos(taxonInfo)
            val spPhotos = mutableListOf<SpeciesPhoto>()

            // Filter to Creative Commons licensed photos only
            val ccPhotos = photos.filter { photo ->
                val license = photo["license_code"]?.jsonPrimitive?.contentOrNull
                license != null && license.startsWith("cc")
            }

            // Fallback to observation photos if taxon photos are insufficient
            val allCcPhotos = if (ccPhotos.size < maxPhotos) {
                val obsPhotos = try {
                    apiClient.getObservationPhotos(sp.taxonId, placeIds, qualityGrade, maxPhotos - ccPhotos.size)
                } catch (_: Exception) { emptyList() }
                ccPhotos + obsPhotos
            } else {
                ccPhotos
            }

            for ((pi, photo) in allCcPhotos.take(maxPhotos).withIndex()) {
                var url = photo["medium_url"]?.jsonPrimitive?.contentOrNull
                    ?: photo["url"]?.jsonPrimitive?.contentOrNull
                    ?: continue
                url = url.replace("/square.", "/medium.")

                val filename = "${sp.taxonId}_$pi.jpg"
                val bytes = apiClient.downloadPhoto(url) ?: continue
                File(photosDir, filename).writeBytes(bytes)

                spPhotos.add(SpeciesPhoto(
                    file = filename,
                    attribution = photo["attribution"]?.jsonPrimitive?.contentOrNull,
                    license = photo["license_code"]?.jsonPrimitive?.contentOrNull
                ))
            }
            photoMap[sp.taxonId] = spPhotos
        }

        // Step 5: Build and save dataset
        onProgress(GenerationProgress(GenerationPhase.SAVING, 0, 1, "Saving dataset..."))

        val weeklyBySpecies = mutableMapOf<String, MutableList<WeeklyEntry>>()
        for (row in weekly) {
            weeklyBySpecies.getOrPut(row.species) { mutableListOf() }
                .add(WeeklyEntry(row.week, row.n, row.relAbundance))
        }

        val speciesEntries = speciesList.map { sp ->
            val taxonInfo = taxaDetails[sp.taxonId]
            val family = extractAncestorByRank(taxonInfo, "family")
            val familySci = extractAncestorScientificByRank(taxonInfo, "family")
            val order = extractAncestorByRank(taxonInfo, "order")
            val orderSci = extractAncestorScientificByRank(taxonInfo, "order")

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
                family = family,
                familyScientific = familySci,
                order = order,
                orderScientific = orderSci,
                photos = photoMap[sp.taxonId] ?: emptyList(),
                weekly = spWeekly
            )
        }

        val dataset = Dataset(
            metadata = DatasetMetadata(
                placeName = placeName,
                placeId = placeIds.first(),
                placeIds = placeIds,
                group = groupName,
                taxonName = taxonName,
                taxonIds = taxonIds.filterNotNull(),
                totalObs = speciesList.sumOf { it.totalObs },
                speciesCount = speciesList.size,
                generatedAt = Instant.now().toString(),
                minObs = minObs,
                qualityGrade = qualityGrade,
                maxPhotos = maxPhotos
            ),
            species = speciesEntries
        )

        val jsonStr = Json { encodeDefaults = true }.encodeToString(dataset)
        File(outputDir, "dataset.json").writeText(jsonStr)

        onProgress(GenerationProgress(GenerationPhase.SAVING, 1, 1,
            "Complete! ${speciesEntries.size} species"))

        return outputDir.absolutePath
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
