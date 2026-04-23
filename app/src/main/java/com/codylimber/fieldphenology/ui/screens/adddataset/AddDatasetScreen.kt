package com.codylimber.fieldphenology.ui.screens.adddataset

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codylimber.fieldphenology.data.api.INatApiClient
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.navigation.GenerationParams
import com.codylimber.fieldphenology.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddDatasetScreen(
    apiClient: INatApiClient,
    onBack: () -> Unit,
    onGenerate: () -> Unit,
    repository: PhenologyRepository? = null,
    viewModel: AddDatasetViewModel = viewModel { AddDatasetViewModel(apiClient) }
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var importSuccess by remember { mutableStateOf<Boolean?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.let { stream ->
                importSuccess = repository?.importDataset(stream) == true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Dataset", color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Place search
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Locations", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                FilterChip(
                    selected = state.showAllPlaces,
                    onClick = { viewModel.toggleShowAllPlaces() },
                    label = { Text(if (state.showAllPlaces) "All Places" else "Regions Only", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.15f),
                        selectedLabelColor = Primary
                    )
                )
            }
            ExposedDropdownMenuBox(
                expanded = state.showPlaceDropdown && state.filteredPlaceResults.isNotEmpty(),
                onExpandedChange = { }
            ) {
                OutlinedTextField(
                    value = state.placeQuery,
                    onValueChange = { viewModel.onPlaceQueryChanged(it) },
                    placeholder = { Text("Search for a place...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable)
                )
                ExposedDropdownMenu(
                    expanded = state.showPlaceDropdown && state.filteredPlaceResults.isNotEmpty(),
                    onDismissRequest = { viewModel.dismissDropdowns() },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    state.filteredPlaceResults.forEach { place ->
                        DropdownMenuItem(
                            text = { Text(place.name, fontSize = 14.sp) },
                            onClick = { viewModel.addPlace(place) }
                        )
                    }
                }
            }

            // Selected place chips
            if (state.selectedPlaces.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.selectedPlaces.forEach { place ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.removePlace(place) },
                            label = { Text(place.name, fontSize = 13.sp) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp))
                            },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.15f),
                                selectedLabelColor = Primary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Taxon search
            Text("Taxa (optional)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            ExposedDropdownMenuBox(
                expanded = state.showTaxonDropdown && state.taxonResults.isNotEmpty(),
                onExpandedChange = { }
            ) {
                OutlinedTextField(
                    value = state.taxonQuery,
                    onValueChange = { viewModel.onTaxonQueryChanged(it) },
                    placeholder = { Text("Search for a taxon...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable)
                )
                ExposedDropdownMenu(
                    expanded = state.showTaxonDropdown && state.taxonResults.isNotEmpty(),
                    onDismissRequest = { viewModel.dismissDropdowns() },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    state.taxonResults.forEach { taxon ->
                        DropdownMenuItem(
                            text = { Text(taxon.displayName, fontSize = 14.sp) },
                            onClick = { viewModel.addTaxon(taxon) }
                        )
                    }
                }
            }

            // Selected taxon chips
            if (state.selectedTaxons.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.selectedTaxons.forEach { taxon ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.removeTaxon(taxon) },
                            label = { Text(taxon.commonName.ifEmpty { taxon.scientificName }, fontSize = 13.sp) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp))
                            },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.15f),
                                selectedLabelColor = Primary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Tab label
            Text("Tab Label", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.groupLabel,
                onValueChange = { viewModel.onGroupLabelChanged(it) },
                placeholder = { Text("e.g., CT Butterflies") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Advanced options
            TextButton(
                onClick = { viewModel.toggleAdvanced() },
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    if (state.showAdvanced) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Advanced Options", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            AnimatedVisibility(visible = state.showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Min observations
                    Text("Minimum Observations", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Species with fewer observations will be excluded. Higher = smaller, more reliable list.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    OutlinedTextField(
                        value = state.minObs,
                        onValueChange = { viewModel.onMinObsChanged(it) },
                        placeholder = { Text("10") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        modifier = Modifier.width(120.dp)
                    )

                    // Quality grade
                    Text("Quality Grade", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.qualityGrade == "research",
                            onClick = { viewModel.onQualityGradeChanged("research") },
                            label = { Text("Research Grade", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.15f),
                                selectedLabelColor = Primary
                            )
                        )
                        FilterChip(
                            selected = state.qualityGrade == "research,needs_id",
                            onClick = { viewModel.onQualityGradeChanged("research,needs_id") },
                            label = { Text("+ Needs ID", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.15f),
                                selectedLabelColor = Primary
                            )
                        )
                    }

                    // Max photos
                    Text("Photos per Species", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = state.maxPhotos,
                        onValueChange = { viewModel.onMaxPhotosChanged(it) },
                        placeholder = { Text("3") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                        modifier = Modifier.width(120.dp)
                    )

                    // Import dataset
                    if (repository != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Import Dataset", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Import a .manakin file shared by another user.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("Choose File", color = Primary, fontSize = 13.sp) }

                        importSuccess?.let { success ->
                            Text(
                                if (success) "Dataset imported successfully!" else "Import failed. Check the file format.",
                                color = if (success) Primary else MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                            if (success) {
                                LaunchedEffect(Unit) { onBack() }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Estimate card
            if (state.isEstimating) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                        Text("Estimating...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            } else if (state.estimatedSpecies != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Estimate", color = Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        val count = state.estimatedSpecies!!
                        val sizeMb = state.estimatedSizeMb!!
                        val minutes = state.estimatedMinutes!!
                        val sizeStr = if (sizeMb < 1) "< 1 MB" else "~${sizeMb.toInt()} MB"
                        val timeStr = if (minutes < 1) "< 1 min" else "~${minutes.toInt()} min"
                        Text(
                            "~$count species  \u2022  $sizeStr  \u2022  $timeStr",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Generate button
            Button(
                onClick = {
                    val places = state.selectedPlaces
                    val taxons = state.selectedTaxons
                    val placeName = places.joinToString(", ") { it.name }
                    val taxonName = if (taxons.isEmpty()) "All Species"
                        else taxons.joinToString(", ") { it.commonName.ifEmpty { it.scientificName } }
                    val taxonIds = if (taxons.isEmpty()) listOf(null)
                        else taxons.map { it.id }

                    GenerationParams.current = GenerationParams(
                        placeIds = places.map { it.id },
                        placeName = placeName,
                        taxonIds = taxonIds,
                        taxonName = taxonName,
                        groupName = state.groupLabel,
                        minObs = state.minObs.toIntOrNull() ?: 1,
                        qualityGrade = state.qualityGrade,
                        maxPhotos = state.maxPhotos.toIntOrNull() ?: 3
                    )
                    onGenerate()
                },
                enabled = state.canGenerate,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Generate Dataset", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
