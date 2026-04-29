package com.codylimber.fieldphenology.ui.screens.speciesdetail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codylimber.fieldphenology.data.api.LifeListService
import com.codylimber.fieldphenology.data.model.Species
import com.codylimber.fieldphenology.data.model.SpeciesStatus
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.components.ConservationBadge
import com.codylimber.fieldphenology.ui.components.RarityDot
import com.codylimber.fieldphenology.ui.components.StatusBadge
import com.codylimber.fieldphenology.ui.theme.AppSettings
import com.codylimber.fieldphenology.ui.theme.LocalBottomPadding
import com.codylimber.fieldphenology.ui.theme.Primary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.WeekFields

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesDetailScreen(
    taxonId: Int,
    repository: PhenologyRepository,
    lifeListService: LifeListService? = null,
    onBack: () -> Unit
) {
    val species = repository.getSpeciesById(taxonId)
    val key = repository.getKeyForSpecies(taxonId)

    if (species == null || key == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Species Not Found") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Primary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This species is no longer available.\nThe dataset may have been removed.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val currentWeek = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val status = SpeciesStatus.classify(species, currentWeek)
    val context = LocalContext.current
    val isObserved = lifeListService?.let { service ->
        service.hasUsername() && taxonId in service.getObservedForScope(key)
    } ?: false

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Primary
                        )
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
                .verticalScroll(rememberScrollState())
        ) {
            // Photo carousel
            if (species.photos.isNotEmpty()) {
                PhotoCarousel(
                    photos = species.photos,
                    group = key,
                    photoUriFn = { g, f -> repository.getPhotoUri(g, f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                )
            }

            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isObserved) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Observed",
                            tint = Primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    val useSci = AppSettings.useScientificNames
                    val headerName = if (useSci) species.scientificName
                        else species.commonName.ifEmpty { species.scientificName }
                    Text(
                        text = headerName,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = if (useSci) FontStyle.Italic else FontStyle.Normal
                    )
                }
                val subName = if (AppSettings.useScientificNames && species.commonName.isNotEmpty())
                    species.commonName else if (!AppSettings.useScientificNames) species.scientificName else null
                if (subName != null) {
                    Text(
                        text = subName,
                        color = Primary,
                        fontSize = 16.sp,
                        fontStyle = if (!AppSettings.useScientificNames) FontStyle.Italic else FontStyle.Normal
                    )
                }

                // Taxonomy
                val taxonomy = if (AppSettings.useScientificNames) {
                    listOfNotNull(
                        species.familyScientific ?: species.family,
                        species.orderScientific ?: species.order
                    )
                } else {
                    listOfNotNull(
                        species.family?.let { common ->
                            species.familyScientific?.let { "$common ($it)" } ?: common
                        },
                        species.order?.let { common ->
                            species.orderScientific?.let { "$common ($it)" } ?: common
                        }
                    )
                }.joinToString(" \u2022 ")
                if (taxonomy.isNotEmpty()) {
                    Text(
                        text = taxonomy,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Badges + target button
                Row(
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(status = status)
                        ConservationBadge(status = species.conservationStatus)
                    }
                    val isFav = AppSettings.isFavorite(species.taxonId)
                    TextButton(
                        onClick = { AppSettings.toggleFavorite(species.taxonId) }
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (isFav) com.codylimber.fieldphenology.ui.theme.FavoriteGold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (isFav) "Target" else "Add Target",
                            fontSize = 13.sp,
                            color = if (isFav) com.codylimber.fieldphenology.ui.theme.FavoriteGold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Phenology chart
                Text(
                    "Phenology",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                PhenologyChart(
                    weekly = species.weekly,
                    currentWeek = currentWeek,
                    peakWeek = species.peakWeek,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Key facts
                KeyFactsCard(species)

                // Description
                if (species.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "About",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = species.description,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                // Links
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val url = "https://www.inaturalist.org/taxa/${species.taxonId}"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("View on iNat", color = Primary, fontSize = 13.sp)
                    }
                    val dataset = repository.getDataset(key)
                    OutlinedButton(
                        onClick = {
                            val mapUrl = buildString {
                                append("https://www.inaturalist.org/observations?taxon_id=${species.taxonId}")
                                dataset?.metadata?.placeId?.let { append("&place_id=$it") }
                                append("&subview=map")
                            }
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl)))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Observation Map", color = Primary, fontSize = 13.sp)
                    }
                }

                // My observations link
                val username = lifeListService?.username
                if (!username.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val url = "https://www.inaturalist.org/observations?user_id=$username&taxon_id=${species.taxonId}"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("My Observations", color = Primary, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(LocalBottomPadding.current))
            }
        }
    }
}

@Composable
private fun KeyFactsCard(species: Species) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FactRow("Observations", "${"%,d".format(species.totalObs)}")
            FactRow("Rarity", species.rarity, species)
            FactRow("Active Season", "${weekToDate(species.firstWeek)} – ${weekToDate(species.lastWeek)}")
            FactRow("Peak", weekToDate(species.peakWeek))
            if (species.periodCount > 1) {
                FactRow("Active Periods", "${species.periodCount}")
            }
        }
    }
}

@Composable
private fun FactRow(label: String, value: String, species: Species? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (species != null) {
                RarityDot(rarity = value)
            }
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

internal val dateFormat = DateTimeFormatter.ofPattern("MMM d")

internal fun weekToDate(week: Int): String {
    if (week < 1 || week > 53) return "Week $week"
    val year = LocalDate.now().year
    val date = LocalDate.of(year, 1, 1)
        .with(WeekFields.ISO.weekOfWeekBasedYear(), week.toLong())
        .with(WeekFields.ISO.dayOfWeek(), 1)
    return date.format(dateFormat)
}
