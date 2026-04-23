package com.codylimber.fieldphenology.ui.screens.managedatasets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.core.content.FileProvider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codylimber.fieldphenology.data.repository.DatasetInfo
import com.codylimber.fieldphenology.data.repository.DatasetSource
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageDatasetsScreen(
    repository: PhenologyRepository,
    bottomBarHeight: androidx.compose.ui.unit.Dp = 0.dp,
    onBack: (() -> Unit)? = null,
    onAddDataset: () -> Unit = {},
    onUpdateDataset: ((meta: com.codylimber.fieldphenology.data.model.DatasetMetadata) -> Unit)? = null,
    onTimeline: (() -> Unit)? = null,
    onTripReport: (() -> Unit)? = null,
    onCompare: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
    onAbout: (() -> Unit)? = null
) {
    var datasets by remember { mutableStateOf(repository.getAllDatasets()) }
    var deleteTarget by remember { mutableStateOf<DatasetInfo?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Refresh when returning to this screen
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                datasets = repository.getAllDatasets()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Datasets", color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Primary)
                        }
                    }
                },
                actions = {
                    com.codylimber.fieldphenology.ui.screens.specieslist.AppOverflowMenu(onTimeline = onTimeline, onTripReport = onTripReport, onCompare = onCompare, onHelp = onHelp, onAbout = onAbout)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddDataset,
                containerColor = Primary,
                modifier = Modifier.padding(bottom = bottomBarHeight)
            ) {
                Icon(Icons.Default.Add, "Add Dataset")
            }
        }
    ) { padding ->
        if (datasets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No datasets installed", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + bottomBarHeight),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(datasets) { info ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(info.group, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(info.placeName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                val details = buildString {
                                    append("${info.speciesCount} species")
                                    if (info.source == DatasetSource.INTERNAL && info.sizeBytes > 0) {
                                        append("  \u2022  ${info.sizeDisplay}")
                                    }
                                    if (info.source == DatasetSource.ASSET) {
                                        append("  \u2022  Bundled")
                                    }
                                }
                                Text(details, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            if (info.source == DatasetSource.INTERNAL && onUpdateDataset != null) {
                                val ds = repository.getDataset(info.key)
                                IconButton(onClick = {
                                    ds?.metadata?.let { meta ->
                                        onUpdateDataset(meta)
                                    }
                                }) {
                                    Icon(Icons.Default.Refresh, "Update", tint = Primary, modifier = Modifier.size(20.dp))
                                }
                            }
                            if (info.source == DatasetSource.INTERNAL) {
                                IconButton(onClick = {
                                    val zipFile = repository.exportDataset(info.key)
                                    if (zipFile != null) {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/octet-stream"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(Intent.EXTRA_TEXT,
                                                "${info.group} — ${info.placeName} dataset for Manakin.\n\n" +
                                                "To import: Open Manakin > Datasets tab > tap + > Advanced Options > Import Dataset, then select this .manakin file."
                                            )
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Dataset"))
                                    }
                                }) {
                                    Icon(Icons.Default.Share, "Share", tint = Primary)
                                }
                            }
                            IconButton(onClick = { deleteTarget = info }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Delete confirmation dialog
        deleteTarget?.let { info ->
            val isBundled = info.source == DatasetSource.ASSET
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("Remove ${info.group}?") },
                text = {
                    Text(
                        if (isBundled) "This will hide the bundled dataset. You can reinstall the app to restore it."
                        else "This will remove the dataset and all downloaded photos."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        repository.deleteDataset(info.key)
                        datasets = repository.getAllDatasets()
                        deleteTarget = null
                    }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
