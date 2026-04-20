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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codylimber.fieldphenology.data.model.Species
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.theme.AppSettings
import com.codylimber.fieldphenology.ui.theme.FavoriteGold
import com.codylimber.fieldphenology.ui.theme.Primary
import java.time.LocalDate
import java.time.temporal.IsoFields

enum class TimelineEventType { ENTERED_PEAK, LEFT_PEAK, NEWLY_ACTIVE, BECAME_INACTIVE, APPROACHING_PEAK }

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
        for (key in selectedKeys) {
            for (sp in repository.getSpeciesForKey(key)) {
                val thisAbundance = sp.weekly.find { it.week == currentWeek }?.relAbundance ?: 0f
                val lastAbundance = sp.weekly.find { it.week == lastWeek }?.relAbundance ?: 0f
                val nextAbundance = sp.weekly.find { it.week == nextWeek }?.relAbundance ?: 0f

                when {
                    thisAbundance >= 0.8f && lastAbundance < 0.8f ->
                        list.add(TimelineEvent(sp, key, TimelineEventType.ENTERED_PEAK))
                    thisAbundance < 0.8f && lastAbundance >= 0.8f ->
                        list.add(TimelineEvent(sp, key, TimelineEventType.LEFT_PEAK))
                    thisAbundance > 0f && lastAbundance == 0f ->
                        list.add(TimelineEvent(sp, key, TimelineEventType.NEWLY_ACTIVE))
                    thisAbundance == 0f && lastAbundance > 0f ->
                        list.add(TimelineEvent(sp, key, TimelineEventType.BECAME_INACTIVE))
                    nextAbundance >= 0.8f && thisAbundance < 0.8f && thisAbundance > 0f ->
                        list.add(TimelineEvent(sp, key, TimelineEventType.APPROACHING_PEAK))
                }
            }
        }
        // Sort: peaks first, then newly active, then approaching, then leaving, then inactive
        list.sortedBy { when (it.type) {
            TimelineEventType.ENTERED_PEAK -> 0
            TimelineEventType.NEWLY_ACTIVE -> 1
            TimelineEventType.APPROACHING_PEAK -> 2
            TimelineEventType.LEFT_PEAK -> 3
            TimelineEventType.BECAME_INACTIVE -> 4
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

                grouped[TimelineEventType.ENTERED_PEAK]?.let { items ->
                    item { SectionHeader("\uD83D\uDD25 Entering Peak") }
                    items(items) { event -> EventCard(event, onSpeciesClick) }
                }
                grouped[TimelineEventType.NEWLY_ACTIVE]?.let { items ->
                    item { SectionHeader("\uD83C\uDF31 Newly Active") }
                    items(items) { event -> EventCard(event, onSpeciesClick) }
                }
                grouped[TimelineEventType.APPROACHING_PEAK]?.let { items ->
                    item { SectionHeader("\u2B06\uFE0F Approaching Peak Next Week") }
                    items(items) { event -> EventCard(event, onSpeciesClick) }
                }
                grouped[TimelineEventType.LEFT_PEAK]?.let { items ->
                    item { SectionHeader("\u2B07\uFE0F Left Peak") }
                    items(items) { event -> EventCard(event, onSpeciesClick) }
                }
                grouped[TimelineEventType.BECAME_INACTIVE]?.let { items ->
                    item { SectionHeader("\uD83D\uDCA4 Became Inactive") }
                    items(items) { event -> EventCard(event, onSpeciesClick) }
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
private fun EventCard(event: TimelineEvent, onSpeciesClick: (Int) -> Unit) {
    val sp = event.species
    val isFav = com.codylimber.fieldphenology.ui.theme.AppSettings.isFavorite(sp.taxonId)
    Card(
        onClick = { onSpeciesClick(sp.taxonId) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isFav) {
                        Icon(Icons.Filled.Star, null, tint = FavoriteGold, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(sp.commonName.ifEmpty { sp.scientificName },
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
                if (sp.commonName.isNotEmpty()) {
                    Text(sp.scientificName, fontSize = 12.sp, fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val (label, color) = when (event.type) {
                TimelineEventType.ENTERED_PEAK -> "Peak" to com.codylimber.fieldphenology.ui.theme.StatusPeak
                TimelineEventType.NEWLY_ACTIVE -> "Active" to com.codylimber.fieldphenology.ui.theme.StatusActive
                TimelineEventType.APPROACHING_PEAK -> "Rising" to Primary
                TimelineEventType.LEFT_PEAK -> "Declining" to com.codylimber.fieldphenology.ui.theme.StatusEarlyLate
                TimelineEventType.BECAME_INACTIVE -> "Inactive" to com.codylimber.fieldphenology.ui.theme.StatusInactive
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
    }
}
