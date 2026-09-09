package com.codylimber.fieldphenology.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.codylimber.fieldphenology.data.model.SpeciesStatus
import com.codylimber.fieldphenology.ui.theme.*

/**
 * The one chip primitive the app's badges are built from: a pill with a tonal
 * background and matching text color, so it stays legible in both themes
 * without the white-on-pastel washout that solid-fill badges produce in light
 * mode.
 */
@Composable
private fun ToneChip(
    label: String,
    role: ToneRole,
    modifier: Modifier = Modifier,
) {
    val dark = LocalIsDarkTheme.current
    Text(
        text = label,
        color = role.onContainer(dark),
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(PillShape)
            .background(role.container(dark))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun statusRole(status: SpeciesStatus): Pair<String, ToneRole> = when (status) {
    SpeciesStatus.PEAK -> "Peak" to StatusPeakRole
    SpeciesStatus.ACTIVE -> "Active" to StatusActiveRole
    SpeciesStatus.EARLY -> "Early" to StatusEdgeRole
    SpeciesStatus.LATE -> "Late" to StatusEdgeRole
    SpeciesStatus.INACTIVE -> "Inactive" to StatusInactiveRole
}

@Composable
fun StatusBadge(status: SpeciesStatus, modifier: Modifier = Modifier) {
    val (label, role) = statusRole(status)
    ToneChip(label = label, role = role, modifier = modifier)
}

private fun activityRole(percent: Int): ToneRole = when {
    percent >= 80 -> StatusPeakRole
    percent >= 20 -> StatusActiveRole
    percent > 0 -> StatusEdgeRole
    else -> StatusInactiveRole
}

@Composable
fun ActivityBadge(percent: Int, modifier: Modifier = Modifier) {
    ToneChip(label = "$percent%", role = activityRole(percent), modifier = modifier)
}

@Composable
fun ActivityDot(percent: Int, modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current
    val color = activityRole(percent).solid(dark)
    // Fill tracks abundance: solid when the species is well into its season,
    // hollow when it is only just on or off.
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(if (percent >= 50) color else color.copy(alpha = 0.28f))
            .padding(1.5.dp)
    ) {
        if (percent in 1..49) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

private fun rarityRole(rarity: String): ToneRole? = when (rarity) {
    "Common" -> RarityCommonRole
    "Uncommon" -> RarityUncommonRole
    "Rare" -> RarityRareRole
    else -> null
}

@Composable
fun RarityDot(rarity: String, modifier: Modifier = Modifier) {
    val dark = LocalIsDarkTheme.current
    val color = rarityRole(rarity)?.solid(dark) ?: MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun RarityChip(rarity: String, modifier: Modifier = Modifier) {
    val role = rarityRole(rarity) ?: return
    ToneChip(label = rarity, role = role, modifier = modifier)
}

@Composable
fun ConservationBadge(status: String?, modifier: Modifier = Modifier) {
    if (status == null) return
    val code = status.uppercase()
    val dark = LocalIsDarkTheme.current
    // Only the IUCN codes get a badge; anything else is dataset noise.
    val solid = when (code) {
        "LC" -> ConservationLC
        "NT" -> ConservationNT
        "VU" -> ConservationVU
        "EN" -> ConservationEN
        "CR", "EW", "EX" -> ConservationCR
        else -> return
    }
    // Conservation codes are rare enough on screen that they read better as an
    // outlined chip than as another filled one competing with the status badge.
    Text(
        text = code,
        color = if (dark) solid else solid.darken(),
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(PillShape)
            .background(solid.copy(alpha = if (dark) 0.16f else 0.20f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Pull a bright dark-theme accent down to something readable on white. */
private fun Color.darken(factor: Float = 0.62f) =
    Color(red * factor, green * factor, blue * factor, alpha)
