package com.codylimber.fieldphenology.ui.screens.timeline

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.codylimber.fieldphenology.data.model.Species
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.theme.AppSettings
import com.codylimber.fieldphenology.ui.theme.FavoriteGold
import com.codylimber.fieldphenology.ui.theme.Primary
import com.codylimber.fieldphenology.ui.theme.RarityRare
import com.codylimber.fieldphenology.ui.theme.StatusActive
import com.codylimber.fieldphenology.ui.theme.StatusEarlyLate
import com.codylimber.fieldphenology.ui.theme.StatusInactive
import com.codylimber.fieldphenology.ui.theme.StatusPeak
import java.time.LocalDate
import java.time.temporal.IsoFields

enum class TimelineEventType {
    ENTERED_PEAK, LEFT_PEAK, NEWLY_ACTIVE, BECAME_INACTIVE, APPROACHING_PEAK,
    LAST_CHANCE, COMING_SOON, RARE_AND_ACTIVE, PEAK_THIS_WEEK
}

data class TimelineEvent(
    val species: Species,
    val key: String,
    val type: TimelineEventType
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    repository: PhenologyRepository,
    onBack: () -> Unit,
    onSpeciesClick: (Int) -> Unit
) {
    val currentWeek = LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val lastWeek = if (currentWeek > 1) currentWeek - 1 else 53
    val nextWeek = if (currentWeek < 53) currentWeek + 1 else 1
    val selectedKeys = AppSettings.selectedDatasetKeys.ifEmpty { repository.getKeys().toSet() }

    val events = remember(selectedKeys) {
        val list = mutableListOf<TimelineEvent>()
        val enteredPeakIds = mutableSetOf<Int>()
        val seenByType = mutableMapOf<TimelineEventType, MutableSet<Int>>()

        fun addEvent(sp: Species, key: String, type: TimelineEventType) {
            val seen = seenByType.getOrPut(type) { mutableSetOf() }
            if (seen.add(sp.taxonId)) {
                list.add(TimelineEvent(sp, key, type))
            }
        }

        for (key in selectedKeys) {
            for (sp in repository.getSpeciesForKey(key)) {
                val thisAbundance = sp.weekly.find { it.week == currentWeek }?.relAbundance ?: 0f
                val lastAbundance = sp.weekly.find { it.week == lastWeek }?.relAbundance ?: 0f
                val nextAbundance = sp.weekly.find { it.week == nextWeek }?.relAbundance ?: 0f

                // Transition events (mutually exclusive)
                when {
                    thisAbundance >= 0.8f && lastAbundance < 0.8f -> {
                        addEvent(sp, key, TimelineEventType.ENTERED_PEAK)
                        enteredPeakIds.add(sp.taxonId)
                    }
                    thisAbundance < 0.8f && lastAbundance >= 0.8f ->
                        addEvent(sp, key, TimelineEventType.LEFT_PEAK)
                    thisAbundance > 0f && lastAbundance == 0f ->
                        addEvent(sp, key, TimelineEventType.NEWLY_ACTIVE)
                    thisAbundance == 0f && lastAbundance > 0f ->
                        addEvent(sp, key, TimelineEventType.BECAME_INACTIVE)
                    nextAbundance >= 0.8f && thisAbundance < 0.8f && thisAbundance > 0f ->
                        addEvent(sp, key, TimelineEventType.APPROACHING_PEAK)
                }

                // Forward-looking events
                if (thisAbundance > 0f && nextAbundance == 0f) {
                    addEvent(sp, key, TimelineEventType.LAST_CHANCE)
                }
                if (thisAbundance == 0f && nextAbundance > 0f) {
                    addEvent(sp, key, TimelineEventType.COMING_SOON)
                }

                // Highlight events
                if (sp.rarity == "rare" && thisAbundance > 0f) {
                    addEvent(sp, key, TimelineEventType.RARE_AND_ACTIVE)
                }
                if (thisAbundance >= 0.8f && sp.taxonId !in enteredPeakIds) {
                    addEvent(sp, key, TimelineEventType.PEAK_THIS_WEEK)
                }
            }
        }

        list.sortedBy { when (it.type) {
            TimelineEventType.LAST_CHANCE -> 0
            TimelineEventType.ENTERED_PEAK -> 1
            TimelineEventType.RARE_AND_ACTIVE -> 2
            TimelineEventType.NEWLY_ACTIVE -> 3
            TimelineEventType.COMING_SOON -> 4
            TimelineEventType.APPROACHING_PEAK -> 5
            TimelineEventType.PEAK_THIS_WEEK -> 6
            TimelineEventType.LEFT_PEAK -> 7
            TimelineEventType.BECAME_INACTIVE -> 8
        } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("This Week", color = Primary, fontWeight = FontWeight.Bold)
                        val label = if (selectedKeys.size == repository.getKeys().size) "All Datasets"
                            else selectedKeys.joinToString(", ") { repository.getGroupName(it) }
                        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No changes this week", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Group by event type
                val grouped = events.groupBy { it.type }

                grouped[TimelineEventType.LAST_CHANCE]?.let { items ->
                    item { SectionHeader("\u23F3 Last Chance") }
                    items(items) { event -> EventCard(event, repository, onSpeciesClick) }
                }
                grouped[TimelineEventType.ENTERED_PEAK]?.let { items ->
                    item { SectionHeader("\uD83D\uDD25 Entering Peak") }
                    items(items) { event -> EventCard(event, repository, onSpeciesClick) }
                }
                grouped[TimelineEventType.RARE_AND_ACTIVE]?.let { items ->
                    item { SectionHeader("\uD83D\uDC8E Rare + Active") }
                    items(items) { event -> EventCard(event, repository, onSpeciesClick) }
                }
                grouped[TimelineEventType.NEWLY_ACTIVE]?.let { items ->
                    item { SectionHeader("\uD83C\uDF31 Newly Active") }
                    items(items) { event -> EventCard(event, repository, onSpeciesClick) }
                }
                grouped[TimelineEventType.COMING_SOON]?.let { items ->
                    item { SectionHeader("\uD83D\uDC40 Coming Soon") }
                    items(items) { event -> EventCard(event, repository, onSpeciesClick) }
                }
                grouped[TimelineEventType.APPROACHING_PEAK]?.let { items ->
                    item { SectionHeader("\u2B06\uFE0F Approaching Peak Next Week") }
                    items(items) { event -> EventCard(event, repository, onSpeciesClick) }
                }
                grouped[TimelineEventType.PEAK_THIS_WEEK]?.let { items ->
                    item { SectionHeader("\u2B50 At Peak") }
                    items(items) { event -> EventCard(event, repository, onSpeciesClick) }
                }
                grouped[TimelineEventType.LEFT_PEAK]?.let { items ->
                    item { SectionHeader("\u2B07\uFE0F Left Peak") }
                    items(items) { event -> EventCard(event, repository, onSpeciesClick) }
                }
                grouped[TimelineEventType.BECAME_INACTIVE]?.let { items ->
                    item { SectionHeader("\uD83D\uDCA4 Became Inactive") }
                    items(items) { event -> EventCard(event, repository, onSpeciesClick) }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
}

@Composable
private fun EventCard(event: TimelineEvent, repository: PhenologyRepository, onSpeciesClick: (Int) -> Unit) {
    val sp = event.species
    val isFav = AppSettings.isFavorite(sp.taxonId)
    val photoUri = sp.photos.firstOrNull()?.let { repository.getPhotoUri(event.key, it.file) }
    Card(
        onClick = { onSpeciesClick(sp.taxonId) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (photoUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(photoUri).build(),
                    contentDescription = sp.commonName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isFav) {
                        Icon(Icons.Filled.Star, null, tint = FavoriteGold, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    val useSci = AppSettings.useScientificNames
                    val primaryName = if (useSci) sp.scientificName else sp.commonName.ifEmpty { sp.scientificName }
                    val secondaryName = if (useSci) sp.commonName else sp.scientificName
                    Text(primaryName,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                if (sp.commonName.isNotEmpty()) {
                    val useSci2 = AppSettings.useScientificNames
                    val secondaryName = if (useSci2) sp.commonName else sp.scientificName
                    Text(secondaryName, fontSize = 12.sp, fontStyle = if (!useSci2) FontStyle.Italic else FontStyle.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val (label, color) = when (event.type) {
                TimelineEventType.ENTERED_PEAK -> "Peak" to StatusPeak
                TimelineEventType.NEWLY_ACTIVE -> "Active" to StatusActive
                TimelineEventType.APPROACHING_PEAK -> "Rising" to Primary
                TimelineEventType.LEFT_PEAK -> "Declining" to StatusEarlyLate
                TimelineEventType.BECAME_INACTIVE -> "Inactive" to StatusInactive
                TimelineEventType.LAST_CHANCE -> "Last Chance" to RarityRare
                TimelineEventType.COMING_SOON -> "Soon" to StatusActive
                TimelineEventType.RARE_AND_ACTIVE -> "Rare" to RarityRare
                TimelineEventType.PEAK_THIS_WEEK -> "Peak" to StatusPeak
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}
