package com.codylimber.fieldphenology.ui.screens.lifelist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codylimber.fieldphenology.data.api.INatApiClient
import com.codylimber.fieldphenology.data.api.LifeListService
import com.codylimber.fieldphenology.data.api.TaxonResult
import com.codylimber.fieldphenology.ui.theme.Primary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GenerateLifeListScreen(
    apiClient: INatApiClient,
    lifeListService: LifeListService,
    onBack: () -> Unit,
    onGenerated: () -> Unit,
    initialTaxonId: Int? = null,
    initialTaxonName: String? = null
) {
    val isUpdate = initialTaxonId != null && initialTaxonName != null
    val initialTaxons = if (isUpdate)
        listOf(TaxonResult(initialTaxonId!!, initialTaxonName!!, "", "", ""))
    else emptyList()

    var query by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<TaxonResult>>(emptyList()) }
    var selectedTaxons by remember { mutableStateOf<List<TaxonResult>>(initialTaxons) }
    var isSearching by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var progressFetched by remember { mutableStateOf(0) }
    var progressTotal by remember { mutableStateOf(0) }
    var currentTaxonLabel by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isUpdate) "Update Life List" else "Generate Life List", color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isGenerating) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Search for taxa (e.g. Birds, Butterflies, Odonata). Add multiple for paraphyletic groups.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Selected taxon chips
            if (selectedTaxons.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedTaxons.forEach { taxon ->
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text(taxon.displayName, fontSize = 13.sp) },
                            trailingIcon = {
                                if (!isGenerating) {
                                    IconButton(
                                        onClick = { selectedTaxons = selectedTaxons.filter { it.id != taxon.id } },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.15f),
                                selectedLabelColor = Primary
                            )
                        )
                    }
                }
            }

            // Taxon search field
            if (!isGenerating) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { q ->
                        query = q
                        if (q.length >= 2) {
                            searchJob?.cancel()
                            searchJob = coroutineScope.launch {
                                delay(300)
                                isSearching = true
                                try {
                                    suggestions = apiClient.searchTaxa(q)
                                        .filter { t -> selectedTaxons.none { it.id == t.id } }
                                } catch (_: Exception) {
                                    suggestions = emptyList()
                                } finally {
                                    isSearching = false
                                }
                            }
                        } else {
                            suggestions = emptyList()
                        }
                    },
                    label = { Text("Search taxon") },
                    singleLine = true,
                    trailingIcon = {
                        if (isSearching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        else if (query.isNotEmpty()) IconButton(onClick = { query = ""; suggestions = emptyList() }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    modifier = Modifier.fillMaxWidth()
                )

                // Suggestions
                suggestions.forEach { taxon ->
                    OutlinedButton(
                        onClick = {
                            selectedTaxons = selectedTaxons + taxon
                            query = ""
                            suggestions = emptyList()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(taxon.displayName, modifier = Modifier.fillMaxWidth(), fontSize = 14.sp)
                    }
                }
            }

            // Progress / generate button
            if (isGenerating) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(color = Primary)
                    if (currentTaxonLabel.isNotEmpty()) {
                        Text(currentTaxonLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (progressTotal > 0) {
                        Text(
                            "$progressFetched of $progressTotal species fetched…",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text("Fetching observations…", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (selectedTaxons.isNotEmpty()) {
                Button(
                    onClick = {
                        isGenerating = true
                        errorMessage = null
                        progressFetched = 0
                        progressTotal = 0
                        coroutineScope.launch {
                            try {
                                val ids = selectedTaxons.map { it.id }
                                val names = selectedTaxons.map { it.displayName }
                                lifeListService.generateLifeList(
                                    taxonIds = ids,
                                    taxonNames = names,
                                    onTaxonStart = { idx ->
                                        currentTaxonLabel = if (ids.size > 1)
                                            "Fetching ${names[idx]} (${idx + 1}/${ids.size})…"
                                        else ""
                                        progressFetched = 0
                                        progressTotal = 0
                                    }
                                ) { fetched, total ->
                                    progressFetched = fetched
                                    progressTotal = total
                                }
                                onGenerated()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                errorMessage = e.message ?: "Generation failed"
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(if (isUpdate) "Update Life List" else "Generate Life List")
                }
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            if (!lifeListService.hasUsername()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No iNaturalist account connected. Add your username in Settings first.",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
