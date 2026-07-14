package com.codylimber.fieldphenology.data.api

/** Which occurrence data source a dataset is generated from. */
enum class DatasetSource { INAT, GBIF }

/**
 * A GBIF geographic area (a GADM administrative region). Unlike iNaturalist places
 * (integer place_id), GBIF filters occurrences by a string GADM id (`gadmGid`),
 * e.g. Connecticut = "USA.7_1", United States = "USA".
 *
 * gadmLevel: 0 = country, 1 = state/province, 2 = district/county.
 */
data class GbifArea(
    val id: String,          // gadmGid, used directly as the occurrence-search filter
    val name: String,
    val gadmLevel: Int
) {
    val levelLabel: String
        get() = when (gadmLevel) {
            0 -> "Country"
            1 -> "Region"
            2 -> "District"
            else -> "Area"
        }
}

/** Result of sampling GBIF occurrences for one species: a weekly histogram plus captured names. */
data class GbifSpeciesSample(
    val weekly: Map<Int, Int>,       // week-of-year (1..53) -> count
    val scientificName: String,
    val commonName: String,
    val family: String?,
    val order: String?
)
