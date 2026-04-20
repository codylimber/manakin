package com.codylimber.fieldphenology.ui.screens.compare

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codylimber.fieldphenology.data.api.LifeListService
import com.codylimber.fieldphenology.data.model.SortMode
import com.codylimber.fieldphenology.data.model.Species
import com.codylimber.fieldphenology.data.model.SpeciesStatus
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.theme.AppSettings
import com.codylimber.fieldphenology.ui.screens.specieslist.SpeciesCard
import com.codylimber.fieldphenology.ui.theme.Primary
import java.time.LocalDate
import java.time.temporal.IsoFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    repository: PhenologyRepository,
    lifeListService: LifeListService? = null,
    onBack: (() -> Unit)? = null,
    onSpeciesClick: (Int) -> Unit
) {
    val keys = repository.getKeys()
    var keyA by remember { mutableStateOf(keys.getOrNull(0) ?: "") }
    var keyB by remember { mutableStateOf(keys.getOrNull(1) ?: "") }
    var expandedA by remember { mutableStateOf(false) }
    var expandedB by remember { mutableStateOf(false) }
    val currentWeek = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val hasLifeList = lifeListService?.hasUsername() == true
    val observedIds = remember(keys) {
        if (lifeListService == null || !hasLifeList) emptySet()
        else keys.flatMap { lifeListService.getObservedForScope(it) }.toSet()
    }
    var sortMode by remember { mutableStateOf(AppSettings.defaultSortMode) }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, UNSEEN, STARRED

    fun filterSpecies(species: Collection<Species>): List<Species> {
        var list = species.toList()
        if (filterMode == "UNSEEN" && hasLifeList) list = list.filter { it.taxonId !in observedIds }
        if (filterMode == "STARRED") list = list.filter { AppSettings.isFavorite(it.taxonId) }
        return when (sortMode) {
            SortMode.NAME -> list.sortedBy { it.commonName.ifEmpty { it.scientificName }.lowercase() }
            SortMode.PEAK_DATE -> list.sortedBy { it.peakWeek }
            SortMode.TAXONOMY -> list.sortedWith(compareBy<Species> { it.order ?: "" }.thenBy { it.family ?: "" }.thenBy { it.scientificName })
            SortMode.LIKELIHOOD -> list.sortedWith(compareBy<Species> {
                val w = it.weekly.find { e -> e.week == currentWeek }?.relAbundance ?: 0f
                if (w > 0f) 0 else 1
            }.thenByDescending {
                val w = it.weekly.find { e -> e.week == currentWeek }?.relAbundance ?: 0f
                val logObs = if (it.totalObs > 0) kotlin.math.ln(it.totalObs.toDouble()) else 0.0
                w * logObs
            })
        }
    }

    val speciesA = repository.getSpeciesForKey(keyA).associateBy { it.taxonId }
    val speciesB = repository.getSpeciesForKey(keyB).associateBy { it.taxonId }

    val onlyA = speciesA.filterKeys { it !in speciesB }
    val onlyB = speciesB.filterKeys { it !in speciesA }
    val shared = speciesA.filterKeys { it in speciesB }

    val nameA = "${repository.getGroupName(keyA)} — ${repository.getPlaceNameForKey(keyA)}"
    val nameB = "${repository.getGroupName(keyB)} — ${repository.getPlaceNameForKey(keyB)}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare", color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Primary)
                        }
                    }
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
            // Dataset selectors
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { expandedA = true }, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(repository.getGroupName(keyA), fontSize = 13.sp, maxLines = 1)
                        }
                        DropdownMenu(expanded = expandedA, onDismissRequest = { expandedA = false }) {
                            keys.forEach { key ->
                                DropdownMenuItem(text = {
                                    Column {
                                        Text(repository.getGroupName(key), fontSize = 14.sp)
                                        Text(repository.getPlaceNameForKey(key), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }, onClick = { keyA = key; expandedA = false })
                            }
                        }
                    }
                    Text("vs", modifier = Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { expandedB = true }, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(repository.getGroupName(keyB), fontSize = 13.sp, maxLines = 1)
                        }
                        DropdownMenu(expanded = expandedB, onDismissRequest = { expandedB = false }) {
                            keys.forEach { key ->
                                DropdownMenuItem(text = {
                                    Column {
                                        Text(repository.getGroupName(key), fontSize = 14.sp)
                                        Text(repository.getPlaceNameForKey(key), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }, onClick = { keyB = key; expandedB = false })
                            }
                        }
                    }
                }
            }

            // Summary
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    SummaryChip("Only ${repository.getGroupName(keyA)}", onlyA.size, Primary)
                    SummaryChip("Shared", shared.size, MaterialTheme.colorScheme.onSurfaceVariant)
                    SummaryChip("Only ${repository.getGroupName(keyB)}", onlyB.size, Primary)
                }
            }

            // Filter + sort controls
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("ALL" to "All", "UNSEEN" to "Unseen", "STARRED" to "Starred").forEach { (value, label) ->
                            if (value == "UNSEEN" && !hasLifeList) return@forEach
                            FilterChip(selected = filterMode == value, onClick = { filterMode = value },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(alpha = 0.2f), selectedLabelColor = Primary))
                        }
                    }
                    com.codylimber.fieldphenology.ui.screens.specieslist.SortDropdownSimple(sortMode) { sortMode = it }
                }
            }

            // Only in A
            val filteredA = filterSpecies(onlyA.values)
            if (filteredA.isNotEmpty()) {
                item { Text("Only in $nameA (${filteredA.size})", color = Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp)) }
                items(filteredA) { sp ->
                    val photoUri = sp.photos.firstOrNull()?.let { repository.getPhotoUri(keyA, it.file) }
                    SpeciesCard(species = sp, status = SpeciesStatus.classify(sp, currentWeek), currentWeek = currentWeek, isObserved = sp.taxonId in observedIds, showObservedIndicator = hasLifeList,
                        photoUri = photoUri, onClick = { onSpeciesClick(sp.taxonId) })
                }
            }

            // Only in B
            val filteredB = filterSpecies(onlyB.values)
            if (filteredB.isNotEmpty()) {
                item { Text("Only in $nameB (${filteredB.size})", color = Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp)) }
                items(filteredB) { sp ->
                    val photoUri = sp.photos.firstOrNull()?.let { repository.getPhotoUri(keyB, it.file) }
                    SpeciesCard(species = sp, status = SpeciesStatus.classify(sp, currentWeek), currentWeek = currentWeek, isObserved = sp.taxonId in observedIds, showObservedIndicator = hasLifeList,
                        photoUri = photoUri, onClick = { onSpeciesClick(sp.taxonId) })
                }
            }

            // Shared
            val filteredShared = filterSpecies(shared.values)
            if (filteredShared.isNotEmpty()) {
                item { Text("In both (${filteredShared.size})", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp)) }
                items(filteredShared) { sp ->
                    val photoUri = sp.photos.firstOrNull()?.let { repository.getPhotoUri(keyA, it.file) }
                    SpeciesCard(species = sp, status = SpeciesStatus.classify(sp, currentWeek), currentWeek = currentWeek, isObserved = sp.taxonId in observedIds, showObservedIndicator = hasLifeList,
                        photoUri = photoUri, onClick = { onSpeciesClick(sp.taxonId) })
                }
            }

            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun SummaryChip(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
