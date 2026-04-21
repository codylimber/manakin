package com.codylimber.fieldphenology.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Dataset(
    val metadata: DatasetMetadata,
    val species: List<Species>
)

@Serializable
data class DatasetMetadata(
    val placeName: String,
    val placeId: Int,
    val placeIds: List<Int> = emptyList(),
    val group: String,
    val taxonName: String,
    val taxonIds: List<Int> = emptyList(),
    val totalObs: Int,
    val speciesCount: Int,
    val generatedAt: String,
    val minObs: Int = 1,
    val qualityGrade: String = "research",
    val maxPhotos: Int = 3
)

@Serializable
data class Species(
    val taxonId: Int,
    val scientificName: String,
    val commonName: String = "",
    val totalObs: Int,
    val rarity: String,
    val peakWeek: Int,
    val firstWeek: Int,
    val lastWeek: Int,
    val periodCount: Int,
    val description: String = "",
    val conservationStatus: String? = null,
    val conservationStatusName: String? = null,
    val family: String? = null,
    val familyScientific: String? = null,
    val order: String? = null,
    val orderScientific: String? = null,
    val photos: List<SpeciesPhoto> = emptyList(),
    val weekly: List<WeeklyEntry> = emptyList()
)

@Serializable
data class SpeciesPhoto(
    val file: String,
    val attribution: String? = null,
    val license: String? = null
)

@Serializable
data class WeeklyEntry(
    val week: Int,
    val n: Int,
    val relAbundance: Float
)

enum class SpeciesStatus {
    PEAK, ACTIVE, EARLY, LATE, INACTIVE;

    companion object {
        fun classify(species: Species, currentWeek: Int): SpeciesStatus {
            val entry = species.weekly.find { it.week == currentWeek }
            if (entry == null || entry.n == 0) return INACTIVE
            return when {
                entry.relAbundance >= 0.8f -> PEAK
                entry.relAbundance >= 0.2f -> ACTIVE
                currentWeek < species.peakWeek -> EARLY
                else -> LATE
            }
        }
    }
}

enum class SortMode {
    LIKELIHOOD, PEAK_DATE, NAME, TAXONOMY
}

enum class ObservationFilter {
    ALL, OBSERVED, NOT_OBSERVED, FAVORITES
}
