package com.codylimber.fieldphenology.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.codylimber.fieldphenology.ui.theme.MapObservation
import com.codylimber.fieldphenology.ui.theme.PillShape
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesMapScreen(
    taxonId: Int,
    placeId: Int?,
    speciesName: String,
    onBack: () -> Unit,
) {
    var basemap by remember { mutableStateOf(Basemap.MINIMAL) }
    var mapState by remember { mutableStateOf(MapUiState()) }
    var selected by remember { mutableStateOf<Observation?>(null) }
    var showLayerMenu by remember { mutableStateOf(false) }
    val camera = rememberMapCameraController()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        speciesName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SpeciesMapView(
                taxonId = taxonId,
                placeId = placeId,
                basemap = basemap,
                interactive = true,
                controller = camera,
                onState = { mapState = it },
                onObservationTap = { selected = it },
                modifier = Modifier.fillMaxSize(),
            )

            // Status strip — says what the map is showing right now, which is
            // the difference between "no observations here" and "zoom in".
            MapStatusBar(
                state = mapState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    MapControlButton(
                        icon = Icons.Default.Layers,
                        contentDescription = "Basemap",
                        onClick = { showLayerMenu = true },
                    )
                    DropdownMenu(
                        expanded = showLayerMenu,
                        onDismissRequest = { showLayerMenu = false },
                    ) {
                        Basemap.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    basemap = option
                                    showLayerMenu = false
                                },
                                trailingIcon = {
                                    RadioButton(
                                        selected = option == basemap,
                                        onClick = null,
                                    )
                                },
                            )
                        }
                    }
                }
                MapControlButton(
                    icon = Icons.Default.MyLocation,
                    contentDescription = "Reset view",
                    onClick = { camera.resetCamera() },
                )
                MapControlButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Zoom in",
                    onClick = { camera.zoomIn() },
                )
                MapControlButton(
                    icon = Icons.Default.Remove,
                    contentDescription = "Zoom out",
                    onClick = { camera.zoomOut() },
                )
            }

            AnimatedVisibility(
                visible = mapState.pointsMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // Clear of MapLibre's attribution control in the corner.
                    .padding(start = 12.dp, bottom = 34.dp),
            ) {
                MapLegend()
            }
        }
    }

    selected?.let { observation ->
        ObservationSheet(
            observation = observation,
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun MapControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 3.dp,
        modifier = Modifier.size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
private fun MapStatusBar(state: MapUiState, modifier: Modifier = Modifier) {
    val label = when {
        state.loading -> "Loading observations…"
        !state.pointsMode -> "Zoom in for individual observations"
        state.loadedCount == 0 -> "No observations in view"
        state.truncated ->
            "${"%,d".format(state.loadedCount)} of ${"%,d".format(state.totalInView)} shown"
        state.loadedCount == 1 -> "1 observation"
        else -> "${"%,d".format(state.loadedCount)} observations"
    }

    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        shadowElevation = 2.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Explains the two marker shapes: exact location versus fuzzed location. */
@Composable
private fun MapLegend() {
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(label = "Exact", filled = true)
            LegendItem(label = "Obscured", filled = false)
        }
    }
}

@Composable
private fun LegendItem(label: String, filled: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(if (filled) MapObservation else Color.Transparent)
                .then(
                    if (filled) Modifier
                    else Modifier.border(2.dp, MapObservation, CircleShape)
                )
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ObservationSheet(observation: Observation, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                observation.mediumPhotoUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        observation.taxonName ?: "Observation",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    observation.date?.let {
                        Text(
                            formatObservationDate(it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    observation.user?.let {
                        Text(
                            "by $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            observation.placeGuess?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                observation.qualityGrade?.let { grade ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(qualityLabel(grade)) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                if (observation.obscured) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("Location obscured") },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }

            if (observation.obscured) {
                Text(
                    "iNaturalist randomises this location within about 20 km, " +
                        "so the marker shows the area rather than the exact spot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = FontStyle.Italic,
                )
            }

            FilledTonalButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(observation.webUrl)))
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open on iNaturalist")
            }
        }
    }
}

private val observationDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy")

private fun formatObservationDate(raw: String): String = try {
    LocalDate.parse(raw).format(observationDateFormat)
} catch (e: Exception) {
    raw
}

private fun qualityLabel(grade: String): String = when (grade) {
    "research" -> "Research grade"
    "needs_id" -> "Needs ID"
    "casual" -> "Casual"
    else -> grade.replaceFirstChar { it.uppercase() }
}
