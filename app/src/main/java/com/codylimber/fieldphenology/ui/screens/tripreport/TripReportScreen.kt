package com.codylimber.fieldphenology.ui.screens.tripreport

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codylimber.fieldphenology.data.model.Species
import com.codylimber.fieldphenology.data.model.SpeciesStatus
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.theme.AppSettings
import com.codylimber.fieldphenology.ui.theme.Primary
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

@Serializable
data class SavedTrip(
    val name: String,
    val startDate: String,
    val endDate: String,
    val datasetKeys: List<String>,
    val checkedTaxonIds: List<Int>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripReportScreen(
    repository: PhenologyRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var startDate by remember { mutableStateOf(today.minusDays(7)) }
    var endDate by remember { mutableStateOf(today) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val checkedSpecies = remember { mutableStateMapOf<Int, Boolean>() }
    var tripName by remember { mutableStateOf("") }

    val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val allKeys = repository.getKeys()
    var tripDatasetKeys by remember { mutableStateOf(AppSettings.selectedDatasetKeys.ifEmpty { allKeys.toSet() }) }

    // Saved trips
    val tripsDir = remember { File(context.filesDir, "trips").also { it.mkdirs() } }
    var savedTrips by remember { mutableStateOf<List<Pair<File, SavedTrip>>>(emptyList()) }
    LaunchedEffect(tripsDir) {
        savedTrips = withContext(Dispatchers.IO) { loadTrips(tripsDir) }
    }
    var showSavedTrips by remember { mutableStateOf(false) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    // Auto-swap if start is after end
    val effectiveStart = if (startDate.isAfter(endDate)) endDate else startDate
    val effectiveEnd = if (startDate.isAfter(endDate)) startDate else endDate

    // Get species active during the date range
    val startWeek = effectiveStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val endWeek = effectiveEnd.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val weekRange = if (startWeek <= endWeek) (startWeek..endWeek).toSet() else (startWeek..53).toSet() + (1..endWeek).toSet()

    val activeSpecies = remember(tripDatasetKeys, startWeek, endWeek) {
        val seen = mutableSetOf<Int>()
        val list = mutableListOf<Pair<Species, String>>()
        for (key in tripDatasetKeys) {
            for (sp in repository.getSpeciesForKey(key)) {
                if (sp.taxonId in seen) continue
                seen.add(sp.taxonId)
                val isActive = sp.weekly.any { it.week in weekRange && it.n > 0 }
                if (isActive) list.add(sp to key)
            }
        }
        list.sortedBy { it.first.commonName.ifEmpty { it.first.scientificName }.lowercase() }
    }

    val checkedCount = checkedSpecies.count { it.value }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Report", color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Primary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val report = buildString {
                            append("Manakin Trip Report\n")
                            append("${startDate.format(fmt)} — ${endDate.format(fmt)}\n\n")
                            append("$checkedCount of ${activeSpecies.size} active species found\n\n")
                            val found = activeSpecies.filter { checkedSpecies[it.first.taxonId] == true }
                            if (found.isNotEmpty()) {
                                append("Found:\n")
                                found.forEach { (sp, _) ->
                                    append("  \u2713 ${sp.commonName.ifEmpty { sp.scientificName }}\n")
                                }
                            }
                            val missed = activeSpecies.filter { checkedSpecies[it.first.taxonId] != true }
                            if (missed.isNotEmpty()) {
                                append("\nMissed:\n")
                                missed.forEach { (sp, _) ->
                                    append("  \u2717 ${sp.commonName.ifEmpty { sp.scientificName }}\n")
                                }
                            }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Trip Report"))
                    }) {
                        Icon(Icons.Default.Share, "Share", tint = Primary)
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
            // Dataset selector
            item {
                com.codylimber.fieldphenology.ui.components.DatasetSelector(
                    datasets = allKeys.map { com.codylimber.fieldphenology.ui.components.DatasetItem(it, repository.getGroupName(it), repository.getPlaceNameForKey(it)) },
                    selectedKeys = tripDatasetKeys,
                    onSelectSingle = { key -> tripDatasetKeys = setOf(key) },
                    onToggle = { key ->
                        tripDatasetKeys = if (key in tripDatasetKeys && tripDatasetKeys.size > 1)
                            tripDatasetKeys - key else tripDatasetKeys + key
                    },
                    onSelectAll = { tripDatasetKeys = allKeys.toSet() }
                )
            }

            // Trip name + save/load
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = tripName, onValueChange = { tripName = it },
                        placeholder = { Text("Trip name...", fontSize = 13.sp) }, singleLine = true,
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary))
                    OutlinedButton(onClick = {
                        if (tripName.isNotBlank()) {
                            val trip = SavedTrip(tripName, startDate.toString(), endDate.toString(),
                                tripDatasetKeys.toList(), checkedSpecies.filter { it.value }.keys.toList())
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    File(tripsDir, "${tripName.replace(Regex("[^a-zA-Z0-9]"), "_")}.json").writeText(json.encodeToString(trip))
                                }
                                savedTrips = withContext(Dispatchers.IO) { loadTrips(tripsDir) }
                            }
                        }
                    }, shape = RoundedCornerShape(8.dp)) { Text("Save", fontSize = 13.sp, color = Primary) }
                    OutlinedButton(onClick = { showSavedTrips = true }, shape = RoundedCornerShape(8.dp)) { Text("Load", fontSize = 13.sp, color = Primary) }
                }
            }

            // Date range selector
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showStartPicker = true }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                        Text(startDate.format(fmt), fontSize = 13.sp)
                    }
                    Text("to", modifier = Modifier.align(Alignment.CenterVertically), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { showEndPicker = true }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                        Text(endDate.format(fmt), fontSize = 13.sp)
                    }
                }
            }

            // Summary
            item {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$checkedCount", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Primary)
                            Text("Found", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${activeSpecies.size}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Active", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val pct = if (activeSpecies.isNotEmpty()) (checkedCount * 100 / activeSpecies.size) else 0
                            Text("$pct%", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Primary)
                            Text("Rate", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { Text("Tap to check off species you found:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }

            // Species checklist
            items(activeSpecies, key = { it.first.taxonId }) { (sp, _) ->
                val isChecked = checkedSpecies[sp.taxonId] == true
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { checkedSpecies[sp.taxonId] = !isChecked },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isChecked) Primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isChecked, onCheckedChange = { checkedSpecies[sp.taxonId] = it },
                            colors = CheckboxDefaults.colors(checkedColor = Primary))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val useSci = AppSettings.useScientificNames
                            val primaryName = if (useSci) sp.scientificName else sp.commonName.ifEmpty { sp.scientificName }
                            Text(primaryName, fontSize = 15.sp,
                                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            if (sp.commonName.isNotEmpty()) {
                                val secondaryName = if (useSci) sp.commonName else sp.scientificName
                                Text(secondaryName, fontSize = 12.sp, fontStyle = if (!useSci) FontStyle.Italic else FontStyle.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (isChecked) {
                            Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Date pickers
    if (showStartPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = startDate.toEpochDay() * 86400000L)
        DatePickerDialog(onDismissRequest = { showStartPicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { startDate = LocalDate.ofEpochDay(it / 86400000L) }; showStartPicker = false }) { Text("OK", color = Primary) } },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
    if (showEndPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = endDate.toEpochDay() * 86400000L)
        DatePickerDialog(onDismissRequest = { showEndPicker = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { endDate = LocalDate.ofEpochDay(it / 86400000L) }; showEndPicker = false }) { Text("OK", color = Primary) } },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    // Saved trips dialog
    if (showSavedTrips) {
        AlertDialog(
            onDismissRequest = { showSavedTrips = false },
            title = { Text("Saved Trips", color = Primary, fontWeight = FontWeight.Bold) },
            text = {
                if (savedTrips.isEmpty()) {
                    Text("No saved trips yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        savedTrips.forEach { (file, trip) ->
                            Card(onClick = {
                                tripName = trip.name
                                startDate = LocalDate.parse(trip.startDate)
                                endDate = LocalDate.parse(trip.endDate)
                                tripDatasetKeys = trip.datasetKeys.toSet()
                                checkedSpecies.clear()
                                trip.checkedTaxonIds.forEach { checkedSpecies[it] = true }
                                showSavedTrips = false
                            }, shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(trip.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        Text("${trip.startDate} — ${trip.endDate}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { scope.launch { withContext(Dispatchers.IO) { file.delete() }; savedTrips = withContext(Dispatchers.IO) { loadTrips(tripsDir) } } }) {
                                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSavedTrips = false }) { Text("Close") } }
        )
    }
}

private fun loadTrips(dir: File): List<Pair<File, SavedTrip>> {
    val json = Json { ignoreUnknownKeys = true }
    return dir.listFiles()?.filter { it.extension == "json" }?.mapNotNull { file ->
        try { file to json.decodeFromString<SavedTrip>(file.readText()) }
        catch (_: Exception) { null }
    }?.sortedByDescending { it.first.lastModified() } ?: emptyList()
}
