package com.codylimber.fieldphenology.ui.screens.targets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codylimber.fieldphenology.data.api.LifeListService
import com.codylimber.fieldphenology.data.model.SortMode
import com.codylimber.fieldphenology.data.model.Species
import com.codylimber.fieldphenology.data.model.SpeciesStatus
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.screens.specieslist.SpeciesCard
import com.codylimber.fieldphenology.ui.theme.AppSettings
import com.codylimber.fieldphenology.ui.theme.BottomNavBarPadding
import com.codylimber.fieldphenology.ui.theme.Primary
import java.time.LocalDate
import java.time.temporal.IsoFields

enum class TargetMode { STARRED, NOT_SEEN_HERE, NOT_SEEN_ANYWHERE }

data class TargetSpecies(
    val species: Species,
    val key: String,
    val groupName: String,
    val status: SpeciesStatus,
    val currentAbundance: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetsScreen(
    repository: PhenologyRepository,
    lifeListService: LifeListService? = null,
    onSpeciesClick: (Int) -> Unit,
    onTimeline: (() -> Unit)? = null,
    onTripReport: (() -> Unit)? = null,
    onCompare: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
    onAbout: (() -> Unit)? = null
) {
    val currentWeek = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val favorites = AppSettings.favorites
    var mode = try { TargetMode.valueOf(AppSettings.targetMode) } catch (_: Exception) { TargetMode.STARRED }
    val showActiveOnly = AppSettings.showActiveOnly
    val keys = repository.getKeys()
    // Use shared dataset selection from AppSettings
    var selectedKeys = AppSettings.selectedDatasetKeys.ifEmpty { keys.toSet() }
    val hasLifeList = lifeListService?.hasUsername() == true

    // Build all species across datasets
    val allSpecies = remember(keys, favorites) {
        val list = mutableListOf<TargetSpecies>()
        val seen = mutableSetOf<Int>()
        for (key in keys) {
            val groupName = repository.getGroupName(key)
            for (sp in repository.getSpeciesForKey(key)) {
                if (sp.taxonId in seen) continue
                seen.add(sp.taxonId)
                val status = SpeciesStatus.classify(sp, currentWeek)
                val abundance = sp.weekly.find { it.week == currentWeek }?.relAbundance ?: 0f
                list.add(TargetSpecies(sp, key, groupName, status, abundance))
            }
        }
        list
    }

    // Observed sets
    val observedGlobal = remember(keys) {
        if (lifeListService == null || !lifeListService.hasUsername()) emptySet()
        else keys.flatMap { lifeListService.getObservedGlobal(it) }.toSet()
    }
    val observedLocal = remember(keys) {
        if (lifeListService == null || !lifeListService.hasUsername()) emptySet()
        else keys.flatMap { lifeListService.getObservedLocal(it) }.toSet()
    }

    // Filter by mode
    var filtered = when (mode) {
        TargetMode.STARRED -> allSpecies.filter { it.species.taxonId in favorites }
        TargetMode.NOT_SEEN_HERE -> allSpecies.filter { it.species.taxonId !in observedLocal }
        TargetMode.NOT_SEEN_ANYWHERE -> allSpecies.filter { it.species.taxonId !in observedGlobal }
    }

    // Filter by dataset
    filtered = filtered.filter { it.key in selectedKeys }

    // Filter by active
    if (showActiveOnly) {
        filtered = filtered.filter { it.status != SpeciesStatus.INACTIVE }
    }

    // Sort
    var sortMode by remember { mutableStateOf(AppSettings.defaultSortMode) }
    val displayed = when (sortMode) {
        SortMode.LIKELIHOOD -> filtered.sortedWith(
            compareBy<TargetSpecies> { when (it.status) {
                SpeciesStatus.PEAK -> 0; SpeciesStatus.ACTIVE -> 1
                SpeciesStatus.EARLY -> 2; SpeciesStatus.LATE -> 2; SpeciesStatus.INACTIVE -> 3
            } }.thenByDescending {
                val logObs = if (it.species.totalObs > 0) kotlin.math.ln(it.species.totalObs.toDouble()) else 0.0
                it.currentAbundance * logObs
            }
        )
        SortMode.PEAK_DATE -> filtered.sortedBy { it.species.peakWeek }
        SortMode.NAME -> filtered.sortedBy { it.species.commonName.ifEmpty { it.species.scientificName }.lowercase() }
        SortMode.TAXONOMY -> filtered.sortedWith(
            compareBy<TargetSpecies> { it.species.order ?: "" }
                .thenBy { it.species.family ?: "" }
                .thenBy { it.species.scientificName }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Targets", color = Primary, fontWeight = FontWeight.Bold) },
                actions = {
                    com.codylimber.fieldphenology.ui.screens.specieslist.ActiveAllToggle(
                        showAll = !showActiveOnly,
                        onToggle = { AppSettings.showActiveOnly = !it }
                    )
                    com.codylimber.fieldphenology.ui.screens.specieslist.AppOverflowMenu(onTimeline = onTimeline, onTripReport = onTripReport, onCompare = onCompare, onHelp = onHelp, onAbout = onAbout)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Dataset selector
            if (keys.size > 1) {
                item {
                    com.codylimber.fieldphenology.ui.components.DatasetSelector(
                        datasets = keys.map { com.codylimber.fieldphenology.ui.components.DatasetItem(it, repository.getGroupName(it), repository.getPlaceNameForKey(it)) },
                        selectedKeys = selectedKeys,
                        onSelectSingle = { key -> AppSettings.selectedDatasetKeys = setOf(key) },
                        onToggle = { key ->
                            val newKeys = if (key in selectedKeys && selectedKeys.size > 1)
                                selectedKeys - key else selectedKeys + key
                            AppSettings.selectedDatasetKeys = newKeys
                        },
                        onSelectAll = { AppSettings.selectedDatasetKeys = keys.toSet() }
                    )
                }
            }

            // Mode chips
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == TargetMode.STARRED, onClick = { AppSettings.targetMode = TargetMode.STARRED.name },
                        label = { Text("\u2605 Starred", fontSize = 13.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(alpha = 0.2f), selectedLabelColor = Primary))
                    if (hasLifeList) {
                        FilterChip(selected = mode == TargetMode.NOT_SEEN_HERE, onClick = { AppSettings.targetMode = TargetMode.NOT_SEEN_HERE.name },
                            label = { Text("New for Area", fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(alpha = 0.2f), selectedLabelColor = Primary))
                        FilterChip(selected = mode == TargetMode.NOT_SEEN_ANYWHERE, onClick = { AppSettings.targetMode = TargetMode.NOT_SEEN_ANYWHERE.name },
                            label = { Text("Lifer Targets", fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(alpha = 0.2f), selectedLabelColor = Primary))
                    }
                }
            }

            // Count + sort
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${displayed.size} species", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    com.codylimber.fieldphenology.ui.screens.specieslist.SortDropdownSimple(sortMode) { sortMode = it }
                }
            }

            if (displayed.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        val msg = when (mode) {
                            TargetMode.STARRED -> "No starred species yet.\nSwipe right on a species card to star it."
                            TargetMode.NOT_SEEN_HERE -> if (!hasLifeList) "Connect your iNaturalist account\nin Settings to use this feature." else "You've seen every active species in this area!"
                            TargetMode.NOT_SEEN_ANYWHERE -> if (!hasLifeList) "Connect your iNaturalist account\nin Settings to use this feature." else "You've observed every active species! Amazing."
                        }
                        Text(msg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }
            } else {
                // Group by dataset
                val grouped = displayed.groupBy { it.groupName }
                grouped.forEach { (group, speciesList) ->
                    item { Text(group, color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
                    items(speciesList, key = { "${it.species.taxonId}_${mode.name}" }) { target ->
                        val photoUri = target.species.photos.firstOrNull()?.let {
                            repository.getPhotoUri(target.key, it.file)
                        }
                        if (mode == TargetMode.STARRED) {
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { AppSettings.toggleFavorite(target.species.taxonId); true } else false }
                            )
                            SwipeToDismissBox(state = dismissState, enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                                        Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                    }
                                }) {
                                SpeciesCard(species = target.species, status = target.status,
                                    currentWeek = currentWeek, photoUri = photoUri,
                                    onClick = { onSpeciesClick(target.species.taxonId) })
                            }
                        } else {
                            SpeciesCard(species = target.species, status = target.status,
                                currentWeek = currentWeek, photoUri = photoUri,
                                onClick = { onSpeciesClick(target.species.taxonId) })
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(BottomNavBarPadding)) }
        }
    }
}

