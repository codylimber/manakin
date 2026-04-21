package com.codylimber.fieldphenology.ui.screens.adddataset

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codylimber.fieldphenology.data.api.INatApiClient
import com.codylimber.fieldphenology.data.api.PlaceResult
import com.codylimber.fieldphenology.data.api.TaxonResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AddDatasetState(
    val placeQuery: String = "",
    val placeResults: List<PlaceResult> = emptyList(),
    val selectedPlaces: List<PlaceResult> = emptyList(),
    val showPlaceDropdown: Boolean = false,

    val taxonQuery: String = "",
    val taxonResults: List<TaxonResult> = emptyList(),
    val selectedTaxons: List<TaxonResult> = emptyList(),
    val showTaxonDropdown: Boolean = false,

    val showAllPlaces: Boolean = false,
    val groupLabel: String = "",
    val groupLabelEdited: Boolean = false,

    // Advanced options
    val showAdvanced: Boolean = false,
    val minObs: String = "10",
    val qualityGrade: String = "research",
    val maxPhotos: String = "3",

    // Estimate
    val estimatedSpecies: Int? = null,
    val isEstimating: Boolean = false,

    val isSearching: Boolean = false
) {
    val filteredPlaceResults: List<PlaceResult>
        get() = if (showAllPlaces) placeResults
                else placeResults.filter { it.adminLevel != null }

    val canGenerate: Boolean
        get() = selectedPlaces.isNotEmpty() && groupLabel.isNotBlank()

    val estimatedSizeMb: Double?
        get() {
            val count = estimatedSpecies ?: return null
            val photosPerSpecies = maxPhotos.toIntOrNull() ?: 3
            val bytes = count * (2_000 + photosPerSpecies * 75_000L)
            return bytes / 1_000_000.0
        }

    val estimatedMinutes: Double?
        get() {
            val count = estimatedSpecies ?: return null
            return count * 2.0 / 60.0
        }
}

class AddDatasetViewModel(private val apiClient: INatApiClient) : ViewModel() {

    private val _state = MutableStateFlow(AddDatasetState())
    val state: StateFlow<AddDatasetState> = _state

    private var placeSearchJob: Job? = null
    private var taxonSearchJob: Job? = null
    private var estimateJob: Job? = null

    fun onPlaceQueryChanged(query: String) {
        _state.value = _state.value.copy(placeQuery = query, showPlaceDropdown = true)
        placeSearchJob?.cancel()
        if (query.length < 2) {
            _state.value = _state.value.copy(placeResults = emptyList())
            return
        }
        placeSearchJob = viewModelScope.launch {
            delay(150)
            _state.value = _state.value.copy(isSearching = true)
            try {
                val results = apiClient.searchPlaces(query)
                Log.d("AddDataset", "Place search '$query': ${results.size} results")
                _state.value = _state.value.copy(placeResults = results, isSearching = false)
            } catch (e: Exception) {
                Log.e("AddDataset", "Place search failed", e)
                _state.value = _state.value.copy(placeResults = emptyList(), isSearching = false)
            }
        }
    }

    fun addPlace(place: PlaceResult) {
        val current = _state.value.selectedPlaces
        if (current.none { it.id == place.id }) {
            _state.value = _state.value.copy(
                selectedPlaces = current + place,
                placeQuery = "",
                showPlaceDropdown = false,
                placeResults = emptyList()
            )
            updateAutoLabel()
            fetchEstimate()
        }
    }

    fun removePlace(place: PlaceResult) {
        _state.value = _state.value.copy(
            selectedPlaces = _state.value.selectedPlaces.filter { it.id != place.id }
        )
        updateAutoLabel()
        fetchEstimate()
    }

    fun onTaxonQueryChanged(query: String) {
        _state.value = _state.value.copy(taxonQuery = query, showTaxonDropdown = true)
        taxonSearchJob?.cancel()
        if (query.length < 2) {
            _state.value = _state.value.copy(taxonResults = emptyList())
            return
        }
        taxonSearchJob = viewModelScope.launch {
            delay(150)
            _state.value = _state.value.copy(isSearching = true)
            try {
                val results = apiClient.searchTaxa(query)
                _state.value = _state.value.copy(taxonResults = results, isSearching = false)
            } catch (_: Exception) {
                _state.value = _state.value.copy(taxonResults = emptyList(), isSearching = false)
            }
        }
    }

    fun addTaxon(taxon: TaxonResult) {
        val current = _state.value.selectedTaxons
        if (current.none { it.id == taxon.id }) {
            _state.value = _state.value.copy(
                selectedTaxons = current + taxon,
                taxonQuery = "",
                showTaxonDropdown = false,
                taxonResults = emptyList()
            )
            updateAutoLabel()
            fetchEstimate()
        }
    }

    fun removeTaxon(taxon: TaxonResult) {
        _state.value = _state.value.copy(
            selectedTaxons = _state.value.selectedTaxons.filter { it.id != taxon.id }
        )
        updateAutoLabel()
        fetchEstimate()
    }

    fun onGroupLabelChanged(label: String) {
        _state.value = _state.value.copy(groupLabel = label, groupLabelEdited = true)
    }

    fun onMinObsChanged(value: String) {
        _state.value = _state.value.copy(minObs = value.filter { it.isDigit() })
    }

    fun onQualityGradeChanged(grade: String) {
        _state.value = _state.value.copy(qualityGrade = grade)
        fetchEstimate()
    }

    fun onMaxPhotosChanged(value: String) {
        _state.value = _state.value.copy(maxPhotos = value.filter { it.isDigit() })
    }

    fun toggleShowAllPlaces() {
        _state.value = _state.value.copy(showAllPlaces = !_state.value.showAllPlaces)
    }

    fun toggleAdvanced() {
        _state.value = _state.value.copy(showAdvanced = !_state.value.showAdvanced)
    }

    fun dismissDropdowns() {
        _state.value = _state.value.copy(showPlaceDropdown = false, showTaxonDropdown = false)
    }

    private fun updateAutoLabel() {
        if (_state.value.groupLabelEdited) return
        val s = _state.value
        val taxonPart = if (s.selectedTaxons.isNotEmpty()) {
            s.selectedTaxons.joinToString(", ") { it.commonName.ifEmpty { it.scientificName } }
        } else {
            "All Species"
        }
        val placePart = s.selectedPlaces.joinToString(", ") {
            it.name.substringBefore(",")
        }
        val label = if (placePart.isNotEmpty()) "$placePart $taxonPart" else taxonPart
        _state.value = _state.value.copy(groupLabel = label)
    }

    private fun fetchEstimate() {
        estimateJob?.cancel()
        val s = _state.value
        if (s.selectedPlaces.isEmpty()) {
            _state.value = _state.value.copy(estimatedSpecies = null, isEstimating = false)
            return
        }
        estimateJob = viewModelScope.launch {
            _state.value = _state.value.copy(isEstimating = true)
            try {
                val taxonIds = if (s.selectedTaxons.isEmpty()) listOf(null)
                    else s.selectedTaxons.map { it.id }
                var total = 0
                for (taxonId in taxonIds) {
                    total += apiClient.getSpeciesCountEstimate(
                        taxonId,
                        s.selectedPlaces.map { it.id },
                        s.qualityGrade
                    )
                }
                _state.value = _state.value.copy(estimatedSpecies = total, isEstimating = false)
            } catch (_: Exception) {
                _state.value = _state.value.copy(estimatedSpecies = null, isEstimating = false)
            }
        }
    }
}
