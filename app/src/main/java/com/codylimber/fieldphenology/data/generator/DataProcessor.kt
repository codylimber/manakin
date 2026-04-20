package com.codylimber.fieldphenology.data.generator

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class RawSpecies(
    val scientificName: String,
    val commonName: String,
    val taxonId: Int,
    var totalObs: Int,
    var rarity: String = "",
    var peakWeek: Int = 0,
    var firstWeek: Int = 0,
    var lastWeek: Int = 0,
    var periodCount: Int = 1
)

data class WeeklyRow(
    val species: String,
    val week: Int,
    val n: Int,
    val relAbundance: Float
)

object DataProcessor {

    fun buildSpeciesList(rawCounts: List<JsonObject>): List<RawSpecies> {
        val species = rawCounts.mapNotNull { item ->
            val taxon = item["taxon"]?.jsonObject ?: return@mapNotNull null
            val id = taxon["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            RawSpecies(
                scientificName = taxon["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown",
                commonName = taxon["preferred_common_name"]?.jsonPrimitive?.contentOrNull ?: "",
                taxonId = id,
                totalObs = item["count"]?.jsonPrimitive?.intOrNull ?: 0
            )
        }
        computeRarity(species)
        return species
    }

    fun computeRarity(species: List<RawSpecies>) {
        if (species.size <= 3) {
            species.forEach { it.rarity = "Uncommon" }
            return
        }
        val counts = species.map { it.totalObs }.sorted()
        val n = counts.size
        val q25 = counts[n / 4]
        val q75 = counts[3 * n / 4]
        for (sp in species) {
            sp.rarity = when {
                sp.totalObs >= q75 -> "Common"
                sp.totalObs >= q25 -> "Uncommon"
                else -> "Rare"
            }
        }
    }

    fun buildWeeklyMatrix(histograms: Map<String, Map<Int, Int>>): List<WeeklyRow> {
        val rows = mutableListOf<WeeklyRow>()
        for ((speciesName, weekCounts) in histograms) {
            val maxCount = weekCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
            for ((week, count) in weekCounts) {
                if (week in 1..53) {
                    rows.add(WeeklyRow(
                        species = speciesName,
                        week = week,
                        n = count,
                        relAbundance = (count.toFloat() / maxCount * 10000).toInt() / 10000f
                    ))
                }
            }
        }
        return rows
    }

    fun detectFlightPeriods(weeklyData: List<WeeklyRow>, speciesName: String, gapWeeks: Int = 3): Int {
        val activeWeeks = weeklyData
            .filter { it.species == speciesName && it.n > 0 }
            .map { it.week }
            .sorted()

        if (activeWeeks.isEmpty()) return 0

        var periods = 1
        for (i in 1 until activeWeeks.size) {
            if (activeWeeks[i] - activeWeeks[i - 1] > gapWeeks) {
                periods++
            }
        }
        return periods
    }
}
