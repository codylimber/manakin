package com.codylimber.fieldphenology.ui.screens.specieslist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codylimber.fieldphenology.data.api.LifeListService
import com.codylimber.fieldphenology.data.model.ObservationFilter
import com.codylimber.fieldphenology.data.model.SortMode
import com.codylimber.fieldphenology.ui.theme.AppSettings
import com.codylimber.fieldphenology.data.model.Species
import com.codylimber.fieldphenology.data.model.SpeciesStatus
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.IsoFields

data class DatasetOption(
    val key: String,
    val group: String,
    val placeName: String
)

data class OrganismOfTheDay(
    val species: Species,
    val key: String,
    val status: SpeciesStatus,
    val photoUri: String?
)

data class SpeciesListState(
    val datasets: List<DatasetOption> = emptyList(),
    val selectedKeys: Set<String> = emptySet(),
    val displayLabel: String = "",
    val sortMode: SortMode = SortMode.LIKELIHOOD,
    val showAllSpecies: Boolean = !AppSettings.showActiveOnly,
    val searchQuery: String = "",
    val searchAllDatasets: Boolean = false,
    val species: List<SpeciesWithStatus> = emptyList(),
    val currentWeek: Int = 1,
    val endWeek: Int? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val isCustomDate: Boolean = false,
    val isRangeMode: Boolean = false,
    val activeCount: Int = 0,
    val totalCount: Int = 0,
    val observedIds: Set<Int> = emptySet(),
    val observationFilter: ObservationFilter = ObservationFilter.ALL,
    val observedCount: Int = 0,
    val hasLifeList: Boolean = false,
    val taxonomyHeaders: Map<Int, String> = emptyMap(),
    val isLoading: Boolean = false
)

data class SpeciesWithStatus(
    val species: Species,
    val status: SpeciesStatus,
    val currentAbundance: Float,
    val isObserved: Boolean = false,
    val sourceKey: String = ""
)

class SpeciesListViewModel(
    private val repository: PhenologyRepository,
    private val lifeListService: LifeListService? = null
) : ViewModel() {

    private val _state = MutableStateFlow(SpeciesListState())
    val state: StateFlow<SpeciesListState> = _state
    private var updateJob: Job? = null
    private var searchDebounceJob: Job? = null

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 250L
    }

    init {
        val currentWeek = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val datasets = buildDatasetOptions()
        val firstKey = datasets.firstOrNull()?.key ?: ""

        // Use shared selection if available, otherwise default to first
        val initialKeys = if (AppSettings.selectedDatasetKeys.isNotEmpty() &&
            AppSettings.selectedDatasetKeys.any { k -> datasets.any { it.key == k } }) {
            AppSettings.selectedDatasetKeys.filter { k -> datasets.any { it.key == k } }.toSet()
        } else if (firstKey.isNotEmpty()) setOf(firstKey) else emptySet()
        AppSettings.selectedDatasetKeys = initialKeys

        _state.value = SpeciesListState(
            datasets = datasets,
            selectedKeys = initialKeys,
            displayLabel = buildDisplayLabel(initialKeys, datasets),
            currentWeek = currentWeek,
            sortMode = AppSettings.defaultSortMode
        )

        if (firstKey.isNotEmpty()) {
            loadObservedSpecies()
            scheduleUpdate()
        }
    }

    fun refresh() {
        repository.reloadDatasets()
        val datasets = buildDatasetOptions()
        val currentKeys = AppSettings.selectedDatasetKeys
        val validKeys = currentKeys.filter { k -> datasets.any { it.key == k } }.toSet()
        val selected = validKeys.ifEmpty { datasets.firstOrNull()?.key?.let { setOf(it) } ?: emptySet() }
        AppSettings.selectedDatasetKeys = selected
        _state.value = _state.value.copy(
            datasets = datasets,
            selectedKeys = selected,
            displayLabel = buildDisplayLabel(selected, datasets)
        )
        loadObservedSpecies()
        scheduleUpdate()
    }

    fun toggleDataset(key: String) {
        val current = _state.value.selectedKeys.toMutableSet()
        if (key in current) {
            if (current.size > 1) current.remove(key)
        } else {
            current.add(key)
        }
        AppSettings.selectedDatasetKeys = current
        _state.value = _state.value.copy(
            selectedKeys = current,
            displayLabel = buildDisplayLabel(current, _state.value.datasets)
        )
        loadObservedSpecies()
        scheduleUpdate()
    }

    fun selectAllDatasets() {
        val allKeys = _state.value.datasets.map { it.key }.toSet()
        AppSettings.selectedDatasetKeys = allKeys
        _state.value = _state.value.copy(
            selectedKeys = allKeys,
            displayLabel = buildDisplayLabel(allKeys, _state.value.datasets)
        )
        loadObservedSpecies()
        scheduleUpdate()
    }

    fun selectSingleDataset(key: String) {
        AppSettings.selectedDatasetKeys = setOf(key)
        _state.value = _state.value.copy(
            selectedKeys = setOf(key),
            displayLabel = buildDisplayLabel(setOf(key), _state.value.datasets)
        )
        loadObservedSpecies()
        scheduleUpdate()
    }

    fun setSortMode(mode: SortMode) {
        _state.value = _state.value.copy(sortMode = mode)
        scheduleUpdate()
    }

    fun setShowAllSpecies(showAll: Boolean) {
        AppSettings.showActiveOnly = !showAll
        _state.value = _state.value.copy(showAllSpecies = showAll)
        scheduleUpdate()
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        // Debounce search to avoid recomputing on every keystroke
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            updateSpeciesList()
        }
    }

    fun toggleSearchAllDatasets() {
        _state.value = _state.value.copy(searchAllDatasets = !_state.value.searchAllDatasets)
        scheduleUpdate()
    }

    fun setDate(date: LocalDate) {
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val isCustom = date != LocalDate.now()
        _state.value = _state.value.copy(
            selectedDate = date,
            currentWeek = week,
            endDate = null,
            endWeek = null,
            isCustomDate = isCustom,
            isRangeMode = false
        )
        scheduleUpdate()
    }

    fun setDateRange(start: LocalDate, end: LocalDate) {
        val startWeek = start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        val endWeek = end.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        _state.value = _state.value.copy(
            selectedDate = start,
            currentWeek = startWeek,
            endDate = end,
            endWeek = endWeek,
            isCustomDate = true,
            isRangeMode = true
        )
        scheduleUpdate()
    }

    fun resetToToday() {
        setDate(LocalDate.now())
    }

    fun setObservationFilter(filter: ObservationFilter) {
        _state.value = _state.value.copy(observationFilter = filter)
        scheduleUpdate()
    }

    fun getOrganismOfTheDay(): OrganismOfTheDay? {
        val week = _state.value.currentWeek
        val candidates = mutableListOf<Triple<Species, String, SpeciesStatus>>()
        for (key in repository.getKeys()) {
            for (sp in repository.getSpeciesForKey(key)) {
                val entry = sp.weekly.find { it.week == week } ?: continue
                if (entry.relAbundance >= 0.25f && entry.n > 0) {
                    val status = SpeciesStatus.classify(sp, week)
                    candidates.add(Triple(sp, key, status))
                }
            }
        }
        if (candidates.isEmpty()) return null
        val dayOfYear = LocalDate.now().dayOfYear
        val pick = candidates[dayOfYear % candidates.size]
        val photoUri = pick.first.photos.firstOrNull()?.let {
            repository.getPhotoUri(pick.second, it.file)
        }
        return OrganismOfTheDay(pick.first, pick.second, pick.third, photoUri)
    }

    /** Get the first selected key that contains a given species (for photo URIs) */
    fun getKeyForSpecies(taxonId: Int): String? {
        for (key in _state.value.selectedKeys) {
            if (repository.getSpeciesForKey(key).any { it.taxonId == taxonId }) return key
        }
        return repository.getKeyForSpecies(taxonId)
    }

    private fun loadObservedSpecies() {
        val service = lifeListService ?: return
        if (!service.hasUsername()) {
            _state.value = _state.value.copy(observedIds = emptySet(), hasLifeList = false)
            return
        }
        val allIds = mutableSetOf<Int>()
        for (key in _state.value.selectedKeys) {
            allIds.addAll(service.getObservedForScope(key))
        }
        _state.value = _state.value.copy(observedIds = allIds, hasLifeList = true)
    }

    private fun buildDatasetOptions(): List<DatasetOption> =
        repository.getKeys().map { key ->
            DatasetOption(key, repository.getGroupName(key), repository.getPlaceNameForKey(key))
        }

    private fun buildDisplayLabel(keys: Set<String>, datasets: List<DatasetOption>): String {
        val selected = datasets.filter { it.key in keys }
        return when {
            selected.isEmpty() -> ""
            selected.size == 1 -> "${selected[0].group} — ${selected[0].placeName}"
            else -> "${selected.size} datasets selected"
        }
    }

    private fun scheduleUpdate() {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            updateSpeciesList()
        }
    }

    private suspend fun updateSpeciesList() {
        val st = _state.value

        withContext(Dispatchers.Default) {
            val week = st.currentWeek
            val weekRange = if (st.isRangeMode && st.endWeek != null) {
                if (st.currentWeek <= st.endWeek) (st.currentWeek..st.endWeek).toSet()
                else (st.currentWeek..53).toSet() + (1..st.endWeek).toSet()
            } else setOf(week)

            // Merge species from selected (or all) datasets, dedup by taxonId
            val keysToSearch = if (st.searchQuery.isNotBlank())
                repository.getKeys() else st.selectedKeys.toList()
            val seen = mutableSetOf<Int>()
            val allWithStatus = mutableListOf<SpeciesWithStatus>()
            for (key in keysToSearch) {
                for (sp in repository.getSpeciesForKey(key)) {
                    if (sp.taxonId in seen) continue
                    seen.add(sp.taxonId)
                    val abundance = if (st.isRangeMode) {
                        sp.weekly.filter { it.week in weekRange }.maxOfOrNull { it.relAbundance } ?: 0f
                    } else {
                        sp.weekly.find { it.week == week }?.relAbundance ?: 0f
                    }
                    val status = if (st.isRangeMode) {
                        when {
                            abundance >= 0.8f -> SpeciesStatus.PEAK
                            abundance >= 0.2f -> SpeciesStatus.ACTIVE
                            abundance > 0f -> SpeciesStatus.EARLY
                            else -> SpeciesStatus.INACTIVE
                        }
                    } else SpeciesStatus.classify(sp, week)
                    val isObserved = sp.taxonId in st.observedIds
                    allWithStatus.add(SpeciesWithStatus(sp, status, abundance, isObserved, key))
                }
            }

            var filtered = if (st.showAllSpecies) allWithStatus
                else allWithStatus.filter { it.status != SpeciesStatus.INACTIVE }

            // Min activity threshold — only apply when showing active species, not in "All" mode
            if (!st.showAllSpecies) {
                val minThreshold = AppSettings.minActivityPercent / 100f
                if (minThreshold > 0f) {
                    filtered = filtered.filter { it.currentAbundance >= minThreshold || it.status == SpeciesStatus.INACTIVE }
                }
            }

            filtered = when (st.observationFilter) {
                ObservationFilter.ALL -> filtered
                ObservationFilter.OBSERVED -> filtered.filter { it.isObserved }
                ObservationFilter.NOT_OBSERVED -> filtered.filter { !it.isObserved }
                ObservationFilter.FAVORITES -> filtered.filter { AppSettings.isFavorite(it.species.taxonId) }
            }

            if (st.searchQuery.isNotBlank()) {
                val query = st.searchQuery.lowercase()
                filtered = filtered.filter { item ->
                    item.species.commonName.lowercase().contains(query) ||
                    item.species.scientificName.lowercase().contains(query)
                }
            }

            val sorted = when (st.sortMode) {
                SortMode.LIKELIHOOD -> filtered.sortedWith(
                    compareBy<SpeciesWithStatus> { statusPriority(it.status) }
                        .thenByDescending {
                            val logObs = if (it.species.totalObs > 0) kotlin.math.ln(it.species.totalObs.toDouble()) else 0.0
                            it.currentAbundance * logObs
                        }
                )
                SortMode.PEAK_DATE -> filtered.sortedBy { it.species.peakWeek }
                SortMode.NAME -> filtered.sortedBy {
                    it.species.commonName.ifEmpty { it.species.scientificName }.lowercase()
                }
                SortMode.TAXONOMY -> filtered.sortedWith(
                    compareBy<SpeciesWithStatus> { it.species.order ?: "" }
                        .thenBy { it.species.family ?: "" }
                        .thenBy { it.species.scientificName }
                )
            }

            val headers = if (st.sortMode == SortMode.TAXONOMY) {
                buildTaxonomyHeaders(sorted)
            } else emptyMap()

            // Compute counts from the final displayed list so the info bar matches
            val displayedActiveCount = sorted.count { it.status != SpeciesStatus.INACTIVE }
            val displayedObservedCount = sorted.count { it.isObserved }

            _state.update { current ->
                current.copy(
                    species = sorted,
                    activeCount = displayedActiveCount,
                    totalCount = sorted.size,
                    observedCount = displayedObservedCount,
                    taxonomyHeaders = headers
                )
            }
        }
    }

    private fun buildTaxonomyHeaders(sorted: List<SpeciesWithStatus>): Map<Int, String> {
        if (sorted.isEmpty()) return emptyMap()

        val useSci = AppSettings.useScientificNames
        val distinctOrders = sorted.mapNotNull { it.species.order }.distinct()
        val multipleOrders = distinctOrders.size > 1

        val headers = mutableMapOf<Int, String>()
        var lastOrder = ""
        var lastFamily = ""
        for ((index, item) in sorted.withIndex()) {
            val order = item.species.order ?: ""
            val familyCommon = item.species.family ?: "Unknown"
            val familySci = item.species.familyScientific

            // Insert order header when order changes and there are multiple orders
            if (multipleOrders && order != lastOrder) {
                val orderCommon = item.species.order ?: "Unknown"
                val orderSci = item.species.orderScientific
                val orderLabel = if (useSci) orderSci ?: orderCommon
                    else if (orderSci != null) "$orderCommon ($orderSci)" else orderCommon
                headers[index] = orderLabel
                lastOrder = order
                lastFamily = "" // force family header after order header
            }

            // Always insert family header when family changes
            val familyKey = "$order/$familyCommon"
            if (familyKey != lastFamily) {
                val familyLabel = if (useSci) familySci ?: familyCommon
                    else if (familySci != null) "$familyCommon ($familySci)" else familyCommon
                // If we just inserted an order header at this index, combine them
                if (headers.containsKey(index)) {
                    headers[index] = headers[index] + "\n" + familyLabel
                } else {
                    headers[index] = familyLabel
                }
                lastFamily = familyKey
            }
        }
        return headers
    }

    private fun statusPriority(status: SpeciesStatus): Int = when (status) {
        SpeciesStatus.PEAK -> 0
        SpeciesStatus.ACTIVE -> 1
        SpeciesStatus.EARLY -> 2
        SpeciesStatus.LATE -> 2
        SpeciesStatus.INACTIVE -> 3
    }
}
