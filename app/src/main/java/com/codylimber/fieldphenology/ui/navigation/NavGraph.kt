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

    fun speciesDetail(taxonId: Int) = "species_detail/$taxonId"
}
