package com.codylimber.fieldphenology.ui.screens.lifelist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateLifeListScreen(
    apiClient: INatApiClient,
    lifeListService: LifeListService,
    onBack: () -> Unit,
    onGenerated: () -> Unit,
    initialTaxonId: Int? = null,
    initialTaxonName: String? = null
) {
    val initialTaxon = if (initialTaxonId != null && initialTaxonName != null)
        TaxonResult(initialTaxonId, initialTaxonName, "", "", "")
    else null

    var query by remember { mutableStateOf(initialTaxonName ?: "") }
    var suggestions by remember { mutableStateOf<List<TaxonResult>>(emptyList()) }
    var selectedTaxon by remember { mutableStateOf<TaxonResult?>(initialTaxon) }
    var isSearching by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var progressFetched by remember { mutableStateOf(0) }
    var progressTotal by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initialTaxon != null) "Update Life List" else "Generate Life List", color = Primary, fontWeight = FontWeight.Bold) },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Search for a taxon (e.g. Birds, Butterflies, Odonata) to generate a life list from your iNaturalist observations.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (selectedTaxon == null) {
                // Taxon search field
                OutlinedTextField(
                    value = query,
                    onValueChange = { q ->
                        query = q
                        selectedTaxon = null
                        if (q.length >= 2) {
                            searchJob?.cancel()
                            searchJob = coroutineScope.launch {
                                delay(300)
                                isSearching = true
                                try {
                                    suggestions = apiClient.searchTaxa(q)
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
                    keyboardActions = KeyboardActions(onSearch = { /* handled by debounce */ }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                    modifier = Modifier.fillMaxWidth()
                )

                // Suggestions
                suggestions.forEach { taxon ->
                    OutlinedButton(
                        onClick = { selectedTaxon = taxon; query = taxon.displayName; suggestions = emptyList() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(taxon.displayName, modifier = Modifier.fillMaxWidth(), fontSize = 14.sp)
                    }
                }
            } else {
                // Selected taxon chip
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(selectedTaxon!!.displayName, fontWeight = FontWeight.SemiBold, color = Primary)
                            Text("Taxon ID: ${selectedTaxon!!.id}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!isGenerating) {
                            IconButton(onClick = { selectedTaxon = null; query = "" }) {
                                Icon(Icons.Default.Close, "Clear", tint = Primary)
                            }
                        }
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
            } else if (selectedTaxon != null) {
                Button(
                    onClick = {
                        val taxon = selectedTaxon ?: return@Button
                        isGenerating = true
                        errorMessage = null
                        progressFetched = 0
                        progressTotal = 0
                        coroutineScope.launch {
                            try {
                                lifeListService.generateLifeList(
                                    taxonId = taxon.id,
                                    taxonName = taxon.displayName
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
                    Text(if (initialTaxon != null) "Update Life List" else "Generate Life List")
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
