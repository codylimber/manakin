package com.codylimber.fieldphenology.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codylimber.fieldphenology.data.model.SpeciesStatus
import androidx.compose.material3.MaterialTheme
import com.codylimber.fieldphenology.ui.theme.*

@Composable
fun StatusBadge(status: SpeciesStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        SpeciesStatus.PEAK -> "Peak" to StatusPeak
        SpeciesStatus.ACTIVE -> "Active" to StatusActive
        SpeciesStatus.EARLY -> "Early" to StatusEarlyLate
        SpeciesStatus.LATE -> "Late" to StatusEarlyLate
        SpeciesStatus.INACTIVE -> "Inactive" to StatusInactive
    }
    Text(
        text = label,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.85f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun ActivityBadge(percent: Int, modifier: Modifier = Modifier) {
    val color = when {
        percent >= 80 -> StatusPeak
        percent >= 20 -> StatusActive
        percent > 0 -> StatusEarlyLate
        else -> StatusInactive
    }
    Text(
        text = "${percent}%",
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.85f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun ActivityDot(percent: Int, modifier: Modifier = Modifier) {
    val color = when {
        percent >= 80 -> StatusPeak
        percent >= 20 -> StatusActive
        percent > 0 -> StatusEarlyLate
        else -> StatusInactive
    }
    // Full circle for >= 50%, half circle for > 0%, empty for 0%
    val symbol = when {
        percent >= 50 -> "\u25CF"  // filled circle
        percent > 0 -> "\u25D0"    // half circle
        else -> "\u25CB"           // empty circle
    }
    Text(
        text = symbol,
        color = color,
        fontSize = 14.sp,
        modifier = modifier
    )
}

@Composable
fun RarityDot(rarity: String, modifier: Modifier = Modifier) {
    val color = when (rarity) {
        "Common" -> RarityCommon
        "Uncommon" -> RarityUncommon
        "Rare" -> RarityRare
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = "\u25CF",
        color = color,
        fontSize = 12.sp,
        modifier = modifier
    )
}

@Composable
fun RarityChip(rarity: String, modifier: Modifier = Modifier) {
    val color = when (rarity) {
        "Common" -> RarityCommon
        "Uncommon" -> RarityUncommon
        "Rare" -> RarityRare
        else -> return
    }
    Text(
        text = rarity,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.75f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}

@Composable
fun ConservationBadge(status: String?, modifier: Modifier = Modifier) {
    if (status == null) return
    val iucnStatuses = setOf("LC", "NT", "VU", "EN", "CR", "EW", "EX")
    if (status.uppercase() !in iucnStatuses) return
    val color = when (status.uppercase()) {
        "LC" -> ConservationLC
        "NT" -> ConservationNT
        "VU" -> ConservationVU
        "EN" -> ConservationEN
        "CR", "EW", "EX" -> ConservationCR
        else -> return
    }
    Text(
        text = status.uppercase(),
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.85f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    )
}
