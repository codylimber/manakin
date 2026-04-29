package com.codylimber.fieldphenology.ui.screens.specieslist

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codylimber.fieldphenology.R
import com.codylimber.fieldphenology.data.api.LifeListService
import com.codylimber.fieldphenology.data.model.SortMode
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.theme.Primary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesListScreen(
    repository: PhenologyRepository,
    onSpeciesClick: (Int) -> Unit,
    onTimeline: () -> Unit = {},
    onTripReport: () -> Unit = {},
    onCompare: () -> Unit = {},
    onHelp: () -> Unit = {},
    onAbout: () -> Unit = {},
    lifeListService: LifeListService? = null,
    viewModel: SpeciesListViewModel = viewModel { SpeciesListViewModel(repository, lifeListService) }
) {
    val state by viewModel.state.collectAsState()
    var showOotd by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showOotd = true }) {
                        Image(painter = painterResource(id = R.drawable.manakin_logo), contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manakin", color = Primary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                },
                actions = {
                    ActiveAllToggle(
                        showAll = state.showAllSpecies,
                        onToggle = { viewModel.setShowAllSpecies(it) }
                    )
                    AppOverflowMenu(onTimeline = onTimeline, onTripReport = onTripReport, onCompare = onCompare, onHelp = onHelp, onAbout = onAbout)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
    ) { padding ->
        if (state.datasets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No datasets yet", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Go to the Datasets tab to download\nspecies data for your area", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.datasets.isNotEmpty()) {
                item {
                    DatasetSelector(state.datasets, state.selectedKeys, state.displayLabel,
                        { viewModel.selectSingleDataset(it) }, { viewModel.toggleDataset(it) },
                        { viewModel.selectAllDatasets() })
                }
            }
            item {
                OutlinedTextField(
                    value = state.searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Search species...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingIcon = { if (state.searchQuery.isNotEmpty()) IconButton(onClick = { viewModel.setSearchQuery("") }) { Icon(Icons.Default.Clear, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val info = buildString {
                        if (state.showAllSpecies) append("${state.totalCount} species") else append("${state.activeCount} active")
                        if (state.hasLifeList) append(" \u2022 ${state.observedCount} seen")
                    }
                    Text(info, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    DateChip(state.selectedDate, state.endDate, state.isCustomDate, state.isRangeMode,
                        { viewModel.setDate(it) }, { s, e -> viewModel.setDateRange(s, e) }, { viewModel.resetToToday() })
                    SortDropdown(state.sortMode) { viewModel.setSortMode(it) }
                }
            }
            state.species.forEachIndexed { index, item ->
                state.taxonomyHeaders[index]?.let { header ->
                    item {
                        val parts = header.split("\n")
                        Column(modifier = Modifier.padding(top = if (index > 0) 8.dp else 0.dp, bottom = 2.dp)) {
                            if (parts.size > 1) {
                                Text(parts[0], color = Primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(parts[1], color = Primary.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                            } else {
                                Text(header, color = Primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item(key = item.species.taxonId) {
                    val photoUri = item.species.photos.firstOrNull()?.let { repository.getPhotoUri(item.sourceKey, it.file) }
                    SpeciesCard(species = item.species, status = item.status, currentWeek = state.currentWeek,
                        photoUri = photoUri, isObserved = item.isObserved, showObservedIndicator = state.hasLifeList,
                        onClick = { onSpeciesClick(item.species.taxonId) })
                }
            }
            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }

    if (showOotd) {
        val ootd = remember { viewModel.getOrganismOfTheDay() }
        val ctx = LocalContext.current
        DisposableEffect(Unit) { val p = android.media.MediaPlayer.create(ctx, R.raw.manakin_call); p?.start(); onDispose { p?.release() } }
        if (ootd != null) {
            AlertDialog(onDismissRequest = { showOotd = false },
                confirmButton = { TextButton(onClick = { showOotd = false; onSpeciesClick(ootd.species.taxonId) }) { Text("View Details", color = Primary) } },
                dismissButton = { TextButton(onClick = { showOotd = false }) { Text("Close") } },
                title = { Text("Organism of the Day", color = Primary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        ootd.photoUri?.let { AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(it).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp))); Spacer(modifier = Modifier.height(12.dp)) }
                        Text(if (AppSettings.useScientificNames) ootd.species.scientificName else ootd.species.commonName.ifEmpty { ootd.species.scientificName }, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (ootd.species.commonName.isNotEmpty()) Text(if (AppSettings.useScientificNames) ootd.species.commonName else ootd.species.scientificName, fontSize = 14.sp, fontStyle = if (!AppSettings.useScientificNames) FontStyle.Italic else FontStyle.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(ootd.key, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                })
        } else {
            AlertDialog(onDismissRequest = { showOotd = false }, confirmButton = { TextButton(onClick = { showOotd = false }) { Text("OK") } },
                title = { Text("Organism of the Day") }, text = { Text("No active species found.") })
        }
    }
}

@Composable
private fun DatasetSelector(datasets: List<DatasetOption>, selectedKeys: Set<String>, displayLabel: String, onSelectSingle: (String) -> Unit, onToggle: (String) -> Unit, onSelectAll: () -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable { expanded = true }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(displayLabel, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, "Switch", tint = Primary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.9f)) {
            DropdownMenuItem(
                text = { Text("All Datasets", fontWeight = if (selectedKeys.size == datasets.size) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp, color = Primary) },
                onClick = { onSelectAll(); expanded = false }
            )
            HorizontalDivider()
            datasets.forEach { opt ->
                DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = opt.key in selectedKeys, onCheckedChange = { onToggle(opt.key) }, colors = CheckboxDefaults.colors(checkedColor = Primary))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column { Text(opt.group, fontWeight = if (opt.key in selectedKeys) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp); Text(opt.placeName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                } }, onClick = { onSelectSingle(opt.key); expanded = false })
            }
        }
    }
}

@Composable
private fun SortDropdown(currentSort: SortMode, onSortChange: (SortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(when (currentSort) { SortMode.LIKELIHOOD -> "Likelihood"; SortMode.PEAK_DATE -> "Peak"; SortMode.NAME -> "Name"; SortMode.TAXONOMY -> "Taxonomy" }, fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(text = { Text(when (mode) { SortMode.LIKELIHOOD -> "By Likelihood"; SortMode.PEAK_DATE -> "By Peak Date"; SortMode.NAME -> "By Name"; SortMode.TAXONOMY -> "By Taxonomy" }) },
                    onClick = { onSortChange(mode); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChip(selectedDate: LocalDate, endDate: LocalDate?, isCustomDate: Boolean, isRangeMode: Boolean, onDateSelected: (LocalDate) -> Unit, onDateRangeSelected: (LocalDate, LocalDate) -> Unit, onReset: () -> Unit) {
    var showPicker by remember { mutableStateOf(false) }; var showRange by remember { mutableStateOf(false) }; var showMenu by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("MMM d")
    val label = when { isRangeMode && endDate != null -> "${selectedDate.format(fmt)} - ${endDate.format(fmt)}"; isCustomDate -> selectedDate.format(fmt); else -> "Today" }
    Box { TextButton(onClick = { showMenu = true }) { Text(label, fontSize = 13.sp, color = if (isCustomDate) Primary else MaterialTheme.colorScheme.onSurfaceVariant); if (isCustomDate) { Spacer(modifier = Modifier.width(2.dp)); Text("\u2715", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onReset() }) } }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) { DropdownMenuItem(text = { Text("Pick a date") }, onClick = { showMenu = false; showPicker = true }); DropdownMenuItem(text = { Text("Pick a date range") }, onClick = { showMenu = false; showRange = true }); if (isCustomDate) DropdownMenuItem(text = { Text("Reset to today") }, onClick = { showMenu = false; onReset() }) } }
    if (showPicker) { val s = rememberDatePickerState(initialSelectedDateMillis = selectedDate.toEpochDay() * 86400000L); DatePickerDialog(onDismissRequest = { showPicker = false }, confirmButton = { TextButton(onClick = { s.selectedDateMillis?.let { onDateSelected(LocalDate.ofEpochDay(it / 86400000L)) }; showPicker = false }) { Text("OK", color = Primary) } }, dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }) { DatePicker(state = s) } }
    if (showRange) { val r = rememberDateRangePickerState(initialSelectedStartDateMillis = selectedDate.toEpochDay() * 86400000L); DatePickerDialog(onDismissRequest = { showRange = false }, confirmButton = { TextButton(onClick = { val a = r.selectedStartDateMillis; val b = r.selectedEndDateMillis; if (a != null && b != null) onDateRangeSelected(LocalDate.ofEpochDay(a / 86400000L), LocalDate.ofEpochDay(b / 86400000L)); showRange = false }) { Text("OK", color = Primary) } }, dismissButton = { TextButton(onClick = { showRange = false }) { Text("Cancel") } }) { DateRangePicker(state = r, modifier = Modifier.weight(1f)) } }
}

@Composable
fun ActiveAllToggle(showAll: Boolean, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        listOf(false to "Active", true to "All").forEach { (isAll, label) ->
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (showAll == isAll) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (showAll == isAll) Primary else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onToggle(isAll) }
                    .padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

@Composable
fun AppOverflowMenu(
    onTimeline: (() -> Unit)? = null,
    onTripReport: (() -> Unit)? = null,
    onCompare: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
    onAbout: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    IconButton(onClick = { showMenu = true }) {
        Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
        if (onTimeline != null) DropdownMenuItem(text = { Text("This Week") }, onClick = { showMenu = false; onTimeline() })
        if (onTripReport != null) DropdownMenuItem(text = { Text("Trip Report") }, onClick = { showMenu = false; onTripReport() })
        if (onCompare != null) DropdownMenuItem(text = { Text("Compare Locations") }, onClick = { showMenu = false; onCompare() })
        if (onHelp != null) DropdownMenuItem(text = { Text("Help") }, onClick = { showMenu = false; onHelp() })
        if (onAbout != null) DropdownMenuItem(text = { Text("About") }, onClick = { showMenu = false; onAbout() })
    }
}

@Composable
fun SortDropdownSimple(currentSort: SortMode, onSortChange: (SortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(when (currentSort) { SortMode.LIKELIHOOD -> "Likelihood"; SortMode.PEAK_DATE -> "Peak"; SortMode.NAME -> "Name"; SortMode.TAXONOMY -> "Taxonomy" }, fontSize = 13.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(text = { Text(when (mode) { SortMode.LIKELIHOOD -> "By Likelihood"; SortMode.PEAK_DATE -> "By Peak Date"; SortMode.NAME -> "By Name"; SortMode.TAXONOMY -> "By Taxonomy" }) },
                    onClick = { onSortChange(mode); expanded = false })
            }
        }
    }
}
