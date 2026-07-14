package com.codylimber.fieldphenology.data.generator

import android.content.Context
import com.codylimber.fieldphenology.data.api.GbifApiClient
import com.codylimber.fieldphenology.data.api.INatApiClient
import com.codylimber.fieldphenology.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Builds a dataset from GBIF occurrence data:
 *  - species list + counts from GBIF's speciesKey facet,
 *  - true weekly phenology by sampling real GBIF occurrences (last [yearsBack] years),
 *  - photos, descriptions, taxonomy and conservation status borrowed from iNaturalist
 *    where the GBIF scientific name resolves to an iNat taxon (falls back to GBIF's own
 *    family/order otherwise).
 *
 * Produces the same Dataset JSON + photos layout as the iNaturalist generator, so the rest
 * of the app is unchanged. The saved Species.taxonId is the resolved iNaturalist id when
 * available (keeping live map/photo features working), else the GBIF speciesKey.
 */
private val datasetJson = Json { encodeDefaults = true }

class GbifDatasetGenerator(
    private val gbif: GbifApiClient,
    private val inat: INatApiClient,
    private val context: Context
) {
    suspend fun generate(
        areaGids: List<String>,
        areaName: String,
        taxonKeys: List<Int?>,
        taxonName: String,
        groupName: String,
        minObs: Int = 1,
        yearsBack: Int = 10,
        sampleSize: Int = 600,
        maxPhotos: Int = 3,
        onProgress: (GenerationProgress) -> Unit
    ): String {
        val slug = "${groupName}-${areaName}".lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val outputDir = File(context.filesDir, "datasets/$slug")
        val photosDir = File(outputDir, "photos")
        photosDir.mkdirs()

        val yearEnd = LocalDate.now().year
        val yearStart = yearEnd - yearsBack + 1

        // Step 1: Species list — page the speciesKey facet across all taxa + areas, summing counts.
        onProgress(GenerationProgress(GenerationPhase.FETCHING_SPECIES, 0, 1, "Fetching species list..."))
        val counts = mutableMapOf<Int, Int>() // speciesKey -> summed occurrence count
        for (taxonKey in taxonKeys) {
            for (gid in areaGids) {
                var offset = 0
                while (true) {
                    val (_, buckets) = gbif.getSpeciesFacet(taxonKey, gid, yearStart, yearEnd, offset)
                    for ((sk, c) in buckets) counts[sk] = (counts[sk] ?: 0) + c
                    onProgress(GenerationProgress(GenerationPhase.FETCHING_SPECIES,
                        counts.size, counts.size, "Found ${counts.size} species so far..."))
                    if (buckets.size < GbifApiClient.FACET_PAGE) break
                    offset += GbifApiClient.FACET_PAGE
                }
            }
        }

        val filtered = counts.filter { it.value >= minObs }
        if (filtered.isEmpty()) throw RuntimeException("No species found matching criteria.")
        val speciesKeys = filtered.keys.sortedByDescending { filtered[it] }
        val nSpecies = speciesKeys.size
        onProgress(GenerationProgress(GenerationPhase.FETCHING_SPECIES, nSpecies, nSpecies, "$nSpecies species found"))

        // Step 2: Sample occurrences per species -> weekly phenology + names/taxonomy.
        val raws = mutableListOf<RawSpecies>()
        val histograms = mutableMapOf<String, Map<Int, Int>>() // keyed by speciesKey string
        val gbifFamily = mutableMapOf<Int, String?>()
        val gbifOrder = mutableMapOf<Int, String?>()

        for ((i, sk) in speciesKeys.withIndex()) {
            onProgress(GenerationProgress(GenerationPhase.FETCHING_HISTOGRAMS, i + 1, nSpecies,
                "Sampling occurrences (${i + 1}/$nSpecies)"))
            val combined = mutableMapOf<Int, Int>()
            var sci = ""
            var common = ""
            var family: String? = null
            var order: String? = null
            for (gid in areaGids) {
                val sample = gbif.sampleOccurrences(sk, gid, yearStart, yearEnd, sampleSize)
                for ((w, c) in sample.weekly) combined[w] = (combined[w] ?: 0) + c
                if (sci.isEmpty() && sample.scientificName.isNotEmpty()) {
                    sci = sample.scientificName
                    common = sample.commonName
                    family = sample.family
                    order = sample.order
                }
            }
            if (sci.isEmpty()) continue // couldn't identify species; skip
            histograms[sk.toString()] = combined
            gbifFamily[sk] = family
            gbifOrder[sk] = order
            raws.add(RawSpecies(
                scientificName = sci,
                commonName = common,
                taxonId = sk,                 // GBIF speciesKey (canonical id resolved later)
                totalObs = filtered[sk] ?: 0
            ))
        }
        if (raws.isEmpty()) throw RuntimeException("No identifiable species found.")

        DataProcessor.computeRarity(raws)
        val weekly = DataProcessor.buildWeeklyMatrix(histograms)
        for (sp in raws) {
            val h = histograms[sp.taxonId.toString()] ?: continue
            val active = h.filter { it.value > 0 }
            if (active.isNotEmpty()) {
                sp.peakWeek = active.maxByOrNull { it.value }!!.key
                sp.firstWeek = active.keys.min()
                sp.lastWeek = active.keys.max()
            }
            sp.periodCount = DataProcessor.detectFlightPeriods(weekly, sp.taxonId.toString())
        }

        // Step 3: Resolve each species to an iNaturalist taxon, then batch-fetch iNat details.
        onProgress(GenerationProgress(GenerationPhase.FETCHING_DETAILS, 0, nSpecies, "Matching to iNaturalist..."))
        val inatIdByKey = mutableMapOf<Int, Int>() // gbif speciesKey -> iNat taxon id
        for ((i, sp) in raws.withIndex()) {
            val inatId = try { inat.resolveTaxonIdByName(sp.scientificName) } catch (_: Exception) { null }
            if (inatId != null) inatIdByKey[sp.taxonId] = inatId
            onProgress(GenerationProgress(GenerationPhase.FETCHING_DETAILS, i + 1, nSpecies,
                "Matching: ${sp.commonName.ifEmpty { sp.scientificName }} (${i + 1}/$nSpecies)"))
        }

        val inatDetails = mutableMapOf<Int, JsonObject>()
        val inatIds = inatIdByKey.values.distinct()
        for (i in inatIds.indices step 30) {
            val batch = inatIds.subList(i, minOf(i + 30, inatIds.size))
            val results = try { inat.getTaxaDetails(batch) } catch (_: Exception) { emptyList() }
            for (t in results) {
                val id = t["id"]?.jsonPrimitive?.intOrNull ?: continue
                inatDetails[id] = t
            }
        }

        // Step 4: Download photos from iNaturalist (where matched).
        val photoMap = mutableMapOf<Int, List<SpeciesPhoto>>() // keyed by gbif speciesKey
        for ((idx, sp) in raws.withIndex()) {
            onProgress(GenerationProgress(GenerationPhase.DOWNLOADING_PHOTOS, idx + 1, nSpecies,
                "Photos: ${sp.commonName.ifEmpty { sp.scientificName }} (${idx + 1}/$nSpecies)"))
            val inatId = inatIdByKey[sp.taxonId]
            val taxonInfo = inatId?.let { inatDetails[it] }
            val photos = extractPhotos(taxonInfo)
            val spPhotos = mutableListOf<SpeciesPhoto>()

            val ccPhotos = photos.filter { photo ->
                val license = photo["license_code"]?.jsonPrimitive?.contentOrNull
                license != null && license.startsWith("cc")
            }
            val allCcPhotos = if (ccPhotos.size < maxPhotos && inatId != null) {
                val obsPhotos = try {
                    inat.getObservationPhotos(inatId, emptyList(), "research", maxPhotos - ccPhotos.size)
                } catch (_: Exception) { emptyList() }
                ccPhotos + obsPhotos
            } else ccPhotos

            for ((pi, photo) in allCcPhotos.take(maxPhotos).withIndex()) {
                var url = photo["medium_url"]?.jsonPrimitive?.contentOrNull
                    ?: photo["url"]?.jsonPrimitive?.contentOrNull ?: continue
                url = url.replace("/square.", "/medium.")
                val filename = "${sp.taxonId}_$pi.jpg"
                val bytes = inat.downloadPhoto(url) ?: continue
                File(photosDir, filename).writeBytes(bytes)
                spPhotos.add(SpeciesPhoto(
                    file = filename,
                    attribution = photo["attribution"]?.jsonPrimitive?.contentOrNull,
                    license = photo["license_code"]?.jsonPrimitive?.contentOrNull
                ))
            }
            photoMap[sp.taxonId] = spPhotos
        }

        // Step 5: Build and save the dataset.
        onProgress(GenerationProgress(GenerationPhase.SAVING, 0, 1, "Saving dataset..."))

        val weeklyByKey = mutableMapOf<String, MutableList<WeeklyEntry>>()
        for (row in weekly) {
            weeklyByKey.getOrPut(row.species) { mutableListOf() }
                .add(WeeklyEntry(row.week, row.n, row.relAbundance))
        }

        val speciesEntries = raws.map { sp ->
            val inatId = inatIdByKey[sp.taxonId]
            val taxonInfo = inatId?.let { inatDetails[it] }

            val family = extractAncestorByRank(taxonInfo, "family") ?: gbifFamily[sp.taxonId]
            val familySci = extractAncestorScientificByRank(taxonInfo, "family") ?: gbifFamily[sp.taxonId]
            val order = extractAncestorByRank(taxonInfo, "order") ?: gbifOrder[sp.taxonId]
            val orderSci = extractAncestorScientificByRank(taxonInfo, "order") ?: gbifOrder[sp.taxonId]

            val conservation = safeJsonObject(taxonInfo?.get("conservation_status"))
            var description = taxonInfo?.get("wikipedia_summary")?.jsonPrimitive?.contentOrNull ?: ""
            description = description.replace(Regex("<[^>]+>"), "").trim()

            // GBIF occurrence records rarely carry a vernacularName, so prefer the iNaturalist
            // preferred_common_name (already fetched for photos/description) when matched.
            val commonName = taxonInfo?.get("preferred_common_name")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() } ?: sp.commonName

            val spWeekly = weeklyByKey[sp.taxonId.toString()] ?: mutableListOf()
            val existingWeeks = spWeekly.map { it.week }.toSet()
            for (w in 1..53) if (w !in existingWeeks) spWeekly.add(WeeklyEntry(w, 0, 0f))
            spWeekly.sortBy { it.week }

            Species(
                taxonId = inatId ?: sp.taxonId,   // prefer iNat id so live features work
                scientificName = sp.scientificName,
                commonName = commonName,
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
                placeName = areaName,
                placeId = 0,                 // no iNaturalist place for GBIF areas
                placeIds = emptyList(),
                group = groupName,
                taxonName = taxonName,
                taxonIds = taxonKeys.filterNotNull(),
                totalObs = raws.sumOf { it.totalObs },
                speciesCount = speciesEntries.size,
                generatedAt = Instant.now().toString(),
                minObs = minObs,
                qualityGrade = "research",
                maxPhotos = maxPhotos,
                source = "gbif",
                yearsBack = yearsBack,
                gbifAreaIds = areaGids,
                gbifSampleSize = sampleSize
            ),
            species = speciesEntries
        )

        File(outputDir, "dataset.json").writeText(datasetJson.encodeToString(dataset))
        onProgress(GenerationProgress(GenerationPhase.SAVING, 1, 1, "Complete! ${speciesEntries.size} species"))
        return outputDir.absolutePath
    }

    // --- iNaturalist taxon-JSON helpers (mirrors DatasetGenerator) ---

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
