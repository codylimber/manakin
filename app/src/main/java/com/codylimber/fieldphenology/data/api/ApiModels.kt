package com.codylimber.fieldphenology.data.api

data class PlaceResult(val id: Int, val name: String, val adminLevel: Int? = null)

data class TaxonResult(
    val id: Int,
    val displayName: String,
    val scientificName: String,
    val commonName: String,
    val rank: String
)
