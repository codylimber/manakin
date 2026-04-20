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

    val groupLabel: String = "",
    val minObs: String = "1",
    val isSearching: Boolean = false
) {
    val canGenerate: Boolean
        get() = selectedPlaces.isNotEmpty() && groupLabel.isNotBlank()
}

class AddDatasetViewModel(private val apiClient: INatApiClient) : ViewModel() {

    private val _state = MutableStateFlow(AddDatasetState())
    val state: StateFlow<AddDatasetState> = _state

    private var placeSearchJob: Job? = null
    private var taxonSearchJob: Job? = null

    fun onPlaceQueryChanged(query: String) {
        _state.value = _state.value.copy(placeQuery = query, showPlaceDropdown = true)
        placeSearchJob?.cancel()
        if (query.length < 2) {
            _state.value = _state.value.copy(placeResults = emptyList())
            return
        }
        placeSearchJob = viewModelScope.launch {
            delay(300)
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
        }
    }

    fun removePlace(place: PlaceResult) {
        _state.value = _state.value.copy(
            selectedPlaces = _state.value.selectedPlaces.filter { it.id != place.id }
        )
    }

    fun onTaxonQueryChanged(query: String) {
        _state.value = _state.value.copy(taxonQuery = query, showTaxonDropdown = true)
        taxonSearchJob?.cancel()
        if (query.length < 2) {
            _state.value = _state.value.copy(taxonResults = emptyList())
            return
        }
        taxonSearchJob = viewModelScope.launch {
            delay(300)
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
            val label = taxon.commonName.ifEmpty { taxon.scientificName }
            _state.value = _state.value.copy(
                selectedTaxons = current + taxon,
                taxonQuery = "",
                showTaxonDropdown = false,
                taxonResults = emptyList(),
                groupLabel = if (_state.value.groupLabel.isBlank()) label else _state.value.groupLabel
            )
        }
    }

    fun removeTaxon(taxon: TaxonResult) {
        _state.value = _state.value.copy(
            selectedTaxons = _state.value.selectedTaxons.filter { it.id != taxon.id }
        )
    }

    fun onGroupLabelChanged(label: String) {
        _state.value = _state.value.copy(groupLabel = label)
    }

    fun onMinObsChanged(value: String) {
        _state.value = _state.value.copy(minObs = value.filter { it.isDigit() })
    }

    fun dismissDropdowns() {
        _state.value = _state.value.copy(showPlaceDropdown = false, showTaxonDropdown = false)
    }
}
