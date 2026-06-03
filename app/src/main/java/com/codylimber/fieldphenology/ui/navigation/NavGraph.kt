package com.codylimber.fieldphenology.ui.navigation

object Routes {
    const val SPECIES_LIST = "species_list"
    const val SPECIES_DETAIL = "species_detail/{taxonId}"
    const val ADD_DATASET = "add_dataset"
    const val GENERATING = "generating"
    const val MANAGE_DATASETS = "manage_datasets"
    const val HELP = "help"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val ONBOARDING = "onboarding"
    const val COMPARE = "compare"
    const val TARGETS = "targets"
    const val TIMELINE = "timeline"
    const val TRIP_REPORT = "trip_report"
    const val LIFE_LIST = "life_list"
    const val GENERATE_LIFE_LIST = "generate_life_list"
    const val GENERATE_LIFE_LIST_ROUTE = "generate_life_list?taxonId={taxonId}&taxonName={taxonName}"
    const val SPECIES_MAP = "species_map/{taxonId}/{placeId}"

    fun speciesDetail(taxonId: Int) = "species_detail/$taxonId"
    fun speciesMap(taxonId: Int, placeId: Int?) = "species_map/$taxonId/${placeId ?: -1}"
    fun generateLifeListUpdate(taxonId: Int, taxonName: String) =
        "generate_life_list?taxonId=$taxonId&taxonName=${java.net.URLEncoder.encode(taxonName, "UTF-8")}"
}
