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
        months: List<Int> = emptyList(),
        onProgress: (GenerationProgress) -> Unit
    ): String {
        val slug = GeneratorSupport.slug(groupName, areaName)
        val monthFilter = months.filter { it in 1..12 }.distinct().sorted()
            .takeIf { it.size in 1..11 } ?: emptyList()
        return GeneratorSupport.buildAtomically(context, slug) { outputDir ->
            build(outputDir, areaGids, areaName, taxonKeys, taxonName, groupName, minObs,
                yearsBack, sampleSize, maxPhotos, monthFilter, onProgress)
        }
    }

    private suspend fun build(
        outputDir: File,
        areaGids: List<String>,
        areaName: String,
        taxonKeys: List<Int?>,
        taxonName: String,
        groupName: String,
        minObs: Int,
        yearsBack: Int,
        sampleSize: Int,
        maxPhotos: Int,
        months: List<Int>,
        onProgress: (GenerationProgress) -> Unit
    ) {
        val photosDir = File(outputDir, "photos")

        val yearEnd = LocalDate.now().year
        val yearStart = yearEnd - yearsBack + 1

        // Step 1: Species list — page the speciesKey facet across all taxa + areas, summing counts.
        onProgress(GenerationProgress(GenerationPhase.FETCHING_SPECIES, 0, 1, "Fetching species list..."))
        val counts = mutableMapOf<Int, Int>() // speciesKey -> summed occurrence count
        // Month runs are disjoint, so their counts add up; null = whole year.
        val monthRanges: List<IntRange?> = gbif.monthRuns(months).ifEmpty { listOf(null) }
        for (taxonKey in taxonKeys) {
            for (gid in areaGids) for (monthRange in monthRanges) {
                var offset = 0
                while (true) {
                    val (_, buckets) = gbif.getSpeciesFacet(
                        taxonKey, gid, yearStart, yearEnd, offset, monthRange = monthRange
                    )
                    for ((sk, c) in buckets) counts[sk] = (counts[sk] ?: 0) + c
                    onProgress(GenerationProgress(GenerationPhase.FETCHING_SPECIES,
                        counts.size, counts.size, "Found ${counts.size} species so far..."))
                    if (buckets.size < GbifApiClient.FACET_PAGE) break
                    offset += GbifApiClient.FACET_PAGE
                }
            }
        }

        val filtered = counts.filter { it.value >= minObs }
        if (filtered.isEmpty()) {
            throw RuntimeException(
                if (months.isEmpty()) "No species found matching criteria."
                else "No species recorded in the selected months match these criteria."
            )
        }
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
            DataProcessor.applyHistogram(sp, h)
        }

        // Step 3: Resolve each species to an iNaturalist taxon, then batch-fetch iNat details.
        onProgress(GenerationProgress(GenerationPhase.FETCHING_DETAILS, 0, nSpecies, "Matching to iNaturalist..."))
        val inatIdByKey = mutableMapOf<Int, Int>() // gbif speciesKey -> iNat taxon id
        for ((i, sp) in raws.withIndex()) {
            val inatId = try { inat.resolveTaxonIdByName(sp.scientificName) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
            if (inatId != null) inatIdByKey[sp.taxonId] = inatId
            onProgress(GenerationProgress(GenerationPhase.FETCHING_DETAILS, i + 1, nSpecies,
                "Matching: ${sp.commonName.ifEmpty { sp.scientificName }} (${i + 1}/$nSpecies)"))
        }

        val inatDetails = mutableMapOf<Int, JsonObject>()
        val inatIds = inatIdByKey.values.distinct()
        for (i in inatIds.indices step 30) {
            val batch = inatIds.subList(i, minOf(i + 30, inatIds.size))
            val results = try { inat.getTaxaDetails(batch, includeProfile = true) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { emptyList() }
            for (t in results) {
                val id = t["id"]?.jsonPrimitive?.intOrNull ?: continue
                inatDetails[id] = t
            }
        }

        // Step 4: Download photos from iNaturalist (where matched), several at a time.
        val photoLists = GeneratorSupport.forEachConcurrently(
            raws,
            onEach = { done, total, sp ->
                onProgress(GenerationProgress(GenerationPhase.DOWNLOADING_PHOTOS, done, total,
                    "Photos: ${sp.commonName.ifEmpty { sp.scientificName }} ($done/$total)"))
            }
        ) { sp -> downloadPhotos(sp, inatIdByKey[sp.taxonId], inatDetails, maxPhotos, photosDir) }
        val photoMap = raws.indices.associate { raws[it].taxonId to photoLists[it] }

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
                gbifSampleSize = sampleSize,
                months = months
            ),
            species = speciesEntries
        )

        File(outputDir, "dataset.json").writeText(datasetJson.encodeToString(dataset))
        onProgress(GenerationProgress(GenerationPhase.SAVING, 1, 1, "Complete! ${speciesEntries.size} species"))
    }

    private suspend fun downloadPhotos(
        sp: RawSpecies,
        inatId: Int?,
        inatDetails: Map<Int, JsonObject>,
        maxPhotos: Int,
        photosDir: File
    ): List<SpeciesPhoto> {
        if (maxPhotos <= 0) return emptyList()
        val taxonInfo = inatId?.let { inatDetails[it] }
        val ccPhotos = extractPhotos(taxonInfo).filter { photo ->
            val license = photo["license_code"]?.jsonPrimitive?.contentOrNull
            license != null && license.startsWith("cc")
        }
        val allCcPhotos = if (ccPhotos.size < maxPhotos && inatId != null) {
            val obsPhotos = try {
                inat.getObservationPhotos(inatId, emptyList(), "research", maxPhotos - ccPhotos.size)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) { emptyList() }
            ccPhotos + obsPhotos
        } else ccPhotos

        val spPhotos = mutableListOf<SpeciesPhoto>()
        for ((pi, photo) in allCcPhotos.take(maxPhotos).withIndex()) {
            val url = (photo["medium_url"]?.jsonPrimitive?.contentOrNull
                ?: photo["url"]?.jsonPrimitive?.contentOrNull ?: continue)
                .replace("/square.", "/medium.")
            val filename = "${sp.taxonId}_$pi.jpg"
            val bytes = inat.downloadPhoto(url) ?: continue
            File(photosDir, filename).writeBytes(bytes)
            spPhotos.add(SpeciesPhoto(
                file = filename,
                attribution = photo["attribution"]?.jsonPrimitive?.contentOrNull,
                license = photo["license_code"]?.jsonPrimitive?.contentOrNull
            ))
        }
        return spPhotos
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
