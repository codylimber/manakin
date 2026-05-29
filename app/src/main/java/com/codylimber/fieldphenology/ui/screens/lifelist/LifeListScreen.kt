package com.codylimber.fieldphenology.ui.screens.lifelist

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.codylimber.fieldphenology.data.api.LifeListService
import com.codylimber.fieldphenology.data.model.LifeListEntry
import com.codylimber.fieldphenology.data.model.LifeListSortMode
import com.codylimber.fieldphenology.data.model.SavedLifeList
import com.codylimber.fieldphenology.ui.theme.LocalBottomPadding
import com.codylimber.fieldphenology.ui.theme.Primary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeListScreen(
    lifeListService: LifeListService,
    onBack: (() -> Unit)? = null,
    onGenerate: () -> Unit,
    onUpdate: (SavedLifeList) -> Unit = {}
) {
    // null = index view; non-null = detail view for that list
    var detailList by remember { mutableStateOf<SavedLifeList?>(null) }
    var savedLists by remember { mutableStateOf(lifeListService.getSavedLifeLists()) }

    val current = detailList
    if (current != null) {
        LifeListDetailScreen(
            lifeList = current,
            onBack = { detailList = null }
        )
    } else {
        LifeListIndexScreen(
            lifeListService = lifeListService,
            savedLists = savedLists,
            onBack = onBack,
            onGenerate = onGenerate,
            onOpen = { detailList = it },
            onDelete = { list ->
                lifeListService.deleteLifeList(list.taxonId)
                savedLists = lifeListService.getSavedLifeLists()
            },
            onUpdate = onUpdate
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LifeListIndexScreen(
    lifeListService: LifeListService,
    savedLists: List<SavedLifeList>,
    onBack: (() -> Unit)?,
    onGenerate: () -> Unit,
    onOpen: (SavedLifeList) -> Unit,
    onDelete: (SavedLifeList) -> Unit,
    onUpdate: (SavedLifeList) -> Unit
) {
    val bottomPadding = LocalBottomPadding.current
    var deleteTarget by remember { mutableStateOf<SavedLifeList?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Life List", color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Primary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onGenerate) {
                        Icon(Icons.Default.Add, "Generate life list", tint = Primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (!lifeListService.hasUsername()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Connect your iNaturalist account in Settings, then tap + to generate a life list.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        if (savedLists.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No life lists yet", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text("Tap + to generate a life list for a taxon.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = onGenerate,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generate Life List")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, bottomPadding + 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(savedLists, key = { it.taxonId }) { list ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(list) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(list.taxonName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(
                                    "${list.entries.size} species · ${formatFileSize(lifeListService.getLifeListFileSize(list.taxonId))}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Generated ${formatDate(list.generatedAt)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onUpdate(list) }) {
                                Icon(Icons.Default.Refresh, "Update", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { deleteTarget = list }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Life List") },
            text = { Text("Delete your \"${target.taxonName}\" life list? This only removes it from Manakin — your iNaturalist observations are unchanged.") },
            confirmButton = {
                TextButton(onClick = { onDelete(target); deleteTarget = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LifeListDetailScreen(
    lifeList: SavedLifeList,
    onBack: () -> Unit
) {
    var sortMode by remember { mutableStateOf(LifeListSortMode.RECENTLY_ADDED) }
    val bottomPadding = LocalBottomPadding.current
    val context = LocalContext.current

    val sorted = remember(lifeList, sortMode) {
        when (sortMode) {
            LifeListSortMode.RECENTLY_ADDED -> lifeList.entries.sortedByDescending { it.firstObservedDate }
            LifeListSortMode.RECENTLY_SEEN  -> lifeList.entries.sortedByDescending { it.lastObservedDate }
            LifeListSortMode.TAXONOMY       -> lifeList.entries.sortedWith(
                compareBy<LifeListEntry> { it.scientificName.substringBefore(" ") }
                    .thenBy { it.scientificName }
            )
            LifeListSortMode.NAME           -> lifeList.entries.sortedBy {
                it.commonName.ifEmpty { it.scientificName }.lowercase()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(lifeList.taxonName, color = Primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${lifeList.entries.size} species", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Primary)
                    }
                },
                actions = {
                    LifeListSortDropdown(sortMode) { sortMode = it }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
        ) {
            items(sorted, key = { it.taxonId }) { entry ->
                LifeListEntryRow(
                    entry = entry,
                    sortMode = sortMode,
                    onClick = {
                        val uri = Uri.parse("https://www.inaturalist.org/taxa/${entry.taxonId}")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                )
            }
        }
    }
}

@Composable
private fun LifeListEntryRow(
    entry: LifeListEntry,
    sortMode: LifeListSortMode,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (entry.photoUrl != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context).data(entry.photoUrl).build(),
                contentDescription = entry.commonName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.commonName.ifEmpty { entry.scientificName },
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.commonName.isNotEmpty()) {
                Text(
                    text = entry.scientificName,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = when (sortMode) {
                    LifeListSortMode.RECENTLY_SEEN -> "Last seen ${formatDate(entry.lastObservedDate)}"
                    else -> "Since ${formatDate(entry.firstObservedDate)}"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 80.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun LifeListSortDropdown(current: LifeListSortMode, onChange: (LifeListSortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                when (current) {
                    LifeListSortMode.RECENTLY_ADDED -> "Recently Added"
                    LifeListSortMode.RECENTLY_SEEN  -> "Recently Seen"
                    LifeListSortMode.TAXONOMY       -> "Taxonomy"
                    LifeListSortMode.NAME           -> "Name"
                },
                fontSize = 13.sp
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LifeListSortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(when (mode) {
                            LifeListSortMode.RECENTLY_ADDED -> "Recently Added"
                            LifeListSortMode.RECENTLY_SEEN  -> "Recently Seen"
                            LifeListSortMode.TAXONOMY       -> "Taxonomy"
                            LifeListSortMode.NAME           -> "Name"
                        })
                    },
                    onClick = { onChange(mode); expanded = false }
                )
            }
        }
    }
}

private val displayFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun formatDate(dateStr: String): String {
    return try {
        LocalDate.parse(dateStr).format(displayFormatter)
    } catch (_: Exception) {
        dateStr
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
