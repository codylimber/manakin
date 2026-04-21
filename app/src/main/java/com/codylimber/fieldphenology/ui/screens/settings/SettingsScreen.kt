package com.codylimber.fieldphenology.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star


import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codylimber.fieldphenology.data.api.LifeListService
import com.codylimber.fieldphenology.data.api.ObservationScope
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.data.model.SortMode
import com.codylimber.fieldphenology.ui.theme.AppSettings
import com.codylimber.fieldphenology.ui.theme.Primary
import com.codylimber.fieldphenology.ui.theme.ThemeState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    lifeListService: LifeListService,
    repository: PhenologyRepository,
    onBack: (() -> Unit)? = null,
    onTimeline: (() -> Unit)? = null,
    onTripReport: (() -> Unit)? = null,
    onCompare: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
    onAbout: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var username by remember { mutableStateOf(lifeListService.username) }
    var scope by remember { mutableStateOf(lifeListService.observationScope) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val lastSync = lifeListService.getLastSyncTime()
    val lastSyncText = if (lastSync > 0) {
        SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date(lastSync))
    } else "Never"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Primary)
                    }
                },
                actions = {
                    com.codylimber.fieldphenology.ui.screens.specieslist.AppOverflowMenu(onTimeline = onTimeline, onTripReport = onTripReport, onCompare = onCompare, onHelp = onHelp, onAbout = onAbout)
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Profile card
            if (username.isNotBlank()) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(username, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Text("iNaturalist", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            // iNaturalist Account section
            SectionHeader(Icons.Default.Person, "iNaturalist Account")

            Text(
                "Enter your iNaturalist username to see which species you've already observed. " +
                "Your observation data is public on iNaturalist — no login is required.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp
            )

            OutlinedTextField(
                value = username,
                onValueChange = {
                    val trimmed = it.trim()
                    username = trimmed
                    lifeListService.username = trimmed
                },
                label = { Text("iNaturalist Username") },
                placeholder = { Text("e.g., codylimber") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary),
                modifier = Modifier.fillMaxWidth()
            )

            // Sync button
            SectionHeader(Icons.Default.Refresh, "Sync Observations")
            Text("Last synced: $lastSyncText", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

            Button(
                onClick = {
                    if (username.isBlank()) {
                        syncMessage = "Please enter a username first."
                        return@Button
                    }
                    isSyncing = true
                    syncMessage = null
                    coroutineScope.launch {
                        try {
                            val keys = repository.getKeys()
                            for (key in keys) {
                                val dataset = repository.getDataset(key) ?: continue
                                val meta = dataset.metadata
                                lifeListService.refreshForDataset(
                                    datasetKey = key,
                                    taxonId = null, // fetch all observed within the taxon group
                                    placeId = meta.placeId
                                )
                            }
                            syncMessage = "Synced ${keys.size} dataset(s) successfully!"
                        } catch (e: Exception) {
                            syncMessage = "Sync failed: ${e.message}"
                        } finally {
                            isSyncing = false
                        }
                    }
                },
                enabled = !isSyncing && username.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Syncing...")
                } else {
                    Text("Sync Observations", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            syncMessage?.let {
                Text(it, color = if (it.startsWith("Sync failed")) MaterialTheme.colorScheme.error else Primary, fontSize = 13.sp)
            }

            // Appearance
            SectionHeader(Icons.Filled.Star, "Appearance")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Mode", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                Switch(
                    checked = ThemeState.isDarkMode,
                    onCheckedChange = {
                        ThemeState.isDarkMode = it
                        lifeListService.prefs.edit().putBoolean("dark_mode", it).apply()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Scientific Names", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Text(
                        "Show scientific names as the primary name",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = AppSettings.useScientificNames,
                    onCheckedChange = {
                        AppSettings.useScientificNames = it
                        lifeListService.prefs.edit().putBoolean("use_scientific_names", it).apply()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                )
            }

            // Min activity threshold
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Minimum Activity", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Text("${AppSettings.minActivityPercent}%", color = Primary, fontSize = 15.sp)
                }
                Text(
                    "Hide species below this activity threshold",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Slider(
                    value = AppSettings.minActivityPercent.toFloat(),
                    onValueChange = {
                        AppSettings.minActivityPercent = it.toInt()
                        lifeListService.prefs.edit().putInt("min_activity_percent", it.toInt()).apply()
                    },
                    valueRange = 0f..50f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary)
                )
            }

            // Default sort
            Column {
                Text("Default Sort", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                Text(
                    "Sort order when opening the app",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SortMode.entries.forEach { mode ->
                        FilterChip(
                            selected = AppSettings.defaultSortMode == mode,
                            onClick = {
                                AppSettings.defaultSortMode = mode
                                lifeListService.prefs.edit().putString("default_sort", mode.name).apply()
                            },
                            label = {
                                Text(
                                    when (mode) {
                                        SortMode.LIKELIHOOD -> "Likelihood"
                                        SortMode.PEAK_DATE -> "Peak"
                                        SortMode.NAME -> "Name"
                                        SortMode.TAXONOMY -> "Taxonomy"
                                    },
                                    fontSize = 12.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary.copy(alpha = 0.2f),
                                selectedLabelColor = Primary
                            )
                        )
                    }
                }
            }

            // Notifications
            SectionHeader(Icons.Default.Notifications, "Notifications")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Weekly Digest", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Text(
                        "Get notified about newly active and peak species",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = AppSettings.weeklyDigestEnabled,
                    onCheckedChange = {
                        AppSettings.weeklyDigestEnabled = it
                        lifeListService.prefs.edit().putBoolean("weekly_digest_enabled", it).apply()
                        if (it) {
                            com.codylimber.fieldphenology.notifications.WeeklyDigestWorker.schedule(
                                context, AppSettings.digestDay, AppSettings.digestHour
                            )
                        } else {
                            com.codylimber.fieldphenology.notifications.WeeklyDigestWorker.cancel(context)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                )
            }

            if (AppSettings.weeklyDigestEnabled) {
                val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                Column {
                    Text("Digest Day", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        days.forEachIndexed { idx, day ->
                            val calDay = idx + 1 // Calendar.SUNDAY = 1
                            FilterChip(
                                selected = AppSettings.digestDay == calDay,
                                onClick = {
                                    AppSettings.digestDay = calDay
                                    lifeListService.prefs.edit().putInt("digest_day", calDay).apply()
                                    com.codylimber.fieldphenology.notifications.WeeklyDigestWorker.schedule(
                                        context, calDay, AppSettings.digestHour
                                    )
                                },
                                label = { Text(day, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(alpha = 0.2f),
                                    selectedLabelColor = Primary
                                )
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Target Species Alerts", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    Text(
                        "Get notified when favorited species approach peak (2 weeks before and at peak)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = AppSettings.targetNotificationsEnabled,
                    onCheckedChange = {
                        AppSettings.targetNotificationsEnabled = it
                        lifeListService.prefs.edit().putBoolean("target_notifications_enabled", it).apply()
                        // Ensure the weekly worker is scheduled if either notification is enabled
                        if (it && !AppSettings.weeklyDigestEnabled) {
                            com.codylimber.fieldphenology.notifications.WeeklyDigestWorker.schedule(
                                context, AppSettings.digestDay, AppSettings.digestHour
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                )
            }

            // Widget settings
            SectionHeader(Icons.Default.Star, "Home Screen Widget")

            Text("Choose what the widget displays. Long-press your home screen to add the Manakin widget.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

            var widgetMode by remember { mutableStateOf(lifeListService.prefs.getString("widget_mode", "top_active") ?: "top_active") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("top_active" to "Top Active", "organism_of_day" to "Species of Day", "timeline" to "This Week", "targets" to "Targets").forEach { (value, label) ->
                    FilterChip(selected = widgetMode == value,
                        onClick = {
                            widgetMode = value
                            lifeListService.prefs.edit().putString("widget_mode", value).apply()
                            coroutineScope.launch {
                                androidx.glance.appwidget.updateAll<com.codylimber.fieldphenology.widget.ManakinGlanceWidget>(context)
                            }
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(alpha = 0.2f), selectedLabelColor = Primary))
                }
            }


            Spacer(modifier = Modifier.height(88.dp))
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = null,
            tint = com.codylimber.fieldphenology.ui.theme.Primary,
            modifier = androidx.compose.ui.Modifier.size(20.dp)
        )
        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
        androidx.compose.material3.Text(
            title,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
            fontSize = 17.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        )
    }
}
