package com.codylimber.fieldphenology.ui.screens.adddataset

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codylimber.fieldphenology.data.api.DatasetSource
import com.codylimber.fieldphenology.data.api.GbifApiClient
import com.codylimber.fieldphenology.data.api.GbifArea
import com.codylimber.fieldphenology.data.api.INatApiClient
import com.codylimber.fieldphenology.data.api.PlaceResult
import com.codylimber.fieldphenology.data.api.TaxonResult
import java.time.LocalDate
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

    // Data source (GBIF lives under advanced options)
    val source: DatasetSource = DatasetSource.INAT,
    val gbifAreaResults: List<GbifArea> = emptyList(),
    val selectedGbifAreas: List<GbifArea> = emptyList(),
    val gbifYearsBack: String = "10",
    val gbifSampleSize: String = "600",

    // Estimate
    val estimatedSpecies: Int? = null,
    val isEstimating: Boolean = false,

    val isSearching: Boolean = false
) {
    val isGbif: Boolean get() = source == DatasetSource.GBIF

    val filteredPlaceResults: List<PlaceResult>
        get() = if (showAllPlaces) placeResults
                else placeResults.filter { it.adminLevel != null }

    val hasLocation: Boolean
        get() = if (isGbif) selectedGbifAreas.isNotEmpty() else selectedPlaces.isNotEmpty()

    val canGenerate: Boolean
        get() = hasLocation && selectedTaxons.isNotEmpty() && groupLabel.isNotBlank()

    val isLargeDataset: Boolean
        get() = (estimatedSizeMb ?: 0.0) >= 500.0

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
            // GBIF pages through occurrence samples per species, so it runs slower per species.
            val perSpeciesSec = if (isGbif) {
                val pages = ((gbifSampleSize.toIntOrNull() ?: 600) / 300.0).coerceAtLeast(1.0)
                pages * 0.4 + 1.0
            } else 2.0
            return count * perSpeciesSec / 60.0
        }
}

class AddDatasetViewModel(
    private val apiClient: INatApiClient,
    private val gbifClient: GbifApiClient
) : ViewModel() {

    private val _state = MutableStateFlow(AddDatasetState())
    val state: StateFlow<AddDatasetState> = _state

    private var placeSearchJob: Job? = null
    private var taxonSearchJob: Job? = null
    private var estimateJob: Job? = null

    fun onPlaceQueryChanged(query: String) {
        _state.value = _state.value.copy(placeQuery = query, showPlaceDropdown = true)
        placeSearchJob?.cancel()
        if (query.length < 2) {
            _state.value = _state.value.copy(placeResults = emptyList(), gbifAreaResults = emptyList())
            return
        }
        placeSearchJob = viewModelScope.launch {
            delay(150)
            _state.value = _state.value.copy(isSearching = true)
            try {
                if (_state.value.isGbif) {
                    val results = gbifClient.searchAreas(query)
                    _state.value = _state.value.copy(gbifAreaResults = results, isSearching = false)
                } else {
                    val results = apiClient.searchPlaces(query)
                    Log.d("AddDataset", "Place search '$query': ${results.size} results")
                    _state.value = _state.value.copy(placeResults = results, isSearching = false)
                }
            } catch (e: Exception) {
                Log.e("AddDataset", "Place search failed", e)
                _state.value = _state.value.copy(placeResults = emptyList(), gbifAreaResults = emptyList(), isSearching = false)
            }
        }
    }

    fun onSourceChanged(source: DatasetSource) {
        if (_state.value.source == source) return
        // Geography and taxon ids differ between sources, so clear location/taxon selections.
        _state.value = _state.value.copy(
            source = source,
            placeQuery = "",
            placeResults = emptyList(),
            selectedPlaces = emptyList(),
            gbifAreaResults = emptyList(),
            selectedGbifAreas = emptyList(),
            taxonQuery = "",
            taxonResults = emptyList(),
            selectedTaxons = emptyList(),
            estimatedSpecies = null,
            isEstimating = false,
            showPlaceDropdown = false,
            showTaxonDropdown = false
        )
        updateAutoLabel()
    }

    fun addGbifArea(area: GbifArea) {
        val current = _state.value.selectedGbifAreas
        if (current.none { it.id == area.id }) {
            _state.value = _state.value.copy(
                selectedGbifAreas = current + area,
                placeQuery = "",
                showPlaceDropdown = false,
                gbifAreaResults = emptyList()
            )
            updateAutoLabel()
            fetchEstimate()
        }
    }

    fun removeGbifArea(area: GbifArea) {
        _state.value = _state.value.copy(
            selectedGbifAreas = _state.value.selectedGbifAreas.filter { it.id != area.id }
        )
        updateAutoLabel()
        fetchEstimate()
    }

    fun onGbifYearsBackChanged(value: String) {
        _state.value = _state.value.copy(gbifYearsBack = value.filter { it.isDigit() })
        fetchEstimate()
    }

    fun onGbifSampleSizeChanged(value: String) {
        _state.value = _state.value.copy(gbifSampleSize = value.filter { it.isDigit() })
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
                val results = if (_state.value.isGbif) gbifClient.searchTaxa(query)
                              else apiClient.searchTaxa(query)
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

    fun addTaxa(taxa: List<TaxonResult>) {
        val current = _state.value.selectedTaxons
        val existingIds = current.map { it.id }.toSet()
        val additions = taxa.filter { it.id !in existingIds }
            .distinctBy { it.id }
        if (additions.isEmpty()) return
        _state.value = _state.value.copy(
            selectedTaxons = current + additions,
            taxonQuery = "",
            showTaxonDropdown = false,
            taxonResults = emptyList()
        )
        updateAutoLabel()
        fetchEstimate()
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
        val placePart = if (s.isGbif) {
            s.selectedGbifAreas.joinToString(", ") { it.name.substringBefore(",") }
        } else {
            s.selectedPlaces.joinToString(", ") { it.name.substringBefore(",") }
        }
        val label = if (placePart.isNotEmpty()) "$placePart $taxonPart" else taxonPart
        _state.value = _state.value.copy(groupLabel = label)
    }

    private fun fetchEstimate() {
        estimateJob?.cancel()
        val s = _state.value
        if (!s.hasLocation) {
            _state.value = _state.value.copy(estimatedSpecies = null, isEstimating = false)
            return
        }
        estimateJob = viewModelScope.launch {
            _state.value = _state.value.copy(isEstimating = true)
            try {
                val taxonIds = if (s.selectedTaxons.isEmpty()) listOf(null)
                    else s.selectedTaxons.map { it.id }
                val total = if (s.isGbif) {
                    val yearEnd = LocalDate.now().year
                    val yearStart = yearEnd - (s.gbifYearsBack.toIntOrNull() ?: 10) + 1
                    gbifClient.getSpeciesCountEstimate(
                        taxonIds, s.selectedGbifAreas.map { it.id }, yearStart, yearEnd
                    )
                } else {
                    var t = 0
                    for (taxonId in taxonIds) {
                        t += apiClient.getSpeciesCountEstimate(
                            taxonId, s.selectedPlaces.map { it.id }, s.qualityGrade
                        )
                    }
                    t
                }
                _state.value = _state.value.copy(estimatedSpecies = total, isEstimating = false)
            } catch (_: Exception) {
                _state.value = _state.value.copy(estimatedSpecies = null, isEstimating = false)
            }
        }
    }
}
