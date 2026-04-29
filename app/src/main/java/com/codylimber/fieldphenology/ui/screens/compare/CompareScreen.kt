package com.codylimber.fieldphenology.ui.screens.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
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
import com.codylimber.fieldphenology.ui.theme.BottomNavBarPadding
import com.codylimber.fieldphenology.ui.theme.Primary
import java.time.LocalDate
import java.time.temporal.IsoFields

enum class CompareViewMode { ALL, ONLY_A, SHARED, ONLY_B }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    repository: PhenologyRepository,
    lifeListService: LifeListService? = null,
    onBack: (() -> Unit)? = null,
    onSpeciesClick: (Int) -> Unit
) {
    val keys = repository.getKeys()

    if (keys.size < 2) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Compare") },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Primary)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("You need at least 2 datasets to compare.\nDownload more from the Datasets tab.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    var keyA by remember { mutableStateOf(keys.getOrNull(0) ?: "") }
    var keyB by remember { mutableStateOf(keys.getOrNull(1) ?: "") }
    var expandedA by remember { mutableStateOf(false) }
    var expandedB by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(CompareViewMode.ALL) }
    val currentWeek = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val hasLifeList = lifeListService?.hasUsername() == true

    // Filter keyB to same taxon group as keyA
    val compatibleKeysForB = keys.filter { repository.getTaxonGroup(it) == repository.getTaxonGroup(keyA) && it != keyA }

    // Auto-correct keyB when keyA changes
    LaunchedEffect(keyA) {
        if (keyB !in compatibleKeysForB) {
            keyB = compatibleKeysForB.firstOrNull() ?: ""
        }
    }
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

    val speciesA = remember(keyA) { repository.getSpeciesForKey(keyA).associateBy { it.taxonId } }
    val speciesB = remember(keyB) { repository.getSpeciesForKey(keyB).associateBy { it.taxonId } }

    val onlyA = remember(speciesA, speciesB) { speciesA.filterKeys { it !in speciesB } }
    val onlyB = remember(speciesA, speciesB) { speciesB.filterKeys { it !in speciesA } }
    val shared = remember(speciesA, speciesB) { speciesA.filterKeys { it in speciesB } }

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
                            Text(repository.getPlaceNameForKey(keyA), fontSize = 13.sp, maxLines = 1)
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
                        OutlinedButton(
                            onClick = { if (compatibleKeysForB.isNotEmpty()) expandedB = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = compatibleKeysForB.isNotEmpty()
                        ) {
                            Text(
                                if (keyB.isNotEmpty()) repository.getPlaceNameForKey(keyB) else "—",
                                fontSize = 13.sp, maxLines = 1
                            )
                        }
                        DropdownMenu(expanded = expandedB, onDismissRequest = { expandedB = false }) {
                            compatibleKeysForB.forEach { key ->
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
                    SummaryChip(
                        label = "Only ${repository.getPlaceNameForKey(keyA)}",
                        count = onlyA.size,
                        color = Primary,
                        selected = viewMode == CompareViewMode.ONLY_A,
                        onClick = { viewMode = if (viewMode == CompareViewMode.ONLY_A) CompareViewMode.ALL else CompareViewMode.ONLY_A }
                    )
                    SummaryChip(
                        label = "Shared",
                        count = shared.size,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        selected = viewMode == CompareViewMode.SHARED,
                        onClick = { viewMode = if (viewMode == CompareViewMode.SHARED) CompareViewMode.ALL else CompareViewMode.SHARED }
                    )
                    SummaryChip(
                        label = "Only ${repository.getPlaceNameForKey(keyB)}",
                        count = onlyB.size,
                        color = Primary,
                        selected = viewMode == CompareViewMode.ONLY_B,
                        onClick = { viewMode = if (viewMode == CompareViewMode.ONLY_B) CompareViewMode.ALL else CompareViewMode.ONLY_B }
                    )
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
            if (viewMode == CompareViewMode.ALL || viewMode == CompareViewMode.ONLY_A) {
                val filteredA = filterSpecies(onlyA.values)
                if (filteredA.isNotEmpty()) {
                    item { Text("Only in $nameA (${filteredA.size})", color = Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp)) }
                    items(filteredA) { sp ->
                        val photoUri = sp.photos.firstOrNull()?.let { repository.getPhotoUri(keyA, it.file) }
                        SpeciesCard(species = sp, status = SpeciesStatus.classify(sp, currentWeek), currentWeek = currentWeek, isObserved = sp.taxonId in observedIds, showObservedIndicator = hasLifeList,
                            photoUri = photoUri, onClick = { onSpeciesClick(sp.taxonId) })
                    }
                }
            }

            // Shared
            if (viewMode == CompareViewMode.ALL || viewMode == CompareViewMode.SHARED) {
                val filteredShared = filterSpecies(shared.values)
                if (filteredShared.isNotEmpty()) {
                    item { Text("In both (${filteredShared.size})", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp)) }
                    items(filteredShared) { sp ->
                        val photoUri = sp.photos.firstOrNull()?.let { repository.getPhotoUri(keyA, it.file) }
                        SpeciesCard(species = sp, status = SpeciesStatus.classify(sp, currentWeek), currentWeek = currentWeek, isObserved = sp.taxonId in observedIds, showObservedIndicator = hasLifeList,
                            photoUri = photoUri, onClick = { onSpeciesClick(sp.taxonId) })
                    }
                }
            }

            // Only in B
            if (viewMode == CompareViewMode.ALL || viewMode == CompareViewMode.ONLY_B) {
                val filteredB = filterSpecies(onlyB.values)
                if (filteredB.isNotEmpty()) {
                    item { Text("Only in $nameB (${filteredB.size})", color = Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp)) }
                    items(filteredB) { sp ->
                        val photoUri = sp.photos.firstOrNull()?.let { repository.getPhotoUri(keyB, it.file) }
                        SpeciesCard(species = sp, status = SpeciesStatus.classify(sp, currentWeek), currentWeek = currentWeek, isObserved = sp.taxonId in observedIds, showObservedIndicator = hasLifeList,
                            photoUri = photoUri, onClick = { onSpeciesClick(sp.taxonId) })
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(BottomNavBarPadding)) }
        }
    }
}

@Composable
private fun SummaryChip(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Primary.copy(alpha = 0.15f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("$count", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
