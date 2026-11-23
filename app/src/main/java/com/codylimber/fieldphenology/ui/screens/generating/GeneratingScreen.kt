package com.codylimber.fieldphenology.ui.screens.generating

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.codylimber.fieldphenology.data.generator.GenerationJob
import com.codylimber.fieldphenology.data.generator.GenerationPhase
import com.codylimber.fieldphenology.data.generator.GenerationService
import com.codylimber.fieldphenology.data.generator.JobState
import com.codylimber.fieldphenology.ui.theme.Primary
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Download queue: the running pack with its phases, queued packs, and packs finished this
 * session. Leaving the screen never affects downloads; they run in [GenerationService].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratingScreen(
    onBack: () -> Unit,
    onAddAnother: () -> Unit
) {
    val context = LocalContext.current
    val jobs by GenerationService.jobs.collectAsState()
    val anyActive = jobs.any { it.isActive }

    // Android 13+: progress and "pack ready" notifications need runtime permission. Ask once,
    // the first time someone watches a download; downloads work either way.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads", color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (jobs.any { !it.isActive }) {
                        TextButton(onClick = { GenerationService.clearFinished() }) { Text("Clear") }
                    }
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
                .padding(horizontal = 16.dp)
        ) {
            if (jobs.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "No downloads",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(jobs, key = { it.id }) { job ->
                        JobCard(
                            job = job,
                            onCancel = { GenerationService.cancel(job.id) },
                            onRetry = { GenerationService.retry(context, job.id) },
                            onDismiss = { GenerationService.dismiss(job.id) }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (anyActive) {
                    Text(
                        "Downloads keep running if you leave this screen or close the app.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onAddAnother,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Queue another")
                    }
                    Button(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text(if (anyActive) "Run in background" else "Done", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

private val phaseLabels = mapOf(
    GenerationPhase.FETCHING_SPECIES to "Species list",
    GenerationPhase.FETCHING_HISTOGRAMS to "Weekly phenology",
    GenerationPhase.FETCHING_DETAILS to "Species details",
    GenerationPhase.DOWNLOADING_PHOTOS to "Photos",
    GenerationPhase.SAVING to "Saving"
)

@Composable
private fun JobCard(
    job: GenerationJob,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val p = job.params
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StateIcon(job.state)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.groupName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle(job),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when (job.state) {
                    JobState.QUEUED, JobState.RUNNING -> IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                    JobState.FAILED, JobState.CANCELLED -> Row {
                        IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = "Retry") }
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Dismiss") }
                    }
                    JobState.DONE -> IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            }

            if (job.state == JobState.RUNNING) {
                val progress = job.progress
                // Phase steps as a compact row of labels.
                val currentPhase = progress?.phase
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GenerationPhase.entries.forEach { phase ->
                        val done = currentPhase != null && phase.ordinal < currentPhase.ordinal
                        val active = phase == currentPhase
                        Text(
                            phaseLabels[phase] ?: "",
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                active -> MaterialTheme.colorScheme.onSurface
                                done -> Primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                            maxLines = 1
                        )
                    }
                }
                if (progress != null && progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { (progress.current.toFloat() / progress.total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Primary
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
                }
                Text(
                    progress?.message ?: "Starting…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (job.state == JobState.FAILED && job.error != null) {
                Text(job.error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StateIcon(state: JobState) {
    val mod = Modifier.size(22.dp)
    when (state) {
        JobState.RUNNING -> CircularProgressIndicator(modifier = mod, strokeWidth = 2.dp, color = Primary)
        JobState.QUEUED -> Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = mod)
        JobState.DONE -> Icon(Icons.Default.Check, null, tint = Primary, modifier = mod)
        JobState.FAILED -> Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = mod)
        JobState.CANCELLED -> Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = mod)
    }
}

private fun subtitle(job: GenerationJob): String {
    val state = when (job.state) {
        JobState.QUEUED -> "Queued"
        JobState.RUNNING -> "Downloading"
        JobState.DONE -> "Ready"
        JobState.FAILED -> "Failed"
        JobState.CANCELLED -> "Cancelled"
    }
    val months = monthsLabel(job.params.months)
    return listOfNotNull(state, job.params.placeName.ifBlank { null }, months).joinToString(" · ")
}

/** "Apr–Jun", "Jan, Mar, Dec", or null for the whole year. */
fun monthsLabel(months: List<Int>): String? {
    val sorted = months.filter { it in 1..12 }.distinct().sorted()
    if (sorted.isEmpty() || sorted.size == 12) return null
    fun name(m: Int) = Month.of(m).getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val contiguous = sorted.zipWithNext().all { (a, b) -> b == a + 1 }
    return if (contiguous && sorted.size > 2) "${name(sorted.first())}–${name(sorted.last())}"
    else sorted.joinToString(", ") { name(it) }
}
