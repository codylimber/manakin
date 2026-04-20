package com.codylimber.fieldphenology.ui.screens.adddataset

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codylimber.fieldphenology.data.api.INatApiClient
import com.codylimber.fieldphenology.ui.navigation.GenerationParams
import com.codylimber.fieldphenology.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddDatasetScreen(
    apiClient: INatApiClient,
    onBack: () -> Unit,
    onGenerate: () -> Unit,
    viewModel: AddDatasetViewModel = viewModel { AddDatasetViewModel(apiClient) }
) {
    val state by viewModel.state.collectAsState()

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
            Text("Locations", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            ExposedDropdownMenuBox(
                expanded = state.showPlaceDropdown && state.placeResults.isNotEmpty(),
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
                    expanded = state.showPlaceDropdown && state.placeResults.isNotEmpty(),
                    onDismissRequest = { viewModel.dismissDropdowns() },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    state.placeResults.forEach { place ->
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

            // Group label
            Text("Tab Label", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = state.groupLabel,
                onValueChange = { viewModel.onGroupLabelChanged(it) },
                placeholder = { Text("e.g., Herps, Trip Species") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                modifier = Modifier.fillMaxWidth()
            )

            // Min observations
            Text("Minimum Observations", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Species with fewer than this many total observations will be excluded. " +
                "Higher values give you a smaller list of more reliably observed species. " +
                "Lower values include rarer species but with less phenology data.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            OutlinedTextField(
                value = state.minObs,
                onValueChange = { viewModel.onMinObsChanged(it) },
                placeholder = { Text("1") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                modifier = Modifier.width(120.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

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
                        minObs = state.minObs.toIntOrNull() ?: 1
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

            Text(
                "This will take several minutes depending on the number of species (~2 seconds per species).",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}
