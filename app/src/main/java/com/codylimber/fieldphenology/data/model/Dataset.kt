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
    val minObs: Int = 10,
    val qualityGrade: String = "research",
    val maxPhotos: Int = 3,
    // Origin of the occurrence data. "inaturalist" (default, back-compat) or "gbif".
    // GBIF datasets sample real occurrences from the last [yearsBack] years for
    // phenology, and borrow photos/descriptions from iNaturalist where a match exists.
    val source: String = "inaturalist",
    val yearsBack: Int? = null,
    // GBIF-only: persisted so the dataset can be regenerated/updated. gbifAreaIds are
    // GADM gadmGids (taxonIds above hold the GBIF backbone keys for GBIF datasets).
    val gbifAreaIds: List<String> = emptyList(),
    val gbifSampleSize: Int = 600,
    // Months (1..12) the species list was limited to; empty = whole year. Each species still
    // carries its full 53-week phenology — only membership in the pack is filtered.
    val months: List<Int> = emptyList()
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
) {
    /**
     * Smoothed activity profile, computed once per loaded species. Delegated properties have
     * no backing field, so kotlinx.serialization ignores it and dataset JSON is unchanged.
     */
    val profile: PhenologyProfile by lazy(LazyThreadSafetyMode.PUBLICATION) {
        PhenologyProfile.compute(weekly)
    }
}

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
        /**
         * Status from the smoothed profile, so a gap week between active weeks is not
         * reported as inactive (any week with real records stays active, as before). EARLY/LATE only apply to seasonal species (and only when the
         * user has them enabled); for year-round residents a low week is still ACTIVE.
         */
        fun classify(
            species: Species,
            currentWeek: Int,
            showEarlyLate: Boolean = PhenologyPrefs.showEarlyLate
        ): SpeciesStatus {
            val profile = species.profile
            val a = profile.at(currentWeek)
            return when {
                !profile.isActive(currentWeek) -> INACTIVE
                a >= 0.8f -> PEAK
                a >= 0.2f -> ACTIVE
                !showEarlyLate || !profile.isSeasonal -> ACTIVE
                profile.isRising(currentWeek) -> EARLY
                else -> LATE
            }
        }

        /** Classifies at the week of highest activity within [weeks] (for date ranges). */
        fun classifyRange(species: Species, weeks: Collection<Int>): SpeciesStatus {
            val best = weeks.maxByOrNull { species.abundanceAt(it) } ?: return INACTIVE
            return classify(species, best)
        }
    }
}

enum class SortMode {
    LIKELIHOOD, PEAK_DATE, NAME, TAXONOMY
}

enum class ObservationFilter {
    ALL, OBSERVED, NOT_OBSERVED, FAVORITES
}

@Serializable
data class LifeListEntry(
    val taxonId: Int,
    val commonName: String = "",
    val scientificName: String = "",
    val firstObservedDate: String,
    val lastObservedDate: String,
    val photoUrl: String? = null
)

@Serializable
data class SavedLifeList(
    val taxonId: Int,                        // primary taxon (kept for back-compat file naming)
    val taxonName: String,                   // auto-generated display name
    val generatedAt: String,
    val entries: List<LifeListEntry>,
    val taxonIds: List<Int> = listOf(taxonId), // all taxa for multi-taxon lists
    val customName: String? = null            // user-set rename, overrides taxonName in UI
) {
    val displayName: String get() = customName?.takeIf { it.isNotBlank() } ?: taxonName
}

enum class LifeListSortMode {
    RECENTLY_ADDED,
    RECENTLY_SEEN,
    TAXONOMY,
    NAME
}
