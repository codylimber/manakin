package com.codylimber.fieldphenology.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codylimber.fieldphenology.ui.theme.Primary

data class DatasetItem(
    val key: String,
    val group: String,
    val placeName: String
)

@Composable
fun DatasetSelector(
    datasets: List<DatasetItem>,
    selectedKeys: Set<String>,
    onSelectSingle: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    val displayLabel = when {
        selectedKeys.size == datasets.size && onSelectAll != null -> "All Datasets"
        selectedKeys.size == 1 -> {
            val item = datasets.find { it.key == selectedKeys.first() }
            if (item != null) "${item.group} — ${item.placeName}" else selectedKeys.first()
        }
        else -> "${selectedKeys.size} datasets selected"
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = true },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                displayLabel,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ArrowDropDown, "Switch dataset", tint = Primary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            if (onSelectAll != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "All Datasets",
                            fontWeight = if (selectedKeys.size == datasets.size) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            color = Primary
                        )
                    },
                    onClick = { onSelectAll(); expanded = false }
                )
                HorizontalDivider()
            }
            datasets.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.key in selectedKeys,
                                onCheckedChange = { onToggle(item.key) },
                                colors = CheckboxDefaults.colors(checkedColor = Primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    item.group,
                                    fontWeight = if (item.key in selectedKeys) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 15.sp
                                )
                                Text(
                                    item.placeName,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    },
                    onClick = { onSelectSingle(item.key); expanded = false }
                )
            }
        }
    }
}
