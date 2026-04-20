package com.codylimber.fieldphenology.ui.screens.specieslist

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.codylimber.fieldphenology.data.model.Species
import com.codylimber.fieldphenology.data.model.SpeciesStatus
import com.codylimber.fieldphenology.ui.components.RarityDot
import com.codylimber.fieldphenology.ui.components.StatusBadge
import com.codylimber.fieldphenology.ui.theme.AppSettings
import com.codylimber.fieldphenology.ui.theme.FavoriteGold
import com.codylimber.fieldphenology.ui.theme.ObservedBlue
import com.codylimber.fieldphenology.ui.theme.Primary
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpeciesCard(
    species: Species,
    status: SpeciesStatus,
    currentWeek: Int,
    photoUri: String?,
    isObserved: Boolean = false,
    showObservedIndicator: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentAbundance = species.weekly.find { it.week == currentWeek }?.relAbundance ?: 0f
    val activityPercent = (currentAbundance * 100).toInt()
    val useSci = AppSettings.useScientificNames

    val primaryName = if (useSci) species.scientificName
        else species.commonName.ifEmpty { species.scientificName }
    val secondaryName = if (useSci && species.commonName.isNotEmpty()) species.commonName
        else if (!useSci && species.commonName.isNotEmpty()) species.scientificName
        else null

    // Swipe to favorite with manual drag
    var offsetX by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 120.dp.toPx() }
    val pastThreshold = offsetX > threshold
    val isFav = AppSettings.isFavorite(species.taxonId)

    val bgColor by animateColorAsState(
        targetValue = when {
            pastThreshold && !isFav -> FavoriteGold.copy(alpha = 0.3f)
            pastThreshold && isFav -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
            offsetX > 10f && !isFav -> Primary.copy(alpha = 0.1f)
            offsetX > 10f && isFav -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
            else -> Color.Transparent
        },
        label = "swipeBg"
    )

    Box(modifier = modifier) {
        // Background revealed on swipe
        if (offsetX > 10f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Star, contentDescription = null,
                        tint = if (pastThreshold) {
                            if (isFav) MaterialTheme.colorScheme.error else FavoriteGold
                        } else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (pastThreshold) { if (isFav) "Release to Remove" else "Release to Add" }
                        else { if (isFav) "Swipe to Remove" else "Swipe to Add" },
                        color = if (pastThreshold) {
                            if (isFav) MaterialTheme.colorScheme.error else FavoriteGold
                        } else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (pastThreshold) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Card content
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetX = (offsetX + delta).coerceIn(0f, threshold * 1.5f)
                    },
                    onDragStopped = {
                        if (pastThreshold) {
                            AppSettings.toggleFavorite(species.taxonId)
                        }
                        offsetX = 0f
                    }
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        val name = species.commonName.ifEmpty { species.scientificName }
                        val url = "https://www.inaturalist.org/taxa/${species.taxonId}"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Check out $name on iNaturalist: $url")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share species"))
                    }
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                // Thumbnail
                if (photoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(photoUri).build(),
                        contentDescription = species.commonName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isFav) {
                                    Icon(Icons.Filled.Star, contentDescription = "Target",
                                        tint = FavoriteGold, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                                Text(
                                    text = primaryName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontStyle = if (useSci) FontStyle.Italic else FontStyle.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (showObservedIndicator && isObserved) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.Check, contentDescription = "Observed",
                                        tint = ObservedBlue, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                RarityDot(rarity = species.rarity)
                            }
                            if (secondaryName != null) {
                                Text(
                                    text = secondaryName,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontStyle = if (!useSci) FontStyle.Italic else FontStyle.Normal,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(horizontalAlignment = Alignment.End) {
                            StatusBadge(status = status)
                            if (activityPercent > 0) {
                                Text("${activityPercent}%", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    MiniBarChart(
                        weekly = species.weekly,
                        currentWeek = currentWeek,
                        modifier = Modifier.fillMaxWidth().height(20.dp)
                    )
                }
            }
        }
    }
}
