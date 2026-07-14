package com.codylimber.fieldphenology.ui.navigation

import com.codylimber.fieldphenology.data.api.DatasetSource

data class GenerationParams(
    val placeIds: List<Int>,
    val placeName: String,
    val taxonIds: List<Int?>,
    val taxonName: String,
    val groupName: String,
    val minObs: Int,
    val qualityGrade: String = "research",
    val maxPhotos: Int = 3,
    // --- GBIF-only fields (ignored for iNaturalist datasets) ---
    val source: DatasetSource = DatasetSource.INAT,
    val gbifAreaIds: List<String> = emptyList(), // GADM gadmGids
    val gbifYearsBack: Int = 10,
    val gbifSampleSize: Int = 600
) {
    companion object {
        @Volatile var current: GenerationParams? = null
    }
}
