package com.codylimber.fieldphenology.ui.navigation

data class GenerationParams(
    val placeIds: List<Int>,
    val placeName: String,
    val taxonIds: List<Int?>,
    val taxonName: String,
    val groupName: String,
    val minObs: Int
) {
    companion object {
        var current: GenerationParams? = null
    }
}
