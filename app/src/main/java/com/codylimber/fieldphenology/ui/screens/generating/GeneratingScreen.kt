package com.codylimber.fieldphenology.ui.screens.generating

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codylimber.fieldphenology.data.generator.DatasetGenerator
import com.codylimber.fieldphenology.data.generator.GenerationPhase
import com.codylimber.fieldphenology.data.repository.PhenologyRepository
import com.codylimber.fieldphenology.ui.navigation.GenerationParams
import com.codylimber.fieldphenology.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratingScreen(
    params: GenerationParams,
    generator: DatasetGenerator,
    repository: PhenologyRepository,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: GeneratingViewModel = viewModel { GeneratingViewModel(generator, repository) }
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.startGeneration(params)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generating Dataset", color = Primary, fontWeight = FontWeight.Bold) },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(params.groupName, color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(params.placeName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(8.dp))

            val phaseLabels = mapOf(
                GenerationPhase.FETCHING_SPECIES to "Fetching species list",
                GenerationPhase.FETCHING_HISTOGRAMS to "Fetching weekly histograms",
                GenerationPhase.FETCHING_DETAILS to "Fetching species details",
                GenerationPhase.DOWNLOADING_PHOTOS to "Downloading photos",
                GenerationPhase.SAVING to "Saving dataset"
            )

            for (phase in GenerationPhase.entries) {
                val isComplete = phase in state.completedPhases
                val isActive = state.progress?.phase == phase && !state.isComplete
                PhaseRow(
                    label = phaseLabels[phase] ?: "",
                    isComplete = isComplete,
                    isActive = isActive
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            state.progress?.let { progress ->
                if (!state.isComplete && state.error == null) {
                    LinearProgressIndicator(
                        progress = { if (progress.total > 0) progress.current.toFloat() / progress.total else 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(progress.message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(error, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state.isComplete) {
                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Done", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            } else if (state.error != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Cancel") }
                    Button(
                        onClick = { viewModel.startGeneration(params) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text("Retry") }
                }
            } else {
                OutlinedButton(
                    onClick = { viewModel.cancel(); onCancel() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun PhaseRow(label: String, isComplete: Boolean, isActive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            isComplete -> Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(20.dp))
            isActive -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
            else -> Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            color = when {
                isComplete -> Primary
                isActive -> MaterialTheme.colorScheme.onBackground
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 14.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
